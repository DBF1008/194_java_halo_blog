package run.halo.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;
import run.halo.app.config.properties.HaloProperties;
import run.halo.app.exception.ThemePropertyMissingException;
import run.halo.app.handler.theme.config.impl.YamlThemeConfigResolverImpl;
import run.halo.app.handler.theme.config.support.ThemeProperty;
import run.halo.app.model.dto.ThemePreviewDTO.ChangeType;
import run.halo.app.model.dto.ThemePreviewDTO.FileChange;
import run.halo.app.model.dto.ThemePreviewDTO.Operation;
import run.halo.app.repository.ThemeRepository;
import run.halo.app.repository.ThemeSettingRepository;
import run.halo.app.service.ThemeService;
import run.halo.app.utils.FileUtils;

/**
 * Network-free end-to-end tests for the theme install/upgrade <em>preview</em> on
 * {@link ThemeServiceImpl}.
 *
 * <p>A real {@link ThemeServiceImpl} is constructed against a {@link TempDir} work directory and
 * its own real {@code ThemeFetcherComposite} (the zip fetcher needs no network). The repository,
 * event publisher and rest template are mocked so the tests can assert that the preview produces
 * <strong>no side effects</strong>: no {@code attemptToAdd}, no {@code deleteTheme}, no published
 * events, the installed-theme directory is never written, and an existing theme is never mutated.
 * </p>
 *
 * @author halo
 */
class ThemeServicePreviewTest {

    @TempDir
    Path workDir;

    private ThemeServiceImpl themeService;
    private ThemeRepository themeRepository;
    private ThemeSettingRepository themeSettingRepository;
    private ApplicationEventPublisher eventPublisher;
    private RestTemplate restTemplate;

    /**
     * {@code <workDir>/templates/themes} — where a real install would land.
     */
    private Path themeFolder;

    @BeforeEach
    void setUp() {
        themeRepository = mock(ThemeRepository.class);
        themeSettingRepository = mock(ThemeSettingRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        restTemplate = mock(RestTemplate.class);

        final var haloProperties = new HaloProperties();
        haloProperties.setWorkDir(workDir.toString() + File.separator);

        themeService = new ThemeServiceImpl(haloProperties, new YamlThemeConfigResolverImpl(),
            restTemplate, eventPublisher, themeSettingRepository, themeRepository);

        themeFolder = Paths.get(haloProperties.getWorkDir(), ThemeService.THEME_FOLDER);
    }

    private void writeFiles(Path dir, Map<String, String> files) throws IOException {
        Files.createDirectories(dir);
        for (var entry : files.entrySet()) {
            final var target = dir.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
        }
    }

    /**
     * Builds a theme folder from the given files and zips it into a {@link MockMultipartFile}
     * whose original filename ends with {@code .zip} (so the multipart zip fetcher accepts it).
     */
    private MockMultipartFile zipTheme(String name, Map<String, String> files) throws IOException {
        final var source = workDir.resolve("build-" + name);
        writeFiles(source, files);
        final var archive = workDir.resolve(name + ".zip");
        FileUtils.zip(source, archive);
        return new MockMultipartFile("file", name + ".zip", "application/zip",
            Files.readAllBytes(archive));
    }

    private void verifyNoSideEffects() {
        verify(themeRepository, never()).attemptToAdd(any());
        verify(themeRepository, never()).deleteTheme(any(ThemeProperty.class));
        verify(themeRepository, never()).deleteTheme(anyString());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void previewByUploadOnFreshInstall() throws IOException {
        when(themeRepository.fetchThemePropertyByThemeId(anyString()))
            .thenReturn(Optional.empty());
        when(themeRepository.getActivatedThemeId()).thenReturn("other");

        final var files = new LinkedHashMap<String, String>();
        files.put("theme.yaml", "id: installme\nname: Install Me\nversion: 1.0.0\n");
        files.put("index.ftl", "hello");
        final var zip = zipTheme("install", files);

        final var dto = themeService.previewByUpload(zip);

        assertEquals(Operation.INSTALL, dto.getOperation());
        assertEquals("installme", dto.getThemeProperty().getId());
        assertFalse(dto.isExisting());
        assertFalse(dto.isAffectsActivatedTheme());
        assertFalse(dto.getFileChanges().isEmpty());
        dto.getFileChanges().forEach(change ->
            assertEquals(ChangeType.ADD, change.getType()));

        // no install actually happened
        assertTrue(Files.notExists(themeFolder.resolve("installme")));
        verifyNoSideEffects();
    }

    @Test
    void previewUpdateByUploadDoesNotTouchExistingTheme() throws IOException {
        // pre-create the currently installed (and activated) theme on disk
        final var existingRoot = themeFolder.resolve("upme");
        final var existingFiles = new LinkedHashMap<String, String>();
        existingFiles.put("theme.yaml", "id: upme\nname: Up Me\nversion: 1.0.0\n");
        existingFiles.put("index.ftl", "old-content");
        existingFiles.put("extra.ftl", "shared");
        writeFiles(existingRoot, existingFiles);

        final var existingProperty = new ThemeProperty();
        existingProperty.setId("upme");
        existingProperty.setThemePath(existingRoot.toString());

        when(themeRepository.fetchThemePropertyByThemeId("upme"))
            .thenReturn(Optional.of(existingProperty));
        when(themeRepository.getActivatedThemeId()).thenReturn("upme");

        // upload a same-id package with a modified index.ftl and an unchanged extra.ftl
        final var newFiles = new LinkedHashMap<String, String>();
        newFiles.put("theme.yaml", "id: upme\nname: Up Me\nversion: 1.0.0\n");
        newFiles.put("index.ftl", "new-content");
        newFiles.put("extra.ftl", "shared");
        final var zip = zipTheme("upme-new", newFiles);

        final var dto = themeService.previewUpdateByUpload("upme", zip);

        assertEquals(Operation.UPDATE, dto.getOperation());
        assertTrue(dto.isExisting());
        assertTrue(dto.isAffectsActivatedTheme());

        final Map<String, ChangeType> byPath = dto.getFileChanges().stream()
            .collect(Collectors.toMap(FileChange::getPath, FileChange::getType));
        assertEquals(ChangeType.MODIFY, byPath.get("index.ftl"));
        assertEquals(ChangeType.UNCHANGED, byPath.get("extra.ftl"));
        assertEquals(ChangeType.UNCHANGED, byPath.get("theme.yaml"));

        // the installed theme directory must be byte-for-byte unchanged
        assertEquals("old-content", Files.readString(existingRoot.resolve("index.ftl")));
        assertEquals("shared", Files.readString(existingRoot.resolve("extra.ftl")));

        verifyNoSideEffects();
    }

    @Test
    void previewByUploadRejectsIllegalPackage() throws IOException {
        final var files = new LinkedHashMap<String, String>();
        files.put("readme.txt", "this package has no theme.yaml");
        final var zip = zipTheme("bad", files);

        assertThrows(ThemePropertyMissingException.class,
            () -> themeService.previewByUpload(zip));

        verifyNoSideEffects();
    }

    @Test
    @Disabled("Requires network access to a remote git repository")
    void previewByFetchingFromRemote() {
        // Counterpart of the zip-based tests for a git/zip remote source. The network-free
        // git-equivalent file diff is already exercised by ThemePreviewerTest, so this remote
        // round-trip is disabled by default (mirrors GitThemeFetcherTest / ZipThemeFetcherTest).
    }
}
