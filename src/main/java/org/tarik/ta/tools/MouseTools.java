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
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.exceptions.ToolExecutionException;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.currentTimeMillis;
import static org.tarik.ta.error.ErrorCategory.TIMEOUT;
import static org.tarik.ta.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.utils.CommonUtils.*;
import static org.tarik.ta.utils.PromptUtils.singleImageContent;

public class MouseTools extends AbstractTools {
    private static final int MOUSE_ACTION_DELAY_MILLIS = 100;
    private static final int RETRIABLE_ACTION_DELAY_MILLIS = AgentConfig.getActionVerificationDelayMillis() * 2;

    public MouseTools() {
        super();
    }

    protected MouseTools(UiStateCheckAgent uiStateCheckAgent) {
        super(uiStateCheckAgent);
    }

    @Tool(value = "Performs a right click with a mouse at the specified coordinates. Use this tool when you need to right-click at a " +
            "specific location on the screen.")
    public void rightMouseClick(
            @P(value = "The x-coordinate of the screen location to right-click on (must be >= 0)") int x,
            @P(value = "The y-coordinate of the screen location to right-click on (must be >= 0)") int y) {
        validateCoordinates(x, y);
        try {
            getRobot().mouseMove(x, y);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            getRobot().mousePress(InputEvent.BUTTON3_DOWN_MASK);
            getRobot().mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "performing right click with mouse at (%s, %s)".formatted(x, y));
        }
    }

    @Tool(value = "Performs a left click with a mouse at the specified coordinates. Use this tool when you need to left-click at a " +
            "specific location on the screen.")
    public void leftMouseClick(
            @P(value = "The x-coordinate of the screen location to left-click on (must be >= 0)") int x,
            @P(value = "The y-coordinate of the screen location to left-click on (must be >= 0)") int y) {
        validateCoordinates(x, y);
        try {
            leftMouseClick(new Point(x, y));
        } catch (Exception e) {
            throw rethrowAsToolException(e, "performing left click with mouse at (%s, %s)".formatted(x, y));
        }
    }

    @Tool(value = "Performs a double click with a left mouse button at the specified coordinates. Use this tool when you need to " +
            "double-click at a specific location on the screen.")
    public void leftMouseDoubleClick(
            @P(value = "The x-coordinate of the screen location to double-click on (must be >= 0)") int x,
            @P(value = "The y-coordinate of the screen location to double-click on (must be >= 0)") int y) {
        validateCoordinates(x, y);
        try {
            getRobot().mouseMove(x, y);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
            getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
            getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "performing double click with mouse at (%s, %s)".formatted(x, y));
        }
    }

    @Tool(value = "Moves the mouse to the specified coordinates. Use this tool when you need to move the mouse to a specific " +
            "location on the screen.")
    public void moveMouseTo(
            @P(value = "The x-coordinate of the screen location to move the mouse to (must be >= 0)") int x,
            @P(value = "The y-coordinate of the screen location to move the mouse to (must be >= 0)") int y) {
        validateCoordinates(x, y);
        try {
            getRobot().mouseMove(x, y);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "moving mouse to (%s, %s)".formatted(x, y));
        }
    }

    @Tool(value = "Clicks and drags the mouse pointer from the current location by the specified amount of pixels." +
            "Use this when you need to click and drag the mouse pointer on the screen for the specific distance in any direction. " +
            "Provide the dragging distance in pixels on the X and Y scales.")
    public void clickAndDrag(
            @P("Number of pixels to drag the mouse to the right (negative for left)") int xOffset,
            @P("Number of pixels to drag the mouse to the bottom (negative for up)") int yOffset) {
        try {
            var mouseLocation = getMouseLocation();
            getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            getRobot().mouseMove(mouseLocation.x + xOffset, mouseLocation.y + yOffset);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "clicking and dragging mouse by offset (%s, %s)".formatted(xOffset, yOffset));
        }
    }

    @Tool(value = "Repeatedly clicks at specified coordinates until a desired state is reached or a timeout occurs.")
    public void clickElementUntilStateAchieved(
            @P("The x-coordinate of the screen location to click (must be >= 0)") int x,
            @P("The y-coordinate of the screen location to click (must be >= 0)") int y,
            @P("Description of the expected state after the click") String expectedStateDescription           ) {
        validateCoordinates(x, y);
        if (expectedStateDescription == null || expectedStateDescription.isBlank()) {
            throw new ToolExecutionException("Expected state description cannot be empty.", TRANSIENT_TOOL_ERROR);
        }

        try {
            var actionDescription = "Clicked at location (%s, %s)".formatted(x, y);
            var verificationResult = uiStateCheckAgent.verify(expectedStateDescription, actionDescription, "",
                    singleImageContent(captureScreen()));
            if (!verificationResult.success()) {
                var waitDuration = AgentConfig.getMaxActionExecutionDurationMillis();
                long deadline = currentTimeMillis() + waitDuration;
                AtomicReference<BufferedImage> latestScreenshot = new AtomicReference<>();
                Point clickLocation = new Point(x, y);
                do {
                    leftMouseClick(clickLocation);
                    sleepMillis(RETRIABLE_ACTION_DELAY_MILLIS);
                    var screenshot = latestScreenshot.updateAndGet(_ -> captureScreen());
                    if (uiStateCheckAgent.verify(expectedStateDescription, actionDescription, "", singleImageContent(screenshot))
                            .success()) {
                        return;
                    }
                } while (currentTimeMillis() < deadline);
                throw new ToolExecutionException(("Failed to reach expected state '%s' after clicking at location (%s, %s) within " +
                        "the timeout of %s millis").formatted(expectedStateDescription, x, y, waitDuration), TIMEOUT);
            }
        } catch (Exception e) {
            throw rethrowAsToolException(e, "clicking at (%s, %s) until state '%s' achieved".formatted(x, y, expectedStateDescription));
        }
    }

    protected void leftMouseClick(Point elementLocation) {
        getRobot().mouseMove(elementLocation.x, elementLocation.y);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
        getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
        getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
    }

    private void validateCoordinates(int x, int y) {
        if (x < 0 || y < 0) {
            throw new ToolExecutionException("Invalid coordinates: x=%s, y=%s. Coordinates must be non-negative.".formatted(x, y),
                    TRANSIENT_TOOL_ERROR);
        }
    }
}
