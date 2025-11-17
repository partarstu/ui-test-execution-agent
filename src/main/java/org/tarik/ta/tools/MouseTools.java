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
import org.tarik.ta.AgentConfig;
import org.tarik.ta.prompts.VerificationExecutionPrompt;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.currentTimeMillis;
import static org.tarik.ta.utils.CommonUtils.*;
import static org.tarik.ta.utils.Verifier.verifyOnce;

public class MouseTools extends AbstractTools {
    private static final int MOUSE_ACTION_DELAY_MILLIS = 100;
    private static final int RETRIABLE_ACTION_DELAY_MILLIS = AgentConfig.getActionVerificationDelayMillis() * 2;

    @Tool(value = "Performs a right click with a mouse at the specified coordinates. Use this tool when you need to right-click at a " +
            "specific location on the screen.")
    public static ToolExecutionResult<?> rightMouseClick(
            @P(value = "The x-coordinate of the screen location to right-click on (must be >= 0)") int x,
            @P(value = "The y-coordinate of the screen location to right-click on (must be >= 0)") int y) {
        if (x < 0 || y < 0) {
            return getFailedToolExecutionResult("Invalid coordinates: x=%s, y=%s. Coordinates must be non-negative.".formatted(x, y),
                    false, (BufferedImage) null);
        }
        getRobot().mouseMove(x, y);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
        getRobot().mousePress(InputEvent.BUTTON3_DOWN_MASK);
        getRobot().mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        var message = "Clicked using right mouse button at location (%s, %s)".formatted(x, y);
        return getSuccessfulResult(message);
    }

    @Tool(value = "Performs a left click with a mouse at the specified coordinates. Use this tool when you need to left-click at a " +
            "specific location on the screen.")
    public static ToolExecutionResult<?> leftMouseClick(
            @P(value = "The x-coordinate of the screen location to left-click on (must be >= 0)") int x,
            @P(value = "The y-coordinate of the screen location to left-click on (must be >= 0)") int y) {
        if (x < 0 || y < 0) {
            return getFailedToolExecutionResult("Invalid coordinates: x=%s, y=%s. Coordinates must be non-negative.".formatted(x, y),
                    false, (BufferedImage) null);
        }
        leftMouseClick(new Point(x, y));
        var message = "Clicked left mouse button at location (%s, %s)".formatted(x, y);
        return getSuccessfulResult(message);
    }

    static void leftMouseClick(Point elementLocation) {
        getRobot().mouseMove(elementLocation.x, elementLocation.y);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
        getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
        getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
    }

    @Tool(value = "Performs a double click with a left mouse button at the specified coordinates. Use this tool when you need to " +
            "double-click at a specific location on the screen.")
    public static ToolExecutionResult<?> leftMouseDoubleClick(
            @P(value = "The x-coordinate of the screen location to double-click on (must be >= 0)") int x,
            @P(value = "The y-coordinate of the screen location to double-click on (must be >= 0)") int y) {
        if (x < 0 || y < 0) {
            return getFailedToolExecutionResult("Invalid coordinates: x=%s, y=%s. Coordinates must be non-negative.".formatted(x, y),
                    false, (BufferedImage) null);
        }
        getRobot().mouseMove(x, y);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
        getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
        getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
        getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        var message = "Double-clicked left mouse button at location (%s, %s)".formatted(x, y);
        return getSuccessfulResult(message);
    }


    @Tool(value = "Moves the mouse to the specified coordinates. Use this tool when you need to move the mouse to a specific " +
            "location on the screen.")
    public static ToolExecutionResult<?> moveMouseTo(
            @P(value = "The x-coordinate of the screen location to move the mouse to (must be >= 0)") int x,
            @P(value = "The y-coordinate of the screen location to move the mouse to (must be >= 0)") int y) {
        if (x < 0 || y < 0) {
            return getFailedToolExecutionResult("Invalid coordinates: x=%s, y=%s. Coordinates must be non-negative.".formatted(x, y),
                    false, (BufferedImage) null);
        }
        getRobot().mouseMove(x, y);
        var message = "Moved mouse to location (%s, %s)".formatted(x, y);
        return getSuccessfulResult(message);
    }

    @Tool(value = "Clicks and drags the mouse pointer from the current location by the specified amount of pixels."
            + "Use this when you need to click and drag the mouse pointer on the screen for the specific distance in any direction. "
            + "Provide the dragging distance in pixels on the X and Y scales.")
    public static ToolExecutionResult<?> clickAndDrag(
            @P("Number of pixels to drag the mouse to the right (negative for left)") int xOffset,
            @P("Number of pixels to drag the mouse to the bottom (negative for up)") int yOffset) {
        var mouseLocation = getMouseLocation();
        getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
        getRobot().mouseMove(mouseLocation.x + xOffset, mouseLocation.y + yOffset);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
        getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        var message = "Clicked and dragged the mouse from the point at %s by offset (%d, %d)"
                .formatted(mouseLocation, xOffset, yOffset);
        return getSuccessfulResult(message);
    }

    @Tool(value = "Repeatedly clicks at specified coordinates until a desired state is reached or a timeout occurs.")
    public static ToolExecutionResult<?> clickElementUntilStateAchieved(
            @P("The x-coordinate of the screen location to click (must be >= 0)") int x,
            @P("The y-coordinate of the screen location to click (must be >= 0)") int y,
            @P("Description of the expected state after the click") String expectedStateDescription) {
        if (x < 0 || y < 0) {
            return getFailedToolExecutionResult("Invalid coordinates: x=%s, y=%s. Coordinates must be non-negative.".formatted(x, y),
                    false, (BufferedImage) null);
        }
        if (expectedStateDescription == null || expectedStateDescription.isBlank()) {
            return getFailedToolExecutionResult("Expected state description cannot be empty.", false, (BufferedImage) null);
        }
        var promptBuilder = VerificationExecutionPrompt.builder()
                .withVerificationDescription(expectedStateDescription)
                .withActionDescription("Clicked at location (%s, %s)".formatted(x, y));
        var verificationResult = verifyOnce(promptBuilder.screenshot(captureScreen()).build());

        if (verificationResult.success()) {
            return getSuccessfulResult(
                    "The screen is already in the expected state '%s' before clicking.".formatted(expectedStateDescription));
        } else {
            var waitDuration = AgentConfig.getMaxActionExecutionDurationMillis();
            long deadline = currentTimeMillis() + waitDuration;
            AtomicReference<BufferedImage> latestScreenshot = new AtomicReference<>();
            Point clickLocation = new Point(x, y);
            do {
                leftMouseClick(clickLocation);
                sleepMillis(RETRIABLE_ACTION_DELAY_MILLIS);
                var screenshot = latestScreenshot.updateAndGet(_ -> captureScreen());
                var prompt = promptBuilder.screenshot(screenshot).build();
                if (verifyOnce(prompt).success()) {
                    return getSuccessfulResult("Clicked at location (%s, %s) and reached expected state '%s'"
                            .formatted(x, y, expectedStateDescription));
                }
            } while (currentTimeMillis() < deadline);
            return getFailedToolExecutionResult("Failed to reach expected state '%s' after clicking at location (%s, %s) within the timeout of %s millis"
                    .formatted(expectedStateDescription, x, y, waitDuration), false, latestScreenshot.get());
        }
    }
}