package tup.tally.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-30
 * @Description 创建配置类，提供共享的 tMapper
 */

@Configuration
public class MapperConfig {
    @Bean
    public ObjectMapper objectMapper() {
        // 用于业务读写（保留 pretty print）
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 可以根据需要开启或关闭缩进
        return objectMapper;
    }

    @Bean
    public ObjectMapper actionMapper() {
        // 专门用于action操作日志的 mapper（紧凑，单行）
        ObjectMapper actionMapper = new ObjectMapper();
        actionMapper.registerModule(new JavaTimeModule());
        actionMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 不启用 INDENT_OUTPUT，确保输出为一行
        return actionMapper;
    }
}
