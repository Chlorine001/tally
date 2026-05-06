package tup.tally.controller;

import tup.tally.entity.Tag;
import tup.tally.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    /**
     * 创建新标签
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tag createTag(@Valid @RequestBody Tag tag) throws Exception {
        // 确保没有 id（让服务自动生成）
        tag.setId(null);
        return tagService.saveTag(tag);
    }

    /**
     * 更新标签（全量替换）
     */
    @PutMapping("/{id}")
    public Tag updateTag(@PathVariable String id, @Valid @RequestBody Tag tag) throws Exception {
        tag.setId(id);   // 确保路径 id 被使用
        return tagService.saveTag(tag);
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@PathVariable String id) throws Exception {
        tagService.deleteTag(id);
    }

    /**
     * 根据 ID 获取单个标签
     */
    @GetMapping("/{id}")
    public Tag getTagById(@PathVariable String id) {
        Tag tag = tagService.findById(id);
        if (tag == null) {
            throw new RuntimeException("标签不存在: " + id);
        }
        return tag;
    }

    /**
     * 获取所有标签
     */
    @GetMapping
    public List<Tag> listAllTags() {
        return tagService.listAll();
    }

    /**
     * 根据名称搜索标签（可选）
     */
    @GetMapping("/search")
    public Tag searchTagByName(@RequestParam String name) {
        Tag tag = tagService.findByName(name);
        if (tag == null) {
            throw new RuntimeException("该标签不存在: " + name);
        }
        return tag;
    }

}