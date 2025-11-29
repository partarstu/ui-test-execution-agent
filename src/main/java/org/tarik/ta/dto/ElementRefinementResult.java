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
import org.tarik.ta.annotations.JsonClassDescription;
import org.tarik.ta.annotations.JsonFieldDescription;
import org.tarik.ta.rag.model.UiElement;

import java.util.List;

/**
 * Result of the UI element refinement workflow.
 * This workflow allows users to update or delete existing UI elements from the database.
 */
@Description("Result of refining existing UI elements through user interaction")
public record ElementRefinementResult(
        @Description("Whether the refinement process completed successfully") boolean success,
        @Description("Total number of elements modified (updated or deleted)") int modificationCount,
        @Description("Whether the user interrupted the refinement process") boolean interrupted,
        @Description("Additional message or details about the refinement") String message
) {
    /**
     * Factory method for successful refinement.
     */
    public static ElementRefinementResult success(List<UiElement> updated, List<UiElement> deleted) {
        int count = updated.size() + deleted.size();
        String msg = String.format("Refinement completed: %d updated, %d deleted", updated.size(), deleted.size());
        return new ElementRefinementResult(true, count, false, msg);
    }

    /**
     * Factory method for interrupted refinement.
     */
    public static ElementRefinementResult wasInterrupted(String cause) {
        return new ElementRefinementResult(false, 0, true, cause);
    }

    /**
     * Factory method for no changes made.
     */
    public static ElementRefinementResult noChanges() {
        return new ElementRefinementResult(true, 0, false, "Refinement completed with no changes");
    }

    /**
     * Factory method for failed result.
     */
    public static ElementRefinementResult failure(String reason) {
        return new ElementRefinementResult(false, 0, false, reason);
    }
}
