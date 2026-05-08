package tup.tally.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-05-07
 * @Description
 */


// tagStats=[TagStat(tagId=8f80b988-8e8d-4434-966e-224f471b0472, tagName=美食, amount=10363.10, color=#ffffff), TagStat(tagId=ca8ad0fa-2b75-46aa-8ec2-c00eed5b4976, tagName=测试, amount=10363.10, color=#1989fa)])

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagStat {
    private String tagId;
    private String tagName;
    private BigDecimal amount;
    private String color;
}
