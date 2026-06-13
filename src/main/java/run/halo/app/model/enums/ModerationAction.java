package run.halo.app.model.enums;

/**
 * Recommended moderation actions for a comment in the moderation inbox.
 *
 * @author halo
 */
public enum ModerationAction {

    /**
     * Approve the comment (set status to PUBLISHED).
     */
    APPROVE,

    /**
     * Reject the comment (set status to RECYCLE).
     */
    REJECT,

    /**
     * Ban the commenter's IP address.
     */
    BAN_IP,

    /**
     * Unban the commenter's IP address.
     */
    UNBAN_IP,

    /**
     * Renew (extend) an existing IP ban.
     */
    RENEW_BAN
}
