package tup.tally.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import tup.tally.entity.crdt.Action;
import tup.tally.entity.crdt.AddAction;
import tup.tally.entity.crdt.DeleteAction;
import tup.tally.entity.crdt.MetaAction;
import tup.tally.entity.crdt.UpdateAction;
import tup.tally.entity.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */

@Slf4j
@Service
public class ActionLogService {

    private static final String ACTIONS_DIR = "actions";
    private static final String META_LOG_FILE = "meta.log";

    private final ObjectMapper objectMapper;
    private final ObjectMapper logMapper;  // 紧凑，用于日志文件
    // 内存状态（最终数据）
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    private final Map<String, Object> metaData = new ConcurrentHashMap<>();

    public ActionLogService() {
        // 用于业务读写（保留 pretty print）
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 专门用于操作日志的 mapper（紧凑，单行）
        this.logMapper = new ObjectMapper();
        this.logMapper.registerModule(new JavaTimeModule());
        this.logMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 不启用 INDENT_OUTPUT，确保输出为一行
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Paths.get(ACTIONS_DIR));
        // 重放所有日志，构建内存状态
        replayAllActions();
    }

    // 获取当前月份日志文件路径
    private Path getLogPath(YearMonth yearMonth) {
        return Paths.get(ACTIONS_DIR, yearMonth.getYear() + "-" + String.format("%02d", yearMonth.getMonthValue()) + ".log");
    }

    // 追加一条操作日志（同步写入文件）
    public synchronized void appendAction(Action action) throws IOException {
        YearMonth ym = YearMonth.from(java.time.Instant.ofEpochMilli(action.getTimestamp()).atZone(java.time.ZoneId.systemDefault()).toLocalDate());
        Path logFile = getLogPath(ym);
        Files.createDirectories(logFile.getParent());
        // 以追加模式写入 JSON 行（每行一个 Action）
//        try (BufferedWriter writer = Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
//            writer.write(objectMapper.writeValueAsString(action));
//            writer.newLine();
//        }
        try (BufferedWriter writer = Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(logMapper.writeValueAsString(action));
            writer.newLine();   // 重要：每条 action 独占一行
            writer.flush();     // 确保立即写入磁盘
        } catch (IOException e) {
            log.error("Failed to append log action", e);
            throw new RuntimeException(e);
        }
        // 立即应用到内存状态
        applyAction(action);
    }

    // 将操作应用到内存状态
    private void applyAction(Action action) {
        if (action instanceof AddAction add) {
            Transaction tx = add.getContent();
            transactions.put(tx.getId(), tx);
        } else if (action instanceof UpdateAction update) {
            Transaction newTx = update.getNewValue();
            // 保留创建时间
            Transaction existing = transactions.get(update.getTargetId());
            if (existing != null) {
                newTx.setCreatedAt(existing.getCreatedAt());
            }
            transactions.put(update.getTargetId(), newTx);
        } else if (action instanceof DeleteAction delete) {
            transactions.remove(delete.getTargetId());
        } else if (action instanceof MetaAction meta) {
            metaData.put(meta.getKey(), meta.getValue());
        }
    }

    // 重放所有历史日志（启动时调用）
    private void replayAllActions() throws IOException {
        // 清空当前状态
        transactions.clear();
        metaData.clear();

        // 1. 读取所有 actions/*.log 文件，按时间戳排序
        List<Action> allActions = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(ACTIONS_DIR), "*.log")) {
            for (Path logFile : stream) {
                if (logFile.getFileName().toString().equals(META_LOG_FILE)) continue;
                try (BufferedReader reader = Files.newBufferedReader(logFile)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        try {
                            Action action = objectMapper.readValue(line, Action.class);
                            allActions.add(action);
                        } catch (Exception e) {
                            log.warn("Skipping invalid action line: {}", line, e);
                        }
                    }
                }
            }
        }
        // 读取 meta.log
        Path metaPath = Paths.get(ACTIONS_DIR, META_LOG_FILE);
        if (Files.exists(metaPath)) {
            try (BufferedReader reader = Files.newBufferedReader(metaPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    Action action = objectMapper.readValue(line, Action.class);
                    allActions.add(action);
                }
            }
        }

        // 按时间戳排序
        allActions.sort(Comparator.comparingLong(Action::getTimestamp));

        // 顺序重放
        for (Action action : allActions) {
            applyAction(action);
        }
        log.info("Replayed {} actions, {} transactions loaded", allActions.size(), transactions.size());
    }

    // 仅重放某个时间范围（用于增量同步）
    public void replayActionsSince(long sinceTimestamp) throws IOException {
        // 类似 replayAllActions 但只读取 timestamp > sinceTimestamp 的操作
        // 省略细节...
    }

    // 对外提供当前状态
    public Map<String, Transaction> getAllTransactions() {
        return new HashMap<>(transactions);
    }

    public Transaction getTransaction(String id) {
        return transactions.get(id);
    }

    public Map<String, Object> getMetaData() {
        return new HashMap<>(metaData);
    }
}