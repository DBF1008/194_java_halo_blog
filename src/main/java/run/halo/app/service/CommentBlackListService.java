package run.halo.app.service;

import java.util.Collection;
import java.util.List;
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
     * Whether the given ip address is currently banned (entry exists and ban time is after now).
     *
     * @param ipAddress ip address must not be blank
     * @return true if the ip is currently banned, false otherwise
     */
    boolean isBanned(String ipAddress);

    /**
     * Manually bans an ip address. Creates a new black list entry when absent, or refreshes the
     * ban time from now when present (re-ban / 续封). When {@code banMinutes} is null, a long-term
     * ban is applied.
     *
     * @param ipAddress ip address must not be blank
     * @param banMinutes ban duration in minutes, nullable for a long-term ban
     * @return the created or updated black list entry
     */
    CommentBlackList ban(String ipAddress, Long banMinutes);

    /**
     * Manually unbans an ip address by removing its black list entry (解封). Does nothing when the
     * ip address has no entry.
     *
     * @param ipAddress ip address must not be blank
     */
    void unban(String ipAddress);

    /**
     * Gets the black list entry of the given ip address.
     *
     * @param ipAddress ip address must not be blank
     * @return an optional black list entry
     */
    Optional<CommentBlackList> getByIpAddress(String ipAddress);

    /**
     * Lists black list entries whose ip address is in the given collection.
     *
     * @param ipAddresses ip address collection
     * @return a list of black list entries
     */
    List<CommentBlackList> listByIpAddressIn(Collection<String> ipAddresses);
}
