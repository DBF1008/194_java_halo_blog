package run.halo.app.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import run.halo.app.controller.admin.api.ModerationController;
import run.halo.app.exception.NotFoundException;
import run.halo.app.model.dto.BaseCommentDTO;
import run.halo.app.model.dto.ModerationStatsDTO;
import run.halo.app.model.entity.CommentBlackList;
import run.halo.app.model.enums.CommentSourceType;
import run.halo.app.model.enums.CommentStatus;
import run.halo.app.model.enums.ModerationAction;
import run.halo.app.model.params.CommentBatchBanParam;
import run.halo.app.model.vo.ModerationCommentVO;
import run.halo.app.service.ModerationService;

/**
 * ModerationController tests using standalone MockMvc.
 *
 * @author halo
 */
@ExtendWith(MockitoExtension.class)
class ModerationControllerTest {

    @Mock
    ModerationService moderationService;

    @InjectMocks
    ModerationController moderationController;

    MockMvc mockMvc;

    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(moderationController)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    // ===== Helper methods =====

    private ModerationCommentVO buildVo(Long id, CommentStatus status,
        CommentSourceType sourceType) {
        ModerationCommentVO vo = new ModerationCommentVO();
        vo.setId(id);
        vo.setStatus(status);
        vo.setSourceType(sourceType);
        vo.setSourceId(1);
        vo.setSourceTitle("Test");
        vo.setAuthor("author");
        vo.setEmail("a@b.com");
        vo.setContent("content");
        vo.setIpAddress("1.1.1.1");
        vo.setParentId(0L);
        vo.setCreateTime(new Date());
        vo.setReplyChain(Collections.emptyList());
        vo.setRecommendedActions(Arrays.asList(
            ModerationAction.APPROVE, ModerationAction.REJECT));
        vo.setIpBanned(false);
        vo.setIpCommentCount(1);

        ModerationCommentVO.RiskIndicators indicators =
            new ModerationCommentVO.RiskIndicators();
        indicators.setPendingReview(status == CommentStatus.AUDITING);
        vo.setRiskIndicators(indicators);
        return vo;
    }

    private BaseCommentDTO buildDto(Long id, CommentStatus status) {
        BaseCommentDTO dto = new BaseCommentDTO();
        dto.setId(id);
        dto.setStatus(status);
        dto.setAuthor("author");
        dto.setEmail("a@b.com");
        dto.setContent("content");
        return dto;
    }

    // ===== Inbox tests =====

