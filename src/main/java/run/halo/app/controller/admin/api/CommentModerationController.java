package run.halo.app.controller.admin.api;

import static org.springframework.data.domain.Sort.Direction.DESC;

import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import run.halo.app.model.enums.CommentTargetType;
import run.halo.app.model.enums.ModerationAction;
import run.halo.app.model.params.CommentModerationParam;
import run.halo.app.model.params.CommentModerationQuery;
import run.halo.app.model.vo.CommentBlackListVO;
import run.halo.app.model.vo.CommentModerationResultVO;
import run.halo.app.model.vo.CommentModerationVO;
import run.halo.app.model.vo.CommentReplyChainVO;
import run.halo.app.service.CommentModerationService;

/**
 * Comment moderation controller (风险处置台). Exposes a unified moderation surface over post, sheet
 * and journal comments: an inbox risk view, reply chains, the comment black list, per-ip history,
 * and batch / single handling.
 *
 * @author halo
 */
@RestController
@RequestMapping("/api/admin/comments/moderation")
public class CommentModerationController {

    private final CommentModerationService commentModerationService;

    public CommentModerationController(CommentModerationService commentModerationService) {
        this.commentModerationService = commentModerationService;
    }

    @GetMapping("inbox")
    @ApiOperation("Pages the unified moderation inbox across post, sheet and journal comments")
    public Page<CommentModerationVO> pageInbox(
        @PageableDefault(sort = "createTime", direction = DESC) Pageable pageable,
        CommentModerationQuery query) {
        return commentModerationService.pageInbox(query, pageable);
    }

    @GetMapping("reply-chain")
    @ApiOperation("Gets the reply chain (ancestors, the comment and descendants) of a comment")
    public CommentReplyChainVO getReplyChain(
        @RequestParam("targetType") CommentTargetType targetType,
        @RequestParam("commentId") Long commentId) {
        return commentModerationService.getReplyChain(targetType, commentId);
    }

    @GetMapping("blacklist")
    @ApiOperation("Lists comment black list entries with related comment statistics")
    public List<CommentBlackListVO> listBlacklist(
        @RequestParam(name = "onlyBanned", required = false) Boolean onlyBanned) {
        return commentModerationService.listBlacklist(onlyBanned);
    }

    @GetMapping("ip")
    @ApiOperation("Lists the comment history of an ip address across all sources")
    public List<CommentModerationVO> ipHistory(@RequestParam("ipAddress") String ipAddress) {
        return commentModerationService.ipHistory(ipAddress);
    }

    @PostMapping("actions")
    @ApiOperation("Handles a batch moderation request (approve / reject / blacklist / unban)")
    public CommentModerationResultVO moderate(
        @Valid @RequestBody CommentModerationParam param) {
        return commentModerationService.moderate(param);
    }

    @PutMapping("{targetType}/{commentId:\\d+}/action/{action}")
    @ApiOperation("Handles a single comment through the same code path as the batch operation")
    public CommentModerationResultVO handleSingle(
        @PathVariable("targetType") CommentTargetType targetType,
        @PathVariable("commentId") Long commentId,
        @PathVariable("action") ModerationAction action,
        @RequestParam(name = "banMinutes", required = false) Long banMinutes) {
        return commentModerationService.handleSingle(action, targetType, commentId, banMinutes);
    }
}
