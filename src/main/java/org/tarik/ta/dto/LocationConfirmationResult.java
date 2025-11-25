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
 * Result of confirming the location of a UI element.
 * The user is shown a screenshot with the element highlighted and asked to confirm
 * if the location is correct.
 */
@Description("Result of user confirmation for a located UI element")
public record LocationConfirmationResult(
        @Description("The user's choice regarding the element location") UserChoice choice,
        @Description("Additional message or context") String message
) {
    /**
     * Enum representing the user's choice.
     */
    public enum UserChoice {
        CORRECT,
        INCORRECT,
        INTERRUPTED
    }

    /**
     * Factory method for correct location.
     */
    public static LocationConfirmationResult correct() {
        return new LocationConfirmationResult(UserChoice.CORRECT, "Location confirmed as correct");
    }

    /**
     * Factory method for incorrect location.
     */
    public static LocationConfirmationResult incorrect() {
        return new LocationConfirmationResult(UserChoice.INCORRECT, "Location marked as incorrect");
    }

    /**
     * Factory method for interrupted confirmation.
     */
    public static LocationConfirmationResult interrupted() {
        return new LocationConfirmationResult(UserChoice.INTERRUPTED, "Confirmation interrupted by user");
    }

    /**
     * Factory method for failed result.
     */
    public static LocationConfirmationResult failure(String reason) {
        return new LocationConfirmationResult(null, reason);
    }
}
