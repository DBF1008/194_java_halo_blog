package run.halo.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import run.halo.app.exception.BadRequestException;
import run.halo.app.exception.NotFoundException;
import run.halo.app.model.dto.BaseCommentDTO;
import run.halo.app.model.dto.ModerationStatsDTO;
import run.halo.app.model.entity.BaseComment;
import run.halo.app.model.entity.CommentBlackList;
import run.halo.app.model.entity.Journal;
import run.halo.app.model.entity.JournalComment;
import run.halo.app.model.entity.Post;
import run.halo.app.model.entity.PostComment;
import run.halo.app.model.entity.Sheet;
import run.halo.app.model.entity.SheetComment;
import run.halo.app.model.enums.CommentSourceType;
import run.halo.app.model.enums.CommentStatus;
import run.halo.app.model.enums.ModerationAction;
import run.halo.app.model.params.CommentBatchBanParam;
import run.halo.app.model.params.ModerationQuery;
import run.halo.app.model.properties.CommentProperties;
import run.halo.app.model.vo.ModerationCommentVO;
import run.halo.app.repository.CommentBlackListRepository;
import run.halo.app.repository.ModerationRepository;
import run.halo.app.service.CommentBlackListService;
import run.halo.app.service.JournalCommentService;
import run.halo.app.service.JournalService;
import run.halo.app.service.OptionService;
import run.halo.app.service.PostCommentService;
import run.halo.app.service.PostService;
import run.halo.app.service.SheetCommentService;
import run.halo.app.service.SheetService;

/**
 * ModerationService unit tests.
 *
 * @author halo
 */
class ModerationServiceTest {

    @Mock
    ModerationRepository moderationRepository;

    @Mock
    PostCommentService postCommentService;

    @Mock
    SheetCommentService sheetCommentService;

    @Mock
    JournalCommentService journalCommentService;

    @Mock
    CommentBlackListService commentBlackListService;

    @Mock
    CommentBlackListRepository commentBlackListRepository;

    @Mock
    PostService postService;

    @Mock
    SheetService sheetService;

    @Mock
    JournalService journalService;

    @Mock
    OptionService optionService;

    @InjectMocks
    ModerationServiceImpl moderationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    // ===== Helper methods =====

    private PostComment buildPostComment(Long id, String ip, CommentStatus status,
        Long parentId) {
        PostComment c = new PostComment();
        c.setId(id);
        c.setIpAddress(ip);
        c.setStatus(status);
        c.setParentId(parentId);
        c.setPostId(1);
        c.setAuthor("author");
        c.setEmail("a@b.com");
        c.setContent("test content");
        c.setCreateTime(new Date());
        return c;
    }

    private SheetComment buildSheetComment(Long id, String ip, CommentStatus status) {
        SheetComment c = new SheetComment();
        c.setId(id);
        c.setIpAddress(ip);
        c.setStatus(status);
        c.setParentId(0L);
        c.setPostId(2);
        c.setAuthor("author");
        c.setEmail("a@b.com");
        c.setContent("sheet comment");
        c.setCreateTime(new Date());
        return c;
    }

    private JournalComment buildJournalComment(Long id, String ip, CommentStatus status) {
        JournalComment c = new JournalComment();
        c.setId(id);
        c.setIpAddress(ip);
        c.setStatus(status);
        c.setParentId(0L);
        c.setPostId(3);
        c.setAuthor("author");
        c.setEmail("a@b.com");
        c.setContent("journal comment");
        c.setCreateTime(new Date());
        return c;
    }

    // ===== Inbox query tests =====

