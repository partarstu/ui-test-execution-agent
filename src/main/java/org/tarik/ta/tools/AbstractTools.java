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

import org.tarik.ta.agents.ToolVerificationAgent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.langchain4j.service.AiServices;
import org.tarik.ta.model.ModelFactory;

import java.awt.image.BufferedImage;
import java.time.Instant;

import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.SUCCESS;

public class AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTools.class);
    protected final ToolVerificationAgent toolVerificationAgent;

    public AbstractTools() {
        this(AiServices.builder(ToolVerificationAgent.class)
                .chatModel(ModelFactory.getVerificationVisionModel().getChatModel())
                .build());
    }

    protected AbstractTools(ToolVerificationAgent toolVerificationAgent) {
        this.toolVerificationAgent = toolVerificationAgent;
    }

    @NotNull
    protected static <T> AgentExecutionResult<T> getSuccessfulResult(String message, T resultPayload) {
        LOG.info(message);
        return new AgentExecutionResult<>(SUCCESS, message, false, null, resultPayload, Instant.now());
    }

    @NotNull
    protected static AgentExecutionResult<?> getSuccessfulResult(String message) {
        LOG.info(message);
        return new AgentExecutionResult<>(SUCCESS, message, false, Instant.now());
    }

    @NotNull
    protected static AgentExecutionResult<?> getFailedToolExecutionResult(String message, boolean retryMakesSense) {
        LOG.error(message);
        return new AgentExecutionResult<>(ERROR, message, retryMakesSense, Instant.now());
    }

    @NotNull
    protected static <T> AgentExecutionResult<T> getFailedToolExecutionResult(String message, boolean retryMakesSense,
            BufferedImage screenshot, @Nullable T result) {
        LOG.error(message);
        return new AgentExecutionResult<>(ERROR, message, retryMakesSense, screenshot, result, Instant.now());
    }

    @NotNull
    protected static AgentExecutionResult<?> getFailedToolExecutionResult(String message, boolean retryMakesSense,
            BufferedImage screenshot) {
        LOG.error(message);
        return new AgentExecutionResult<>(ERROR, message, retryMakesSense, screenshot, Instant.now());
    }

    @NotNull
    protected static AgentExecutionResult<?> getFailedToolExecutionResult(String message, boolean retryMakesSense,
            Throwable t) {
        LOG.error(message, t);
        return new AgentExecutionResult<>(ERROR, message, retryMakesSense, Instant.now());
    }
}