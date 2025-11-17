package org.tarik.ta.dto;

import java.util.UUID;

public record ElementRefinementOperation(Operation operation, UUID elementId) {
    public enum Operation {
        UPDATE_SCREENSHOT,
        UPDATE_ELEMENT,
        DELETE_ELEMENT,
        DONE
    }

    public static ElementRefinementOperation forUpdateScreenshot(UUID elementId) {
        return new ElementRefinementOperation(Operation.UPDATE_SCREENSHOT, elementId);
    }

    public static ElementRefinementOperation forUpdateElement(UUID elementId) {
        return new ElementRefinementOperation(Operation.UPDATE_ELEMENT, elementId);
    }

    public static ElementRefinementOperation forDeleteElement(UUID elementId) {
        return new ElementRefinementOperation(Operation.DELETE_ELEMENT, elementId);
    }

    public static ElementRefinementOperation done() {
        return new ElementRefinementOperation(Operation.DONE, null);
    }
}
