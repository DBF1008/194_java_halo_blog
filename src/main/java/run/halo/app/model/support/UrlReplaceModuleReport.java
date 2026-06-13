package run.halo.app.model.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.halo.app.model.enums.UrlReplaceModule;
import run.halo.app.utils.UrlReplacer;

/**
 * Statistics of a url replacement within a single module.
 *
 * @author halo
 */
public class UrlReplaceModuleReport {

    private final UrlReplaceModule module;

    private final String moduleName;

    private boolean applied;

    private int scannedItemCount;

    private int matchedItemCount;

    @JsonIgnore
    private final Map<String, UrlReplaceFieldReport> fieldMap = new LinkedHashMap<>();

    public UrlReplaceModuleReport(UrlReplaceModule module) {
        this.module = module;
        this.moduleName = module.getLabel();
    }

    /**
     * Replaces the url inside a single field value and folds the result into this report. The
     * caller decides whether to persist the new value (apply) or discard it (dry-run).
     *
     * @param field field name
     * @param value original field value
     * @param oldUrl url fragment to look for
     * @param newUrl replacement value
     * @param regex whether {@code oldUrl} is treated as a regex
     * @return the computed field change
     */
    public UrlReplacer.FieldChange apply(String field, String value, String oldUrl,
        String newUrl, boolean regex) {
        UrlReplacer.FieldChange change = UrlReplacer.replace(value, oldUrl, newUrl, regex);
        fieldMap.computeIfAbsent(field, UrlReplaceFieldReport::new).accumulate(change);
        return change;
    }

    public void incScannedItemCount() {
        scannedItemCount++;
    }

    public void markItemChanged() {
        matchedItemCount++;
    }

    public UrlReplaceModule getModule() {
        return module;
    }

    public String getModuleName() {
        return moduleName;
    }

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    public int getScannedItemCount() {
        return scannedItemCount;
    }

    public int getMatchedItemCount() {
        return matchedItemCount;
    }

    public List<UrlReplaceFieldReport> getFields() {
        return new ArrayList<>(fieldMap.values());
    }

    public long getOccurrenceCount() {
        long total = 0;
        for (UrlReplaceFieldReport fieldReport : fieldMap.values()) {
            total += fieldReport.getOccurrenceCount();
        }
        return total;
    }

    /**
     * Whether any field reported a potential conflict (the new url already present) or a potential
     * collateral replacement (regex matched more than a literal would).
     *
     * @return true if this module has at least one conflict signal
     */
    public boolean isConflict() {
        for (UrlReplaceFieldReport fieldReport : fieldMap.values()) {
            if (fieldReport.getConflictItemCount() > 0
                || fieldReport.getRegexExtraOccurrenceCount() > 0) {
                return true;
            }
        }
        return false;
    }
}
