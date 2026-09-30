package io.picstr.app.model;

/** State of the background processing of an upload (HEIC conversion, thumbnail). */
public enum ProcessingStatus {
    /** Stored, waiting for or in background processing; the thumbnail may not exist yet. */
    PROCESSING,
    /** Fully processed. */
    READY,
    /** Background processing failed; the original is kept as uploaded. */
    FAILED
}
