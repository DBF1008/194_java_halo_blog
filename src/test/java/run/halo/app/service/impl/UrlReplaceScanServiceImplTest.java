package run.halo.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import run.halo.app.model.dto.UrlReplaceModuleDetail;
import run.halo.app.model.dto.UrlReplaceResult;
import run.halo.app.model.entity.Attachment;
import run.halo.app.model.entity.Post;
import run.halo.app.model.entity.PostComment;
import run.halo.app.model.enums.ReplaceableModule;
import run.halo.app.service.AttachmentService;
import run.halo.app.service.JournalCommentService;
import run.halo.app.service.OptionService;
import run.halo.app.service.PhotoService;
import run.halo.app.service.PostCommentService;
import run.halo.app.service.PostService;
import run.halo.app.service.SheetCommentService;
import run.halo.app.service.SheetService;
import run.halo.app.service.ThemeSettingService;

/**
 * UrlReplaceScanService test.
 *
 * @author halo-dev
 */
class UrlReplaceScanServiceImplTest {

    private static final String OLD_URL = "http://old.example.com";
    private static final String NEW_URL = "http://new.example.com";

    @Mock
    PostService postService;
    @Mock
    SheetService sheetService;
    @Mock
    PostCommentService postCommentService;
    @Mock
    SheetCommentService sheetCommentService;
    @Mock
    JournalCommentService journalCommentService;
    @Mock
    AttachmentService attachmentService;
    @Mock
    OptionService optionService;
    @Mock
    PhotoService photoService;
    @Mock
    ThemeSettingService themeSettingService;

