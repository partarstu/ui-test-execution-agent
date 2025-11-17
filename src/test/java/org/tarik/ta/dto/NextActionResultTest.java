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

import static org.junit.jupiter.api.Assertions.*;

class NextActionResultTest {

    @Test
    void testCreateNewElementFactoryMethod() {
        // When
        NextActionResult result = NextActionResult.createNewElement();

        // Then
        assertEquals(NextActionResult.UserDecision.CREATE_NEW_ELEMENT, result.decision());
        assertTrue(result.shouldCreateNewElement());
        assertFalse(result.shouldRetrySearch());
        assertFalse(result.shouldTerminate());
        assertEquals("User chose to create a new element", result.message());
    }

    @Test
    void testRetrySearchFactoryMethod() {
        // When
        NextActionResult result = NextActionResult.retrySearch();

        // Then
        assertEquals(NextActionResult.UserDecision.RETRY_SEARCH, result.decision());
        assertFalse(result.shouldCreateNewElement());
        assertTrue(result.shouldRetrySearch());
        assertFalse(result.shouldTerminate());
        assertEquals("User chose to retry the search", result.message());
    }

    @Test
    void testTerminateFactoryMethod() {
        // When
        NextActionResult result = NextActionResult.terminate();

        // Then
        assertEquals(NextActionResult.UserDecision.TERMINATE, result.decision());
        assertFalse(result.shouldCreateNewElement());
        assertFalse(result.shouldRetrySearch());
        assertTrue(result.shouldTerminate());
        assertEquals("User chose to terminate execution", result.message());
    }

    @Test
    void testUserDecisionEnum() {
        // Verify all enum values exist
        NextActionResult.UserDecision[] decisions = NextActionResult.UserDecision.values();
        assertEquals(3, decisions.length);
        
        // Verify specific enum values
        assertNotNull(NextActionResult.UserDecision.valueOf("CREATE_NEW_ELEMENT"));
        assertNotNull(NextActionResult.UserDecision.valueOf("RETRY_SEARCH"));
        assertNotNull(NextActionResult.UserDecision.valueOf("TERMINATE"));
    }

    @Test
    void testJsonClassDescriptionAnnotation() {
        // Verify that the class has the JsonClassDescription annotation
        assertTrue(NextActionResult.class.isAnnotationPresent(JsonClassDescription.class));
        JsonClassDescription annotation = NextActionResult.class.getAnnotation(JsonClassDescription.class);
        assertEquals("Result of user decision on next action when element location fails", annotation.value());
    }

    @Test
    void testJsonFieldDescriptionAnnotations() {
        // Verify that all record components have JsonFieldDescription annotations
        var recordComponents = NextActionResult.class.getRecordComponents();
        assertNotNull(recordComponents);
        assertEquals(2, recordComponents.length);

        // Check that each component has the annotation
        for (var component : recordComponents) {
            assertTrue(component.isAnnotationPresent(JsonFieldDescription.class),
                    "Missing JsonFieldDescription on: " + component.getName());
        }
    }

    @Test
    void testConvenienceMethods() {
        // Test shouldCreateNewElement()
        NextActionResult createResult = NextActionResult.createNewElement();
        assertTrue(createResult.shouldCreateNewElement());
        assertFalse(createResult.shouldRetrySearch());
        assertFalse(createResult.shouldTerminate());

        // Test shouldRetrySearch()
        NextActionResult retryResult = NextActionResult.retrySearch();
        assertFalse(retryResult.shouldCreateNewElement());
        assertTrue(retryResult.shouldRetrySearch());
        assertFalse(retryResult.shouldTerminate());

        // Test shouldTerminate()
        NextActionResult terminateResult = NextActionResult.terminate();
        assertFalse(terminateResult.shouldCreateNewElement());
        assertFalse(terminateResult.shouldRetrySearch());
        assertTrue(terminateResult.shouldTerminate());
    }

    @Test
    void testFactoryMethods() {
        // When
        NextActionResult createResult = NextActionResult.createNewElement();
        NextActionResult retryResult = NextActionResult.retrySearch();
        NextActionResult terminateResult = NextActionResult.terminate();

        // Then
        assertEquals(NextActionResult.UserDecision.CREATE_NEW_ELEMENT, createResult.decision());
        assertEquals(NextActionResult.UserDecision.RETRY_SEARCH, retryResult.decision());
        assertEquals(NextActionResult.UserDecision.TERMINATE, terminateResult.decision());
    }
}
