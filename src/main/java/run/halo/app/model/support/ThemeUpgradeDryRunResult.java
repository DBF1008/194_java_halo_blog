package run.halo.app.model.support;

import java.util.List;
import lombok.Data;
import run.halo.app.handler.theme.config.support.ThemeProperty;

/**
 * Result of a dry-run preview for theme upgrade.
 *
 * <p>Contains file-level and configuration-level diffs between the currently installed
 * theme and the newly fetched theme, along with compatibility and activation status.</p>
 *
 * @author halo-dev
 */
@Data
public class ThemeUpgradeDryRunResult {

    /**
     * The currently installed theme property.
     */
    private ThemeProperty currentTheme;

    /**
     * The newly fetched theme property (from temporary directory).
     */
    private ThemeProperty newTheme;

    /**
     * Whether the current Halo version satisfies the new theme's require field.
     */
    private boolean versionCompatible;

    /**
     * Whether the theme being updated is the currently activated theme.
     */
    private boolean affectsActivatedTheme;

    /**
     * File-level changes between old and new theme.
     */
    private List<FileDiffEntry> fileDiffs;

    /**
     * Configuration changes between old and new settings.yaml.
     */
    private List<ConfigDiffEntry> configDiffs;

    /**
     * Whether there are any file-level changes.
     */
    private boolean hasFileChanges;

    /**
     * Whether there are any configuration changes.
     */
    private boolean hasConfigChanges;
}
