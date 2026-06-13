package run.halo.app.model.dto;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Data import conflict analysis DTO.
 *
 * @author ryanwang
 */
@Data
public class DataImportConflictDTO {

    /**
     * Entity counts from the import file.
     */
    private Map<String, Integer> incomingEntityCounts;

    /**
     * Map of entity type to list of conflicting IDs (PKs that exist in both
     * DB and file).
     */
    private Map<String, List<Object>> conflictingIds;

    /**
     * Map of entity type to list of conflicting slug values.
     */
    private Map<String, List<String>> conflictingSlugs;

    /**
     * Key options that would change after import.
     */
    private List<OptionChangePreview> optionChanges;

    /**
     * Total number of conflicting entities across all types.
     */
    private Integer totalConflicts;
}
