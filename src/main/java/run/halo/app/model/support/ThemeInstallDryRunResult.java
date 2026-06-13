package run.halo.app.model.support;

import java.util.List;
import lombok.Data;
import run.halo.app.handler.theme.config.support.Group;
import run.halo.app.handler.theme.config.support.ThemeProperty;

/**
 * Result of a dry-run preview for theme installation.
 *
 * <p>Contains all preview information collected without writing to the theme directory
 * or modifying the database.</p>
 *
 * @author halo-dev
 */
@Data
public class ThemeInstallDryRunResult {

    /**
     * Fully-parsed theme metadata (id, name, version, author, etc.).
     */
    private ThemeProperty themeProperty;

    /**
     * Whether a theme with the same id is already installed.
     */
    private boolean alreadyExists;

    /**
     * Whether the current Halo version satisfies the theme's require field.
     */
    private boolean versionCompatible;

    /**
     * The theme's required Halo version (from theme.yaml require field).
     */
    private String requiredHaloVersion;

    /**
     * The current Halo version.
     */
    private String currentHaloVersion;

    /**
     * Parsed configuration groups from settings.yaml. Empty if the theme has no settings.
     */
    private List<Group> configGroups;

    /**
     * Full file tree of the fetched theme.
     */
    private List<ThemeFile> themeFiles;
}
