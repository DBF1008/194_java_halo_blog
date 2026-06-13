package run.halo.app.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.halo.app.model.support.FileDiffEntry;
import run.halo.app.model.support.FileDiffEntry.DiffType;

/**
 * Theme file comparator test.
 *
 * @author halo-dev
 */
class ThemeFileComparatorTest {

    @TempDir
    Path tempDir;

    @Test
    void identicalDirectories() throws IOException {
        Path oldTheme = tempDir.resolve("old");
        Path newTheme = tempDir.resolve("new");
        Files.createDirectories(oldTheme);
        Files.createDirectories(newTheme);

        Files.writeString(oldTheme.resolve("theme.yaml"), "id: test\nname: Test");
        Files.writeString(newTheme.resolve("theme.yaml"), "id: test\nname: Test");

        Files.createDirectories(oldTheme.resolve("templates"));
        Files.createDirectories(newTheme.resolve("templates"));
        Files.writeString(oldTheme.resolve("templates/index.ftl"), "<html>hello</html>");
        Files.writeString(newTheme.resolve("templates/index.ftl"), "<html>hello</html>");

        List<FileDiffEntry> diffs = ThemeFileComparator.compare(oldTheme, newTheme);

        // All entries should be UNCHANGED
        assertTrue(diffs.stream().allMatch(d -> d.getDiffType() == DiffType.UNCHANGED));
    }

    @Test
    void addedFiles() throws IOException {
        Path oldTheme = tempDir.resolve("old");
        Path newTheme = tempDir.resolve("new");
        Files.createDirectories(oldTheme);
        Files.createDirectories(newTheme);

        Files.writeString(oldTheme.resolve("theme.yaml"), "id: test");
        Files.writeString(newTheme.resolve("theme.yaml"), "id: test");
        Files.writeString(newTheme.resolve("new-file.ftl"), "new content");

        List<FileDiffEntry> diffs = ThemeFileComparator.compare(oldTheme, newTheme);

        FileDiffEntry added = findByPath(diffs, "new-file.ftl");
        assertEquals(DiffType.ADDED, added.getDiffType());
        assertTrue(added.isFile());
    }

    @Test
    void deletedFiles() throws IOException {
        Path oldTheme = tempDir.resolve("old");
        Path newTheme = tempDir.resolve("new");
        Files.createDirectories(oldTheme);
        Files.createDirectories(newTheme);

        Files.writeString(oldTheme.resolve("theme.yaml"), "id: test");
        Files.writeString(newTheme.resolve("theme.yaml"), "id: test");
        Files.writeString(oldTheme.resolve("old-file.ftl"), "old content");

        List<FileDiffEntry> diffs = ThemeFileComparator.compare(oldTheme, newTheme);

        FileDiffEntry deleted = findByPath(diffs, "old-file.ftl");
        assertEquals(DiffType.DELETED, deleted.getDiffType());
    }

    @Test
    void modifiedFiles() throws IOException {
        Path oldTheme = tempDir.resolve("old");
        Path newTheme = tempDir.resolve("new");
        Files.createDirectories(oldTheme);
        Files.createDirectories(newTheme);

        Files.writeString(oldTheme.resolve("index.ftl"), "<html>old</html>");
        Files.writeString(newTheme.resolve("index.ftl"), "<html>new</html>");

        List<FileDiffEntry> diffs = ThemeFileComparator.compare(oldTheme, newTheme);

        FileDiffEntry modified = findByPath(diffs, "index.ftl");
        assertEquals(DiffType.MODIFIED, modified.getDiffType());
        assertTrue(modified.isFile());
    }

    @Test
    void mixedChanges() throws IOException {
        Path oldTheme = tempDir.resolve("old");
        Path newTheme = tempDir.resolve("new");
        Files.createDirectories(oldTheme);
        Files.createDirectories(newTheme);

        // Unchanged file
        Files.writeString(oldTheme.resolve("unchanged.ftl"), "same");
        Files.writeString(newTheme.resolve("unchanged.ftl"), "same");

        // Modified file
        Files.writeString(oldTheme.resolve("modified.ftl"), "old content");
        Files.writeString(newTheme.resolve("modified.ftl"), "new content");

        // Deleted file
        Files.writeString(oldTheme.resolve("deleted.ftl"), "gone");

        // Added file
        Files.writeString(newTheme.resolve("added.ftl"), "new");

        List<FileDiffEntry> diffs = ThemeFileComparator.compare(oldTheme, newTheme);

        assertEquals(4, diffs.size());
        assertEquals(DiffType.UNCHANGED, findByPath(diffs, "unchanged.ftl").getDiffType());
        assertEquals(DiffType.MODIFIED, findByPath(diffs, "modified.ftl").getDiffType());
        assertEquals(DiffType.DELETED, findByPath(diffs, "deleted.ftl").getDiffType());
        assertEquals(DiffType.ADDED, findByPath(diffs, "added.ftl").getDiffType());
    }

