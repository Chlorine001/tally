package tup.tally.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */

@Slf4j
@Service
public class ActionLogService {

    @Value("${tally.actions.dir:actions}")
    private String actionsDirPath;

    private Path actionsDir;
    private Path metaLogPath;

    private final ObjectMapper objectMapper;
    private final ObjectMapper actionMapper;  // 紧凑，用于日志文件
    // 交易内存状态
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    // 元数据内存状态： key -> 完整值对象
    private final Map<String, Object> metaData = new ConcurrentHashMap<>();
    // 记录每个元数据 key 的最新时间戳，用于 LWW
    private final Map<String, Long> metaTimestamps = new ConcurrentHashMap<>();

    public ActionLogService() {
        // 用于业务读写（保留 pretty print）
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 专门用于action操作日志的 mapper（紧凑，单行）
        this.actionMapper = new ObjectMapper();
        this.actionMapper.registerModule(new JavaTimeModule());
        this.actionMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 不启用 INDENT_OUTPUT，确保输出为一行
    }

    @PostConstruct
    public void init() throws IOException {
        actionsDir = Paths.get(actionsDirPath);
        Files.createDirectories(actionsDir);
        metaLogPath = actionsDir.resolve("meta.log");
        // 重放所有日志，构建内存状态（交易日志 + 元数据日志）
        replayAllActions();
    }

    // 获取指定月份的交易日志路径--修改 getTransactionLogPath 为按日
    private Path getTransactionLogPath(LocalDate date) {
//        .\repo_cache\actions\2026-04.log
//        return actionsDir.resolve(yearMonth.getYear() + "-" + String.format("%02d", yearMonth.getMonthValue()) + ".log");
        return actionsDir.resolve(String.valueOf(date.getYear()))
                .resolve(String.format("%02d", date.getMonthValue()))
                .resolve(date.getDayOfMonth() + ".log");
    }

    // 追加一条操作日志（同步写入文件）
    public synchronized void appendAction(Action action) throws IOException {
        long ts = action.getTimestamp();
        Path targetFile;
        if (action instanceof MetaAction) {
            targetFile = metaLogPath;
        } else {
//            YearMonth ym = YearMonth.from(java.time.Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate());
//            targetFile = getTransactionLogPath(ym);
            LocalDate date = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate();
            targetFile = getTransactionLogPath(date);
        }
        Files.createDirectories(targetFile.getParent());
        // 以追加模式写入 JSON 行（每行一个 Action）
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(actionMapper.writeValueAsString(action));
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
            String key = meta.getKey();
            long ts = meta.getTimestamp();
            // 只更新时间戳更大的操作
            Long lastTs = metaTimestamps.get(key);
            if (lastTs == null || ts > lastTs) {
                metaData.put(key, meta.getValue());
                metaTimestamps.put(key, ts);
                log.info("Meta updated: {} at {}", key, ts);
            } else {
                log.debug("Ignored older meta action for key {} (ts {} <= {})", key, ts, lastTs);
            }
        }
    }

    // 重放所有历史日志（启动时调用）
    private void replayAllActions() throws IOException {
        // 清空当前状态
        transactions.clear();
        metaData.clear();
        metaTimestamps.clear();

        // 读取所有 actions/*.log 文件，按时间戳排序
        List<Action> allActions = new ArrayList<>();
//        try (DirectoryStream<Path> stream = Files.newDirectoryStream(actionsDir, "*.log")) {
//            for (Path logFile : stream) {
//                // 跳过 meta.log，单独处理保证顺序统一
//                if (logFile.getFileName().toString().equals("meta.log")) continue;
//                // 读取所有交易日志文件
//                try (BufferedReader reader = Files.newBufferedReader(logFile)) {
//                    String line;
//                    while ((line = reader.readLine()) != null) {
//                        if (line.trim().isEmpty()) continue;
//                        try {
//                            Action action = actionMapper.readValue(line, Action.class);
//                            allActions.add(action);
//                        } catch (Exception e) {
//                            log.warn("Skipping invalid action line in {}: {}", logFile.getFileName(), line, e);
//                        }
//                    }
//                }
//            }
//        }
        // 递归遍历 actionsDir 下所有 .log 文件，排除 meta.log
        try (Stream<Path> walk = Files.walk(actionsDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".log"))
                    .filter(p -> !p.getFileName().toString().equals("meta.log"))
                    .forEach(logFile -> {
                        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.trim().isEmpty()) continue;
                                try {
                                    Action action = actionMapper.readValue(line, Action.class);
                                    synchronized (allActions) {
                                        allActions.add(action);
                                    }
                                } catch (Exception e) {
                                    log.warn("Skipping invalid action line in {}: {}", logFile, line, e);
                                }
                            }
                        } catch (IOException e) {
                            log.warn("Failed to read log file: {}", logFile, e);
                        }
                    });
        }

        // 读取 meta.log
        if (Files.exists(metaLogPath)) {
            try (BufferedReader reader = Files.newBufferedReader(metaLogPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        Action action = actionMapper.readValue(line, Action.class);
                        allActions.add(action);
                    } catch (Exception e) {
                        log.warn("Skipping invalid meta action line: {}", line, e);
                    }
                }
            }
        }

        // 按时间戳排序
        allActions.sort(Comparator.comparingLong(Action::getTimestamp));

        // 顺序重放
        for (Action action : allActions) {
            applyAction(action);
        }
        log.info("Replayed {} actions, {} transactions, {} meta keys", allActions.size(), transactions.size(), metaData.size());
    }

    // 仅重放某个时间范围（用于增量同步）
    public void replayActionsSince(long sinceTimestamp) throws IOException {
        // 类似 replayAllActions 但只读取 timestamp > sinceTimestamp 的操作
        // 省略细节...
    }

    // 对外提供当前状态-交易查询
    public Map<String, Transaction> getAllTransactions() {
        return new HashMap<>(transactions);
    }

    public Transaction getTransaction(String id) {
        return transactions.get(id);
    }

    // 对外提供元数据查询
    public Map<String, Object> getMetaData() {
        return new HashMap<>(metaData);
    }
    @SuppressWarnings("unchecked")
    public <T> T getMetaValue(String key, Class<T> type) {
        Object value = metaData.get(key);
        if (value == null) return null;
        // 由于 Jackson 反序列化时可能存为 LinkedHashMap，这里尝试类型转换
        if (type.isAssignableFrom(value.getClass())) {
            return type.cast(value);
        }
        // 如果需要更严格的转换，可以用 objectMapper.convertValue
        return objectMapper.convertValue(value, type);
    }
}