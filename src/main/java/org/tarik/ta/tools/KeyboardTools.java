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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.awt.Toolkit.getDefaultToolkit;
import static java.awt.event.KeyEvent.*;
import static java.lang.Character.isUpperCase;
import static java.util.Arrays.stream;
import static java.util.stream.IntStream.range;
import static org.tarik.ta.utils.CommonUtils.*;

public class KeyboardTools extends AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(KeyboardTools.class);
    private static final Map<String, Integer> actionableKeyCodeByNameMap = getActionableKeyCodesByName();
    private static final int MAX_KEY_INDEX = 120000;
    private static final int KEYBOARD_ACTION_DELAY_MILLIS = 500;
    private static final int AUTO_DELAY = 30;

    @Tool(value = "Presses the specified keyboard key. Use this tool when you need to press a single keyboard key.")
    public AgentExecutionResult<?> pressKey(
            @P(value = "The specific value of a keyboard key which needs to be pressed, e.g. 'Ctrl', " +
                    "'Enter', 'A', '1', 'Shift' etc.") String keyboardKey) {
        getRobot().setAutoDelay(AUTO_DELAY);
        if (keyboardKey == null || keyboardKey.isBlank()) {
            return getFailedToolExecutionResult("%s: In order to press a keyboard key it can't be empty"
                    .formatted(KeyboardTools.class.getSimpleName()), true);
        }
        int keyCode = getKeyCode(keyboardKey);
        getRobot().keyPress(keyCode);
        getRobot().keyRelease(keyCode);
        return getSuccessfulResult("Pressed '%s' key".formatted(keyboardKey));
    }

    @Tool(value = "Presses the specified sequence of keyboard keys. Use this tool when you need to press a combination or sequence of" +
            " multiple keyboard keys at the same time."    )
    public AgentExecutionResult<?> pressKeys(@P("A non-empty array of values each representing the keyboard key which needs to be " +
            "pressed, e.g. 'Ctrl', 'Enter', 'A', '1', 'Shift' etc.") String... keyboardKeys) {
        getRobot().setAutoDelay(AUTO_DELAY);
        if (keyboardKeys == null || keyboardKeys.length == 0) {
            return getFailedToolExecutionResult("%s: In order to press keyboard keys combination it can't be empty"
                    .formatted(KeyboardTools.class.getSimpleName()), true);
        }

        var validKeys = stream(keyboardKeys)
                .filter(key -> key != null && !key.isBlank())
                .toList();

        if (validKeys.isEmpty()) {
            return getFailedToolExecutionResult("%s: All keys provided are empty"
                    .formatted(KeyboardTools.class.getSimpleName()), true);
        }

        validKeys.stream().map(KeyboardTools::getKeyCode).forEach(getRobot()::keyPress);
        validKeys.stream().map(KeyboardTools::getKeyCode).forEach(getRobot()::keyRelease);
        var message = "Pressed the following keys combination: '%s'".formatted(String.join(" + ", validKeys));
        return getSuccessfulResult(message);
    }

    @Tool(value = "Types (enters, inputs) the specified text using the keyboard. Normally you would first click the element with a " +
            "mouse in order to get the focus on the element and only then call this tool. If the content of the target UI " +
            "element might not be empty, it can be wiped out before typing if the corresponding boolean parameter is set.")
    public AgentExecutionResult<?> typeText(
            @P(value = "The text to be typed.") String text,
            @P(value = "A boolean which defines if existing contents of the UI element, in which the text should be input, need to be " +
                    "wiped out before input") String wipeOutOldContent) {
        getRobot().setAutoDelay(AUTO_DELAY);
        if (text == null) {
            return getFailedToolExecutionResult("%s: Text which needs to be input can't be NULL"
                    .formatted(KeyboardTools.class.getSimpleName()), true);
        }

        if (isNotBlank(wipeOutOldContent) && !List.of("true", "false").contains(wipeOutOldContent.trim().toLowerCase())) {
            return getFailedToolExecutionResult(("%s: Got incorrect value for the variable which defines if the content should be wiped " +
                    "out. Expected boolean value, got : {%s}")
                    .formatted(KeyboardTools.class.getSimpleName(), wipeOutOldContent), true);
        }


        if (isBlank(wipeOutOldContent) || Boolean.parseBoolean(wipeOutOldContent)) {
            selectAndDeleteContent();
        }

        for (char ch : text.toCharArray()) {
            if (isAsciiPrintable(ch)) {
                try {
                    typeCharacter(ch);
                } catch (Exception e) {
                    LOG.info("Couldn't type '{}' character using keyboard keys, falling back to copy-paste.", ch, e);
                    copyPaste(ch);
                }
            } else {
                copyPaste(ch);
            }
        }
        return getSuccessfulResult("Input the following text using keyboard: %s".formatted(text));
    }

    @Tool(value = "Clears (wipes out) data inside some input UI element by first selecting the whole content and then clicking the delete" +
            " button. Normally you would first click the element with a mouse in order to get the focus.")
    public AgentExecutionResult<?> clearData(            ) {
        getRobot().setAutoDelay(AUTO_DELAY);
        selectAndDeleteContent();
        return getSuccessfulResult("Cleared the contents using keyboard");
    }


    private static void selectAndDeleteContent() {
        getRobot().keyPress(VK_CONTROL);
        getRobot().keyPress(VK_A);
        getRobot().keyRelease(VK_CONTROL);
        getRobot().keyRelease(VK_A);
        getRobot().keyPress(VK_BACK_SPACE);
        getRobot().keyRelease(VK_BACK_SPACE);
        sleepMillis(KEYBOARD_ACTION_DELAY_MILLIS);
    }

    private static void copyPaste(char ch) {
        try {
            getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(String.valueOf(ch)), null);
            getRobot().keyPress(VK_CONTROL);
            getRobot().keyPress(VK_V);
            getRobot().keyRelease(VK_CONTROL);
            getRobot().keyRelease(VK_V);
        } catch (Exception ex) {
            String message = "Got error while copy-pasting '%s' character.".formatted(ch);
            LOG.error(message, ex);
            throw new RuntimeException(ex);
        }
    }

    private static boolean isAsciiPrintable(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static void typeCharacter(char ch) {
        try {
            int keyCode = getKeyCode(ch).orElseGet(() -> getKeyCode(String.valueOf(ch)));
            if (isUpperCase(ch)) {
                getRobot().keyPress(VK_SHIFT);
            }
            getRobot().keyPress(keyCode);
            getRobot().keyRelease(keyCode);
            if (isUpperCase(ch)) {
                getRobot().keyRelease(VK_SHIFT);
            }
        } catch (IllegalArgumentException e) {
            LOG.error("Can't type character '{}' as it can't be mapped to a key code. Trying to fall back to copy-paste", ch);
            throw e;
        }
    }

    private static int getKeyCode(String keyboardKeyName) {
        if (!actionableKeyCodeByNameMap.containsKey(keyboardKeyName.toLowerCase())) {
            throw new IllegalArgumentException("There is no keyboard key with the name '%s'".formatted(keyboardKeyName));
        }
        return actionableKeyCodeByNameMap.get(keyboardKeyName.toLowerCase());
    }

    private static Map<String, Integer> getActionableKeyCodesByName() {
        Map<String, Integer> result = new HashMap<>();
        range(0, MAX_KEY_INDEX)
                .filter(ind -> ind != VK_UNDEFINED)
                .forEach(ind -> {
                    String keyText = getKeyText(ind);
                    String lowerCaseKeyText = keyText.toLowerCase();
                    if (!lowerCaseKeyText.contains("unknown")) {
                        result.put(lowerCaseKeyText, ind);
                    }
                });
        result.put("ctrl", VK_CONTROL);
        result.put("alt", VK_ALT);
        result.put("shift", VK_SHIFT);
        result.put("enter", VK_ENTER);
        result.put("backspace", VK_BACK_SPACE);
        result.put("delete", VK_DELETE);
        result.put("tab", VK_TAB);
        result.put("escape", VK_ESCAPE);
        result.put("up", VK_UP);
        result.put("down", VK_DOWN);
        result.put("left", VK_LEFT);
        result.put("right", VK_RIGHT);
        result.put("home", VK_HOME);
        result.put("end", VK_END);
        result.put("page up", VK_PAGE_UP);
        result.put("page down", VK_PAGE_DOWN);
        result.put("caps lock", VK_CAPS_LOCK);
        result.put("num lock", VK_NUM_LOCK);
        result.put("scroll lock", VK_SCROLL_LOCK);
        result.put("print screen", VK_PRINTSCREEN);
        result.put("pause", VK_PAUSE);
        result.put("insert", VK_INSERT);
        return result;
    }

    private static Optional<Integer> getKeyCode(char c) {
        return Optional.of(KeyEvent.getExtendedKeyCodeForChar(c)).filter(code -> code != VK_UNDEFINED);
    }
}
