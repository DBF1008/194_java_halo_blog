package run.halo.app.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import run.halo.app.exception.BadRequestException;
import run.halo.app.exception.NotFoundException;
import run.halo.app.model.dto.BaseCommentDTO;
import run.halo.app.model.dto.ModerationStatsDTO;
import run.halo.app.model.entity.BaseComment;
import run.halo.app.model.entity.CommentBlackList;
import run.halo.app.model.entity.Journal;
import run.halo.app.model.entity.JournalComment;
import run.halo.app.model.entity.PostComment;
import run.halo.app.model.entity.SheetComment;
import run.halo.app.model.enums.CommentSourceType;
import run.halo.app.model.enums.CommentStatus;
import run.halo.app.model.enums.ModerationAction;
import run.halo.app.model.params.CommentBatchBanParam;
import run.halo.app.model.params.ModerationQuery;
import run.halo.app.model.properties.CommentProperties;
import run.halo.app.model.vo.ModerationCommentVO;
import run.halo.app.repository.CommentBlackListRepository;
import run.halo.app.repository.ModerationRepository;
import run.halo.app.service.CommentBlackListService;
import run.halo.app.service.JournalCommentService;
import run.halo.app.service.JournalService;
import run.halo.app.service.ModerationService;
import run.halo.app.service.OptionService;
import run.halo.app.service.PostCommentService;
import run.halo.app.service.PostService;
import run.halo.app.service.SheetCommentService;
import run.halo.app.service.SheetService;
import run.halo.app.service.base.BaseCommentService;
import run.halo.app.utils.ServiceUtils;

/**
 * Moderation inbox service implementation.
 * Orchestrates across the three comment services and the blacklist service
 * without duplicating any business logic.
 *
 * @author halo
 */
@Service
@Slf4j
public class ModerationServiceImpl implements ModerationService {

    private static final int JOURNAL_TITLE_MAX_LENGTH = 50;
    private static final int MAX_ANCESTOR_DEPTH = 20;

    private final ModerationRepository moderationRepository;
    private final PostCommentService postCommentService;
    private final SheetCommentService sheetCommentService;
    private final JournalCommentService journalCommentService;
    private final CommentBlackListService commentBlackListService;
    private final CommentBlackListRepository commentBlackListRepository;
    private final PostService postService;
    private final SheetService sheetService;
    private final JournalService journalService;
    private final OptionService optionService;

    public ModerationServiceImpl(ModerationRepository moderationRepository,
        PostCommentService postCommentService,
        SheetCommentService sheetCommentService,
        JournalCommentService journalCommentService,
        CommentBlackListService commentBlackListService,
        CommentBlackListRepository commentBlackListRepository,
        PostService postService,
        SheetService sheetService,
        JournalService journalService,
        OptionService optionService) {
        this.moderationRepository = moderationRepository;
        this.postCommentService = postCommentService;
        this.sheetCommentService = sheetCommentService;
        this.journalCommentService = journalCommentService;
        this.commentBlackListService = commentBlackListService;
        this.commentBlackListRepository = commentBlackListRepository;
        this.postService = postService;
        this.sheetService = sheetService;
        this.journalService = journalService;
        this.optionService = optionService;
    }

    @Override
    public Page<ModerationCommentVO> pageModerationInbox(ModerationQuery query,
        Pageable pageable) {
        Assert.notNull(pageable, "Pageable must not be null");

        // Step 1: Cross-type query via Specification
        Specification<BaseComment> spec = buildModerationSpec(query);
        Page<BaseComment> commentPage = moderationRepository.findAll(spec, pageable);

        if (commentPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        List<BaseComment> comments = commentPage.getContent();

        // Step 2: Batch-load ancestor comments for reply chains
        Map<Long, BaseComment> ancestorMap = batchLoadAncestors(comments);

        // Step 3: Batch-load blacklist status for all unique IPs
        Set<String> uniqueIps = ServiceUtils.fetchProperty(comments, BaseComment::getIpAddress);
        uniqueIps.remove("");
        uniqueIps.remove(null);
        Map<String, CommentBlackList> blacklistMap = buildBlacklistMap(uniqueIps);

        // Step 4: Batch-count comments per IP for risk scoring
        Map<String, Long> ipCommentCountMap = buildIpCommentCountMap(uniqueIps);

        // Step 5: Resolve source titles
        Map<String, String> sourceTitleMap = buildSourceTitleMap(comments);

        // Step 6: Assemble VOs
        List<ModerationCommentVO> voList = comments.stream()
            .map(comment -> buildModerationVO(comment, ancestorMap, blacklistMap,
                ipCommentCountMap, sourceTitleMap))
            .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, commentPage.getTotalElements());
    }

