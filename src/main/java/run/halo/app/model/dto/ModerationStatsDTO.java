package run.halo.app.model.dto;

import lombok.Data;

/**
 * Moderation statistics DTO.
 *
 * @author halo
 */
@Data
public class ModerationStatsDTO {

    /**
     * Number of comments with AUDITING status.
     */
    private long pendingCount;

    /**
     * Number of comments with PUBLISHED status.
     */
    private long publishedCount;

    /**
     * Number of comments with RECYCLE status.
     */
    private long recycleCount;

    /**
     * Total number of IP addresses in the blacklist.
     */
    private long blacklistedIpCount;

    /**
     * Number of IPs with a non-expired ban.
     */
    private long activeBanCount;
}
