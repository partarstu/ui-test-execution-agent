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

import java.awt.image.BufferedImage;

/**
 * Result of confirming the location of a UI element.
 * The user is shown a screenshot with the element highlighted and asked to confirm
 * if the location is correct.
 */
@JsonClassDescription("Result of user confirmation for a located UI element")
public record LocationConfirmationResult(
        @JsonFieldDescription("The user's choice regarding the element location") UserChoice choice,
        @JsonFieldDescription("The bounding box that was presented for confirmation") BoundingBox boundingBox,
        @JsonFieldDescription("Description of the element that was being confirmed") String elementDescription,
        @JsonFieldDescription("Additional message or context") String message
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
    public static LocationConfirmationResult correct(BoundingBox boundingBox, BufferedImage screenshot,
                                                     String elementDescription) {
        return new LocationConfirmationResult(UserChoice.CORRECT, boundingBox, screenshot, elementDescription,
                "Location confirmed as correct");
    }

    /**
     * Factory method for incorrect location.
     */
    public static LocationConfirmationResult incorrect(BoundingBox boundingBox, BufferedImage screenshot,
                                                       String elementDescription) {
        return new LocationConfirmationResult(UserChoice.INCORRECT, boundingBox, screenshot, elementDescription,
                "Location marked as incorrect");
    }

    /**
     * Factory method for interrupted confirmation.
     */
    public static LocationConfirmationResult interrupted(String elementDescription) {
        return new LocationConfirmationResult(UserChoice.INTERRUPTED, null, null, elementDescription,
                "Confirmation interrupted by user");
    }

    /**
     * Factory method for failed result.
     */
    public static LocationConfirmationResult failure(String reason) {
        return new LocationConfirmationResult(null, null, null, null, reason);
    }

    /**
     * Check if the location was confirmed as correct.
     */
    public boolean isCorrect() {
        return choice == UserChoice.CORRECT;
    }

    /**
     * Check if the location was marked as incorrect.
     */
    public boolean isIncorrect() {
        return choice == UserChoice.INCORRECT;
    }

    /**
     * Check if the process was interrupted.
     */
    public boolean isInterrupted() {
        return choice == UserChoice.INTERRUPTED;
    }

    /**
     * Check if the result indicates a failure.
     */
    public boolean isFailure() {
        return choice == null;
    }
}
