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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.dto.VerificationExecutionResult;
import org.tarik.ta.utils.CommonUtils;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MouseToolsTest {

    private Robot robot;
    private MockedStatic<CommonUtils> commonUtilsMockedStatic;
    private MockedStatic<AgentConfig> agentConfigMockedStatic;
    private MouseTools mouseTools;

    @Mock
    private UiStateCheckAgent uiStateCheckAgent;

    @BeforeEach
    void setUp() {
        robot = mock(Robot.class);
        mouseTools = new MouseTools(uiStateCheckAgent);
        commonUtilsMockedStatic = mockStatic(CommonUtils.class);
        commonUtilsMockedStatic.when(CommonUtils::getRobot).thenReturn(robot);
        commonUtilsMockedStatic.when(() -> CommonUtils.sleepMillis(anyInt())).thenAnswer(_ -> null);
        commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mock(BufferedImage.class));
        commonUtilsMockedStatic.when(CommonUtils::getMouseLocation).thenReturn(new Point(100, 100));
        commonUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(anyString())).thenCallRealMethod();
        commonUtilsMockedStatic.when(() -> CommonUtils.isBlank(anyString())).thenCallRealMethod();

        agentConfigMockedStatic = mockStatic(AgentConfig.class);
        agentConfigMockedStatic.when(AgentConfig::getActionVerificationDelayMillis).thenReturn(10);
        agentConfigMockedStatic.when(AgentConfig::getMaxActionExecutionDurationMillis).thenReturn(100);
    }

    @AfterEach
    void tearDown() {
        commonUtilsMockedStatic.close();
        agentConfigMockedStatic.close();
    }

    @Test
    @DisplayName("rightMouseClick should move and click at specified coordinates")
    void rightMouseClickShouldMoveAndClickAtSpecifiedCoordinates() {
        int x = 100;
        int y = 200;

        mouseTools.rightMouseClick(x, y);

        verify(robot).mouseMove(x, y);
        verify(robot).mousePress(InputEvent.BUTTON3_DOWN_MASK);
        verify(robot).mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    @Test
    @DisplayName("leftMouseClick should move and click at specified coordinates")
    void leftMouseClickShouldMoveAndClickAtSpecifiedCoordinates() {
        int x = 150;
        int y = 250;

        mouseTools.leftMouseClick(x, y);

        verify(robot).mouseMove(x, y);
        verify(robot).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(robot).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Test
    @DisplayName("leftMouseDoubleClick should move and double click at specified coordinates")
    void leftMouseDoubleClickShouldMoveAndDoubleClickAtSpecifiedCoordinates() {
        int x = 200;
        int y = 300;

        mouseTools.leftMouseDoubleClick(x, y);

        verify(robot).mouseMove(x, y);
        verify(robot, times(2)).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(robot, times(2)).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Test
    @DisplayName("moveMouseTo should move mouse to specified coordinates")
    void moveMouseToShouldMoveMouseToSpecifiedCoordinates() {
        int x = 300;
        int y = 400;

        mouseTools.moveMouseTo(x, y);

        verify(robot).mouseMove(x, y);
        verify(robot, never()).mousePress(anyInt());
        verify(robot, never()).mouseRelease(anyInt());
    }

    @Test
    @DisplayName("clickElementUntilStateAchieved succeeds on first try")
    void clickElementUntilStateAchievedSucceedsOnFirstTry() {
        int x = 10;
        int y = 20;
        String expectedState = "State is achieved";

        // To simulate that the state is not achieved before the first click
        when(uiStateCheckAgent.verify(eq(expectedState), anyString(), anyString(), any()))
                .thenReturn(new VerificationExecutionResult(false, "Initial state not met"))
                .thenReturn(new VerificationExecutionResult(true, "State achieved after click"));

        mouseTools.clickElementUntilStateAchieved(x, y, expectedState);

        verify(robot).mouseMove(x, y);
        verify(robot).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(robot).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        verify(uiStateCheckAgent, times(2)).verify(eq(expectedState), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("clickElementUntilStateAchieved fails after timeout")
    void clickElementUntilStateAchievedFailsAfterTimeout() {
        int x = 10;
        int y = 20;
        String expectedState = "State is not achieved";

        when(uiStateCheckAgent.verify(eq(expectedState), anyString(), anyString(), any()))
                .thenReturn(new VerificationExecutionResult(false, "State not met"));

        mouseTools.clickElementUntilStateAchieved(x, y, expectedState);

        // It will click at least once
        verify(robot, atLeastOnce()).mouseMove(x, y);
        verify(robot, atLeastOnce()).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(robot, atLeastOnce()).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }
}