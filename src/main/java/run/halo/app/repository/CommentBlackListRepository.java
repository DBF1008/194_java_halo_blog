package run.halo.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import run.halo.app.model.entity.CommentBlackList;
import run.halo.app.repository.base.BaseRepository;

/**
 * 评论黑名单Repository
 *
 * @author Lei XinXin
 * @date 2020/1/3
 */
public interface CommentBlackListRepository extends BaseRepository<CommentBlackList, Long> {

    /**
     * 根据IP地址获取数据.
     *
     * @param ipAddress ip address
     * @return comment black list or empty
     */
    Optional<CommentBlackList> findByIpAddress(String ipAddress);

    /**
     * Update Comment BlackList By IPAddress.
     *
     * @param commentBlackList comment black list
     * @return result
     */
    @Modifying
    @Query("UPDATE CommentBlackList SET banTime=:#{#commentBlackList.banTime} WHERE "
        + "ipAddress=:#{#commentBlackList.ipAddress}")
    int updateByIpAddress(@Param("commentBlackList") CommentBlackList commentBlackList);

    /**
     * Find all blacklist entries matching the given IP addresses.
     * Used for batch enrichment in the moderation inbox.
     *
     * @param ipAddresses collection of IP addresses
     * @return list of blacklist entries
     */
    List<CommentBlackList> findAllByIpAddressIn(Collection<String> ipAddresses);
}
