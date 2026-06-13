package run.halo.app.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import run.halo.app.model.dto.BaseCommentDTO;
import run.halo.app.model.dto.ModerationStatsDTO;
import run.halo.app.model.entity.CommentBlackList;
import run.halo.app.model.params.CommentBatchBanParam;
import run.halo.app.model.params.ModerationQuery;
import run.halo.app.model.vo.ModerationCommentVO;

/**
 * Moderation inbox service.
 * Provides a unified view and operations across post, sheet and journal comments
 * for content moderation purposes.
 *
 * @author halo
 */
public interface ModerationService {

    /**
     * Page the moderation inbox with filters.
     *
     * @param query filter parameters
     * @param pageable pagination info
     * @return page of moderation comment VOs
     */
    Page<ModerationCommentVO> pageModerationInbox(ModerationQuery query, Pageable pageable);

    /**
     * Get full detail for a single comment including reply chain and risk info.
     *
     * @param commentId comment ID
     * @return moderation comment VO
     */
    ModerationCommentVO getCommentDetail(Long commentId);

    /**
     * Approve a single comment (set status to PUBLISHED).
     *
     * @param commentId comment ID
     * @return updated comment DTO
     */
    BaseCommentDTO approveComment(Long commentId);

    /**
     * Reject a single comment (set status to RECYCLE).
     *
     * @param commentId comment ID
     * @return updated comment DTO
     */
    BaseCommentDTO rejectComment(Long commentId);

    /**
     * Batch approve comments. Delegates to the same code path as single approve.
     *
     * @param ids comment IDs
     * @return list of updated comment DTOs
     */
    List<BaseCommentDTO> batchApprove(List<Long> ids);

    /**
     * Batch reject comments. Delegates to the same code path as single reject.
     *
     * @param ids comment IDs
     * @return list of updated comment DTOs
     */
    List<BaseCommentDTO> batchReject(List<Long> ids);

    /**
     * Ban the IP address of a specific comment.
     *
     * @param commentId comment ID whose IP to ban
     * @param banDurationMinutes custom ban duration (null uses system default)
     * @return created or updated blacklist entry
     */
    CommentBlackList banIpByCommentId(Long commentId, Integer banDurationMinutes);

    /**
     * Batch ban IPs from a list of comment IDs.
     *
     * @param param batch ban parameters
     * @return list of blacklist entries
     */
    List<CommentBlackList> batchBanIps(CommentBatchBanParam param);

    /**
     * Unban an IP address.
     *
     * @param ipAddress IP to unban
     * @return the removed blacklist entry
     */
    CommentBlackList unbanIp(String ipAddress);

    /**
     * Renew (extend) an existing IP ban.
     *
     * @param ipAddress IP address
     * @param additionalMinutes additional minutes to add (null uses system default)
     * @return updated blacklist entry
     */
    CommentBlackList renewBan(String ipAddress, Integer additionalMinutes);

    /**
     * Get moderation statistics.
     *
     * @return stats DTO
     */
    ModerationStatsDTO getModerationStats();

    /**
     * Page the blacklist entries.
     *
     * @param pageable pagination info
     * @return page of blacklist entries
     */
    Page<CommentBlackList> pageBlacklist(Pageable pageable);
}
