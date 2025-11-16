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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tarik.ta.utils.CommonUtils;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.SUCCESS;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeyboardToolsTest {

    private Robot robot;
    private MockedStatic<CommonUtils> commonUtilsMockedStatic;
    private MockedStatic<MouseTools> mouseToolsMockedStatic;
    private MockedStatic<Toolkit> toolkitMockedStatic;
    private Clipboard clipboard;

    @BeforeEach
    void setUp() {
        robot = mock(Robot.class);
        clipboard = mock(Clipboard.class);

        commonUtilsMockedStatic = mockStatic(CommonUtils.class);
        commonUtilsMockedStatic.when(CommonUtils::getRobot).thenReturn(robot);
        commonUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(anyString())).thenCallRealMethod();
        commonUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(null)).thenReturn(false);
        commonUtilsMockedStatic.when(() -> CommonUtils.isBlank(anyString())).thenCallRealMethod();
        commonUtilsMockedStatic.when(() -> CommonUtils.sleepMillis(anyInt())).thenAnswer(_ -> null);

        mouseToolsMockedStatic = mockStatic(MouseTools.class, invocation -> {
            // Only mock leftMouseClick, let other methods pass through
            if (invocation.getMethod().getName().equals("leftMouseClick") && 
                invocation.getMethod().getParameterCount() == 2) {
                return new AbstractTools.ToolExecutionResult<>(SUCCESS, "Clicked", false);
            }
            return invocation.callRealMethod();
        });

        Toolkit toolkit = mock(Toolkit.class);
        when(toolkit.getSystemClipboard()).thenReturn(clipboard);
        
        toolkitMockedStatic = mockStatic(Toolkit.class);
        toolkitMockedStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkit);
    }

    @AfterEach
    void tearDown() {
        commonUtilsMockedStatic.close();
        mouseToolsMockedStatic.close();
        toolkitMockedStatic.close();
    }

    @Test
    @DisplayName("typeText with coordinates should click and type")
    void typeTextWithCoordinatesShouldClickAndType() {
        String text = "abc";
        int x = 100;
        int y = 200;

        AbstractTools.ToolExecutionResult<?> result = KeyboardTools.typeText(text, x, y, "true");

        mouseToolsMockedStatic.verify(() -> MouseTools.leftMouseClick(x, y));

        // Verify clear actions (Ctrl+A, Backspace) - VK_A is used for both Ctrl+A and typing 'a'
        verify(robot, times(1)).keyPress(KeyEvent.VK_CONTROL);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot, times(1)).keyPress(KeyEvent.VK_BACK_SPACE);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_BACK_SPACE);

        // Verify typing 'a', 'b', 'c' (VK_A pressed twice: once for Ctrl+A, once for 'a')
        verify(robot, times(2)).keyPress(KeyEvent.VK_A);
        verify(robot, times(2)).keyRelease(KeyEvent.VK_A);
        verify(robot, times(1)).keyPress(KeyEvent.VK_B);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_B);
        verify(robot, times(1)).keyPress(KeyEvent.VK_C);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_C);

        assertThat(result.executionStatus()).isEqualTo(SUCCESS);
    }

    @Test
    @DisplayName("clearData with coordinates should click and clear")
    void clearDataWithCoordinatesShouldClickAndClear() {
        int x = 100;
        int y = 200;

        AbstractTools.ToolExecutionResult<?> result = KeyboardTools.clearData(x, y);

        mouseToolsMockedStatic.verify(() -> MouseTools.leftMouseClick(x, y));

        verify(robot).keyPress(KeyEvent.VK_CONTROL);
        verify(robot).keyPress(KeyEvent.VK_A);
        verify(robot).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot).keyRelease(KeyEvent.VK_A);
        verify(robot).keyPress(KeyEvent.VK_BACK_SPACE);
        verify(robot).keyRelease(KeyEvent.VK_BACK_SPACE);

        assertThat(result.executionStatus()).isEqualTo(SUCCESS);
    }

    @Test
    @DisplayName("pressKey should press and release a single key")
    void pressKeyShouldPressAndReleaseSingleKey() {
        KeyboardTools.pressKey("A");
        verify(robot).keyPress(KeyEvent.VK_A);
        verify(robot).keyRelease(KeyEvent.VK_A);
    }

    @Test
    @DisplayName("pressKeys should press and release multiple keys")
    void pressKeysShouldPressAndReleaseMultipleKeys() {
        KeyboardTools.pressKeys("Ctrl", "C");
        verify(robot).keyPress(KeyEvent.VK_CONTROL);
        verify(robot).keyPress(KeyEvent.VK_C);
        verify(robot).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot).keyRelease(KeyEvent.VK_C);
    }

    @Test
    @DisplayName("typeText should handle non-ASCII characters with copy-paste")
    void typeTextShouldHandleNonAsciiWithCopyPaste() {
        String text = "你好";
        int x = 10, y = 20;

        KeyboardTools.typeText(text, x, y, "true");

        // Verify clipboard was set for each non-ASCII character
        verify(clipboard, times(2)).setContents(any(StringSelection.class), any());
        
        // Verify Ctrl+V was pressed for each character (plus once for Ctrl+A during clear)
        // Clear: Ctrl+A, Backspace, then two characters: Ctrl+V (twice)
        verify(robot, times(3)).keyPress(KeyEvent.VK_CONTROL); // 1x for clear (Ctrl+A), 2x for paste
        verify(robot, times(2)).keyPress(KeyEvent.VK_V);
        verify(robot, times(3)).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot, times(2)).keyRelease(KeyEvent.VK_V);
    }
}
