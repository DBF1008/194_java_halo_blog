package run.halo.app.model.vo;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Comment black list item with related comment statistics.
 *
 * @author halo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentBlackListVO {

    /**
     * Banned ip address.
     */
    private String ipAddress;

    /**
     * Ban expiry time.
     */
    private Date banTime;

    /**
     * Whether the ban is currently effective (ban time after now).
     */
    private Boolean banned;

    /**
     * Number of comments related to this ip address.
     */
    private Long relatedCommentCount;

    /**
     * Create time of the latest comment from this ip address.
     */
    private Date lastCommentTime;
}
