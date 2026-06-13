package run.halo.app.theme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import run.halo.app.handler.theme.config.ThemeConfigResolver;
import run.halo.app.handler.theme.config.impl.YamlThemeConfigResolverImpl;
import run.halo.app.handler.theme.config.support.Group;
import run.halo.app.handler.theme.config.support.Item;
import run.halo.app.handler.theme.config.support.ThemeProperty;
import run.halo.app.model.dto.ThemePreviewDTO;
import run.halo.app.model.dto.ThemePreviewDTO.ChangeType;
import run.halo.app.model.dto.ThemePreviewDTO.FileChange;
import run.halo.app.model.dto.ThemePreviewDTO.OptionChanges;
import run.halo.app.model.dto.ThemePreviewDTO.Operation;
import run.halo.app.utils.VersionUtil;

/**
 * Computes a dry-run preview (change set) of installing or upgrading a theme.
 *
 * <p>This component is intentionally side-effect free: it only <em>reads</em> the candidate
 * theme folder (typically a temporary directory produced by a {@link ThemeFetcher}) and the
 * currently installed theme folder, and never writes to either. It therefore mirrors what
 * {@code ThemeRepository#attemptToAdd} / the theme updaters would do, without performing the
 * destructive copy/merge.</p>
 *
 * <p>For upgrades the change set is computed as a net file diff between the freshly fetched
 * candidate tree and the current installed tree. This is an honest, side-effect-free view of
 * the result and deliberately does not replay the git rebase performed by
 * {@code GitThemeUpdater}, which would mutate the installed theme directory.</p>
 *
 * @author halo
 */
@Slf4j
public enum ThemePreviewer {

    INSTANCE;

    private final ThemeConfigResolver themeConfigResolver = new YamlThemeConfigResolverImpl();

    /**
     * Builds a preview of the change set for installing or upgrading a theme.
     *
     * @param candidate the resolved candidate theme property; its {@code themePath} must point
     *     at the candidate theme root folder (the folder containing {@code theme.yaml})
     * @param existingThemeRoot the root folder of the currently installed theme with the same
     *     id, or {@code null} for a fresh install
     * @param currentHaloVersion the running Halo version used for the compatibility check
     * @param affectsActivatedTheme whether this operation would affect the activated theme
     * @return the preview result
     */
    @NonNull
    public ThemePreviewDTO preview(@NonNull ThemeProperty candidate,
        @Nullable Path existingThemeRoot,
        @Nullable String currentHaloVersion,
        boolean affectsActivatedTheme) {
        final var dto = new ThemePreviewDTO();
        dto.setThemeProperty(candidate);

        final boolean update = existingThemeRoot != null;
        dto.setOperation(update ? Operation.UPDATE : Operation.INSTALL);
        dto.setExisting(update);
        dto.setAffectsActivatedTheme(affectsActivatedTheme);
        dto.setCurrentVersion(currentHaloVersion);

        // version compatibility (same rule as ThemeRepositoryImpl#checkThemePropertyCompatibility)
        final var require = candidate.getRequire();
        dto.setRequiredVersion(require);
        dto.setCompatible(StringUtils.isBlank(require)
            || VersionUtil.compareVersion(StringUtils.defaultString(currentHaloVersion), require));

        final var candidateRoot = Paths.get(candidate.getThemePath());

        resolveFileChanges(dto, candidateRoot, existingThemeRoot, update);
        resolveOptionChanges(dto, candidateRoot, existingThemeRoot, update);

        return dto;
    }

