package run.halo.app.model.vo;

import java.util.Date;
import java.util.List;
import lombok.Data;
import run.halo.app.model.enums.SearchResultType;

/**
 * Unified search result vo.
 *
 * <p>Represents a single hit of the unified content search across posts and sheets. Besides the
 * content itself, it carries the matched fields, a highlighted excerpt, a relevance score and a
 * human readable ranking reason so that the theme layer, the admin or any API consumer can reuse
 * the same result without re-computing.</p>
 *
 * @author halo
 */
@Data
public class SearchResultVO {

    /**
     * Id of the matched content.
     */
    private Integer id;

    /**
     * Content type of the matched result, post or sheet.
     */
    private SearchResultType type;

    /**
     * Title of the matched content.
     */
    private String title;

    /**
     * Accessible front-end path of the matched content.
     */
    private String fullPath;

    /**
     * Highlighted excerpt. The matched keyword is wrapped with {@code <mark></mark>} and the
     * surrounding text is html escaped so the excerpt is safe to render directly.
     */
    private String excerpt;

    /**
     * Fields that the keyword hit, for example {@code title} and/or {@code content}.
     */
    private List<String> matchedFields;

    /**
     * Relevance score used for ranking. Higher means more relevant.
     */
    private Double score;

    /**
     * Human readable explanation of why this result is ranked at its position.
     */
    private String rankReason;

    /**
     * Create time of the matched content, used as the ranking tie-breaker.
     */
    private Date createTime;
}
