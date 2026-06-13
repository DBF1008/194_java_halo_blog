package run.halo.app.controller.admin.api;

import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import run.halo.app.annotation.DisableOnCondition;
import run.halo.app.model.dto.OptionDiffDTO;
import run.halo.app.model.dto.SnapshotDTO;
import run.halo.app.service.OptionSnapshotService;

/**
 * Option snapshot controller.
 *
 * @author halo
 * @date 2024-01-01
 */
@RestController
@RequestMapping("/api/admin/options/snapshots")
public class OptionSnapshotController {

    private final OptionSnapshotService optionSnapshotService;

    public OptionSnapshotController(OptionSnapshotService optionSnapshotService) {
        this.optionSnapshotService = optionSnapshotService;
    }

    @PostMapping
    @ApiOperation("Creates a named snapshot of current options")
    @DisableOnCondition
    public SnapshotDTO create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        return optionSnapshotService.create(name);
    }

    @GetMapping
    @ApiOperation("Lists all snapshots")
    public List<SnapshotDTO> list() {
        return optionSnapshotService.list();
    }

    @GetMapping("{name}")
    @ApiOperation("Gets snapshot detail by name")
    public SnapshotDTO get(@PathVariable("name") String name) {
        return optionSnapshotService.get(name);
    }

    @DeleteMapping("{name}")
    @ApiOperation("Deletes a snapshot by name")
    @DisableOnCondition
    public void delete(@PathVariable("name") String name) {
        optionSnapshotService.remove(name);
    }

    @GetMapping("{name}/diff")
    @ApiOperation("Computes diff between snapshot and current options")
    public OptionDiffDTO diff(@PathVariable("name") String name) {
        return optionSnapshotService.diff(name);
    }

    @PostMapping("{name}/apply")
    @ApiOperation("Applies entire snapshot as current configuration")
    @DisableOnCondition
    public void apply(@PathVariable("name") String name) {
        optionSnapshotService.apply(name);
    }

    @PostMapping("{name}/apply-partial")
    @ApiOperation("Applies specified keys from snapshot")
    @DisableOnCondition
    public void applyPartially(@PathVariable("name") String name,
        @RequestBody Map<String, List<String>> body) {
        List<String> keys = body.get("keys");
        optionSnapshotService.applyPartially(name, keys);
    }
}
