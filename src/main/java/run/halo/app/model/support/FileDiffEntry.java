package run.halo.app.model.support;

import lombok.Data;

/**
 * Represents a file-level difference between two theme versions.
 *
 * @author halo-dev
 */
@Data
public class FileDiffEntry {

    /**
     * Relative path within the theme directory (e.g., "templates/post.ftl").
     */
    private String relativePath;

    /**
     * The type of difference.
     */
    private DiffType diffType;

    /**
     * Whether this entry is a regular file (true) or a directory (false).
     */
    private boolean isFile;

    /**
     * Types of file differences.
     */
    public enum DiffType {
        /** File exists only in the new theme. */
        ADDED,
        /** File exists in both but content differs. */
        MODIFIED,
        /** File exists only in the old theme. */
        DELETED,
        /** File exists in both with identical content. */
        UNCHANGED
    }
}
