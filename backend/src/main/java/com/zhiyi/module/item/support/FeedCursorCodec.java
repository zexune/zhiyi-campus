package com.zhiyi.module.item.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyi.module.item.vo.FeedCursorState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Feed 签名游标编解码器。
 *
 * 游标绑定：规范化筛选哈希、用户 profile_version、快照上界（最大 item id 与
 * listing_revision）、排序代码、当前层级与 keyset 边界、首屏估算 total 与过期时间。
 * HMAC-SHA256 签名防篡改；过期或签名不匹配一律 FEED_CURSOR_INVALID（从首屏重启）。
 * 客户端不得解析、修改或自行构造游标。
 */
@Component
public class FeedCursorCodec {

    private final byte[] secret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FeedCursorCodec(@Value("${zhiyi.feed.cursor-secret:${zhiyi.jwt.secret}}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String encode(FeedCursorState state) {
        try {
            String body = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(state));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(hmac(body));
            return body + "." + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("feed cursor encode failed", exception);
        }
    }

    /** 签名不匹配返回 null（调用方映射为 FEED_CURSOR_INVALID）；过期由调用方判定。 */
    public FeedCursorState decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        int dot = cursor.lastIndexOf('.');
        if (dot <= 0 || dot == cursor.length() - 1) {
            return null;
        }
        String body = cursor.substring(0, dot);
        String signature = cursor.substring(dot + 1);
        if (!MessageDigest.isEqual(hmac(body),
                Base64.getUrlDecoder().decode(signature))) {
            return null;
        }
        try {
            return objectMapper.readValue(Base64.getUrlDecoder().decode(body), FeedCursorState.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private byte[] hmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("feed cursor hmac failed", exception);
        }
    }
}
