package tup.tally.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import tup.tally.entity.Tag;
import tup.tally.entity.crdt.MetaAction;
import tup.tally.service.ActionLogService;
import tup.tally.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */
@Slf4j
@Service
public class TagServiceImpl implements TagService {

    private final ActionLogService actionLogService;
    private final ObjectMapper objectMapper;  // 用于转换和迁移
    private static final String TAGS_KEY = "tags";

    // 使用构造器注入，让 Spring 提供 ActionLogService 的 bean
    public TagServiceImpl(ActionLogService actionLogService) {
        this.actionLogService = actionLogService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Tag saveTag(Tag tag) throws Exception {
        // 参数校验
        if (tag.getName() == null || tag.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tag name cannot be empty");
        }
        // 新建或更新
        boolean isNew = (tag.getId() == null);
        if (isNew) {
            tag.setId(UUID.randomUUID().toString());
            tag.setCreatedAt(System.currentTimeMillis());
        } else {
            Tag existing = findById(tag.getId());
            if (existing == null) {
                throw new RuntimeException("Tag not found: " + tag.getId());
            }
            tag.setCreatedAt(existing.getCreatedAt());
        }
        tag.setUpdatedAt(System.currentTimeMillis());

        // 唯一性校验（排除自身）
        Tag conflict = findByName(tag.getName());
        if (conflict != null && !conflict.getId().equals(tag.getId())) {
            throw new RuntimeException("Tag name already exists: " + tag.getName());
        }

        // 获取当前所有标签（Map形式）
        Map<String, Tag> current = getAllTagsMap();
        current.put(tag.getId(), tag);

        // 生成 MetaAction 并追加
        MetaAction action = new MetaAction();
        action.setKey(TAGS_KEY);
        action.setValue(current);
        actionLogService.appendAction(action);

        return tag;
    }

    @Override
    public void deleteTag(String id) throws Exception {
        Map<String, Tag> current = getAllTagsMap();
        if (current.remove(id) == null) {
            throw new RuntimeException("Tag not found: " + id);
        }
        MetaAction action = new MetaAction();
        action.setKey(TAGS_KEY);
        action.setValue(current);
        actionLogService.appendAction(action);
    }

    @Override
    public Tag findById(String id) {
        return getAllTagsMap().get(id);
    }

    @Override
    public Tag findByName(String name) {
        return getAllTagsMap().values().stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Tag> listAll() {
        return new ArrayList<>(getAllTagsMap().values());
    }

    // 从 ActionLogService 中获取当前标签 Map，处理可能的类型转换
    @SuppressWarnings("unchecked")
    private Map<String, Tag> getAllTagsMap() {
        Object raw = actionLogService.getMetaData().get(TAGS_KEY);
        if (raw == null) {
            return new HashMap<>();
        }
        // 如果已经是 Map<String, Tag> 则直接返回
        if (raw instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) raw;
            Map<String, Tag> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof Tag) {
                    result.put((String) entry.getKey(), (Tag) entry.getValue());
                } else if (entry.getValue() instanceof Map) {
                    // 因 Jackson 反序列化后可能是 LinkedHashMap，需要转换
                    Tag tag = objectMapper.convertValue(entry.getValue(), Tag.class);
                    result.put((String) entry.getKey(), tag);
                }
            }
            return result;
        }
        return new HashMap<>();
    }


    // 迁移旧文件（可选，在启动时调用）
    public void migrateFromLegacyFile(Path legacyPath) throws IOException {
        if (Files.exists(legacyPath) && !getAllTagsMap().isEmpty()) {
            List<Tag> tags = objectMapper.readValue(legacyPath.toFile(), new TypeReference<List<Tag>>() {
            });
            Map<String, Tag> tagMap = tags.stream().collect(Collectors.toMap(Tag::getId, t -> t));
            MetaAction action = new MetaAction();
            action.setKey(TAGS_KEY);
            action.setValue(tagMap);
            actionLogService.appendAction(action);
            Files.move(legacyPath, legacyPath.resolveSibling("tags.json.back"));
            log.info("Migrated legacy tags to CRDT");
        }
    }
}
