package tup.tally.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */

@Slf4j
@Service
public class GitSyncService {

    @Value("${tally.github.repo-url:}")
    private String repoUrl;

    @Value("${tally.github.token:}")
    private String token;

    @Value("${tally.local.repo-path:./repo_cache}")
    private String localRepoPath;

    private Git git;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() throws Exception {
        Path localPath = Paths.get(localRepoPath);
        if (!Files.exists(localPath)) {
            // 克隆远程仓库
            if (repoUrl != null && !repoUrl.isEmpty()) {
                CloneCommand clone = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(localPath.toFile())
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""));
                git = clone.call();
                log.info("Cloned repository from {}", repoUrl);
            } else {
                // 本地初始化空仓库
                Files.createDirectories(localPath);
                git = Git.init().setDirectory(localPath.toFile()).call();
                log.info("Initialized empty local repository");
            }
        } else {
            // 打开已有仓库
            Repository repo = new FileRepositoryBuilder()
                    .setGitDir(new File(localPath.toFile(), ".git"))
                    .build();
            git = new Git(repo);
            // 拉取最新
            pull();
        }
        // 将 actions/ 目录链接到本地仓库（或直接克隆后已经存在）
        // 确保数据目录在仓库内
        linkDataDirectory();
    }

    private void linkDataDirectory() throws IOException {
        // 将本地的 actions/ 目录和 meta.log 放到 Git 仓库工作目录下
        Path repoDataDir = Paths.get(localRepoPath, "actions");
        Path localDataDir = Paths.get("actions");
        if (!Files.exists(repoDataDir)) {
            // 如果仓库中没有 actions，复制本地已有的
            if (Files.exists(localDataDir)) {
                // 复制目录
                copyDirectory(localDataDir, repoDataDir);
            } else {
                Files.createDirectories(repoDataDir);
            }
        }
        // 创建符号链接或直接使用统一路径？为了简单，我们让 ActionLogService 直接读写 Git 工作目录下的文件
        // 所以修改 ActionLogService 的 ACTIONS_DIR 为 Paths.get(localRepoPath, "actions")
        // 为了不破坏现有结构，可以配置环境变量，或直接重构：所有文件操作都基于 Git 工作目录。
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                log.error("Copy error", e);
            }
        });
    }

    public void commitAndPush(String message) {
        executor.submit(() -> {
            try {
                // 添加所有变更
                git.add().addFilepattern(".").call();
                // 提交
                git.commit().setMessage(message).call();
                // 推送
                if (repoUrl != null && !repoUrl.isEmpty()) {
                    git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, "")).call();
                }
                log.info("Pushed changes: {}", message);
            } catch (Exception e) {
                log.error("Failed to commit/push", e);
            }
        });
    }

    public void pull() {
        try {
            git.pull().setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, "")).call();
            log.info("Pulled latest changes");
            // 拉取后需要重新加载新日志并重放（触发 ActionLogService 的增量重放）
        } catch (Exception e) {
            log.error("Pull failed", e);
        }
    }
}