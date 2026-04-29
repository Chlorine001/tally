package tup.tally.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import tup.tally.entity.Transaction;
import tup.tally.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */

@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService {

    private static final String DATA_DIR = "data";
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock fileLock = new ReentrantReadWriteLock();

    public TransactionServiceImpl() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        // 确保 data 目录存在
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
        } catch (IOException e) {
            log.error("Failed to create data directory", e);
        }
    }

    // ========== 业务方法 ==========
    @Override
    public Transaction add(Transaction transaction) {
        // 填充基础字段
        if (transaction.getId() == null) {
            transaction.setId(UUID.randomUUID().toString());
        }
        long now = System.currentTimeMillis();
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);
        if (transaction.getCurrency() == null) {
            transaction.setCurrency("CNY");
        }
        if (transaction.getDate() == null) {
            transaction.setDate(LocalDate.now());
        }
        if (transaction.getTags() == null) {
            transaction.setTags(new ArrayList<>());
        }

        LocalDate date = transaction.getDate();
        List<Transaction> dayList = loadTransactions(date);
        dayList.add(transaction);
        saveTransactions(date, dayList);
        return transaction;
    }

    @Override
    public Transaction update(String id, Transaction updated) {
        Transaction existing = findById(id);
        if (existing == null) {
            throw new RuntimeException("Transaction not found: " + id);
        }

        LocalDate oldDate = existing.getDate();
        LocalDate newDate = updated.getDate();

        // 保留不可变字段
        updated.setId(id);
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setUpdatedAt(System.currentTimeMillis());

        if (oldDate.equals(newDate)) {
            // 同一天：直接替换
            List<Transaction> dayList = loadTransactions(oldDate);
            int index = findIndexById(dayList, id);
            if (index >= 0) {
                dayList.set(index, updated);
                saveTransactions(oldDate, dayList);
            } else {
                throw new RuntimeException("Transaction not found in day file");
            }
        } else {
            // 跨天移动：从旧日文件删除，添加到新日文件
            List<Transaction> oldDayList = loadTransactions(oldDate);
            oldDayList.removeIf(t -> t.getId().equals(id));
            saveTransactions(oldDate, oldDayList);

            List<Transaction> newDayList = loadTransactions(newDate);
            newDayList.add(updated);
            saveTransactions(newDate, newDayList);
        }
        return updated;
    }

    @Override
    public void delete(String id) {
        Transaction existing = findById(id);
        if (existing == null) {
            return;
        }
        LocalDate date = existing.getDate();
        List<Transaction> dayList = loadTransactions(date);
        boolean removed = dayList.removeIf(t -> t.getId().equals(id));
        if (removed) {
            saveTransactions(date, dayList);
            // 可选：如果当天文件变为空，是否删除文件？暂不删除，保留空数组即可。
        }
    }

    @Override
    public Transaction findById(String id) {
        // 遍历所有交易（性能可接受，个人数据量小）
        List<Transaction> all = loadAllTransactions();
        return all.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public List<Transaction> list(Integer year, Integer month) {
        if (year == null || month == null) {
            YearMonth now = YearMonth.now();
            year = now.getYear();
            month = now.getMonthValue();
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        return loadMonthTransactions(yearMonth);
    }

    @Override
    public List<Transaction> listAll() {
        return loadAllTransactions();
    }

    // ========== 私有辅助方法 ==========

    //路径工具
    private Path getDataPath(LocalDate date) {
        return Paths.get(DATA_DIR,
                String.valueOf(date.getYear()),
                String.format("%02d", date.getMonthValue()),
                String.format("%02d.json", date.getDayOfMonth()));
    }

    // 加载某一天的所有交易
    private List<Transaction> loadTransactions(LocalDate date) {
        fileLock.readLock().lock();
        try {
            Path path = getDataPath(date);
            if (Files.exists(path)) {
                return objectMapper.readValue(path.toFile(), new TypeReference<>() {
                });
            } else {
                return new ArrayList<>();
            }
        } catch (IOException e) {
            log.error("Failed to load transactions for date {}", date, e);
            return new ArrayList<>();
        } finally {
            fileLock.readLock().unlock();
        }
    }

    // 保存某一天的所有交易
    private void saveTransactions(LocalDate date, List<Transaction> transactions) {
        fileLock.writeLock().lock();
        try {
            Path path = getDataPath(date);
            // 确保父目录存在
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), transactions);
        } catch (IOException e) {
            log.error("Failed to save transactions for date {}", date, e);
            throw new RuntimeException("Failed to save data", e);
        } finally {
            fileLock.writeLock().unlock();
        }
    }

    // 加载一个月的所有交易（遍历当月所有日文件）
    private List<Transaction> loadMonthTransactions(YearMonth yearMonth) {
        List<Transaction> all = new ArrayList<>();
        Path monthDir = Paths.get(DATA_DIR,
                String.valueOf(yearMonth.getYear()),
                String.format("%02d", yearMonth.getMonthValue()));
        if (!Files.isDirectory(monthDir)) {
            return all;
        }
        try (Stream<Path> paths = Files.list(monthDir)) {
            List<Path> jsonFiles = paths.filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
            for (Path file : jsonFiles) {
                try {
                    List<Transaction> dayTx = objectMapper.readValue(file.toFile(), new TypeReference<>() {
                    });
                    all.addAll(dayTx);
                } catch (IOException e) {
                    log.error("Failed to read day file: {}", file, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list files for month {}", yearMonth, e);
        }
        return all;
    }

    // 加载所有交易（遍历所有年/月/日文件）
    private List<Transaction> loadAllTransactions() {
        List<Transaction> all = new ArrayList<>();
        File root = new File(DATA_DIR);
        File[] years = root.listFiles(File::isDirectory);
        if (years == null) return all;
        for (File yearDir : years) {
            File[] months = yearDir.listFiles(File::isDirectory);
            if (months == null) continue;
            for (File monthDir : months) {
                File[] dayFiles = monthDir.listFiles((dir, name) -> name.endsWith(".json"));
                if (dayFiles == null) continue;
                for (File dayFile : dayFiles) {
                    try {
                        List<Transaction> dayTx = objectMapper.readValue(dayFile, new TypeReference<>() {
                        });
                        all.addAll(dayTx);
                    } catch (IOException e) {
                        log.error("Failed to read file: {}", dayFile, e);
                    }
                }
            }
        }
        // 按日期倒序排序
        all.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return all;
    }

    // 辅助：从列表中查找索引
    private int findIndexById(List<Transaction> transactions, String id) {
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}