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

import dev.langchain4j.model.output.structured.Description;

/**
 * Result of prompting the user for the next action when element location attempts fail.
 * The user can choose to create a new element, retry the search, or terminate.
 */
@Description("Result of user decision on next action when element location fails")
public record NextActionResult(
        @Description("The user's decision on what action to take next") UserDecision decision,
        @Description("Message or details about the decision") String message
) {
    /**
     * Enum representing the user's decision.
     */
    public enum UserDecision {
        CREATE_NEW_ELEMENT,
        REFINE_EXISTING_ELEMENTS,
        RETRY_SEARCH,
        TERMINATE
    }

    /**
     * Factory method for create new element decision.
     */
    public static NextActionResult createNewElement() {
        return new NextActionResult(UserDecision.CREATE_NEW_ELEMENT, "User chose to create a new element");
    }

    /**
     * Factory method for refine existing element decision.
     */
    public static NextActionResult refineExistingElement() {
        return new NextActionResult(UserDecision.REFINE_EXISTING_ELEMENTS, "User chose to refine existing elements");
    }

    /**
     * Factory method for retry search decision.
     */
    public static NextActionResult retrySearch() {
        return new NextActionResult(UserDecision.RETRY_SEARCH, "User chose to retry the UI element search");
    }

    /**
     * Factory method for terminate decision.
     */
    public static NextActionResult terminate() {
        return new NextActionResult(UserDecision.TERMINATE, "User chose to terminate execution");
    }

    /**
     * Factory method for failed result.
     */
    public static NextActionResult failure(String error) {
        return new NextActionResult(null, error);
    }
}
