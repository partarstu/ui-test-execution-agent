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

class NewElementCreationResultTest {

    @Test
    void testSuccessFactoryMethod() {
        // When
        NewElementCreationResult result = NewElementCreationResult.asSuccess();

        // Then
        assertTrue(result.success());
        assertFalse(result.interrupted());
        assertEquals("Element created successfully", result.message());
    }

    @Test
    void testInterruptedFactoryMethod() {
        // When
        NewElementCreationResult result = NewElementCreationResult.interrupted("User closed dialog");

        // Then
        assertFalse(result.success());
        assertTrue(result.interrupted());
        assertEquals("User closed dialog", result.message());
    }

    @Test
    void testFailureFactoryMethod() {
        // When
        NewElementCreationResult result = NewElementCreationResult.failure("Invalid element data");

        // Then
        assertFalse(result.success());
        assertFalse(result.interrupted());
        assertEquals("Invalid element data", result.message());
    }

    @Test
    void testDescriptionAnnotation() {
        // Verify that the class has the Description annotation
        assertTrue(NewElementCreationResult.class.isAnnotationPresent(Description.class));
        Description annotation = NewElementCreationResult.class.getAnnotation(Description.class);
        assertEquals("Result of creating a new UI element through user interaction", annotation.value()[0]);
    }

    @Test
    void testFieldDescriptionAnnotations() {
        // Verify that all record components have Description annotations
        var recordComponents = NewElementCreationResult.class.getRecordComponents();
        assertNotNull(recordComponents);
        assertTrue(recordComponents.length > 0);

        // Check specific fields
        verifyFieldHasDescription("success", "Whether the element was successfully created");
        verifyFieldHasDescription("interrupted", "Whether the user interrupted the creation process");
        verifyFieldHasDescription("message", "Additional message or error details");
    }

    private void verifyFieldHasDescription(String fieldName, String expectedDescription) {
        try {
            var accessor = NewElementCreationResult.class.getMethod(fieldName);
            assertNotNull(accessor, "Accessor method not found: " + fieldName);
            assertTrue(accessor.isAnnotationPresent(Description.class),
                    "Missing Description on: " + fieldName);
            Description annotation = accessor.getAnnotation(Description.class);
            assertEquals(expectedDescription, annotation.value()[0]);
        } catch (Exception e) {
            fail("Failed to verify field description for: " + fieldName + " - " + e.getMessage());
        }
    }
}