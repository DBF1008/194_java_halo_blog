package run.halo.app.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.app.handler.theme.config.support.Group;
import run.halo.app.handler.theme.config.support.Item;
import run.halo.app.handler.theme.config.support.Option;
import run.halo.app.model.enums.DataType;
import run.halo.app.model.enums.InputType;
import run.halo.app.model.support.ConfigDiffEntry;
import run.halo.app.model.support.ConfigDiffEntry.ConfigDiffType;

/**
 * Theme config comparator test.
 *
 * @author halo-dev
 */
class ThemeConfigComparatorTest {

    @Test
    void identicalConfigs() {
        List<Group> groups = List.of(createGroup("general",
                createItem("title", "Blog Title", InputType.TEXT, DataType.STRING, "My Blog")));

        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(groups, groups);

        assertEquals(1, diffs.size());
        assertEquals(ConfigDiffType.UNCHANGED, diffs.get(0).getDiffType());
    }

    @Test
    void addedItems() {
        List<Group> oldGroups = List.of(createGroup("general",
                createItem("title", "Blog Title", InputType.TEXT, DataType.STRING, "My Blog")));
        List<Group> newGroups = List.of(createGroup("general",
                createItem("title", "Blog Title", InputType.TEXT, DataType.STRING, "My Blog"),
                createItem("subtitle", "Subtitle", InputType.TEXT, DataType.STRING, "")));

        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(oldGroups, newGroups);

        ConfigDiffEntry added = findByItem(diffs, "subtitle");
        assertNotNull(added);
        assertEquals(ConfigDiffType.ADDED, added.getDiffType());
        assertNull(added.getOldItem());
        assertNotNull(added.getNewItem());
    }

    @Test
    void removedItems() {
        List<Group> oldGroups = List.of(createGroup("general",
                createItem("title", "Blog Title", InputType.TEXT, DataType.STRING, "My Blog"),
                createItem("subtitle", "Subtitle", InputType.TEXT, DataType.STRING, "")));
        List<Group> newGroups = List.of(createGroup("general",
                createItem("title", "Blog Title", InputType.TEXT, DataType.STRING, "My Blog")));

        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(oldGroups, newGroups);

        ConfigDiffEntry removed = findByItem(diffs, "subtitle");
        assertNotNull(removed);
        assertEquals(ConfigDiffType.REMOVED, removed.getDiffType());
        assertNotNull(removed.getOldItem());
        assertNull(removed.getNewItem());
    }

    @Test
    void modifiedItems() {
        List<Group> oldGroups = List.of(createGroup("general",
                createItem("title", "Blog Title", InputType.TEXT, DataType.STRING, "My Blog")));
        List<Group> newGroups = List.of(createGroup("general",
                createItem("title", "Site Title", InputType.TEXT, DataType.STRING, "My Site")));

        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(oldGroups, newGroups);

        ConfigDiffEntry modified = findByItem(diffs, "title");
        assertNotNull(modified);
        assertEquals(ConfigDiffType.MODIFIED, modified.getDiffType());
        assertEquals("Blog Title", modified.getOldItem().getLabel());
        assertEquals("Site Title", modified.getNewItem().getLabel());
    }

    @Test
    void addedGroup() {
        List<Group> oldGroups = List.of(createGroup("general",
                createItem("title", "Title", InputType.TEXT, DataType.STRING, "")));
        List<Group> newGroups = Arrays.asList(
                createGroup("general",
                        createItem("title", "Title", InputType.TEXT, DataType.STRING, "")),
                createGroup("social",
                        createItem("github", "GitHub", InputType.TEXT, DataType.STRING, "")));

        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(oldGroups, newGroups);

        ConfigDiffEntry added = findByItem(diffs, "github");
        assertNotNull(added);
        assertEquals(ConfigDiffType.ADDED, added.getDiffType());
        assertEquals("social", added.getGroupName());
    }

    @Test
    void removedGroup() {
        List<Group> oldGroups = Arrays.asList(
                createGroup("general",
                        createItem("title", "Title", InputType.TEXT, DataType.STRING, "")),
                createGroup("social",
                        createItem("github", "GitHub", InputType.TEXT, DataType.STRING, "")));
        List<Group> newGroups = List.of(createGroup("general",
                createItem("title", "Title", InputType.TEXT, DataType.STRING, "")));

        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(oldGroups, newGroups);

        ConfigDiffEntry removed = findByItem(diffs, "github");
        assertNotNull(removed);
        assertEquals(ConfigDiffType.REMOVED, removed.getDiffType());
        assertEquals("social", removed.getGroupName());
    }

