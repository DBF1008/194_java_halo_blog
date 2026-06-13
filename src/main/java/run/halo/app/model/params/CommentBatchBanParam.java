package run.halo.app.model.params;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * Parameters for batch-banning IPs based on comment IDs.
 *
 * @author halo
 */
@Data
public class CommentBatchBanParam {

    /**
     * Comment IDs whose IP addresses should be banned.
     */
    @NotEmpty(message = "Comment IDs must not be empty")
    private List<Long> commentIds;

    /**
     * Custom ban duration in minutes. If null, the system default is used.
     */
    private Integer banDurationMinutes;
}
