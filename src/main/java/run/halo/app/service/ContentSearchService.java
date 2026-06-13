package run.halo.app.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import run.halo.app.model.vo.PostListVO;
import run.halo.app.model.vo.SearchResultVO;

/**
 * Unified content search service.
 *
 * <p>Provides search across both Posts and Sheets with rich metadata
 * (highlighted snippets, matched fields, content type, access URL, scoring).
 * Only publicly visible (PUBLISHED) content is included in results.</p>
 *
 * @author halo
 */
public interface ContentSearchService {

    /**
     * Searches across posts and sheets, returning unified results with
     * highlighting, scoring, and metadata.
     *
     * @param keyword the search keyword (must not be blank)
     * @param pageable pagination and sorting information
     * @return a page of unified search results
     */
    Page<SearchResultVO> search(String keyword, Pageable pageable);

    /**
     * Searches across posts and sheets but returns results as PostListVO
     * for backward compatibility with existing theme search templates.
     *
     * <p>Only POST-type results are included; sheets are excluded from this
     * compatibility path to maintain the existing search.ftl contract.</p>
     *
     * @param keyword the search keyword (must not be blank)
     * @param pageable pagination and sorting information
     * @return a page of PostListVO results compatible with theme templates
     */
    Page<PostListVO> searchPostsCompat(String keyword, Pageable pageable);
}
