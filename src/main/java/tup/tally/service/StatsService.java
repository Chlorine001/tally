package tup.tally.service;

import org.springframework.stereotype.Service;
import tup.tally.dto.DailyTrendData;
import tup.tally.dto.MonthlyTrendData;
import tup.tally.entity.Tag;
import tup.tally.dto.TagStat;
import tup.tally.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-05-07
 * @Description
 */
@Service
public class StatsService {
    private final ActionLogService actionLogService;
    private final TagService tagService;

    public StatsService(ActionLogService actionLogService, TagService tagService) {
        this.actionLogService = actionLogService;
        this.tagService = tagService;
    }


    // 后端应返回 { months: ["1","2", ...], expense: [...], income: [...] }
    public DailyTrendData getDailyTrend(int year, int month) {
        // 获取该月份的所有交易（按日期过滤）
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<Transaction> transactions = actionLogService.getTransactionsBetween(start, end);
        boolean isNull = false;

        // 获取该月份的总天数
        int daysInMonth = start.lengthOfMonth();
        List<String> days = new ArrayList<>();
        BigDecimal[] income = new BigDecimal[daysInMonth];
        BigDecimal[] expense = new BigDecimal[daysInMonth];
        for (int i = 0; i < daysInMonth; i++) {
            income[i] = BigDecimal.ZERO;
            expense[i] = BigDecimal.ZERO;
        }

        // 填充日期数组
        for (int i = 1; i <= daysInMonth; i++) {
            days.add(String.format("%02d", i));
        }

        // 按天聚合收支
        for (Transaction tx : transactions) {
            isNull = true;
            int day = tx.getDate().getDayOfMonth() - 1;
            if ("income".equals(tx.getType())) {
                income[day] = income[day].add(tx.getAmount());
            } else {
                expense[day] = expense[day].add(tx.getAmount());
            }
        }
        List<String> filteredDays = new ArrayList<>();
        List<BigDecimal> filteredIncome = new ArrayList<>();
        List<BigDecimal> filteredExpense = new ArrayList<>();

        for (int i = 0; i < daysInMonth; i++) {
            if (income[i].compareTo(BigDecimal.ZERO) != 0 || expense[i].compareTo(BigDecimal.ZERO) != 0) {
                filteredDays.add(days.get(i));
                filteredIncome.add(income[i]);
                filteredExpense.add(expense[i]);
            }
        }

        BigDecimal[] incomeArray = filteredIncome.toArray(new BigDecimal[0]);
        BigDecimal[] expenseArray = filteredExpense.toArray(new BigDecimal[0]);


        return isNull ? new DailyTrendData(filteredDays, incomeArray, expenseArray) : null;
    }


    public MonthlyTrendData getMonthlyTrend(int year) {
        return null;
    }


    public List<TagStat> getTagsMonthlyTrend(int year, int month) {
        // 获取该月份的所有交易（按日期过滤）
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<Transaction> transactions = actionLogService.getTransactionsBetween(start, end);

        // 标签支出统计
        Map<String, BigDecimal> tagAmountMap = new HashMap<>();
        for (Transaction tx : transactions) {
            if ("expense".equals(tx.getType())) {
                for (String tagId : tx.getTagIds()) {
                    tagAmountMap.merge(tagId, tx.getAmount(), BigDecimal::add);
                }
            }
        }

        // 获取标签详情
        List<TagStat> tagStats = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : tagAmountMap.entrySet()) {
            Tag tag = tagService.findById(entry.getKey());
            if (tag != null) {
                tagStats.add(new TagStat(entry.getKey(), tag.getName(), entry.getValue(), tag.getColor()));
            } else {
                tagStats.add(new TagStat(entry.getKey(), "未知", entry.getValue(), "#999999"));
            }
        }
        return tagStats;
    }
}