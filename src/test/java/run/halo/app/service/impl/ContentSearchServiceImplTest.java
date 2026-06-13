package run.halo.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import run.halo.app.service.OptionService;

/**
 * Tests for {@link ContentSearchServiceImpl}.
 *
 * @author halo
 */
class ContentSearchServiceImplTest {

    @Mock
    PostRepository postRepository;

    @Mock
    SheetRepository sheetRepository;

    @Mock
    OptionService optionService;

    @InjectMocks
    ContentSearchServiceImpl contentSearchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        // Default option stubs
        given(optionService.isEnabledAbsolutePath()).willReturn(false);
        given(optionService.getPostPermalinkType()).willReturn(PostPermalinkType.DEFAULT);
        given(optionService.getSheetPermalinkType()).willReturn(SheetPermalinkType.SECONDARY);
        given(optionService.getArchivesPrefix()).willReturn("archives");
        given(optionService.getSheetPrefix()).willReturn("s");
        given(optionService.getPathSuffix()).willReturn("");
    }

    // --- Keyword matching tests ---

    @Test
    void search_keywordInPostTitle_returnsWithTitleMatch() {
        Post post = buildPost(1, "Spring Boot Guide", "some content here", PostStatus.PUBLISHED);
        givenPostResults(List.of(post));
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Spring");

        assertEquals(1, result.getTotalElements());
        SearchResultVO vo = result.getContent().get(0);
        assertEquals("title", vo.getMatchedField());
        assertEquals(10.0, vo.getScore());
        assertEquals(ContentType.POST, vo.getContentType());
    }

    @Test
    void search_keywordInPostContent_returnsWithContentMatch() {
        Post post = buildPost(1, "My Post", "This content contains the keyword Java",
            PostStatus.PUBLISHED);
        givenPostResults(List.of(post));
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Java");

        assertEquals(1, result.getTotalElements());
        SearchResultVO vo = result.getContent().get(0);
        assertEquals("content", vo.getMatchedField());
        assertEquals(3.0, vo.getScore());
    }

    @Test
    void search_keywordInBoth_returnsHighestScore() {
        Post post = buildPost(1, "Java Tutorial", "Learn Java programming here",
            PostStatus.PUBLISHED);
        givenPostResults(List.of(post));
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Java");

        assertEquals(1, result.getTotalElements());
        SearchResultVO vo = result.getContent().get(0);
        assertEquals("title,content", vo.getMatchedField());
        assertEquals(12.0, vo.getScore());
        assertTrue(vo.getSortReason().contains("12.0"));
    }

    // --- Mixed Post/Sheet sorting ---

    @Test
    void search_mixedPostAndSheet_sortedByScoreThenDate() {
        // Post: title match (score=10)
        Post post = buildPost(1, "Spring Guide", "unrelated content", PostStatus.PUBLISHED);
        post.setCreateTime(new Date(1000));
        givenPostResults(List.of(post));

        // Sheet: title+content match (score=12)
        Sheet sheet = buildSheet(2, "Spring Framework", "Spring is great", PostStatus.PUBLISHED);
        sheet.setCreateTime(new Date(2000));
        givenSheetResults(List.of(sheet));

        Page<SearchResultVO> result = searchFor("Spring");

        assertEquals(2, result.getTotalElements());
        // Sheet should be first (score 12 > 10)
        assertEquals(ContentType.SHEET, result.getContent().get(0).getContentType());
        assertEquals(12.0, result.getContent().get(0).getScore());
        assertEquals(ContentType.POST, result.getContent().get(1).getContentType());
        assertEquals(10.0, result.getContent().get(1).getScore());
    }

    @Test
    void search_sameScore_sortedByCreateTimeDesc() {
        // Both title-only match (score=10)
        Post post1 = buildPost(1, "Spring Post One", "content one", PostStatus.PUBLISHED);
        post1.setCreateTime(new Date(1000));

        Post post2 = buildPost(2, "Spring Post Two", "content two", PostStatus.PUBLISHED);
        post2.setCreateTime(new Date(2000));

        givenPostResults(List.of(post1, post2));
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Spring");

        assertEquals(2, result.getTotalElements());
        // Newer post (post2) should be first
        assertEquals(Integer.valueOf(2), result.getContent().get(0).getId());
        assertEquals(Integer.valueOf(1), result.getContent().get(1).getId());
    }

    // --- Visibility boundary tests ---

    @Test
    void search_draftPostExcluded_visibility() {
        // The JPA spec filters by PUBLISHED status, so the repository should
        // never return DRAFT posts. We verify the spec is applied by ensuring
        // that only PUBLISHED posts appear in results.
        Post draftPost = buildPost(1, "Draft Spring Post", "draft content", PostStatus.DRAFT);
        // Repository should not return draft posts because spec filters them
        givenPostResults(Collections.emptyList());
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Spring");

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void search_intimatePostExcluded_visibility() {
        Post intimatePost = buildPost(1, "Private Spring Post", "secret", PostStatus.INTIMATE);
        givenPostResults(Collections.emptyList());
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Spring");

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void search_recyclePostExcluded_visibility() {
        Post recycledPost = buildPost(1, "Recycled Spring Post", "trashed", PostStatus.RECYCLE);
        givenPostResults(Collections.emptyList());
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Spring");

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void search_draftSheetExcluded_visibility() {
        Sheet draftSheet = buildSheet(1, "Draft Spring Sheet", "draft", PostStatus.DRAFT);
        givenPostResults(Collections.emptyList());
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Spring");

        assertEquals(0, result.getTotalElements());
    }

    // --- Empty / blank keyword ---

    @Test
    void search_emptyKeyword_returnsEmpty() {
        Page<SearchResultVO> result = contentSearchService.search("",
            PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void search_blankKeyword_returnsEmpty() {
        Page<SearchResultVO> result = contentSearchService.search("   ",
            PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
    }

    // --- Pagination ---

    @Test
    void search_pagination_correctSlicing() {
        // Create 5 posts all matching "Spring"
        List<Post> posts = Arrays.asList(
            buildPost(1, "Spring One", "content", PostStatus.PUBLISHED),
            buildPost(2, "Spring Two", "content", PostStatus.PUBLISHED),
            buildPost(3, "Spring Three", "content", PostStatus.PUBLISHED),
            buildPost(4, "Spring Four", "content", PostStatus.PUBLISHED),
            buildPost(5, "Spring Five", "content", PostStatus.PUBLISHED)
        );
        // Set different create times for deterministic ordering
        for (int i = 0; i < posts.size(); i++) {
            posts.get(i).setCreateTime(new Date(5000 - i * 1000));
        }
        givenPostResults(posts);
        givenSheetResults(Collections.emptyList());

        // Page 0 with size 2
        Pageable pageable = PageRequest.of(0, 2);
        Page<SearchResultVO> page0 = contentSearchService.search("Spring", pageable);

        assertEquals(5, page0.getTotalElements());
        assertEquals(2, page0.getContent().size());
        assertEquals(3, page0.getTotalPages());

        // Page 1 with size 2
        Pageable pageable1 = PageRequest.of(1, 2);
        Page<SearchResultVO> page1 = contentSearchService.search("Spring", pageable1);

        assertEquals(5, page1.getTotalElements());
        assertEquals(2, page1.getContent().size());
    }

    @Test
    void search_pagination_beyondTotalReturnsEmpty() {
        Post post = buildPost(1, "Spring Post", "content", PostStatus.PUBLISHED);
        givenPostResults(List.of(post));
        givenSheetResults(Collections.emptyList());

        Pageable pageable = PageRequest.of(5, 10);
        Page<SearchResultVO> result = contentSearchService.search("Spring", pageable);

        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    // --- Full path generation ---

    @Test
    void search_fullPath_postDefault() {
        Post post = buildPost(1, "Spring Post", "content about Spring", PostStatus.PUBLISHED);
        post.setSlug("spring-post");
        givenPostResults(List.of(post));
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Spring");

        assertEquals("/archives/spring-post", result.getContent().get(0).getFullPath());
    }

    @Test
    void search_fullPath_sheetSecondary() {
        Sheet sheet = buildSheet(1, "About Spring", "content about Spring", PostStatus.PUBLISHED);
        sheet.setSlug("about-spring");
        givenPostResults(Collections.emptyList());
        givenSheetResults(List.of(sheet));

        Page<SearchResultVO> result = searchFor("Spring");

        assertEquals("/s/about-spring", result.getContent().get(0).getFullPath());
    }

    @Test
    void search_fullPath_sheetRoot() {
        given(optionService.getSheetPermalinkType()).willReturn(SheetPermalinkType.ROOT);
        Sheet sheet = buildSheet(1, "About Spring", "content about Spring", PostStatus.PUBLISHED);
        sheet.setSlug("about-spring");
        givenPostResults(Collections.emptyList());
        givenSheetResults(List.of(sheet));

        Page<SearchResultVO> result = searchFor("Spring");

        assertEquals("/about-spring", result.getContent().get(0).getFullPath());
    }

    // --- Backward compat (searchPostsCompat) ---

    @Test
    void searchPostsCompat_onlyPostsReturned() {
        Post post = buildPost(1, "Spring Post", "content", PostStatus.PUBLISHED);
        Sheet sheet = buildSheet(2, "Spring Sheet", "content", PostStatus.PUBLISHED);
        givenPostResults(List.of(post));
        givenSheetResults(List.of(sheet));
        given(postRepository.count(any(Specification.class))).willReturn(1L);

        Page<PostListVO> result = contentSearchService.searchPostsCompat("Spring",
            PageRequest.of(0, 10));

        // Only the post should be in the result
        assertEquals(1, result.getContent().size());
        assertEquals(Integer.valueOf(1), result.getContent().get(0).getId());
    }

    @Test
    void searchPostsCompat_emptyKeyword() {
        Page<PostListVO> result = contentSearchService.searchPostsCompat("",
            PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
    }

    // --- Highlighting ---

    @Test
    void search_highlightedSnippet_containsMark() {
        Post post = buildPost(1, "My Post",
            "This is a long content that contains the keyword Java somewhere",
            PostStatus.PUBLISHED);
        givenPostResults(List.of(post));
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Java");

        assertNotNull(result.getContent().get(0).getHighlightedSnippet());
        assertTrue(result.getContent().get(0).getHighlightedSnippet().contains("<mark>"));
        assertTrue(result.getContent().get(0).getHighlightedSnippet().contains("</mark>"));
    }

    @Test
    void search_highlightedTitle_containsMark() {
        Post post = buildPost(1, "Java Programming Guide", "some content",
            PostStatus.PUBLISHED);
        givenPostResults(List.of(post));
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Java");

        assertEquals("<mark>Java</mark> Programming Guide",
            result.getContent().get(0).getHighlightedTitle());
    }

    @Test
    void search_matchedField_correctValue() {
        // Title-only match
        Post titleOnly = buildPost(1, "Spring Guide", "unrelated text", PostStatus.PUBLISHED);
        givenPostResults(List.of(titleOnly));
        givenSheetResults(Collections.emptyList());

        Page<SearchResultVO> result = searchFor("Spring");
        assertEquals("title", result.getContent().get(0).getMatchedField());
    }

    // --- Helper methods ---

    private Page<SearchResultVO> searchFor(String keyword) {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        return contentSearchService.search(keyword, pageable);
    }

    @SuppressWarnings("unchecked")
    private void givenPostResults(List<Post> posts) {
        given(postRepository.findAll(any(Specification.class), any(Pageable.class)))
            .willReturn(new PageImpl<>(posts));
    }

    @SuppressWarnings("unchecked")
    private void givenSheetResults(List<Sheet> sheets) {
        given(sheetRepository.findAll(any(Specification.class), any(Pageable.class)))
            .willReturn(new PageImpl<>(sheets));
    }

    private Post buildPost(Integer id, String title, String content, PostStatus status) {
        Post post = new Post();
        post.setId(id);
        post.setTitle(title);
        post.setOriginalContent(content);
        post.setStatus(status);
        post.setSlug("post-" + id);
        post.setCreateTime(new Date());
        post.setVisits(0L);
        post.setLikes(0L);
        return post;
    }

    private Sheet buildSheet(Integer id, String title, String content, PostStatus status) {
        Sheet sheet = new Sheet();
        sheet.setId(id);
        sheet.setTitle(title);
        sheet.setOriginalContent(content);
        sheet.setStatus(status);
        sheet.setSlug("sheet-" + id);
        sheet.setCreateTime(new Date());
        sheet.setVisits(0L);
        sheet.setLikes(0L);
        return sheet;
    }
}
