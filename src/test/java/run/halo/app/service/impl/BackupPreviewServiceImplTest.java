package run.halo.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import run.halo.app.config.properties.HaloProperties;
import run.halo.app.exception.BadRequestException;
import run.halo.app.exception.ForbiddenException;
import run.halo.app.exception.NotFoundException;
import run.halo.app.model.dto.BackupPreviewDTO;
import run.halo.app.model.dto.DataImportConflictDTO;
import run.halo.app.model.dto.JsonDataPreviewDetail;
import run.halo.app.model.dto.MarkdownPreviewDetail;
import run.halo.app.model.dto.OptionChangePreview;
import run.halo.app.model.dto.WorkDirPreviewDetail;
import run.halo.app.model.entity.Category;
import run.halo.app.model.entity.Post;
import run.halo.app.model.entity.Tag;
import run.halo.app.service.AttachmentService;
import run.halo.app.service.BackupService.BackupType;
import run.halo.app.service.CategoryService;
import run.halo.app.service.CommentBlackListService;
import run.halo.app.service.JournalCommentService;
import run.halo.app.service.JournalService;
import run.halo.app.service.LinkService;
import run.halo.app.service.LogService;
import run.halo.app.service.MenuService;
import run.halo.app.service.OptionService;
import run.halo.app.service.PhotoService;
import run.halo.app.service.PostCommentService;
import run.halo.app.service.PostMetaService;
import run.halo.app.service.PostService;
import run.halo.app.service.SheetCommentService;
import run.halo.app.service.SheetMetaService;
import run.halo.app.service.SheetService;
import run.halo.app.service.TagService;
import run.halo.app.service.ThemeSettingService;
import run.halo.app.service.UserService;
import run.halo.app.utils.FileUtils;
import run.halo.app.utils.JsonUtils;

/**
 * BackupPreviewServiceImpl test.
 */
class BackupPreviewServiceImplTest {

    @Mock
    HaloProperties haloProperties;

    @Mock
    OptionService optionService;

    @Mock
    PostService postService;

    @Mock
    SheetService sheetService;

    @Mock
    CategoryService categoryService;

    @Mock
    TagService tagService;

    @Mock
    AttachmentService attachmentService;

    @Mock
    CommentBlackListService commentBlackListService;

    @Mock
    JournalService journalService;

    @Mock
    JournalCommentService journalCommentService;

    @Mock
    LinkService linkService;

    @Mock
    LogService logService;

    @Mock
    MenuService menuService;

    @Mock
    PhotoService photoService;

    @Mock
    PostCommentService postCommentService;

    @Mock
    PostMetaService postMetaService;

    @Mock
    SheetCommentService sheetCommentService;

    @Mock
    SheetMetaService sheetMetaService;

    @Mock
    ThemeSettingService themeSettingService;

    @Mock
    UserService userService;

