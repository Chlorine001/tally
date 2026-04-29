package tup.tally.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tup.tally.service.TagService;
import tup.tally.service.impl.TagServiceImpl;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description 启动时迁移旧的tags数据，CRDT一致
 */
@Component
public class DataMigrationRunner implements CommandLineRunner {

    private final TagServiceImpl tagService;
    private final ObjectMapper objectMapper;

    public DataMigrationRunner(TagServiceImpl tagService, ObjectMapper objectMapper) {
        this.tagService = tagService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        Path legacyTags = Paths.get("metadata/tags.json");
        tagService.migrateFromLegacyFile(legacyTags);
    }
}