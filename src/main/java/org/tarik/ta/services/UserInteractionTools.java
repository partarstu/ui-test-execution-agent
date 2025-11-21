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

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.dto.*;
import org.tarik.ta.prompts.ElementDescriptionPrompt;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.rag.model.UiElement;
import org.tarik.ta.rag.model.UiElement.Screenshot;
import org.tarik.ta.user_dialogs.*;
import org.tarik.ta.user_dialogs.UiElementInfoPopup.UiElementInfo;
import org.tarik.ta.user_dialogs.UiElementScreenshotCaptureWindow.UiElementCaptureResult;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.tarik.ta.AgentConfig.getGuiGroundingModelName;
import static org.tarik.ta.AgentConfig.getGuiGroundingModelProvider;
import static org.tarik.ta.model.ModelFactory.getModel;
import static org.tarik.ta.rag.model.UiElement.Screenshot.fromBufferedImage;
import static org.tarik.ta.utils.CommonUtils.getColorByName;
import static org.tarik.ta.utils.CommonUtils.sleepMillis;

/**
 * Default implementation of UserInteractionService that coordinates UI dialogs.
 * This service manages the lifecycle of user dialogs and handles user
 * responses,
 * converting them to structured result objects.
 */
public class UserInteractionTools implements UserInteractionService {
    private static final Logger LOG = LoggerFactory.getLogger(UserInteractionTools.class);
    private final UiElementRetriever uiElementRetriever;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
    private static final String BOUNDING_BOX_COLOR_NAME = AgentConfig.getElementBoundingBoxColorName();
    private static final Color BOUNDING_BOX_COLOR = getColorByName(BOUNDING_BOX_COLOR_NAME);
    private static final int USER_DIALOG_DISMISS_DELAY_MILLIS = 1000;

    /**
     * Constructs a new UserInteractionServiceImpl.
     *
     * @param uiElementRetriever The retriever for persisting and querying UI
     *                           elements
     */
    public UserInteractionTools(UiElementRetriever uiElementRetriever) {
        this.uiElementRetriever = uiElementRetriever;
    }

