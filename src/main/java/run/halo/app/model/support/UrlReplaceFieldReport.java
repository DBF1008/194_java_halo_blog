package run.halo.app.model.support;

import lombok.Getter;
import run.halo.app.utils.UrlReplacer;

/**
 * Per-field statistics of a url replacement within a single module.
 *
 * @author halo
 */
@Getter
public class UrlReplaceFieldReport {

    private final String field;

    private int matchedItemCount;

    private long occurrenceCount;

    private long literalOccurrenceCount;

    private int conflictItemCount;

    public UrlReplaceFieldReport(String field) {
        this.field = field;
    }

    /**
     * Folds one field change into this report.
     *
     * @param change field change to accumulate
     */
    public void accumulate(UrlReplacer.FieldChange change) {
        if (change.getOccurrences() > 0) {
            matchedItemCount++;
            occurrenceCount += change.getOccurrences();
            literalOccurrenceCount += change.getLiteralOccurrences();
        }
        if (change.isNewUrlAlreadyPresent()) {
            conflictItemCount++;
        }
    }

    /**
     * The number of occurrences a literal match would have missed, i.e. potential collateral
     * replacements caused by treating the old url as a regex.
     *
     * @return non-negative count of extra occurrences matched by the regex
     */
    public long getRegexExtraOccurrenceCount() {
        return Math.max(occurrenceCount - literalOccurrenceCount, 0);
    }
}
