package run.halo.app.model.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.app.handler.theme.config.support.ThemeProperty;

/**
 * Theme install/upgrade dry-run preview result.
 *
 * <p>Describes what <em>would</em> happen if a theme were installed or upgraded, without
 * actually writing to the theme work directory or modifying any persistent state.</p>
 *
 * @author halo
 */
@Data
public class ThemePreviewDTO {

    /**
     * Resolved theme meta information of the candidate (incoming) theme.
     */
    private ThemeProperty themeProperty;

    /**
     * Whether this preview represents a fresh install or an upgrade of an existing theme.
     */
    private Operation operation;

    /**
     * Whether a theme with the same id is already installed.
     */
    private boolean existing;

    /**
     * Whether the candidate theme is compatible with the current Halo version.
     */
    private boolean compatible;

    /**
     * The Halo version required by the candidate theme (may be blank).
     */
    private String requiredVersion;

    /**
     * The current running Halo version used for the compatibility check.
     */
    private String currentVersion;

    /**
     * Whether performing this operation would affect the currently activated theme.
     */
    private boolean affectsActivatedTheme;

    /**
     * Whether the candidate theme provides a settings (options) configuration.
     */
    private boolean hasOptions;

    /**
     * Per-file changes that would be applied, sorted by path.
     */
    private List<FileChange> fileChanges = new ArrayList<>();

    /**
     * Number of files that would be newly added.
     */
    private int addedFileCount;

    /**
     * Number of files that would be overwritten with different content.
     */
    private int modifiedFileCount;

    /**
     * Number of files that would be deleted.
     */
    private int deletedFileCount;

    /**
     * Number of files that would remain unchanged.
     */
    private int unchangedFileCount;

    /**
     * Potential configuration (settings options) changes.
     */
    private OptionChanges optionChanges = new OptionChanges();

    /**
     * Preview operation type.
     */
    public enum Operation {

        /**
         * The theme is not yet installed; it would be added.
         */
        INSTALL,

        /**
         * The theme is already installed; it would be upgraded.
         */
        UPDATE
    }

    /**
     * File change type.
     */
    public enum ChangeType {

        /**
         * File only exists in the candidate theme.
         */
        ADD,

        /**
         * File exists in both, but content differs.
         */
        MODIFY,

        /**
         * File only exists in the currently installed theme.
         */
        DELETE,

        /**
         * File exists in both with identical content.
         */
        UNCHANGED
    }

    /**
     * A single file change entry.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileChange {

        /**
         * Theme-relative file path (using '/' as separator).
         */
        private String path;

        /**
         * The change that would be applied to this file.
         */
        private ChangeType type;
    }

    /**
     * Configuration option changes between the installed theme and the candidate theme.
     */
    @Data
    public static class OptionChanges {

        /**
         * Option names present only in the candidate theme.
         */
        private List<String> added = new ArrayList<>();

        /**
         * Option names present only in the installed theme.
         */
        private List<String> removed = new ArrayList<>();

        /**
         * Option names present in both but whose type or default value differs.
         */
        private List<String> changed = new ArrayList<>();
    }
}
