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
package org.tarik.ta.prompts;

import dev.langchain4j.data.message.Content;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.dto.UiElementIdentificationResult;
import org.tarik.ta.rag.model.UiElement;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class SelectBestUiElementPrompt extends StructuredResponsePrompt<UiElementIdentificationResult> {
    private static final String SYSTEM_PROMPT_FILE_NAME = "find_best_matching_ui_element_id.txt";
    private static final String ELEMENT_NAME_PLACEHOLDER = "element_name";
    private static final String ELEMENT_OWN_DESCRIPTION_PLACEHOLDER = "element_own_description";
    private static final String ELEMENT_ANCHORS_DESCRIPTION_PLACEHOLDER = "element_anchors_description";
    private static final String ELEMENT_TEST_DATA_PLACEHOLDER = "element_test_data";
    private static final String DATA_DEPENDENT_ATTRIBUTES_PLACEHOLDER = "data_dependent_attributes";

    private final BufferedImage screenshot;
    private final String userMessageTemplate;

    private SelectBestUiElementPrompt(@NotNull Map<String, String> systemMessagePlaceholders,
                                      @NotNull Map<String, String> userMessagePlaceholders,
                                      @NotNull BufferedImage screenshot,
                                      String userMessageTemplate) {
        super(systemMessagePlaceholders, userMessagePlaceholders);
        this.screenshot = screenshot;
        this.userMessageTemplate = userMessageTemplate;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected String getUserMessageTemplate() {
        return userMessageTemplate;
    }

    @Override
    protected List<Content> getUserMessageAdditionalContents() {
        return List.of(singleImageContent(screenshot));
    }

    @Override
    protected String getSystemMessageTemplate() {
        return getSystemPromptFileContent(SYSTEM_PROMPT_FILE_NAME);
    }

    @NotNull
    @Override
    public Class<UiElementIdentificationResult> getResponseObjectClass() {
        return UiElementIdentificationResult.class;
    }

    public static class Builder {
        private UiElement uiElement;
        private BufferedImage screenshot;
        private List<String> boundingBoxIds;
        private String elementTestData;

        public Builder withUiElement(@NotNull UiElement uiElement) {
            this.uiElement = uiElement;
            return this;
        }

        public Builder withScreenshot(@NotNull BufferedImage screenshot) {
            this.screenshot = requireNonNull(screenshot, "Screenshot cannot be null");
            return this;
        }

        public Builder withBoundingBoxIds(@NotNull List<String> boundingBoxIds) {
            this.boundingBoxIds = requireNonNull(boundingBoxIds, "Bounding box IDs cannot be null");
            return this;
        }

        public Builder withElementTestData(String elementTestData) {
            this.elementTestData = elementTestData;
            return this;
        }

        public SelectBestUiElementPrompt build() {
            checkArgument(nonNull(uiElement), "UI element must be set");
            checkArgument(nonNull(boundingBoxIds) && !boundingBoxIds.isEmpty(), "Bounding box IDs cannot be null or empty");

            Map<String, String> userMessagePlaceholders = new HashMap<>();
            userMessagePlaceholders.put(ELEMENT_NAME_PLACEHOLDER, uiElement.name());
            userMessagePlaceholders.put(ELEMENT_OWN_DESCRIPTION_PLACEHOLDER, uiElement.description());
            userMessagePlaceholders.put(ELEMENT_ANCHORS_DESCRIPTION_PLACEHOLDER, uiElement.locationDetails());

            String userMessageTemplateString;
            String boundingBoxIdsString = "Bounding box IDs: %s.".formatted(String.join(", ", boundingBoxIds));

            if (isNotBlank(elementTestData) && !uiElement.dataDependentAttributes().isEmpty()) {
                userMessagePlaceholders.put(ELEMENT_TEST_DATA_PLACEHOLDER, elementTestData);
                userMessagePlaceholders.put(DATA_DEPENDENT_ATTRIBUTES_PLACEHOLDER, String.join(", ", uiElement.dataDependentAttributes()));
                userMessageTemplateString = """
                    The target element:
                    "{{%s}}. {{%s}} {{%s}}"
                    
                    This element is data-dependent.
                    The element attributes which depend on the test data: [{{%s}}].
                    Available test data for this element: "{{%s}}"
                    
                    %s
                    """.formatted(ELEMENT_NAME_PLACEHOLDER, ELEMENT_OWN_DESCRIPTION_PLACEHOLDER, ELEMENT_ANCHORS_DESCRIPTION_PLACEHOLDER,
                        DATA_DEPENDENT_ATTRIBUTES_PLACEHOLDER, ELEMENT_TEST_DATA_PLACEHOLDER, boundingBoxIdsString);
            } else {
                userMessageTemplateString = """
                    The target element: "{{%s}}. {{%s}} {{%s}}"
                    
                    %s
                    """.formatted(ELEMENT_NAME_PLACEHOLDER, ELEMENT_OWN_DESCRIPTION_PLACEHOLDER, ELEMENT_ANCHORS_DESCRIPTION_PLACEHOLDER,
                        boundingBoxIdsString);
            }

            return new SelectBestUiElementPrompt(Map.of(), userMessagePlaceholders, screenshot, userMessageTemplateString);
        }
    }
}