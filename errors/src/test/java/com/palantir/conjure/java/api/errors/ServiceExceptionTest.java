/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.conjure.java.api.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ServiceExceptionTest {

    private static final String ERROR_NAME = "Namespace:MyDesc";
    private static final ErrorType ERROR = ErrorType.create(ErrorType.Code.CUSTOM_CLIENT, ERROR_NAME);
    private static final String EXPECTED_ERROR_MSG = "ServiceException: CUSTOM_CLIENT (Namespace:MyDesc)";

    @Test
    public void testExceptionMessageContainsNoArgs_safeLogMessageContainsSafeArgsOnly() {
        Arg<?>[] args = {
                SafeArg.of("arg1", "foo"),
                UnsafeArg.of("arg2", 2),
                UnsafeArg.of("arg3", null)};
        ServiceException ex = new ServiceException(ERROR, args);

        assertThat(ex.getLogMessage()).isEqualTo(EXPECTED_ERROR_MSG);
        assertThat(ex.getMessage()).isEqualTo(EXPECTED_ERROR_MSG + ": {arg1=foo, arg2=2, arg3=null}");
    }

    @Test
    public void testExceptionMessageWithDuplicateKeys() {
        ServiceException ex = new ServiceException(ERROR, SafeArg.of("arg1", "foo"), SafeArg.of("arg1", 2));
        assertThat(ex.getMessage()).isEqualTo(EXPECTED_ERROR_MSG + ": {arg1=foo, arg1=2}");
    }

    @Test
    public void testExceptionMessageWithUnsafeArgs() {
        ServiceException ex = new ServiceException(ERROR, UnsafeArg.of("arg1", 1), SafeArg.of("arg2", 2));
        assertThat(ex.getMessage()).isEqualTo(EXPECTED_ERROR_MSG + ": {arg1=1, arg2=2}");
    }

    @Test
    public void testExceptionMessageWithNoArgs() {
        ServiceException ex = new ServiceException(ERROR);
        assertThat(ex.getMessage()).isEqualTo(EXPECTED_ERROR_MSG);
    }

    @Test
    public void testExceptionCause() {
        Throwable cause = new RuntimeException("foo");
        ServiceException ex = new ServiceException(ERROR, cause);

        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    public void testStatus() {
        ServiceException ex = new ServiceException(ERROR);
        assertThat(ex.getErrorType().httpErrorCode()).isEqualTo(400);
    }

    @Test
    public void testErrorIdsAreUnique() {
        UUID errorId1 = UUID.fromString(new ServiceException(ERROR).getErrorInstanceId());
        UUID errorId2 = UUID.fromString(new ServiceException(ERROR).getErrorInstanceId());

        assertThat(errorId1).isNotEqualTo(errorId2);
    }

    @Test
    public void testErrorIdsAreInheritedFromServiceExceptions() {
        ServiceException rootCause = new ServiceException(ERROR);
        SafeRuntimeException intermediate = new SafeRuntimeException("Handled an exception", rootCause);
        ServiceException parent = new ServiceException(ERROR, intermediate);
        assertThat(parent.getErrorInstanceId()).isEqualTo(rootCause.getErrorInstanceId());
    }

    @Test
    public void testErrorIdsAreInheritedFromRemoteExceptions() {
        RemoteException rootCause = new RemoteException(new SerializableError.Builder()
                .errorCode("errorCode")
                .errorName("errorName")
                .build(), 500);
        SafeRuntimeException intermediate = new SafeRuntimeException("Handled an exception", rootCause);
        ServiceException parent = new ServiceException(ERROR, intermediate);
        assertThat(parent.getErrorInstanceId()).isEqualTo(rootCause.getError().errorInstanceId());
    }

    @Test
    public void testCircularCause() {
        RuntimeException first = new RuntimeException();
        RuntimeException second = new RuntimeException(first);
        // Yes, you can do this. In practice it's often more subtle when libraries attempt to piece together
        // more helpful exception chains and encounter unexpected edge cases.
        first.initCause(second);
        // invoke getErrorInstanceId to ensure this is tested even if future developers
        // optimize generation to occur lazily.
        assertThat(new ServiceException(ERROR, second).getErrorInstanceId()).isNotNull();
    }
}
