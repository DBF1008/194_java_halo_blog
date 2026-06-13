package run.halo.app.controller.content.api;

import static org.springframework.data.domain.Sort.Direction.DESC;

import io.swagger.annotations.ApiOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import run.halo.app.model.vo.SearchResultVO;
import run.halo.app.service.SearchService;

/**
 * Content unified search controller.
 *
 * @author halo
 */
@RestController("ApiContentSearchController")
@RequestMapping("/api/content/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Searches published posts and sheets by keyword.
     *
     * @param keyword keyword to search for
     * @param pageable page info
     * @return a page of ranked search results
     */
    @GetMapping
    @ApiOperation("Searches posts and sheets by keyword")
    public Page<SearchResultVO> search(
        @RequestParam(value = "keyword") String keyword,
        @PageableDefault(sort = "createTime", direction = DESC) Pageable pageable) {
        return searchService.search(keyword, pageable);
    }
}