    @Test
    void nullOldGroups() {
        List<Group> newGroups = List.of(createGroup("general",
                createItem("title", "Title", InputType.TEXT, DataType.STRING, "")));

        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(null, newGroups);

        assertEquals(1, diffs.size());
        assertEquals(ConfigDiffType.ADDED, diffs.get(0).getDiffType());
    }

    @Test
    void nullNewGroups() {
        List<Group> oldGroups = List.of(createGroup("general",
                createItem("title", "Title", InputType.TEXT, DataType.STRING, "")));

        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(oldGroups, null);

        assertEquals(1, diffs.size());
        assertEquals(ConfigDiffType.REMOVED, diffs.get(0).getDiffType());
    }

    @Test
    void bothNull() {
        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(null, null);

        assertTrue(diffs.isEmpty());
    }

    @Test
    void bothEmpty() {
        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(
                Collections.emptyList(), Collections.emptyList());

        assertTrue(diffs.isEmpty());
    }

    @Test
    void modifiedTypeChange() {
        Item oldItem = createItem("color", "Color", InputType.TEXT, DataType.STRING, "#fff");
        Item newItem = createItem("color", "Color", InputType.COLOR, DataType.STRING, "#fff");

        List<Group> oldGroups = List.of(createGroup("style", oldItem));
        List<Group> newGroups = List.of(createGroup("style", newItem));

        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(oldGroups, newGroups);

        ConfigDiffEntry modified = findByItem(diffs, "color");
        assertNotNull(modified);
        assertEquals(ConfigDiffType.MODIFIED, modified.getDiffType());
    }

    @Test
    void modifiedOptionsChange() {
        Item oldItem = createItem("layout", "Layout", InputType.SELECT, DataType.STRING, "default");
        oldItem.setOptions(List.of(createOption("Default", "default")));

        Item newItem = createItem("layout", "Layout", InputType.SELECT, DataType.STRING, "default");
        newItem.setOptions(List.of(
                createOption("Default", "default"),
                createOption("Grid", "grid")));

        List<Group> oldGroups = List.of(createGroup("general", oldItem));
        List<Group> newGroups = List.of(createGroup("general", newItem));

        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(oldGroups, newGroups);

        ConfigDiffEntry modified = findByItem(diffs, "layout");
        assertNotNull(modified);
        assertEquals(ConfigDiffType.MODIFIED, modified.getDiffType());
    }

    @Test
    void resultsSortedByKey() {
        List<Group> newGroups = List.of(createGroup("z_group",
                createItem("z_item", "Z", InputType.TEXT, DataType.STRING, "")),
                createGroup("a_group",
                        createItem("a_item", "A", InputType.TEXT, DataType.STRING, "")));

        List<ConfigDiffEntry> diffs = ThemeConfigComparator.compare(null, newGroups);

        assertEquals("a_group.a_item",
                diffs.get(0).getGroupName() + "." + diffs.get(0).getItemName());
        assertEquals("z_group.z_item",
                diffs.get(1).getGroupName() + "." + diffs.get(1).getItemName());
    }

    private Group createGroup(String name, Item... items) {
        Group group = new Group();
        group.setName(name);
        group.setLabel(name);
        group.setItems(Arrays.asList(items));
        return group;
    }

    private Item createItem(String name, String label, InputType type, DataType dataType,
            Object defaultValue) {
        Item item = new Item();
        item.setName(name);
        item.setLabel(label);
        item.setType(type);
        item.setDataType(dataType);
        item.setDefaultValue(defaultValue);
        return item;
    }

    private Option createOption(String label, Object value) {
        Option option = new Option();
        option.setLabel(label);
        option.setValue(value);
        return option;
    }

    private ConfigDiffEntry findByItem(List<ConfigDiffEntry> diffs, String itemName) {
        return diffs.stream()
                .filter(d -> d.getItemName().equals(itemName))
                .findFirst()
                .orElse(null);
    }
}