    @Test
    void emptyDirectories() throws IOException {
        Path oldTheme = tempDir.resolve("old");
        Path newTheme = tempDir.resolve("new");
        Files.createDirectories(oldTheme);
        Files.createDirectories(newTheme);

        List<FileDiffEntry> diffs = ThemeFileComparator.compare(oldTheme, newTheme);

        assertTrue(diffs.isEmpty());
    }

    @Test
    void nestedDirectoryStructure() throws IOException {
        Path oldTheme = tempDir.resolve("old");
        Path newTheme = tempDir.resolve("new");

        // Old: templates/post.ftl, templates/module/sidebar.ftl
        Files.createDirectories(oldTheme.resolve("templates/module"));
        Files.writeString(oldTheme.resolve("templates/post.ftl"), "old post");
        Files.writeString(oldTheme.resolve("templates/module/sidebar.ftl"), "old sidebar");

        // New: templates/post.ftl (modified), templates/module/header.ftl (added)
        Files.createDirectories(newTheme.resolve("templates/module"));
        Files.writeString(newTheme.resolve("templates/post.ftl"), "new post");
        Files.writeString(newTheme.resolve("templates/module/header.ftl"), "new header");

        List<FileDiffEntry> diffs = ThemeFileComparator.compare(oldTheme, newTheme);

        assertEquals(DiffType.MODIFIED, findByPath(diffs, "templates/post.ftl").getDiffType());
        assertEquals(DiffType.DELETED,
                findByPath(diffs, "templates/module/sidebar.ftl").getDiffType());
        assertEquals(DiffType.ADDED,
                findByPath(diffs, "templates/module/header.ftl").getDiffType());
    }

    @Test
    void gitDirectoryExcluded() throws IOException {
        Path oldTheme = tempDir.resolve("old");
        Path newTheme = tempDir.resolve("new");
        Files.createDirectories(oldTheme);
        Files.createDirectories(newTheme);

        // Old has .git directory
        Files.createDirectories(oldTheme.resolve(".git/objects"));
        Files.writeString(oldTheme.resolve(".git/HEAD"), "ref: refs/heads/master");
        Files.writeString(oldTheme.resolve(".git/objects/pack"), "data");

        Files.writeString(oldTheme.resolve("theme.yaml"), "id: test");
        Files.writeString(newTheme.resolve("theme.yaml"), "id: test");

        List<FileDiffEntry> diffs = ThemeFileComparator.compare(oldTheme, newTheme);

        // .git entries should not appear in the diff
        assertTrue(diffs.stream().noneMatch(d -> d.getRelativePath().startsWith(".git")));
    }

    @Test
    void resultsSortedByRelativePath() throws IOException {
        Path oldTheme = tempDir.resolve("old");
        Path newTheme = tempDir.resolve("new");
        Files.createDirectories(oldTheme);
        Files.createDirectories(newTheme);

        Files.writeString(newTheme.resolve("z-last.ftl"), "z");
        Files.writeString(newTheme.resolve("a-first.ftl"), "a");
        Files.writeString(newTheme.resolve("m-middle.ftl"), "m");

        List<FileDiffEntry> diffs = ThemeFileComparator.compare(oldTheme, newTheme);

        assertEquals("a-first.ftl", diffs.get(0).getRelativePath());
        assertEquals("m-middle.ftl", diffs.get(1).getRelativePath());
        assertEquals("z-last.ftl", diffs.get(2).getRelativePath());
    }

    private FileDiffEntry findByPath(List<FileDiffEntry> diffs, String relativePath) {
        return diffs.stream()
                .filter(d -> d.getRelativePath().equals(relativePath))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected diff entry for: " + relativePath));
    }
}
