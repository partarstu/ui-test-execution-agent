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
import org.tarik.ta.rag.model.UiElement;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ElementRefinementResultTest {

    @Test
    void testSuccessFactoryMethod() {
        // Given
        List<UiElement> updated = List.of(createTestElement("Updated1"), createTestElement("Updated2"));
        List<UiElement> deleted = List.of(createTestElement("Deleted1"));

        // When
        ElementRefinementResult result = ElementRefinementResult.success(updated, deleted);

        // Then
        assertTrue(result.success());
        assertFalse(result.interrupted());
        assertEquals(3, result.modificationCount());
        assertTrue(result.message().contains("2 updated"));
        assertTrue(result.message().contains("1 deleted"));
    }

    @Test
    void testSuccessWithNoModifications() {
        // Given
        List<UiElement> empty = List.of();

        // When
        ElementRefinementResult result = ElementRefinementResult.success(empty, empty);

        // Then
        assertTrue(result.success());
        assertFalse(result.interrupted());
        assertEquals(0, result.modificationCount());
    }

    @Test
    void testInterruptedFactoryMethod() {
        // When
        String reason = "dummy reason";
        ElementRefinementResult result = ElementRefinementResult.wasInterrupted(reason);

        // Then
        assertFalse(result.success());
        assertTrue(result.interrupted());
        assertEquals(0, result.modificationCount());
        assertEquals(reason, result.message());
    }

    @Test
    void testNoChangesFactoryMethod() {
        // When
        ElementRefinementResult result = ElementRefinementResult.noChanges();

        // Then
        assertTrue(result.success());
        assertFalse(result.interrupted());
        assertEquals(0, result.modificationCount());
        assertEquals("Refinement completed with no changes", result.message());
    }

    @Test
    void testDescriptionAnnotation() {
        // Verify that the class has the Description annotation
        assertTrue(ElementRefinementResult.class.isAnnotationPresent(Description.class));
        Description annotation = ElementRefinementResult.class.getAnnotation(Description.class);
        assertEquals("Result of refining existing UI elements through user interaction", annotation.value()[0]);
    }

    @Test
    void testFieldDescriptionAnnotations() {
        // Verify that all record components have Description annotations
        var recordComponents = ElementRefinementResult.class.getRecordComponents();
        assertNotNull(recordComponents);
        assertEquals(4, recordComponents.length);

        // Check that each component's accessor method has the annotation
        for (var component : recordComponents) {
            try {
                var accessor = ElementRefinementResult.class.getMethod(component.getName());
                assertTrue(accessor.isAnnotationPresent(Description.class),
                        "Missing Description on: " + component.getName());
            } catch (NoSuchMethodException e) {
                fail("Failed to find accessor method for: " + component.getName());
            }
        }
    }

    @Test
    void testModificationCountCalculation() {
        // Given
        List<UiElement> updated = List.of(
                createTestElement("U1"),
                createTestElement("U2"),
                createTestElement("U3")
        );
        List<UiElement> deleted = List.of(
                createTestElement("D1"),
                createTestElement("D2")
        );

        // When
        ElementRefinementResult result = ElementRefinementResult.success(updated, deleted);

        // Then
        assertEquals(5, result.modificationCount());
        assertEquals(updated.size() + deleted.size(), result.modificationCount());
    }

    private UiElement createTestElement(String name) {
        return new UiElement(
                UUID.randomUUID(),
                name,
                "Description of " + name,
                "Location details",
                "Test Page",
                UiElement.Screenshot.fromBufferedImage(
                        new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB), "png"),
                false,
                List.of()
        );
    }
}
