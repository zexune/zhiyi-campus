package com.zhiyi.architecture;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架构守卫（P1-6）：业务 service 不得直接抛 UNAUTHORIZED。
 * Web 请求中的身份由 JwtInterceptor 保证；service 收到 null userId 属于编程错误
 * （IllegalStateException），而不是可以用 401 表达的业务失败——401 会触发前端
 * 清理登录态与幂等键，绝不能由业务层产生。
 *
 * 实现方式：用 JDK ClassFile API 扫描编译产物字节码，匹配
 * GETSTATIC ResultCode.UNAUTHORIZED 后紧跟 INVOKESPECIAL BusinessException.&lt;init&gt;
 * 的抛出序列（覆盖 new BusinessException(UNAUTHORIZED) 与带 message 两种形态）。
 */
class ServiceUnauthorizedThrowTest {

    /** GETSTATIC 与 BusinessException 构造调用之间允许的最大指令数（DUP/LDC 装参）。 */
    private static final int INSTRUCTION_WINDOW = 4;

    @Test
    @DisplayName("业务 service 不得抛 BusinessException(UNAUTHORIZED)")
    void servicesMustNotThrowUnauthorized() throws Exception {
        Path mainClasses = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI())
                .getParent().resolve("classes");
        assertTrue(Files.isDirectory(mainClasses), "找不到编译产物目录：" + mainClasses);

        List<String> violations = new ArrayList<>();
        try (Stream<Path> classFiles = Files.walk(mainClasses)) {
            classFiles.filter(path -> path.toString().endsWith(".class"))
                    .filter(ServiceUnauthorizedThrowTest::isServiceClass)
                    .forEach(path -> violations.addAll(scan(path)));
        }
        assertTrue(violations.isEmpty(),
                "以下 service 直接抛出了 UNAUTHORIZED（应为 IllegalStateException 或拦截器 401）：\n"
                        + String.join("\n", violations));
    }

    private static boolean isServiceClass(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/com/zhiyi/module/") && normalized.contains("/service/");
    }

    private static List<String> scan(Path path) {
        List<String> violations = new ArrayList<>();
        ClassModel model;
        try {
            model = ClassFile.of().parse(path);
        } catch (Exception parseFailure) {
            throw new IllegalStateException("无法解析字节码：" + path, parseFailure);
        }
        for (MethodModel method : model.methods()) {
            method.code().ifPresent(code -> {
                int instructionsSinceUnauthorized = -1;
                for (var element : code) {
                    if (element instanceof FieldInstruction field
                            && field.opcode() == Opcode.GETSTATIC
                            && "com/zhiyi/common/ResultCode".equals(field.owner().asInternalName())
                            && "UNAUTHORIZED".equals(field.name().stringValue())) {
                        instructionsSinceUnauthorized = 0;
                        continue;
                    }
                    if (instructionsSinceUnauthorized >= 0) {
                        instructionsSinceUnauthorized++;
                        if (element instanceof InvokeInstruction invoke
                                && invoke.opcode() == Opcode.INVOKESPECIAL
                                && "com/zhiyi/common/BusinessException".equals(invoke.owner().asInternalName())
                                && invoke.name().stringValue().equals("<init>")) {
                            violations.add(model.thisClass().asInternalName() + "#" + method.methodName().stringValue());
                            instructionsSinceUnauthorized = -1;
                            break;
                        }
                        if (instructionsSinceUnauthorized > INSTRUCTION_WINDOW) {
                            instructionsSinceUnauthorized = -1;
                        }
                    }
                }
            });
        }
        return violations;
    }
}
