/*
 * Copyright (c) 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 */
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.dto.*;
import org.tarik.ta.exceptions.ToolExecutionException;
import org.tarik.ta.prompts.ElementDescriptionPrompt;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.rag.UiElementRetriever.RetrievedUiElementItem;
import org.tarik.ta.rag.model.UiElement;
import org.tarik.ta.user_dialogs.*;
import org.tarik.ta.user_dialogs.UiElementInfoPopup.UiElementInfo;
import org.tarik.ta.user_dialogs.UiElementScreenshotCaptureWindow.UiElementCaptureResult;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static java.lang.String.format;
import static java.util.Comparator.comparingDouble;
import static java.util.UUID.randomUUID;
import static org.tarik.ta.AgentConfig.getGuiGroundingModelName;
import static org.tarik.ta.AgentConfig.getGuiGroundingModelProvider;
import static org.tarik.ta.dto.ElementRefinementOperation.Operation.DONE;
import static org.tarik.ta.error.ErrorCategory.*;
import static org.tarik.ta.model.ModelFactory.getModel;
import static org.tarik.ta.rag.model.UiElement.Screenshot.fromBufferedImage;
import static org.tarik.ta.utils.CommonUtils.*;

/**
 * Default implementation of UserInteractionService that coordinates UI dialogs.
 * This service manages the lifecycle of user dialogs and handles user
 * responses,
 * converting them to structured result objects.
 */
