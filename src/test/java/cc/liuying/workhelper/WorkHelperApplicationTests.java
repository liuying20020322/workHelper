package cc.liuying.workhelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:workhelper;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "app.time-zone=Asia/Shanghai"
})
class WorkHelperApplicationTests {
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach void clean() { jdbc.update("DELETE FROM job_application"); }

    @Test void fullCrudKeepsSameCompanyApplicationsIndependent() throws Exception {
        var first = send("POST", "/api/applications", Map.of("companyName", "  示例公司  ", "positionName", "Java 开发", "location", "深圳", "requirements", "第一行\n第二行", "appliedAt", "2026-10-07T10:30:00"));
        assertThat(first.statusCode()).isEqualTo(201);
        JsonNode created = json.readTree(first.body());
        long id = created.get("id").asLong();
        assertThat(created.get("companyName").asString()).isEqualTo("示例公司");
        assertThat(created.get("currentStage").asString()).isEqualTo("APPLIED");
        assertThat(created.get("requirements").asString()).isEqualTo("第一行\n第二行");
        var second = send("POST", "/api/applications", Map.of("companyName", "示例公司", "positionName", "Java 开发", "location", "上海"));
        long secondId = json.readTree(second.body()).get("id").asLong();
        assertThat(secondId).isNotEqualTo(id);
        assertThat(json.readTree(send("GET", "/api/applications", null).body()).size()).isEqualTo(2);
        var updated = send("PUT", "/api/applications/" + id, Map.of("companyName", "另一家公司", "positionName", "后端", "notes", "复盘\n补充"));
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(json.readTree(updated.body()).get("appliedAt").asString()).isEqualTo("2026-10-07T10:30:00");
        assertThat(json.readTree(send("GET", "/api/applications/" + secondId, null).body()).get("location").asString()).isEqualTo("上海");
        assertThat(send("DELETE", "/api/applications/" + id, null).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/applications/" + id, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/applications/" + secondId, null).statusCode()).isEqualTo(200);
    }

    @Test void searchIsLiteralAndStageIsServerOwned() throws Exception {
        send("POST", "/api/applications", Map.of("companyName", "100%_科技", "positionName", "Java", "location", "北京", "currentStage", "OFFER"));
        send("POST", "/api/applications", Map.of("companyName", "其他", "positionName", "设计"));
        for (String keyword : new String[]{"%_", "Java", "北京"}) {
            var result = json.readTree(send("GET", "/api/applications?search=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8), null).body());
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get(0).get("currentStage").asString()).isEqualTo("APPLIED");
        }
        assertThat(json.readTree(send("GET", "/api/applications?stage=APPLIED", null).body()).size()).isEqualTo(2);
        assertThat(json.readTree(send("GET", "/api/applications?stage=OFFER", null).body()).size()).isZero();
        assertThat(send("GET", "/api/applications?stage=unknown", null).statusCode()).isEqualTo(400);
    }

    @Test void invalidInputCannotOverwriteStoredRecord() throws Exception {
        var valid = send("POST", "/api/applications", Map.of("companyName", "原公司", "positionName", "原岗位"));
        long id = json.readTree(valid.body()).get("id").asLong();
        for (var input : java.util.List.of(
                Map.of("companyName", " ", "positionName", "开发"),
                Map.of("companyName", "公司", "positionName", "开发", "jobUrl", "javascript:alert(1)"),
                Map.of("companyName", "公司", "positionName", "开发", "appliedAt", "not-a-date"),
                Map.of("companyName", "a".repeat(201), "positionName", "开发"))) {
            var response = send("PUT", "/api/applications/" + id, input);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).get("message").asString()).isNotBlank();
        }
        assertThat(json.readTree(send("GET", "/api/applications/" + id, null).body()).get("companyName").asString()).isEqualTo("原公司");
        assertThat(send("DELETE", "/api/applications/999999", null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/applications/not-a-number", null).statusCode()).isEqualTo(400);
    }

    @Test void healthAndBusinessTimezoneAreAvailable() throws Exception {
        assertThat(send("GET", "/actuator/health", null).statusCode()).isEqualTo(200);
        var info = json.readTree(send("GET", "/api/info", null).body());
        assertThat(info.get("application").asString()).isEqualTo("workHelper");
        assertThat(info.get("timeZone").asString()).isEqualTo("Asia/Shanghai");
    }

    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path))
                .header("Content-Type", "application/json").method(method, publisher).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
