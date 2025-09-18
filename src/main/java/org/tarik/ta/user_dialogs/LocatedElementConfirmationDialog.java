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
import java.awt.image.BufferedImage;

import static org.tarik.ta.utils.BoundingBoxUtil.drawBoundingBox;
import static org.tarik.ta.utils.ImageUtils.cloneImage;
import static org.tarik.ta.utils.ImageUtils.toBufferedImage;

public class LocatedElementConfirmationDialog extends AbstractDialog {

    private UserChoice userChoice;

    public enum UserChoice {
        CORRECT,
        INCORRECT,
        INTERRUPTED
    }

    private LocatedElementConfirmationDialog(BufferedImage screenshot, Rectangle boundingBox, Color boundingBoxColor, String elementDescription) {
        super("Confirm Element Location for: " + elementDescription);
        setupUI(screenshot, boundingBox, boundingBoxColor);
    }

    private void setupUI(BufferedImage screenshot, Rectangle boundingBox, Color boundingBoxColor) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        double defaultZoomOutFactor = 0.9;
        BufferedImage scaledScreenshot = scaleImage(screenshot,
                (int) (screenSize.width * defaultZoomOutFactor), (int) (screenSize.height * defaultZoomOutFactor));
        Rectangle scaledBoundingBox = scaleRectangle(boundingBox, screenshot.getWidth(), screenshot.getHeight(),
                scaledScreenshot.getWidth(), scaledScreenshot.getHeight());

        BufferedImage screenshotWithBox = cloneImage(scaledScreenshot);
        drawBoundingBox(screenshotWithBox, scaledBoundingBox, boundingBoxColor);

        JLabel imageLabel = new JLabel(new ImageIcon(screenshotWithBox));
        JScrollPane scrollPane = new JScrollPane(imageLabel);

        JButton correctButton = new JButton("Correct");
        correctButton.addActionListener(e -> {
            userChoice = UserChoice.CORRECT;
            dispose();
        });

        JButton incorrectButton = new JButton("Incorrect");
        incorrectButton.addActionListener(e -> {
            userChoice = UserChoice.INCORRECT;
            dispose();
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(correctButton);
        buttonPanel.add(incorrectButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        displayPopup();
    }

    private BufferedImage scaleImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        Image scaledImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
        return toBufferedImage(scaledImage, targetWidth, targetHeight);
    }

    private Rectangle scaleRectangle(Rectangle originalRect, int originalWidth, int originalHeight, int scaledWidth, int scaledHeight) {
        double scaleX = (double) scaledWidth / originalWidth;
        double scaleY = (double) scaledHeight / originalHeight;
        return new Rectangle(
                (int) (originalRect.x * scaleX),
                (int) (originalRect.y * scaleY),
                (int) (originalRect.width * scaleX),
                (int) (originalRect.height * scaleY)
        );
    }

    @Override
    protected void onDialogClosing() {
        if (userChoice == null) {
            userChoice = UserChoice.INTERRUPTED;
        }
    }

    private UserChoice getChoice() {
        return userChoice;
    }

    public static UserChoice displayAndGetUserChoice(BufferedImage screenshot, Rectangle boundingBox, Color boundingBoxColor, String elementDescription) {
        LocatedElementConfirmationDialog dialog = new LocatedElementConfirmationDialog(screenshot, boundingBox, boundingBoxColor, elementDescription);
        waitForUserInteractions(dialog);
        UserChoice choice = dialog.getChoice();
        return choice == null ? UserChoice.INTERRUPTED : choice;
    }
}
