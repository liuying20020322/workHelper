package cc.liuying.workhelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes={WorkHelperApplication.class, ReminderTests.TestTime.class}, properties={
        "spring.datasource.url=jdbc:h2:mem:reminders;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "app.time-zone=Asia/Shanghai"
})
class ReminderTests {
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-07T15:30:00Z");
        void set(String instant) { now=Instant.parse(instant); }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now,zone); }
        @Override public Instant instant() { return now; }
    }
    @TestConfiguration
    static class TestTime {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    @Autowired MutableClock clock;
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient client=HttpClient.newHttpClient();
    private final JsonMapper json=JsonMapper.builder().build();
    private long app;

    @BeforeEach void setup() throws Exception {
        clock.set("2026-10-07T15:30:00Z"); // 23:30 Shanghai, intentionally close to midnight.
        jdbc.update("DELETE FROM job_application");
        app=node(send("POST","/api/applications",Map.of("companyName","提醒测试","positionName","Java","location","深圳"))).get("id").asLong();
    }

    @Test void recentUsesThreeCalendarDaysAndKeepsOverdueSeparate() throws Exception {
        long later=scheduled("2026-10-09T23:59:59",null,"PENDING");
        long tomorrow=scheduled("2026-10-08T00:00:00",null,"PENDING");
        long today=scheduled("2026-10-07T23:40:00",null,"PENDING");
        long outside=scheduled("2026-10-10T00:00:00",null,"PENDING");
        long overdue=scheduled("2026-10-01T10:00:00",null,"PENDING");
        JsonNode page=reminders("RECENT");
        assertThat(page.get("today").asString()).isEqualTo("2026-10-07");
        assertThat(page.get("throughDate").asString()).isEqualTo("2026-10-09");
        assertThat(page.get("timeZone").asString()).isEqualTo("Asia/Shanghai");
        assertThat(ids(page,"items")).containsExactly(today,tomorrow,later).doesNotContain(outside);
        assertThat(ids(page,"overdue")).containsExactly(overdue);
        assertThat(page.get("items").get(0).get("dayLabel").asString()).isEqualTo("今天");
        assertThat(page.get("items").get(1).get("dayLabel").asString()).isEqualTo("明天");
        assertThat(page.get("items").get(2).get("dayLabel").asString()).isEqualTo("后天");
        assertThat(page.get("items").get(0).get("companyName").asString()).isEqualTo("提醒测试");
        assertThat(page.get("items").get(0).get("jobLocation").asString()).isEqualTo("深圳");
        assertThat(ids(reminders("ALL"),"items")).containsExactly(today,tomorrow,later,outside);
    }

    @Test void expiryUsesEndOrDeadlineAndStrictlyAfterBoundary() throws Exception {
        long atStart=scheduled("2026-10-07T23:30:00",null,"PENDING");
        long atEnd=scheduled("2026-10-07T22:00:00","2026-10-07T23:30:00","PENDING");
        long ongoing=scheduled("2026-10-06T20:00:00","2026-10-08T01:00:00","PENDING");
        long atDeadline=deadline("2026-10-07T23:30:00","PENDING");
        assertThat(ids(reminders("RECENT"),"overdue")).isEmpty();
        assertThat(ids(reminders("RECENT"),"items")).containsExactly(ongoing,atEnd,atStart,atDeadline);
        assertThat(reminders("RECENT").get("items").get(0).get("inProgress").asBoolean()).isTrue();
        clock.set("2026-10-07T15:30:01Z");
        assertThat(ids(reminders("RECENT"),"overdue")).containsExactly(atEnd,atStart,atDeadline);
        assertThat(ids(reminders("RECENT"),"items")).containsExactly(ongoing);
    }

    @Test void completedCancelledAndRecordOnlyNeverLeakIntoPending() throws Exception {
        long pending=deadline("2026-10-08T12:00:00","PENDING");
        long completed=scheduled("2026-10-01T10:00:00",null,"COMPLETED");
        scheduled("2026-10-08T10:00:00",null,"CANCELLED");
        create(Map.of("stage","OFFER","timeMode","RECORD_ONLY","status","PENDING"));
        create(Map.of("stage","OFFER","timeMode","RECORD_ONLY","status","COMPLETED"));
        assertThat(ids(reminders("ALL"),"items")).containsExactly(pending);
        assertThat(ids(reminders("ALL"),"overdue")).isEmpty();
        JsonNode history=reminders("COMPLETED");
        assertThat(ids(history,"items")).containsExactly(completed);
        assertThat(ids(history,"overdue")).isEmpty();
        assertThat(history.get("items").get(0).get("overdue").asBoolean()).isFalse();
    }

    @Test void reschedulingCompletionCancellationAndModeChangeShareProcessData() throws Exception {
        long id=scheduled("2026-10-08T10:00:00",null,"PENDING");
        String path="/api/applications/"+app+"/processes/"+id;
        Map<String,Object> edit=new HashMap<>(Map.of("stage","INTERVIEW_1","timeMode","DEADLINE","deadlineAt","2026-11-01T18:00:00","status","PENDING","notes","改期备注"));
        assertThat(send("PUT",path,edit).statusCode()).isEqualTo(200);
        assertThat(ids(reminders("RECENT"),"items")).isEmpty();
        JsonNode item=reminders("ALL").get("items").get(0);
        assertThat(item.get("effectiveAt").asString()).isEqualTo("2026-11-01T18:00:00");
        assertThat(item.get("process").get("notes").asString()).isEqualTo("改期备注");
        assertThat(send("PATCH",path+"/status",Map.of("status","COMPLETED")).statusCode()).isEqualTo(200);
        assertThat(ids(reminders("ALL"),"items")).isEmpty();
        assertThat(ids(reminders("COMPLETED"),"items")).containsExactly(id);
        assertThat(node(send("GET",path,null)).get("status").asString()).isEqualTo("COMPLETED");
        send("PATCH",path+"/status",Map.of("status","CANCELLED"));
        assertThat(ids(reminders("COMPLETED"),"items")).isEmpty();
        edit.put("timeMode","RECORD_ONLY");
        send("PUT",path,edit);
        assertThat(ids(reminders("ALL"),"items")).isEmpty();
    }

    @Test void midnightReclassifiesDatesAndAdvancesWindow() throws Exception {
        long tomorrow=deadline("2026-10-08T00:00:00","PENDING");
        long outside=deadline("2026-10-10T00:00:00","PENDING");
        assertThat(ids(reminders("RECENT"),"items")).containsExactly(tomorrow);
        clock.set("2026-10-07T16:00:00Z"); // Midnight in the configured business zone.
        JsonNode page=reminders("RECENT");
        assertThat(page.get("today").asString()).isEqualTo("2026-10-08");
        assertThat(ids(page,"items")).containsExactly(tomorrow,outside);
        assertThat(page.get("items").get(0).get("dayLabel").asString()).isEqualTo("今天");
        assertThat(page.get("items").get(1).get("dayLabel").asString()).isEqualTo("后天");
        clock.set("2026-10-07T16:00:01Z");
        assertThat(ids(reminders("RECENT"),"overdue")).containsExactly(tomorrow);
    }

    @Test void deletionRemovesReminderAndUnknownViewIsRejected() throws Exception {
        long id=deadline("2026-10-08T12:00:00","PENDING");
        send("DELETE","/api/applications/"+app+"/processes/"+id,null);
        assertThat(ids(reminders("ALL"),"items")).isEmpty();
        deadline("2026-10-08T12:00:00","PENDING");
        send("DELETE","/api/applications/"+app,null);
        assertThat(ids(reminders("ALL"),"items")).isEmpty();
        assertThat(send("GET","/api/reminders?view=UNKNOWN",null).statusCode()).isEqualTo(400);
    }

    private long scheduled(String start,String end,String status) throws Exception {
        Map<String,Object> input=new HashMap<>(Map.of("stage","INTERVIEW_1","timeMode","SCHEDULED","startAt",start,"status",status));
        if(end!=null) input.put("endAt",end);
        return create(input);
    }
    private long deadline(String deadline,String status) throws Exception {
        return create(Map.of("stage","ASSESSMENT","timeMode","DEADLINE","deadlineAt",deadline,"status",status));
    }
    private long create(Object input) throws Exception {
        var response=send("POST","/api/applications/"+app+"/processes",input);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
        return node(response).get("id").asLong();
    }
    private JsonNode reminders(String view) throws Exception {
        var response=send("GET","/api/reminders?view="+view,null);
        assertThat(response.statusCode()).isEqualTo(200);
        return node(response);
    }
    private List<Long> ids(JsonNode page,String key) {
        return StreamSupport.stream(page.get(key).spliterator(),false).map(item -> item.get("process").get("id").asLong()).toList();
    }
    private JsonNode node(HttpResponse<String> response) { return json.readTree(response.body()); }
    private HttpResponse<String> send(String method,String path,Object body) throws Exception {
        var publisher=body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+environment.getProperty("local.server.port")+path))
                .header("Content-Type","application/json").method(method,publisher).build();
        return client.send(request,HttpResponse.BodyHandlers.ofString());
    }
}
