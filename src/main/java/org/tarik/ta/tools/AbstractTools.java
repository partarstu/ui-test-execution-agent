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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.time.Instant;

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
        return new ToolExecutionResult<>(SUCCESS, message, false, null, resultPayload, Instant.now());
    }

    @NotNull
    protected static ToolExecutionResult<?> getSuccessfulResult(String message) {
        LOG.info(message);
        return new ToolExecutionResult<>(SUCCESS, message, false, Instant.now());
    }

    @NotNull
    protected static ToolExecutionResult<?> getFailedToolExecutionResult(String message, boolean retryMakesSense) {
        LOG.error(message);
        return new ToolExecutionResult<>(ERROR, message, retryMakesSense, Instant.now());
    }

    @NotNull
    protected static <T> ToolExecutionResult<T> getFailedToolExecutionResult(String message, boolean retryMakesSense,
                                                                             BufferedImage screenshot, @Nullable T result) {
        LOG.error(message);
        return new ToolExecutionResult<>(ERROR, message, retryMakesSense, screenshot, result, Instant.now());
    }

    @NotNull
    protected static ToolExecutionResult<?> getFailedToolExecutionResult(String message, boolean retryMakesSense, BufferedImage screenshot) {
        LOG.error(message);
        return new ToolExecutionResult<>(ERROR, message, retryMakesSense, screenshot, Instant.now());
    }

    @NotNull
    protected static ToolExecutionResult<?> getFailedToolExecutionResult(String message, boolean retryMakesSense, Throwable t) {
        LOG.error(message, t);
        return new ToolExecutionResult<>(ERROR, message, retryMakesSense, Instant.now());
    }
}