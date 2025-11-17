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
package org.tarik.ta.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.dto.*;
import org.tarik.ta.model.GenAiModel;
import org.tarik.ta.model.ModelFactory;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.rag.model.UiElement;
import org.tarik.ta.user_dialogs.*;
import org.tarik.ta.user_dialogs.UiElementInfoPopup.UiElementInfo;
import org.tarik.ta.user_dialogs.UiElementScreenshotCaptureWindow.UiElementCaptureResult;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserInteractionServiceImplTest {

    @Mock
    private UiElementRetriever mockRetriever;

    private UserInteractionService attendedService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        attendedService = new UserInteractionServiceImpl(mockRetriever);
    }

    @Test
    void testPromptUserToCreateNewElement_UserCancels() {
        // Given
        String pageName = "Login Page";
        String elementDescription = "Username field";

        try (MockedStatic<BoundingBoxCaptureNeededPopup> bbc = mockStatic(BoundingBoxCaptureNeededPopup.class);
             MockedStatic<UiElementScreenshotCaptureWindow> uiscw = mockStatic(UiElementScreenshotCaptureWindow.class)) {

            uiscw.when(() -> UiElementScreenshotCaptureWindow.displayAndGetResult(any(), any()))
                    .thenReturn(Optional.empty());

            // When
            NewElementCreationResult result = attendedService.promptUserToCreateNewElement(pageName, elementDescription);

            // Then
            assertFalse(result.success());
            assertNull(result.createdElement());
            assertTrue(result.interrupted());
            assertEquals("User cancelled screenshot capture", result.message());
            verify(mockRetriever, never()).storeElement(any());
        }
    }

    @Test
    void testPromptUserToCreateNewElement_Success() {
        // Given
        String pageName = "Login Page";
        String elementDescription = "Username field";
        BufferedImage mockImage = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        Rectangle boundingBox = new Rectangle(10, 10, 100, 50);

        UiElementCaptureResult captureResult = new UiElementCaptureResult(true, boundingBox, mockImage, mockImage);
        UiElementDescriptionResult descriptionResult = new UiElementDescriptionResult("username", "the username field", "top left", "login page");
        UiElementInfo uiElementInfo = new UiElementInfo("username", "the username field", "top left", "login page", false, false, List.of());

        try (MockedStatic<BoundingBoxCaptureNeededPopup> bbc = mockStatic(BoundingBoxCaptureNeededPopup.class);
             MockedStatic<UiElementScreenshotCaptureWindow> uiscw = mockStatic(UiElementScreenshotCaptureWindow.class);
             MockedStatic<ModelFactory> mf = mockStatic(ModelFactory.class);
             MockedStatic<AgentConfig> ac = mockStatic(AgentConfig.class);
             MockedStatic<UiElementInfoPopup> ueip = mockStatic(UiElementInfoPopup.class)) {

            uiscw.when(() -> UiElementScreenshotCaptureWindow.displayAndGetResult(any(), any()))
                    .thenReturn(Optional.of(captureResult));

            GenAiModel mockModel = mock(GenAiModel.class);
            when(mockModel.generateAndGetResponseAsObject(any(), any())).thenReturn(descriptionResult);
            mf.when(() -> ModelFactory.getModel(anyString(), any())).thenReturn(mockModel);

            ac.when(AgentConfig::getGuiGroundingModelName).thenReturn("gemini");
            ac.when(AgentConfig::getGuiGroundingModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);

            ueip.when(() -> UiElementInfoPopup.displayAndGetUpdatedElementInfo(any(), any()))
                    .thenReturn(Optional.of(uiElementInfo));

            // When
            NewElementCreationResult result = attendedService.promptUserToCreateNewElement(pageName, elementDescription);

            // Then
            assertTrue(result.success());
            assertNotNull(result.createdElement());
            assertFalse(result.interrupted());
            assertEquals("username", result.createdElement().name());
            verify(mockRetriever, times(1)).storeElement(any(UiElement.class));
        }
    }

    @Test
    void testPromptUserToCreateNewElement_CancellationRequested() {
        // Given
        attendedService.requestCancellation();
        String pageName = "Login Page";
        String elementDescription = "Username field";

        // When
        NewElementCreationResult result = attendedService.promptUserToCreateNewElement(pageName, elementDescription);

        // Then
        assertFalse(result.success());
        assertTrue(result.interrupted());
        assertEquals("Cancellation requested", result.message());
        verify(mockRetriever, never()).storeElement(any());
    }

    @Test
    void testPromptUserToRefineExistingElements_DoneImmediately() {
        // Given
        List<UiElement> elements = List.of();
        String context = "Test context";

        try (MockedStatic<UiElementRefinementPopup> uerp = mockStatic(UiElementRefinementPopup.class)) {
            uerp.when(() -> UiElementRefinementPopup.displayAndGetChoice(any(), any(), any()))
                    .thenReturn(Optional.of(new ElementRefinementOperation(ElementRefinementOperation.Operation.DONE, null)));

            // When
            ElementRefinementResult result = attendedService.promptUserToRefineExistingElements(elements, context);

            // Then
            assertTrue(result.success());
            assertEquals(0, result.modificationCount());
        }
    }

    @Test
    void testPromptUserToRefineExistingElements_CancellationRequested() {
        // Given
        attendedService.requestCancellation();
        List<UiElement> elements = List.of();
        String context = "Test context";

        // When
        ElementRefinementResult result = attendedService.promptUserToRefineExistingElements(elements, context);

        // Then
        assertFalse(result.success());
        assertTrue(result.interrupted());
    }

    @Test
    void testConfirmLocatedElement_UserInterrupts() {
        // Given
        String elementDescription = "Submit button";
        BoundingBox boundingBox = new BoundingBox(10, 20, 110, 70);
        BufferedImage screenshot = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);

        try (MockedStatic<LocatedElementConfirmationDialog> lecd = mockStatic(LocatedElementConfirmationDialog.class)) {
            lecd.when(() -> LocatedElementConfirmationDialog.displayAndGetUserChoice(any(), any(), any(), any(), any()))
                    .thenReturn(LocatedElementConfirmationDialog.UserChoice.INTERRUPTED);
            // When
            LocationConfirmationResult result = attendedService.confirmLocatedElement(elementDescription, boundingBox, screenshot);

            // Then
            assertTrue(result.isInterrupted());
            assertEquals(elementDescription, result.elementDescription());
        }
    }

    @Test
    void testConfirmLocatedElement_CancellationRequested() {
        // Given
        attendedService.requestCancellation();
        String elementDescription = "Submit button";
        BoundingBox boundingBox = new BoundingBox(10, 20, 110, 70);
        BufferedImage screenshot = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);

        // When
        LocationConfirmationResult result = attendedService.confirmLocatedElement(elementDescription, boundingBox, screenshot);

        // Then
        assertTrue(result.isInterrupted());
        assertEquals("Cancellation requested, skipping location confirmation", result.message());
    }

    @Test
    void testPromptUserForNextAction_UserTerminates() {
        // Given
        String reason = "Element not visible";
        try (MockedStatic<NextActionPopup> nap = mockStatic(NextActionPopup.class)) {
            nap.when(() -> NextActionPopup.displayAndGetUserDecision(any(), any()))
                    .thenReturn(NextActionPopup.UserDecision.TERMINATE);
            // When
            NextActionResult result = attendedService.promptUserForNextAction(reason);

            // Then
            assertTrue(result.shouldTerminate());
        }
    }

    @Test
    void testPromptUserForNextAction_CancellationRequested() {
        // Given
        attendedService.requestCancellation();
        String reason = "Timeout";

        // When
        NextActionResult result = attendedService.promptUserForNextAction(reason);

        // Then
        assertFalse(result.shouldCreateNewElement());
        assertFalse(result.shouldRetrySearch());
        // Based on NextActionResult implementation, failure does not mean terminate
        assertFalse(result.shouldTerminate());
        assertEquals("Cancellation requested", result.message());
    }

    @Test
    void testDisplayInformationalPopup_NoException() {
        // Given
        String title = "Test Title";
        String message = "Test Message";
        BufferedImage screenshot = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

        try (MockedStatic<JOptionPane> jo = mockStatic(JOptionPane.class)) {
            // When/Then - should not throw exception
            assertDoesNotThrow(() -> attendedService.displayInformationalPopup(
                    title, message, screenshot, UserInteractionService.PopupType.INFO));
            jo.verify(() -> JOptionPane.showMessageDialog(any(), any(), eq(title), anyInt()));
        }
    }

    @Test
    void testDisplayInformationalPopup_CancellationRequested() {
        // Given
        attendedService.requestCancellation();
        String title = "Test Title";
        String message = "Test Message";

        try (MockedStatic<JOptionPane> jo = mockStatic(JOptionPane.class)) {
            // When/Then - should not throw exception even when cancelled
            assertDoesNotThrow(() -> attendedService.displayInformationalPopup(
                    title, message, null, UserInteractionService.PopupType.WARNING));
            jo.verifyNoInteractions();
        }
    }

    @Test
    void testDisplayVerificationFailure_NoException() {
        // Given
        String verificationDescription = "Button should be visible";
        String expectedState = "Button visible";
        String actualState = "Button hidden";
        String failureReason = "Element not found in DOM";
        BufferedImage screenshot = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

        try (MockedStatic<JOptionPane> jo = mockStatic(JOptionPane.class)) {
            // When/Then - should not throw exception
            assertDoesNotThrow(() -> attendedService.displayVerificationFailure(
                    verificationDescription, expectedState, actualState, failureReason, screenshot));
            jo.verify(() -> JOptionPane.showMessageDialog(any(), any(), anyString(), anyInt()));
        }
    }

    @Test
    void testMultipleCancellationRequests() {
        // Given
        assertFalse(attendedService.isCancellationRequested());

        // When
        attendedService.requestCancellation();
        attendedService.requestCancellation();
        attendedService.requestCancellation();

        // Then - should still be true after multiple requests
        assertTrue(attendedService.isCancellationRequested());
    }

    @Test
    void testPopupTypeEnum() {
        // Verify all popup types are available
        assertEquals(3, UserInteractionService.PopupType.values().length);
        assertNotNull(UserInteractionService.PopupType.valueOf("INFO"));
        assertNotNull(UserInteractionService.PopupType.valueOf("WARNING"));
        assertNotNull(UserInteractionService.PopupType.valueOf("ERROR"));
    }
}
