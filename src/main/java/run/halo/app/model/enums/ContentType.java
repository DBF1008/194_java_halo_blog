package run.halo.app.model.enums;

/**
 * Content type for unified search results.
 *
 * @author halo
 */
public enum ContentType implements ValueEnum<Integer> {

    /**
     * Blog post.
     */
    POST(0),

    /**
     * Independent page (sheet).
     */
    SHEET(1);

    private final Integer value;

    ContentType(Integer value) {
        this.value = value;
    }

    @Override
    public Integer getValue() {
        return value;
    }
}
