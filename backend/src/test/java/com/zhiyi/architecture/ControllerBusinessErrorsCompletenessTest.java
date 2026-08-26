package com.zhiyi.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4-1 契约：每个 Controller operation 都必须显式出现 @BusinessErrors
 * （确认没有特有业务错误时使用空注解）。这样"完全忘记审计"与"审计后为空"
 * 可以区分，OpenAPI 错误契约与 @BusinessErrors 声明保持一一对应。
 */
class ControllerBusinessErrorsCompletenessTest {

    private static final List<Class<? extends java.lang.annotation.Annotation>> MAPPING_ANNOTATIONS = List.of(
            org.springframework.web.bind.annotation.GetMapping.class,
            org.springframework.web.bind.annotation.PostMapping.class,
            org.springframework.web.bind.annotation.PutMapping.class,
            org.springframework.web.bind.annotation.DeleteMapping.class,
            org.springframework.web.bind.annotation.PatchMapping.class,
            org.springframework.web.bind.annotation.RequestMapping.class);

    @Test
    @DisplayName("所有 Controller operation 都显式声明 @BusinessErrors")
    void everyControllerOperationDeclaresBusinessErrors() throws Exception {
        Path mainClasses = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI())
                .getParent().resolve("classes");
        assertTrue(Files.isDirectory(mainClasses), "找不到编译产物目录：" + mainClasses);

        List<String> violations = new ArrayList<>();
        List<Class<?>> controllerClasses = new ArrayList<>();
        try (Stream<Path> classFiles = Files.walk(mainClasses)) {
            classFiles.filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/com/zhiyi/"))
                    .forEach(path -> {
                        try {
                            Class<?> type = Class.forName(toBinaryName(path), false, getClass().getClassLoader());
                            if (type.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class)) {
                                controllerClasses.add(type);
                            }
                        } catch (Throwable ignored) {
                            // 测试专用合成类等无法加载时跳过
                        }
                    });
        }
        assertTrue(controllerClasses.size() >= 15,
                "应扫描到全部 Controller，实际 " + controllerClasses.size());

        for (Class<?> controller : controllerClasses) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isOperation(method)) {
                    continue;
                }
                if (!method.isAnnotationPresent(com.zhiyi.common.annotation.BusinessErrors.class)) {
                    violations.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "以下 operation 缺少 @BusinessErrors 声明（无特有业务错误也需空注解显式审计）：\n"
                        + String.join("\n", violations));
    }

    @Test
    @DisplayName("商品详情、溯源、收藏与举报声明用户删除竞态错误")
    void marketplaceOperationsDeclareUserNotFound() {
        List<String> operations = List.of("detail", "lineage", "favorite", "report");

        for (String operation : operations) {
            Method method = Arrays.stream(com.zhiyi.module.item.controller.ItemController.class
                            .getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(operation))
                    .findFirst()
                    .orElseThrow();
            com.zhiyi.common.annotation.BusinessErrors errors = method.getAnnotation(
                    com.zhiyi.common.annotation.BusinessErrors.class);
            assertTrue(errors != null && Arrays.asList(errors.value()).contains(
                            com.zhiyi.common.ResultCode.USER_NOT_FOUND),
                    operation + " 必须声明 USER_NOT_FOUND");
        }
    }

    private static boolean isOperation(Method method) {
        return MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent);
    }

    private static String toBinaryName(Path path) {
        String s = path.toString().replace('\\', '/');
        int idx = s.indexOf("/com/zhiyi/");
        return s.substring(idx + 1).replaceAll("\\.class$", "").replace('/', '.');
    }
}
