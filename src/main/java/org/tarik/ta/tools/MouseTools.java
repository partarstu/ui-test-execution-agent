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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.exceptions.UserChoseTerminationException;
import org.tarik.ta.exceptions.UserInterruptedExecutionException;
import org.tarik.ta.prompts.VerificationExecutionPrompt;
import org.tarik.ta.tools.ElementLocator.UiElementLocationResult;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.lang.System.currentTimeMillis;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.INTERRUPTED_BY_USER;
import static org.tarik.ta.tools.ElementLocator.locateElementOnTheScreen;
import static org.tarik.ta.utils.CommonUtils.*;
import static org.tarik.ta.utils.Verifier.verifyOnce;

public class MouseTools extends AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(MouseTools.class);
    private static final int MOUSE_ACTION_DELAY_MILLIS = 100;
    private static final int RETRIABLE_ACTION_DELAY_MILLIS = AgentConfig.getActionVerificationDelayMillis() * 2;

    @Tool(value = "Performs a right click with a mouse at the specified UI element."
            + "Use this tool when you need to right-click on a specific UI element. "
            + "Provide a detailed description of the element, including its name, type, and any "
            + "relevant context that helps to identify it uniquely.")
    public static ToolExecutionResult rightMouseClick(
            @P(value = "Detailed description of the UI element to right-click on") String elementDescription,
            @P(value = "Any data related to this action or this element, if any", required = false) String relatedData) {
        if (elementDescription == null || elementDescription.isBlank()) {
            return getFailedToolExecutionResult("Can't click with right mouse button on an element without any description", true);
        }

        return executeUsingUiElement(elementDescription, relatedData, elementLocation -> {
            getRobot().mouseMove(elementLocation.x, elementLocation.y);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            getRobot().mousePress(InputEvent.BUTTON3_DOWN_MASK);
            getRobot().mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            var message = "Clicked using right mouse button on '%s' using location %s"
                    .formatted(elementDescription, elementLocation);
            return getSuccessfulResult(message);
        });
    }

    @Tool(value = "Performs a left click with a mouse at the specified UI element."
            + "Use this tool when you need to left-click on a specific UI element. "
            + "Provide a detailed description of the element, including its name, type, and any "
            + "relevant context that helps to identify it uniquely.")
    public static ToolExecutionResult leftMouseClick(
            @P(value = "Detailed description of the UI element to left-click on") String elementDescription,
            @P(value = "Any data related to this action or this element, if any", required = false) String relatedData) {
        if (elementDescription == null || elementDescription.isBlank()) {
            return getFailedToolExecutionResult("Can't click an element without any description using mouse", true);
        }

        return executeUsingUiElement(elementDescription, relatedData, elementLocation -> {
            leftMouseClick(elementLocation);
            var message = "Clicked left mouse button on '%s' using location %s".formatted(elementDescription, elementLocation);
            return getSuccessfulResult(message);
        });
    }

    static void leftMouseClick(Point elementLocation) {
        getRobot().mouseMove(elementLocation.x, elementLocation.y);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
        getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
        getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
    }

    @Tool(value = "Performs a double click with a left mouse button at the specified UI element."
            + "Use this tool when you need to double-click on a specific UI element. "
            + "Provide a detailed description of the element, including its name, type, and any "
            + "relevant context that helps to identify it uniquely.")
    public static ToolExecutionResult leftMouseDoubleClick(
            @P(value = "Detailed description of the UI element to double-click on") String elementDescription,
            @P(value = "Any data related to this action or this element, if any", required = false) String relatedData) {
        if (elementDescription == null || elementDescription.isBlank()) {
            return getFailedToolExecutionResult("Can't double-click an element without any description using mouse", true);
        }

        return executeUsingUiElement(elementDescription, relatedData, elementLocation -> {
            getRobot().mouseMove(elementLocation.x, elementLocation.y);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
            getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
            getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            var message =
                    "Double-clicked left mouse button on '%s' using location %s".formatted(elementDescription, elementLocation);
            return getSuccessfulResult(message);
        });
    }


    @Tool(value = "Moves the mouse to the center of the specified UI element. "
            + "Use this tool when you need to move the mouse to the center of a specific UI element. "
            + "Provide a detailed description of the element, including its name, type, and any "
            + "relevant context that helps to identify it uniquely.")
    public static ToolExecutionResult moveMouseToElementCenter(
            @P(value = "Detailed description of the UI element to move the mouse to") String elementDescription,
            @P(value = "Any data related to this action or this element, if any", required = false) String relatedData) {
        if (elementDescription == null || elementDescription.isBlank()) {
            return getFailedToolExecutionResult("Can't move mouse to an element without any description", true);
        }

        return executeUsingUiElement(elementDescription, relatedData, elementLocation -> {
            getRobot().mouseMove(elementLocation.x, elementLocation.y);
            var message = "Moved mouse to the center of '%s' at location (%s, %s)"
                    .formatted(elementDescription, elementLocation.x, elementLocation.y);
            return getSuccessfulResult(message);
        });
    }

    @Tool(value = "Clicks and drags the mouse pointer from the current location by the specified amount of pixels."
            + "Use this when you need to click and drag the mouse pointer on the screen for the specific distance in any direction. "
            + "Provide the dragging distance in pixels on the X and Y scales.")
    public static ToolExecutionResult clickAndDrag(
            @P("Number of pixels to drag the mouse to the right (negative for left)") String xOffset,
            @P("Number of pixels to drag the mouse to the bottom (negative for up)") String yOffset) {
        return parseStringAsInteger(xOffset)
                .map(xOffsetInt -> parseStringAsInteger(yOffset)
                        .map(yOffsetInt -> {
                            var mouseLocation = getMouseLocation();
                            getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
                            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
                            getRobot().mouseMove(mouseLocation.x + xOffsetInt, mouseLocation.y + yOffsetInt);
                            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
                            getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                            var message = "Clicked and dragged the mouse from the point at %s by offset (%s, %s)"
                                    .formatted(mouseLocation, xOffset, yOffset);
                            return getSuccessfulResult(message);
                        })
                        .orElseGet(() -> getFailedToolExecutionResult("'%s' is not a valid integer value for the 'yOffset' variable"
                                .formatted(yOffset), true)))
                .orElseGet(() -> getFailedToolExecutionResult("'%s' is not a valid integer value for the 'xOffset' variable"
                        .formatted(xOffset), true));
    }

    @Tool(value = "Repeatedly clicks a UI element until a desired state is reached or a timeout occurs.")
    public static ToolExecutionResult clickElementUntilStateAchieved(
            @P("Detailed description of the UI element to click") String elementDescription,
            @P("Description of the expected state after the click") String expectedStateDescription,
            @P(value = "Any data related to this action or this element, if any", required = false) String relatedData) {
        var promptBuilder = VerificationExecutionPrompt.builder()
                .withVerificationDescription(expectedStateDescription)
                .withActionDescription("Clicked the '%s'".formatted(elementDescription))
                .withActionTestData(relatedData);
        var verificationResult = verifyOnce(promptBuilder.screenshot(captureScreen()).build());
        if (verificationResult.success()) {
            return getSuccessfulResult(
                    "Element '%s' is already in expected state '%s'".formatted(elementDescription, expectedStateDescription));
        } else {
            var waitDuration = AgentConfig.getMaxActionExecutionDurationMillis();
            long deadline = currentTimeMillis() + waitDuration;
            AtomicReference<BufferedImage> latestScreenshot = new AtomicReference<>();
            return executeUsingUiElement(elementDescription, relatedData, elementLocation -> {
                do {
                    leftMouseClick(elementLocation);
                    sleepMillis(RETRIABLE_ACTION_DELAY_MILLIS);
                    var screenshot = latestScreenshot.updateAndGet(_ -> captureScreen());
                    var prompt = promptBuilder.screenshot(screenshot).build();
                    if (verifyOnce(prompt).success()) {
                        return getSuccessfulResult("Clicked element '%s' and reached expected state '%s'"
                                .formatted(elementDescription, expectedStateDescription));
                    }
                } while (currentTimeMillis() < deadline);
                return getFailedToolExecutionResult("Failed to reach expected state '%s' for element '%s' within the timeout of %s millis"
                        .formatted(expectedStateDescription, elementDescription, waitDuration), false, latestScreenshot.get());
            });
        }
    }

    @NotNull
    private static ToolExecutionResult executeUsingUiElement(String elementDescription,
                                                             String testSpecificData,
                                                             Function<Point, ToolExecutionResult> executionResultProvider) {
        UiElementLocationResult uiElementLocationResult = null;
        try {
            uiElementLocationResult = locateElementOnTheScreen(elementDescription, testSpecificData);
            if (uiElementLocationResult.uiElementBoundingBox() != null) {
                var boundingBox = uiElementLocationResult.uiElementBoundingBox();
                var point = new Point((int) boundingBox.getCenterX(), (int) boundingBox.getCenterY());
                return executionResultProvider.apply(point);
            } else {
                return getNoElementFoundResult(elementDescription, uiElementLocationResult.screenshot());
            }
        } catch (UserChoseTerminationException | UserInterruptedExecutionException e) {
            LOG.error(e.getMessage());
            var screenshot = uiElementLocationResult == null ? captureScreen() : uiElementLocationResult.screenshot();
            return new ToolExecutionResult(INTERRUPTED_BY_USER, e.getMessage(), false, screenshot);
        } catch (Exception e) {
            var screenshot = uiElementLocationResult == null ? captureScreen() : uiElementLocationResult.screenshot();
            return getFailedToolExecutionResult(e.getMessage(), true, screenshot);
        }
    }

    @NotNull
    private static ToolExecutionResult getNoElementFoundResult(String elementDescription, BufferedImage screenshot) {
        return getFailedToolExecutionResult("The element with description '%s' was not found on the screen".formatted(elementDescription),
                true, screenshot);
    }
}