    @Override
    public ModerationCommentVO getCommentDetail(Long commentId) {
        Assert.notNull(commentId, "Comment ID must not be null");

        BaseComment comment = moderationRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found").setErrorData(commentId));

        List<BaseComment> singleList = Collections.singletonList(comment);
        Map<Long, BaseComment> ancestorMap = batchLoadAncestors(singleList);

        Set<String> ips = new HashSet<>();
        if (StringUtils.isNotBlank(comment.getIpAddress())) {
            ips.add(comment.getIpAddress());
        }
        Map<String, CommentBlackList> blacklistMap = buildBlacklistMap(ips);
        Map<String, Long> ipCommentCountMap = buildIpCommentCountMap(ips);
        Map<String, String> sourceTitleMap = buildSourceTitleMap(singleList);

        return buildModerationVO(comment, ancestorMap, blacklistMap,
            ipCommentCountMap, sourceTitleMap);
    }

    @Override
    public BaseCommentDTO approveComment(Long commentId) {
        return updateCommentStatus(commentId, CommentStatus.PUBLISHED);
    }

    @Override
    public BaseCommentDTO rejectComment(Long commentId) {
        return updateCommentStatus(commentId, CommentStatus.RECYCLE);
    }

    @Override
    public List<BaseCommentDTO> batchApprove(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return ids.stream()
            .map(this::approveComment)
            .collect(Collectors.toList());
    }

    @Override
    public List<BaseCommentDTO> batchReject(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return ids.stream()
            .map(this::rejectComment)
            .collect(Collectors.toList());
    }

    @Override
    public CommentBlackList banIpByCommentId(Long commentId, Integer banDurationMinutes) {
        Assert.notNull(commentId, "Comment ID must not be null");

        BaseComment comment = moderationRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found").setErrorData(commentId));

        String ip = comment.getIpAddress();
        if (StringUtils.isBlank(ip)) {
            throw new BadRequestException("Comment has no IP address");
        }

        return doBanIp(ip, banDurationMinutes);
    }

    @Override
    public List<CommentBlackList> batchBanIps(CommentBatchBanParam param) {
        Assert.notNull(param, "Batch ban param must not be null");
        Assert.notEmpty(param.getCommentIds(), "Comment IDs must not be empty");

        // Collect unique IPs from the given comment IDs
        Set<String> uniqueIps = new HashSet<>();
        for (Long commentId : param.getCommentIds()) {
            moderationRepository.findById(commentId).ifPresent(comment -> {
                if (StringUtils.isNotBlank(comment.getIpAddress())) {
                    uniqueIps.add(comment.getIpAddress());
                }
            });
        }

        return uniqueIps.stream()
            .map(ip -> doBanIp(ip, param.getBanDurationMinutes()))
            .collect(Collectors.toList());
    }

    @Override
    public CommentBlackList unbanIp(String ipAddress) {
        Assert.hasText(ipAddress, "IP address must not be blank");

        CommentBlackList bl = commentBlackListRepository.findByIpAddress(ipAddress)
            .orElseThrow(() -> new NotFoundException(
                "IP address not found in blacklist: " + ipAddress));
        commentBlackListService.removeById(bl.getId());
        return bl;
    }

    @Override
    public CommentBlackList renewBan(String ipAddress, Integer additionalMinutes) {
        Assert.hasText(ipAddress, "IP address must not be blank");

        CommentBlackList bl = commentBlackListRepository.findByIpAddress(ipAddress)
            .orElseThrow(() -> new NotFoundException(
                "IP address not found in blacklist: " + ipAddress));

        int minutes = resolveBanDuration(additionalMinutes);
        Date newBanTime = new Date(System.currentTimeMillis() + minutes * 60 * 1000L);
        bl.setBanTime(newBanTime);
        commentBlackListRepository.updateByIpAddress(bl);
        return bl;
    }