    @Test
    void pageInbox_default() throws Exception {
        // Given
        ModerationCommentVO vo = buildVo(1L, CommentStatus.AUDITING, CommentSourceType.POST);
        Page<ModerationCommentVO> page =
            new PageImpl<>(Collections.singletonList(vo), PageRequest.of(0, 10), 1);
        given(moderationService.pageModerationInbox(any(ModerationQuery.class),
            any(Pageable.class))).willReturn(page);

        // When/Then
        mockMvc.perform(get("/api/admin/comments/moderation"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[0].sourceType").value("POST"))
            .andExpect(jsonPath("$.content[0].status").value("AUDITING"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pageInbox_withFilters() throws Exception {
        // Given
        ModerationCommentVO vo = buildVo(2L, CommentStatus.AUDITING, CommentSourceType.POST);
        Page<ModerationCommentVO> page =
            new PageImpl<>(Collections.singletonList(vo), PageRequest.of(0, 10), 1);
        given(moderationService.pageModerationInbox(any(ModerationQuery.class),
            any(Pageable.class))).willReturn(page);

        // When/Then
        mockMvc.perform(get("/api/admin/comments/moderation")
            .param("status", "AUDITING")
            .param("sourceType", "POST"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(2));
    }

    @Test
    void getDetail_success() throws Exception {
        // Given
        ModerationCommentVO vo = buildVo(1L, CommentStatus.AUDITING, CommentSourceType.POST);
        given(moderationService.getCommentDetail(1L)).willReturn(vo);

        // When/Then
        mockMvc.perform(get("/api/admin/comments/moderation/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.replyChain").isArray());
    }

    // ===== Single-item operation tests =====

    @Test
    void approve_success() throws Exception {
        // Given
        BaseCommentDTO dto = buildDto(1L, CommentStatus.PUBLISHED);
        given(moderationService.approveComment(1L)).willReturn(dto);

        // When/Then
        mockMvc.perform(put("/api/admin/comments/moderation/1/status/approve"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void reject_success() throws Exception {
        // Given
        BaseCommentDTO dto = buildDto(1L, CommentStatus.RECYCLE);
        given(moderationService.rejectComment(1L)).willReturn(dto);

        // When/Then
        mockMvc.perform(put("/api/admin/comments/moderation/1/status/reject"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RECYCLE"));
    }

    // ===== Batch operation tests =====

    @Test
    void batchApprove_success() throws Exception {
        // Given
        List<BaseCommentDTO> dtos = Arrays.asList(
            buildDto(1L, CommentStatus.PUBLISHED),
            buildDto(2L, CommentStatus.PUBLISHED),
            buildDto(3L, CommentStatus.PUBLISHED));
        given(moderationService.batchApprove(any())).willReturn(dtos);

        // When/Then
        mockMvc.perform(put("/api/admin/comments/moderation/batch/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Arrays.asList(1L, 2L, 3L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void batchReject_success() throws Exception {
        // Given
        List<BaseCommentDTO> dtos = Arrays.asList(
            buildDto(1L, CommentStatus.RECYCLE),
            buildDto(2L, CommentStatus.RECYCLE));
        given(moderationService.batchReject(any())).willReturn(dtos);

        // When/Then
        mockMvc.perform(put("/api/admin/comments/moderation/batch/reject")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Arrays.asList(1L, 2L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].status").value("RECYCLE"));
    }

    // ===== Blacklist tests =====

    @Test
    void banIp_success() throws Exception {
        // Given
        CommentBlackList bl = CommentBlackList.builder()
            .ipAddress("1.1.1.1")
            .banTime(new Date(System.currentTimeMillis() + 600000))
            .build();
        given(moderationService.banIpByCommentId(eq(1L), anyInt())).willReturn(bl);

        // When/Then
        mockMvc.perform(post("/api/admin/comments/moderation/blacklist/ban")
            .param("commentId", "1")
            .param("duration", "30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ipAddress").value("1.1.1.1"));
    }

    @Test
    void batchBanIps_success() throws Exception {
        // Given
        CommentBlackList bl = CommentBlackList.builder()
            .ipAddress("1.1.1.1")
            .banTime(new Date())
            .build();
        given(moderationService.batchBanIps(any(CommentBatchBanParam.class)))
            .willReturn(Collections.singletonList(bl));

        CommentBatchBanParam param = new CommentBatchBanParam();
        param.setCommentIds(Arrays.asList(1L, 2L));
        param.setBanDurationMinutes(30);

        // When/Then
        mockMvc.perform(post("/api/admin/comments/moderation/blacklist/batch-ban")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(param)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void unbanIp_success() throws Exception {
        // Given
        CommentBlackList bl = CommentBlackList.builder()
            .ipAddress("1.1.1.1")
            .banTime(new Date())
            .build();
        given(moderationService.unbanIp("1.1.1.1")).willReturn(bl);

        // When/Then
        mockMvc.perform(delete("/api/admin/comments/moderation/blacklist/1.1.1.1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ipAddress").value("1.1.1.1"));
    }

    @Test
    void renewBan_success() throws Exception {
        // Given
        CommentBlackList bl = CommentBlackList.builder()
            .ipAddress("1.1.1.1")
            .banTime(new Date(System.currentTimeMillis() + 900000))
            .build();
        given(moderationService.renewBan(eq("1.1.1.1"), anyInt())).willReturn(bl);

        // When/Then
        mockMvc.perform(put("/api/admin/comments/moderation/blacklist/1.1.1.1/renew")
            .param("duration", "15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ipAddress").value("1.1.1.1"));
    }

    @Test
    void pageBlacklist_success() throws Exception {
        // Given
        CommentBlackList bl = CommentBlackList.builder()
            .ipAddress("1.1.1.1")
            .banTime(new Date())
            .build();
        Page<CommentBlackList> page =
            new PageImpl<>(Collections.singletonList(bl), PageRequest.of(0, 10), 1);
        given(moderationService.pageBlacklist(any(Pageable.class))).willReturn(page);

        // When/Then
        mockMvc.perform(get("/api/admin/comments/moderation/blacklist"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].ipAddress").value("1.1.1.1"));
    }

    // ===== Stats test =====

    @Test
    void stats_success() throws Exception {
        // Given
        ModerationStatsDTO stats = new ModerationStatsDTO();
        stats.setPendingCount(10);
        stats.setPublishedCount(50);
        stats.setRecycleCount(5);
        stats.setBlacklistedIpCount(3);
        stats.setActiveBanCount(2);
        given(moderationService.getModerationStats()).willReturn(stats);

        // When/Then
        mockMvc.perform(get("/api/admin/comments/moderation/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingCount").value(10))
            .andExpect(jsonPath("$.activeBanCount").value(2));
    }

    // ===== Error case tests =====

    @Test
    void approve_notFound_propagates() {
        // Given: NotFoundException is thrown by the service
        given(moderationService.approveComment(999L))
            .willThrow(new NotFoundException("Comment not found"));

        // When/Then: with standalone MockMvc (no @ControllerAdvice),
        // the exception propagates as NestedServletException
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
            mockMvc.perform(put("/api/admin/comments/moderation/999/status/approve")));
    }
}