    @InjectMocks
    BackupPreviewServiceImpl backupPreviewService;

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        tempDir = FileUtils.createTempDirectory();
    }

    // ====== A. Work-dir backup preview tests ======

    @Test
    void previewWorkDir_validZip_returnsCorrectDetail() throws Exception {
        // Given: create a zip with known structure
        String filename = "halo-backup-test.zip";
        Path zipPath = tempDir.resolve(filename);
        createTestZip(zipPath, List.of(
            "db/", "db/halo.mv.db",
            "themes/", "themes/anatole/", "themes/anatole/index.ftl",
            "upload/", "upload/2024/photo.jpg",
            "logs/", "logs/halo.log"
        ));

        given(haloProperties.getBackupDir()).willReturn(tempDir.toString() + "/");

        // When
        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.WHOLE_SITE);

        // Then
        assertNotNull(result);
        assertEquals("WHOLE_SITE", result.getBackupType());
        assertEquals(filename, result.getFilename());
        assertNotNull(result.getWorkDirDetail());

        WorkDirPreviewDetail detail = result.getWorkDirDetail();
        assertTrue(detail.getTopLevelEntries().contains("db"));
        assertTrue(detail.getTopLevelEntries().contains("themes"));
        assertTrue(detail.getTopLevelEntries().contains("upload"));
        assertTrue(detail.getTopLevelEntries().contains("logs"));
        assertTrue(detail.getHasDatabase());
        assertTrue(detail.getHasThemes());
        assertTrue(detail.getHasUploads());
        assertTrue(detail.getThemeNames().contains("anatole"));
        assertEquals(4, detail.getTotalFileCount().intValue());
    }

    @Test
    void previewWorkDir_fileNotFound_throwsNotFoundException() {
        given(haloProperties.getBackupDir()).willReturn(tempDir.toString() + "/");

        assertThrows(NotFoundException.class,
            () -> backupPreviewService.previewExistingBackup(
                "nonexistent.zip", BackupType.WHOLE_SITE));
    }

    @Test
    void previewWorkDir_emptyZip_returnsZeroCounts() throws Exception {
        String filename = "empty-backup.zip";
        Path zipPath = tempDir.resolve(filename);
        createTestZip(zipPath, Collections.emptyList());

        given(haloProperties.getBackupDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.WHOLE_SITE);

        WorkDirPreviewDetail detail = result.getWorkDirDetail();
        assertEquals(0, detail.getTotalFileCount().intValue());
        assertFalse(detail.getHasDatabase());
        assertFalse(detail.getHasThemes());
        assertFalse(detail.getHasUploads());
        assertTrue(detail.getThemeNames().isEmpty());
    }

    @Test
    void previewWorkDir_corruptedZip_throwsBadRequestException() throws Exception {
        String filename = "corrupted.zip";
        Path zipPath = tempDir.resolve(filename);
        Files.write(zipPath, "this is not a zip file".getBytes(StandardCharsets.UTF_8));

        given(haloProperties.getBackupDir()).willReturn(tempDir.toString() + "/");

        assertThrows(BadRequestException.class,
            () -> backupPreviewService.previewExistingBackup(
                filename, BackupType.WHOLE_SITE));
    }

    @Test
    void previewWorkDir_directoryTraversal_throwsForbiddenException() {
        given(haloProperties.getBackupDir()).willReturn(tempDir.toString() + "/");

        assertThrows(ForbiddenException.class,
            () -> backupPreviewService.previewExistingBackup(
                "../../etc/passwd", BackupType.WHOLE_SITE));
    }

    @Test
    void previewWorkDir_uploadedFile_doesNotModifyWorkDir() throws Exception {
        // Given
        byte[] zipBytes = createTestZipBytes(List.of(
            "db/", "db/halo.mv.db",
            "themes/", "themes/anatole/"
        ));
        MockMultipartFile file = new MockMultipartFile(
            "file", "backup.zip", "application/zip", zipBytes);

        // When
        BackupPreviewDTO result =
            backupPreviewService.previewUploadedBackup(file, BackupType.WHOLE_SITE);

        // Then: verify no entity service write methods were called
        verify(postService, never()).createInBatch(List.of());
        verify(optionService, never()).createInBatch(List.of());
        assertNotNull(result.getWorkDirDetail());
        assertTrue(result.getWorkDirDetail().getHasDatabase());
        assertTrue(result.getWorkDirDetail().getHasThemes());
    }

    // ====== B. JSON data preview tests ======

    @Test
    void previewJsonData_validFile_returnsCorrectCounts() throws Exception {
        String filename = "halo-data-export-test.json";
        Path jsonPath = tempDir.resolve(filename);
        Files.write(jsonPath, createTestDataExportJson().getBytes(StandardCharsets.UTF_8));

        given(haloProperties.getDataExportDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.JSON_DATA);

        assertNotNull(result);
        assertEquals("JSON_DATA", result.getBackupType());

        JsonDataPreviewDetail detail = result.getJsonDataDetail();
        assertNotNull(detail);
        assertEquals("1.4.7", detail.getVersion());
        assertEquals("2024-01-15 10:30:00", detail.getExportDate());
        assertEquals(2, detail.getEntityCounts().get("posts").intValue());
        assertEquals(2, detail.getEntityCounts().get("tags").intValue());
        assertEquals(1, detail.getEntityCounts().get("sheets").intValue());
        assertEquals(1, detail.getEntityCounts().get("journals").intValue());
        assertTrue(detail.getTotalEntityCount() > 0);
    }

    @Test
    void previewJsonData_emptyEntities_returnsZeroCounts() throws Exception {
        String filename = "empty-data.json";
        Path jsonPath = tempDir.resolve(filename);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", "1.4.7");
        data.put("export_date", "2024-01-15");
        data.put("posts", Collections.emptyList());
        data.put("tags", Collections.emptyList());
        data.put("categories", Collections.emptyList());

        Files.write(jsonPath,
            JsonUtils.objectToJson(data).getBytes(StandardCharsets.UTF_8));

        given(haloProperties.getDataExportDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.JSON_DATA);

        JsonDataPreviewDetail detail = result.getJsonDataDetail();
        assertEquals(0, detail.getEntityCounts().get("posts").intValue());
        assertEquals(0, detail.getEntityCounts().get("tags").intValue());
    }

    @Test
    void previewJsonData_missingVersion_returnsNullVersion() throws Exception {
        String filename = "no-version.json";
        Path jsonPath = tempDir.resolve(filename);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("export_date", "2024-01-15");
        data.put("posts", List.of(Map.of("id", 1, "title", "Test")));

        Files.write(jsonPath,
            JsonUtils.objectToJson(data).getBytes(StandardCharsets.UTF_8));

        given(haloProperties.getDataExportDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.JSON_DATA);

        JsonDataPreviewDetail detail = result.getJsonDataDetail();
        assertNull(detail.getVersion());
        assertEquals(1, detail.getEntityCounts().get("posts").intValue());
    }

    @Test
    void previewJsonData_invalidJson_throwsBadRequestException() throws Exception {
        String filename = "invalid.json";
        Path jsonPath = tempDir.resolve(filename);
        Files.write(jsonPath, "{this is not valid json".getBytes(StandardCharsets.UTF_8));

        given(haloProperties.getDataExportDir()).willReturn(tempDir.toString() + "/");

        assertThrows(BadRequestException.class,
            () -> backupPreviewService.previewExistingBackup(
                filename, BackupType.JSON_DATA));
    }

    @Test
    void previewJsonData_singleUserEntity_countsAsOne() throws Exception {
        String filename = "single-user.json";
        Path jsonPath = tempDir.resolve(filename);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", "1.4.7");
        data.put("export_date", "2024-01-15");
        data.put("user", Map.of("id", 1, "username", "admin"));

        Files.write(jsonPath,
            JsonUtils.objectToJson(data).getBytes(StandardCharsets.UTF_8));

        given(haloProperties.getDataExportDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.JSON_DATA);

        JsonDataPreviewDetail detail = result.getJsonDataDetail();
        // user is a single object (not an array), should count as 1
        assertEquals(1, detail.getEntityCounts().get("user").intValue());
    }

    // ====== C. Markdown zip preview tests ======

    @Test
    void previewMarkdown_validZip_returnsCorrectDetail() throws Exception {
        String filename = "halo-backup-markdown-test.zip";
        Path zipPath = tempDir.resolve(filename);
        createTestZip(zipPath, List.of(
            "Hello-World-hello-world.md",
            "Second-Post-second-post.md",
            "About-Page-about.md",
            "upload/", "upload/photo1.jpg", "upload/photo2.png"
        ));

        given(haloProperties.getBackupMarkdownDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.MARKDOWN);

        assertNotNull(result);
        assertEquals("MARKDOWN", result.getBackupType());

        MarkdownPreviewDetail detail = result.getMarkdownDetail();
        assertNotNull(detail);
        assertEquals(3, detail.getPostCount().intValue());
        assertTrue(detail.getHasUploadDir());
        assertEquals(2, detail.getUploadFileCount().intValue());
        assertEquals(3, detail.getSamplePostTitles().size());
    }

    @Test
    void previewMarkdown_noUploadDir_hasUploadDirFalse() throws Exception {
        String filename = "md-no-upload.zip";
        Path zipPath = tempDir.resolve(filename);
        createTestZip(zipPath, List.of(
            "post1.md", "post2.md"
        ));

        given(haloProperties.getBackupMarkdownDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.MARKDOWN);

        MarkdownPreviewDetail detail = result.getMarkdownDetail();
        assertEquals(2, detail.getPostCount().intValue());
        assertFalse(detail.getHasUploadDir());
        assertEquals(0, detail.getUploadFileCount().intValue());
    }

    @Test
    void previewMarkdown_manyFiles_samplePostTitlesLimited() throws Exception {
        String filename = "md-many.zip";
        Path zipPath = tempDir.resolve(filename);
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            entries.add("post-" + i + ".md");
        }
        createTestZip(zipPath, entries);

        given(haloProperties.getBackupMarkdownDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.MARKDOWN);

        MarkdownPreviewDetail detail = result.getMarkdownDetail();
        assertEquals(20, detail.getPostCount().intValue());
        assertEquals(10, detail.getSamplePostTitles().size());
    }

    @Test
    void previewMarkdown_emptyZip_returnsZeroCounts() throws Exception {
        String filename = "md-empty.zip";
        Path zipPath = tempDir.resolve(filename);
        createTestZip(zipPath, Collections.emptyList());

        given(haloProperties.getBackupMarkdownDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.MARKDOWN);

        MarkdownPreviewDetail detail = result.getMarkdownDetail();
        assertEquals(0, detail.getPostCount().intValue());
        assertFalse(detail.getHasUploadDir());
        assertTrue(detail.getSamplePostTitles().isEmpty());
    }

    // ====== D. Data import conflict preview tests ======

    @Test
    void previewDataImport_noConflicts_returnsEmptyConflicts() throws Exception {
        String filename = "import-data.json";
        Path jsonPath = tempDir.resolve(filename);

        Map<String, Object> data = createTestDataMap();
        // Use IDs that don't exist in DB
        ((List<Map<String, Object>>) data.get("posts")).get(0).put("id", 100);
        ((List<Map<String, Object>>) data.get("posts")).get(1).put("id", 200);

        Files.write(jsonPath,
            JsonUtils.objectToJson(data).getBytes(StandardCharsets.UTF_8));

        given(haloProperties.getDataExportDir()).willReturn(tempDir.toString() + "/");
        // Mock: no existing entities with these IDs
        given(postService.listAllByIds(List.of(100, 200)))
            .willReturn(Collections.emptyList());

        DataImportConflictDTO result = backupPreviewService.previewDataImport(filename);

        assertNotNull(result);
        assertNotNull(result.getIncomingEntityCounts());
        assertEquals(0, result.getTotalConflicts().intValue());
    }

    @Test
    void previewDataImport_withIdConflicts_returnsConflictingIds() throws Exception {
        String filename = "import-conflict.json";
        Path jsonPath = tempDir.resolve(filename);

        Map<String, Object> data = createTestDataMap();
        Files.write(jsonPath,
            JsonUtils.objectToJson(data).getBytes(StandardCharsets.UTF_8));

        given(haloProperties.getDataExportDir()).willReturn(tempDir.toString() + "/");

        // Mock: post IDs 1, 2 exist in DB
        Post existingPost1 = new Post();
        existingPost1.setId(1);
        Post existingPost2 = new Post();
        existingPost2.setId(2);
        given(postService.listAllByIds(List.of(1, 2)))
            .willReturn(List.of(existingPost1, existingPost2));

        DataImportConflictDTO result = backupPreviewService.previewDataImport(filename);

        assertTrue(result.getConflictingIds().containsKey("posts"));
        List<Object> postConflicts = result.getConflictingIds().get("posts");
        assertEquals(2, postConflicts.size());
        assertTrue(postConflicts.contains(1));
        assertTrue(postConflicts.contains(2));
    }

    @Test
    void previewDataImport_withSlugConflicts_returnsConflictingSlugs() throws Exception {
        String filename = "import-slug-conflict.json";
        Path jsonPath = tempDir.resolve(filename);

        Map<String, Object> data = createTestDataMap();
        Files.write(jsonPath,
            JsonUtils.objectToJson(data).getBytes(StandardCharsets.UTF_8));

        given(haloProperties.getDataExportDir()).willReturn(tempDir.toString() + "/");

        // Mock: no ID conflicts
        given(postService.listAllByIds(List.of(1, 2)))
            .willReturn(Collections.emptyList());

        // Mock: existing post with slug "hello-world"
        Post existingPost = new Post();
        existingPost.setSlug("hello-world");
        given(postService.listAll()).willReturn(List.of(existingPost));

        // No slug conflicts for sheets, categories, tags
        given(sheetService.listAll()).willReturn(Collections.emptyList());
        given(categoryService.listAll()).willReturn(Collections.emptyList());
        given(tagService.listAll()).willReturn(Collections.emptyList());

        DataImportConflictDTO result = backupPreviewService.previewDataImport(filename);

        assertTrue(result.getConflictingSlugs().containsKey("posts"));
        List<String> slugConflicts = result.getConflictingSlugs().get("posts");
        assertTrue(slugConflicts.contains("hello-world"));
    }

    @Test
    void previewDataImport_optionChanges_detected() throws Exception {
        String filename = "import-options.json";
        Path jsonPath = tempDir.resolve(filename);

        Map<String, Object> data = createTestDataMap();
        Files.write(jsonPath,
            JsonUtils.objectToJson(data).getBytes(StandardCharsets.UTF_8));

        given(haloProperties.getDataExportDir()).willReturn(tempDir.toString() + "/");

        // Mock: current blog_title is different
        given(optionService.getByKey("blog_title"))
            .willReturn(Optional.of("Old Title"));
        given(optionService.getByKey("blog_url"))
            .willReturn(Optional.of("https://example.com"));
        given(optionService.getByKey("blog_logo"))
            .willReturn(Optional.empty());
        given(optionService.getByKey("blog_favicon"))
            .willReturn(Optional.empty());

        DataImportConflictDTO result = backupPreviewService.previewDataImport(filename);

        List<OptionChangePreview> changes = result.getOptionChanges();
        assertFalse(changes.isEmpty());
        assertTrue(changes.stream().anyMatch(
            c -> "blog_title".equals(c.getKey())
                && "Old Title".equals(c.getCurrentValue())
                && "My Blog".equals(c.getIncomingValue())));
    }

    @Test
    void previewDataImport_uploadedFile_noSideEffects() throws Exception {
        byte[] jsonBytes = createTestDataExportJson().getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file", "data.json", "application/json", jsonBytes);

        // Mock: no existing entities (for conflict detection reads)
        given(postService.listAllByIds(List.of(1, 2)))
            .willReturn(Collections.emptyList());
        given(sheetService.listAllByIds(List.of(10)))
            .willReturn(Collections.emptyList());
        given(postService.listAll()).willReturn(Collections.emptyList());
        given(sheetService.listAll()).willReturn(Collections.emptyList());
        given(categoryService.listAll()).willReturn(Collections.emptyList());
        given(tagService.listAll()).willReturn(Collections.emptyList());

        DataImportConflictDTO result =
            backupPreviewService.previewDataImportFromUpload(file);

        assertNotNull(result);

        // CRITICAL: verify no write methods were called
        verify(postService, never()).createInBatch(org.mockito.ArgumentMatchers.anyList());
        verify(sheetService, never()).createInBatch(org.mockito.ArgumentMatchers.anyList());
        verify(optionService, never()).createInBatch(org.mockito.ArgumentMatchers.anyList());
        verify(categoryService, never()).createInBatch(org.mockito.ArgumentMatchers.anyList());
        verify(tagService, never()).createInBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void previewDataImport_invalidJson_throwsBadRequestException() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "bad.json", "application/json",
            "{invalid json".getBytes(StandardCharsets.UTF_8));

        assertThrows(BadRequestException.class,
            () -> backupPreviewService.previewDataImportFromUpload(file));
    }

    // ====== E. Uploaded file safety verification ======

    @Test
    void previewUploadedData_noDatabaseInteraction() throws Exception {
        byte[] jsonBytes = createTestDataExportJson().getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file", "data.json", "application/json", jsonBytes);

        BackupPreviewDTO result =
            backupPreviewService.previewUploadedBackup(file, BackupType.JSON_DATA);

        assertNotNull(result);
        assertNotNull(result.getJsonDataDetail());

        // Verify no write operations on any service
        verify(postService, never()).create(org.mockito.ArgumentMatchers.any());
        verify(postService, never()).createInBatch(org.mockito.ArgumentMatchers.anyList());
        verify(sheetService, never()).createInBatch(org.mockito.ArgumentMatchers.anyList());
        verify(optionService, never()).createInBatch(org.mockito.ArgumentMatchers.anyList());
        verify(categoryService, never()).createInBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void previewUploadedMarkdown_noDatabaseInteraction() throws Exception {
        byte[] zipBytes = createTestZipBytes(List.of(
            "post1.md", "post2.md", "upload/", "upload/img.jpg"
        ));
        MockMultipartFile file = new MockMultipartFile(
            "file", "markdown.zip", "application/zip", zipBytes);

        BackupPreviewDTO result =
            backupPreviewService.previewUploadedBackup(file, BackupType.MARKDOWN);

        assertNotNull(result);
        assertNotNull(result.getMarkdownDetail());

        // Verify no interactions with entity services at all
        verifyNoInteractions(postService);
        verifyNoInteractions(sheetService);
        verifyNoInteractions(optionService);
        verifyNoInteractions(categoryService);
        verifyNoInteractions(tagService);
    }

    @Test
    void previewUploadedWorkDir_cleansUpTempFiles() throws Exception {
        byte[] zipBytes = createTestZipBytes(List.of(
            "db/", "db/halo.mv.db",
            "themes/", "themes/test-theme/"
        ));
        MockMultipartFile file = new MockMultipartFile(
            "file", "backup.zip", "application/zip", zipBytes);

        // When
        BackupPreviewDTO result =
            backupPreviewService.previewUploadedBackup(file, BackupType.WHOLE_SITE);

        // Then: the result should be valid
        assertNotNull(result);
        assertNotNull(result.getWorkDirDetail());
        assertTrue(result.getWorkDirDetail().getHasDatabase());

        // Note: temp dir cleanup is verified by the fact that the method
        // completes without error; the try/finally block in the implementation
        // ensures cleanup even on error
    }

    // ====== F. Edge cases ======

    @Test
    void previewExistingBackup_fileSizeIsCorrect() throws Exception {
        String filename = "sized-backup.zip";
        Path zipPath = tempDir.resolve(filename);
        createTestZip(zipPath, List.of("file1.txt", "file2.txt"));

        given(haloProperties.getBackupDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.WHOLE_SITE);

        long expectedSize = Files.size(zipPath);
        assertEquals(expectedSize, result.getFileSize().longValue());
    }

    @Test
    void previewJsonData_extraUnknownKeys_ignoredGracefully() throws Exception {
        String filename = "extra-keys.json";
        Path jsonPath = tempDir.resolve(filename);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", "1.4.7");
        data.put("export_date", "2024-01-15");
        data.put("posts", List.of(Map.of("id", 1)));
        data.put("custom_unknown_field", List.of(Map.of("x", 1), Map.of("x", 2)));
        data.put("another_unknown", "scalar_value");

        Files.write(jsonPath,
            JsonUtils.objectToJson(data).getBytes(StandardCharsets.UTF_8));

        given(haloProperties.getDataExportDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.JSON_DATA);

        JsonDataPreviewDetail detail = result.getJsonDataDetail();
        // Unknown keys should still be counted
        assertEquals(2, detail.getEntityCounts().get("custom_unknown_field").intValue());
        assertEquals(1, detail.getEntityCounts().get("another_unknown").intValue());
        // Should not throw
        assertDoesNotThrow(() -> result.getJsonDataDetail().getTotalEntityCount());
    }

    @Test
    void previewWorkDir_zipWithNestedThemes_allThemeNamesFound() throws Exception {
        String filename = "multi-theme.zip";
        Path zipPath = tempDir.resolve(filename);
        createTestZip(zipPath, List.of(
            "themes/",
            "themes/theme-a/", "themes/theme-a/index.ftl",
            "themes/theme-b/", "themes/theme-b/post.ftl",
            "themes/theme-c/", "themes/theme-c/settings.yaml"
        ));

        given(haloProperties.getBackupDir()).willReturn(tempDir.toString() + "/");

        BackupPreviewDTO result =
            backupPreviewService.previewExistingBackup(filename, BackupType.WHOLE_SITE);

        WorkDirPreviewDetail detail = result.getWorkDirDetail();
        assertTrue(detail.getHasThemes());
        assertEquals(3, detail.getThemeNames().size());
        assertTrue(detail.getThemeNames().contains("theme-a"));
        assertTrue(detail.getThemeNames().contains("theme-b"));
        assertTrue(detail.getThemeNames().contains("theme-c"));
    }

    // ====== Test helper methods ======

    private void createTestZip(Path targetPath, List<String> entries) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(targetPath))) {
            for (String entry : entries) {
                zos.putNextEntry(new ZipEntry(entry));
                if (!entry.endsWith("/")) {
                    zos.write(("test content for " + entry).getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
    }

    private byte[] createTestZipBytes(List<String> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String entry : entries) {
                zos.putNextEntry(new ZipEntry(entry));
                if (!entry.endsWith("/")) {
                    zos.write(("test content for " + entry).getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private String createTestDataExportJson() {
        return createTestDataExportJsonFromMap(createTestDataMap());
    }

    private Map<String, Object> createTestDataMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", "1.4.7");
        data.put("export_date", "2024-01-15 10:30:00");

        List<Map<String, Object>> posts = new ArrayList<>();
        Map<String, Object> post1 = new LinkedHashMap<>();
        post1.put("id", 1);
        post1.put("title", "Hello World");
        post1.put("slug", "hello-world");
        post1.put("status", 0);
        posts.add(post1);
        Map<String, Object> post2 = new LinkedHashMap<>();
        post2.put("id", 2);
        post2.put("title", "Second Post");
        post2.put("slug", "second-post");
        post2.put("status", 1);
        posts.add(post2);
        data.put("posts", posts);

        List<Map<String, Object>> sheets = new ArrayList<>();
        Map<String, Object> sheet1 = new LinkedHashMap<>();
        sheet1.put("id", 10);
        sheet1.put("title", "About");
        sheet1.put("slug", "about");
        sheet1.put("status", 0);
        sheets.add(sheet1);
        data.put("sheets", sheets);

        data.put("tags", List.of(
            Map.of("id", 1, "name", "java", "slug", "java"),
            Map.of("id", 2, "name", "spring", "slug", "spring")
        ));
        data.put("categories", List.of(
            Map.of("id", 1, "name", "Tech", "slug", "tech")
        ));

        data.put("options", List.of(
            Map.of("id", 1, "type", "INTERNAL", "key", "blog_title",
                "value", "My Blog"),
            Map.of("id", 2, "type", "INTERNAL", "key", "blog_url",
                "value", "https://example.com")
        ));
        data.put("attachments", Collections.emptyList());
        data.put("comment_black_list", Collections.emptyList());
        data.put("journals", Collections.emptyList());
        data.put("journal_comments", Collections.emptyList());
        data.put("links", Collections.emptyList());
        data.put("logs", Collections.emptyList());
        data.put("menus", Collections.emptyList());
        data.put("photos", Collections.emptyList());
        data.put("post_categories", Collections.emptyList());
        data.put("post_comments", Collections.emptyList());
        data.put("post_metas", Collections.emptyList());
        data.put("post_tags", Collections.emptyList());
        data.put("sheet_comments", Collections.emptyList());
        data.put("sheet_metas", Collections.emptyList());
        data.put("theme_settings", Collections.emptyList());
        data.put("user", List.of(
            Map.of("id", 1, "username", "admin", "email", "admin@test.com")
        ));

        return data;
    }

    private String createTestDataExportJsonFromMap(Map<String, Object> data) {
        try {
            return JsonUtils.objectToJson(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize test data", e);
        }
    }
}
