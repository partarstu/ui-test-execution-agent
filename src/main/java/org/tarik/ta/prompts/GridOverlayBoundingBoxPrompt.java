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
import org.tarik.ta.dto.BoundingBox;
import org.tarik.ta.rag.model.UiElement;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class GridOverlayBoundingBoxPrompt extends StructuredResponsePrompt<BoundingBox> {
    private static final String SYSTEM_PROMPT_FILE_NAME = "grid_based_bounding_box_prompt.txt";
    private static final String ELEMENT_NAME_PLACEHOLDER = "element_name";
    private static final String ELEMENT_DESCRIPTION_PLACEHOLDER = "element_description";

    private final BufferedImage screenshot;

    private GridOverlayBoundingBoxPrompt(@NotNull Map<String, String> systemMessagePlaceholders, @NotNull BufferedImage screenshot) {
        super(systemMessagePlaceholders, Map.of());
        this.screenshot = screenshot;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected List<Content> getUserMessageAdditionalContents() {
        return List.of(singleImageContent(screenshot));
    }

    @Override
    protected String getUserMessageTemplate() {
        return "";
    }

    @Override
    protected String getSystemMessageTemplate() {
        return getSystemPromptFileContent(SYSTEM_PROMPT_FILE_NAME);
    }

    @NotNull
    @Override
    public Class<BoundingBox> getResponseObjectClass() {
        return BoundingBox.class;
    }

    public static class Builder {
        private UiElement uiElement;
        private BufferedImage screenshot;

        public Builder withUiElement(@NotNull UiElement uiElement) {
            this.uiElement = uiElement;
            return this;
        }

        public Builder withScreenshot(@NotNull BufferedImage screenshot) {
            this.screenshot = screenshot;
            return this;
        }

        public GridOverlayBoundingBoxPrompt build() {
            checkArgument(uiElement != null, "UI element must be set");
            checkArgument(screenshot != null, "Screenshot must be set");

            String description = isNotBlank(uiElement.ownDescription()) ? uiElement.ownDescription() : uiElement.name();
            Map<String, String> systemMessagePlaceholders = Map.of(
                    ELEMENT_NAME_PLACEHOLDER, uiElement.name(),
                    ELEMENT_DESCRIPTION_PLACEHOLDER, description
            );
            return new GridOverlayBoundingBoxPrompt(systemMessagePlaceholders, screenshot);
        }
    }
}