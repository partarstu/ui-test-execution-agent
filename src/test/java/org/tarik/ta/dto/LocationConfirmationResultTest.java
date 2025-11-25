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
import dev.langchain4j.model.output.structured.Description;

import static org.junit.jupiter.api.Assertions.*;

class LocationConfirmationResultTest {

    @Test
    void testCorrectFactoryMethod() {
        // When
        LocationConfirmationResult result = LocationConfirmationResult.correct();

        // Then
        assertEquals(LocationConfirmationResult.UserChoice.CORRECT, result.choice());
        assertEquals("Location confirmed as correct", result.message());
    }

    @Test
    void testIncorrectFactoryMethod() {
        // When
        LocationConfirmationResult result = LocationConfirmationResult.incorrect();

        // Then
        assertEquals(LocationConfirmationResult.UserChoice.INCORRECT, result.choice());
        assertEquals("Location marked as incorrect", result.message());
    }

    @Test
    void testInterruptedFactoryMethod() {
        // When
        LocationConfirmationResult result = LocationConfirmationResult.interrupted();

        // Then
        assertEquals(LocationConfirmationResult.UserChoice.INTERRUPTED, result.choice());
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
    void testDescriptionAnnotation() {
        // Verify that the class has the Description annotation
        assertTrue(LocationConfirmationResult.class.isAnnotationPresent(Description.class));
        Description annotation = LocationConfirmationResult.class.getAnnotation(Description.class);
        assertEquals("Result of user confirmation for a located UI element", annotation.value()[0]);
    }

    @Test
    void testFieldDescriptionAnnotations() {
        // Verify that all record components have Description annotations
        var recordComponents = LocationConfirmationResult.class.getRecordComponents();
        assertNotNull(recordComponents);
        assertEquals(2, recordComponents.length);

        // Check that each component's accessor method has the annotation
        for (var component : recordComponents) {
            try {
                var accessor = LocationConfirmationResult.class.getMethod(component.getName());
                assertTrue(accessor.isAnnotationPresent(Description.class),
                        "Missing Description on: " + component.getName());
            } catch (NoSuchMethodException e) {
                fail("Failed to find accessor method for: " + component.getName());
            }
        }
    }
}
