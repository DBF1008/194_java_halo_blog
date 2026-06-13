package run.halo.app.service.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import run.halo.app.config.properties.HaloProperties;
import run.halo.app.exception.BadRequestException;
import run.halo.app.exception.NotFoundException;
import run.halo.app.model.dto.BackupManifestDTO;
import run.halo.app.model.entity.Option;
import run.halo.app.model.entity.Post;
import run.halo.app.model.entity.User;
import run.halo.app.security.service.OneTimeTokenService;
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
import run.halo.app.service.PostCategoryService;
import run.halo.app.service.PostCommentService;
import run.halo.app.service.PostMetaService;
import run.halo.app.service.PostService;
import run.halo.app.service.PostTagService;
import run.halo.app.service.SheetCommentService;
import run.halo.app.service.SheetMetaService;
import run.halo.app.service.SheetService;
import run.halo.app.service.TagService;
import run.halo.app.service.ThemeSettingService;
import run.halo.app.service.UserService;
import run.halo.app.utils.JsonUtils;

/**
 * Tests for the read-only backup preview ({@link BackupServiceImpl#previewBackup} and
 * {@link BackupServiceImpl#previewUploadedBackup}).
 *
 * <p>The tests cover the three backup types (data, work directory and markdown), missing and
 * illegal files, and prove that producing a preview never writes to the database nor touches the
 * work directory.</p>
 *
 * @author halo
 */
class BackupServiceImplTest {

    @Mock
    AttachmentService attachmentService;

    @Mock
    CategoryService categoryService;

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
    OptionService optionService;

    @Mock
    PhotoService photoService;

    @Mock
    PostService postService;

    @Mock
    PostCategoryService postCategoryService;

    @Mock
    PostCommentService postCommentService;

    @Mock
    PostMetaService postMetaService;

    @Mock
    PostTagService postTagService;

    @Mock
    SheetService sheetService;

    @Mock
    SheetCommentService sheetCommentService;

    @Mock
    SheetMetaService sheetMetaService;

    @Mock
    TagService tagService;

    @Mock
    ThemeSettingService themeSettingService;

    @Mock
    UserService userService;

    @Mock
    OneTimeTokenService oneTimeTokenService;

    @Mock
    HaloProperties haloProperties;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    BackupServiceImpl backupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void previewDataBackupCountsContentAndDetectsConflicts() throws IOException {
        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("posts", List.of(mapWithId(1), mapWithId(2)));
        tables.put("sheets", List.of(mapWithId(9)));
        tables.put("options", List.of(optionMap(1, "blog_title"), optionMap(2, "blog_url")));
        tables.put("user", List.of(mapWithId(1)));

        // Existing site data that should collide with the backup.
        Post existingPost = new Post();
        existingPost.setId(1);
        given(postService.listAll()).willReturn(List.of(existingPost));

        Option existingOption = new Option("blog_title", "current title");
        existingOption.setId(1);
        given(optionService.listAll()).willReturn(List.of(existingOption));

        User existingUser = new User();
        existingUser.setId(1);
        given(userService.listAll()).willReturn(List.of(existingUser));

        MockMultipartFile file = multipart("data.json", dataJson(tables).getBytes(UTF_8));

        BackupManifestDTO manifest = backupService.previewUploadedBackup(file, BackupType.JSON_DATA);

        assertEquals(BackupType.JSON_DATA, manifest.getBackupType());
        assertTrue(manifest.isFromUpload());
        assertNotNull(manifest.getData());
        assertNull(manifest.getArchive());

        Map<String, Integer> counts = manifest.getData().getContentCounts();
        assertEquals(2, counts.get("posts").intValue());
        assertEquals(1, counts.get("sheets").intValue());
        assertEquals(1, counts.get("user").intValue());
        assertEquals(0, counts.get("links").intValue());

        BackupManifestDTO.TableConflict postsConflict = manifest.getData().getConflicts().stream()
            .filter(conflict -> "posts".equals(conflict.getTable()))
            .findFirst()
            .orElseThrow();
        assertEquals(2, postsConflict.getBackupCount());
        assertEquals(1, postsConflict.getExistingCount());
        assertEquals(1, postsConflict.getConflictCount());
        assertTrue(postsConflict.getSampleConflictIds().contains("1"));

        assertTrue(manifest.getData().getConflictingOptionKeys().contains("blog_title"));
        assertFalse(manifest.getData().getConflictingOptionKeys().contains("blog_url"));
        assertTrue(manifest.getData().isUserConflict());

        assertTrue(manifest.getWarnings().stream().anyMatch(w -> w.contains("追加写入")));
        assertTrue(manifest.getWarnings().stream().anyMatch(w -> w.contains("选项键")));
    }

