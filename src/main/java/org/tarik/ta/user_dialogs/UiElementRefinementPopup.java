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
 * distributed under the License is an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.user_dialogs;

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.dto.ElementRefinementOperation;
import org.tarik.ta.rag.model.UiElement;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;

import static java.awt.Image.SCALE_AREA_AVERAGING;
import static java.util.Optional.ofNullable;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class UiElementRefinementPopup extends AbstractDialog {
    private static final String DIALOG_TITLE = "UI Elements Refinement";
    private static final String ELEMENT_LABEL_FORMAT = "<html><body style='width: %dpx; font-size: %dpx;'>"
            + "<p><b>%s</b></p><p></p><p><i>%s</i></p></body></html>";
    private static final String ELEMENT_ACTION_DIALOG_TITLE = "Element Action";
    private static final String UPDATE_BUTTON_TEXT = "Update Element";
    private static final String DELETE_BUTTON_TEXT = "Delete Element";
    private static final String ELEMENT_ACTION_DIALOG_MESSAGE = "What do you want to do with this element ?";
    private static final int ELEMENT_ACTION_DIALOG_WIDTH = 300;
    private static final int ELEMENT_ACTION_DIALOG_HEIGHT = 150;
    private static final int ELEMENT_DESCRIPTION_FONT_SIZE = 8;
    private static final int IMAGE_TARGET_WIDTH = 100;
    private static final int ELEMENT_DESCRIPTION_LENGTH = 550;

    private ElementRefinementOperation result;

    private UiElementRefinementPopup(Window owner, String message, List<UiElement> itemsToRefine) {
        super(owner, DIALOG_TITLE);

        JPanel mainPanel = getDefaultMainPanel();
        var messageArea = getUserMessageArea(message);
        mainPanel.add(messageArea, BorderLayout.NORTH);

        JPanel elementPanel = new JPanel();
        elementPanel.setLayout(new BoxLayout(elementPanel, BoxLayout.Y_AXIS));
        itemsToRefine.forEach(element -> {
            var elementLabel = getElementLabel(element);
            setHoverAsClick(elementLabel);
            elementLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showElementActionDialog(element);
                }
            });
            elementLabel.setBorder(new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
            elementPanel.add(elementLabel);
        });

        JScrollPane scrollPane = new JScrollPane(elementPanel);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JButton doneButton = new JButton("Done");
        setHoverAsClick(doneButton);
        doneButton.addActionListener(_ -> {
            result = ElementRefinementOperation.done();
            dispose();
        });
        JPanel buttonsPanel = getButtonsPanel(doneButton);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);
        add(mainPanel);
        setDefaultSizeAndPosition(0.5, 0.6);
        setVisible(true);
        toFront();
    }

    private void showElementActionDialog(UiElement element) {
        JDialog dialog = new JDialog(this, ELEMENT_ACTION_DIALOG_TITLE, true);
        dialog.setLayout(new FlowLayout());

        JButton updateButton = new JButton(UPDATE_BUTTON_TEXT);
        setHoverAsClick(updateButton);
        updateButton.addActionListener(_ -> {
            result = ElementRefinementOperation.forUpdateElement(element.uuid());
            dialog.dispose();
            UiElementRefinementPopup.this.dispose();
        });

        JButton deleteButton = new JButton(DELETE_BUTTON_TEXT);
        setHoverAsClick(deleteButton);
        deleteButton.addActionListener(_ -> {
            result = ElementRefinementOperation.forDeleteElement(element.uuid());
            dialog.dispose();
            UiElementRefinementPopup.this.dispose();
        });

        JButton newScreenshotButton = new JButton("Update Screenshot");
        setHoverAsClick(newScreenshotButton);
        newScreenshotButton.addActionListener(_ -> {
            result = ElementRefinementOperation.forUpdateScreenshot(element.uuid());
            dialog.dispose();
            UiElementRefinementPopup.this.dispose();
        });

        dialog.add(new JLabel(ELEMENT_ACTION_DIALOG_MESSAGE));
        dialog.add(updateButton);
        dialog.add(deleteButton);
        dialog.add(newScreenshotButton);
        dialog.setSize(ELEMENT_ACTION_DIALOG_WIDTH, ELEMENT_ACTION_DIALOG_HEIGHT);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    @NotNull
    private JLabel getElementLabel(UiElement element) {
        var elementFullName =
                isNotBlank(element.pageSummary()) ? "%s belonging to %s".formatted(element.name(), element.pageSummary()) : element.name();
        String labelText = String.format(ELEMENT_LABEL_FORMAT, ELEMENT_DESCRIPTION_LENGTH, ELEMENT_DESCRIPTION_FONT_SIZE, elementFullName,
                element.description());
        JLabel label = new JLabel(labelText);
        label.setIcon(getImageIcon(element));
        label.setHorizontalTextPosition(SwingConstants.RIGHT);
        label.setVerticalTextPosition(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setIconTextGap(10);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return label;
    }

    @NotNull
    private static ImageIcon getImageIcon(UiElement element) {
        var elementScreenshot = element.screenshot().toBufferedImage();
        var originalWidth = elementScreenshot.getWidth();
        var scalingRatio = ((double) IMAGE_TARGET_WIDTH) / originalWidth;
        var imageTargetHeight = (int) (elementScreenshot.getHeight() * scalingRatio);
        return new ImageIcon(elementScreenshot.getScaledInstance(IMAGE_TARGET_WIDTH, imageTargetHeight, SCALE_AREA_AVERAGING));
    }

    public static Optional<ElementRefinementOperation> displayAndGetChoice(Window owner,
                                                                          @NotNull String message,
                                                                          @NotNull List<UiElement> elementsToRefine) {
        UiElementRefinementPopup popup = new UiElementRefinementPopup(owner, message, elementsToRefine);
        return ofNullable(popup.result);
    }

    @Override
    protected void onDialogClosing() {
        if (result == null) {
            result = ElementRefinementOperation.done();
        }
    }
}
