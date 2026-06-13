package run.halo.app.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import run.halo.app.model.vo.SearchResultVO;

/**
 * Unified content search service.
 *
 * <p>Searches across published posts and sheets at once and returns ranked
 * {@link SearchResultVO} hits that carry a highlighted excerpt, the matched fields, the content
 * type, the accessible front-end path and a human readable ranking reason. It is meant to be reused
 * by the theme search page, the admin and any API consumer.</p>
 *
 * @author halo
 */
public interface SearchService {

    /**
     * Searches published posts and sheets by keyword.
     *
     * <p>Only published content is returned, so drafts, recycled and password protected (intimate)
     * content are never exposed. Results are ranked by relevance and the create time is used as the
     * tie-breaker. The given {@link Pageable} drives the returned slice while the ranking order is
     * computed across the whole matched result set.</p>
     *
     * @param keyword keyword to search for, must not be null
     * @param pageable page info, must not be null
     * @return a page of ranked search results
     */
    @NonNull
    Page<SearchResultVO> search(@NonNull String keyword, @NonNull Pageable pageable);
}
