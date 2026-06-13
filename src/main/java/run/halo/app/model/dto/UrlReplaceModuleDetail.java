package run.halo.app.model.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import run.halo.app.model.enums.ReplaceableModule;

/**
 * URL replacement module-level detail.
 *
 * @author halo-dev
 */
@Data
public class UrlReplaceModuleDetail {

    /**
     * The module identifier.
     */
    private ReplaceableModule module;

    /**
     * Human-readable module name.
     */
    private String displayName;

    /**
     * Total number of entities in this module.
     */
    private int totalEntities;

    /**
     * Number of entities where at least one field matched the old URL.
     */
    private int matchedEntities;

    /**
     * Per-field match details.
     */
    private List<UrlReplaceFieldDetail> fields = new ArrayList<>();

    /**
     * Warning messages (e.g. new URL already present in data).
     */
    private List<String> warnings = new ArrayList<>();

    public UrlReplaceModuleDetail() {
    }

    public UrlReplaceModuleDetail(ReplaceableModule module) {
        this.module = module;
        this.displayName = module.getDisplayName();
    }
}
