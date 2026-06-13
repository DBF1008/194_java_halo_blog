package run.halo.app.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * Performs a single url replacement on a string value and reports how many occurrences were
 * (or would be) changed.
 *
 * <p>This is the single source of truth shared by the dry-run preview and the real apply, so that
 * both report identical statistics.</p>
 *
 * @author halo
 */
public final class UrlReplacer {

    private UrlReplacer() {
    }

    /**
     * Computes the replacement of {@code oldUrl} with {@code newUrl} inside {@code value}.
     *
     * @param value original field value, may be empty
     * @param oldUrl url fragment to look for, treated as a regex when {@code regex} is true
     * @param newUrl replacement value
     * @param regex whether {@code oldUrl} is treated as a regular expression
     * @return a field change describing the new value and occurrence counts
     */
    public static FieldChange replace(String value, String oldUrl, String newUrl, boolean regex) {
        if (StringUtils.isEmpty(value) || StringUtils.isEmpty(oldUrl)) {
            return new FieldChange(value, 0, 0, false);
        }
        boolean newUrlPresent = StringUtils.isNotEmpty(newUrl) && value.contains(newUrl);
        int literalOccurrences = StringUtils.countMatches(value, oldUrl);
        if (regex) {
            int occurrences = countRegexMatches(value, oldUrl);
            String newValue = value.replaceAll(oldUrl, newUrl);
            return new FieldChange(newValue, occurrences, literalOccurrences, newUrlPresent);
        }
        String newValue = value.replace(oldUrl, newUrl);
        return new FieldChange(newValue, literalOccurrences, literalOccurrences, newUrlPresent);
    }

    /**
     * Counts non-overlapping regex matches the same way {@link String#replaceAll} would replace
     * them, advancing past zero-width matches to avoid an infinite loop.
     */
    private static int countRegexMatches(String value, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(value);
        int count = 0;
        int from = 0;
        while (from <= value.length() && matcher.find(from)) {
            count++;
            from = matcher.end();
            if (matcher.end() == matcher.start()) {
                from++;
            }
        }
        return count;
    }

    /**
     * Result of a single field replacement.
     */
    @Getter
    public static final class FieldChange {

        private final String newValue;

        private final int occurrences;

        private final int literalOccurrences;

        private final boolean newUrlAlreadyPresent;

        FieldChange(String newValue, int occurrences, int literalOccurrences,
            boolean newUrlAlreadyPresent) {
            this.newValue = newValue;
            this.occurrences = occurrences;
            this.literalOccurrences = literalOccurrences;
            this.newUrlAlreadyPresent = newUrlAlreadyPresent;
        }

        public boolean isChanged() {
            return occurrences > 0;
        }
    }
}
