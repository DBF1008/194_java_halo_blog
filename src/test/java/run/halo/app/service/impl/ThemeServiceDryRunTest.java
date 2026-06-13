package run.halo.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import run.halo.app.config.properties.HaloProperties;
import run.halo.app.exception.BadRequestException;
import run.halo.app.exception.NotFoundException;
import run.halo.app.handler.theme.config.ThemeConfigResolver;
import run.halo.app.handler.theme.config.support.Group;
import run.halo.app.handler.theme.config.support.Item;
import run.halo.app.handler.theme.config.support.ThemeProperty;
import run.halo.app.model.enums.DataType;
import run.halo.app.model.enums.InputType;
import run.halo.app.model.support.ConfigDiffEntry;
import run.halo.app.model.support.FileDiffEntry;
import run.halo.app.model.support.ThemeInstallDryRunResult;
import run.halo.app.model.support.ThemeUpgradeDryRunResult;
import run.halo.app.repository.ThemeRepository;
import run.halo.app.repository.ThemeSettingRepository;
import run.halo.app.theme.ThemeFetcherComposite;

/**
 * Tests for dry-run preview methods in ThemeServiceImpl.
 *
 * <p>Verifies that dry-run operations produce correct preview information
 * without side effects (no writes to theme directory, no database changes,
 * no event publishing).</p>
 *
 * @author halo-dev
 */
class ThemeServiceDryRunTest {

    @TempDir
    Path tempDir;

    ThemeServiceImpl themeService;

    @Mock
    HaloProperties haloProperties;

    @Mock
    ThemeConfigResolver themeConfigResolver;

    @Mock
    RestTemplate restTemplate;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    ThemeSettingRepository themeSettingRepository;

    @Mock
    ThemeRepository themeRepository;

    ThemeFetcherComposite mockFetcherComposite;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Set up HaloProperties to return a valid work dir
        given(haloProperties.getWorkDir()).willReturn(tempDir.toString());

        // Create theme directory structure
        Path themeDir = tempDir.resolve("templates/themes");
        themeDir.toFile().mkdirs();

        themeService = new ThemeServiceImpl(
                haloProperties, themeConfigResolver, restTemplate,
                eventPublisher, themeSettingRepository, themeRepository);

