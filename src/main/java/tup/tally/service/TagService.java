package tup.tally.service;
import tup.tally.entity.Tag;
import java.util.List;
/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */
public interface TagService {
    Tag create(Tag tag);
    Tag update(String id, Tag tag);
    void delete(String id);
    Tag findById(String id);
    Tag findByName(String name);
    List<Tag> listAll();
}