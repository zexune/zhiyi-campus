package com.zhiyi.common.storage;

import com.zhiyi.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 共享图片存储：魔数校验、格式一致性、分桶落盘与公开 URL 生成。
 * 上传测试自 ItemPublishService 迁移（v3.2 抽取 LocalImageStorage），
 * 商品图与用户头像共用同一校验路径。
 */
class LocalImageStorageTest {

    @TempDir Path uploadDirectory;

    private LocalImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalImageStorage();
        ReflectionTestUtils.setField(storage, "uploadPath", uploadDirectory.toString());
    }

    @Test
    void acceptsImagesWhenMagicNumbersMatchDeclaredFormats() throws IOException {
        byte[] png = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d
        };
        byte[] jpeg = {
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0,
                0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01
        };
        byte[] webp = {
                0x52, 0x49, 0x46, 0x46, 0x04, 0x00, 0x00, 0x00,
                0x57, 0x45, 0x42, 0x50
        };

        String uploadedPng = store("items", "campus.png", "image/png", png);
        String uploadedJpeg = store("items", "campus.jpeg", "image/jpeg", jpeg);
        String uploadedWebp = store("avatars", "campus.webp", "image/webp", webp);

        assertTrue(uploadedPng.startsWith("/uploads/items/"));
        assertTrue(uploadedPng.endsWith(".png"));
        assertTrue(uploadedJpeg.endsWith(".jpg"));
        assertTrue(uploadedWebp.startsWith("/uploads/avatars/"));
        assertTrue(uploadedWebp.endsWith(".webp"));
        try (var files = Files.walk(uploadDirectory)) {
            assertEquals(3L, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void rejectsExecutableContentDisguisedAsJpegBeforeWritingIt() throws IOException {
        byte[] executable = {0x4d, 0x5a, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

        assertThrows(BusinessException.class,
                () -> store("avatars", "avatar.jpg", "image/jpeg", executable));

        try (var files = Files.walk(uploadDirectory)) {
            assertEquals(0L, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void rejectsWhenMagicNumberConflictsWithFilenameAndContentType() {
        byte[] jpeg = {
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0,
                0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01
        };

        assertThrows(BusinessException.class,
                () -> store("avatars", "avatar.png", "image/png", jpeg));
    }

    @Test
    void rejectsWhenFilenameExtensionConflictsWithContentType() {
        byte[] png = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d
        };

        assertThrows(BusinessException.class,
                () -> store("avatars", "avatar.jpg", "image/png", png));
    }

    @Test
    void rejectsEmptyAndOversizedFilesWithoutTouchingDisk() {
        assertThrows(BusinessException.class,
                () -> store("avatars", "empty.png", "image/png", new byte[0]));

        byte[] png = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        assertThrows(BusinessException.class,
                () -> storage.store(new MockMultipartFile("file", "big.png", "image/png", png),
                        "avatars", 1));
    }

    @Test
    void rejectsBucketNamesThatCouldEscapeStorageRoot() {
        byte[] png = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", png);

        assertThrows(IllegalArgumentException.class, () -> storage.store(file, "../etc", 100));
        assertThrows(IllegalArgumentException.class, () -> storage.store(file, "Items", 100));
    }

    private String store(String bucket, String filename, String contentType, byte[] content) {
        return storage.store(
                new MockMultipartFile("file", filename, contentType, content), bucket, 5 << 20);
    }
}
