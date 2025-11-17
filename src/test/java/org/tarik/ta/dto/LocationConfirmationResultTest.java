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

import org.junit.jupiter.api.Test;
import org.tarik.ta.annotations.JsonClassDescription;
import org.tarik.ta.annotations.JsonFieldDescription;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class LocationConfirmationResultTest {

    @Test
    void testCorrectFactoryMethod() {
        // Given
        BoundingBox boundingBox = new BoundingBox(10, 20, 100, 50);
        BufferedImage screenshot = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        String description = "Login Button";

        // When
        LocationConfirmationResult result = LocationConfirmationResult.correct(boundingBox, screenshot, description);

        // Then
        assertEquals(LocationConfirmationResult.UserChoice.CORRECT, result.choice());
        assertTrue(result.isCorrect());
        assertFalse(result.isIncorrect());
        assertFalse(result.isInterrupted());
        assertEquals(boundingBox, result.boundingBox());
        assertEquals(screenshot, result.screenshot());
        assertEquals(description, result.elementDescription());
        assertEquals("Location confirmed as correct", result.message());
    }

    @Test
    void testIncorrectFactoryMethod() {
        // Given
        BoundingBox boundingBox = new BoundingBox(50, 60, 80, 40);
        BufferedImage screenshot = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        String description = "Submit Button";

        // When
        LocationConfirmationResult result = LocationConfirmationResult.incorrect(boundingBox, screenshot, description);

        // Then
        assertEquals(LocationConfirmationResult.UserChoice.INCORRECT, result.choice());
        assertFalse(result.isCorrect());
        assertTrue(result.isIncorrect());
        assertFalse(result.isInterrupted());
        assertEquals(boundingBox, result.boundingBox());
        assertEquals(screenshot, result.screenshot());
        assertEquals(description, result.elementDescription());
        assertEquals("Location marked as incorrect", result.message());
    }

    @Test
    void testInterruptedFactoryMethod() {
        // Given
        String description = "Cancel Button";

        // When
        LocationConfirmationResult result = LocationConfirmationResult.interrupted(description);

        // Then
        assertEquals(LocationConfirmationResult.UserChoice.INTERRUPTED, result.choice());
        assertFalse(result.isCorrect());
        assertFalse(result.isIncorrect());
        assertTrue(result.isInterrupted());
        assertNull(result.boundingBox());
        assertNull(result.screenshot());
        assertEquals(description, result.elementDescription());
        assertEquals("Confirmation interrupted by user", result.message());
    }

    @Test
    void testUserChoiceEnum() {
        // Verify all enum values exist
        LocationConfirmationResult.UserChoice[] choices = LocationConfirmationResult.UserChoice.values();
        assertEquals(3, choices.length);
        
        // Verify specific enum values
        assertNotNull(LocationConfirmationResult.UserChoice.valueOf("CORRECT"));
        assertNotNull(LocationConfirmationResult.UserChoice.valueOf("INCORRECT"));
        assertNotNull(LocationConfirmationResult.UserChoice.valueOf("INTERRUPTED"));
    }

    @Test
    void testJsonClassDescriptionAnnotation() {
        // Verify that the class has the JsonClassDescription annotation
        assertTrue(LocationConfirmationResult.class.isAnnotationPresent(JsonClassDescription.class));
        JsonClassDescription annotation = LocationConfirmationResult.class.getAnnotation(JsonClassDescription.class);
        assertEquals("Result of user confirmation for a located UI element", annotation.value());
    }

    @Test
    void testJsonFieldDescriptionAnnotations() {
        // Verify that all record components have JsonFieldDescription annotations
        var recordComponents = LocationConfirmationResult.class.getRecordComponents();
        assertNotNull(recordComponents);
        assertEquals(5, recordComponents.length);

        // Check that each component has the annotation
        for (var component : recordComponents) {
            assertTrue(component.isAnnotationPresent(JsonFieldDescription.class),
                    "Missing JsonFieldDescription on: " + component.getName());
        }
    }

    @Test
    void testConvenienceMethods() {
        // Test isCorrect()
        LocationConfirmationResult correctResult = LocationConfirmationResult.correct(
                new BoundingBox(0, 0, 10, 10),
                new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB),
                "Element"
        );
        assertTrue(correctResult.isCorrect());
        assertFalse(correctResult.isIncorrect());
        assertFalse(correctResult.isInterrupted());

        // Test isIncorrect()
        LocationConfirmationResult incorrectResult = LocationConfirmationResult.incorrect(
                new BoundingBox(0, 0, 10, 10),
                new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB),
                "Element"
        );
        assertFalse(incorrectResult.isCorrect());
        assertTrue(incorrectResult.isIncorrect());
        assertFalse(incorrectResult.isInterrupted());

        // Test isInterrupted()
        LocationConfirmationResult interruptedResult = LocationConfirmationResult.interrupted("Element");
        assertFalse(interruptedResult.isCorrect());
        assertFalse(interruptedResult.isIncorrect());
        assertTrue(interruptedResult.isInterrupted());
    }
}
