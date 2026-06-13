package run.halo.app.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import run.halo.app.handler.theme.config.support.Group;
import run.halo.app.handler.theme.config.support.Item;
import run.halo.app.model.support.ConfigDiffEntry;
import run.halo.app.model.support.ConfigDiffEntry.ConfigDiffType;

/**
 * Compares two lists of theme configuration groups and produces a config-level diff.
 *
 * <p>Used by the dry-run preview feature to show what configuration items would be
 * added, removed, or modified during a theme update.</p>
 *
 * @author halo-dev
 */
public final class ThemeConfigComparator {

    private ThemeConfigComparator() {
    }

    /**
     * Compares two lists of configuration groups and returns a list of config differences.
     *
     * @param oldGroups the configuration groups from the old theme (may be null or empty)
     * @param newGroups the configuration groups from the new theme (may be null or empty)
     * @return a sorted list of config diff entries
     */
    @NonNull
    public static List<ConfigDiffEntry> compare(@Nullable List<Group> oldGroups,
            @Nullable List<Group> newGroups) {

        Map<String, Item> oldItems = flattenGroups(oldGroups);
        Map<String, Item> newItems = flattenGroups(newGroups);

        // Use TreeSet to get deterministic ordering by composite key
        TreeSet<String> allKeys = new TreeSet<>();
        allKeys.addAll(oldItems.keySet());
        allKeys.addAll(newItems.keySet());

        List<ConfigDiffEntry> diffs = new ArrayList<>();

        for (String key : allKeys) {
            Item oldItem = oldItems.get(key);
            Item newItem = newItems.get(key);

            // Parse group and item name from composite key
            String groupName = key.substring(0, key.indexOf('.'));
            String itemName = key.substring(key.indexOf('.') + 1);

            if (oldItem != null && newItem != null) {
                // Both exist — compare properties
                ConfigDiffType type = areItemsEqual(oldItem, newItem)
                        ? ConfigDiffType.UNCHANGED : ConfigDiffType.MODIFIED;
                diffs.add(createEntry(groupName, itemName, oldItem, newItem, type));
            } else if (newItem != null) {
                diffs.add(createEntry(groupName, itemName, null, newItem, ConfigDiffType.ADDED));
            } else {
                diffs.add(createEntry(groupName, itemName, oldItem, null, ConfigDiffType.REMOVED));
            }
        }

        return diffs;
    }

    /**
     * Flattens a list of groups into a map keyed by "groupName.itemName".
     */
    private static Map<String, Item> flattenGroups(@Nullable List<Group> groups) {
        if (groups == null || groups.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Item> result = new LinkedHashMap<>();
        for (Group group : groups) {
            if (group.getItems() == null) {
                continue;
            }
            for (Item item : group.getItems()) {
                String key = group.getName() + "." + item.getName();
                result.put(key, item);
            }
        }
        return result;
    }

    /**
     * Compares two items field by field to determine if they are equal.
     */
    private static boolean areItemsEqual(@NonNull Item a, @NonNull Item b) {
        return Objects.equals(a.getLabel(), b.getLabel())
                && Objects.equals(a.getType(), b.getType())
                && Objects.equals(a.getDataType(), b.getDataType())
                && Objects.equals(a.getDefaultValue(), b.getDefaultValue())
                && Objects.equals(a.getPlaceholder(), b.getPlaceholder())
                && Objects.equals(a.getDescription(), b.getDescription())
                && Objects.equals(a.getOptions(), b.getOptions());
    }

    private static ConfigDiffEntry createEntry(String groupName, String itemName,
            Item oldItem, Item newItem, ConfigDiffType type) {
        ConfigDiffEntry entry = new ConfigDiffEntry();
        entry.setGroupName(groupName);
        entry.setItemName(itemName);
        entry.setOldItem(oldItem);
        entry.setNewItem(newItem);
        entry.setDiffType(type);
        return entry;
    }
}
