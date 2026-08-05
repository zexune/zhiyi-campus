package com.zhiyi.module.user.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StudentIdNormalizerTest {

    @Test
    void trimsAndLowercases() {
        assertEquals("admin", StudentIdNormalizer.normalize("  AdMiN  "));
    }

    @Test
    void preservesNullForValidation() {
        assertNull(StudentIdNormalizer.normalize(null));
    }
}
