package run.halo.app.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.util.HtmlUtils;
import run.halo.app.model.dto.post.BasePostMinimalDTO;
import run.halo.app.model.entity.Post;
import run.halo.app.model.entity.Sheet;
import run.halo.app.model.enums.SearchResultType;
import run.halo.app.model.vo.SearchResultVO;
import run.halo.app.service.PostService;
import run.halo.app.service.SearchService;
import run.halo.app.service.SheetService;
import run.halo.app.utils.HaloUtils;

/**
 * Unified content search service implementation.
 *
 * @author halo
 */
@Service
public class SearchServiceImpl implements SearchService {

    /**
     * Weight applied to every keyword occurrence found in the title.
     */
    private static final double TITLE_WEIGHT = 10.0;

    /**
     * Weight applied to every keyword occurrence found in the content.
     */
    private static final double CONTENT_WEIGHT = 1.0;

    /**
     * Number of characters kept on each side of the matched keyword in the excerpt.
     */
    private static final int EXCERPT_RADIUS = 60;

    private static final String FIELD_TITLE = "title";

    private static final String FIELD_CONTENT = "content";

    private final PostService postService;

    private final SheetService sheetService;

    public SearchServiceImpl(PostService postService, SheetService sheetService) {
        this.postService = postService;
        this.sheetService = sheetService;
    }

    @Override
    @NonNull
    public Page<SearchResultVO> search(@NonNull String keyword, @NonNull Pageable pageable) {
        Assert.notNull(keyword, "Keyword must not be null");
        Assert.notNull(pageable, "Page info must not be null");

        String strippedKeyword = StringUtils.strip(keyword);
        if (StringUtils.isBlank(strippedKeyword)) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }

        // Only published posts/sheets are returned. The visibility boundary (drafts, recycled and
        // password protected/intimate content) is enforced by the published-only keyword search
        // methods, so unauthorized content can never leak into the unified results.
        List<Post> posts = postService.pageBy(strippedKeyword, Pageable.unpaged()).getContent();
        List<Sheet> sheets = sheetService.pageBy(strippedKeyword, Pageable.unpaged()).getContent();

        List<SearchResultVO> results = new LinkedList<>();

