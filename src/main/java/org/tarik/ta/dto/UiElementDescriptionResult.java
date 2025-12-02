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

import org.tarik.ta.dto.FinalResult;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;

@Description("the extracted by you information about the target UI element")
public record UiElementDescriptionResult(
        @Description("Identified by you name of the target element based on the original provided to you information.")
        String name,
        @Description("An accurate, specific, compact information about the visual appearance of the target element. " +
                "This information must be enough for you to find this element on the screenshot, but at the same time this info shouldn't " +
                "contain any details which are too specific and might change over time (e.g. color, size etc.)")
        String ownDescription,
        @Description("The detailed description of the location of the target element relative to the nearest neighboring " +
                "element or elements. This information must be enough for you to find this element on the screenshot if multiple similar " +
                "elements are displayed on it, e.g. in case of multiple identical check-boxes or input fields with unique labels, " +
                "multiple identical buttons related to different forms or dialogs etc. This info shouldn't contain any details which are " +
                "too specific and might easily change over time during refactoring of UI.")
        String locationDescription,
        @Description("Name or very short description of the direct parent (enclosing) element (e.g. page/form/dialog/popup/view " +
                "etc.) in which the target element is located.")
        String pageSummary) implements FinalResult<UiElementDescriptionResult> {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementDescriptionResult.class);

    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE)
    public UiElementDescriptionResult endExecutionAndGetFinalResult(
            @P(value = FINAL_RESULT_PARAM_DESCRIPTION) UiElementDescriptionResult result) {
        LOG.debug("Ending execution and returning the final result: {}", result);
        return result;
    }
}