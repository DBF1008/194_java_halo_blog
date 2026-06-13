package run.halo.app.service.impl;

import static run.halo.app.model.support.HaloConst.URL_SEPARATOR;

import cn.hutool.core.date.DateUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import run.halo.app.model.entity.BasePost;
import run.halo.app.model.entity.Post;
import run.halo.app.model.entity.Sheet;
import run.halo.app.model.enums.ContentType;
import run.halo.app.model.enums.PostPermalinkType;
import run.halo.app.model.enums.PostStatus;
import run.halo.app.model.enums.SheetPermalinkType;
import run.halo.app.model.vo.PostListVO;
import run.halo.app.model.vo.SearchResultVO;
import run.halo.app.repository.PostRepository;
import run.halo.app.repository.SheetRepository;
import run.halo.app.service.ContentSearchService;
import run.halo.app.service.OptionService;
import run.halo.app.utils.SearchHighlightHelper;

/**
 * Unified content search service implementation.
 *
 * <p>Searches across both Posts and Sheets using JPA LIKE queries, then merges,
 * scores, highlights, and paginates the combined result set.</p>
 *
 * <p>Scoring strategy:
 * <ul>
 *   <li>Title + Content match = 12.0</li>
 *   <li>Title-only match = 10.0</li>
 *   <li>Content-only match = 3.0</li>
 * </ul>
 *
 * <p>Only PUBLISHED content is included. DRAFT, RECYCLE, and INTIMATE content
 * is excluded by hardcoding the status filter in both JPA queries.</p>
 *
 * @author halo
 */
@Slf4j
@Service
public class ContentSearchServiceImpl implements ContentSearchService {

    /**
     * Maximum number of records to over-fetch from each repository.
     */
    private static final int MAX_OVER_FETCH = 500;

    /**
     * Score when keyword matches in both title and content.
     */
    private static final double SCORE_TITLE_AND_CONTENT = 12.0;

    /**
     * Score when keyword matches in title only.
     */
    private static final double SCORE_TITLE_ONLY = 10.0;

    /**
     * Score when keyword matches in content only.
     */
    private static final double SCORE_CONTENT_ONLY = 3.0;

    private static final int DEFAULT_SNIPPET_CONTEXT = 80;

    private final PostRepository postRepository;
    private final SheetRepository sheetRepository;
    private final OptionService optionService;

    public ContentSearchServiceImpl(PostRepository postRepository,
        SheetRepository sheetRepository,
        OptionService optionService) {
        this.postRepository = postRepository;
        this.sheetRepository = sheetRepository;
        this.optionService = optionService;
    }

    @Override
    public Page<SearchResultVO> search(String keyword, Pageable pageable) {
        Assert.notNull(keyword, "keyword must not be null");
        Assert.notNull(pageable, "Pageable must not be null");

        String trimmedKeyword = StringUtils.strip(keyword);
        if (StringUtils.isBlank(trimmedKeyword)) {
            return Page.empty(pageable);
        }

        // Calculate over-fetch limit: enough to cover the requested page
        int overFetchLimit = Math.min(
            (pageable.getPageNumber() + 1) * pageable.getPageSize(),
            MAX_OVER_FETCH
        );

        // Query both repositories
        List<Post> posts = postRepository.findAll(
            buildPostSpec(trimmedKeyword),
            org.springframework.data.domain.PageRequest.of(0, overFetchLimit)
        ).getContent();

        List<Sheet> sheets = sheetRepository.findAll(
            buildSheetSpec(trimmedKeyword),
            org.springframework.data.domain.PageRequest.of(0, overFetchLimit)
        ).getContent();

        // Convert to SearchResultVO with scoring
        List<SearchResultVO> allResults = new ArrayList<>();
        allResults.addAll(
            posts.stream().map(post -> toSearchResult(post, trimmedKeyword)).collect(
                Collectors.toList()));
        allResults.addAll(
            sheets.stream().map(sheet -> toSearchResult(sheet, trimmedKeyword)).collect(
                Collectors.toList()));

        // Sort by score DESC, then createTime DESC
        allResults.sort(Comparator
            .comparing(SearchResultVO::getScore, Comparator.reverseOrder())
            .thenComparing(SearchResultVO::getCreateTime, Comparator.reverseOrder()));

        // Paginate
        int total = allResults.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);

        List<SearchResultVO> pageContent;
        if (start >= total) {
            pageContent = new LinkedList<>();
        } else {
            pageContent = allResults.subList(start, end);
        }