        // Reuse the existing conversion so the permalink/full path logic is not duplicated.
        List<BasePostMinimalDTO> postMinimals = postService.convertToMinimal(posts);
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            results.add(buildResult(post.getId(), SearchResultType.POST, post.getTitle(),
                post.getOriginalContent(), post.getCreateTime(), postMinimals.get(i).getFullPath(),
                strippedKeyword));
        }

        List<BasePostMinimalDTO> sheetMinimals = sheetService.convertToMinimal(sheets);
        for (int i = 0; i < sheets.size(); i++) {
            Sheet sheet = sheets.get(i);
            results.add(buildResult(sheet.getId(), SearchResultType.SHEET, sheet.getTitle(),
                sheet.getOriginalContent(), sheet.getCreateTime(),
                sheetMinimals.get(i).getFullPath(), strippedKeyword));
        }

        // Rank: higher score first, then the more recent content first as the tie-breaker.
        results.sort(Comparator.comparingDouble(SearchResultVO::getScore).reversed()
            .thenComparing(SearchResultVO::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int total = results.size();
        List<SearchResultVO> content;
        if (pageable.isPaged()) {
            int from = (int) Math.min(pageable.getOffset(), total);
            int to = Math.min(from + pageable.getPageSize(), total);
            content = new ArrayList<>(results.subList(from, to));
        } else {
            content = new ArrayList<>(results);
        }

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Builds a single search result from a matched content.
     *
     * @param id matched content id
     * @param type matched content type
     * @param title matched content title
     * @param originalContent matched content original (un-rendered) content
     * @param createTime matched content create time
     * @param fullPath accessible front-end path of the matched content
     * @param keyword stripped keyword
     * @return a populated search result
     */
    private SearchResultVO buildResult(Integer id, SearchResultType type, String title,
        String originalContent, Date createTime, String fullPath, String keyword) {
        String safeTitle = title == null ? "" : title;
        String plainContent = normalizeWhitespace(HaloUtils.cleanHtmlTag(originalContent));
        String lowerKeyword = keyword.toLowerCase();

        boolean titleHit = safeTitle.toLowerCase().contains(lowerKeyword);
        boolean contentHit = plainContent.toLowerCase().contains(lowerKeyword);

        List<String> matchedFields = new LinkedList<>();
        if (titleHit) {
            matchedFields.add(FIELD_TITLE);
        }
        if (contentHit) {
            matchedFields.add(FIELD_CONTENT);
        }

        int titleCount = countOccurrences(safeTitle, lowerKeyword);
        int contentCount = countOccurrences(plainContent, lowerKeyword);
        double score = titleCount * TITLE_WEIGHT + contentCount * CONTENT_WEIGHT;

        String excerpt;
        if (contentHit) {
            excerpt = buildExcerpt(plainContent, keyword);
        } else if (titleHit) {
            excerpt = highlightAndEscape(safeTitle, keyword);
        } else {
            excerpt = preview(plainContent);
        }

        String rankReason;
        if (titleHit && contentHit) {
            rankReason = "标题与正文均包含关键词";
        } else if (titleHit) {
            rankReason = "标题包含关键词";
        } else {
            rankReason = "正文包含关键词";
        }

        SearchResultVO vo = new SearchResultVO();
        vo.setId(id);
        vo.setType(type);
        vo.setTitle(safeTitle);
        vo.setFullPath(fullPath);
        vo.setExcerpt(excerpt);
        vo.setMatchedFields(matchedFields);
        vo.setScore(score);
        vo.setRankReason(rankReason);
        vo.setCreateTime(createTime);
        return vo;
    }

    /**
     * Builds an html-safe excerpt windowed around the first keyword occurrence with the keyword
     * highlighted.
     *
     * @param text plain text to excerpt from
     * @param keyword stripped keyword
     * @return html-safe highlighted excerpt
     */
    private String buildExcerpt(String text, String keyword) {
        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int index = lowerText.indexOf(lowerKeyword);
        if (index < 0) {
            return preview(text);
        }

        int start = Math.max(0, index - EXCERPT_RADIUS);
        int end = Math.min(text.length(), index + keyword.length() + EXCERPT_RADIUS);
        String window = text.substring(start, end);

        StringBuilder excerpt = new StringBuilder();
        if (start > 0) {
            excerpt.append("…");
        }
        excerpt.append(highlightAndEscape(window, keyword));
        if (end < text.length()) {
            excerpt.append("…");
        }
        return excerpt.toString();
    }

    /**
     * Html-escapes the given text and wraps every (case-insensitive) keyword occurrence with a
     * {@code <mark></mark>} tag. The surrounding text is escaped so the result is safe to render
     * directly.
     *
     * @param text text to highlight
     * @param keyword stripped keyword
     * @return html-safe highlighted text
     */
    private String highlightAndEscape(String text, String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return HtmlUtils.htmlEscape(text);
        }

        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int keywordLength = keyword.length();

        StringBuilder builder = new StringBuilder();
        int from = 0;
        int index;
        while ((index = lowerText.indexOf(lowerKeyword, from)) >= 0) {
            builder.append(HtmlUtils.htmlEscape(text.substring(from, index)));
            builder.append("<mark>")
                .append(HtmlUtils.htmlEscape(text.substring(index, index + keywordLength)))
                .append("</mark>");
            from = index + keywordLength;
        }
        builder.append(HtmlUtils.htmlEscape(text.substring(from)));
        return builder.toString();
    }

    /**
     * Counts the non-overlapping, case-insensitive occurrences of the keyword in the text.
     *
     * @param text text to scan
     * @param lowerKeyword lower-cased keyword
     * @return number of occurrences
     */
    private int countOccurrences(String text, String lowerKeyword) {
        if (StringUtils.isEmpty(text) || StringUtils.isEmpty(lowerKeyword)) {
            return 0;
        }

        String lowerText = text.toLowerCase();
        int count = 0;
        int from = 0;
        int index;
        while ((index = lowerText.indexOf(lowerKeyword, from)) >= 0) {
            count++;
            from = index + lowerKeyword.length();
        }
        return count;
    }

    private String preview(String text) {
        return HtmlUtils.htmlEscape(StringUtils.substring(text, 0, EXCERPT_RADIUS * 2));
    }

    private String normalizeWhitespace(String text) {
        if (StringUtils.isEmpty(text)) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }
}