    @Override
    public ModerationStatsDTO getModerationStats() {
        ModerationStatsDTO stats = new ModerationStatsDTO();

        // Count comments by status across all types
        stats.setPendingCount(countByStatus(CommentStatus.AUDITING));
        stats.setPublishedCount(countByStatus(CommentStatus.PUBLISHED));
        stats.setRecycleCount(countByStatus(CommentStatus.RECYCLE));

        // Blacklist stats
        List<CommentBlackList> allBlacklist = commentBlackListService.listAll();
        stats.setBlacklistedIpCount(allBlacklist.size());

        Date now = new Date();
        long activeBanCount = allBlacklist.stream()
            .filter(bl -> bl.getBanTime() != null && bl.getBanTime().after(now))
            .count();
        stats.setActiveBanCount(activeBanCount);

        return stats;
    }

    @Override
    public Page<CommentBlackList> pageBlacklist(Pageable pageable) {
        Assert.notNull(pageable, "Pageable must not be null");
        return commentBlackListService.listAll(pageable);
    }

    // ========== Private helpers ==========

    /**
     * Update comment status by routing to the correct typed service.
     * This is the single code path that both single-item and batch operations share.
     */
    private BaseCommentDTO updateCommentStatus(Long commentId, CommentStatus status) {
        Assert.notNull(commentId, "Comment ID must not be null");
        Assert.notNull(status, "Comment status must not be null");

        BaseComment comment = moderationRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found").setErrorData(commentId));

        BaseCommentService<?> service = getCommentService(comment);
        BaseComment updated = service.updateStatus(commentId, status);
        return new BaseCommentDTO().convertFrom(updated);
    }

    /**
     * Route to the correct typed comment service based on entity type.
     */
    @NonNull
    private BaseCommentService<?> getCommentService(@NonNull BaseComment comment) {
        if (comment instanceof PostComment) {
            return postCommentService;
        } else if (comment instanceof SheetComment) {
            return sheetCommentService;
        } else if (comment instanceof JournalComment) {
            return journalCommentService;
        }
        throw new IllegalArgumentException("Unknown comment type: " + comment.getClass());
    }

