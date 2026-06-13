package run.halo.app.model.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.app.model.enums.ModerationAction;

/**
 * Result of a batch or single comment moderation operation, listing succeeded targets and the
 * reason for each failed target so partial failures are visible.
 *
 * @author halo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentModerationResultVO {

    /**
     * The applied action.
     */
    private ModerationAction action;

    /**
     * Targets that were handled successfully (comment ids or ip addresses as text).
     */
    private List<String> succeeded;

    /**
     * Targets that failed, each with a reason.
     */
    private List<Failure> failed;

    /**
     * Number of succeeded targets.
     */
    private int successCount;

    /**
     * Number of failed targets.
     */
    private int failureCount;

    /**
     * A single failed target with its failure reason.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Failure {

        /**
         * The failed target (comment id or ip address as text).
         */
        private String target;

        /**
         * Failure reason.
         */
        private String reason;
    }
}
