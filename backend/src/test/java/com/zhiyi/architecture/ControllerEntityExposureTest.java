package com.zhiyi.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P8 架构守卫：Controller 返回类型的泛型树中不得出现
 * {@code com.zhiyi.*.entity.*} 或 MyBatis-Plus {@code IPage}——
 * 持久化实体与 ORM 分页类型只能停留在 service 层，公开边界一律 DTO/VO。
 */
class ControllerEntityExposureTest {

    @Test
    @DisplayName("Controller 返回泛型树不泄漏 entity 与 IPage")
    void controllersMustNotExposeEntitiesOrIPage() throws Exception {
        Path mainClasses = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI())
                .getParent().resolve("classes");
        assertTrue(Files.isDirectory(mainClasses), "找不到编译产物目录：" + mainClasses);

        List<String> violations = new ArrayList<>();
        List<Class<?>> controllerClasses = new ArrayList<>();
        try (Stream<Path> classFiles = Files.walk(mainClasses)) {
            classFiles.filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/com/zhiyi/"))
                    .forEach(path -> {
                        String binaryName = toBinaryName(path);
                        try {
                            Class<?> type = Class.forName(binaryName, false, getClass().getClassLoader());
                            if (type.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class)) {
                                controllerClasses.add(type);
                            }
                        } catch (Throwable ignored) {
                            // 无法加载的合成类跳过
                        }
                    });
        }

        for (Class<?> controller : controllerClasses) {
            for (var method : controller.getDeclaredMethods()) {
                collectViolatingTypes(method.getGenericReturnType(), method, violations);
                for (Type param : method.getGenericParameterTypes()) {
                    collectViolatingTypes(param, method, violations);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "以下位置泄漏了 entity/IPage 类型到 Controller 边界：\n" + String.join("\n", violations));
    }

    private static void collectViolatingTypes(Type type, Method method, List<String> violations) {
        if (type == null) {
            return;
        }
        if (type instanceof Class<?> clazz) {
            if (isForbidden(clazz)) {
                violations.add(describe(method) + " → " + clazz.getName());
            }
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            if (parameterized.getRawType() instanceof Class<?> raw && isForbidden(raw)) {
                violations.add(describe(method) + " → " + raw.getName());
            }
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectViolatingTypes(argument, method, violations);
            }
        }
    }

    private static boolean isForbidden(Class<?> clazz) {
        return clazz.getName().startsWith("com.zhiyi.")
                && clazz.getPackageName().contains(".entity.")
                || clazz.getName().equals("com.baomidou.mybatisplus.core.metadata.IPage");
    }

    private static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }

    private static String toBinaryName(Path path) {
        String s = path.toString().replace('\\', '/');
        int idx = s.indexOf("/com/zhiyi/");
        return s.substring(idx + 1).replaceAll("\\.class$", "").replace('/', '.');
    }
}
