package org.tarik.ta.exceptions;

/**
 * Exception thrown when an element cannot be located on the screen.
 * This exception provides detailed information about why the location failed.
 */
public class ElementLocationException extends RuntimeException {
    private final ElementLocationStatus status;

    public ElementLocationException(String locationFailureDescriptionReason, ElementLocationStatus status) {
        super(locationFailureDescriptionReason);
        this.status = status;
    }

    public ElementLocationStatus getStatus() {
        return status;
    }

    public enum ElementLocationStatus {
        NO_ELEMENTS_FOUND_IN_DB,
        SIMILAR_ELEMENTS_IN_DB_BUT_SCORE_TOO_LOW,
        ELEMENT_NOT_FOUND_ON_SCREEN_VISUAL_AND_ALGORITHMIC_FAILED,
        ELEMENT_NOT_FOUND_ON_SCREEN_VALIDATION_FAILED,
        UNKNOWN_ERROR
    }
}
