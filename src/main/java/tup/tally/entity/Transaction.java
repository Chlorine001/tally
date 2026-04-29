package tup.tally.entity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transaction {
    private String id;                 // UUID
    private String type;               // "expense" 或 "income"
    @NotNull(message = "金额不能为空")
    @Positive(message = "金额必须大于0")
    private BigDecimal amount;
    @NotNull(message = "日期不能为空")
    private LocalDate date;             // 记账日期
    private String currency;           // 默认 "CNY"
    private List<String> tags;         // 标签列表
    private String note;               // 备注
    private Long createdAt;            // 毫秒时间戳
    private Long updatedAt;
}