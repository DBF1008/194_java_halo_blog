package run.halo.app.controller.admin.api;

import io.swagger.annotations.ApiOperation;
import java.util.EnumSet;
import java.util.Set;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import run.halo.app.model.dto.UrlReplaceResult;
import run.halo.app.model.enums.ReplaceableModule;
import run.halo.app.model.params.UrlReplaceParam;
import run.halo.app.service.ThemeSettingService;
import run.halo.app.service.UrlReplaceScanService;

/**
 * Data process controller.
 *
 * @author ryanwang
 * @author halo-dev
 * @date 2019-12-29
 */
@RestController
@RequestMapping("/api/admin/data/process")
public class DataProcessController {

    private final ThemeSettingService themeSettingService;

    private final UrlReplaceScanService urlReplaceScanService;

    public DataProcessController(ThemeSettingService themeSettingService,
        UrlReplaceScanService urlReplaceScanService) {
        this.themeSettingService = themeSettingService;
        this.urlReplaceScanService = urlReplaceScanService;
    }

    @PostMapping("url/replace")
    @ApiOperation("Preview or execute URL replacement across selected modules.")
    public UrlReplaceResult replaceUrl(@Valid @RequestBody UrlReplaceParam param) {
        Set<ReplaceableModule> modules = param.getModules().isEmpty()
            ? EnumSet.allOf(ReplaceableModule.class)
            : param.getModules();

        if (Boolean.TRUE.equals(param.getDryRun())) {
            return urlReplaceScanService.scan(param.getOldUrl(), param.getNewUrl(), modules);
        } else {
            return urlReplaceScanService.execute(param.getOldUrl(), param.getNewUrl(), modules);
        }
    }

    @DeleteMapping("themes/settings/inactivated")
    @ApiOperation("Delete inactivated theme settings.")
    public void deleteInactivatedThemeSettings() {
        themeSettingService.deleteInactivated();
    }
}
