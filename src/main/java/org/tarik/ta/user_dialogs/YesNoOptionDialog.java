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

import javax.swing.*;
import java.awt.*;

import static javax.swing.JOptionPane.NO_OPTION;
import static javax.swing.JOptionPane.YES_OPTION;

public class YesNoOptionDialog extends AbstractDialog {
    private int userChoice = NO_OPTION;

    private YesNoOptionDialog(Window owner, String title, JPanel contentPanel) throws HeadlessException {
        super(owner, title);
        initializeDialog(contentPanel);
    }

    @Override
    protected void onDialogClosing() {
        userChoice = NO_OPTION;
    }

    private void initializeDialog(JPanel contentPanel) {
        var yesButton = new JButton("Yes");
        yesButton.addActionListener(_ -> {
            userChoice = YES_OPTION;
            dispose();
        });
        setHoverAsClick(yesButton);

        var noButton = new JButton("No");
        noButton.addActionListener(_ -> {
            userChoice = NO_OPTION;
            dispose();
        });
        setHoverAsClick(noButton);

        JPanel buttonsPanel = getButtonsPanel(yesButton, noButton);

        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(new JScrollPane(contentPanel), BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);
        pack();
        displayPopup();
    }

    public int getUserChoice() {
        return userChoice;
    }

    public static int display(Window owner, String title, JPanel contentPanel) {
        var dialog = new YesNoOptionDialog(owner, title, contentPanel);
        return dialog.getUserChoice();
    }
}