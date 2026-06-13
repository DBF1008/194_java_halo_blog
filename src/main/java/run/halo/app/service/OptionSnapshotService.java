package run.halo.app.service;

import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;
import run.halo.app.model.dto.OptionSnapshotDTO;
import run.halo.app.model.entity.OptionSnapshot;
import run.halo.app.model.params.OptionSnapshotParam;
import run.halo.app.model.vo.OptionSnapshotDetailVO;
import run.halo.app.model.vo.OptionSnapshotDiffVO;
import run.halo.app.service.base.CrudService;

/**
 * Option snapshot service interface.
 *
 * <p>Provides the ability to capture a named, point-in-time copy of the current configuration,
 * compare it against the live configuration, and either apply the whole snapshot or restore only
 * selected keys. All restore operations go through {@link OptionService#save(java.util.Map)} so that
 * default values, dynamic property conversion, cache refresh and the option-updated event chain
 * stay consistent with the existing option entry points (both map_view and list_view).
 *
 * @author halo
 */
public interface OptionSnapshotService extends CrudService<OptionSnapshot, Integer> {

    /**
     * Creates a snapshot capturing the current raw (persisted) options.
     *
     * @param param snapshot param must not be null
     * @return the created snapshot dto
     */
    @NonNull
    @Transactional
    OptionSnapshotDTO createSnapshot(@NonNull OptionSnapshotParam param);

    /**
     * Lists all snapshots (lightweight, without the captured data) ordered by update time desc.
     *
     * @return a list of snapshot dtos
     */
    @NonNull
    List<OptionSnapshotDTO> listDtos();

    /**
     * Gets the snapshot detail (including the captured data) by id.
     *
     * @param id snapshot id must not be null
     * @return the snapshot detail vo
     */
    @NonNull
    OptionSnapshotDetailVO getDetailVoById(@NonNull Integer id);

    /**
     * Computes the difference between the snapshot and the current raw configuration.
     *
     * @param id snapshot id must not be null
     * @return the diff result
     */
    @NonNull
    OptionSnapshotDiffVO diff(@NonNull Integer id);

    /**
     * Applies the whole snapshot, restoring every captured key to its snapshot value. Keys that
     * exist in the current configuration but were not captured by the snapshot are left untouched.
     *
     * @param id snapshot id must not be null
     */
    @Transactional
    void apply(@NonNull Integer id);

    /**
     * Restores only the specified keys from the snapshot to their snapshot values.
     *
     * @param id snapshot id must not be null
     * @param keys keys to restore, must contain at least one key present in the snapshot
     */
    @Transactional
    void applyKeys(@NonNull Integer id, @NonNull List<String> keys);

    /**
     * Converts an option snapshot to a dto.
     *
     * @param snapshot snapshot must not be null
     * @return the snapshot dto
     */
    @NonNull
    OptionSnapshotDTO convertTo(@NonNull OptionSnapshot snapshot);
}
