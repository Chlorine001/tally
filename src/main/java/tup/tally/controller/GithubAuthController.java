package tup.tally.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tup.tally.service.GitSyncService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-30
 * @Description Git 授权控制器
 */
@Slf4j
@RestController
@RequestMapping("/auth/github")
public class GithubAuthController {

    @Value("${github.client.id}")
    private String clientId;

    @Value("${github.client.secret}")
    private String clientSecret;

    @Value("${VUE_APP_API_BASE_URL}")
    private String apiBaseUrl;

    private final ObjectMapper objectMapper;

    public GithubAuthController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        String redirectUri = apiBaseUrl + "/auth/github/callback";
        String url = "https://github.com/login/oauth/authorize?client_id=" + clientId +
                "&redirect_uri=" + redirectUri + "&scope=repo";
        response.sendRedirect(url);
    }

    @GetMapping("/callback")
    public String callback(@RequestParam String code, HttpSession session) throws IOException, InterruptedException {
        // 用 code 换取 access_token
        String token = exchangeCodeForToken(code);
        // 保存 token（例如存入 session 或本地配置文件）
        session.setAttribute("github_token", token);
        // 同时写入 application.properties 或内存中的全局变量，供 GitSyncService 使用
        GitSyncService.setGlobalToken(token);
        return "Authorization successful, you can now close this page.";
    }

    private String exchangeCodeForToken(String code) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String body = "client_id=" + clientId +
                "&client_secret=" + clientSecret +
                "&code=" + code +
                "&redirect_uri=" + apiBaseUrl + "/auth/github/callback";

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