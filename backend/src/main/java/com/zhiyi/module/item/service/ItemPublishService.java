package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.common.enums.ViolationSource;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.dto.ReportItemDTO;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.vo.ItemCardVO;
import com.zhiyi.module.item.vo.UploadImageVO;
import com.zhiyi.module.trade.mapper.ItemReservationMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品发布、整改、重新上架、举报与本地内容检测。
 */
@Service
@RequiredArgsConstructor
public class ItemPublishService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final int IMAGE_SIGNATURE_BYTES = 12;
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Map<String, String> REPORT_LABELS = Map.of(
            "PRICE_FRAUD", "疑似虚假价格",
            "PROHIBITED_ITEM", "疑似违禁商品",
            "IMAGE_VIOLATION", "图片内容违规",
            "ADVERTISING", "广告或站外引流",
            "OTHER", "其他问题"
    );

    private final ItemMapper itemMapper;
    private final CategoryMapper categoryMapper;
    private final ViolationReportMapper violationReportMapper;
    private final MarketplaceService marketplaceService;
    private final SysUserMapper userMapper;
    private final ItemReservationMapper reservationMapper;
    private final LocalContentAnalyzer contentAnalyzer;
    private final ItemTagService itemTagService;

    @Value("${zhiyi.upload-path:./uploads}")
    private String uploadPath;

    public UploadImageVO uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单张图片不能超过5MB");
        }
        String day = LocalDate.now().format(DAY_FORMATTER);
        Path targetDir = Path.of(uploadPath, "items", day).toAbsolutePath().normalize();
        try (BufferedInputStream input = new BufferedInputStream(file.getInputStream())) {
            input.mark(IMAGE_SIGNATURE_BYTES);
            byte[] signature = input.readNBytes(IMAGE_SIGNATURE_BYTES);
            ImageFormat imageFormat = validateImageFormat(
                    file.getOriginalFilename(), file.getContentType(), signature);
            input.reset();

            String filename = UUID.randomUUID().toString().replace("-", "")
                    + "." + imageFormat.extension();
            Path target = targetDir.resolve(filename).normalize();
            if (!target.startsWith(targetDir)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "文件名非法");
            }
            Files.createDirectories(targetDir);
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return new UploadImageVO("/uploads/items/" + day + "/" + filename);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "图片保存失败");
        }
    }

    @Transactional
    public ItemCardVO publish(Long publisherId, PublishItemDTO dto) {
        SysUser publisher = requirePublisher(publisherId);
        Category category = requireCategory(dto.getCategoryId());
        validateImages(dto.getImages());

        ContentCheck check = checkContent(dto, category);
        Item item = buildItem(publisherId, publisher.getSchoolId(), dto);
        item.setStatus(ItemStatus.ON_SALE);
        item.setModerationStatus(check.risky() ? ModerationStatus.PENDING : ModerationStatus.PASSED);
        itemMapper.insert(item);
        itemTagService.replaceTags(item.getId(), item.getSchoolId(), check.tags());
        if (check.risky()) {
            saveReview(publisherId, null, item.getId(), dto, ViolationSource.LOCAL_RULE, "KEYWORD_MATCH",
                    check.reason(), check.matchedRules(), check.ruleVersion());
        }
        return marketplaceService.getSnapshot(item.getId(), publisherId);
    }

    @Transactional
    public ItemCardVO update(Long publisherId, Long itemId, PublishItemDTO dto) {
        Item item = requireOwnedItem(publisherId, itemId);
        assertNotReserved(itemId);
        if (item.getStatus() == ItemStatus.SOLD) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已售出的商品不能编辑");
        }
        if (item.getModerationStatus() == ModerationStatus.PENDING) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品正在审核中，暂不能重复修改");
        }

        Category category = requireCategory(dto.getCategoryId());
        validateImages(dto.getImages());
        boolean correction = item.getModerationStatus() == ModerationStatus.REJECTED;
        ContentCheck check = checkContent(dto, category);
        applyContent(item, dto);

        if (correction) {
            item.setStatus(ItemStatus.OFF_SHELF);
            item.setModerationStatus(ModerationStatus.PENDING);
            itemMapper.updateById(item);
            String reason = check.risky()
                    ? check.reason()
                    : "卖家已提交整改内容，等待管理员复核";
            saveReview(publisherId, null, itemId, dto, ViolationSource.CORRECTION, "CORRECTION_REVIEW",
                    reason, check.matchedRules(), check.ruleVersion());
        } else if (check.risky()) {
            item.setModerationStatus(ModerationStatus.PENDING);
            itemMapper.updateById(item);
            saveReview(publisherId, null, itemId, dto, ViolationSource.LOCAL_RULE, "KEYWORD_MATCH",
                    check.reason(), check.matchedRules(), check.ruleVersion());
        } else {
            item.setModerationStatus(ModerationStatus.PASSED);
            itemMapper.updateById(item);
        }
        itemTagService.replaceTags(itemId, item.getSchoolId(), check.tags());
        return marketplaceService.getSnapshot(itemId, publisherId);
    }

    @Transactional
    public ItemCardVO relist(Long publisherId, Long itemId) {
        Item item = requireOwnedItem(publisherId, itemId);
        assertNotReserved(itemId);
        if (item.getStatus() != ItemStatus.OFF_SHELF) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已下架商品可以重新上架");
        }
        if (item.getModerationStatus() == ModerationStatus.PENDING) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品正在审核中");
        }
        if (item.getModerationStatus() == ModerationStatus.REJECTED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该商品因内容违规下架，请先整改或申诉");
        }

        Category category = requireCategory(item.getCategoryId());
        PublishItemDTO dto = toReviewDTO(item);
        ContentCheck check = checkContent(dto, category);
        if (check.risky()) {
            item.setModerationStatus(ModerationStatus.PENDING);
            itemMapper.updateById(item);
            saveReview(publisherId, null, itemId, dto, ViolationSource.LOCAL_RULE, "KEYWORD_MATCH",
                    check.reason(), check.matchedRules(), check.ruleVersion());
        } else {
            item.setStatus(ItemStatus.ON_SALE);
            item.setModerationStatus(ModerationStatus.PASSED);
            itemMapper.updateById(item);
        }
        itemTagService.replaceTags(itemId, item.getSchoolId(), check.tags());
        return marketplaceService.getSnapshot(itemId, publisherId);
    }

    /**
     * 用户举报不会自动隐藏商品，避免恶意举报造成下架；最终状态由管理员决定。
     */
    @Transactional
    public void report(Long reporterId, Long itemId, ReportItemDTO dto) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        marketplaceService.requireVisibleItem(reporterId, itemId);
        if (Objects.equals(item.getPublisherId(), reporterId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能举报自己发布的商品");
        }
        long existing = violationReportMapper.selectCount(new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, itemId)
                .eq(ViolationReport::getReporterId, reporterId)
                .eq(ViolationReport::getSource, ViolationSource.USER_REPORT)
                .eq(ViolationReport::getStatus, ViolationStatus.PENDING));
        if (existing > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "你已经举报过该商品，管理员正在处理");
        }

        ViolationReport report = new ViolationReport();
        report.setUserId(item.getPublisherId());
        report.setReporterId(reporterId);
        report.setItemId(itemId);
        report.setOriginalTitle(item.getTitle());
        report.setOriginalDescription(item.getDescription());
        report.setSource(ViolationSource.USER_REPORT);
        report.setViolationType(dto.type());
        String label = REPORT_LABELS.getOrDefault(dto.type(), "其他问题");
        report.setViolationReason(StringUtils.hasText(dto.details())
                ? label + "：" + dto.details().trim()
                : label);
        report.setMatchedRules(List.of());
        report.setRuleVersion(null);
        report.setStatus(ViolationStatus.PENDING);
        violationReportMapper.insert(report);
    }

    private void saveReview(Long sellerId,
                            Long reporterId,
                            Long itemId,
                            PublishItemDTO dto,
                            ViolationSource source,
                            String violationType,
                            String reason,
                            List<String> matchedRules,
                            String ruleVersion) {
        long existing = violationReportMapper.selectCount(new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, itemId)
                .in(ViolationReport::getSource, ViolationSource.LOCAL_RULE, ViolationSource.CORRECTION)
                .eq(ViolationReport::getStatus, ViolationStatus.PENDING));
        if (existing > 0) {
            return;
        }
        ViolationReport report = new ViolationReport();
        report.setUserId(sellerId);
        report.setReporterId(reporterId);
        report.setItemId(itemId);
        report.setOriginalTitle(dto.getTitle().trim());
        report.setOriginalDescription(dto.getDescription().trim());
        report.setSource(source);
        report.setViolationType(violationType);
        report.setViolationReason(reason);
        report.setMatchedRules(List.copyOf(matchedRules));
        report.setRuleVersion(ruleVersion);
        report.setStatus(ViolationStatus.PENDING);
        violationReportMapper.insert(report);
    }

    private Item buildItem(Long publisherId,
                           Long schoolId,
                           PublishItemDTO dto) {
        Item item = new Item();
        item.setPublisherId(publisherId);
        item.setSchoolId(schoolId);
        applyContent(item, dto);
        item.setViewCount(0);
        item.setIsDeleted(false);
        item.setFeedKey(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
        return item;
    }

    private void applyContent(Item item, PublishItemDTO dto) {
        item.setType(ItemType.from(dto.getType()));
        item.setTitle(dto.getTitle().trim());
        item.setDescription(dto.getDescription().trim());
        item.setCategoryId(dto.getCategoryId());
        item.setPrice(normalizePrice(dto));
        item.setImages(List.copyOf(dto.getImages()));
        item.setTradeLocation(trimToNull(dto.getTradeLocation()));
        item.setPickupLocation(trimToNull(dto.getPickupLocation()));
        item.setDeliveryLocation(trimToNull(dto.getDeliveryLocation()));
    }

    private SysUser requirePublisher(Long publisherId) {
        SysUser publisher = userMapper.selectById(publisherId);
        if (publisher == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (publisher.getSchoolId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先设置所属学校");
        }
        return publisher;
    }

    private Item requireOwnedItem(Long userId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        if (!Objects.equals(item.getPublisherId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己发布的商品");
        }
        return item;
    }

    private Category requireCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品分类不存在");
        }
        return category;
    }

    private void assertNotReserved(Long itemId) {
        if (reservationMapper.selectById(itemId) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "商品存在进行中的订单，暂不能修改");
        }
    }

    private void validateImages(List<String> images) {
        for (String image : images) {
            if (!StringUtils.hasText(image) || !image.startsWith("/uploads/items/")) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址必须来自平台上传接口");
            }
        }
    }

    private PublishItemDTO toReviewDTO(Item item) {
        PublishItemDTO dto = new PublishItemDTO();
        dto.setType(item.getType().code());
        dto.setTitle(item.getTitle());
        dto.setDescription(item.getDescription());
        dto.setCategoryId(item.getCategoryId());
        dto.setPrice(item.getPrice());
        dto.setImages(item.getImages());
        dto.setTradeLocation(item.getTradeLocation());
        dto.setPickupLocation(item.getPickupLocation());
        dto.setDeliveryLocation(item.getDeliveryLocation());
        // 重上架复检时保留用户已选标签（视为用户提供的 tags，走同一套清洗与规则审查）
        dto.setTags(itemTagService.tagsByItemIds(java.util.Set.of(item.getId()))
                .getOrDefault(item.getId(), List.of()));
        return dto;
    }

    /**
     * 内容审查汇总 = 正文/地点分析 + 用户自定义标签清洗。
     * dto.tags 为 null 表示调用方未提供（旧客户端），沿用系统生成标签；
     * 非 null（含空数组）以用户选择为准，但必须通过规则审查，命中即整单转人工。
     */
    private ContentCheck checkContent(PublishItemDTO dto, Category category) {
        LocalContentAnalyzer.AnalysisResult analysis = contentAnalyzer.analyze(dto, category);
        if (dto.getTags() == null) {
            return new ContentCheck(analysis.risky(), analysis.reason(), analysis.matchedRules(),
                    analysis.ruleVersion(), analysis.tags());
        }
        LocalContentAnalyzer.TagCheck tagCheck = contentAnalyzer.sanitizeUserTags(dto.getTags());
        List<String> matchedRules = new java.util.ArrayList<>(analysis.matchedRules());
        for (String code : tagCheck.matchedRules()) {
            if (!matchedRules.contains(code)) matchedRules.add(code);
        }
        String reason = java.util.stream.Stream.of(analysis.reason(), tagCheck.reason())
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.joining("；"));
        boolean risky = analysis.risky() || tagCheck.risky();
        List<String> tags = risky ? List.of() : tagCheck.tags();
        return new ContentCheck(risky, reason, matchedRules, analysis.ruleVersion(), tags);
    }

    /** 内容审查汇总结果：tags 为最终应写入的商品标签集合 */
    private record ContentCheck(boolean risky,
                                String reason,
                                List<String> matchedRules,
                                String ruleVersion,
                                List<String> tags) {
    }

    /** 标签建议：按标题与分类生成候选，仅供前端选择，不落库 */
    public List<String> suggestTags(String title, Long categoryId) {
        Category category = categoryId == null ? null : requireCategory(categoryId);
        return contentAnalyzer.suggestTags(title, category);
    }

    private BigDecimal normalizePrice(PublishItemDTO dto) {
        return ItemType.from(dto.getType()) == ItemType.SWAP ? null : dto.getPrice().setScale(2);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ImageFormat validateImageFormat(String originalFilename, String contentType, byte[] signature) {
        ImageFormat filenameFormat = formatFromOriginalFilename(originalFilename);
        ImageFormat contentTypeFormat = formatFromContentType(contentType);
        if (filenameFormat != null && contentTypeFormat != null && filenameFormat != contentTypeFormat) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件扩展名与 Content-Type 不一致");
        }

        ImageFormat declaredFormat = filenameFormat != null ? filenameFormat : contentTypeFormat;
        if (declaredFormat == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无法确定声明的图片格式");
        }

        ImageFormat actualFormat = ImageFormat.detect(signature);
        if (actualFormat == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件内容不是受支持的图片格式");
        }
        if (actualFormat != declaredFormat) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件内容与声明的图片格式不一致");
        }
        return actualFormat;
    }

    private ImageFormat formatFromOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }
        String filename = StringUtils.getFilename(originalFilename);
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        ImageFormat format = ImageFormat.fromExtension(extension);
        if (format == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 jpg、jpeg、png、webp 图片");
        }
        return format;
    }

    private ImageFormat formatFromContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        int parameters = normalized.indexOf(';');
        if (parameters >= 0) {
            normalized = normalized.substring(0, parameters);
        }
        ImageFormat format = ImageFormat.fromContentType(normalized.trim());
        if (format == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "Content-Type 必须是 image/jpeg、image/png 或 image/webp");
        }
        return format;
    }

    private enum ImageFormat {
        JPEG("jpg"),
        PNG("png"),
        WEBP("webp");

        private final String extension;

        ImageFormat(String extension) {
            this.extension = extension;
        }

        String extension() {
            return extension;
        }

        static ImageFormat fromExtension(String extension) {
            return switch (extension) {
                case "jpg", "jpeg" -> JPEG;
                case "png" -> PNG;
                case "webp" -> WEBP;
                default -> null;
            };
        }

        static ImageFormat fromContentType(String contentType) {
            return switch (contentType) {
                case "image/jpeg", "image/jpg", "image/pjpeg" -> JPEG;
                case "image/png" -> PNG;
                case "image/webp" -> WEBP;
                default -> null;
            };
        }

        static ImageFormat detect(byte[] signature) {
            if (signature.length >= 3
                    && signature[0] == (byte) 0xff
                    && signature[1] == (byte) 0xd8
                    && signature[2] == (byte) 0xff) {
                return JPEG;
            }
            if (signature.length >= 8
                    && signature[0] == (byte) 0x89
                    && signature[1] == 0x50
                    && signature[2] == 0x4e
                    && signature[3] == 0x47
                    && signature[4] == 0x0d
                    && signature[5] == 0x0a
                    && signature[6] == 0x1a
                    && signature[7] == 0x0a) {
                return PNG;
            }
            if (signature.length >= 12
                    && signature[0] == 'R'
                    && signature[1] == 'I'
                    && signature[2] == 'F'
                    && signature[3] == 'F'
                    && signature[8] == 'W'
                    && signature[9] == 'E'
                    && signature[10] == 'B'
                    && signature[11] == 'P') {
                return WEBP;
            }
            return null;
        }
    }
}
