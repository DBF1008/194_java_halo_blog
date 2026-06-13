package run.halo.app.controller.admin.api;

import static org.springframework.data.domain.Sort.Direction.DESC;

import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import run.halo.app.model.dto.BaseCommentDTO;
import run.halo.app.model.dto.ModerationStatsDTO;
import run.halo.app.model.entity.CommentBlackList;
import run.halo.app.model.params.CommentBatchBanParam;
import run.halo.app.model.params.ModerationQuery;
import run.halo.app.model.vo.ModerationCommentVO;
import run.halo.app.service.ModerationService;

/**
 * Moderation inbox controller.
 * Provides a unified moderation view across post, sheet and journal comments.
 *
 * @author halo
 */
@RestController
@RequestMapping("/api/admin/comments/moderation")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping
    @ApiOperation("Page the moderation inbox")
    public Page<ModerationCommentVO> pageInbox(
        @PageableDefault(sort = "createTime", direction = DESC) Pageable pageable,
        ModerationQuery query) {
        return moderationService.pageModerationInbox(query, pageable);
    }

    @GetMapping("{commentId:\\d+}")
    @ApiOperation("Get comment detail with reply chain and risk info")
    public ModerationCommentVO getDetail(@PathVariable("commentId") Long commentId) {
        return moderationService.getCommentDetail(commentId);
    }

    @PutMapping("{commentId:\\d+}/status/approve")
    @ApiOperation("Approve a comment")
    public BaseCommentDTO approve(@PathVariable("commentId") Long commentId) {
        return moderationService.approveComment(commentId);
    }

    @PutMapping("{commentId:\\d+}/status/reject")
    @ApiOperation("Reject a comment")
    public BaseCommentDTO reject(@PathVariable("commentId") Long commentId) {
        return moderationService.rejectComment(commentId);
    }

    @PutMapping("batch/approve")
    @ApiOperation("Batch approve comments")
    public List<BaseCommentDTO> batchApprove(@RequestBody List<Long> ids) {
        return moderationService.batchApprove(ids);
    }

    @PutMapping("batch/reject")
    @ApiOperation("Batch reject comments")
    public List<BaseCommentDTO> batchReject(@RequestBody List<Long> ids) {
        return moderationService.batchReject(ids);
    }

    @PostMapping("blacklist/ban")
    @ApiOperation("Ban IP by comment ID")
    public CommentBlackList banIp(
        @RequestParam("commentId") Long commentId,
        @RequestParam(value = "duration", required = false) Integer durationMinutes) {
        return moderationService.banIpByCommentId(commentId, durationMinutes);
    }

    @PostMapping("blacklist/batch-ban")
    @ApiOperation("Batch ban IPs from comment IDs")
    public List<CommentBlackList> batchBanIps(
        @Valid @RequestBody CommentBatchBanParam param) {
        return moderationService.batchBanIps(param);
    }

    @DeleteMapping("blacklist/{ipAddress}")
    @ApiOperation("Unban an IP address")
    public CommentBlackList unbanIp(@PathVariable("ipAddress") String ipAddress) {
        return moderationService.unbanIp(ipAddress);
    }

    @PutMapping("blacklist/{ipAddress}/renew")
    @ApiOperation("Renew ban for an IP address")
    public CommentBlackList renewBan(
        @PathVariable("ipAddress") String ipAddress,
        @RequestParam(value = "duration", required = false) Integer additionalMinutes) {
        return moderationService.renewBan(ipAddress, additionalMinutes);
    }

    @GetMapping("blacklist")
    @ApiOperation("Page blacklist entries")
    public Page<CommentBlackList> pageBlacklist(
        @PageableDefault(sort = "banTime", direction = DESC) Pageable pageable) {
        return moderationService.pageBlacklist(pageable);
    }

    @GetMapping("stats")
    @ApiOperation("Get moderation statistics")
    public ModerationStatsDTO getStats() {
        return moderationService.getModerationStats();
    }
}
