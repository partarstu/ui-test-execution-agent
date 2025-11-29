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

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;

public class FinalResultTool extends AbstractTools {
    @Tool(returnBehavior = IMMEDIATE, value = "Ends the execution and returns the final result.")
    public String endExecution(@P(value = "Final result of the execution", required = false) String finalResult) {
        try {
            return finalResult;
        } catch (Exception e) {
            throw rethrowAsToolException(e, "returning the final result");
        }
    }
}