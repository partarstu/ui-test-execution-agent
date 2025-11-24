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
package org.tarik.ta.utils;

import dev.langchain4j.data.message.ImageContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.ImageContent.DetailLevel.HIGH;

public class PromptUtils {
    private static final Logger LOG = LoggerFactory.getLogger(PromptUtils.class);
    private static final String DEFAULT_IMAGE_FORMAT = "png";

    public static String loadSystemPrompt(String agentPath, String version, String fileName) {
        String path = "prompt_templates/system/agents/" + agentPath + "/" + version + "/" + fileName;
        LOG.info("Loading system prompt from: {}", path);
        try (InputStream inputStream = PromptUtils.class.getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Prompt file not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt from " + path, e);
        }
    }

    public static ImageContent singleImageContent(BufferedImage image) {
        return ImageContent.from(ImageUtils.getImage(image, DEFAULT_IMAGE_FORMAT), HIGH);
    }
}
