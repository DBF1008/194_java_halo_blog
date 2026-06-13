package run.halo.app.controller.admin.api;

import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import run.halo.app.annotation.DisableOnCondition;
import run.halo.app.model.dto.OptionSnapshotDTO;
import run.halo.app.model.params.OptionSnapshotParam;
import run.halo.app.model.vo.OptionSnapshotDetailVO;
import run.halo.app.model.vo.OptionSnapshotDiffVO;
import run.halo.app.service.OptionSnapshotService;

/**
 * Option snapshot controller.
 *
 * <p>Lets administrators stage configuration changes: save a named snapshot of the current
 * options, inspect the diff against the live configuration, then apply the whole snapshot or
 * restore only selected keys (with cache and option-updated event refreshed afterwards).
 *
 * @author halo
 */
@RestController
@RequestMapping("/api/admin/options/snapshots")
public class OptionSnapshotController {

    private final OptionSnapshotService optionSnapshotService;

    public OptionSnapshotController(OptionSnapshotService optionSnapshotService) {
        this.optionSnapshotService = optionSnapshotService;
    }

    @GetMapping
    @ApiOperation("Lists option snapshots")
    public List<OptionSnapshotDTO> list() {
        return optionSnapshotService.listDtos();
    }

    @GetMapping("{id:\\d+}")
    @ApiOperation("Gets option snapshot detail by id")
    public OptionSnapshotDetailVO getBy(@PathVariable("id") Integer id) {
        return optionSnapshotService.getDetailVoById(id);
    }

    @GetMapping("{id:\\d+}/diff")
    @ApiOperation("Compares an option snapshot with the current configuration")
    public OptionSnapshotDiffVO diff(@PathVariable("id") Integer id) {
        return optionSnapshotService.diff(id);
    }

    @PostMapping
    @ApiOperation("Creates an option snapshot of the current configuration")
    @DisableOnCondition
    public OptionSnapshotDTO create(@RequestBody @Valid OptionSnapshotParam optionSnapshotParam) {
        return optionSnapshotService.createSnapshot(optionSnapshotParam);
    }

    @PutMapping("{id:\\d+}/apply")
    @ApiOperation("Applies an option snapshot as a whole")
    @DisableOnCondition
    public void apply(@PathVariable("id") Integer id) {
        optionSnapshotService.apply(id);
    }

    @PutMapping("{id:\\d+}/apply_keys")
    @ApiOperation("Restores selected keys from an option snapshot")
    @DisableOnCondition
    public void applyKeys(@PathVariable("id") Integer id, @RequestBody List<String> keys) {
        optionSnapshotService.applyKeys(id, keys);
    }

    @DeleteMapping("{id:\\d+}")
    @ApiOperation("Deletes an option snapshot")
    @DisableOnCondition
    public void delete(@PathVariable("id") Integer id) {
        optionSnapshotService.removeById(id);
    }
}
