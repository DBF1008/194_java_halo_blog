package run.halo.app.repository;

import java.util.Optional;
import run.halo.app.model.entity.OptionSnapshot;
import run.halo.app.repository.base.BaseRepository;

/**
 * Option snapshot repository.
 *
 * @author halo
 */
public interface OptionSnapshotRepository extends BaseRepository<OptionSnapshot, Integer> {

    /**
     * Finds an option snapshot by name.
     *
     * @param name snapshot name
     * @return an optional option snapshot
     */
    Optional<OptionSnapshot> findByName(String name);

    /**
     * Checks whether a snapshot with the given name exists.
     *
     * @param name snapshot name
     * @return true if exists, false otherwise
     */
    boolean existsByName(String name);
}
