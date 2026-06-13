package run.halo.app.service;

import java.util.Optional;
import run.halo.app.model.entity.CommentBlackList;
import run.halo.app.model.enums.CommentViolationTypeEnum;
import run.halo.app.service.base.CrudService;

/**
 * Comment BlackList Service
 *
 * @author Lei XinXin
 * @date 2020/1/3
 */
public interface CommentBlackListService extends CrudService<CommentBlackList, Long> {
    /**
     * 评论封禁状态
     *
     * @param ipAddress ip地址
     * @return boolean
     */
    CommentViolationTypeEnum commentsBanStatus(String ipAddress);

    /**
     * Check if an IP address is currently banned (ban not expired).
     *
     * @param ipAddress ip address
     * @return true if the IP has an active (non-expired) ban
     */
    boolean isIpBanned(String ipAddress);

    /**
     * Get blacklist entry by IP address if it exists.
     *
     * @param ipAddress ip address
     * @return optional blacklist entry
     */
    Optional<CommentBlackList> getByIpAddress(String ipAddress);
}
