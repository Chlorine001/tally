package tup.tally.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import tup.tally.entity.Tag;
import tup.tally.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */
@Slf4j
@Service
public class TagServiceImpl implements TagService {

    private static final String METADATA_DIR = "metadata";
    private static final String TAGS_FILE = "tags.json";
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public TagServiceImpl() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Files.createDirectories(Paths.get(METADATA_DIR));
        } catch (IOException e) {
            log.error("Failed to create metadata directory", e);
        }
    }

    private Path getTagsPath() {
        return Paths.get(METADATA_DIR, TAGS_FILE);
    }

    private List<Tag> loadTags() {
        lock.readLock().lock();
        try {
            Path path = getTagsPath();
            if (Files.exists(path)) {
                return objectMapper.readValue(path.toFile(), new TypeReference<>() {});
            } else {
                return new ArrayList<>();
            }
        } catch (IOException e) {
            log.error("Failed to load tags", e);
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    private void saveTags(List<Tag> tags) {
        lock.writeLock().lock();
        try {
            Path path = getTagsPath();
            objectMapper.writeValue(path.toFile(), tags);
        } catch (IOException e) {
            log.error("Failed to save tags", e);
            throw new RuntimeException("Failed to save tags", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Tag create(Tag tag) {
        if (tag.getName() == null || tag.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tag name cannot be empty");
        }
        // 检查唯一性
        if (findByName(tag.getName()) != null) {
            throw new RuntimeException("Tag already exists: " + tag.getName());
        }
        tag.setId(UUID.randomUUID().toString());
        long now = System.currentTimeMillis();
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);
        List<Tag> tags = loadTags();
        tags.add(tag);
        saveTags(tags);
        return tag;
    }

    @Override
    public Tag update(String id, Tag updated) {
        List<Tag> tags = loadTags();
        int index = -1;
        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).getId().equals(id)) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            throw new RuntimeException("Tag not found: " + id);
        }
        Tag existing = tags.get(index);
        // 如果名称改变，检查唯一性
        if (!existing.getName().equals(updated.getName()) && findByName(updated.getName()) != null) {
            throw new RuntimeException("Tag already exists: " + updated.getName());
        }
        existing.setName(updated.getName());
        existing.setColor(updated.getColor());
        existing.setIcon(updated.getIcon());
        existing.setUpdatedAt(System.currentTimeMillis());
        tags.set(index, existing);
        saveTags(tags);
        return existing;
    }

    @Override
    public void delete(String id) {
        List<Tag> tags = loadTags();
        boolean removed = tags.removeIf(t -> t.getId().equals(id));
        if (removed) {
            saveTags(tags);
        } else {
            throw new RuntimeException("Tag not found: " + id);
        }
    }

    @Override
    public Tag findById(String id) {
        return loadTags().stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public Tag findByName(String name) {
        return loadTags().stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null);
    }

    @Override
    public List<Tag> listAll() {
        return loadTags();
    }
}