    /**
     * Build JPA Specification for cross-type moderation queries.
     */
    private Specification<BaseComment> buildModerationSpec(ModerationQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new LinkedList<>();

            if (query == null) {
                return cq.where(predicates.toArray(new Predicate[0])).getRestriction();
            }

            // Status filter
            if (query.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), query.getStatus()));
            }

            // Keyword search (same pattern as BaseCommentServiceImpl.buildSpecByQuery)
            if (StringUtils.isNotBlank(query.getKeyword())) {
                String like = String.format("%%%s%%", StringUtils.strip(query.getKeyword()));
                Predicate authorLike = cb.like(root.get("author"), like);
                Predicate contentLike = cb.like(root.get("content"), like);
                Predicate emailLike = cb.like(root.get("email"), like);
                predicates.add(cb.or(authorLike, contentLike, emailLike));
            }

            return cq.where(predicates.toArray(new Predicate[0])).getRestriction();
        };
    }

    /**
     * Batch-load all ancestor comments for reply chain building.
     * Uses iterative round-based loading to avoid N+1 queries.
     */
    private Map<Long, BaseComment> batchLoadAncestors(List<BaseComment> comments) {
        Map<Long, BaseComment> ancestorMap = new HashMap<>();

        // Collect immediate parent IDs
        Queue<Long> toProcess = new LinkedList<>();
        for (BaseComment c : comments) {
            if (c.getParentId() != null && c.getParentId() != 0L) {
                toProcess.add(c.getParentId());
            }
        }

        int depth = 0;
        while (!toProcess.isEmpty() && depth < MAX_ANCESTOR_DEPTH) {
            Set<Long> batch = new HashSet<>();
            while (!toProcess.isEmpty()) {
                Long id = toProcess.poll();
                if (!ancestorMap.containsKey(id)) {
                    batch.add(id);
                }
            }
            if (batch.isEmpty()) {
                break;
            }
            List<BaseComment> parents = moderationRepository.findAllByIdIn(batch);
            for (BaseComment p : parents) {
                ancestorMap.put(p.getId(), p);
                if (p.getParentId() != null && p.getParentId() != 0L
                    && !ancestorMap.containsKey(p.getParentId())) {
                    toProcess.add(p.getParentId());
                }
            }
            depth++;
        }

        return ancestorMap;
    }

    /**
     * Batch-load blacklist entries for a set of IPs.
     */
    private Map<String, CommentBlackList> buildBlacklistMap(Set<String> ips) {
        if (CollectionUtils.isEmpty(ips)) {
            return Collections.emptyMap();
        }
        List<CommentBlackList> entries = commentBlackListRepository.findAllByIpAddressIn(ips);
        return entries.stream()
            .collect(Collectors.toMap(CommentBlackList::getIpAddress, bl -> bl, (a, b) -> a));
    }

    /**
     * Batch-count comments per IP.
     */
    private Map<String, Long> buildIpCommentCountMap(Set<String> ips) {
        if (CollectionUtils.isEmpty(ips)) {
            return Collections.emptyMap();
        }
        List<Object[]> results = moderationRepository.countCommentsByIpAddresses(ips);
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : results) {
            String ip = (String) row[0];
            Long count = (Long) row[1];
            map.put(ip, count);
        }
        return map;
    }

    /**
     * Build source title map: key = "type:postId", value = title.
     */
    private Map<String, String> buildSourceTitleMap(List<BaseComment> comments) {
        Map<String, String> titleMap = new HashMap<>();

        // Group by type
        Set<Integer> postIds = new HashSet<>();
        Set<Integer> sheetIds = new HashSet<>();
        Set<Integer> journalIds = new HashSet<>();

        for (BaseComment c : comments) {
            CommentSourceType type = resolveSourceType(c);
            switch (type) {
                case POST:
                    postIds.add(c.getPostId());
                    break;
                case SHEET:
                    sheetIds.add(c.getPostId());
                    break;
                case JOURNAL:
                    journalIds.add(c.getPostId());
                    break;
                default:
                    break;
            }
        }

        // Batch-load titles
        if (!postIds.isEmpty()) {
            postService.listAllByIds(postIds).forEach(
                post -> titleMap.put(
                    CommentSourceType.POST.getDiscriminator() + ":" + post.getId(),
                    post.getTitle()));
        }
        if (!sheetIds.isEmpty()) {
            sheetService.listAllByIds(sheetIds).forEach(
                sheet -> titleMap.put(
                    CommentSourceType.SHEET.getDiscriminator() + ":" + sheet.getId(),
                    sheet.getTitle()));
        }
        if (!journalIds.isEmpty()) {
            journalService.listAllByIds(journalIds).forEach(
                journal -> titleMap.put(
                    CommentSourceType.JOURNAL.getDiscriminator() + ":" + journal.getId(),
                    truncate(journal.getContent(), JOURNAL_TITLE_MAX_LENGTH)));
        }

        return titleMap;
    }

    /**
     * Assemble a ModerationCommentVO from a BaseComment and enrichment data.
     */
    private ModerationCommentVO buildModerationVO(BaseComment comment,
        Map<Long, BaseComment> ancestorMap,
        Map<String, CommentBlackList> blacklistMap,
        Map<String, Long> ipCommentCountMap,
        Map<String, String> sourceTitleMap) {

        ModerationCommentVO vo = new ModerationCommentVO();

        // Core comment fields
        vo.setId(comment.getId());
        vo.setAuthor(comment.getAuthor());
        vo.setEmail(comment.getEmail());
        vo.setIpAddress(comment.getIpAddress());
        vo.setAuthorUrl(comment.getAuthorUrl());
        vo.setGravatarMd5(comment.getGravatarMd5());
        vo.setContent(comment.getContent());
        vo.setStatus(comment.getStatus());
        vo.setUserAgent(comment.getUserAgent());
        vo.setParentId(comment.getParentId());
        vo.setIsAdmin(comment.getIsAdmin());
        vo.setAllowNotification(comment.getAllowNotification());
        vo.setCreateTime(comment.getCreateTime());

        // Source type and title
        CommentSourceType sourceType = resolveSourceType(comment);
        vo.setSourceType(sourceType);
        vo.setSourceId(comment.getPostId());
        String titleKey = sourceType.getDiscriminator() + ":" + comment.getPostId();
        vo.setSourceTitle(sourceTitleMap.get(titleKey));

        // Reply chain
        vo.setReplyChain(buildReplyChain(comment, ancestorMap));

        // IP blacklist status
        String ip = comment.getIpAddress();
        CommentBlackList blacklist = StringUtils.isNotBlank(ip) ? blacklistMap.get(ip) : null;
        Date now = new Date();
        boolean ipCurrentlyBanned = blacklist != null && blacklist.getBanTime() != null
            && blacklist.getBanTime().after(now);
        vo.setIpBanned(ipCurrentlyBanned);
        vo.setIpBanExpiry(ipCurrentlyBanned ? blacklist.getBanTime() : null);

        // IP comment count
        long ipCount = StringUtils.isNotBlank(ip)
            ? ipCommentCountMap.getOrDefault(ip, 0L) : 0L;
        vo.setIpCommentCount((int) ipCount);

        // Risk indicators
        ModerationCommentVO.RiskIndicators indicators = new ModerationCommentVO.RiskIndicators();
        indicators.setPendingReview(comment.getStatus() == CommentStatus.AUDITING);
        indicators.setFromBannedIp(ipCurrentlyBanned);
        indicators.setFrequentCommenter(ipCount > 10);
        vo.setRiskIndicators(indicators);

        // Recommended actions
        vo.setRecommendedActions(computeRecommendedActions(comment, blacklist, ip));

        return vo;
    }

    /**
     * Build the reply chain from root to the immediate parent of this comment.
     */
    private List<BaseCommentDTO> buildReplyChain(BaseComment comment,
        Map<Long, BaseComment> ancestorMap) {
        List<BaseCommentDTO> chain = new ArrayList<>();
        Long currentParentId = comment.getParentId();
        int depth = 0;

        while (currentParentId != null && currentParentId != 0L
            && depth < MAX_ANCESTOR_DEPTH) {
            BaseComment parent = ancestorMap.get(currentParentId);
            if (parent == null) {
                break;
            }
            chain.add(0, new BaseCommentDTO().convertFrom(parent));
            currentParentId = parent.getParentId();
            depth++;
        }
        return chain;
    }

    /**
     * Compute recommended moderation actions for a comment.
     */
    private List<ModerationAction> computeRecommendedActions(BaseComment comment,
        CommentBlackList blacklist, String ip) {
        List<ModerationAction> actions = new ArrayList<>();
        Date now = new Date();

        // Status-based actions
        if (comment.getStatus() == CommentStatus.AUDITING) {
            actions.add(ModerationAction.APPROVE);
            actions.add(ModerationAction.REJECT);
        }
        if (comment.getStatus() == CommentStatus.PUBLISHED) {
            actions.add(ModerationAction.REJECT);
        }

        // IP-based actions
        boolean ipCurrentlyBanned = blacklist != null && blacklist.getBanTime() != null
            && blacklist.getBanTime().after(now);
        if (ipCurrentlyBanned) {
            actions.add(ModerationAction.UNBAN_IP);
            actions.add(ModerationAction.RENEW_BAN);
        } else if (StringUtils.isNotBlank(ip)) {
            actions.add(ModerationAction.BAN_IP);
        }

        return actions;
    }

    /**
     * Resolve CommentSourceType from a BaseComment entity using instanceof.
     */
    private CommentSourceType resolveSourceType(BaseComment comment) {
        if (comment instanceof PostComment) {
            return CommentSourceType.POST;
        } else if (comment instanceof SheetComment) {
            return CommentSourceType.SHEET;
        } else if (comment instanceof JournalComment) {
            return CommentSourceType.JOURNAL;
        }
        // Default to POST for safety
        return CommentSourceType.POST;
    }

    /**
     * Execute an IP ban (create new or update existing).
     */
    private CommentBlackList doBanIp(String ip, Integer banDurationMinutes) {
        int duration = resolveBanDuration(banDurationMinutes);
        Date banTime = new Date(System.currentTimeMillis() + duration * 60 * 1000L);

        Optional<CommentBlackList> existing = commentBlackListRepository.findByIpAddress(ip);
        if (existing.isPresent()) {
            CommentBlackList bl = existing.get();
            bl.setBanTime(banTime);
            commentBlackListRepository.updateByIpAddress(bl);
            return bl;
        } else {
            CommentBlackList bl = CommentBlackList.builder()
                .ipAddress(ip)
                .banTime(banTime)
                .build();
            return commentBlackListService.create(bl);
        }
    }

    /**
     * Resolve ban duration: use custom value if provided, otherwise system default.
     */
    private int resolveBanDuration(Integer customMinutes) {
        if (customMinutes != null && customMinutes > 0) {
            return customMinutes;
        }
        return optionService.getByPropertyOrDefault(
            CommentProperties.COMMENT_BAN_TIME, Integer.class, 10);
    }

    /**
     * Count comments by status across all types using the moderation repository.
     */
    private long countByStatus(CommentStatus status) {
        Specification<BaseComment> spec = (root, cq, cb) ->
            cb.equal(root.get("status"), status);
        return moderationRepository.count(spec);
    }

    /**
     * Truncate string to max length, appending "..." if truncated.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
