package run.halo.app.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import run.halo.app.model.enums.CommentTargetType;
import run.halo.app.model.enums.ModerationAction;
import run.halo.app.model.params.CommentModerationParam;
import run.halo.app.model.params.CommentModerationQuery;
import run.halo.app.model.vo.CommentBlackListVO;
import run.halo.app.model.vo.CommentModerationResultVO;
import run.halo.app.model.vo.CommentModerationVO;
import run.halo.app.model.vo.CommentReplyChainVO;

/**
 * Comment moderation service. Provides a unified risk view and batch handling across post, sheet
 * and journal comments by orchestrating the existing comment services and the comment black list
 * service.
 *
 * @author halo
 */
public interface CommentModerationService {

    /**
     * Pages a unified moderation inbox merged from post, sheet and journal comments, each item
     * enriched with target title, ip correlation and risk flags.
     *
     * @param query moderation query, nullable for defaults
     * @param pageable page info must not be null
     * @return a page of unified moderation items sorted by create time descending
     */
    @NonNull
    Page<CommentModerationVO> pageInbox(@Nullable CommentModerationQuery query,
        @NonNull Pageable pageable);

    /**
     * Gets the reply chain of a comment: its ancestors (root to parent), the comment itself and the
     * descendant tree.
     *
     * @param targetType comment target type must not be null
     * @param commentId comment id must not be null
     * @return the reply chain
     */
    @NonNull
    CommentReplyChainVO getReplyChain(@NonNull CommentTargetType targetType,
        @NonNull Long commentId);

    /**
     * Lists comment black list entries with related comment statistics.
     *
     * @param onlyBanned only include entries whose ban is currently effective, nullable for all
     * @return a list of black list items
     */
    @NonNull
    List<CommentBlackListVO> listBlacklist(@Nullable Boolean onlyBanned);

    /**
     * Lists the comment history of a given ip address across all sources, including ban info.
     *
     * @param ipAddress ip address must not be blank
     * @return a list of unified moderation items sorted by create time descending
     */
    @NonNull
    List<CommentModerationVO> ipHistory(@NonNull String ipAddress);

    /**
     * Handles a batch moderation request. Each target is processed independently through the same
     * per-item code path as single handling, so partial failures are reported.
     *
     * @param param moderation request must not be null
     * @return the moderation result
     */
    @NonNull
    CommentModerationResultVO moderate(@NonNull CommentModerationParam param);

    /**
     * Handles a single comment moderation action through the exact same code path as the batch
     * operation, guaranteeing identical behavior.
     *
     * @param action moderation action must not be null
     * @param targetType comment target type must not be null
     * @param commentId comment id must not be null
     * @param banMinutes ban duration in minutes for the BLACKLIST action, nullable for long-term
     * @return the moderation result
     */
    @NonNull
    CommentModerationResultVO handleSingle(@NonNull ModerationAction action,
        @NonNull CommentTargetType targetType, @NonNull Long commentId, @Nullable Long banMinutes);
}
