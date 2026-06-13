package run.halo.app.model.enums;

/**
 * Modules that support URL replacement.
 *
 * @author halo-dev
 */
public enum ReplaceableModule {

    POSTS("文章", new String[] {"thumbnail", "originalContent", "formatContent"}),

    SHEETS("页面", new String[] {"thumbnail", "originalContent", "formatContent"}),

    POST_COMMENTS("文章评论", new String[] {"authorUrl"}),

    SHEET_COMMENTS("页面评论", new String[] {"authorUrl"}),

    JOURNAL_COMMENTS("日志评论", new String[] {"authorUrl"}),

    ATTACHMENTS("附件", new String[] {"path", "thumbPath"}),

    OPTIONS("设置", new String[] {"value"}),

    PHOTOS("相册", new String[] {"thumbnail", "url"}),

    THEME_SETTINGS("主题设置", new String[] {"value"});

    private final String displayName;

    private final String[] fields;

    ReplaceableModule(String displayName, String[] fields) {
        this.displayName = displayName;
        this.fields = fields;
    }

    /**
     * Get the display name of this module.
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the fields that will be scanned for URL replacement.
     *
     * @return field names
     */
    public String[] getFields() {
        return fields;
    }
}
