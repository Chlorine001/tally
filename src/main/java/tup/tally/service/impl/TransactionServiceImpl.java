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

        YearMonth yearMonth = YearMonth.from(transaction.getDate());
        List<Transaction> transactions = loadTransactions(yearMonth);
        transactions.add(transaction);
        saveTransactions(yearMonth, transactions);
        return transaction;
    }

    @Override
    public Transaction update(String id, Transaction updated) {
        Transaction existing = findById(id);
        if (existing == null) {
            throw new RuntimeException("Transaction not found: " + id);
        }

        // 检查日期是否变更（可能导致需要跨文件移动）
        YearMonth oldMonth = YearMonth.from(existing.getDate());
        YearMonth newMonth = YearMonth.from(updated.getDate());

        // 保留不可变字段
        updated.setId(id);
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setUpdatedAt(System.currentTimeMillis());

        if (oldMonth.equals(newMonth)) {
            // 同月更新：直接替换
            List<Transaction> transactions = loadTransactions(oldMonth);
            int index = findIndexById(transactions, id);
            if (index >= 0) {
                transactions.set(index, updated);
                saveTransactions(oldMonth, transactions);
            } else {
                throw new RuntimeException("Transaction not found in file: " + id);
            }
        } else {
            // 跨月移动：先从旧文件删除，再添加到新文件
            List<Transaction> oldList = loadTransactions(oldMonth);
            oldList.removeIf(t -> t.getId().equals(id));
            saveTransactions(oldMonth, oldList);

            List<Transaction> newList = loadTransactions(newMonth);
            newList.add(updated);
            saveTransactions(newMonth, newList);
        }
        return updated;
    }

    @Override
    public void delete(String id) {
        Transaction existing = findById(id);
        if (existing == null) {
            return;
        }
        YearMonth yearMonth = YearMonth.from(existing.getDate());
        List<Transaction> transactions = loadTransactions(yearMonth);
        boolean removed = transactions.removeIf(t -> t.getId().equals(id));
        if (removed) {
            saveTransactions(yearMonth, transactions);
        }
    }

    @Override
    public Transaction findById(String id) {
        // 遍历所有月份文件查找（小数据量，简单实现，后续可优化）
        File dataDir = new File(DATA_DIR);
        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return null;
        for (File file : files) {
            try {
                List<Transaction> transactions = objectMapper.readValue(file,
                        new TypeReference<List<Transaction>>() {});
                Optional<Transaction> found = transactions.stream()
                        .filter(t -> t.getId().equals(id))
                        .findFirst();
                if (found.isPresent()) {
                    return found.get();
                }
            } catch (IOException e) {
                log.error("Failed to read file: {}", file.getName(), e);
            }
        }
        return null;
    }

    @Override
    public List<Transaction> list(Integer year, Integer month) {
        if (year == null || month == null) {
            YearMonth now = YearMonth.now();
            year = now.getYear();
            month = now.getMonthValue();
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        return loadTransactions(yearMonth);
    }

    @Override
    public List<Transaction> listAll() {
        List<Transaction> all = new ArrayList<>();
        File dataDir = new File(DATA_DIR);
        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return all;
        for (File file : files) {
            try {
                List<Transaction> transactions = objectMapper.readValue(file,
                        new TypeReference<List<Transaction>>() {});
                all.addAll(transactions);
            } catch (IOException e) {
                log.error("Failed to read file: {}", file.getName(), e);
            }
        }
        // 按日期倒序排序
        all.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return all;
    }

    // ========== 私有辅助方法 ==========

    private Path getDataPath(YearMonth yearMonth) {
        String fileName = yearMonth.getYear() + "-" + String.format("%02d", yearMonth.getMonthValue()) + ".json";
        return Paths.get(DATA_DIR, fileName);
    }

    private List<Transaction> loadTransactions(YearMonth yearMonth) {
        fileLock.readLock().lock();
        try {
            Path path = getDataPath(yearMonth);
            if (Files.exists(path)) {
                return objectMapper.readValue(path.toFile(), new TypeReference<List<Transaction>>() {});
            } else {
                return new ArrayList<>();
            }
        } catch (IOException e) {
            log.error("Failed to load transactions for {}", yearMonth, e);
            return new ArrayList<>();
        } finally {
            fileLock.readLock().unlock();
        }
    }

    private void saveTransactions(YearMonth yearMonth, List<Transaction> transactions) {
        fileLock.writeLock().lock();
        try {
            Path path = getDataPath(yearMonth);
            objectMapper.writeValue(path.toFile(), transactions);
        } catch (IOException e) {
            log.error("Failed to save transactions for {}", yearMonth, e);
            throw new RuntimeException("Failed to save data", e);
        } finally {
            fileLock.writeLock().unlock();
        }
    }

    private int findIndexById(List<Transaction> transactions, String id) {
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}