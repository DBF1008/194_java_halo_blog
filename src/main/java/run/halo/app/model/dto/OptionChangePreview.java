package run.halo.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single option change preview for import conflict analysis.
 *
 * @author ryanwang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OptionChangePreview {

    /**
     * Option key.
     */
    private String key;

    /**
     * Current value in the database (null if not set).
     */
    private String currentValue;

    /**
     * Incoming value from the import file.
     */
    private String incomingValue;
}
