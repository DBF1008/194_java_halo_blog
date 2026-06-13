package run.halo.app.model.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Overall result of a URL replacement scan or execution.
 *
 * @author halo-dev
 */
@Data
public class UrlReplaceResult {

    /**
     * Whether this was a dry-run (preview only, no data modified).
     */
    private boolean dryRun;

    /**
     * The old URL being searched for replacement.
     */
    private String oldUrl;

    /**
     * The new URL that will replace the old URL.
     */
    private String newUrl;

    /**
     * Per-module scan/execution details.
     */
    private List<UrlReplaceModuleDetail> modules = new ArrayList<>();

    /**
     * Total number of entities matched across all modules.
     */
    private int totalMatchedEntities;

    public UrlReplaceResult() {
    }

    public UrlReplaceResult(boolean dryRun, String oldUrl, String newUrl) {
        this.dryRun = dryRun;
        this.oldUrl = oldUrl;
        this.newUrl = newUrl;
    }

    /**
     * Compute totalMatchedEntities from the sum of per-module matched entities.
     */
    public void computeTotal() {
        this.totalMatchedEntities = modules.stream()
            .mapToInt(UrlReplaceModuleDetail::getMatchedEntities)
            .sum();
    }
}
