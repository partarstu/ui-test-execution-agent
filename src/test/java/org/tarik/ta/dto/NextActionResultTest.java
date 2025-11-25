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

class NextActionResultTest {

    @Test
    void testCreateNewElementFactoryMethod() {
        // When
        NextActionResult result = NextActionResult.createNewElement();

        // Then
        assertEquals(NextActionResult.UserDecision.CREATE_NEW_ELEMENT, result.decision());
        assertEquals("User chose to create a new element", result.message());
    }

    @Test
    void testRefineExistingElementFactoryMethod() {
        // When
        NextActionResult result = NextActionResult.refineExistingElement();

        // Then
        assertEquals(NextActionResult.UserDecision.REFINE_EXISTING_ELEMENTS, result.decision());
        assertEquals("User chose to refine existing elements", result.message());
    }

    @Test
    void testRetrySearchFactoryMethod() {
        // When
        NextActionResult result = NextActionResult.retrySearch();

        // Then
        assertEquals(NextActionResult.UserDecision.RETRY_SEARCH, result.decision());
        assertEquals("User chose to retry the UI element search", result.message());
    }

    @Test
    void testTerminateFactoryMethod() {
        // When
        NextActionResult result = NextActionResult.terminate();

        // Then
        assertEquals(NextActionResult.UserDecision.TERMINATE, result.decision());
        assertEquals("User chose to terminate execution", result.message());
    }

    @Test
    void testUserDecisionEnum() {
        // Verify all enum values exist
        NextActionResult.UserDecision[] decisions = NextActionResult.UserDecision.values();
        assertEquals(4, decisions.length);
        
        // Verify specific enum values
        assertNotNull(NextActionResult.UserDecision.valueOf("CREATE_NEW_ELEMENT"));
        assertNotNull(NextActionResult.UserDecision.valueOf("REFINE_EXISTING_ELEMENTS"));
        assertNotNull(NextActionResult.UserDecision.valueOf("RETRY_SEARCH"));
        assertNotNull(NextActionResult.UserDecision.valueOf("TERMINATE"));
    }

    @Test
    void testDescriptionAnnotation() {
        // Verify that the class has the Description annotation
        assertTrue(NextActionResult.class.isAnnotationPresent(Description.class));
        Description annotation = NextActionResult.class.getAnnotation(Description.class);
        assertEquals("Result of user decision on next action when element location fails", annotation.value()[0]);
    }

    @Test
    void testFieldDescriptionAnnotations() {
        // Verify that all record components have Description annotations
        var recordComponents = NextActionResult.class.getRecordComponents();
        assertNotNull(recordComponents);
        assertEquals(2, recordComponents.length);

        // Check that each component's accessor method has the annotation
        for (var component : recordComponents) {
            try {
                var accessor = NextActionResult.class.getMethod(component.getName());
                assertTrue(accessor.isAnnotationPresent(Description.class),
                        "Missing Description on: " + component.getName());
            } catch (NoSuchMethodException e) {
                fail("Failed to find accessor method for: " + component.getName());
            }
        }
    }

    @Test
    void testFactoryMethods() {
        // When
        NextActionResult createResult = NextActionResult.createNewElement();
        NextActionResult refineResult = NextActionResult.refineExistingElement();
        NextActionResult retryResult = NextActionResult.retrySearch();
        NextActionResult terminateResult = NextActionResult.terminate();

        // Then
        assertEquals(NextActionResult.UserDecision.CREATE_NEW_ELEMENT, createResult.decision());
        assertEquals(NextActionResult.UserDecision.REFINE_EXISTING_ELEMENTS, refineResult.decision());
        assertEquals(NextActionResult.UserDecision.RETRY_SEARCH, retryResult.decision());
        assertEquals(NextActionResult.UserDecision.TERMINATE, terminateResult.decision());
    }
}