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
package org.tarik.ta.user_dialogs;

import java.awt.*;

public class NewElementInfoNeededPopup extends AbstractConfirmationDialog {
    private NewElementInfoNeededPopup(Window owner, String elementDescription) {
        super(owner, "UI Element Not Found");

        initializeDialog(("I haven't found any UI element in my Database which matches the description '%s'." +
                " Please provide the info for the corresponding UI element.").formatted(elementDescription));
    }

    public static void display(Window owner, String elementDescription) {
        new NewElementInfoNeededPopup(owner, elementDescription);
    }
}
