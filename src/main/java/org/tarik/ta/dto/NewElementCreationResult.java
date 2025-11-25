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
package org.tarik.ta.dto;

import dev.langchain4j.model.output.structured.Description;

/**
 * Result of the new UI element creation workflow.
 * This workflow includes capturing element screenshot, collecting element information,
 * and persisting the element to the database.
 */
@Description("Result of creating a new UI element through user interaction")
public record NewElementCreationResult(
        @Description("Whether the element was successfully created") boolean success,
        @Description("Whether the user interrupted the creation process") boolean interrupted,
        @Description("Additional message or error details") String message
) {
    /**
     * Factory method for successful element creation.
     */
    public static NewElementCreationResult success() {
        return new NewElementCreationResult(true, false, "Element created successfully");
    }

    /**
     * Factory method for interrupted creation.
     */
    public static NewElementCreationResult interrupted(String reason) {
        return new NewElementCreationResult(false, true, reason);
    }

    /**
     * Factory method for failed creation.
     */
    public static NewElementCreationResult failure(String reason) {
        return new NewElementCreationResult(false, false, reason);
    }
}
