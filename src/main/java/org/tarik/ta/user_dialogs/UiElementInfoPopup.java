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

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.rag.model.UiElement;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class UiElementInfoPopup extends AbstractDialog {
    private static final int FONT_SIZE = 4;
    private final JTextArea nameField;
    private final JTextArea descriptionArea;
    private final JTextArea anchorsArea;
    private final JTextArea pageSummaryArea;
    private final JCheckBox dataDependentCheckBox;
    private final JCheckBox zoomInNeededCheckBox;
    private final JTextArea dataDependentAttributesArea;
    private final DefaultListModel<String> dataDependentAttributesListModel;
    private final UiElement originalElement;
    private boolean windowClosedByUser = false;

    private UiElementInfoPopup(UiElement originalElement) {
        super("UI Element Info");

        this.originalElement = originalElement;
        JPanel panel = getDefaultMainPanel();
        var userMessageArea = getUserMessageArea("Please revise, and if needed, modify the following info regarding the element");

        panel.add(new JScrollPane(userMessageArea), BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        nameField = addLabelWithValueField("Name", originalElement.name(), contentPanel);
        descriptionArea = addLabelWithValueField("Description", originalElement.description(), contentPanel);
        anchorsArea = addLabelWithValueField("Location Details", originalElement.locationDetails(), contentPanel);
        pageSummaryArea = addLabelWithValueField("Name or short description of the page on which the element is located",
                originalElement.pageSummary(), contentPanel);

        dataDependentCheckBox = new JCheckBox("Data-Driven Element", originalElement.isDataDependent());
        zoomInNeededCheckBox = new JCheckBox("Use Zoom for Precision", originalElement.zoomInRequired());

        JPanel dataDependentPanel = new JPanel();
        dataDependentPanel.setLayout(new BoxLayout(dataDependentPanel, BoxLayout.Y_AXIS));
        dataDependentPanel.setBorder(BorderFactory.createTitledBorder("Data-Dependent Attributes"));
        dataDependentAttributesArea = new JTextArea(5, 30);
        dataDependentAttributesListModel = new DefaultListModel<>();
        if (originalElement.dataDependentAttributes() != null) {
            originalElement.dataDependentAttributes().forEach(dataDependentAttributesListModel::addElement);
        }
        JList<String> dataDependentAttributesList = new JList<>(dataDependentAttributesListModel);
        JButton addAttributeButton = new JButton("Add");
        addAttributeButton.addActionListener(_ -> {
            String attribute = dataDependentAttributesArea.getText().trim();
            if (!attribute.isEmpty()) {
                dataDependentAttributesListModel.addElement(attribute);
                dataDependentAttributesArea.setText("");
            }
        });
        dataDependentPanel.add(new JScrollPane(dataDependentAttributesArea));
        dataDependentPanel.add(addAttributeButton);
        dataDependentPanel.add(new JScrollPane(dataDependentAttributesList));

        dataDependentPanel.setVisible(dataDependentCheckBox.isSelected());
        dataDependentCheckBox.addActionListener(_ -> {
            dataDependentPanel.setVisible(dataDependentCheckBox.isSelected());
            if (!dataDependentCheckBox.isSelected()) {
                dataDependentAttributesListModel.clear();
            }
        });

        contentPanel.add(dataDependentCheckBox);
        contentPanel.add(zoomInNeededCheckBox);
        contentPanel.add(dataDependentPanel);

        panel.add(contentPanel, BorderLayout.CENTER);

        JButton doneButton = new JButton("Done");
        doneButton.addActionListener(_ -> dispose());
        JPanel buttonsPanel = getButtonsPanel(doneButton);
        panel.add(buttonsPanel, BorderLayout.SOUTH);

        add(panel);
        setDefaultSizeAndPosition(0.5, 0.8);
        displayPopup();
    }

    @NotNull
    private JTextArea addLabelWithValueField(String label, String value, JPanel panel) {
        JTextArea nameField = new JTextArea(value.trim());
        nameField.setLineWrap(true);
        nameField.setWrapStyleWord(true);

        JLabel nameLabel = new JLabel(("<html><font size='%d'><b>%s:</b></font></html>").formatted(FONT_SIZE, label));
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(nameLabel, BorderLayout.WEST);

        JScrollPane scrollPane = new JScrollPane(nameField);
        scrollPane.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(scrollPane, BorderLayout.CENTER);
        return nameField;
    }

    @Override
    protected void onDialogClosing() {
        windowClosedByUser = true;
    }

    private UiElement getUiElement() {
        if (!windowClosedByUser) {
            java.util.List<String> attributes = new ArrayList<>();
            if (dataDependentCheckBox.isSelected()) {
                for (int i = 0; i < dataDependentAttributesListModel.size(); i++) {
                    attributes.add(dataDependentAttributesListModel.getElementAt(i));
                }
            }
            return new UiElement(originalElement.uuid(), nameField.getText().trim(), descriptionArea.getText().trim(),
                    anchorsArea.getText().trim(), pageSummaryArea.getText().trim(), originalElement.screenshot(),
                    zoomInNeededCheckBox.isSelected(), attributes);
        } else {
            return null;
        }
    }

    public static Optional<UiElement> displayAndGetUpdatedElement(@NotNull UiElement elementDraftFromModel) {
        var popup = new UiElementInfoPopup(elementDraftFromModel);
        waitForUserInteractions(popup);
        return ofNullable(popup.getUiElement());
    }
}