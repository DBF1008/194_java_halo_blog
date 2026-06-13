package run.halo.app.model.support;

import lombok.Data;
import run.halo.app.handler.theme.config.support.Item;

/**
 * Represents a configuration item difference between two theme versions.
 *
 * @author halo-dev
 */
@Data
public class ConfigDiffEntry {

    /**
     * The configuration group (tab) name.
     */
    private String groupName;

    /**
     * The configuration item name (unique key within the group).
     */
    private String itemName;

    /**
     * The item from the old theme. Null if the item was added.
     */
    private Item oldItem;

    /**
     * The item from the new theme. Null if the item was removed.
     */
    private Item newItem;

    /**
     * The type of configuration difference.
     */
    private ConfigDiffType diffType;

    /**
     * Types of configuration differences.
     */
    public enum ConfigDiffType {
        /** Item exists only in the new theme's settings.yaml. */
        ADDED,
        /** Item exists only in the old theme's settings.yaml. */
        REMOVED,
        /** Item exists in both but properties differ. */
        MODIFIED,
        /** Item exists in both with identical properties. */
        UNCHANGED
    }
}