        // Replace the real fetcher composite with a mock
        mockFetcherComposite = mock(ThemeFetcherComposite.class);
        ReflectionTestUtils.setField(themeService, "fetcherComposite", mockFetcherComposite);
    }

    // ==================== dryRunFetch tests ====================

    @Test
    void dryRunFetchWithZipUri() throws IOException {
        // Create a temp theme directory simulating what a fetcher would return
        Path themeDir = createTempTheme("test-theme", "1.0.0", null);
        ThemeProperty prop = createThemeProperty("test-theme", "Test Theme", "1.0.0", null,
                themeDir.toString());

        given(mockFetcherComposite.fetch(any(String.class))).willReturn(prop);
        given(themeRepository.fetchThemePropertyByThemeId("test-theme"))
                .willReturn(Optional.empty());
        given(themeRepository.checkThemePropertyCompatibility(prop)).willReturn(false);

        ThemeInstallDryRunResult result = themeService.dryRunFetch(
                "https://example.com/theme.zip");

        assertNotNull(result);
        assertEquals("test-theme", result.getThemeProperty().getId());
        assertEquals("Test Theme", result.getThemeProperty().getName());
        assertEquals("1.0.0", result.getThemeProperty().getVersion());
        assertFalse(result.isAlreadyExists());
        assertTrue(result.isVersionCompatible());
        assertNotNull(result.getThemeFiles());

        // Verify no side effects
        verifyNoSideEffects();
    }

    @Test
    void dryRunFetchWithGitUri() throws IOException {
        Path themeDir = createTempTheme("git-theme", "2.0.0", null);
        ThemeProperty prop = createThemeProperty("git-theme", "Git Theme", "2.0.0", null,
                themeDir.toString());

        given(mockFetcherComposite.fetch(any(String.class))).willReturn(prop);
        given(themeRepository.fetchThemePropertyByThemeId("git-theme"))
                .willReturn(Optional.empty());
        given(themeRepository.checkThemePropertyCompatibility(prop)).willReturn(false);

        ThemeInstallDryRunResult result = themeService.dryRunFetch(
                "https://github.com/user/theme.git");

        assertNotNull(result);
        assertEquals("git-theme", result.getThemeProperty().getId());
        assertFalse(result.isAlreadyExists());
        assertTrue(result.isVersionCompatible());

        verifyNoSideEffects();
    }

    @Test
    void dryRunFetchThemeAlreadyExists() throws IOException {
        Path themeDir = createTempTheme("existing-theme", "1.0.0", null);
        ThemeProperty prop = createThemeProperty("existing-theme", "Existing", "1.0.0", null,
                themeDir.toString());

        given(mockFetcherComposite.fetch(any(String.class))).willReturn(prop);
        given(themeRepository.fetchThemePropertyByThemeId("existing-theme"))
                .willReturn(Optional.of(prop));
        given(themeRepository.checkThemePropertyCompatibility(prop)).willReturn(false);

        ThemeInstallDryRunResult result = themeService.dryRunFetch("https://example.com/t.zip");

        assertTrue(result.isAlreadyExists());

        verifyNoSideEffects();
    }

    @Test
    void dryRunFetchIncompatibleVersion() throws IOException {
        Path themeDir = createTempTheme("incompatible", "3.0.0", "99.0.0");
        ThemeProperty prop = createThemeProperty("incompatible", "Incompatible", "3.0.0",
                "99.0.0", themeDir.toString());

        given(mockFetcherComposite.fetch(any(String.class))).willReturn(prop);
        given(themeRepository.fetchThemePropertyByThemeId("incompatible"))
                .willReturn(Optional.empty());
        given(themeRepository.checkThemePropertyCompatibility(prop)).willReturn(true);

        ThemeInstallDryRunResult result = themeService.dryRunFetch("https://example.com/t.zip");

        assertFalse(result.isVersionCompatible());
        assertEquals("99.0.0", result.getRequiredHaloVersion());

        verifyNoSideEffects();
    }

    @Test
    void dryRunFetchNoSettingsYaml() throws IOException {
        // Create theme without settings.yaml
        Path themeDir = createTempTheme("no-settings", "1.0.0", null);
        ThemeProperty prop = createThemeProperty("no-settings", "No Settings", "1.0.0", null,
                themeDir.toString());

        given(mockFetcherComposite.fetch(any(String.class))).willReturn(prop);
        given(themeRepository.fetchThemePropertyByThemeId("no-settings"))
                .willReturn(Optional.empty());
        given(themeRepository.checkThemePropertyCompatibility(prop)).willReturn(false);

        ThemeInstallDryRunResult result = themeService.dryRunFetch("https://example.com/t.zip");

        assertNotNull(result.getConfigGroups());
        assertTrue(result.getConfigGroups().isEmpty());

        verifyNoSideEffects();
    }

    @Test
    void dryRunFetchWithSettingsYaml() throws IOException {
        Path themeDir = createTempTheme("with-settings", "1.0.0", null);
        // Add settings.yaml
        Files.writeString(themeDir.resolve("settings.yaml"),
                "- name: general\n  label: General\n  items:\n    - name: title\n");

        ThemeProperty prop = createThemeProperty("with-settings", "With Settings", "1.0.0", null,
                themeDir.toString());

        Item titleItem = new Item();
        titleItem.setName("title");
        titleItem.setLabel("Title");
        titleItem.setType(InputType.TEXT);
        titleItem.setDataType(DataType.STRING);

        Group group = new Group();
        group.setName("general");
        group.setLabel("General");
        group.setItems(List.of(titleItem));

        given(mockFetcherComposite.fetch(any(String.class))).willReturn(prop);
        given(themeRepository.fetchThemePropertyByThemeId("with-settings"))
                .willReturn(Optional.empty());
        given(themeRepository.checkThemePropertyCompatibility(prop)).willReturn(false);
        given(themeConfigResolver.resolve(anyString())).willReturn(List.of(group));

        ThemeInstallDryRunResult result = themeService.dryRunFetch("https://example.com/t.zip");

        assertNotNull(result.getConfigGroups());
        assertEquals(1, result.getConfigGroups().size());
        assertEquals("general", result.getConfigGroups().get(0).getName());

        verifyNoSideEffects();
    }

    @Test
    void dryRunFetchTempDirectoryCleanedUp() throws IOException {
        Path themeDir = createTempTheme("cleanup-test", "1.0.0", null);
        ThemeProperty prop = createThemeProperty("cleanup-test", "Cleanup", "1.0.0", null,
                themeDir.toString());

        assertTrue(Files.exists(themeDir));

        given(mockFetcherComposite.fetch(any(String.class))).willReturn(prop);
        given(themeRepository.fetchThemePropertyByThemeId("cleanup-test"))
                .willReturn(Optional.empty());
        given(themeRepository.checkThemePropertyCompatibility(prop)).willReturn(false);

        themeService.dryRunFetch("https://example.com/t.zip");

        // Temp directory should be cleaned up
        assertFalse(Files.exists(themeDir));

        verifyNoSideEffects();
    }

    @Test
    void dryRunFetchTempDirectoryCleanedUpOnException() throws IOException {
        Path themeDir = createTempTheme("error-test", "1.0.0", null);
        ThemeProperty prop = createThemeProperty("error-test", "Error", "1.0.0", null,
                themeDir.toString());

        assertTrue(Files.exists(themeDir));

        given(mockFetcherComposite.fetch(any(String.class))).willReturn(prop);
        given(themeRepository.fetchThemePropertyByThemeId(anyString()))
                .willThrow(new RuntimeException("test error"));

        assertThrows(RuntimeException.class,
                () -> themeService.dryRunFetch("https://example.com/t.zip"));

        // Temp directory should still be cleaned up even on exception
        assertFalse(Files.exists(themeDir));
    }

    // ==================== dryRunUpload tests ====================

    @Test
    void dryRunUpload() throws IOException {
        Path themeDir = createTempTheme("upload-theme", "1.0.0", null);
        ThemeProperty prop = createThemeProperty("upload-theme", "Upload Theme", "1.0.0", null,
                themeDir.toString());

        MultipartFile mockFile = mock(MultipartFile.class);
        given(mockFile.getOriginalFilename()).willReturn("theme.zip");

        given(mockFetcherComposite.fetch(any(MultipartFile.class))).willReturn(prop);
        given(themeRepository.fetchThemePropertyByThemeId("upload-theme"))
                .willReturn(Optional.empty());
        given(themeRepository.checkThemePropertyCompatibility(prop)).willReturn(false);

        ThemeInstallDryRunResult result = themeService.dryRunUpload(mockFile);

        assertNotNull(result);
        assertEquals("upload-theme", result.getThemeProperty().getId());
        assertFalse(result.isAlreadyExists());
        assertTrue(result.isVersionCompatible());

        verifyNoSideEffects();
    }

    // ==================== dryRunUpdate tests ====================

    @Test
    void dryRunUpdateWithFileChanges() throws IOException {
        // Create old theme
        Path oldThemeDir = tempDir.resolve("templates/themes/old-theme");
        Files.createDirectories(oldThemeDir);
        Files.writeString(oldThemeDir.resolve("theme.yaml"),
                "id: old-theme\nname: Old Theme\nversion: 1.0.0");
        Files.writeString(oldThemeDir.resolve("index.ftl"), "<html>old index</html>");
        Files.writeString(oldThemeDir.resolve("removed.ftl"), "<html>removed</html>");

        ThemeProperty oldProp = createThemeProperty("old-theme", "Old Theme", "1.0.0", null,
                oldThemeDir.toString());

        // Create new theme in temp dir
        Path newThemeDir = createTempTheme("old-theme", "2.0.0", null);
        Files.writeString(newThemeDir.resolve("index.ftl"), "<html>new index</html>");
        Files.writeString(newThemeDir.resolve("added.ftl"), "<html>added</html>");

        ThemeProperty newProp = createThemeProperty("old-theme", "Old Theme", "2.0.0", null,
                newThemeDir.toString());

        given(themeRepository.fetchThemePropertyByThemeId("old-theme"))
                .willReturn(Optional.of(oldProp));
        given(mockFetcherComposite.fetch(any(String.class))).willReturn(newProp);
        given(themeRepository.checkThemePropertyCompatibility(newProp)).willReturn(false);
        given(themeRepository.getActivatedThemeId()).willReturn("other-theme");

        ThemeUpgradeDryRunResult result = themeService.dryRunUpdate("old-theme");

        assertNotNull(result);
        assertEquals("old-theme", result.getCurrentTheme().getId());
        assertEquals("2.0.0", result.getNewTheme().getVersion());
        assertTrue(result.isVersionCompatible());
        assertFalse(result.isAffectsActivatedTheme());

        // Verify file diffs
        assertNotNull(result.getFileDiffs());
        assertTrue(result.isHasFileChanges());

        // index.ftl should be MODIFIED
        FileDiffEntry indexDiff = findFileDiff(result.getFileDiffs(), "index.ftl");
        assertNotNull(indexDiff);
        assertEquals(FileDiffEntry.DiffType.MODIFIED, indexDiff.getDiffType());

        // added.ftl should be ADDED
        FileDiffEntry addedDiff = findFileDiff(result.getFileDiffs(), "added.ftl");
        assertNotNull(addedDiff);
        assertEquals(FileDiffEntry.DiffType.ADDED, addedDiff.getDiffType());

        // removed.ftl should be DELETED
        FileDiffEntry removedDiff = findFileDiff(result.getFileDiffs(), "removed.ftl");
        assertNotNull(removedDiff);
        assertEquals(FileDiffEntry.DiffType.DELETED, removedDiff.getDiffType());

        verifyNoSideEffects();
    }

    @Test
    void dryRunUpdateWithConfigChanges() throws IOException {
        // Create old theme with settings
        Path oldThemeDir = tempDir.resolve("templates/themes/config-theme");
        Files.createDirectories(oldThemeDir);
        Files.writeString(oldThemeDir.resolve("theme.yaml"),
                "id: config-theme\nname: Config Theme\nversion: 1.0.0");
        Files.writeString(oldThemeDir.resolve("settings.yaml"),
                "- name: general\n  label: General\n  items:\n    - name: title\n");

        ThemeProperty oldProp = createThemeProperty("config-theme", "Config Theme", "1.0.0",
                null, oldThemeDir.toString());

        // Create new theme with modified settings
        Path newThemeDir = createTempTheme("config-theme", "2.0.0", null);
        Files.writeString(newThemeDir.resolve("settings.yaml"),
                "- name: general\n  label: General\n  items:\n    - name: title\n    - name: subtitle\n");

        ThemeProperty newProp = createThemeProperty("config-theme", "Config Theme", "2.0.0",
                null, newThemeDir.toString());

        // Old config: title only
        Item titleItem = new Item();
        titleItem.setName("title");
        titleItem.setLabel("Title");
        titleItem.setType(InputType.TEXT);
        titleItem.setDataType(DataType.STRING);
        Group oldGroup = new Group();
        oldGroup.setName("general");
        oldGroup.setLabel("General");
        oldGroup.setItems(List.of(titleItem));

        // New config: title + subtitle
        Item subtitleItem = new Item();
        subtitleItem.setName("subtitle");
        subtitleItem.setLabel("Subtitle");
        subtitleItem.setType(InputType.TEXT);
        subtitleItem.setDataType(DataType.STRING);
        Group newGroup = new Group();
        newGroup.setName("general");
        newGroup.setLabel("General");
        newGroup.setItems(List.of(titleItem, subtitleItem));

        given(themeRepository.fetchThemePropertyByThemeId("config-theme"))
                .willReturn(Optional.of(oldProp));
        given(mockFetcherComposite.fetch(any(String.class))).willReturn(newProp);
        given(themeRepository.checkThemePropertyCompatibility(newProp)).willReturn(false);
        given(themeRepository.getActivatedThemeId()).willReturn("other-theme");

        // Return old config for first call, new config for second call
        given(themeConfigResolver.resolve(anyString()))
                .willReturn(List.of(oldGroup))
                .willReturn(List.of(newGroup));

        ThemeUpgradeDryRunResult result = themeService.dryRunUpdate("config-theme");

        assertNotNull(result);
        assertTrue(result.isHasConfigChanges());

        // subtitle should be ADDED
        ConfigDiffEntry subtitleDiff = findConfigDiff(result.getConfigDiffs(), "subtitle");
        assertNotNull(subtitleDiff);
        assertEquals(ConfigDiffEntry.ConfigDiffType.ADDED, subtitleDiff.getDiffType());

        verifyNoSideEffects();
    }

    @Test
    void dryRunUpdateAffectsActivatedTheme() throws IOException {
        Path oldThemeDir = tempDir.resolve("templates/themes/active-theme");
        Files.createDirectories(oldThemeDir);
        Files.writeString(oldThemeDir.resolve("theme.yaml"),
                "id: active-theme\nname: Active\nversion: 1.0.0");

        ThemeProperty oldProp = createThemeProperty("active-theme", "Active", "1.0.0", null,
                oldThemeDir.toString());

        Path newThemeDir = createTempTheme("active-theme", "2.0.0", null);
        ThemeProperty newProp = createThemeProperty("active-theme", "Active", "2.0.0", null,
                newThemeDir.toString());

        given(themeRepository.fetchThemePropertyByThemeId("active-theme"))
                .willReturn(Optional.of(oldProp));
        given(mockFetcherComposite.fetch(any(String.class))).willReturn(newProp);
        given(themeRepository.checkThemePropertyCompatibility(newProp)).willReturn(false);
        // This is the activated theme
        given(themeRepository.getActivatedThemeId()).willReturn("active-theme");

        ThemeUpgradeDryRunResult result = themeService.dryRunUpdate("active-theme");

        assertTrue(result.isAffectsActivatedTheme());

        verifyNoSideEffects();
    }

    @Test
    void dryRunUpdateNonActivatedTheme() throws IOException {
        Path oldThemeDir = tempDir.resolve("templates/themes/inactive-theme");
        Files.createDirectories(oldThemeDir);
        Files.writeString(oldThemeDir.resolve("theme.yaml"),
                "id: inactive-theme\nname: Inactive\nversion: 1.0.0");

        ThemeProperty oldProp = createThemeProperty("inactive-theme", "Inactive", "1.0.0", null,
                oldThemeDir.toString());

        Path newThemeDir = createTempTheme("inactive-theme", "2.0.0", null);
        ThemeProperty newProp = createThemeProperty("inactive-theme", "Inactive", "2.0.0", null,
                newThemeDir.toString());

        given(themeRepository.fetchThemePropertyByThemeId("inactive-theme"))
                .willReturn(Optional.of(oldProp));
        given(mockFetcherComposite.fetch(any(String.class))).willReturn(newProp);
        given(themeRepository.checkThemePropertyCompatibility(newProp)).willReturn(false);
        given(themeRepository.getActivatedThemeId()).willReturn("other-theme");

        ThemeUpgradeDryRunResult result = themeService.dryRunUpdate("inactive-theme");

        assertFalse(result.isAffectsActivatedTheme());

        verifyNoSideEffects();
    }

    @Test
    void dryRunUpdateThemeNotFound() {
        given(themeRepository.fetchThemePropertyByThemeId("nonexistent"))
                .willReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> themeService.dryRunUpdate("nonexistent"));

        verifyNoSideEffects();
    }

    @Test
    void dryRunUpdateWithUploadDifferentIdMismatch() throws IOException {
        Path oldThemeDir = tempDir.resolve("templates/themes/theme-a");
        Files.createDirectories(oldThemeDir);
        Files.writeString(oldThemeDir.resolve("theme.yaml"),
                "id: theme-a\nname: Theme A\nversion: 1.0.0");

        ThemeProperty oldProp = createThemeProperty("theme-a", "Theme A", "1.0.0", null,
                oldThemeDir.toString());

        // New theme has different ID
        Path newThemeDir = createTempTheme("theme-b", "2.0.0", null);
        ThemeProperty newProp = createThemeProperty("theme-b", "Theme B", "2.0.0", null,
                newThemeDir.toString());

        given(themeRepository.fetchThemePropertyByThemeId("theme-a"))
                .willReturn(Optional.of(oldProp));

        MultipartFile mockFile = mock(MultipartFile.class);
        given(mockFile.getOriginalFilename()).willReturn("theme.zip");
        given(mockFetcherComposite.fetch(any(MultipartFile.class))).willReturn(newProp);

        assertThrows(BadRequestException.class,
                () -> themeService.dryRunUpdate("theme-a", mockFile));

        // New theme temp dir should be cleaned up
        assertFalse(Files.exists(newThemeDir));

        verifyNoSideEffects();
    }

    @Test
    void dryRunUpdateWithUploadSuccess() throws IOException {
        Path oldThemeDir = tempDir.resolve("templates/themes/upload-update");
        Files.createDirectories(oldThemeDir);
        Files.writeString(oldThemeDir.resolve("theme.yaml"),
                "id: upload-update\nname: Upload Update\nversion: 1.0.0");

        ThemeProperty oldProp = createThemeProperty("upload-update", "Upload Update", "1.0.0",
                null, oldThemeDir.toString());

        Path newThemeDir = createTempTheme("upload-update", "2.0.0", null);
        ThemeProperty newProp = createThemeProperty("upload-update", "Upload Update", "2.0.0",
                null, newThemeDir.toString());

        given(themeRepository.fetchThemePropertyByThemeId("upload-update"))
                .willReturn(Optional.of(oldProp));

        MultipartFile mockFile = mock(MultipartFile.class);
        given(mockFile.getOriginalFilename()).willReturn("theme.zip");
        given(mockFetcherComposite.fetch(any(MultipartFile.class))).willReturn(newProp);
        given(themeRepository.checkThemePropertyCompatibility(newProp)).willReturn(false);
        given(themeRepository.getActivatedThemeId()).willReturn("other-theme");

        ThemeUpgradeDryRunResult result = themeService.dryRunUpdate("upload-update", mockFile);

        assertNotNull(result);
        assertEquals("upload-update", result.getCurrentTheme().getId());
        assertEquals("2.0.0", result.getNewTheme().getVersion());

        verifyNoSideEffects();
    }

    @Test
    void dryRunUpdateTempDirectoryCleanedUp() throws IOException {
        Path oldThemeDir = tempDir.resolve("templates/themes/cleanup-update");
        Files.createDirectories(oldThemeDir);
        Files.writeString(oldThemeDir.resolve("theme.yaml"),
                "id: cleanup-update\nname: Cleanup\nversion: 1.0.0");

        ThemeProperty oldProp = createThemeProperty("cleanup-update", "Cleanup", "1.0.0", null,
                oldThemeDir.toString());

        Path newThemeDir = createTempTheme("cleanup-update", "2.0.0", null);
        ThemeProperty newProp = createThemeProperty("cleanup-update", "Cleanup", "2.0.0", null,
                newThemeDir.toString());

        assertTrue(Files.exists(newThemeDir));

        given(themeRepository.fetchThemePropertyByThemeId("cleanup-update"))
                .willReturn(Optional.of(oldProp));
        given(mockFetcherComposite.fetch(any(String.class))).willReturn(newProp);
        given(themeRepository.checkThemePropertyCompatibility(newProp)).willReturn(false);
        given(themeRepository.getActivatedThemeId()).willReturn("other-theme");

        themeService.dryRunUpdate("cleanup-update");

        // New theme temp directory should be cleaned up
        assertFalse(Files.exists(newThemeDir));

        verifyNoSideEffects();
    }

    @Test
    void dryRunUpdateIncompatibleVersion() throws IOException {
        Path oldThemeDir = tempDir.resolve("templates/themes/incompat-update");
        Files.createDirectories(oldThemeDir);
        Files.writeString(oldThemeDir.resolve("theme.yaml"),
                "id: incompat-update\nname: Incompat\nversion: 1.0.0");

        ThemeProperty oldProp = createThemeProperty("incompat-update", "Incompat", "1.0.0",
                null, oldThemeDir.toString());

        Path newThemeDir = createTempTheme("incompat-update", "2.0.0", "99.0.0");
        ThemeProperty newProp = createThemeProperty("incompat-update", "Incompat", "2.0.0",
                "99.0.0", newThemeDir.toString());

        given(themeRepository.fetchThemePropertyByThemeId("incompat-update"))
                .willReturn(Optional.of(oldProp));
        given(mockFetcherComposite.fetch(any(String.class))).willReturn(newProp);
        given(themeRepository.checkThemePropertyCompatibility(newProp)).willReturn(true);
        given(themeRepository.getActivatedThemeId()).willReturn("other-theme");

        ThemeUpgradeDryRunResult result = themeService.dryRunUpdate("incompat-update");

        assertFalse(result.isVersionCompatible());

        verifyNoSideEffects();
    }

    // ==================== Illegal theme package tests ====================

    @Test
    void dryRunFetchIllegalPackageThrowsException() {
        given(mockFetcherComposite.fetch(any(String.class)))
                .willThrow(new run.halo.app.exception.ThemePropertyMissingException(
                        "Missing theme.yaml"));

        assertThrows(run.halo.app.exception.ThemePropertyMissingException.class,
                () -> themeService.dryRunFetch("https://example.com/bad.zip"));

        verifyNoSideEffects();
    }

    // ==================== No side effects verification ====================

    /**
     * Verifies that no side-effect methods were called during the dry-run.
     * This is the core guarantee of the dry-run feature.
     */
    private void verifyNoSideEffects() {
        // Must never write to theme directory
        verify(themeRepository, never()).attemptToAdd(any());
        verify(themeRepository, never()).setActivatedTheme(anyString());
        verify(themeRepository, never()).deleteTheme(anyString());
        verify(themeRepository, never()).deleteTheme(any(ThemeProperty.class));

        // Must never publish events
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ==================== Helper methods ====================

    /**
     * Creates a temporary theme directory with basic structure.
     */
    private Path createTempTheme(String id, String version, String require) throws IOException {
        Path themeDir = Files.createTempDirectory(tempDir, "theme-" + id + "-");
        StringBuilder yaml = new StringBuilder();
        yaml.append("id: ").append(id).append("\n");
        yaml.append("name: ").append(id).append("\n");
        yaml.append("version: ").append(version).append("\n");
        if (require != null) {
            yaml.append("require: ").append(require).append("\n");
        }
        Files.writeString(themeDir.resolve("theme.yaml"), yaml.toString());
        return themeDir;
    }

    /**
     * Creates a ThemeProperty with the given parameters.
     */
    private ThemeProperty createThemeProperty(String id, String name, String version,
            String require, String themePath) {
        ThemeProperty prop = new ThemeProperty();
        prop.setId(id);
        prop.setName(name);
        prop.setVersion(version);
        prop.setRequire(require);
        prop.setThemePath(themePath);
        prop.setFolderName(id);
        return prop;
    }

    /**
     * Finds a file diff entry by relative path.
     */
    private FileDiffEntry findFileDiff(List<FileDiffEntry> diffs, String relativePath) {
        return diffs.stream()
                .filter(d -> d.getRelativePath().equals(relativePath))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds a config diff entry by item name.
     */
    private ConfigDiffEntry findConfigDiff(List<ConfigDiffEntry> diffs, String itemName) {
        return diffs.stream()
                .filter(d -> d.getItemName().equals(itemName))
                .findFirst()
                .orElse(null);
    }
}
