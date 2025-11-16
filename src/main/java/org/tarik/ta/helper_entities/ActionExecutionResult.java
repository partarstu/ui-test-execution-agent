/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.helper_entities;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.annotations.JsonClassDescription;
import org.tarik.ta.annotations.JsonFieldDescription;

import java.awt.image.BufferedImage;
import java.time.Instant;

@JsonClassDescription("Result of an action execution containing success status, message, optional screenshot, and timing information")
public record ActionExecutionResult(
        @JsonFieldDescription("Indicates whether the action execution was successful")
        boolean success,
        
        @JsonFieldDescription("Human-readable message describing the execution result")
        String message,
        
        @JsonFieldDescription("Optional screenshot captured during execution (nullable)")
        @Nullable BufferedImage screenshot,
        
        @JsonFieldDescription("Timestamp when the action execution started")
        Instant startTime,
        
        @JsonFieldDescription("Timestamp when the action execution completed")
        Instant endTime) {
    
    /**
     * Constructor for backward compatibility - sets timestamps to current time.
     */
    public ActionExecutionResult(boolean success, String message, @Nullable BufferedImage screenshot) {
        this(success, message, screenshot, Instant.now(), Instant.now());
    }
    
    /**
     * Returns the duration of the action execution in milliseconds.
     */
    public long durationMillis() {
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
}