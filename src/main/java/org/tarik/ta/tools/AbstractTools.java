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
package org.tarik.ta.tools;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.ERROR;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.SUCCESS;

public class AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTools.class);

    public enum ToolExecutionStatus {
        SUCCESS, ERROR, INTERRUPTED_BY_USER
    }

    @NotNull
    protected static <T> ToolExecutionResult<T> getSuccessfulResult(String message, T resultPayload) {
        LOG.info(message);
        return new ToolExecutionResult<>(SUCCESS, message, false, null, resultPayload);
    }

    @NotNull
    protected static ToolExecutionResult<?> getSuccessfulResult(String message) {
        LOG.info(message);
        return new ToolExecutionResult<>(SUCCESS, message, false);
    }

    @NotNull
    protected static ToolExecutionResult<?> getFailedToolExecutionResult(String message, boolean retryMakesSense) {
        LOG.error(message);
        return new ToolExecutionResult<>(ERROR, message, retryMakesSense);
    }

    @NotNull
    protected static ToolExecutionResult<?> getFailedToolExecutionResult(String message, boolean retryMakesSense, BufferedImage screenshot) {
        LOG.error(message);
        return new ToolExecutionResult<>(ERROR, message, retryMakesSense, screenshot);
    }

    @NotNull
    protected static ToolExecutionResult<?> getFailedToolExecutionResult(String message, boolean retryMakesSense, Throwable t) {
        LOG.error(message, t);
        return new ToolExecutionResult<>(ERROR, message, retryMakesSense);
    }

    public record ToolExecutionResult<T>(ToolExecutionStatus executionStatus, String message, boolean retryMakesSense,
                                         BufferedImage screenshot, T resultPayload) {
        public ToolExecutionResult(ToolExecutionStatus executionStatus, String message, boolean retryMakesSense) {
            this(executionStatus, message, retryMakesSense, null, null);
        }

        public ToolExecutionResult(ToolExecutionStatus executionStatus, String message, boolean retryMakesSense, T resultPayload) {
            this(executionStatus, message, retryMakesSense, null, resultPayload);
        }

        public ToolExecutionResult(ToolExecutionStatus executionStatus, String message, boolean retryMakesSense, BufferedImage screenshot) {
            this(executionStatus, message, retryMakesSense, screenshot, null);
        }

        public Optional<T> getResultPayload() {
            return Optional.ofNullable(resultPayload);
        }
    }
}