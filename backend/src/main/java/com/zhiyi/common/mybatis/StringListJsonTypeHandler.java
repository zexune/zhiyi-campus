package com.zhiyi.common.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 统一处理 MySQL JSON 字符串数组，杜绝业务层自行拼接或正则解析 JSON。
 */
@MappedTypes(List.class)
@MappedJdbcTypes({JdbcType.VARCHAR, JdbcType.LONGVARCHAR, JdbcType.OTHER})
public final class StringListJsonTypeHandler extends BaseTypeHandler<List<String>> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, JSON.writeValueAsString(parameter));
        } catch (Exception exception) {
            throw new SQLException("无法序列化字符串数组 JSON", exception);
        }
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<String> parse(String raw) throws SQLException {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            List<String> values = JSON.readValue(raw, STRING_LIST);
            return values == null ? List.of() : List.copyOf(values);
        } catch (Exception exception) {
            throw new SQLException("数据库中的字符串数组 JSON 格式无效", exception);
        }
    }
}