    @InjectMocks
    UrlReplaceScanServiceImpl urlReplaceScanService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void dryRun_shouldNotCallAnyReplaceUrlMethod() {
        // Given
        Set<ReplaceableModule> allModules = EnumSet.allOf(ReplaceableModule.class);
        stubAllServicesReturnEmpty();

        // When
        UrlReplaceResult result = urlReplaceScanService.scan(OLD_URL, NEW_URL, allModules);

        // Then — no replaceUrl should be called
        then(postService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(sheetService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(postCommentService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(sheetCommentService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(journalCommentService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(attachmentService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(optionService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(photoService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(themeSettingService).should(never()).replaceUrl(OLD_URL, NEW_URL);

        assertTrue(result.isDryRun());
    }

    @Test
    void scan_shouldReturnCorrectCounts() {
        // Given — 2 posts, both containing OLD_URL in some fields
        Post post1 = new Post();
        post1.setOriginalContent("Visit " + OLD_URL + " for more info. Also " + OLD_URL);
        post1.setThumbnail(null);
        post1.setFormatContent("some html");

        Post post2 = new Post();
        post2.setOriginalContent("No match here");
        post2.setThumbnail(OLD_URL + "/img.png");
        post2.setFormatContent("No match");

        given(postService.listAll()).willReturn(Arrays.asList(post1, post2));
        stubOtherServicesReturnEmpty();

        Set<ReplaceableModule> modules = EnumSet.of(ReplaceableModule.POSTS);

        // When
        UrlReplaceResult result = urlReplaceScanService.scan(OLD_URL, NEW_URL, modules);

        // Then
        assertEquals(1, result.getModules().size());

        UrlReplaceModuleDetail postDetail = result.getModules().get(0);
        assertEquals(ReplaceableModule.POSTS, postDetail.getModule());
        assertEquals(2, postDetail.getTotalEntities());
        assertEquals(2, postDetail.getMatchedEntities());

        // originalContent: post1 has 2 occurrences, post2 has 0
        assertEquals(1, postDetail.getFields().stream()
            .filter(f -> "originalContent".equals(f.getFieldName()))
            .findFirst().get().getMatchedEntityCount());
        assertEquals(2, postDetail.getFields().stream()
            .filter(f -> "originalContent".equals(f.getFieldName()))
            .findFirst().get().getTotalOccurrences());

        // thumbnail: post1 is null, post2 has 1 occurrence
        assertEquals(1, postDetail.getFields().stream()
            .filter(f -> "thumbnail".equals(f.getFieldName()))
            .findFirst().get().getMatchedEntityCount());
        assertEquals(1, postDetail.getFields().stream()
            .filter(f -> "thumbnail".equals(f.getFieldName()))
            .findFirst().get().getTotalOccurrences());

        // formatContent: no match
        assertEquals(0, postDetail.getFields().stream()
            .filter(f -> "formatContent".equals(f.getFieldName()))
            .findFirst().get().getMatchedEntityCount());
    }

    @Test
    void scan_shouldReturnZeroForNoMatch() {
        // Given — entities exist but none contain the old URL
        Post post = new Post();
        post.setOriginalContent("http://other.example.com content");
        post.setThumbnail("http://other.example.com/img.png");
        post.setFormatContent("no match");
        given(postService.listAll()).willReturn(Collections.singletonList(post));

        Set<ReplaceableModule> modules = EnumSet.of(ReplaceableModule.POSTS);

        // When
        UrlReplaceResult result = urlReplaceScanService.scan(OLD_URL, NEW_URL, modules);

        // Then
        assertEquals(0, result.getTotalMatchedEntities());
        assertEquals(1, result.getModules().get(0).getTotalEntities());
        assertEquals(0, result.getModules().get(0).getMatchedEntities());
        result.getModules().get(0).getFields().forEach(f -> {
            assertEquals(0, f.getMatchedEntityCount());
            assertEquals(0, f.getTotalOccurrences());
        });
    }

    @Test
    void scan_shouldDetectWarningWhenNewUrlAlreadyExists() {
        // Given — entity already contains the NEW_URL
        Post post = new Post();
        post.setOriginalContent(OLD_URL + " and " + NEW_URL + " are both here");
        post.setThumbnail(null);
        post.setFormatContent(null);
        given(postService.listAll()).willReturn(Collections.singletonList(post));

        Set<ReplaceableModule> modules = EnumSet.of(ReplaceableModule.POSTS);

        // When
        UrlReplaceResult result = urlReplaceScanService.scan(OLD_URL, NEW_URL, modules);

        // Then — warning should be present
        UrlReplaceModuleDetail detail = result.getModules().get(0);
        assertFalse(detail.getWarnings().isEmpty());
        assertTrue(detail.getWarnings().stream()
            .anyMatch(w -> w.contains(NEW_URL)));
    }

    @Test
    void execute_shouldOnlyProcessSelectedModules() {
        // Given
        stubAllServicesReturnEmpty();

        Set<ReplaceableModule> selectedModules = EnumSet.of(
            ReplaceableModule.POSTS, ReplaceableModule.ATTACHMENTS);

        // When
        urlReplaceScanService.execute(OLD_URL, NEW_URL, selectedModules);

        // Then — only POSTS and ATTACHMENTS should be replaced
        then(postService).should(times(1)).replaceUrl(OLD_URL, NEW_URL);
        then(attachmentService).should(times(1)).replaceUrl(OLD_URL, NEW_URL);

        // Others should NOT be called
        then(sheetService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(postCommentService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(sheetCommentService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(journalCommentService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(optionService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(photoService).should(never()).replaceUrl(OLD_URL, NEW_URL);
        then(themeSettingService).should(never()).replaceUrl(OLD_URL, NEW_URL);
    }

    @Test
    void execute_emptyModules_shouldProcessAll() {
        // Given
        stubAllServicesReturnEmpty();

        // When — empty set means all modules
        Set<ReplaceableModule> allModules = EnumSet.allOf(ReplaceableModule.class);
        UrlReplaceResult result = urlReplaceScanService.execute(OLD_URL, NEW_URL, allModules);

        // Then — all services should have replaceUrl called
        then(postService).should(times(1)).replaceUrl(OLD_URL, NEW_URL);
        then(sheetService).should(times(1)).replaceUrl(OLD_URL, NEW_URL);
        then(postCommentService).should(times(1)).replaceUrl(OLD_URL, NEW_URL);
        then(sheetCommentService).should(times(1)).replaceUrl(OLD_URL, NEW_URL);
        then(journalCommentService).should(times(1)).replaceUrl(OLD_URL, NEW_URL);
        then(attachmentService).should(times(1)).replaceUrl(OLD_URL, NEW_URL);
        then(optionService).should(times(1)).replaceUrl(OLD_URL, NEW_URL);
        then(photoService).should(times(1)).replaceUrl(OLD_URL, NEW_URL);
        then(themeSettingService).should(times(1)).replaceUrl(OLD_URL, NEW_URL);

        assertFalse(result.isDryRun());
        assertEquals(9, result.getModules().size());
    }

    @Test
    void execute_shouldReturnPreExecutionStats() {
        // Given — 1 post with old URL
        Post post = new Post();
        post.setOriginalContent(OLD_URL + " content");
        post.setThumbnail(null);
        post.setFormatContent(null);
        given(postService.listAll()).willReturn(Collections.singletonList(post));
        stubOtherServicesReturnEmpty();

        Set<ReplaceableModule> modules = EnumSet.of(ReplaceableModule.POSTS);

        // When
        UrlReplaceResult result = urlReplaceScanService.execute(OLD_URL, NEW_URL, modules);

        // Then — stats reflect pre-execution state
        assertFalse(result.isDryRun());
        assertEquals(1, result.getTotalMatchedEntities());
        assertNotNull(result.getModules());
    }

    @Test
    void scan_shouldHandleEmptyEntityLists() {
        // Given — all services return empty lists
        stubAllServicesReturnEmpty();

        Set<ReplaceableModule> allModules = EnumSet.allOf(ReplaceableModule.class);

        // When
        UrlReplaceResult result = urlReplaceScanService.scan(OLD_URL, NEW_URL, allModules);

        // Then
        assertEquals(0, result.getTotalMatchedEntities());
        assertEquals(9, result.getModules().size());
        result.getModules().forEach(detail -> {
            assertEquals(0, detail.getTotalEntities());
            assertEquals(0, detail.getMatchedEntities());
        });
    }

    @Test
    void scan_attachments_shouldCountPathAndThumbPath() {
        // Given
        Attachment attachment = new Attachment();
        attachment.setPath(OLD_URL + "/upload/file.jpg");
        attachment.setThumbPath(OLD_URL + "/upload/file_thumb.jpg");
        given(attachmentService.listAll()).willReturn(Collections.singletonList(attachment));

        Set<ReplaceableModule> modules = EnumSet.of(ReplaceableModule.ATTACHMENTS);

        // When
        UrlReplaceResult result = urlReplaceScanService.scan(OLD_URL, NEW_URL, modules);

        // Then
        UrlReplaceModuleDetail detail = result.getModules().get(0);
        assertEquals(1, detail.getMatchedEntities());
        assertEquals(2, detail.getFields().size());
        detail.getFields().forEach(f -> {
            assertEquals(1, f.getMatchedEntityCount());
            assertEquals(1, f.getTotalOccurrences());
        });
    }

    @Test
    void scan_comments_shouldCountAuthorUrl() {
        // Given
        PostComment comment = new PostComment();
        comment.setAuthorUrl(OLD_URL + "/about");
        given(postCommentService.listAll()).willReturn(Collections.singletonList(comment));

        Set<ReplaceableModule> modules = EnumSet.of(ReplaceableModule.POST_COMMENTS);

        // When
        UrlReplaceResult result = urlReplaceScanService.scan(OLD_URL, NEW_URL, modules);

        // Then
        UrlReplaceModuleDetail detail = result.getModules().get(0);
        assertEquals(1, detail.getMatchedEntities());
        assertEquals(1, detail.getFields().get(0).getMatchedEntityCount());
    }

    @Test
    void countOccurrences_shouldWorkCorrectly() {
        assertEquals(0, UrlReplaceScanServiceImpl.countOccurrences("hello", "xyz"));
        assertEquals(1, UrlReplaceScanServiceImpl.countOccurrences("hello world", "world"));
        assertEquals(3, UrlReplaceScanServiceImpl.countOccurrences("aaa", "a"));
        assertEquals(2, UrlReplaceScanServiceImpl.countOccurrences(
            "http://old.com and http://old.com", "http://old.com"));
        assertEquals(0, UrlReplaceScanServiceImpl.countOccurrences("hello", ""));
    }

    // --- Helper methods ---

    private void stubAllServicesReturnEmpty() {
        given(postService.listAll()).willReturn(Collections.emptyList());
        given(sheetService.listAll()).willReturn(Collections.emptyList());
        given(postCommentService.listAll()).willReturn(Collections.emptyList());
        given(sheetCommentService.listAll()).willReturn(Collections.emptyList());
        given(journalCommentService.listAll()).willReturn(Collections.emptyList());
        given(attachmentService.listAll()).willReturn(Collections.emptyList());
        given(optionService.listAll()).willReturn(Collections.emptyList());
        given(photoService.listAll()).willReturn(Collections.emptyList());
        given(themeSettingService.listAll()).willReturn(Collections.emptyList());
    }

    private void stubOtherServicesReturnEmpty() {
        given(sheetService.listAll()).willReturn(Collections.emptyList());
        given(postCommentService.listAll()).willReturn(Collections.emptyList());
        given(sheetCommentService.listAll()).willReturn(Collections.emptyList());
        given(journalCommentService.listAll()).willReturn(Collections.emptyList());
        given(attachmentService.listAll()).willReturn(Collections.emptyList());
        given(optionService.listAll()).willReturn(Collections.emptyList());
        given(photoService.listAll()).willReturn(Collections.emptyList());
        given(themeSettingService.listAll()).willReturn(Collections.emptyList());
    }
}
