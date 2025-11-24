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
import org.tarik.ta.rag.model.UiElement;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NewElementCreationResultTest {

    @Test
    void testSuccessFactoryMethod() {
        // Given
        UiElement element = createTestElement();
        BoundingBox boundingBox = new BoundingBox(10, 20, 110, 70);

        // When
        NewElementCreationResult result = NewElementCreationResult.success(element, boundingBox);

        // Then
        assertTrue(result.success());
        assertFalse(result.interrupted());
        assertNotNull(result.createdElement());
        assertEquals(element, result.createdElement());
        assertEquals(boundingBox, result.boundingBox());
        assertEquals("Element created successfully", result.message());
    }

    @Test
    void testInterruptedFactoryMethod() {
        // When
        NewElementCreationResult result = NewElementCreationResult.interrupted("User closed dialog");

        // Then
        assertFalse(result.success());
        assertTrue(result.interrupted());
        assertNull(result.createdElement());
        assertNull(result.boundingBox());
        assertEquals("User closed dialog", result.message());
    }

    @Test
    void testFailureFactoryMethod() {
        // When
        NewElementCreationResult result = NewElementCreationResult.failure("Invalid element data");

        // Then
        assertFalse(result.success());
        assertFalse(result.interrupted());
        assertNull(result.createdElement());
        assertEquals("Invalid element data", result.message());
    }

    @Test
    void testJsonClassDescriptionAnnotation() {
        // Verify that the class has the JsonClassDescription annotation
        assertTrue(NewElementCreationResult.class.isAnnotationPresent(JsonClassDescription.class));
        JsonClassDescription annotation = NewElementCreationResult.class.getAnnotation(JsonClassDescription.class);
        assertEquals("Result of creating a new UI element through user interaction", annotation.value());
    }

    @Test
    void testJsonFieldDescriptionAnnotations() {
        // Verify that all record components have JsonFieldDescription annotations
        var recordComponents = NewElementCreationResult.class.getRecordComponents();
        assertNotNull(recordComponents);
        assertTrue(recordComponents.length > 0);

        // Check specific fields
        verifyFieldHasDescription("success", "Whether the element was successfully created");
        verifyFieldHasDescription("createdElement", "The newly created UI element, or null if creation was interrupted");
        verifyFieldHasDescription("boundingBox", "The bounding box of the element on the screen");
        verifyFieldHasDescription("interrupted", "Whether the user interrupted the creation process");
        verifyFieldHasDescription("message", "Additional message or error details");
    }

    private void verifyFieldHasDescription(String fieldName, String expectedDescription) {
        try {
            var component = findRecordComponent(fieldName);
            assertNotNull(component, "Record component not found: " + fieldName);
            assertTrue(component.isAnnotationPresent(JsonFieldDescription.class),
                    "Missing JsonFieldDescription on: " + fieldName);
            JsonFieldDescription annotation = component.getAnnotation(JsonFieldDescription.class);
            assertEquals(expectedDescription, annotation.value());
        } catch (Exception e) {
            fail("Failed to verify field description for: " + fieldName + " - " + e.getMessage());
        }
    }

    private java.lang.reflect.RecordComponent findRecordComponent(String name) {
        for (var component : NewElementCreationResult.class.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component;
            }
        }
        return null;
    }

    private UiElement createTestElement() {
        return new UiElement(
                UUID.randomUUID(),
                "Test Element",
                "A test UI element",
                "Near the top button",
                "Login Page",
                UiElement.Screenshot.fromBufferedImage(
                        new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB), "png"),
                false,
                List.of()
        );
    }
}