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
import org.springframework.web.util.HtmlUtils;
import run.halo.app.model.vo.SearchResultVO;
import run.halo.app.service.ContentSearchService;

/**
 * Unified content search API controller.
 *
 * <p>Provides a REST endpoint for searching across both Posts and Sheets,
 * returning rich search results with highlighting, scoring, and metadata.</p>
 *
 * @author halo
 */
@RestController("ApiContentSearchController")
@RequestMapping("/api/content/search")
public class SearchController {

    private final ContentSearchService contentSearchService;

    public SearchController(ContentSearchService contentSearchService) {
        this.contentSearchService = contentSearchService;
    }

    @GetMapping
    @ApiOperation("Searches posts and sheets by keyword")
    public Page<SearchResultVO> search(
        @RequestParam(value = "keyword") String keyword,
        @PageableDefault(sort = "createTime", direction = DESC) Pageable pageable) {
        return contentSearchService.search(HtmlUtils.htmlEscape(keyword), pageable);
    }
}
