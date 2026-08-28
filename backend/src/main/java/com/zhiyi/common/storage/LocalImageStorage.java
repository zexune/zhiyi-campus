package com.zhiyi.common.storage;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 本地图片存储（商品图、用户头像共用）。
 *
 * 安全策略：文件名服务端生成（UUID），不信任客户端文件名；扩展名与
 * Content-Type 声明的格式必须一致，且与文件头魔数（magic bytes）比对，
 * 防止伪装扩展名上传非图片内容。落盘按 {category}/{yyyyMMdd}/ 分桶，
 * 通过 /uploads/** 静态映射对外提供访问。
 */
@Component
public class LocalImageStorage {

    private static final int IMAGE_SIGNATURE_BYTES = 12;
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    /** 桶名是落盘路径段：仅允许小写字母/数字/连字符，杜绝路径穿越 */
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("[a-z0-9-]+");

    @Value("${zhiyi.upload-path:./uploads}")
    private String uploadPath;

    /**
     * 校验并保存图片，返回可直接访问的公开 URL（/uploads/…）。
     *
     * @param category 存储桶名（如 items、avatars）
     * @param maxBytes 单文件大小上限
     */
    public String store(MultipartFile file, String category, long maxBytes) {
        if (!StringUtils.hasText(category) || !CATEGORY_PATTERN.matcher(category).matches()) {
            throw new IllegalArgumentException("非法的存储桶名: " + category);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "单张图片不能超过" + (maxBytes / 1024 / 1024) + "MB");
        }
        String day = LocalDate.now().format(DAY_FORMATTER);
        Path targetDir = Path.of(uploadPath, category, day).toAbsolutePath().normalize();
        try (BufferedInputStream input = new BufferedInputStream(file.getInputStream())) {
            input.mark(IMAGE_SIGNATURE_BYTES);
            byte[] signature = input.readNBytes(IMAGE_SIGNATURE_BYTES);
            ImageFormat imageFormat = validateImageFormat(
                    file.getOriginalFilename(), file.getContentType(), signature);
            input.reset();

            String filename = UUID.randomUUID().toString().replace("-", "")
                    + "." + imageFormat.extension();
            Path target = targetDir.resolve(filename);
            Files.createDirectories(targetDir);
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + category + "/" + day + "/" + filename;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "图片保存失败");
        }
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
        int dot = filename.lastIndexOf('.');
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
