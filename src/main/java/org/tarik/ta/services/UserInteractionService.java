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

import org.tarik.ta.dto.*;
import org.tarik.ta.rag.model.UiElement;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Service responsible for all user dialog interactions in the test automation system.
 * This service acts as a facade for UI dialogs and handles:
 * - Creating and displaying user dialogs based on agent requests
 * - Collecting user responses and returning structured results
 * - Implementing user decisions (creating, updating, or removing UI elements)
 * - Managing dialog sequencing and lifecycle
 * - Handling user interruptions and termination requests gracefully
 * The service coordinates multiple dialogs in workflows and ensures proper
 * resource cleanup and interruption handling.
 */
public interface UserInteractionService {

    /**
     * Prompts the user to create a new UI element through a multi-step workflow.
     * This operation coordinates multiple dialogs:
     * 1. BoundingBoxCaptureNeededPopup - to capture the element's location
     * 2. UiElementScreenshotCaptureWindow - to capture the element's screenshot
     * 3. UiElementInfoPopup - to collect element metadata (name, description, etc.)
     * 4. Persists the new element to the database
     *
     * @param pageName           The name of the page where the element is located
     * @param elementDescription Initial description or hint about the element
     * @return NewElementCreationResult containing the created element and associated data,
     * or an interrupted/failed result if the user cancels or an error occurs
     */
    NewElementCreationResult promptUserToCreateNewElement(String pageName, String elementDescription);

    /**
     * Prompts the user to refine (update or delete) existing UI elements.
     * Opens the UiElementRefinementPopup with the provided candidate elements.
     * The user can:
     * - Update element information (name, description, location, screenshot)
     * - Delete elements that are no longer valid
     * - Skip refinement and proceed
     *
     * @param candidateElements List of UI elements that are candidates for refinement
     * @param context           Additional context about why refinement is being suggested
     * @return ElementRefinementResult containing lists of updated and deleted elements,
     * or an interrupted result if the user cancels
     */
    ElementRefinementResult promptUserToRefineExistingElements(List<UiElement> candidateElements, String context);

    /**
     * Asks the user to confirm that a located element is correct.
     * Opens the LocatedElementConfirmationDialog showing a screenshot with the
     * element's bounding box highlighted.
     *
     * @param elementDescription Description of the element being confirmed
     * @param boundingBox        The bounding box of the located element
     * @param screenshot         Screenshot showing the element with bounding box overlay
     * @return LocationConfirmationResult with the user's choice (CORRECT, INCORRECT, INTERRUPTED)
     */
    LocationConfirmationResult confirmLocatedElement(String elementDescription, BoundingBox boundingBox, BufferedImage screenshot);

    /**
     * Prompts the user to decide on the next action after element location attempts fail.
     * Opens the NextActionPopup presenting options:
     * - Create a new element
     * - Retry the search
     * - Terminate execution
     *
     * @param reason             Description of the reason of prompting the user
     * @return NextActionResult with the user's choice (CREATE_NEW_ELEMENT, RETRY_SEARCH, TERMINATE)
     */
    NextActionResult promptUserForNextAction(String reason);

    /**
     * Displays an informational popup to the user (blocking).
     * Used for showing various informational messages such as:
     * - Instructions for the next action (NewElementInfoNeededPopup)
     * - Target element information (TargetElementToGetLocated)
     * - Verification failure warnings (showing expected vs actual state)
     *
     * @param title      Title of the informational popup
     * @param message    Main message to display
     * @param screenshot Optional screenshot to show (can be null)
     * @param popupType  Type of informational popup (INFO, WARNING, ERROR)
     */
    void displayInformationalPopup(String title, String message, BufferedImage screenshot, PopupType popupType);

    /**
     * Displays a verification failure warning to the user.
     * Shows the expected state, actual state, and reason for the failure.
     *
     * @param verificationDescription Description of what was being verified
     * @param expectedState           The expected state that should have been observed
     * @param actualState             The actual state that was observed
     * @param failureReason           Reason why the verification failed
     * @param screenshot              Screenshot showing the current state
     */
    void displayVerificationFailure(String verificationDescription, String expectedState, String actualState, String failureReason,
                                    BufferedImage screenshot);


    /**
     * Requests cancellation of any ongoing dialog operations.
     * This method should be called when the system needs to gracefully shut down
     * or when a user interrupt is detected at a higher level.
     */
    void requestCancellation();

    /**
     * Checks if a cancellation has been requested.
     *
     * @return true if cancellation has been requested, false otherwise
     */
    boolean isCancellationRequested();

    /**
     * Enum representing the type of informational popup to display.
     */
    enum PopupType {
        INFO,
        WARNING,
        ERROR
    }
}
