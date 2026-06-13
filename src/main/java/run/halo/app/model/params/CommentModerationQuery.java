package run.halo.app.model.params;

import lombok.Data;
import run.halo.app.model.enums.CommentStatus;
import run.halo.app.model.enums.CommentTargetType;

/**
 * Comment moderation inbox query params.
 *
 * @author halo
 */
@Data
public class CommentModerationQuery {

    /**
     * Keyword to match against author or content.
     */
    private String keyword;

    /**
     * Filter by comment status, nullable for all statuses.
     */
    private CommentStatus status;

    /**
     * Filter by comment target type, nullable for all sources.
     */
    private CommentTargetType targetType;

    /**
     * Only include comments whose ip address is currently banned.
     */
    private Boolean onlyBanned;

    /**
     * Only include comments whose ip address is flagged as frequent.
     */
    private Boolean onlyFrequent;
}
