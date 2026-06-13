package run.halo.app.model.params;

import java.util.Set;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import run.halo.app.model.enums.UrlReplaceModule;

/**
 * Parameter describing a site-wide url replacement request.
 *
 * @author halo
 */
@Data
public class UrlReplaceParam {

    @NotBlank(message = "Old url must not be blank")
    private String oldUrl;

    @NotNull(message = "New url must not be null")
    private String newUrl;

    private boolean dryRun = true;

    private Set<UrlReplaceModule> modules;

    public UrlReplaceParam() {
    }

    public UrlReplaceParam(String oldUrl, String newUrl, boolean dryRun,
        Set<UrlReplaceModule> modules) {
        this.oldUrl = oldUrl;
        this.newUrl = newUrl;
        this.dryRun = dryRun;
        this.modules = modules;
    }

    /**
     * Whether the given module should be processed. An empty or null module set means all modules.
     *
     * @param module module to test
     * @return true if the module is selected
     */
    public boolean appliesTo(UrlReplaceModule module) {
        return modules == null || modules.isEmpty() || modules.contains(module);
    }
}
