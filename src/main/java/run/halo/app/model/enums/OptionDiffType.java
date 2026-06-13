package run.halo.app.model.enums;

/**
 * Describes how a single option key changed between a snapshot and the current configuration.
 *
 * <p>The perspective is "what happened since the snapshot was taken".
 *
 * @author halo
 */
public enum OptionDiffType {

    /**
     * Present in the current configuration but not in the snapshot (added after the snapshot).
     */
    ADDED,

    /**
     * Present in the snapshot but not in the current configuration (removed after the snapshot).
     */
    DELETED,

    /**
     * Present in both but with a different value.
     */
    MODIFIED,

    /**
     * Present in both with the same value.
     */
    UNCHANGED
}
