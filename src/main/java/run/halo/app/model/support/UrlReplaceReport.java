package run.halo.app.model.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate statistics of a site-wide url replacement across all selected modules.
 *
 * @author halo
 */
public class UrlReplaceReport {

    private final String oldUrl;

    private final String newUrl;

    private final boolean dryRun;

    private final List<UrlReplaceModuleReport> modules = new ArrayList<>();

    public UrlReplaceReport(String oldUrl, String newUrl, boolean dryRun) {
        this.oldUrl = oldUrl;
        this.newUrl = newUrl;
        this.dryRun = dryRun;
    }

    /**
     * Adds a module report to this aggregate.
     *
     * @param moduleReport module report to add
     */
    public void addModule(UrlReplaceModuleReport moduleReport) {
        modules.add(moduleReport);
    }

    public String getOldUrl() {
        return oldUrl;
    }

    public String getNewUrl() {
        return newUrl;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public List<UrlReplaceModuleReport> getModules() {
        return modules;
    }

    public long getTotalOccurrenceCount() {
        long total = 0;
        for (UrlReplaceModuleReport moduleReport : modules) {
            total += moduleReport.getOccurrenceCount();
        }
        return total;
    }

    public int getTotalMatchedItemCount() {
        int total = 0;
        for (UrlReplaceModuleReport moduleReport : modules) {
            total += moduleReport.getMatchedItemCount();
        }
        return total;
    }
}