    @Override
    @Tool("Prompts the user to create a new UI element through a multi-step workflow. Use this tool when you need to create a new " +
            "element which is not present in the database.")
    public NewElementCreationResult promptUserToCreateNewElement(
            @P("The name of the page where the element is located") String pageName,
            @P("Initial description or hint about the element") String elementDescription) {
        if (isCancellationRequested()) {
            LOG.info("Cancellation requested, skipping element creation");
            return NewElementCreationResult.interrupted("Cancellation requested");
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
                return NewElementCreationResult.interrupted("User cancelled screenshot capture");
            }

            var capture = captureResult.get();
            Rectangle boundingBox = capture.boundingBox();
            BufferedImage wholeScreenshot = capture.wholeScreenshotWithBoundingBox();
            BufferedImage elementScreenshot = capture.elementScreenshot();

            // Step 3: Prompt the model to suggest the new element info based on the element
            // position on the screenshot
            var describedUiElement = getUiElementInfoSuggestionFromModel(elementDescription, capture);

            // Step 4: Prompt user to refine the suggested by the model element info
            var uiElementInfo = new UiElementInfo(describedUiElement.name(), describedUiElement.ownDescription(),
                    describedUiElement.locationDescription(), describedUiElement.pageSummary(), false, false,
                    List.of());
            return UiElementInfoPopup.displayAndGetUpdatedElementInfo(null, uiElementInfo)
                    .map(clarifiedByUserElement -> {
                        // Step 5: Persist the element
                        LOG.debug("Persisting new element to database");
                        var savedUiElement = saveNewUiElementIntoDb(capture.elementScreenshot(),
                                clarifiedByUserElement);
                        LOG.info("Successfully created new element: {}", clarifiedByUserElement.name());
                        return NewElementCreationResult.success(savedUiElement, boundingBox, wholeScreenshot,
                                elementScreenshot);
                    })
                    .orElseGet(() -> NewElementCreationResult.interrupted("User interrupted element creation"));
        } catch (Exception e) {
            LOG.error("Error during element creation", e);
            return NewElementCreationResult.failure("Error during element creation: " + e.getMessage());
        }
    }

    @Override
    @Tool("Prompts the user to refine (update or delete) existing UI elements. Use this tool when you found some elements in the database " +
            "but they seem to be outdated or incorrect.")
    public ElementRefinementResult promptUserToRefineExistingElements(
            @P("List of UI elements that are candidates for refinement") List<UiElement> candidateElements,
            @P("Additional context about why refinement is being suggested") String context) {
        List<UiElement> elementsToRefine = new LinkedList<>(candidateElements);
        if (isCancellationRequested()) {
            LOG.info("Cancellation requested, skipping element refinement");
            return ElementRefinementResult.wasInterrupted();
        }

        Set<UiElement> updatedElementsCollector = new HashSet<>();
        List<UiElement> deletedElementsCollector = new ArrayList<>();
        try {
            LOG.info("Starting element refinement workflow with {} candidates", elementsToRefine.size());
            LOG.debug("Refinement context: {}", context);

            boolean changesMade = false;
            while (true) {
                var choiceOptional = UiElementRefinementPopup.displayAndGetChoice(null, context, elementsToRefine);
                if (choiceOptional.isEmpty()) {
                    LOG.info("User interrupted element refinement");
                    return ElementRefinementResult.wasInterrupted();
                }

                ElementRefinementOperation operation = choiceOptional.get();
                if (operation.operation() == ElementRefinementOperation.Operation.DONE) {
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
                    default -> throw new IllegalStateException(
                            "Unexpected value for element operation type: " + operation.operation());
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
            LOG.error("Error during element refinement", e);
            return ElementRefinementResult.failure(e.getMessage());
        }
    }

    @Override
    @Tool("Asks the user to confirm that a located element is correct. Use this tool when you have located an element but want to ensure " +
            "it is the correct one before proceeding.")
    public LocationConfirmationResult confirmLocatedElement(
            @P("Description of the element being confirmed") String elementDescription,
            @P("The bounding box of the located element") BoundingBox boundingBox,
            @P("Screenshot showing the element with bounding box overlay") BufferedImage screenshot) {
        if (isCancellationRequested()) {
            LOG.info("Cancellation requested, skipping location confirmation");
            return LocationConfirmationResult.interrupted(elementDescription);
        }

        try {
            Rectangle boundingBoxRectangle = getBoundingBoxRectangle(boundingBox);
            LOG.info("Prompting user to confirm located element: {}", elementDescription);
            var choice = LocatedElementConfirmationDialog.displayAndGetUserChoice(null, screenshot,
                    boundingBoxRectangle,
                    BOUNDING_BOX_COLOR, elementDescription);

            return switch (choice) {
                case CORRECT -> {
                    LOG.info("User confirmed element location as correct");
                    yield LocationConfirmationResult.correct(boundingBox, screenshot, elementDescription);
                }
                case INCORRECT -> {
                    LOG.info("User marked element location as incorrect");
                    yield LocationConfirmationResult.incorrect(boundingBox, screenshot, elementDescription);
                }
                case INTERRUPTED -> {
                    LOG.info("User interrupted location confirmation");
                    yield LocationConfirmationResult.interrupted(elementDescription);
                }
            };

        } catch (Exception e) {
            LOG.error("Error during location confirmation", e);
            return LocationConfirmationResult.failure(e.getMessage());
        }
    }

    private static void logCancellation() {
        LOG.info("Cancellation requested, terminating");
    }

    @Override
    @Tool("Prompts the user to decide on the next action after element location attempts fail. Use this tool when you cannot find an " +
            "element and want the user to decide what to do next.")
    public NextActionResult promptUserForNextAction(
            @P("Description of the reason of prompting the user") String reason) {
        if (isCancellationRequested()) {
            logCancellation();
            return NextActionResult.failure("Cancellation requested");
        }

        try {
            LOG.info("Prompting user for next action because {}", reason);
            var decision = NextActionPopup.displayAndGetUserDecision(null, reason);
            return switch (decision) {
                case CREATE_NEW_ELEMENT -> {
                    LOG.info("User chose to create a new element");
                    yield NextActionResult.createNewElement();
                }
                case RETRY_SEARCH -> {
                    LOG.info("User chose to retry search");
                    yield NextActionResult.retrySearch();
                }
                case TERMINATE -> {
                    LOG.info("User chose to terminate");
                    yield NextActionResult.terminate();
                }
            };
        } catch (Exception e) {
            LOG.error("Error during next action prompt", e);
            return NextActionResult.failure("Got error while prompting user for next action: " + e.getMessage());
        }
    }

    @Override
    public void displayInformationalPopup(String title, String message, BufferedImage screenshot, PopupType popupType) {
        if (isCancellationRequested()) {
            LOG.debug("Cancellation requested, skipping informational popup");
            return;
        }

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
            LOG.error("Error displaying informational popup", e);
        }
    }

    @Override
    public void displayVerificationFailure(String verificationDescription, String expectedState, String actualState,
                                           String failureReason,
                                           BufferedImage screenshot) {
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
            displayInformationalPopup("Verification Failure", message, screenshot, PopupType.ERROR);
        } catch (Exception e) {
            LOG.error("Error displaying verification failure", e);
        }
    }

    @Override
    public void requestCancellation() {
        LOG.info("Cancellation requested for UserInteractionService");
        cancellationRequested.set(true);
    }

    @Override
    public boolean isCancellationRequested() {
        return cancellationRequested.get();
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

    private UiElement saveNewUiElementIntoDb(BufferedImage elementScreenshot, UiElementInfo uiElement) {
        Screenshot screenshot = fromBufferedImage(elementScreenshot, "png");
        UiElement uiElementToStore = new UiElement(randomUUID(), uiElement.name(), uiElement.description(),
                uiElement.locationDetails(), uiElement.pageSummary(), screenshot, uiElement.zoomInRequired(),
                uiElement.dataDependentAttributes());
        uiElementRetriever.storeElement(uiElementToStore);
        return uiElementToStore;
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
}
