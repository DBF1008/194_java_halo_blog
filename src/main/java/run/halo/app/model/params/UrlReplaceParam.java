package run.halo.app.model.params;

import java.util.Collections;
import java.util.Set;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import run.halo.app.model.enums.ReplaceableModule;

/**
 * URL replacement request parameter.
 *
 * @author halo-dev
 */
@Data
public class UrlReplaceParam {

    /**
     * The old URL to be replaced. Must not be blank.
     */
    @NotBlank(message = "旧 URL 不能为空")
    private String oldUrl;

    /**
     * The new URL to replace with. Must not be blank.
     */
    @NotBlank(message = "新 URL 不能为空")
    private String newUrl;

    /**
     * Whether this is a dry-run (preview only). Defaults to true for safety.
     */
    private Boolean dryRun = true;

    /**
     * The modules to apply replacement on. Empty set means all modules.
     */
    private Set<ReplaceableModule> modules = Collections.emptySet();
}
