package run.halo.app.model.params;

import lombok.Data;
import run.halo.app.model.enums.CommentSourceType;
import run.halo.app.model.enums.CommentStatus;

/**
 * Query parameters for the moderation inbox.
 *
 * @author halo
 */
@Data
public class ModerationQuery {

    /**
     * Search keyword in author, content, or email.
     */
    private String keyword;

    /**
     * Filter by comment status (null = all).
     */
    private CommentStatus status;

    /**
     * Filter by source type (null = all).
     */
    private CommentSourceType sourceType;

    /**
     * Filter by IP banned status (null = all).
     */
    private Boolean ipBanned;
}
