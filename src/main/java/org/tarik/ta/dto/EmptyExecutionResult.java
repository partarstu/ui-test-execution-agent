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
package org.tarik.ta.dto;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;


@Description("Empty execution result")
public record EmptyExecutionResult() implements FinalResult<EmptyExecutionResult> {
    private static final Logger LOG = LoggerFactory.getLogger(EmptyExecutionResult.class);

    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE)
    public VerificationExecutionResult endExecution() {
        LOG.debug("Ending execution without result.");
        return null;
    }
}