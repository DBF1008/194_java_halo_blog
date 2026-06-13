package run.halo.app.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.halo.app.handler.theme.config.support.ThemeProperty;
import run.halo.app.model.dto.ThemePreviewDTO;
import run.halo.app.model.dto.ThemePreviewDTO.ChangeType;
import run.halo.app.model.dto.ThemePreviewDTO.FileChange;
import run.halo.app.model.dto.ThemePreviewDTO.Operation;

/**
 * Pure, network-free unit tests for {@link ThemePreviewer}.
 *
 * <p>Each test builds candidate / existing theme folders in a {@link TempDir} and asserts the
 * computed change set. The previewer never writes to either folder, so these tests only read
 * back the returned DTO.</p>
 *
 * @author halo
 */
class ThemePreviewerTest {

    /**
     * Minimal {@code theme.yaml}. Its content is irrelevant to the previewer (which never parses
     * it) but the file must exist so {@code ThemeMetaLocator} can locate the theme root and,
     * from there, the settings file.
     */
    private static final String THEME_YAML = "id: t\nname: Test\nversion: 1.0.0\n";

    @TempDir
    Path tempDir;

    private void writeFile(Path dir, String relativePath, String content) throws IOException {
        final var target = dir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private ThemeProperty property(Path root, String id, String require) {
        final var property = new ThemeProperty();
        property.setId(id);
        property.setThemePath(root.toString());
        property.setRequire(require);
        return property;
    }

    private Map<String, ChangeType> byPath(ThemePreviewDTO dto) {
        return dto.getFileChanges().stream()
            .collect(Collectors.toMap(FileChange::getPath, FileChange::getType));
    }

    @Test
    void installMarksEveryFileAsAdd() throws IOException {
        final var candidate = tempDir.resolve("candidate");
        writeFile(candidate, "theme.yaml", THEME_YAML);
        writeFile(candidate, "index.ftl", "index");
        writeFile(candidate, "module/macro.ftl", "macro");

        final var dto = ThemePreviewer.INSTANCE.preview(
            property(candidate, "t", "1.0.0"), null, "2.0.0", false);

        assertEquals(Operation.INSTALL, dto.getOperation());
        assertFalse(dto.isExisting());
        assertEquals(3, dto.getAddedFileCount());
        assertEquals(0, dto.getModifiedFileCount());
        assertEquals(0, dto.getDeletedFileCount());
        assertEquals(0, dto.getUnchangedFileCount());
        assertEquals(3, dto.getFileChanges().size());
        dto.getFileChanges().forEach(change -> assertEquals(ChangeType.ADD, change.getType()));
        // current 2.0.0 satisfies require 1.0.0
        assertTrue(dto.isCompatible());
        assertEquals("1.0.0", dto.getRequiredVersion());
        assertEquals("2.0.0", dto.getCurrentVersion());
    }

    @Test
    void compatibilityFollowsRequiredVersion() throws IOException {
        final var candidate = tempDir.resolve("candidate");
        writeFile(candidate, "theme.yaml", THEME_YAML);
        writeFile(candidate, "index.ftl", "index");

        // current 1.0.0 does NOT satisfy require 2.0.0
        final var incompatible = ThemePreviewer.INSTANCE.preview(
            property(candidate, "t", "2.0.0"), null, "1.0.0", false);
        assertFalse(incompatible.isCompatible());

        // blank require is always compatible
        final var blankRequire = ThemePreviewer.INSTANCE.preview(
            property(candidate, "t", ""), null, "1.0.0", false);
        assertTrue(blankRequire.isCompatible());
    }

    @Test
    void updateClassifiesAddModifyDeleteUnchanged() throws IOException {
        final var existing = tempDir.resolve("existing");
        writeFile(existing, "theme.yaml", THEME_YAML);
        writeFile(existing, "index.ftl", "old");
        writeFile(existing, "old.ftl", "to-be-removed");
        writeFile(existing, "same.ftl", "identical");

        final var candidate = tempDir.resolve("candidate");
        writeFile(candidate, "theme.yaml", THEME_YAML);
        writeFile(candidate, "index.ftl", "new");
        writeFile(candidate, "a.ftl", "added");
        writeFile(candidate, "same.ftl", "identical");

        final var dto = ThemePreviewer.INSTANCE.preview(
            property(candidate, "t", null), existing, "2.0.0", false);

        assertEquals(Operation.UPDATE, dto.getOperation());
        assertTrue(dto.isExisting());

        final var changes = byPath(dto);
        assertEquals(ChangeType.ADD, changes.get("a.ftl"));
        assertEquals(ChangeType.MODIFY, changes.get("index.ftl"));
        assertEquals(ChangeType.DELETE, changes.get("old.ftl"));
        assertEquals(ChangeType.UNCHANGED, changes.get("same.ftl"));
        // theme.yaml is identical in both folders
        assertEquals(ChangeType.UNCHANGED, changes.get("theme.yaml"));

        assertEquals(1, dto.getAddedFileCount());
        assertEquals(1, dto.getModifiedFileCount());
        assertEquals(1, dto.getDeletedFileCount());
        assertEquals(2, dto.getUnchangedFileCount());
    }

    @Test
    void updateDetectsOptionChanges() throws IOException {
        final var oldSettings = ""
            + "- name: general\n"
            + "  label: General\n"
            + "  items:\n"
            + "    - name: a\n"
            + "      type: text\n"
            + "      default: a_val\n"
            + "    - name: b\n"
            + "      type: text\n"
            + "      default: b_old\n";
        final var newSettings = ""
            + "- name: general\n"
            + "  label: General\n"
            + "  items:\n"
            + "    - name: b\n"
            + "      type: text\n"
            + "      default: b_new\n"
            + "    - name: c\n"
            + "      type: text\n"
            + "      default: c_val\n";

        final var existing = tempDir.resolve("existing");
        writeFile(existing, "theme.yaml", THEME_YAML);
        writeFile(existing, "settings.yaml", oldSettings);

        final var candidate = tempDir.resolve("candidate");
        writeFile(candidate, "theme.yaml", THEME_YAML);
        writeFile(candidate, "settings.yaml", newSettings);

        final var dto = ThemePreviewer.INSTANCE.preview(
            property(candidate, "t", null), existing, "2.0.0", false);

        assertTrue(dto.isHasOptions());
        final var optionChanges = dto.getOptionChanges();
        assertEquals(java.util.List.of("c"), optionChanges.getAdded());
        assertEquals(java.util.List.of("a"), optionChanges.getRemoved());
        assertEquals(java.util.List.of("b"), optionChanges.getChanged());
    }

    @Test
    void installListsAllOptionNamesAsAdded() throws IOException {
        final var settings = ""
            + "- name: general\n"
            + "  label: General\n"
            + "  items:\n"
            + "    - name: a\n"
            + "      type: text\n"
            + "    - name: b\n"
            + "      type: text\n";

        final var candidate = tempDir.resolve("candidate");
        writeFile(candidate, "theme.yaml", THEME_YAML);
        writeFile(candidate, "settings.yaml", settings);

        final var dto = ThemePreviewer.INSTANCE.preview(
            property(candidate, "t", null), null, "2.0.0", false);

        assertTrue(dto.isHasOptions());
        assertTrue(dto.getOptionChanges().getAdded().containsAll(java.util.List.of("a", "b")));
        assertTrue(dto.getOptionChanges().getRemoved().isEmpty());
        assertTrue(dto.getOptionChanges().getChanged().isEmpty());
    }

    @Test
    void affectsActivatedThemeFlagIsPassedThrough() throws IOException {
        final var candidate = tempDir.resolve("candidate");
        writeFile(candidate, "theme.yaml", THEME_YAML);
        writeFile(candidate, "index.ftl", "index");

        final var affecting = ThemePreviewer.INSTANCE.preview(
            property(candidate, "t", null), null, "2.0.0", true);
        assertTrue(affecting.isAffectsActivatedTheme());

        final var notAffecting = ThemePreviewer.INSTANCE.preview(
            property(candidate, "t", null), null, "2.0.0", false);
        assertFalse(notAffecting.isAffectsActivatedTheme());
    }

    @Test
    void gitMetadataIsExcludedFromTheDiff() throws IOException {
        final var candidate = tempDir.resolve("candidate");
        writeFile(candidate, "theme.yaml", THEME_YAML);
        writeFile(candidate, "index.ftl", "index");
        writeFile(candidate, ".git/config", "[core]");
        writeFile(candidate, ".git/objects/abc", "blob");

        final var dto = ThemePreviewer.INSTANCE.preview(
            property(candidate, "t", null), null, "2.0.0", false);

        dto.getFileChanges().forEach(change ->
            assertFalse(change.getPath().contains(".git"),
                "file under .git should be excluded: " + change.getPath()));
        // only theme.yaml + index.ftl remain
        assertEquals(2, dto.getAddedFileCount());
    }
}