public class UserInteractionTools extends AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(UserInteractionTools.class);
    private final UiElementRetriever uiElementRetriever;
    private static final String BOUNDING_BOX_COLOR_NAME = AgentConfig.getElementBoundingBoxColorName();
    private static final Color BOUNDING_BOX_COLOR = getColorByName(BOUNDING_BOX_COLOR_NAME);
    private static final int USER_DIALOG_DISMISS_DELAY_MILLIS = 2000;

    /**
     * Constructs a new UserInteractionServiceImpl.
     *
     * @param uiElementRetriever The retriever for persisting and querying UI
     *                           elements
     */
    public UserInteractionTools(UiElementRetriever uiElementRetriever) {
        this.uiElementRetriever = uiElementRetriever;
    }

    @Tool("Prompts the user to create a new UI element. Use this tool when you need to create a new " +
            "UI element which is not present in the database")
    public NewElementCreationResult promptUserToCreateNewElement(
            @P("Initial description or hint about the element") String elementDescription) {
        if (isBlank(elementDescription)) {
            throw new ToolExecutionException("Element description cannot be empty", TRANSIENT_TOOL_ERROR);
        }

        try {
            LOG.info("Starting new element creation workflow for: {}", elementDescription);

            // Step 1: Inform user that bounding box capture is needed
            BoundingBoxCaptureNeededPopup.display(null);
            sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);

            // Step 2: Capture bounding box
            LOG.debug("Prompting user to capture element screenshot");
            var captureResult = UiElementScreenshotCaptureWindow.displayAndGetResult(null, BOUNDING_BOX_COLOR);
            if (captureResult.isEmpty()) {
                LOG.info("User cancelled screenshot capture");
                throw new ToolExecutionException("User cancelled screenshot capture", USER_INTERRUPTION);
            }

            var capture = captureResult.get();

            // Step 3: Prompt the model to suggest the new element info based on the element
            // position on the screenshot
            var describedUiElement = getUiElementInfoSuggestionFromModel(elementDescription, capture);

            // Step 4: Prompt user to refine the suggested by the model element info
            var uiElementInfo = new UiElementInfo(describedUiElement.name(), describedUiElement.ownDescription(),
                    describedUiElement.locationDescription(), describedUiElement.pageSummary(), false, false, List.of());
            return UiElementInfoPopup.displayAndGetUpdatedElementInfo(null, uiElementInfo)
                    .map(clarifiedByUserElement -> {
                        // Step 5: Persist the element
                        LOG.debug("Persisting new element to database");
                        saveNewUiElementIntoDb(capture.elementScreenshot(), clarifiedByUserElement);
                        LOG.info("Successfully created new element: {}", clarifiedByUserElement.name());
                        return NewElementCreationResult.asSuccess();
                    })
                    .orElseThrow(() -> new ToolExecutionException("User interrupted element creation", USER_INTERRUPTION));
        } catch (Exception e) {
            throw rethrowAsToolException(e, "creating a new UI element");
        }
    }

    @Tool("Prompts the user to refine existing UI elements.")
    public ElementRefinementResult promptUserToRefineExistingElements(
            @P("Initial description or hint about the element") String elementDescription) {
        List<UiElement> elementsToRefine = uiElementRetriever.retrieveUiElements(elementDescription, AgentConfig.getRetrieverTopN(),
                        AgentConfig.getElementRetrievalMinGeneralScore())
                .stream()
                .sorted(comparingDouble(RetrievedUiElementItem::mainScore).reversed())
                .map(RetrievedUiElementItem::element)
                .toList();

        if (elementsToRefine.isEmpty()) {
            throw new ToolExecutionException("No candidate elements found for refinement", TRANSIENT_TOOL_ERROR);
        }

        try {
            Set<UiElement> updatedElementsCollector = new HashSet<>();
            List<UiElement> deletedElementsCollector = new ArrayList<>();
            LOG.info("Starting element refinement workflow with {} candidates", elementsToRefine.size());
            boolean changesMade = false;
            var message = "Please refine the following elements which are the best matches to %s".formatted(elementDescription);
            while (true) {
                var choiceOptional = UiElementRefinementPopup.displayAndGetChoice(null, message, elementsToRefine);
                if (choiceOptional.isEmpty()) {
                    LOG.info("User interrupted element refinement");
                    throw new ToolExecutionException("User interrupted element refinement", USER_INTERRUPTION);
                }

                ElementRefinementOperation operation = choiceOptional.get();
                if (operation.operation() == DONE) {
                    LOG.info("User finished element refinement");
                    break;
                }

                changesMade = true;
                UUID elementId = operation.elementId();
                switch (operation.operation()) {
                    case UPDATE_SCREENSHOT -> updateElementScreenshot(elementsToRefine, elementId).ifPresent(updatedElementsCollector::add);
                    case UPDATE_ELEMENT -> updateElementInfo(elementsToRefine, elementId).ifPresent(updatedElementsCollector::add);
                    case DELETE_ELEMENT -> {
                        var deletedElement = deleteElement(elementsToRefine, elementId);
                        deletedElementsCollector.add(deletedElement);
                    }
                    default -> throw new IllegalStateException("Unexpected value for element operation type: " + operation.operation());
                }
                elementsToRefine = elementsToRefine.stream()
                        .filter(elementToRefine -> !deletedElementsCollector.contains(elementToRefine))
                        .map(elementToRefine -> updatedElementsCollector.stream()
                                .filter(updated -> elementToRefine.uuid().equals(updated.uuid()))
                                .findFirst()
                                .orElse(elementToRefine))
                        .toList();
            }

            LOG.info("Element refinement workflow completed");
            return changesMade
                    ? ElementRefinementResult.success(List.copyOf(updatedElementsCollector), deletedElementsCollector)
                    : ElementRefinementResult.noChanges();
        } catch (Exception e) {
            throw rethrowAsToolException(e, "refinement most matching elements");
        }
    }

    @Tool("Asks the user to confirm that a located element is correct. Use this tool when you have located an element but want to ensure " +
            "it is the correct one before proceeding.")
    public LocationConfirmationResult confirmLocatedElement(
            @P("Description of the element being confirmed") String elementDescription,
            @P("The bounding box of the located element") BoundingBox boundingBox) {
        try {
            if (isBlank(elementDescription)) {
                throw new ToolExecutionException("Element description cannot be empty", TRANSIENT_TOOL_ERROR);
            }
            if (boundingBox == null) {
                throw new ToolExecutionException("Bounding box cannot be null", TRANSIENT_TOOL_ERROR);
            }

            var screenshot = captureScreen();
            Rectangle boundingBoxRectangle = getPhysicalBoundingBox(getBoundingBoxRectangle(boundingBox));
            LOG.info("Prompting user to confirm located element: {}", elementDescription);
            var choice =
                    LocatedElementConfirmationDialog.displayAndGetUserChoice(null, screenshot, boundingBoxRectangle, BOUNDING_BOX_COLOR,
                            elementDescription);

            return switch (choice) {
                case CORRECT -> {
                    LOG.info("User confirmed element location as correct, returning the result after {} millis",
                            USER_DIALOG_DISMISS_DELAY_MILLIS);
                    sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);
                    yield LocationConfirmationResult.correct();
                }
                case INCORRECT -> {
                    LOG.info("User marked element location as incorrect, returning the result immediately.");
                    yield LocationConfirmationResult.incorrect();
                }
                case INTERRUPTED -> {
                    LOG.info("User interrupted location confirmation, returning the result immediately.");
                    throw new ToolExecutionException("User interrupted location confirmation", USER_INTERRUPTION);
                }
            };
        } catch (Exception e) {
            throw rethrowAsToolException(e, "confirming located element correctness");
        }
    }

    @Tool("Prompts the user to decide on the next action.")
    public NextActionResult promptUserForNextAction(
            @P("Description of the reason of prompting the user") String reason) {
        try {
            if (isBlank(reason)) {
                throw new ToolExecutionException("Reason cannot be empty", TRANSIENT_TOOL_ERROR);
            }

            LOG.info("Prompting user for next action, root cause: {}", reason);
            reason = "%s\nPlease choose one of the following actions you'd like to do:".formatted(reason);
            var decision = NextActionPopup.displayAndGetUserDecision(null, reason);
            return switch (decision) {
                case CREATE_NEW_ELEMENT -> {
                    LOG.info("User chose to create a new element");
                    yield NextActionResult.createNewElement();
                }
                case REFINE_EXISTING_ELEMENT -> {
                    LOG.info("User chose to refine an existing element");
                    yield NextActionResult.refineExistingElement();
                }
                case RETRY_SEARCH -> {
                    LOG.info("User chose to retry search");
                    yield NextActionResult.retrySearch();
                }
                case TERMINATE -> {
                    LOG.info("User chose to terminate");
                    throw new ToolExecutionException("User chose to terminate", USER_INTERRUPTION);
                }
            };
        } catch (Exception e) {
            throw rethrowAsToolException(e, "prompting for next action");
        }
    }

    @Tool("Displays an informational popup to the user. Use this tool when you need to simply show information, a warning, or an error message to the user.")
    public String displayInformationalPopup(
            @P("The title of the popup window") String title,
            @P("The message content to display") String message,
            @P("The severity level of the popup (INFO, WARNING, ERROR)") PopupType popupType) {
        displayInformationalPopup(title, message, null, popupType);
        return "Popup displayed successfully.";
    }

    public void displayInformationalPopup(String title, String message, BufferedImage screenshot, PopupType popupType) {
        try {
            LOG.debug("Displaying informational popup: {}", title);
            int messageType = switch (popupType) {
                case INFO -> JOptionPane.INFORMATION_MESSAGE;
                case WARNING -> JOptionPane.WARNING_MESSAGE;
                case ERROR -> JOptionPane.ERROR_MESSAGE;
            };

            if (screenshot != null) {
                // Create a panel with the message and screenshot
                JPanel panel = new JPanel(new BorderLayout());
                panel.add(new JLabel(message), BorderLayout.NORTH);
                panel.add(new JLabel(new ImageIcon(screenshot)), BorderLayout.CENTER);
                JOptionPane.showMessageDialog(null, panel, title, messageType);
            } else {
                JOptionPane.showMessageDialog(null, message, title, messageType);
            }
        } catch (Exception e) {
            throw rethrowAsToolException(e, "displaying informational popup");
        }
    }

    @Tool("Informs the user .")
    public void displayVerificationFailure(
            @P("Description of the verification") String verificationDescription,
            @P("Expected state") String expectedState,
            @P("Actual state") String actualState,
            @P("Reason for failure") String failureReason) {
        try {
            LOG.info("Displaying verification failure for: {}", verificationDescription);

            String message = format(
                    "<html><body style='width: 400px'>" +
                            "<h3>Verification Failed</h3>" +
                            "<p><b>Verification:</b> %s</p>" +
                            "<p><b>Expected State:</b> %s</p>" +
                            "<p><b>Actual State:</b> %s</p>" +
                            "<p><b>Reason:</b> %s</p>" +
                            "</body></html>",
                    verificationDescription, expectedState, actualState, failureReason);
            displayInformationalPopup("Verification Failure", message, null, PopupType.ERROR);
        } catch (Exception e) {
            LOG.error("Error displaying verification failure", e);
        }
    }

    @NotNull
    private static UiElementDescriptionResult getUiElementInfoSuggestionFromModel(String elementDescription,
                                                                                  UiElementCaptureResult capture) {
        var prompt = ElementDescriptionPrompt.builder()
                .withOriginalElementDescription(elementDescription)
                .withScreenshot(capture.wholeScreenshotWithBoundingBox())
                .withBoundingBoxColor(BOUNDING_BOX_COLOR)
                .build();
        try (var model = getModel(getGuiGroundingModelName(), getGuiGroundingModelProvider())) {
            return model.generateAndGetResponseAsObject(prompt,
                    "generating the description of selected UI element");
        }
    }

    private void saveNewUiElementIntoDb(BufferedImage elementScreenshot, UiElementInfo uiElement) {
        var screenshot = fromBufferedImage(elementScreenshot, "png");
        UiElement uiElementToStore = new UiElement(randomUUID(), uiElement.name(), uiElement.description(),
                uiElement.locationDetails(), uiElement.pageSummary(), screenshot, uiElement.zoomInRequired(),
                uiElement.dataDependentAttributes());
        uiElementRetriever.storeElement(uiElementToStore);
    }

    private Optional<UiElement> updateElementScreenshot(List<UiElement> elements, UUID elementId) {
        UiElement elementToUpdate = findElementById(elements, elementId);
        LOG.info("User chose to update screenshot for element: {}", elementToUpdate.name());

        BoundingBoxCaptureNeededPopup.display(null);
        sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);

        return UiElementScreenshotCaptureWindow.displayAndGetResult(null, Color.GREEN)
                .filter(UiElementCaptureResult::success)
                .map(captureResult -> {
                    var newScreenshot = fromBufferedImage(captureResult.elementScreenshot(), "png");
                    var elementWithNewScreenshot = new UiElement(
                            elementToUpdate.uuid(), elementToUpdate.name(), elementToUpdate.description(),
                            elementToUpdate.locationDetails(), elementToUpdate.pageSummary(), newScreenshot,
                            elementToUpdate.zoomInRequired(), elementToUpdate.dataDependentAttributes());
                    uiElementRetriever.updateElement(elementToUpdate, elementWithNewScreenshot);
                    LOG.debug("Persisted updated screenshot for element: {}", elementToUpdate.name());
                    return elementWithNewScreenshot;
                });
    }

    private Optional<UiElement> updateElementInfo(List<UiElement> elements, UUID elementId) {
        UiElement elementToUpdate = findElementById(elements, elementId);
        LOG.info("User chose to update info for element: {}", elementToUpdate.name());

        var currentInfo = new UiElementInfo(elementToUpdate.name(), elementToUpdate.description(),
                elementToUpdate.locationDetails(), elementToUpdate.pageSummary(), elementToUpdate.zoomInRequired(),
                false, elementToUpdate.dataDependentAttributes());

        return UiElementInfoPopup.displayAndGetUpdatedElementInfo(null, currentInfo)
                .map(newInfo -> {
                    var updatedElement = new UiElement(elementToUpdate.uuid(), newInfo.name(), newInfo.description(),
                            newInfo.locationDetails(), newInfo.pageSummary(), elementToUpdate.screenshot(),
                            newInfo.zoomInRequired(),
                            newInfo.dataDependentAttributes());
                    uiElementRetriever.updateElement(elementToUpdate, updatedElement);
                    LOG.debug("Persisted updated info for element: {}", updatedElement.name());
                    return updatedElement;
                });
    }

    private UiElement deleteElement(List<UiElement> elements, UUID elementId) {
        UiElement elementToDelete = findElementById(elements, elementId);
        LOG.info("User chose to delete element: {}", elementToDelete.name());
        uiElementRetriever.removeElement(elementToDelete);
        LOG.debug("Deleted element from storage: {}", elementToDelete.name());
        return elementToDelete;
    }

    private UiElement findElementById(List<UiElement> selection, UUID elementId) {
        return selection.stream()
                .filter(el -> el.uuid().equals(elementId))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("Element with ID " + elementId + " not found in the list."));
    }

    @NotNull
    private static Rectangle getBoundingBoxRectangle(@NotNull BoundingBox boundingBox) {
        return new Rectangle(boundingBox.x1(), boundingBox.y1(), boundingBox.x2() - boundingBox.x1(),
                boundingBox.y2() - boundingBox.y1());
    }

    /**
     * Enum representing the type of informational popup to display.
     */
    public enum PopupType {
        INFO,
        WARNING,
        ERROR
    }
}
