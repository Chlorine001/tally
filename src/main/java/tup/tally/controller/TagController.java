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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tag create(@Valid @RequestBody Tag tag) {
        return tagService.create(tag);
    }

    @PutMapping("/{id}")
    public Tag update(@PathVariable String id, @Valid @RequestBody Tag tag) {
        return tagService.update(id, tag);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        tagService.delete(id);
    }

    @GetMapping("/{id}")
    public Tag getById(@PathVariable String id) {
        Tag tag = tagService.findById(id);
        if (tag == null) {
            throw new RuntimeException("Tag not found: " + id);
        }
        return tag;
    }

    @GetMapping
    public List<Tag> listAll() {
        return tagService.listAll();
    }
}