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
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class SelectBestUiElementPrompt extends StructuredResponsePrompt<UiElementIdentificationResult> {
    private static final String SYSTEM_PROMPT_FILE_NAME = "find_best_matching_ui_element_id.txt";
    private static final String TARGET_ELEMENT_DESCRIPTION_PLACEHOLDER = "target_element_description";
    private final BufferedImage screenshot;
    private final List<String> boundingBoxColors;

    private SelectBestUiElementPrompt(@NotNull Map<String, String> systemMessagePlaceholders,
                                                      @NotNull Map<String, String> userMessagePlaceholders,
                                                      @NotNull BufferedImage screenshot,
                                                      @NotNull List<String> boundingBoxColors) {
        super(systemMessagePlaceholders, userMessagePlaceholders);
        this.screenshot = screenshot;
        this.boundingBoxColors = boundingBoxColors;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected String getUserMessageTemplate() {
        return """
                The target element: "{{%s}}".
                
                Bounding box IDs: %s.
                """
                .formatted(TARGET_ELEMENT_DESCRIPTION_PLACEHOLDER, boundingBoxColors);
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

        public SelectBestUiElementPrompt build() {
            checkArgument(!boundingBoxIds.isEmpty(), "Bounding box IDs cannot be empty");
            var targetElementDescription = "%s. %s %s"
                    .formatted(uiElement.name(), uiElement.ownDescription(), uiElement.anchorsDescription());
            Map<String, String> userMessagePlaceholders = Map.of(
                    TARGET_ELEMENT_DESCRIPTION_PLACEHOLDER, targetElementDescription
            );
            return new SelectBestUiElementPrompt(Map.of(), userMessagePlaceholders, screenshot, boundingBoxIds);
        }
    }
}