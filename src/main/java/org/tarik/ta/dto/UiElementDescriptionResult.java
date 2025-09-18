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

import org.tarik.ta.annotations.JsonClassDescription;
import org.tarik.ta.annotations.JsonFieldDescription;

@JsonClassDescription("the extracted by you information about the target UI element")
public record UiElementDescriptionResult(
        @JsonFieldDescription("Identified by you name of the target element based on the original provided to you information.")
        String name,
        @JsonFieldDescription("An accurate, specific and complete information about the visual appearance of the target element and " +
                "its purpose.")
        String ownDescription,
        @JsonFieldDescription("The detailed description of the location of the target element (part of the screen) and relative to other " +
                "directly nearby UI elements from their left, right, top and bottom (e.g. target element is located to the " +
                "right from directly to the left from ..., directly above ..., below ...)")
        String anchorsDescription,
        @JsonFieldDescription("A very short summary of the parent element (e.g. page/form/dialog/popup/view etc.) in which the " +
                "target element is located.")
        String pageSummary) {
}