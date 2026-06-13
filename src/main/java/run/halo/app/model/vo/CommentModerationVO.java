package run.halo.app.model.vo;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.app.model.enums.CommentStatus;
import run.halo.app.model.enums.CommentTargetType;
import run.halo.app.model.enums.ModerationAction;

/**
 * Unified comment moderation item across post, sheet and journal comments.
 *
 * @author halo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentModerationVO {

    /**
     * Comment target type (source).
     */
    private CommentTargetType targetType;

    /**
     * Target id (post, sheet or journal id).
     */
    private Integer targetId;

    /**
     * Target title for display.
     */
    private String targetTitle;

    /**
     * Comment id.
     */
    private Long id;

    /**
     * Commentator name.
     */
    private String author;

    /**
     * Commentator email.
     */
    private String email;

    /**
     * Commentator ip address.
     */
    private String ipAddress;

    /**
     * Comment content.
     */
    private String content;

    /**
     * Comment status.
     */
    private CommentStatus status;

    /**
     * Parent comment id.
     */
    private Long parentId;

    /**
     * Comment create time.
     */
    private Date createTime;

    /**
     * Whether the comment is from an admin.
     */
    private Boolean isAdmin;

    /**
     * Whether the ip address is currently banned.
     */
    private Boolean banned;

    /**
     * Ban expiry time of the ip address, null when not banned.
     */
    private Date banTime;

    /**
     * Whether the ip address is flagged as frequent within the gathered comments.
     */
    private Boolean frequent;

    /**
     * Number of gathered comments sharing the same ip address.
     */
    private Long ipCommentCount;

    /**
     * Recommended handling action, null when no action is recommended.
     */
    private ModerationAction recommendedAction;
}
