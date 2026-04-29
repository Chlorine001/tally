package tup.tally.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tag {
    private String id;       // UUID
    private String name;     // 标签名称，唯一
    private String color;    // 十六进制颜色，如 "#4CAF50"
    private String icon;     // 可选：emoji 或 图标名
    private Long createdAt;
    private Long updatedAt;
}
