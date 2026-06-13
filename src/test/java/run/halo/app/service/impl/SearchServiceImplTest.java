package run.halo.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import run.halo.app.model.dto.post.BasePostMinimalDTO;
import run.halo.app.model.entity.Post;
import run.halo.app.model.entity.Sheet;
import run.halo.app.model.enums.PostStatus;
import run.halo.app.model.enums.SearchResultType;
import run.halo.app.model.vo.SearchResultVO;
import run.halo.app.service.PostService;
import run.halo.app.service.SheetService;

/**
 * Search service implementation test.
 *
 * @author halo
 */
class SearchServiceImplTest {

    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

    private PostService postService;

    private SheetService sheetService;

    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        postService = mock(PostService.class);
        sheetService = mock(SheetService.class);
        searchService = new SearchServiceImpl(postService, sheetService);
    }

    @Test
    void search_keywordHitsTitleAndContent_marksBothFields() {
        Post post = post(1, "Spring Boot Guide", "Learn Spring step by step", date(2021, 1, 1));
        stubPosts(Collections.singletonList(post),
            Collections.singletonList(minimal(1, "/archives/spring")));
        stubSheetsEmpty();

        Page<SearchResultVO> page = searchService.search("Spring", PAGEABLE);

        assertEquals(1, page.getTotalElements());
        SearchResultVO result = page.getContent().get(0);
        assertEquals(SearchResultType.POST, result.getType());
        assertTrue(result.getMatchedFields().contains("title"));
        assertTrue(result.getMatchedFields().contains("content"));
        assertEquals("标题与正文均包含关键词", result.getRankReason());
        assertTrue(result.getExcerpt().contains("<mark>"));
    }

    @Test
    void search_mixedRanking_titleHitOutranksContentOnlyHit() {
        Post titleHitPost =
            post(1, "Docker keyword tutorial", "no body match here", date(2020, 1, 1));
        Sheet contentHitSheet =
            sheet(2, "About page", "this mentions keyword in body", date(2024, 1, 1));
        stubPosts(Collections.singletonList(titleHitPost),
            Collections.singletonList(minimal(1, "/p1")));
        stubSheets(Collections.singletonList(contentHitSheet),
            Collections.singletonList(minimal(2, "/s2")));

        Page<SearchResultVO> page = searchService.search("keyword", PAGEABLE);

        List<SearchResultVO> content = page.getContent();
        assertEquals(2, content.size());
        // Title hit outranks content-only hit even though the sheet is newer.
        assertEquals(SearchResultType.POST, content.get(0).getType());
        assertEquals(Integer.valueOf(1), content.get(0).getId());
        assertEquals(SearchResultType.SHEET, content.get(1).getType());
        assertTrue(content.get(0).getScore() > content.get(1).getScore());
    }

    @Test
    void search_sameScore_recentContentRanksFirst() {
        Post older = post(1, "alpha", "has token in content", date(2020, 1, 1));
        Post newer = post(2, "beta", "also token here", date(2023, 1, 1));
        stubPosts(Arrays.asList(older, newer),
            Arrays.asList(minimal(1, "/a"), minimal(2, "/b")));
        stubSheetsEmpty();

        Page<SearchResultVO> page = searchService.search("token", PAGEABLE);

        List<SearchResultVO> content = page.getContent();
        assertEquals(2, content.size());
        // Equal score (content-only on both) -> the more recent content wins the tie-break.
        assertEquals(Integer.valueOf(2), content.get(0).getId());
        assertEquals(Integer.valueOf(1), content.get(1).getId());
    }

    @Test
    void search_excerptHighlightsAndEscapesHtml() {
        Sheet sheet =
            sheet(1, "Safe page", "intro term <script>alert(1)</script> & co more",
                date(2022, 1, 1));
        stubPostsEmpty();
        stubSheets(Collections.singletonList(sheet),
            Collections.singletonList(minimal(1, "/safe")));

        Page<SearchResultVO> page = searchService.search("term", PAGEABLE);

        String excerpt = page.getContent().get(0).getExcerpt();
        assertTrue(excerpt.contains("<mark>term</mark>"));
        assertTrue(excerpt.contains("&amp;"));
        assertFalse(excerpt.contains("<script>"));
    }

    @Test
    void search_onlyReturnsPublishedContent_viaPublishedOnlyMethods() {
        Post published = post(1, "Published doc", "keyword inside", date(2021, 1, 1));
        stubPosts(Collections.singletonList(published),
            Collections.singletonList(minimal(1, "/pub")));
        stubSheetsEmpty();

        Page<SearchResultVO> page = searchService.search("keyword", PAGEABLE);

        assertEquals(1, page.getTotalElements());
        assertEquals(Integer.valueOf(1), page.getContent().get(0).getId());
        // Visibility boundary: the service relies on the published-only keyword search and never on
        // a status-based listing that could surface drafts/intimate content.
        verify(postService).pageBy(eq("keyword"), any(Pageable.class));
        verify(sheetService).pageBy(eq("keyword"), any(Pageable.class));
        verify(postService, never()).pageBy(any(PostStatus.class), any(Pageable.class));
        verify(sheetService, never()).pageBy(any(PostStatus.class), any(Pageable.class));
    }

    @Test
    void search_blankKeyword_returnsEmptyWithoutQuerying() {
        Page<SearchResultVO> page = searchService.search("   ", PAGEABLE);

        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
        verify(postService, never()).pageBy(anyString(), any(Pageable.class));
        verify(sheetService, never()).pageBy(anyString(), any(Pageable.class));
    }

    private void stubPosts(List<Post> posts, List<BasePostMinimalDTO> minimals) {
        when(postService.pageBy(anyString(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(posts));
        when(postService.convertToMinimal(anyList())).thenReturn(minimals);
    }

    private void stubSheets(List<Sheet> sheets, List<BasePostMinimalDTO> minimals) {
        when(sheetService.pageBy(anyString(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(sheets));
        when(sheetService.convertToMinimal(anyList())).thenReturn(minimals);
    }

    private void stubPostsEmpty() {
        stubPosts(Collections.emptyList(), Collections.emptyList());
    }

    private void stubSheetsEmpty() {
        stubSheets(Collections.emptyList(), Collections.emptyList());
    }

    private Post post(int id, String title, String content, Date createTime) {
        Post post = new Post();
        post.setId(id);
        post.setTitle(title);
        post.setOriginalContent(content);
        post.setStatus(PostStatus.PUBLISHED);
        post.setCreateTime(createTime);
        return post;
    }

    private Sheet sheet(int id, String title, String content, Date createTime) {
        Sheet sheet = new Sheet();
        sheet.setId(id);
        sheet.setTitle(title);
        sheet.setOriginalContent(content);
        sheet.setStatus(PostStatus.PUBLISHED);
        sheet.setCreateTime(createTime);
        return sheet;
    }

    private BasePostMinimalDTO minimal(int id, String fullPath) {
        BasePostMinimalDTO minimalDto = new BasePostMinimalDTO();
        minimalDto.setId(id);
        minimalDto.setFullPath(fullPath);
        return minimalDto;
    }

    private Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
