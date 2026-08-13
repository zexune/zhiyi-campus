package com.zhiyi.testsupport;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * Initializes the MyBatis-Plus metadata required by lambda wrappers in isolated unit tests.
 * Production obtains this metadata from the MyBatis application context; mapper mocks do not.
 */
public final class MybatisMetadata {

    private MybatisMetadata() {
    }

    public static synchronized void initialize(Class<?> entityType, Class<?> mapperType) {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "unit-test");
        assistant.setCurrentNamespace(mapperType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
