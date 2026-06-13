package run.halo.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import run.halo.app.config.properties.HaloProperties;
import run.halo.app.exception.BadRequestException;
import run.halo.app.exception.NotFoundException;
import run.halo.app.model.dto.OptionDiffDTO;
import run.halo.app.model.dto.SnapshotDTO;
import run.halo.app.model.dto.SnapshotData;
import run.halo.app.model.entity.Option;
import run.halo.app.service.OptionService;
import run.halo.app.utils.JsonUtils;

/**
 * Tests for {@link OptionSnapshotServiceImpl}.
 *
 * @author halo
 * @date 2024-01-01
 */
class OptionSnapshotServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock
    OptionService optionService;

    @Mock
    HaloProperties haloProperties;

    @Captor
    ArgumentCaptor<Map<String, Object>> mapCaptor;

    OptionSnapshotServiceImpl snapshotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        given(haloProperties.getWorkDir()).willReturn(tempDir.toString());
        snapshotService = new OptionSnapshotServiceImpl(optionService, haloProperties);
    }

    private Option createOption(String key, String value) {
        Option option = new Option();
        option.setKey(key);
        option.setValue(value);
        return option;
    }

    private void writeSnapshotFile(String name, Map<String, String> options) throws IOException {
        Path dir = tempDir.resolve(".snapshots");
        Files.createDirectories(dir);
        SnapshotData data = new SnapshotData(name, new Date(), options);
        String json = JsonUtils.objectToJson(data);
        Files.write(dir.resolve(name + ".json"), json.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== Create Snapshot Tests ====================

    @Test
    void createSnapshotShouldSucceed() {
        // Given
        List<Option> options = Arrays.asList(
            createOption("blog_title", "My Blog"),
            createOption("blog_url", "https://example.com"));
        given(optionService.listAll()).willReturn(options);

        // When
        SnapshotDTO result = snapshotService.create("test-snapshot");

        // Then
        assertNotNull(result);
        assertEquals("test-snapshot", result.getName());
        assertNotNull(result.getCreateTime());
        assertEquals(2, result.getOptionCount());

        // Verify file was written
        assertTrue(Files.exists(tempDir.resolve(".snapshots/test-snapshot.json")));
    }

    @Test
    void createDuplicateSnapshotShouldFail() throws IOException {
        // Given
        writeSnapshotFile("existing", Collections.singletonMap("key", "val"));
        given(optionService.listAll()).willReturn(
            Collections.singletonList(createOption("key", "val")));

        // When / Then
        assertThrows(BadRequestException.class, () -> snapshotService.create("existing"));
    }

    @Test
    void createWithInvalidNameContainingSlashShouldFail() {
        assertThrows(BadRequestException.class, () -> snapshotService.create("bad/name"));
    }

    @Test
    void createWithInvalidNameContainingDotDotShouldFail() {
        assertThrows(BadRequestException.class, () -> snapshotService.create("bad..name"));
    }

    @Test
    void createWithInvalidNameContainingSpaceShouldFail() {
        assertThrows(BadRequestException.class, () -> snapshotService.create("bad name"));
    }

    @Test
    void createWithBlankNameShouldFail() {
        assertThrows(IllegalArgumentException.class, () -> snapshotService.create(""));
    }

    // ==================== List Snapshot Tests ====================

    @Test
    void listSnapshotsShouldReturnAll() throws IOException {
        // Given
        writeSnapshotFile("snap1", Collections.singletonMap("key1", "val1"));
        writeSnapshotFile("snap2", Collections.singletonMap("key2", "val2"));

        // When
        List<SnapshotDTO> result = snapshotService.list();

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void listSnapshotsOnEmptyDirShouldReturnEmptyList() {
        // When
        List<SnapshotDTO> result = snapshotService.list();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== Get Snapshot Tests ====================

    @Test
    void getExistingSnapshotShouldReturnDetail() throws IOException {
        // Given
        Map<String, String> options = new LinkedHashMap<>();
        options.put("blog_title", "My Blog");
        options.put("blog_url", "https://example.com");
        writeSnapshotFile("test-get", options);

        // When
        SnapshotDTO result = snapshotService.get("test-get");

        // Then
        assertEquals("test-get", result.getName());
        assertEquals(2, result.getOptionCount());
        assertNotNull(result.getCreateTime());
    }

    @Test
    void getNonExistentSnapshotShouldThrow() {
        assertThrows(NotFoundException.class, () -> snapshotService.get("nonexistent"));
    }

    // ==================== Remove Snapshot Tests ====================

    @Test
    void removeExistingSnapshotShouldSucceed() throws IOException {
        // Given
        writeSnapshotFile("to-remove", Collections.singletonMap("key", "val"));

        // When
        snapshotService.remove("to-remove");

        // Then
        assertFalse(Files.exists(tempDir.resolve(".snapshots/to-remove.json")));
    }

    @Test
    void removeNonExistentSnapshotShouldThrow() {
        assertThrows(NotFoundException.class, () -> snapshotService.remove("nonexistent"));
    }

    // ==================== Diff Tests ====================

    @Test
    void diffWithChangesShouldDetectAddedRemovedAndChanged() throws IOException {
        // Given - snapshot has: a=1, b=2, c=3
        Map<String, String> snapshotOptions = new LinkedHashMap<>();
        snapshotOptions.put("a", "1");
        snapshotOptions.put("b", "2");
        snapshotOptions.put("c", "3");
        writeSnapshotFile("diff-test", snapshotOptions);

        // Current DB has: b=CHANGED, c=3 (same), d=4 (new in DB)
        // "a" is missing from DB → will be added when applying
        // "d" is in DB but not in snapshot → will be removed when applying
        // "b" changed from "2" to "CHANGED"
        List<Option> currentOptions = Arrays.asList(
            createOption("b", "CHANGED"),
            createOption("c", "3"),
            createOption("d", "4"));
        given(optionService.listAll()).willReturn(currentOptions);

        // When
        OptionDiffDTO diff = snapshotService.diff("diff-test");

        // Then
        assertEquals("diff-test", diff.getSnapshotName());

        // "a" is in snapshot but not in current → added
        assertEquals(1, diff.getAdded().size());
        assertEquals("1", diff.getAdded().get("a"));

        // "d" is in current but not in snapshot → removed
        assertEquals(1, diff.getRemoved().size());
        assertEquals("4", diff.getRemoved().get("d"));

        // "b" changed
        assertEquals(1, diff.getChanged().size());
        assertEquals("2", diff.getChanged().get("b").getSnapshotValue());
        assertEquals("CHANGED", diff.getChanged().get("b").getCurrentValue());
    }

    @Test
    void diffWithNoChangesShouldReturnEmptyMaps() throws IOException {
        // Given - snapshot and current are identical
        Map<String, String> options = new LinkedHashMap<>();
        options.put("key1", "val1");
        options.put("key2", "val2");
        writeSnapshotFile("no-change", options);

        given(optionService.listAll()).willReturn(Arrays.asList(
            createOption("key1", "val1"),
            createOption("key2", "val2")));

        // When
        OptionDiffDTO diff = snapshotService.diff("no-change");

        // Then
        assertTrue(diff.getAdded().isEmpty());
        assertTrue(diff.getRemoved().isEmpty());
        assertTrue(diff.getChanged().isEmpty());
    }

    @Test
    void diffWithEmptySnapshotShouldShowAllCurrentAsRemoved() throws IOException {
        // Given - empty snapshot
        writeSnapshotFile("empty-snap", Collections.emptyMap());

        List<Option> currentOptions = Arrays.asList(
            createOption("key1", "val1"),
            createOption("key2", "val2"));
        given(optionService.listAll()).willReturn(currentOptions);

        // When
        OptionDiffDTO diff = snapshotService.diff("empty-snap");

        // Then
        assertTrue(diff.getAdded().isEmpty());
        assertEquals(2, diff.getRemoved().size());
        assertTrue(diff.getChanged().isEmpty());
    }

    // ==================== Full Apply (Rollback) Tests ====================

    @Test
    void fullApplyShouldRemoveAllThenSave() throws IOException {
        // Given
        Map<String, String> snapshotOptions = new LinkedHashMap<>();
        snapshotOptions.put("blog_title", "Old Blog");
        snapshotOptions.put("blog_url", "https://old.example.com");
        writeSnapshotFile("rollback", snapshotOptions);

        // When
        snapshotService.apply("rollback");

        // Then
        then(optionService).should().removeAll();
        then(optionService).should().save(mapCaptor.capture());

        Map<String, Object> savedMap = mapCaptor.getValue();
        assertEquals(2, savedMap.size());
        assertEquals("Old Blog", savedMap.get("blog_title"));
        assertEquals("https://old.example.com", savedMap.get("blog_url"));
    }

    @Test
    void fullApplyWithExtraKeysInCurrentShouldRemoveAllFirst() throws IOException {
        // Given - snapshot has only "blog_title", but current DB also has "blog_url"
        // After removeAll + save, only "blog_title" should exist
        Map<String, String> snapshotOptions = new LinkedHashMap<>();
        snapshotOptions.put("blog_title", "Snapshot Title");
        writeSnapshotFile("shrink", snapshotOptions);

        // When
        snapshotService.apply("shrink");

        // Then - removeAll clears everything, save only adds snapshot keys
        then(optionService).should().removeAll();
        then(optionService).should().save(mapCaptor.capture());

        Map<String, Object> savedMap = mapCaptor.getValue();
        assertEquals(1, savedMap.size());
        assertEquals("Snapshot Title", savedMap.get("blog_title"));
        // "blog_url" is NOT in the saved map → effectively removed
        assertFalse(savedMap.containsKey("blog_url"));
    }

    @Test
    void fullApplyNonExistentSnapshotShouldThrow() {
        assertThrows(NotFoundException.class, () -> snapshotService.apply("nonexistent"));
    }

    // ==================== Partial Apply Tests ====================

    @Test
    void partialApplyShouldOnlySaveSpecifiedKeys() throws IOException {
        // Given - snapshot has 3 keys
        Map<String, String> snapshotOptions = new LinkedHashMap<>();
        snapshotOptions.put("key1", "val1");
        snapshotOptions.put("key2", "val2");
        snapshotOptions.put("key3", "val3");
        writeSnapshotFile("partial", snapshotOptions);

        // When - only restore key1 and key3
        snapshotService.applyPartially("partial", Arrays.asList("key1", "key3"));

        // Then - only key1 and key3 should be passed to save
        then(optionService).should().save(mapCaptor.capture());
        Map<String, Object> savedMap = mapCaptor.getValue();

        assertEquals(2, savedMap.size());
        assertEquals("val1", savedMap.get("key1"));
        assertEquals("val3", savedMap.get("key3"));
        assertFalse(savedMap.containsKey("key2"));
    }

    @Test
    void partialApplyWithNonExistentKeysShouldIgnoreThem() throws IOException {
        // Given - snapshot has key1 and key2
        Map<String, String> snapshotOptions = new LinkedHashMap<>();
        snapshotOptions.put("key1", "val1");
        snapshotOptions.put("key2", "val2");
        writeSnapshotFile("partial-miss", snapshotOptions);

        // When - request key1 and a non-existent key
        snapshotService.applyPartially("partial-miss", Arrays.asList("key1", "nonexistent"));

        // Then - only key1 should be passed to save
        then(optionService).should().save(mapCaptor.capture());
        Map<String, Object> savedMap = mapCaptor.getValue();

        assertEquals(1, savedMap.size());
        assertEquals("val1", savedMap.get("key1"));
    }

    @Test
    void partialApplyWithAllNonExistentKeysShouldNotCallSave() throws IOException {
        // Given
        writeSnapshotFile("partial-empty", Collections.singletonMap("key1", "val1"));

        // When - request keys that don't exist in the snapshot
        snapshotService.applyPartially("partial-empty", Arrays.asList("nope1", "nope2"));

        // Then - save should NOT be called (no matching keys)
        then(optionService).should(never()).save(anyMap());
    }

    // ==================== Event & Cache Chain Tests ====================

    @Test
    void applyShouldCallSaveWhichTriggersEventChain() throws IOException {
        // Given
        Map<String, String> snapshotOptions = new LinkedHashMap<>();
        snapshotOptions.put("blog_title", "Event Test");
        writeSnapshotFile("event-test", snapshotOptions);

        // When
        snapshotService.apply("event-test");

        // Then - save() is the entry point for OptionUpdatedEvent publishing chain
        // In production, OptionServiceImpl.save() triggers:
        //   FreemarkerConfigAwareListener → cache delete + reload vars
        //   ThemeUpdatedListener → theme cache clear
        //   ThemeRepositoryImpl → currentTheme reset
        //   MailServiceImpl → mail sender cache clear
        then(optionService).should().save(anyMap());
        then(optionService).should().removeAll();
    }

    @Test
    void applyUnchangedOptionsShouldStillCallSave() throws IOException {
        // Given - snapshot matches current exactly
        Map<String, String> snapshotOptions = new LinkedHashMap<>();
        snapshotOptions.put("key1", "val1");
        writeSnapshotFile("unchanged", snapshotOptions);

        given(optionService.listAll()).willReturn(
            Collections.singletonList(createOption("key1", "val1")));

        // When
        snapshotService.apply("unchanged");

        // Then - save is still called (real OptionServiceImpl.save() will detect
        // no changes and skip event publishing internally)
        then(optionService).should().removeAll();
        then(optionService).should().save(anyMap());
    }

    @Test
    void applyEmptySnapshotShouldRemoveAllAndSaveEmptyMap() throws IOException {
        // Given - empty snapshot
        writeSnapshotFile("empty", Collections.emptyMap());

        // When
        snapshotService.apply("empty");

        // Then - removeAll clears DB, save with empty map is a no-op
        then(optionService).should().removeAll();
        then(optionService).should().save(anyMap());
    }
}