    @Test
    void previewDataBackupWithEmptySiteReportsNoConflicts() throws IOException {
        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("posts", List.of(mapWithId(1)));
        tables.put("options", List.of(optionMap(1, "blog_title")));

        MockMultipartFile file = multipart("data.json", dataJson(tables).getBytes(UTF_8));

        BackupManifestDTO manifest = backupService.previewUploadedBackup(file, BackupType.JSON_DATA);

        BackupManifestDTO.TableConflict postsConflict = manifest.getData().getConflicts().stream()
            .filter(conflict -> "posts".equals(conflict.getTable()))
            .findFirst()
            .orElseThrow();
        assertEquals(1, postsConflict.getBackupCount());
        assertEquals(0, postsConflict.getExistingCount());
        assertEquals(0, postsConflict.getConflictCount());

        assertTrue(manifest.getData().getConflictingOptionKeys().isEmpty());
        assertFalse(manifest.getData().isUserConflict());
    }

    @Test
    void previewWorkDirBackupCountsArchiveEntries() throws IOException {
        byte[] zip = zipBytes(List.of("templates/", "templates/index.ftl", "application.yaml"));
        MockMultipartFile file = multipart("backup.zip", zip);

        BackupManifestDTO manifest =
            backupService.previewUploadedBackup(file, BackupType.WHOLE_SITE);

        assertEquals(BackupType.WHOLE_SITE, manifest.getBackupType());
        assertNull(manifest.getData());
        assertNotNull(manifest.getArchive());

        BackupManifestDTO.ArchiveManifest archive = manifest.getArchive();
        assertEquals(3, archive.getTotalEntries());
        assertEquals(1, archive.getDirectoryCount());
        assertEquals(2, archive.getFileCount());
        assertTrue(archive.getTopLevelEntries().contains("templates"));
        assertTrue(archive.getTopLevelEntries().contains("application.yaml"));
        // Markdown-only fields are left untouched for a whole-site archive.
        assertNull(archive.getMarkdownCount());
        assertNull(archive.getContainsUpload());
    }

    @Test
    void previewMarkdownBackupWithUploadDirectory() throws IOException {
        byte[] zip = zipBytes(List.of("123/first.md", "123/second.md", "upload/2021/cover.png"));
        MockMultipartFile file = multipart("markdown.zip", zip);

        BackupManifestDTO manifest = backupService.previewUploadedBackup(file, BackupType.MARKDOWN);

        BackupManifestDTO.ArchiveManifest archive = manifest.getArchive();
        assertNotNull(archive);
        assertEquals(2, archive.getMarkdownCount().intValue());
        assertEquals(Boolean.TRUE, archive.getContainsUpload());
        assertEquals(1, archive.getUploadFileCount().intValue());
        assertTrue(manifest.getWarnings().stream().anyMatch(w -> w.contains("upload")));
    }

    @Test
    void previewMarkdownBackupWithoutUploadDirectory() throws IOException {
        byte[] zip = zipBytes(List.of("123/first.md"));
        MockMultipartFile file = multipart("markdown.zip", zip);

        BackupManifestDTO manifest = backupService.previewUploadedBackup(file, BackupType.MARKDOWN);

        BackupManifestDTO.ArchiveManifest archive = manifest.getArchive();
        assertNotNull(archive);
        assertEquals(1, archive.getMarkdownCount().intValue());
        assertEquals(Boolean.FALSE, archive.getContainsUpload());
        assertEquals(0, archive.getUploadFileCount().intValue());
        assertTrue(manifest.getWarnings().stream().anyMatch(w -> w.contains("不包含 upload")));
    }

    @Test
    void previewStoredBackupReadsFileFromDisk(@TempDir Path tempDir) throws IOException {
        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("posts", List.of(mapWithId(1)));
        Path backupFile = tempDir.resolve("data.json");
        Files.write(backupFile, dataJson(tables).getBytes(UTF_8));

        BackupManifestDTO manifest = backupService.previewBackup(backupFile, BackupType.JSON_DATA);

        assertFalse(manifest.isFromUpload());
        assertEquals("data.json", manifest.getFilename());
        assertEquals(1, manifest.getData().getContentCounts().get("posts").intValue());
    }

    @Test
    void previewMissingStoredBackupThrowsNotFound(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.json");

        assertThrows(NotFoundException.class,
            () -> backupService.previewBackup(missing, BackupType.JSON_DATA));
    }

