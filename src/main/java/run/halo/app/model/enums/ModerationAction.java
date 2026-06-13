package run.halo.app.model.enums;

/**
 * Moderation action that can be applied from the comment moderation inbox.
 *
 * @author halo
 */
public enum ModerationAction {

    /**
     * Approve the comment(s), setting status to PUBLISHED.
     */
    APPROVE,

    /**
     * Reject the comment(s), setting status to RECYCLE.
     */
    REJECT,

    /**
     * Blacklist the related ip address and reject the comment(s).
     */
    BLACKLIST,

    /**
     * Remove the ban of the given ip address(es).
     */
    UNBAN
}
