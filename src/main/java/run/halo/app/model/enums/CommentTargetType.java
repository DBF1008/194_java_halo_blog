package run.halo.app.model.enums;

/**
 * Comment target type. Identifies which source a comment belongs to (post, sheet or journal),
 * all of which are stored in the same comments table.
 *
 * @author halo
 */
public enum CommentTargetType implements ValueEnum<Integer> {

    /**
     * Post comment.
     */
    POST(0),

    /**
     * Sheet (independent page) comment.
     */
    SHEET(1),

    /**
     * Journal comment.
     */
    JOURNAL(2);

    private final Integer value;

    CommentTargetType(Integer value) {
        this.value = value;
    }

    @Override
    public Integer getValue() {
        return value;
    }
}