    @Test
    void previewEmptyUploadedBackupThrowsBadRequest() {
        MockMultipartFile file = multipart("empty.json", new byte[0]);

        assertThrows(BadRequestException.class,
            () -> backupService.previewUploadedBackup(file, BackupType.JSON_DATA));
    }

    @Test
    void previewIllegalDataBackupThrowsBadRequest() {
        MockMultipartFile file =
            multipart("data.json", "this is definitely not valid json".getBytes(UTF_8));

        assertThrows(BadRequestException.class,
            () -> backupService.previewUploadedBackup(file, BackupType.JSON_DATA));
    }

    @Test
    void previewIllegalArchiveBackupThrowsBadRequest() {
        byte[] garbage =
            "this content is not a zip archive at all, it has no local file header".getBytes(UTF_8);
        MockMultipartFile file = multipart("backup.zip", garbage);

        assertThrows(BadRequestException.class,
            () -> backupService.previewUploadedBackup(file, BackupType.WHOLE_SITE));
    }

    @Test
    void previewDataBackupHasNoSideEffects() throws IOException {
        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("attachments", List.of(mapWithId(1)));
        tables.put("categories", List.of(mapWithId(1)));
        tables.put("comment_black_list", List.of(mapWithId(1)));
        tables.put("journals", List.of(mapWithId(1)));
        tables.put("journal_comments", List.of(mapWithId(1)));
        tables.put("links", List.of(mapWithId(1)));
        tables.put("logs", List.of(mapWithId(1)));
        tables.put("menus", List.of(mapWithId(1)));
        tables.put("options", List.of(optionMap(1, "blog_title")));
        tables.put("photos", List.of(mapWithId(1)));
        tables.put("posts", List.of(mapWithId(1)));
        tables.put("post_categories", List.of(mapWithId(1)));
        tables.put("post_comments", List.of(mapWithId(1)));
        tables.put("post_metas", List.of(mapWithId(1)));
        tables.put("post_tags", List.of(mapWithId(1)));
        tables.put("sheets", List.of(mapWithId(1)));
        tables.put("sheet_comments", List.of(mapWithId(1)));
        tables.put("sheet_metas", List.of(mapWithId(1)));
        tables.put("tags", List.of(mapWithId(1)));
        tables.put("theme_settings", List.of(mapWithId(1)));
        tables.put("user", List.of(mapWithId(1)));

        MockMultipartFile file = multipart("data.json", dataJson(tables).getBytes(UTF_8));

        backupService.previewUploadedBackup(file, BackupType.JSON_DATA);

        verify(attachmentService, never()).createInBatch(any());
        verify(categoryService, never()).createInBatch(any());
        verify(commentBlackListService, never()).createInBatch(any());
        verify(journalService, never()).createInBatch(any());
        verify(journalCommentService, never()).createInBatch(any());
        verify(linkService, never()).createInBatch(any());
        verify(logService, never()).createInBatch(any());
        verify(menuService, never()).createInBatch(any());
        verify(optionService, never()).createInBatch(any());
        verify(photoService, never()).createInBatch(any());
        verify(postService, never()).createInBatch(any());
        verify(postCategoryService, never()).createInBatch(any());
        verify(postCommentService, never()).createInBatch(any());
        verify(postMetaService, never()).createInBatch(any());
        verify(postTagService, never()).createInBatch(any());
        verify(sheetService, never()).createInBatch(any());
        verify(sheetCommentService, never()).createInBatch(any());
        verify(sheetMetaService, never()).createInBatch(any());
        verify(tagService, never()).createInBatch(any());
        verify(themeSettingService, never()).createInBatch(any());
        verify(userService, never()).create(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private Map<String, Object> mapWithId(Object id) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        return row;
    }

    private Map<String, Object> optionMap(Integer id, String key) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("key", key);
        row.put("value", "value-of-" + key);
        return row;
    }

    private String dataJson(Map<String, Object> tables) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", "1.4.0");
        data.put("export_date", "2026-06-13 10:00:00");
        data.putAll(tables);
        return JsonUtils.objectToJson(data);
    }

    private MockMultipartFile multipart(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, null, content);
    }

    private byte[] zipBytes(List<String> entryNames) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
            for (String entryName : entryNames) {
                zipOut.putNextEntry(new ZipEntry(entryName));
                if (!entryName.endsWith("/")) {
                    zipOut.write(("content of " + entryName).getBytes(UTF_8));
                }
                zipOut.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