        return new PageImpl<>(pageContent, pageable, total);
    }

    @Override
    public Page<PostListVO> searchPostsCompat(String keyword, Pageable pageable) {
        Page<SearchResultVO> searchResults = search(keyword, pageable);

        List<PostListVO> postListVOs = searchResults.getContent().stream()
            .filter(result -> result.getContentType() == ContentType.POST)
            .map(this::toPostListVO)
            .collect(Collectors.toList());

        // Count only POST-type results for the total
        // For backward compat, we need to recalculate without sheets
        // Re-query to get accurate total count for posts only
        if (StringUtils.isBlank(StringUtils.strip(keyword))) {
            return Page.empty(pageable);
        }

        long postTotal = postRepository.count(buildPostSpec(StringUtils.strip(keyword)));

        return new PageImpl<>(postListVOs, pageable, postTotal);
    }

    /**
     * Converts a Post to a SearchResultVO with scoring and highlighting.
     */
    private SearchResultVO toSearchResult(Post post, String keyword) {
        return buildResult(
            post.getId(),
            post.getTitle(),
            post.getOriginalContent(),
            keyword,
            ContentType.POST,
            buildPostFullPath(post),
            post.getCreateTime(),
            post.getEditTime(),
            post.getVisits(),
            post.getLikes(),
            post.getSummary(),
            post.getThumbnail()
        );
    }

    /**
     * Converts a Sheet to a SearchResultVO with scoring and highlighting.
     */
    private SearchResultVO toSearchResult(Sheet sheet, String keyword) {
        return buildResult(
            sheet.getId(),
            sheet.getTitle(),
            sheet.getOriginalContent(),
            keyword,
            ContentType.SHEET,
            buildSheetFullPath(sheet),
            sheet.getCreateTime(),
            sheet.getEditTime(),
            sheet.getVisits(),
            sheet.getLikes(),
            sheet.getSummary(),
            sheet.getThumbnail()
        );
    }

    /**
     * Builds a SearchResultVO with scoring, highlighting, and metadata.
     */
    private SearchResultVO buildResult(Integer id, String title, String originalContent,
        String keyword, ContentType contentType, String fullPath,
        java.util.Date createTime, java.util.Date editTime,
        Long visits, Long likes, String summary, String thumbnail) {

        boolean titleMatch = title != null
            && title.toLowerCase().contains(keyword.toLowerCase());
        boolean contentMatch = originalContent != null
            && originalContent.toLowerCase().contains(keyword.toLowerCase());

        // Compute score
        double score;
        String matchedField;
        String sortReason;

        if (titleMatch && contentMatch) {
            score = SCORE_TITLE_AND_CONTENT;
            matchedField = "title,content";
            sortReason = "Matched in title and content (score=" + SCORE_TITLE_AND_CONTENT + ")";
        } else if (titleMatch) {
            score = SCORE_TITLE_ONLY;
            matchedField = "title";
            sortReason = "Matched in title (score=" + SCORE_TITLE_ONLY + ")";
        } else {
            score = SCORE_CONTENT_ONLY;
            matchedField = "content";
            sortReason = "Matched in content (score=" + SCORE_CONTENT_ONLY + ")";
        }

        SearchResultVO result = new SearchResultVO();
        result.setId(id);
        result.setTitle(title);
        result.setHighlightedTitle(SearchHighlightHelper.highlightTitle(title, keyword));
        result.setHighlightedSnippet(
            SearchHighlightHelper.generateSnippet(originalContent, keyword,
                DEFAULT_SNIPPET_CONTEXT));
        result.setMatchedField(matchedField);
        result.setContentType(contentType);
        result.setFullPath(fullPath);
        result.setScore(score);
        result.setSortReason(sortReason);
        result.setCreateTime(createTime);
        result.setEditTime(editTime);
        result.setVisits(visits);
        result.setLikes(likes);
        result.setSummary(summary);
        result.setThumbnail(thumbnail);

        return result;
    }

    /**
     * Converts a SearchResultVO to PostListVO for backward compatibility with
     * theme search templates.
     */
    private PostListVO toPostListVO(SearchResultVO searchResult) {
        PostListVO vo = new PostListVO();
        vo.setId(searchResult.getId());
        vo.setTitle(searchResult.getTitle());
        vo.setFullPath(searchResult.getFullPath());
        vo.setCreateTime(searchResult.getCreateTime());
        vo.setEditTime(searchResult.getEditTime());
        vo.setVisits(searchResult.getVisits());
        vo.setLikes(searchResult.getLikes());
        vo.setSummary(searchResult.getSummary());
        vo.setThumbnail(searchResult.getThumbnail());
        return vo;
    }

    /**
     * Builds JPA Specification for searching published posts by keyword.
     */
    @NonNull
    private Specification<Post> buildPostSpec(@NonNull String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new LinkedList<>();

            // Only published content
            predicates.add(cb.equal(root.get("status"), PostStatus.PUBLISHED));

            // Keyword match on title OR originalContent
            String likeCondition = String.format("%%%s%%", StringUtils.strip(keyword));
            Predicate titleLike = cb.like(root.get("title"), likeCondition);
            Predicate contentLike = cb.like(root.get("originalContent"), likeCondition);
            predicates.add(cb.or(titleLike, contentLike));

            return query.where(predicates.toArray(new Predicate[0])).getRestriction();
        };
    }

    /**
     * Builds JPA Specification for searching published sheets by keyword.
     */
    @NonNull
    private Specification<Sheet> buildSheetSpec(@NonNull String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new LinkedList<>();

            // Only published content
            predicates.add(cb.equal(root.get("status"), PostStatus.PUBLISHED));

            // Keyword match on title OR originalContent
            String likeCondition = String.format("%%%s%%", StringUtils.strip(keyword));
            Predicate titleLike = cb.like(root.get("title"), likeCondition);
            Predicate contentLike = cb.like(root.get("originalContent"), likeCondition);
            predicates.add(cb.or(titleLike, contentLike));

            return query.where(predicates.toArray(new Predicate[0])).getRestriction();
        };
    }

    /**
     * Builds the full URL path for a post.
     *
     * <p>Replicates PostServiceImpl.buildFullPath(Post) logic.
     * TODO: Extract into a shared PermalinkService.</p>
     */
    private String buildPostFullPath(Post post) {
        PostPermalinkType permalinkType = optionService.getPostPermalinkType();
        String pathSuffix = optionService.getPathSuffix();
        String archivesPrefix = optionService.getArchivesPrefix();

        int month = DateUtil.month(post.getCreateTime()) + 1;
        String monthString = month < 10 ? "0" + month : String.valueOf(month);
        int day = DateUtil.dayOfMonth(post.getCreateTime());
        String dayString = day < 10 ? "0" + day : String.valueOf(day);

        StringBuilder fullPath = new StringBuilder();

        if (optionService.isEnabledAbsolutePath()) {
            fullPath.append(optionService.getBlogBaseUrl());
        }

        fullPath.append(URL_SEPARATOR);

        if (permalinkType.equals(PostPermalinkType.DEFAULT)) {
            fullPath.append(archivesPrefix)
                .append(URL_SEPARATOR)
                .append(post.getSlug())
                .append(pathSuffix);
        } else if (permalinkType.equals(PostPermalinkType.ID)) {
            fullPath.append("?p=")
                .append(post.getId());
        } else if (permalinkType.equals(PostPermalinkType.DATE)) {
            fullPath.append(DateUtil.year(post.getCreateTime()))
                .append(URL_SEPARATOR)
                .append(monthString)
                .append(URL_SEPARATOR)
                .append(post.getSlug())
                .append(pathSuffix);
        } else if (permalinkType.equals(PostPermalinkType.DAY)) {
            fullPath.append(DateUtil.year(post.getCreateTime()))
                .append(URL_SEPARATOR)
                .append(monthString)
                .append(URL_SEPARATOR)
                .append(dayString)
                .append(URL_SEPARATOR)
                .append(post.getSlug())
                .append(pathSuffix);
        } else if (permalinkType.equals(PostPermalinkType.YEAR)) {
            fullPath.append(DateUtil.year(post.getCreateTime()))
                .append(URL_SEPARATOR)
                .append(post.getSlug())
                .append(pathSuffix);
        } else if (permalinkType.equals(PostPermalinkType.ID_SLUG)) {
            fullPath.append(archivesPrefix)
                .append(URL_SEPARATOR)
                .append(post.getId())
                .append(pathSuffix);
        }

        return fullPath.toString();
    }

    /**
     * Builds the full URL path for a sheet.
     *
     * <p>Replicates SheetServiceImpl.buildFullPath(Sheet) logic.
     * TODO: Extract into a shared PermalinkService.</p>
     */
    private String buildSheetFullPath(Sheet sheet) {
        SheetPermalinkType permalinkType = optionService.getSheetPermalinkType();

        StringBuilder fullPath = new StringBuilder();

        if (optionService.isEnabledAbsolutePath()) {
            fullPath.append(optionService.getBlogBaseUrl());
        }

        if (permalinkType.equals(SheetPermalinkType.SECONDARY)) {
            fullPath.append(URL_SEPARATOR)
                .append(optionService.getSheetPrefix())
                .append(URL_SEPARATOR)
                .append(sheet.getSlug())
                .append(optionService.getPathSuffix());
        } else if (permalinkType.equals(SheetPermalinkType.ROOT)) {
            fullPath.append(URL_SEPARATOR)
                .append(sheet.getSlug())
                .append(optionService.getPathSuffix());
        }

        return fullPath.toString();
    }
}
