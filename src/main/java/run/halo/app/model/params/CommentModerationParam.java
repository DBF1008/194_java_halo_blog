package run.halo.app.model.params;

import java.util.List;
import lombok.Data;
import run.halo.app.model.enums.CommentTargetType;
import run.halo.app.model.enums.ModerationAction;

/**
 * Batch comment moderation request params.
 *
 * @author halo
 */
@Data
public class CommentModerationParam {

    /**
     * Action to apply to the batch.
     */
    private ModerationAction action;

    /**
     * Comment targets handled by APPROVE / REJECT / BLACKLIST actions.
     */
    private List<Item> items;

    /**
     * Ip addresses handled by the UNBAN action.
     */
    private List<String> ipAddresses;

    /**
     * Ban duration in minutes for the BLACKLIST action, nullable for a long-term ban.
     */
    private Long banMinutes;

    /**
     * A single comment target identified by its source type and id.
     */
    @Data
    public static class Item {

        /**
         * Comment target type.
         */
        private CommentTargetType targetType;

        /**
         * Comment id.
         */
        private Long commentId;
    }
}
