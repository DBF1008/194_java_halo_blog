package run.halo.app.theme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import run.halo.app.model.support.FileDiffEntry;
import run.halo.app.model.support.FileDiffEntry.DiffType;

/**
 * Compares two theme directories and produces a file-level diff.
 *
 * <p>Used by the dry-run preview feature to show what files would be
 * added, modified, or deleted during a theme update.</p>
 *
 * @author halo-dev
 */
public final class ThemeFileComparator {

    private ThemeFileComparator() {
    }

    /**
     * Compares two theme directories and returns a list of file differences.
     *
     * <p>The comparison skips {@code .git} directories and uses relative paths
     * for matching. Regular files are compared by content; directories are
     * compared by presence.</p>
     *
     * @param oldThemePath the path to the old (currently installed) theme
     * @param newThemePath the path to the new (fetched) theme
     * @return a sorted list of file diff entries
     * @throws IOException if an I/O error occurs during comparison
     */
    @NonNull
    public static List<FileDiffEntry> compare(@NonNull Path oldThemePath,
            @NonNull Path newThemePath) throws IOException {
        Assert.notNull(oldThemePath, "Old theme path must not be null");
        Assert.notNull(newThemePath, "New theme path must not be null");

        Set<String> oldPaths = collectRelativePaths(oldThemePath);
        Set<String> newPaths = collectRelativePaths(newThemePath);

        Set<String> allPaths = new TreeSet<>();
        allPaths.addAll(oldPaths);
        allPaths.addAll(newPaths);

        List<FileDiffEntry> diffs = new ArrayList<>();

        for (String relativePath : allPaths) {
            boolean inOld = oldPaths.contains(relativePath);
            boolean inNew = newPaths.contains(relativePath);

            Path oldResolved = oldThemePath.resolve(relativePath);
            Path newResolved = newThemePath.resolve(relativePath);
            boolean isFile = inNew ? Files.isRegularFile(newResolved)
                    : Files.isRegularFile(oldResolved);

            if (inOld && inNew) {
                // Both exist — compare content if both are regular files
                if (isFile) {
                    byte[] oldBytes = Files.readAllBytes(oldResolved);
                    byte[] newBytes = Files.readAllBytes(newResolved);
                    DiffType type = Arrays.equals(oldBytes, newBytes)
                            ? DiffType.UNCHANGED : DiffType.MODIFIED;
                    diffs.add(createEntry(relativePath, type, true));
                } else {
                    // Both are directories — unchanged
                    diffs.add(createEntry(relativePath, DiffType.UNCHANGED, false));
                }
            } else if (inNew) {
                diffs.add(createEntry(relativePath, DiffType.ADDED, isFile));
            } else {
                diffs.add(createEntry(relativePath, DiffType.DELETED, isFile));
            }
        }

        return diffs;
    }

    /**
     * Collects all relative paths under the given root, skipping .git directories.
     */
    private static Set<String> collectRelativePaths(@NonNull Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return Collections.emptySet();
        }

        Set<String> paths = new HashSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> !path.equals(root))
                    .filter(path -> !isInsideGitDir(root, path))
                    .forEach(path -> {
                        String relative = root.relativize(path).toString();
                        // Normalize path separators for cross-platform consistency
                        relative = relative.replace('\\', '/');
                        paths.add(relative);
                    });
        }
        return paths;
    }

    /**
     * Checks if the given path is inside a .git directory relative to the root.
     */
    private static boolean isInsideGitDir(@NonNull Path root, @NonNull Path path) {
        Path relative = root.relativize(path);
        for (Path component : relative) {
            if (".git".equals(component.toString())) {
                return true;
            }
        }
        return false;
    }

    private static FileDiffEntry createEntry(String relativePath, DiffType type, boolean isFile) {
        FileDiffEntry entry = new FileDiffEntry();
        entry.setRelativePath(relativePath);
        entry.setDiffType(type);
        entry.setFile(isFile);
        return entry;
    }
}
