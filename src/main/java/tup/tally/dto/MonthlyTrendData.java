package tup.tally.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-05-07
 * @Description 每月趋势数据
 */

// 后端应返回 { months: ["1月", ...], expense: [...], income: [...] }
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyTrendData {
    private List<String> months;
    private BigDecimal[] income;
    private BigDecimal[] expense;
}
