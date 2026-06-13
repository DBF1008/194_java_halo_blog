package run.halo.app.model.dto;

import lombok.Data;

/**
 * URL replacement field-level detail.
 *
 * @author halo-dev
 */
@Data
public class UrlReplaceFieldDetail {

    /**
     * Field name (e.g. "originalContent").
     */
    private String fieldName;

    /**
     * Number of entities where this field contains the old URL.
     */
    private int matchedEntityCount;

    /**
     * Total occurrences of the old URL across all entities in this field.
     */
    private int totalOccurrences;

    public UrlReplaceFieldDetail() {
    }

    public UrlReplaceFieldDetail(String fieldName, int matchedEntityCount, int totalOccurrences) {
        this.fieldName = fieldName;
        this.matchedEntityCount = matchedEntityCount;
        this.totalOccurrences = totalOccurrences;
    }
}
