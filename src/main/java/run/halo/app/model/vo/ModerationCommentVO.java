package run.halo.app.model.vo;

import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.model.dto.BaseCommentDTO;
import run.halo.app.model.enums.CommentSourceType;
import run.halo.app.model.enums.CommentStatus;
import run.halo.app.model.enums.ModerationAction;

/**
 * Moderation comment view object.
 * Wraps a comment with moderation-specific context: source type, reply chain,
 * risk indicators, recommended actions and IP blacklist status.
 *
 * @author halo
 */
@Data
@ToString
@EqualsAndHashCode
public class ModerationCommentVO {

    private Long id;

    private String author;

    private String email;

    private String ipAddress;

    private String authorUrl;

    private String gravatarMd5;

    private String content;

    private CommentStatus status;

    private String userAgent;

    private Long parentId;

    private Boolean isAdmin;

    private Boolean allowNotification;

    private Date createTime;

    /**
     * Source type: POST, SHEET, or JOURNAL.
     */
    private CommentSourceType sourceType;

    /**
     * The post/sheet/journal ID this comment belongs to.
     */
    private Integer sourceId;

    /**
     * Title of the source entity.
     * For posts/sheets: the entity title. For journals: truncated content.
     */
    private String sourceTitle;

    /**
     * Ancestor chain from root to this comment (exclusive of this comment).
     * Ordered from root (index 0) to immediate parent (last).
     */
    private List<BaseCommentDTO> replyChain;

    /**
     * Recommended moderation actions based on current state.
     */
    private List<ModerationAction> recommendedActions;

    /**
     * Whether this comment's IP is currently banned.
     */
    private boolean ipBanned;

    /**
     * Ban expiry time if IP is banned; null otherwise.
     */
    private Date ipBanExpiry;

    /**
     * Total number of comments from this IP address.
     */
    private int ipCommentCount;

    /**
     * Detailed risk indicators.
     */
    private RiskIndicators riskIndicators;

    /**
     * Risk indicator details.
     */
    @Data
    public static class RiskIndicators {

        private boolean pendingReview;

        private boolean fromBannedIp;

        private boolean frequentCommenter;
    }
}
