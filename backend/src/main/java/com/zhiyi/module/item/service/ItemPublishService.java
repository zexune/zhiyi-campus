package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

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

/**
 * 商品发布、整改、重新上架、举报与本地内容检测。
 */
@Service
@RequiredArgsConstructor
public class ItemPublishService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
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
    private final JsonMapper objectMapper;
    private final LocalContentAnalyzer contentAnalyzer;

    @Value("${zhiyi.upload-path:./uploads}")
    private String uploadPath;

    public UploadImageVO uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单张图片不能超过5MB");
        }
        String extension = extensionOf(file.getOriginalFilename(), file.getContentType());
        String day = LocalDate.now().format(DAY_FORMATTER);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetDir = Path.of(uploadPath, "items", day).toAbsolutePath().normalize();
        Path target = targetDir.resolve(filename).normalize();
        if (!target.startsWith(targetDir)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件名非法");
        }
        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "图片保存失败");
        }
        return new UploadImageVO("/uploads/items/" + day + "/" + filename);
    }

    @Transactional
    public ItemCardVO publish(Long publisherId, PublishItemDTO dto) {
        SysUser publisher = requirePublisher(publisherId);
        Category category = requireCategory(dto.getCategoryId());
        validateImages(dto.getImages());

        LocalContentAnalyzer.AnalysisResult analysis = contentAnalyzer.analyze(dto, category);
        Item item = buildItem(publisherId, publisher.getSchoolId(), dto, analysis.tags());
        item.setStatus("ON_SALE");
        item.setModerationStatus(analysis.risky() ? "PENDING" : "PASSED");
        itemMapper.insert(item);
        if (analysis.risky()) {
            saveReview(publisherId, null, item.getId(), dto, "LOCAL_RULE", "KEYWORD_MATCH",
                    analysis.reason(), analysis.matchedRules(), analysis.ruleVersion());
        }
        return marketplaceService.getSnapshot(item.getId(), publisherId);
    }

    @Transactional
    public ItemCardVO update(Long publisherId, Long itemId, PublishItemDTO dto) {
        Item item = requireOwnedItem(publisherId, itemId);
        assertNotReserved(itemId);
        if ("SOLD".equals(item.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已售出的商品不能编辑");
        }
        if ("PENDING".equals(item.getModerationStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品正在审核中，暂不能重复修改");
        }

        Category category = requireCategory(dto.getCategoryId());
        validateImages(dto.getImages());
        boolean correction = "REJECTED".equals(item.getModerationStatus());
        LocalContentAnalyzer.AnalysisResult analysis = contentAnalyzer.analyze(dto, category);
        applyContent(item, dto, analysis.tags());

        if (correction) {
            item.setStatus("OFF_SHELF");
            item.setModerationStatus("PENDING");
            itemMapper.updateById(item);
            String reason = analysis.risky()
                    ? analysis.reason()
                    : "卖家已提交整改内容，等待管理员复核";
            saveReview(publisherId, null, itemId, dto, "CORRECTION", "CORRECTION_REVIEW",
                    reason, analysis.matchedRules(), analysis.ruleVersion());
        } else if (analysis.risky()) {
            item.setModerationStatus("PENDING");
            itemMapper.updateById(item);
            saveReview(publisherId, null, itemId, dto, "LOCAL_RULE", "KEYWORD_MATCH",
                    analysis.reason(), analysis.matchedRules(), analysis.ruleVersion());
        } else {
            item.setModerationStatus("PASSED");
            itemMapper.updateById(item);
        }
        return marketplaceService.getSnapshot(itemId, publisherId);
    }

    @Transactional
    public ItemCardVO relist(Long publisherId, Long itemId) {
        Item item = requireOwnedItem(publisherId, itemId);
        assertNotReserved(itemId);
        if (!"OFF_SHELF".equals(item.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已下架商品可以重新上架");
        }
        if ("PENDING".equals(item.getModerationStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品正在审核中");
        }
        if ("REJECTED".equals(item.getModerationStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该商品因内容违规下架，请先整改或申诉");
        }

        Category category = requireCategory(item.getCategoryId());
        PublishItemDTO dto = toReviewDTO(item);
        LocalContentAnalyzer.AnalysisResult analysis = contentAnalyzer.analyze(dto, category);
        item.setTags(toJson(analysis.tags()));
        if (analysis.risky()) {
            item.setModerationStatus("PENDING");
            itemMapper.updateById(item);
            saveReview(publisherId, null, itemId, dto, "LOCAL_RULE", "KEYWORD_MATCH",
                    analysis.reason(), analysis.matchedRules(), analysis.ruleVersion());
        } else {
            item.setStatus("ON_SALE");
            item.setModerationStatus("PASSED");
            itemMapper.updateById(item);
        }
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
                .eq(ViolationReport::getSource, "USER_REPORT")
                .eq(ViolationReport::getStatus, "PENDING"));
        if (existing > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "你已经举报过该商品，管理员正在处理");
        }

        ViolationReport report = new ViolationReport();
        report.setUserId(item.getPublisherId());
        report.setReporterId(reporterId);
        report.setItemId(itemId);
        report.setOriginalTitle(item.getTitle());
        report.setOriginalDescription(item.getDescription());
        report.setSource("USER_REPORT");
        report.setViolationType(dto.type());
        String label = REPORT_LABELS.getOrDefault(dto.type(), "其他问题");
        report.setViolationReason(StringUtils.hasText(dto.details())
                ? label + "：" + dto.details().trim()
                : label);
        report.setMatchedRules("[]");
        report.setRuleVersion(null);
        report.setStatus("PENDING");
        violationReportMapper.insert(report);
    }

    private void saveReview(Long sellerId,
                            Long reporterId,
                            Long itemId,
                            PublishItemDTO dto,
                            String source,
                            String violationType,
                            String reason,
                            List<String> matchedRules,
                            String ruleVersion) {
        long existing = violationReportMapper.selectCount(new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, itemId)
                .in(ViolationReport::getSource, "LOCAL_RULE", "CORRECTION")
                .eq(ViolationReport::getStatus, "PENDING"));
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
        report.setMatchedRules(toJson(matchedRules));
        report.setRuleVersion(ruleVersion);
        report.setStatus("PENDING");
        violationReportMapper.insert(report);
    }

    private Item buildItem(Long publisherId,
                           Long schoolId,
                           PublishItemDTO dto,
                           List<String> tags) {
        Item item = new Item();
        item.setPublisherId(publisherId);
        item.setSchoolId(schoolId);
        applyContent(item, dto, tags);
        item.setViewCount(0);
        item.setIsDeleted(false);
        return item;
    }

    private void applyContent(Item item, PublishItemDTO dto, List<String> tags) {
        item.setType(dto.getType());
        item.setTitle(dto.getTitle().trim());
        item.setDescription(dto.getDescription().trim());
        item.setCategoryId(dto.getCategoryId());
        item.setPrice(normalizePrice(dto));
        item.setImages(toJson(dto.getImages()));
        item.setTags(toJson(tags));
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
        dto.setType(item.getType());
        dto.setTitle(item.getTitle());
        dto.setDescription(item.getDescription());
        dto.setCategoryId(item.getCategoryId());
        dto.setPrice(item.getPrice());
        dto.setImages(parseJsonArray(item.getImages()));
        dto.setTradeLocation(item.getTradeLocation());
        dto.setPickupLocation(item.getPickupLocation());
        dto.setDeliveryLocation(item.getDeliveryLocation());
        return dto;
    }

    private List<String> parseJsonArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JacksonException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "商品图片数据格式错误");
        }
    }

    private BigDecimal normalizePrice(PublishItemDTO dto) {
        return "SWAP".equals(dto.getType()) ? null : dto.getPrice().setScale(2);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "JSON 序列化失败");
        }
    }

    private String extensionOf(String originalFilename, String contentType) {
        String ext = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        if (!List.of("jpg", "jpeg", "png", "webp").contains(ext)) {
            if ("image/jpeg".equalsIgnoreCase(contentType)) ext = "jpg";
            else if ("image/png".equalsIgnoreCase(contentType)) ext = "png";
            else if ("image/webp".equalsIgnoreCase(contentType)) ext = "webp";
        }
        if (!List.of("jpg", "jpeg", "png", "webp").contains(ext)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 jpg、png、webp 图片");
        }
        return "jpeg".equals(ext) ? "jpg" : ext;
    }
}
