package run.halo.app.model.dto;

import java.util.Map;
import lombok.Data;

/**
 * JSON data export preview detail.
 *
 * @author ryanwang
 */
@Data
public class JsonDataPreviewDetail {

    /**
     * Halo version recorded in the export.
     */
    private String version;

    /**
     * Export date string as stored in the JSON.
     */
    private String exportDate;

    /**
     * Map of entity type key to count of entities (e.g., "posts" -> 42).
     */
    private Map<String, Integer> entityCounts;

    /**
     * Total entity count across all types.
     */
    private Integer totalEntityCount;
}
