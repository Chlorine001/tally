package tup.tally.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tup.tally.service.GitSyncService;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-30
 * @Description Git 授权控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/git")
public class GithubAuthController {

    @Value("${github.client.id}")
    private String clientId;

    @Value("${github.client.secret}")
    private String clientSecret;

    @Value("${SPRING_APP_API_BASE_URL}")
    private String apiBaseUrl;

    @Value("${VUE_APP_API_BASE_URL}")
    private String frontendBaseUrl;

    private final ObjectMapper objectMapper;

    public GithubAuthController(ObjectMapper objectMapper, GitSyncService gitSyncService) {
        this.objectMapper = objectMapper;
        this.gitSyncService = gitSyncService;
    }

    private final GitSyncService gitSyncService;

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        String redirectUri = apiBaseUrl + "/api/git/callback";
        String url = "https://github.com/login/oauth/authorize?client_id=" + clientId +
                "&redirect_uri=" + redirectUri + "&scope=repo";
        response.sendRedirect(url);
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync(HttpSession session) throws GitAPIException, IOException {
//        String sessionToken = (String) session.getAttribute("github_token");
//        String globalToken = gitSyncService.getToken();
//        log.info("sync: sessionToken={}, globalToken={}", sessionToken, globalToken);
        String token = (String) session.getAttribute("github_token");
//        log.info("sync: sessionToken={}", token);
        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录，请先授权"));
        }
        // 执行同步...
        boolean success = gitSyncService.sync(token);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true, "message", "同步成功"));
        } else {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "同步失败"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session, HttpServletResponse response) throws IOException {
        session.removeAttribute("github_token");
        return ResponseEntity.ok().body(Map.of("success", true, "message", "已退出登录"));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String code, HttpSession session, HttpServletResponse response) throws IOException, InterruptedException {
        // 用 code 换取 access_token
        String token = exchangeCodeForToken(code);
        // 保存 token（例如存入 session 或本地配置文件）
        session.setAttribute("github_token", token);
//        "Authorization successful, you can now close this page."
        String frontendUrl = frontendBaseUrl + "/auth/callback"; // 你的前端回调地址
        response.sendRedirect(frontendUrl);
    }

    private String exchangeCodeForToken(String code) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String body = "client_id=" + clientId +
                "&client_secret=" + clientSecret +
                "&code=" + code +
                "&redirect_uri=" + apiBaseUrl + "/api/git/callback";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://github.com/login/oauth/access_token"))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        // 解析 JSON 获取 access_token
        JsonNode node = objectMapper.readTree(responseBody);
        if (node.has("error")) {
            throw new RuntimeException("GitHub error: " + node.get("error_description").asText());
        }
        if (node.has("access_token")) {
            return node.get("access_token").asText();
        }
        throw new RuntimeException("Unexpected response: " + responseBody);
    }
}