    @Test
    @SuppressWarnings("unchecked")
    void pageInbox_returnsAllTypes() {
        // Given
        PostComment pc = buildPostComment(1L, "1.1.1.1", CommentStatus.AUDITING, 0L);
        SheetComment sc = buildSheetComment(2L, "2.2.2.2", CommentStatus.PUBLISHED);
        JournalComment jc = buildJournalComment(3L, "3.3.3.3", CommentStatus.AUDITING);

        Page<BaseComment> page = new PageImpl<>(
            Arrays.asList(pc, sc, jc), PageRequest.of(0, 10), 3);
        given(moderationRepository.findAll(any(Specification.class), any(Pageable.class)))
            .willReturn(page);
        given(moderationRepository.findAllByIdIn(anyCollection()))
            .willReturn(Collections.emptyList());
        given(commentBlackListRepository.findAllByIpAddressIn(anyCollection()))
            .willReturn(Collections.emptyList());
        given(moderationRepository.countCommentsByIpAddresses(anyCollection()))
            .willReturn(Collections.emptyList());

        Post post = new Post();
        post.setId(1);
        post.setTitle("Test Post");
        given(postService.listAllByIds(anyCollection()))
            .willReturn(Collections.singletonList(post));

        Sheet sheet = new Sheet();
        sheet.setId(2);
        sheet.setTitle("Test Sheet");
        given(sheetService.listAllByIds(anyCollection()))
            .willReturn(Collections.singletonList(sheet));

        Journal journal = new Journal();
        journal.setId(3);
        journal.setContent("Journal content here");
        given(journalService.listAllByIds(anyCollection()))
            .willReturn(Collections.singletonList(journal));

        // When
        ModerationQuery query = new ModerationQuery();
        Page<ModerationCommentVO> result =
            moderationService.pageModerationInbox(query, PageRequest.of(0, 10));

        // Then
        assertEquals(3, result.getTotalElements());
        assertEquals(CommentSourceType.POST, result.getContent().get(0).getSourceType());
        assertEquals(CommentSourceType.SHEET, result.getContent().get(1).getSourceType());
        assertEquals(CommentSourceType.JOURNAL, result.getContent().get(2).getSourceType());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageInbox_filterByStatus() {
        // Given
        PostComment pc = buildPostComment(1L, "1.1.1.1", CommentStatus.AUDITING, 0L);
        Page<BaseComment> page = new PageImpl<>(
            Collections.singletonList(pc), PageRequest.of(0, 10), 1);
        given(moderationRepository.findAll(any(Specification.class), any(Pageable.class)))
            .willReturn(page);
        given(moderationRepository.findAllByIdIn(anyCollection()))
            .willReturn(Collections.emptyList());
        given(commentBlackListRepository.findAllByIpAddressIn(anyCollection()))
            .willReturn(Collections.emptyList());
        given(moderationRepository.countCommentsByIpAddresses(anyCollection()))
            .willReturn(Collections.emptyList());
        given(postService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(sheetService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(journalService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());

        // When
        ModerationQuery query = new ModerationQuery();
        query.setStatus(CommentStatus.AUDITING);
        Page<ModerationCommentVO> result =
            moderationService.pageModerationInbox(query, PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertEquals(CommentStatus.AUDITING, result.getContent().get(0).getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageInbox_emptyResult() {
        // Given
        Page<BaseComment> emptyPage =
            new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        given(moderationRepository.findAll(any(Specification.class), any(Pageable.class)))
            .willReturn(emptyPage);

        // When
        ModerationQuery query = new ModerationQuery();
        Page<ModerationCommentVO> result =
            moderationService.pageModerationInbox(query, PageRequest.of(0, 10));

        // Then
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageInbox_replyChain() {
        // Given: grandparent(id=10) -> parent(id=20) -> child(id=30)
        PostComment grandparent = buildPostComment(10L, "1.1.1.1",
            CommentStatus.PUBLISHED, 0L);
        PostComment parent = buildPostComment(20L, "1.1.1.1",
            CommentStatus.PUBLISHED, 10L);
        PostComment child = buildPostComment(30L, "1.1.1.1",
            CommentStatus.AUDITING, 20L);

        Page<BaseComment> page = new PageImpl<>(
            Collections.singletonList(child), PageRequest.of(0, 10), 1);
        given(moderationRepository.findAll(any(Specification.class), any(Pageable.class)))
            .willReturn(page);

        // Catch-all registered first (lowest priority in Mockito)
        given(moderationRepository.findAllByIdIn(anyCollection()))
            .willReturn(Collections.emptyList());
        // First round: load parent (id=20) - registered later, higher priority
        given(moderationRepository.findAllByIdIn(Collections.singleton(20L)))
            .willReturn(Collections.singletonList(parent));
        // Second round: load grandparent (id=10) - registered last, highest priority
        given(moderationRepository.findAllByIdIn(Collections.singleton(10L)))
            .willReturn(Collections.singletonList(grandparent));

        given(commentBlackListRepository.findAllByIpAddressIn(anyCollection()))
            .willReturn(Collections.emptyList());
        given(moderationRepository.countCommentsByIpAddresses(anyCollection()))
            .willReturn(Collections.emptyList());
        given(postService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(sheetService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(journalService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());

        // When
        Page<ModerationCommentVO> result =
            moderationService.pageModerationInbox(new ModerationQuery(),
                PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        ModerationCommentVO vo = result.getContent().get(0);
        assertEquals(30L, vo.getId());
        assertNotNull(vo.getReplyChain());
        assertEquals(2, vo.getReplyChain().size());
        assertEquals(10L, vo.getReplyChain().get(0).getId());
        assertEquals(20L, vo.getReplyChain().get(1).getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageInbox_blacklistEnrichment() {
        // Given
        PostComment pc = buildPostComment(1L, "1.1.1.1", CommentStatus.AUDITING, 0L);
        Page<BaseComment> page = new PageImpl<>(
            Collections.singletonList(pc), PageRequest.of(0, 10), 1);
        given(moderationRepository.findAll(any(Specification.class), any(Pageable.class)))
            .willReturn(page);
        given(moderationRepository.findAllByIdIn(anyCollection()))
            .willReturn(Collections.emptyList());

        // Blacklist: IP is banned
        CommentBlackList bl = CommentBlackList.builder()
            .ipAddress("1.1.1.1")
            .banTime(new Date(System.currentTimeMillis() + 600000)) // 10 min from now
            .build();
        given(commentBlackListRepository.findAllByIpAddressIn(anyCollection()))
            .willReturn(Collections.singletonList(bl));
        given(moderationRepository.countCommentsByIpAddresses(anyCollection()))
            .willReturn(Collections.emptyList());
        given(postService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(sheetService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(journalService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());

        // When
        Page<ModerationCommentVO> result =
            moderationService.pageModerationInbox(new ModerationQuery(),
                PageRequest.of(0, 10));

        // Then
        ModerationCommentVO vo = result.getContent().get(0);
        assertTrue(vo.isIpBanned());
        assertNotNull(vo.getIpBanExpiry());
        assertTrue(vo.getRiskIndicators().isFromBannedIp());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageInbox_recommendedActions() {
        // Given: AUDITING comment from non-banned IP
        PostComment pc = buildPostComment(1L, "1.1.1.1", CommentStatus.AUDITING, 0L);
        Page<BaseComment> page = new PageImpl<>(
            Collections.singletonList(pc), PageRequest.of(0, 10), 1);
        given(moderationRepository.findAll(any(Specification.class), any(Pageable.class)))
            .willReturn(page);
        given(moderationRepository.findAllByIdIn(anyCollection()))
            .willReturn(Collections.emptyList());
        given(commentBlackListRepository.findAllByIpAddressIn(anyCollection()))
            .willReturn(Collections.emptyList());
        given(moderationRepository.countCommentsByIpAddresses(anyCollection()))
            .willReturn(Collections.emptyList());
        given(postService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(sheetService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(journalService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());

        // When
        Page<ModerationCommentVO> result =
            moderationService.pageModerationInbox(new ModerationQuery(),
                PageRequest.of(0, 10));

        // Then
        List<ModerationAction> actions = result.getContent().get(0).getRecommendedActions();
        assertTrue(actions.contains(ModerationAction.APPROVE));
        assertTrue(actions.contains(ModerationAction.REJECT));
        assertTrue(actions.contains(ModerationAction.BAN_IP));
    }

    // ===== Single-item operation tests =====

    @Test
    void approveComment_postComment() {
        // Given
        PostComment pc = buildPostComment(1L, "1.1.1.1", CommentStatus.AUDITING, 0L);
        given(moderationRepository.findById(1L)).willReturn(Optional.of(pc));
        PostComment updated = buildPostComment(1L, "1.1.1.1", CommentStatus.PUBLISHED, 0L);
        given(postCommentService.updateStatus(1L, CommentStatus.PUBLISHED)).willReturn(updated);

        // When
        BaseCommentDTO result = moderationService.approveComment(1L);

        // Then
        assertNotNull(result);
        assertEquals(CommentStatus.PUBLISHED, result.getStatus());
        then(postCommentService).should().updateStatus(1L, CommentStatus.PUBLISHED);
    }

    @Test
    void approveComment_sheetComment() {
        // Given
        SheetComment sc = buildSheetComment(2L, "2.2.2.2", CommentStatus.AUDITING);
        given(moderationRepository.findById(2L)).willReturn(Optional.of(sc));
        SheetComment updated = buildSheetComment(2L, "2.2.2.2", CommentStatus.PUBLISHED);
        given(sheetCommentService.updateStatus(2L, CommentStatus.PUBLISHED)).willReturn(updated);

        // When
        BaseCommentDTO result = moderationService.approveComment(2L);

        // Then
        assertNotNull(result);
        assertEquals(CommentStatus.PUBLISHED, result.getStatus());
        then(sheetCommentService).should().updateStatus(2L, CommentStatus.PUBLISHED);
    }

    @Test
    void approveComment_journalComment() {
        // Given
        JournalComment jc = buildJournalComment(3L, "3.3.3.3", CommentStatus.AUDITING);
        given(moderationRepository.findById(3L)).willReturn(Optional.of(jc));
        JournalComment updated = buildJournalComment(3L, "3.3.3.3", CommentStatus.PUBLISHED);
        given(journalCommentService.updateStatus(3L, CommentStatus.PUBLISHED))
            .willReturn(updated);

        // When
        BaseCommentDTO result = moderationService.approveComment(3L);

        // Then
        assertNotNull(result);
        assertEquals(CommentStatus.PUBLISHED, result.getStatus());
        then(journalCommentService).should().updateStatus(3L, CommentStatus.PUBLISHED);
    }

    @Test
    void approveComment_notFound() {
        // Given
        given(moderationRepository.findById(999L)).willReturn(Optional.empty());

        // When/Then
        assertThrows(NotFoundException.class, () -> moderationService.approveComment(999L));
    }

    // ===== Batch operation tests =====

    @Test
    void batchApprove_mixedTypes() {
        // Given
        PostComment pc = buildPostComment(1L, "1.1.1.1", CommentStatus.AUDITING, 0L);
        SheetComment sc = buildSheetComment(2L, "2.2.2.2", CommentStatus.AUDITING);

        PostComment updatedPc = buildPostComment(1L, "1.1.1.1", CommentStatus.PUBLISHED, 0L);
        SheetComment updatedSc = buildSheetComment(2L, "2.2.2.2", CommentStatus.PUBLISHED);

        given(moderationRepository.findById(1L)).willReturn(Optional.of(pc));
        given(moderationRepository.findById(2L)).willReturn(Optional.of(sc));
        given(postCommentService.updateStatus(1L, CommentStatus.PUBLISHED))
            .willReturn(updatedPc);
        given(sheetCommentService.updateStatus(2L, CommentStatus.PUBLISHED))
            .willReturn(updatedSc);

        // When
        List<BaseCommentDTO> results = moderationService.batchApprove(Arrays.asList(1L, 2L));

        // Then
        assertEquals(2, results.size());
        assertEquals(CommentStatus.PUBLISHED, results.get(0).getStatus());
        assertEquals(CommentStatus.PUBLISHED, results.get(1).getStatus());
    }

    @Test
    void batchReject_mixedTypes() {
        // Given
        PostComment pc = buildPostComment(1L, "1.1.1.1", CommentStatus.PUBLISHED, 0L);
        JournalComment jc = buildJournalComment(3L, "3.3.3.3", CommentStatus.PUBLISHED);

        PostComment updatedPc = buildPostComment(1L, "1.1.1.1", CommentStatus.RECYCLE, 0L);
        JournalComment updatedJc = buildJournalComment(3L, "3.3.3.3", CommentStatus.RECYCLE);

        given(moderationRepository.findById(1L)).willReturn(Optional.of(pc));
        given(moderationRepository.findById(3L)).willReturn(Optional.of(jc));
        given(postCommentService.updateStatus(1L, CommentStatus.RECYCLE))
            .willReturn(updatedPc);
        given(journalCommentService.updateStatus(3L, CommentStatus.RECYCLE))
            .willReturn(updatedJc);

        // When
        List<BaseCommentDTO> results = moderationService.batchReject(Arrays.asList(1L, 3L));

        // Then
        assertEquals(2, results.size());
        assertEquals(CommentStatus.RECYCLE, results.get(0).getStatus());
        assertEquals(CommentStatus.RECYCLE, results.get(1).getStatus());
    }

    @Test
    void batchApprove_emptyList() {
        // When
        List<BaseCommentDTO> results = moderationService.batchApprove(Collections.emptyList());

        // Then
        assertTrue(results.isEmpty());
    }

    // ===== Ban/Unban tests =====

    @Test
    void banIp_newBan() {
        // Given
        PostComment pc = buildPostComment(1L, "5.5.5.5", CommentStatus.AUDITING, 0L);
        given(moderationRepository.findById(1L)).willReturn(Optional.of(pc));
        given(commentBlackListRepository.findByIpAddress("5.5.5.5"))
            .willReturn(Optional.empty());
        given(optionService.getByPropertyOrDefault(
            eq(CommentProperties.COMMENT_BAN_TIME), eq(Integer.class), eq(10)))
            .willReturn(10);

        CommentBlackList created = CommentBlackList.builder()
            .ipAddress("5.5.5.5")
            .banTime(new Date())
            .build();
        given(commentBlackListService.create(any(CommentBlackList.class)))
            .willReturn(created);

        // When
        CommentBlackList result = moderationService.banIpByCommentId(1L, null);

        // Then
        assertNotNull(result);
        assertEquals("5.5.5.5", result.getIpAddress());
        then(commentBlackListService).should().create(any(CommentBlackList.class));
    }

    @Test
    void banIp_existingBan() {
        // Given
        PostComment pc = buildPostComment(1L, "5.5.5.5", CommentStatus.AUDITING, 0L);
        given(moderationRepository.findById(1L)).willReturn(Optional.of(pc));

        CommentBlackList existing = CommentBlackList.builder()
            .ipAddress("5.5.5.5")
            .banTime(new Date(System.currentTimeMillis() - 1000)) // expired
            .build();
        given(commentBlackListRepository.findByIpAddress("5.5.5.5"))
            .willReturn(Optional.of(existing));
        given(commentBlackListRepository.updateByIpAddress(any(CommentBlackList.class)))
            .willReturn(1);

        // When
        CommentBlackList result = moderationService.banIpByCommentId(1L, 30);

        // Then
        assertNotNull(result);
        then(commentBlackListRepository).should()
            .updateByIpAddress(any(CommentBlackList.class));
    }

    @Test
    void banIp_noIpAddress() {
        // Given
        PostComment pc = buildPostComment(1L, "", CommentStatus.AUDITING, 0L);
        given(moderationRepository.findById(1L)).willReturn(Optional.of(pc));

        // When/Then
        assertThrows(BadRequestException.class,
            () -> moderationService.banIpByCommentId(1L, null));
    }

    @Test
    void batchBanIps_dedup() {
        // Given: two comments with same IP
        PostComment pc1 = buildPostComment(1L, "5.5.5.5", CommentStatus.AUDITING, 0L);
        PostComment pc2 = buildPostComment(2L, "5.5.5.5", CommentStatus.AUDITING, 0L);
        given(moderationRepository.findById(1L)).willReturn(Optional.of(pc1));
        given(moderationRepository.findById(2L)).willReturn(Optional.of(pc2));
        given(commentBlackListRepository.findByIpAddress("5.5.5.5"))
            .willReturn(Optional.empty());
        given(optionService.getByPropertyOrDefault(
            eq(CommentProperties.COMMENT_BAN_TIME), eq(Integer.class), eq(10)))
            .willReturn(10);

        CommentBlackList created = CommentBlackList.builder()
            .ipAddress("5.5.5.5")
            .banTime(new Date())
            .build();
        given(commentBlackListService.create(any(CommentBlackList.class)))
            .willReturn(created);

        CommentBatchBanParam param = new CommentBatchBanParam();
        param.setCommentIds(Arrays.asList(1L, 2L));

        // When
        List<CommentBlackList> results = moderationService.batchBanIps(param);

        // Then: only one ban created (deduped by IP)
        assertEquals(1, results.size());
        then(commentBlackListService).should(times(1)).create(any(CommentBlackList.class));
    }

    @Test
    void unbanIp_success() {
        // Given
        CommentBlackList bl = CommentBlackList.builder()
            .ipAddress("5.5.5.5")
            .banTime(new Date(System.currentTimeMillis() + 600000))
            .build();
        bl.setId(100L);
        given(commentBlackListRepository.findByIpAddress("5.5.5.5"))
            .willReturn(Optional.of(bl));

        // When
        CommentBlackList result = moderationService.unbanIp("5.5.5.5");

        // Then
        assertNotNull(result);
        assertEquals("5.5.5.5", result.getIpAddress());
        then(commentBlackListService).should().removeById(100L);
    }

    @Test
    void unbanIp_notFound() {
        // Given
        given(commentBlackListRepository.findByIpAddress("9.9.9.9"))
            .willReturn(Optional.empty());

        // When/Then
        assertThrows(NotFoundException.class, () -> moderationService.unbanIp("9.9.9.9"));
    }

    @Test
    void renewBan_success() {
        // Given
        CommentBlackList bl = CommentBlackList.builder()
            .ipAddress("5.5.5.5")
            .banTime(new Date(System.currentTimeMillis() + 60000))
            .build();
        given(commentBlackListRepository.findByIpAddress("5.5.5.5"))
            .willReturn(Optional.of(bl));
        given(commentBlackListRepository.updateByIpAddress(any(CommentBlackList.class)))
            .willReturn(1);

        // When
        CommentBlackList result = moderationService.renewBan("5.5.5.5", 15);

        // Then
        assertNotNull(result);
        then(commentBlackListRepository).should()
            .updateByIpAddress(any(CommentBlackList.class));
    }

    @Test
    void renewBan_notFound() {
        // Given
        given(commentBlackListRepository.findByIpAddress("9.9.9.9"))
            .willReturn(Optional.empty());

        // When/Then
        assertThrows(NotFoundException.class,
            () -> moderationService.renewBan("9.9.9.9", 15));
    }

    // ===== Stats test =====

    @Test
    @SuppressWarnings("unchecked")
    void getModerationStats() {
        // Given
        given(moderationRepository.count(any(Specification.class))).willReturn(5L);
        CommentBlackList bl1 = CommentBlackList.builder()
            .ipAddress("1.1.1.1")
            .banTime(new Date(System.currentTimeMillis() + 600000))
            .build();
        CommentBlackList bl2 = CommentBlackList.builder()
            .ipAddress("2.2.2.2")
            .banTime(new Date(System.currentTimeMillis() - 600000)) // expired
            .build();
        given(commentBlackListService.listAll()).willReturn(Arrays.asList(bl1, bl2));

        // When
        ModerationStatsDTO stats = moderationService.getModerationStats();

        // Then
        assertNotNull(stats);
        assertEquals(5L, stats.getPendingCount());
        assertEquals(2, stats.getBlacklistedIpCount());
        assertEquals(1, stats.getActiveBanCount());
    }

    // ===== getCommentDetail test =====

    @Test
    @SuppressWarnings("unchecked")
    void getCommentDetail_withFullChain() {
        // Given
        PostComment parent = buildPostComment(10L, "1.1.1.1",
            CommentStatus.PUBLISHED, 0L);
        PostComment child = buildPostComment(20L, "2.2.2.2",
            CommentStatus.AUDITING, 10L);

        given(moderationRepository.findById(20L)).willReturn(Optional.of(child));
        given(moderationRepository.findAllByIdIn(Collections.singleton(10L)))
            .willReturn(Collections.singletonList(parent));
        given(commentBlackListRepository.findAllByIpAddressIn(anyCollection()))
            .willReturn(Collections.emptyList());
        given(moderationRepository.countCommentsByIpAddresses(anyCollection()))
            .willReturn(Collections.emptyList());
        given(postService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(sheetService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(journalService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());

        // When
        ModerationCommentVO result = moderationService.getCommentDetail(20L);

        // Then
        assertNotNull(result);
        assertEquals(20L, result.getId());
        assertEquals(1, result.getReplyChain().size());
        assertEquals(10L, result.getReplyChain().get(0).getId());
    }

    @Test
    void getCommentDetail_notFound() {
        // Given
        given(moderationRepository.findById(999L)).willReturn(Optional.empty());

        // When/Then
        assertThrows(NotFoundException.class,
            () -> moderationService.getCommentDetail(999L));
    }

    // ===== Risk level tests =====

    @Test
    @SuppressWarnings("unchecked")
    void pageInbox_riskLevel_highRisk() {
        // Given: AUDITING comment from banned IP with high comment count
        PostComment pc = buildPostComment(1L, "1.1.1.1", CommentStatus.AUDITING, 0L);
        Page<BaseComment> page = new PageImpl<>(
            Collections.singletonList(pc), PageRequest.of(0, 10), 1);
        given(moderationRepository.findAll(any(Specification.class), any(Pageable.class)))
            .willReturn(page);
        given(moderationRepository.findAllByIdIn(anyCollection()))
            .willReturn(Collections.emptyList());

        CommentBlackList bl = CommentBlackList.builder()
            .ipAddress("1.1.1.1")
            .banTime(new Date(System.currentTimeMillis() + 600000))
            .build();
        given(commentBlackListRepository.findAllByIpAddressIn(anyCollection()))
            .willReturn(Collections.singletonList(bl));

        // High comment count
        given(moderationRepository.countCommentsByIpAddresses(anyCollection()))
            .willReturn(Collections.singletonList(new Object[] {"1.1.1.1", 50L}));

        given(postService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(sheetService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());
        given(journalService.listAllByIds(anyCollection()))
            .willReturn(Collections.emptyList());

        // When
        Page<ModerationCommentVO> result =
            moderationService.pageModerationInbox(new ModerationQuery(),
                PageRequest.of(0, 10));

        // Then
        ModerationCommentVO vo = result.getContent().get(0);
        assertTrue(vo.getRiskIndicators().isPendingReview());
        assertTrue(vo.getRiskIndicators().isFromBannedIp());
        assertTrue(vo.getRiskIndicators().isFrequentCommenter());
        assertEquals(50, vo.getIpCommentCount());
    }
}
