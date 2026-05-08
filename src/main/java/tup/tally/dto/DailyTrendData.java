package tup.tally.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-05-08
 * @Description 每日趋势数据
 */

// 后端应返回 { days: ["2026-04-01","2026-04-02", ...], expense: [...], income: [...] }
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyTrendData {
    private List<String> days;
    private BigDecimal[] income;
    private BigDecimal[] expense;
}
