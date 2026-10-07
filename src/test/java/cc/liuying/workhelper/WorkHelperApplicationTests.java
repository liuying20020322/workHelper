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

    @Test void processProgressNeverRegressesOnBackfillAndCancellationPreservesStage() throws Exception {
        long app = newApplication();
        String path = "/api/applications/" + app + "/processes";
        long second = createProcess(path, process("INTERVIEW_2", "2026-10-07T10:00:00"));
        long first = createProcess(path, process("INTERVIEW_1", "2026-10-01T10:00:00"));
        assertStage(app, "INTERVIEW_2");
        assertThat(send("PATCH", path + "/" + second + "/status", Map.of("status", "CANCELLED")).statusCode()).isEqualTo(200);
        assertStage(app, "INTERVIEW_2");
        assertThat(send("PATCH", path + "/" + first + "/status", Map.of("status", "COMPLETED")).statusCode()).isEqualTo(200);
        assertStage(app, "INTERVIEW_2");
        assertThat(json.readTree(send("GET", path, null).body()).size()).isEqualTo(2);
        assertThat(send("DELETE", path + "/" + second, null).statusCode()).isEqualTo(204);
        assertStage(app, "INTERVIEW_1");
        assertThat(send("DELETE", path + "/" + first, null).statusCode()).isEqualTo(204);
        assertStage(app, "APPLIED");
    }

    @Test void resultsUseOccurrenceTimeAndEditsAndDeletionRecalculate() throws Exception {
        long app = newApplication();
        String path = "/api/applications/" + app + "/processes";
        createProcess(path, process("INTERVIEW_3", "2026-10-09T10:00:00"));
        long offer = createProcess(path, process("OFFER", "2026-10-07T10:00:00"));
        long rejection = createProcess(path, process("REJECTED", "2026-10-06T10:00:00"));
        assertStage(app, "OFFER");
        assertThat(send("PUT", path + "/" + rejection, process("REJECTED", "2026-10-08T10:00:00")).statusCode()).isEqualTo(200);
        assertStage(app, "REJECTED");
        long withdrawn = createProcess(path, process("WITHDRAWN", "2026-10-08T10:00:00"));
        assertStage(app, "WITHDRAWN"); // Same timestamp: larger ID wins deterministically.
        send("DELETE", path + "/" + withdrawn, null);
        send("DELETE", path + "/" + rejection, null);
        assertStage(app, "OFFER");
        send("DELETE", path + "/" + offer, null);
        assertStage(app, "INTERVIEW_3");
        assertThat(json.readTree(send("GET", "/api/applications?stage=INTERVIEW_3", null).body()).size()).isEqualTo(1);
    }

    @Test void scheduleValidationReschedulingAndModeSwitchAreAtomic() throws Exception {
        long app = newApplication();
        String path = "/api/applications/" + app + "/processes";
        var input = process("WRITTEN_TEST", "2026-10-07T10:00:00");
        input.put("timeMode", "SCHEDULED");
        assertThat(send("POST", path, input).statusCode()).isEqualTo(400);
        assertStage(app, "APPLIED");
        input.put("startAt", "2026-10-08T14:00:00");
        input.put("endAt", "2026-10-08T13:00:00");
        assertThat(send("POST", path, input).statusCode()).isEqualTo(400);
        input.put("endAt", "2026-10-08T15:00:00");
        input.put("notes", "提前准备\n带好材料");
        long id = createProcess(path, input);
        input.put("startAt", "2026-10-09T14:00:00");
        input.put("endAt", "2026-10-09T15:00:00");
        assertThat(send("PUT", path + "/" + id, input).statusCode()).isEqualTo(200);
        input.put("stage", "INTERVIEW_3");
        input.put("timeMode", "DEADLINE");
        assertThat(send("PUT", path + "/" + id, input).statusCode()).isEqualTo(400);
        assertStage(app, "WRITTEN_TEST");
        assertThat(json.readTree(send("GET", path + "/" + id, null).body()).get("startAt").asString()).isEqualTo("2026-10-09T14:00:00");
        input.put("deadlineAt", "2026-10-09T13:00:00");
        assertThat(send("PUT", path + "/" + id, input).statusCode()).isEqualTo(400);
        input.put("deadlineAt", "2026-10-10T14:00:00");
        var changed = send("PUT", path + "/" + id, input);
        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(json.readTree(changed.body()).get("endAt").isNull()).isTrue();
        assertStage(app, "INTERVIEW_3");
        input.put("timeMode", "RECORD_ONLY");
        changed = send("PUT", path + "/" + id, input);
        assertThat(changed.statusCode()).isEqualTo(200);
        var record = json.readTree(changed.body());
        assertThat(record.get("startAt").isNull()).isTrue();
        assertThat(record.get("endAt").isNull()).isTrue();
        assertThat(record.get("deadlineAt").isNull()).isTrue();
        assertThat(record.get("notes").asString()).isEqualTo("提前准备\n带好材料");
        assertThat(record.get("roundName").asString()).isEqualTo("三面");
    }

    @Test void processOwnershipValidationAndCascadeDeletion() throws Exception {
        long app = newApplication();
        long other = newApplication();
        String path = "/api/applications/" + app + "/processes";
        var input = process("INTERVIEW_1", "2026-10-07T10:00:00");
        long id = createProcess(path, input);
        String wrong = "/api/applications/" + other + "/processes/" + id;
        assertThat(send("GET", wrong, null).statusCode()).isEqualTo(404);
        assertThat(send("PUT", wrong, input).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", wrong, null).statusCode()).isEqualTo(404);
        assertThat(send("PATCH", wrong + "/status", Map.of("status", "COMPLETED")).statusCode()).isEqualTo(404);
        assertThat(send("PATCH", path + "/" + id + "/status", Map.of("status", "BAD")).statusCode()).isEqualTo(400);
        input.put("stage", "APPLIED");
        assertThat(send("POST", path, input).statusCode()).isEqualTo(400);
        assertStage(app, "INTERVIEW_1");
        assertStage(other, "APPLIED");
        send("DELETE", "/api/applications/" + app, null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_process WHERE application_id=?", Integer.class, app)).isZero();
        assertThat(send("GET", path, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/applications/" + other, null).statusCode()).isEqualTo(200);
    }

    @Test void concurrentProcessCreationKeepsHighestStage() throws Exception {
        long app = newApplication();
        String path = "/api/applications/" + app + "/processes";
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.List.of("ASSESSMENT", "INTERVIEW_2", "INTERVIEW_1", "WRITTEN_TEST").stream()
                    .map(stage -> (java.util.concurrent.Callable<Integer>) () -> send("POST", path, process(stage, "2026-10-07T10:00:00")).statusCode()).toList();
            for (var future : executor.invokeAll(tasks)) assertThat(future.get()).isEqualTo(201);
        }
        assertStage(app, "INTERVIEW_2");
        assertThat(json.readTree(send("GET", path, null).body()).size()).isEqualTo(4);
    }

    private long newApplication() throws Exception {
        var result = send("POST", "/api/applications", Map.of("companyName", "流程测试", "positionName", "后端"));
        assertThat(result.statusCode()).isEqualTo(201);
        return json.readTree(result.body()).get("id").asLong();
    }
    private java.util.HashMap<String,Object> process(String stage, String occurred) {
        return new java.util.HashMap<>(Map.of("stage", stage, "timeMode", "RECORD_ONLY", "status", "PENDING", "occurredAt", occurred));
    }
    private long createProcess(String path, Object input) throws Exception {
        var result = send("POST", path, input);
        assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(201);
        return json.readTree(result.body()).get("id").asLong();
    }
    private void assertStage(long app, String expected) throws Exception {
        assertThat(json.readTree(send("GET", "/api/applications/" + app, null).body()).get("currentStage").asString()).isEqualTo(expected);
    }
    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path))
                .header("Content-Type", "application/json").method(method, publisher).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
