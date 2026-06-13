package run.halo.app.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SearchHighlightHelper}.
 *
 * @author halo
 */
class SearchHighlightHelperTest {

    @Test
    void stripHtml_removesTags() {
        String html = "<p>Hello <strong>world</strong></p>";
        assertEquals("Hello world", SearchHighlightHelper.stripHtml(html));
    }

    @Test
    void stripHtml_normalizesWhitespace() {
        String html = "<p>Hello   <br/>   world</p>";
        assertEquals("Hello world", SearchHighlightHelper.stripHtml(html));
    }

    @Test
    void stripHtml_blankInput() {
        assertEquals("", SearchHighlightHelper.stripHtml(null));
        assertEquals("", SearchHighlightHelper.stripHtml(""));
        assertEquals("", SearchHighlightHelper.stripHtml("   "));
    }

    @Test
    void generateSnippet_keywordInMiddle() {
        // 200 chars of padding around the keyword
        String before = "a".repeat(100);
        String after = "b".repeat(100);
        String content = before + "keyword" + after;

        String snippet = SearchHighlightHelper.generateSnippet(content, "keyword", 20);

        assertTrue(snippet.contains("<mark>keyword</mark>"));
        assertTrue(snippet.startsWith("..."));
        assertTrue(snippet.endsWith("..."));
        // 20 chars before + 7 keyword + 20 chars after = 47 + marks + ellipsis
        assertTrue(snippet.length() < content.length());
    }

    @Test
    void generateSnippet_keywordAtStart() {
        String content = "keyword is at the beginning of this text";

        String snippet = SearchHighlightHelper.generateSnippet(content, "keyword", 20);

        assertTrue(snippet.contains("<mark>keyword</mark>"));
        assertFalse(snippet.startsWith("..."));
    }

    @Test
    void generateSnippet_keywordAtEnd() {
        String content = "this text has the keyword at its very end";

        String snippet = SearchHighlightHelper.generateSnippet(content, "keyword", 20);

        assertTrue(snippet.contains("<mark>keyword</mark>"));
        assertFalse(snippet.endsWith("..."));
    }

    @Test
    void generateSnippet_caseInsensitive() {
        String content = "This Text Contains KEYWORD In Uppercase";

        String snippet = SearchHighlightHelper.generateSnippet(content, "keyword", 10);

        assertTrue(snippet.contains("<mark>KEYWORD</mark>"), "should preserve original casing");
    }

    @Test
    void generateSnippet_noMatch() {
        String content = "This is some content without the search term";

        String snippet = SearchHighlightHelper.generateSnippet(content, "missing", 20);

        assertEquals("", snippet);
    }

    @Test
    void generateSnippet_htmlContent() {
        String html = "<p>This is a <strong>paragraph</strong> with some keyword in it.</p>";

        String snippet = SearchHighlightHelper.generateSnippet(html, "keyword", 10);

        assertTrue(snippet.contains("<mark>keyword</mark>"));
        assertFalse(snippet.contains("<p>"));
        assertFalse(snippet.contains("<strong>"));
    }

    @Test
    void generateSnippet_longContent() {
        String content = "x".repeat(500) + "keyword" + "y".repeat(500);

        String snippet = SearchHighlightHelper.generateSnippet(content, "keyword", 80);

        assertTrue(snippet.contains("<mark>keyword</mark>"));
        assertTrue(snippet.startsWith("..."));
        assertTrue(snippet.endsWith("..."));
        // Total should be: 3 (ellipsis) + 80 + 7 + 80 + 3 (ellipsis) + mark tags
        assertTrue(snippet.length() < 300);
    }

    @Test
    void generateSnippet_blankInputs() {
        assertEquals("", SearchHighlightHelper.generateSnippet(null, "kw", 20));
        assertEquals("", SearchHighlightHelper.generateSnippet("content", null, 20));
        assertEquals("", SearchHighlightHelper.generateSnippet("", "kw", 20));
        assertEquals("", SearchHighlightHelper.generateSnippet("content", "", 20));
    }

    @Test
    void generateSnippet_defaultContext() {
        String before = "a".repeat(100);
        String after = "b".repeat(100);
        String content = before + "keyword" + after;

        String snippet = SearchHighlightHelper.generateSnippet(content, "keyword");

        assertTrue(snippet.contains("<mark>keyword</mark>"));
    }

    @Test
    void highlightTitle_singleOccurrence() {
        String title = "Hello World Post";

        String result = SearchHighlightHelper.highlightTitle(title, "World");

        assertEquals("Hello <mark>World</mark> Post", result);
    }

    @Test
    void highlightTitle_multipleOccurrences() {
        String title = "Java and Java Programming";

        String result = SearchHighlightHelper.highlightTitle(title, "Java");

        assertEquals("<mark>Java</mark> and <mark>Java</mark> Programming", result);
    }

    @Test
    void highlightTitle_caseInsensitivePreservesCase() {
        String title = "Spring Boot Framework";

        String result = SearchHighlightHelper.highlightTitle(title, "spring");

        assertEquals("<mark>Spring</mark> Boot Framework", result);
    }

    @Test
    void highlightTitle_noMatch() {
        String title = "Hello World";

        String result = SearchHighlightHelper.highlightTitle(title, "missing");

        assertEquals("Hello World", result);
    }

    @Test
    void highlightTitle_blankInputs() {
        assertEquals("", SearchHighlightHelper.highlightTitle(null, "kw"));
        assertEquals("", SearchHighlightHelper.highlightTitle("", "kw"));
        assertEquals("Hello", SearchHighlightHelper.highlightTitle("Hello", null));
        assertEquals("Hello", SearchHighlightHelper.highlightTitle("Hello", ""));
    }
}
