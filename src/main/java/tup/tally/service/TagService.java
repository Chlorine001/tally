package tup.tally.service;
import tools.jackson.databind.ObjectMapper;
import tup.tally.entity.Tag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */
public interface TagService {
    Tag saveTag(Tag tag) throws Exception;
    void deleteTag(String id) throws Exception;

    Tag findById(String id);
    Tag findByName(String name);
    List<Tag> listAll();

    void migrateFromLegacyFile(Path legacyTags) throws IOException;
}