    private void resolveFileChanges(ThemePreviewDTO dto, Path candidateRoot,
        @Nullable Path existingThemeRoot, boolean update) {
        final var candidateHashes = hashTree(candidateRoot);
        final List<FileChange> changes = new ArrayList<>();
        int added = 0;
        int modified = 0;
        int deleted = 0;
        int unchanged = 0;

        if (!update) {
            for (String path : new TreeSet<>(candidateHashes.keySet())) {
                changes.add(new FileChange(path, ChangeType.ADD));
                added++;
            }
        } else {
            final var existingHashes = hashTree(existingThemeRoot);
            final var allPaths = new TreeSet<String>();
            allPaths.addAll(candidateHashes.keySet());
            allPaths.addAll(existingHashes.keySet());

            for (String path : allPaths) {
                final boolean inNew = candidateHashes.containsKey(path);
                final boolean inOld = existingHashes.containsKey(path);
                final ChangeType type;
                if (inNew && !inOld) {
                    type = ChangeType.ADD;
                    added++;
                } else if (!inNew) {
                    type = ChangeType.DELETE;
                    deleted++;
                } else if (Objects.equals(candidateHashes.get(path), existingHashes.get(path))) {
                    type = ChangeType.UNCHANGED;
                    unchanged++;
                } else {
                    type = ChangeType.MODIFY;
                    modified++;
                }
                changes.add(new FileChange(path, type));
            }
        }

        dto.setFileChanges(changes);
        dto.setAddedFileCount(added);
        dto.setModifiedFileCount(modified);
        dto.setDeletedFileCount(deleted);
        dto.setUnchangedFileCount(unchanged);
    }

    private void resolveOptionChanges(ThemePreviewDTO dto, Path candidateRoot,
        @Nullable Path existingThemeRoot, boolean update) {
        dto.setHasOptions(ThemeMetaLocator.INSTANCE.locateSetting(candidateRoot).isPresent());

        final var newOptions = optionMap(candidateRoot);
        final var optionChanges = new OptionChanges();

        if (!update) {
            optionChanges.getAdded().addAll(new TreeSet<>(newOptions.keySet()));
        } else {
            final var oldOptions = optionMap(existingThemeRoot);

            final var addedNames = new TreeSet<>(newOptions.keySet());
            addedNames.removeAll(oldOptions.keySet());

            final var removedNames = new TreeSet<>(oldOptions.keySet());
            removedNames.removeAll(newOptions.keySet());

            final var changedNames = new TreeSet<String>();
            newOptions.forEach((name, newItem) -> {
                final var oldItem = oldOptions.get(name);
                if (oldItem != null
                    && (!Objects.equals(newItem.getType(), oldItem.getType())
                    || !Objects.equals(newItem.getDefaultValue(), oldItem.getDefaultValue()))) {
                    changedNames.add(name);
                }
            });

            optionChanges.getAdded().addAll(addedNames);
            optionChanges.getRemoved().addAll(removedNames);
            optionChanges.getChanged().addAll(changedNames);
        }

        dto.setOptionChanges(optionChanges);
    }

    /**
     * Builds a map of theme-relative file path to a content hash, skipping {@code .git}.
     */
    @NonNull
    private Map<String, String> hashTree(@Nullable Path root) {
        final Map<String, String> hashes = new HashMap<>();
        if (root == null || !Files.isDirectory(root)) {
            return hashes;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> !isUnderGit(root, path))
                .forEach(path -> {
                    try {
                        hashes.put(toRelative(root, path), md5(Files.readAllBytes(path)));
                    } catch (IOException e) {
                        log.warn("Failed to read theme file for preview: {}", path, e);
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to walk theme directory for preview: {}", root, e);
        }
        return hashes;
    }

    private boolean isUnderGit(Path root, Path path) {
        final var relative = root.relativize(path);
        for (Path part : relative) {
            if (".git".equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private String toRelative(Path root, Path path) {
        // normalize to forward slashes so paths are stable across platforms
        return StringUtils.replace(root.relativize(path).toString(), "\\", "/");
    }

    private String md5(byte[] bytes) {
        try {
            final var digest = MessageDigest.getInstance("MD5").digest(bytes);
            final var sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    /**
     * Resolves the settings options of a theme as a map of option name to item. Returns an
     * empty map if the theme has no settings file or the file cannot be parsed.
     */
    @NonNull
    private Map<String, Item> optionMap(@Nullable Path root) {
        final Map<String, Item> options = new LinkedHashMap<>();
        if (root == null) {
            return options;
        }
        final Optional<Path> settingPath = ThemeMetaLocator.INSTANCE.locateSetting(root);
        if (settingPath.isEmpty()) {
            return options;
        }
        try {
            final var content = Files.readString(settingPath.get());
            final List<Group> groups = themeConfigResolver.resolve(content);
            for (Group group : groups) {
                if (group.getItems() == null) {
                    continue;
                }
                for (Item item : group.getItems()) {
                    if (item.getName() != null) {
                        options.put(item.getName(), item);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve theme settings for preview from {}", root, e);
        }
        return options;
    }
}
