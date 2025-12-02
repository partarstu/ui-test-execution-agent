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

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.annotations.JsonClassDescription;
import org.tarik.ta.annotations.JsonFieldDescription;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;

@Description("the identified best match of the bounding box for a target UI element")
public record UiElementIdentificationResult(
        @Description("indicates whether there is a match. Must be \"false\", if you're sure that there are" +
                " no bounding boxes which correctly mark the target UI element based on its info and visual characteristics," +
                " \"true\" otherwise.")
        boolean success,
        @Description("contains the ID of the identified bounding box. If the value of \"success\" field " +
                "is \"false\", this field must be an empty string, \"\".")
        String boundingBoxId,
        @Description("contains any comments regarding the results of identification. If the value of \"success\" field is " +
                "\"true\", this field should have your comments clarifying why a specific bounding box was identified comparing to " +
                "others. If the value of \"success\" field is \"false\", this field should have your comments " +
                "clarifying why you found no good match at all.")
        String message) implements FinalResult<UiElementIdentificationResult> {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementIdentificationResult.class);

    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE)
    public UiElementIdentificationResult endExecutionAndGetFinalResult(
            @P(value = FINAL_RESULT_PARAM_DESCRIPTION) UiElementIdentificationResult result) {
        LOG.debug("Ending execution and returning the final result: {}", result);
        return result;
    }
}