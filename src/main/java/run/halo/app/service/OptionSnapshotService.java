package run.halo.app.service;

import java.util.List;
import org.springframework.lang.NonNull;
import run.halo.app.model.dto.OptionDiffDTO;
import run.halo.app.model.dto.SnapshotDTO;

/**
 * Option snapshot service interface.
 *
 * @author halo
 * @date 2024-01-01
 */
public interface OptionSnapshotService {

    /**
     * Creates a named snapshot of all current options.
     *
     * @param name snapshot name must not be blank
     * @return snapshot detail
     */
    @NonNull
    SnapshotDTO create(@NonNull String name);

    /**
     * Lists all available snapshots.
     *
     * @return list of snapshot summaries
     */
    @NonNull
    List<SnapshotDTO> list();

    /**
     * Gets snapshot detail by name.
     *
     * @param name snapshot name must not be blank
     * @return snapshot detail
     */
    @NonNull
    SnapshotDTO get(@NonNull String name);

    /**
     * Removes a snapshot by name.
     *
     * @param name snapshot name must not be blank
     */
    void remove(@NonNull String name);

    /**
     * Computes the diff between a snapshot and current options.
     *
     * @param name snapshot name must not be blank
     * @return diff result
     */
    @NonNull
    OptionDiffDTO diff(@NonNull String name);

    /**
     * Applies an entire snapshot as the current configuration (full rollback).
     *
     * @param name snapshot name must not be blank
     */
    void apply(@NonNull String name);

    /**
     * Applies only the specified keys from a snapshot.
     *
     * @param name snapshot name must not be blank
     * @param keys option keys to restore from the snapshot
     */
    void applyPartially(@NonNull String name, @NonNull List<String> keys);
}
