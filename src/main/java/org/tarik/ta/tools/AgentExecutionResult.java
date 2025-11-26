/*
 * Copyright (c) 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.tarik.ta.tools;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.annotations.JsonClassDescription;
import org.tarik.ta.annotations.JsonFieldDescription;

import java.awt.image.BufferedImage;
import java.time.Instant;

import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.SUCCESS;

@JsonClassDescription("Result of a tool execution containing status, message, optional screenshot, typed payload, and timestamp")
public record AgentExecutionResult<T>(
        @JsonFieldDescription("Execution status indicating success, error, or user interruption")
        ExecutionStatus executionStatus,

        @JsonFieldDescription("Human-readable message describing the execution result")
        String message,

        @JsonFieldDescription("Indicates whether retrying this operation makes sense")
        boolean retryMakesSense,

        @JsonFieldDescription("Optional screenshot captured during execution (nullable)")
        @Nullable BufferedImage screenshot,

        @JsonFieldDescription("Strongly-typed payload containing the specific result data (nullable)")
        @Nullable T resultPayload,

        @JsonFieldDescription("Timestamp when the tool execution completed")
        Instant timestamp) {

    public AgentExecutionResult(ExecutionStatus executionStatus, String message, boolean retryMakesSense, Instant timestamp) {
        this(executionStatus, message, retryMakesSense, null, null, timestamp);
    }

    public AgentExecutionResult(ExecutionStatus executionStatus, String message, boolean retryMakesSense, T resultPayload,
                                Instant timestamp) {
        this(executionStatus, message, retryMakesSense, null, resultPayload, timestamp);
    }

    public AgentExecutionResult(ExecutionStatus executionStatus, String message, boolean retryMakesSense,
                                BufferedImage screenshot, Instant timestamp) {
        this(executionStatus, message, retryMakesSense, screenshot, null, timestamp);
    }

    /**
     * Returns true if the execution was successful (status is SUCCESS).
     */
    public boolean success() {
        return executionStatus == SUCCESS;
    }

    public enum ExecutionStatus {
        SUCCESS, ERROR, VERIFICATION_FAILURE, INTERRUPTED_BY_USER
    }
}
