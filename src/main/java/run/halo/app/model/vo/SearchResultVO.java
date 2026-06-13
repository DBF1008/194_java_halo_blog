package run.halo.app.model.vo;

import java.util.Date;
import lombok.Data;
import run.halo.app.model.enums.ContentType;

/**
 * Unified search result value object.
 *
 * <p>Wraps search hits from both Posts and Sheets with rich metadata including
 * highlighted snippets, matched fields, content type, access URL, and scoring
 * information.</p>
 *
 * @author halo
 */
@Data
public class SearchResultVO {

    /**
     * Content id.
     */
    private Integer id;

    /**
     * Original title.
     */
    private String title;

    /**
     * Title with keyword highlighted using {@code <mark>} tags.
     */
    private String highlightedTitle;

    /**
     * Context snippet with keyword highlighted using {@code <mark>} tags.
     */
    private String highlightedSnippet;

    /**
     * Which field(s) matched the keyword: "title", "content", or "title,content".
     */
    private String matchedField;

    /**
     * Content type: POST or SHEET.
     */
    private ContentType contentType;

    /**
     * Full URL path to access this content.
     */
    private String fullPath;

    /**
     * Relevance score for sorting (higher = more relevant).
     */
    private Double score;

    /**
     * Human-readable explanation of why this result has its score.
     */
    private String sortReason;

    /**
     * Content creation time.
     */
    private Date createTime;

    /**
     * Content last edit time.
     */
    private Date editTime;

    /**
     * Visit count.
     */
    private Long visits;

    /**
     * Like count.
     */
    private Long likes;

    /**
     * Content summary.
     */
    private String summary;

    /**
     * Thumbnail URL.
     */
    private String thumbnail;
}
