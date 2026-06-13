package run.halo.app.model.enums;

/**
 * Modules that participate in the site-wide url replacement.
 *
 * @author halo
 */
public enum UrlReplaceModule {

    POST("Posts"),
    SHEET("Sheets"),
    POST_COMMENT("Post comments"),
    SHEET_COMMENT("Sheet comments"),
    JOURNAL_COMMENT("Journal comments"),
    ATTACHMENT("Attachments"),
    OPTION("Options"),
    PHOTO("Photos"),
    THEME_SETTING("Theme settings");

    private final String label;

    UrlReplaceModule(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
