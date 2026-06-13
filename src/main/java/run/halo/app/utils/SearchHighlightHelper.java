package run.halo.app.utils;

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Utility for generating highlighted search snippets.
 *
 * <p>Provides HTML stripping, context-window snippet extraction, and keyword
 * highlighting with {@code <mark>} tags for unified search results.</p>
 *
 * @author halo
 */
public class SearchHighlightHelper {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final String MARK_OPEN = "<mark>";
    private static final String MARK_CLOSE = "</mark>";
    private static final String ELLIPSIS = "...";

    private SearchHighlightHelper() {
        // utility class
    }

    /**
     * Strips all HTML tags from the given string and normalizes whitespace.
     *
     * @param html the HTML string to strip
     * @return plain text with normalized whitespace, or empty string if input is blank
     */
    public static String stripHtml(String html) {
        if (StringUtils.isBlank(html)) {
            return StringUtils.EMPTY;
        }
        String stripped = HTML_TAG_PATTERN.matcher(html).replaceAll("");
        return MULTIPLE_SPACES.matcher(stripped).replaceAll(" ").trim();
    }

    /**
     * Generates a highlighted snippet from the content around the first occurrence
     * of the keyword.
     *
     * <p>The algorithm:
     * <ol>
     *   <li>Strip HTML from content</li>
     *   <li>Find the first case-insensitive occurrence of the keyword</li>
     *   <li>Extract {@code contextChars} characters before and after the match</li>
     *   <li>Wrap the keyword with {@code <mark>} tags</li>
     *   <li>Add {@code "..."} prefix/suffix if the snippet is truncated</li>
     * </ol>
     *
     * @param content the raw content (may contain HTML)
     * @param keyword the search keyword
     * @param contextChars number of characters to include before and after the match
     * @return highlighted snippet string, or empty string if keyword not found
     */
    public static String generateSnippet(String content, String keyword, int contextChars) {
        if (StringUtils.isBlank(content) || StringUtils.isBlank(keyword)) {
            return StringUtils.EMPTY;
        }

        String plainText = stripHtml(content);
        if (StringUtils.isBlank(plainText)) {
            return StringUtils.EMPTY;
        }

        String lowerText = plainText.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int keywordIndex = lowerText.indexOf(lowerKeyword);

        if (keywordIndex < 0) {
            return StringUtils.EMPTY;
        }

        // Calculate start and end positions
        int start = Math.max(0, keywordIndex - contextChars);
        int end = Math.min(plainText.length(), keywordIndex + keyword.length() + contextChars);

        // Build snippet parts
        StringBuilder snippet = new StringBuilder();

        // Prefix ellipsis if truncated
        if (start > 0) {
            snippet.append(ELLIPSIS);
        }

        // Text before keyword
        snippet.append(plainText, start, keywordIndex);

        // Highlighted keyword (preserve original casing)
        snippet.append(MARK_OPEN)
            .append(plainText, keywordIndex, keywordIndex + keyword.length())
            .append(MARK_CLOSE);

        // Text after keyword
        int afterKeywordStart = keywordIndex + keyword.length();
        if (afterKeywordStart < end) {
            snippet.append(plainText, afterKeywordStart, end);
        }

        // Suffix ellipsis if truncated
        if (end < plainText.length()) {
            snippet.append(ELLIPSIS);
        }

        return snippet.toString();
    }

    /**
     * Generates a highlighted snippet with a default context window of 80 characters.
     *
     * @param content the raw content (may contain HTML)
     * @param keyword the search keyword
     * @return highlighted snippet string
     */
    public static String generateSnippet(String content, String keyword) {
        return generateSnippet(content, keyword, 80);
    }

    /**
     * Highlights all occurrences of the keyword in the title with {@code <mark>} tags.
     *
     * <p>Matching is case-insensitive but preserves the original casing of the title text.</p>
     *
     * @param title the title string
     * @param keyword the search keyword
     * @return title with keyword occurrences wrapped in {@code <mark>} tags,
     *         or the original title if keyword is blank or not found
     */
    public static String highlightTitle(String title, String keyword) {
        if (StringUtils.isBlank(title) || StringUtils.isBlank(keyword)) {
            return StringUtils.defaultString(title);
        }

        String lowerTitle = title.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();

        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        int index;

        while ((index = lowerTitle.indexOf(lowerKeyword, lastEnd)) >= 0) {
            // Append text before the match
            result.append(title, lastEnd, index);
            // Append highlighted match (preserve original casing)
            result.append(MARK_OPEN)
                .append(title, index, index + keyword.length())
                .append(MARK_CLOSE);
            lastEnd = index + keyword.length();
        }

        // Append remaining text
        if (lastEnd < title.length()) {
            result.append(title, lastEnd, title.length());
        }

        return result.toString();
    }
}
