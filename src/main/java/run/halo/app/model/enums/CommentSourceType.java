package run.halo.app.model.enums;

import lombok.Getter;

/**
 * Comment source type for moderation inbox.
 * Maps to the JPA discriminator column value on the comments table.
 *
 * @author halo
 */
@Getter
public enum CommentSourceType {

    POST(0, "post"),
    SHEET(1, "sheet"),
    JOURNAL(2, "journal");

    private final int discriminator;
    private final String label;

    CommentSourceType(int discriminator, String label) {
        this.discriminator = discriminator;
        this.label = label;
    }

    /**
     * Resolve from JPA discriminator value.
     *
     * @param discriminator discriminator value
     * @return corresponding CommentSourceType
     */
    public static CommentSourceType fromDiscriminator(int discriminator) {
        for (CommentSourceType type : values()) {
            if (type.discriminator == discriminator) {
                return type;
            }
        }
        throw new IllegalArgumentException(
            "Unknown comment type discriminator: " + discriminator);
    }
}
