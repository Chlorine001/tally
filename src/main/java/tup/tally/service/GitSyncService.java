package tup.tally.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private static String dynamicToken = null;

    private static volatile String token = null;
    public static void setGlobalToken(String token) {
        dynamicToken = token;
    }

    public static String getToken() {
        return dynamicToken != null ? dynamicToken : token; // token 来自配置文件的 fallback
    }

    @Value("${tally.github.repo-url:}")
    private String repoUrl;

    @Value("${tally.local.repo-path:./repo_cache}")
    private String localRepoPath;

    private Git git;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActionLogService actionLogService;

    public GitSyncService(ActionLogService actionLogService) {
        this.actionLogService = actionLogService;
    }

    @PostConstruct
    public void init() throws Exception {
        // 如果 dynamicToken 和静态 token 都为空，则提示用户去授权
        if (getToken() == null) {
            log.warn("GitHub token not configured. Please visit /auth/github/login to authorize.");
            // 不初始化 Git，等待授权完成后重新初始化
            return;
        }
        Path localPath = Paths.get(localRepoPath);
        if (!Files.exists(localPath)) {
            // 克隆远程仓库
            if (repoUrl != null && !repoUrl.isEmpty()) {
                CloneCommand clone = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(localPath.toFile())
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(getToken(), ""));
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
//            pull();
        }
        // 确保 actions 目录存在（可能为空）
        Path actionsDir = localPath.resolve("actions");
        Files.createDirectories(actionsDir);

        // 通知 ActionLogService 加载日志
        actionLogService.loadLogs();
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
                    git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(getToken(), "")).call();
                }
                log.info("Pushed changes: {}", message);
            } catch (Exception e) {
                log.error("Failed to commit/push", e);
            }
        });
    }

    public void pull() {
        try {
            git.pull()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(getToken(), ""))
                    .call();
            log.info("Pulled latest changes");
        } catch (InvalidConfigurationException e) {
            log.error("Pull failed due to Git configuration error: {}", e.getMessage());
            // 提示用户运行 'git config --global user.name/email'
            throw new RuntimeException("Please configure git user.name and user.email", e);
        } catch (Exception e) {
            log.error("Pull failed", e);
        }
    }
}

