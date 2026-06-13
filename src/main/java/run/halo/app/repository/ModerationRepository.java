package run.halo.app.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import run.halo.app.model.entity.BaseComment;

/**
 * Repository for cross-type comment queries used by the moderation inbox.
 * Leverages the single-table inheritance strategy on the comments table
 * so that one query can return PostComment, SheetComment, and JournalComment
 * entities simultaneously.
 *
 * @author halo
 */
public interface ModerationRepository
    extends JpaRepository<BaseComment, Long>, JpaSpecificationExecutor<BaseComment> {

    /**
     * Batch-fetch comments by IDs. Used to load parent/ancestor comments
     * for reply chain building without N+1 queries.
     *
     * @param ids comment IDs
     * @return list of comments
     */
    List<BaseComment> findAllByIdIn(Collection<Long> ids);

    /**
     * Count non-recycled comments per IP address for risk scoring.
     *
     * @param ipAddresses collection of IP addresses to count
     * @return each element is Object[]{ipAddress(String), count(Long)}
     */
    @Query("SELECT c.ipAddress, COUNT(c.id) FROM BaseComment c "
        + "WHERE c.ipAddress IN ?1 AND c.status <> 2 "
        + "GROUP BY c.ipAddress")
    List<Object[]> countCommentsByIpAddresses(Collection<String> ipAddresses);
}
