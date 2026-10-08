package cc.liuying.workhelper;

import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:interviews;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa","spring.datasource.password=","app.time-zone=Asia/Shanghai"
})
class InterviewTests {
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    final HttpClient client=HttpClient.newHttpClient();
    final JsonMapper json=JsonMapper.builder().build();
    long app,process;
    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM job_application");
        app=createApp(); process=createProcess(app,"INTERVIEW_1");
    }
    @Test void draftLongTextEmptyAnswersReorderEditAndDeletePersist() throws Exception {
        var empty=body(send("GET",path(app,process),null),200);
        assertThat(empty.get("questions").size()).isZero();
        assertThat(empty.get("summary").asString()).isEmpty();
        String longText="中文问题\n".repeat(2500);
        var saved=body(save(0,"总结\n第二行",List.of(Map.of("question",longText.strip()),Map.of("question","第二题"))),200);
        var first=saved.get("questions").get(0); var second=saved.get("questions").get(1);
        assertThat(first.get("question").asString()).isEqualTo(longText.strip());
        assertThat(first.get("answer").asString()).isEmpty();
        assertThat(saved.get("process").get("hasInterview").asBoolean()).isTrue();
        var reordered=body(save(1,"总结\n第二行",List.of(Map.of("id",second.get("id").asLong(),"question","第二题","review","复盘\n补充"),
                Map.of("id",first.get("id").asLong(),"question","修改问题","answer","回答\n换行"))),200);
        assertThat(reordered.get("questions").get(0).get("id")).isEqualTo(second.get("id"));
        assertThat(reordered.get("questions").get(1).get("createdAt")).isEqualTo(first.get("createdAt"));
        var loaded=body(send("GET",path(app,process),null),200);
        assertThat(loaded.get("questions").get(1).get("answer").asString()).isEqualTo("回答\n换行");
        body(save(2,"仅总结",List.of()),200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_question",Integer.class)).isZero();
        var cleared=body(save(3,"",List.of()),200);
        assertThat(cleared.get("process").get("hasInterview").asBoolean()).isFalse();
    }
    @Test void invalidInputAndForeignQuestionCannotPartiallyOverwrite() throws Exception {
        body(save(0,"原总结",List.of(Map.of("question","原问题"))),200);
        assertThat(save(1,"损坏",List.of(Map.of("question","　 \n"))).statusCode()).isEqualTo(400);
        assertThat(save(1,"损坏",List.of(Map.of("question","x".repeat(20001)))).statusCode()).isEqualTo(400);
        long other=createProcess(app,"INTERVIEW_2");
        var another=body(send("PUT",path(app,other),Map.of("version",0,"summary","","questions",List.of(Map.of("question","另一轮")))),200);
        long foreignId=another.get("questions").get(0).get("id").asLong();
        assertThat(save(1,"损坏",List.of(Map.of("id",foreignId,"question","越界"))).statusCode()).isEqualTo(400);
        var still=body(send("GET",path(app,process),null),200);
        assertThat(still.get("summary").asString()).isEqualTo("原总结");
        assertThat(still.get("questions").get(0).get("question").asString()).isEqualTo("原问题");
        assertThat(still.get("version").asLong()).isEqualTo(1);
        long id=still.get("questions").get(0).get("id").asLong();
        assertThat(save(1,"",List.of(Map.of("id",id,"question","a"),Map.of("id",id,"question","b"))).statusCode()).isEqualTo(400);
    }
    @Test void staleSaveIsRejectedWithoutChangingContent() throws Exception {
        body(save(0,"第一个页面",List.of(Map.of("question","问题"))),200);
        assertThat(save(0,"过期页面",List.of()).statusCode()).isEqualTo(409);
        assertThat(body(send("GET",path(app,process),null),200).get("summary").asString()).isEqualTo("第一个页面");
    }
    @Test void scopesAndStagesAreCheckedAndNotesPreventStageLoss() throws Exception {
        long anotherApp=createApp();
        assertThat(send("GET",path(anotherApp,process),null).statusCode()).isEqualTo(404);
        assertThat(send("PUT",path(anotherApp,process),Map.of("version",0,"summary","","questions",List.of())).statusCode()).isEqualTo(404);
        long assessment=createProcess(app,"ASSESSMENT");
        assertThat(send("GET",path(app,assessment),null).statusCode()).isEqualTo(400);
        body(save(0,"总结",List.of()),200);
        String processPath="/api/applications/"+app+"/processes/"+process;
        assertThat(send("PUT",processPath,processInput("ASSESSMENT")).statusCode()).isEqualTo(400);
        assertThat(send("PUT",processPath,processInput("INTERVIEW_2")).statusCode()).isEqualTo(200);
        assertThat(body(send("GET",path(app,process),null),200).get("summary").asString()).isEqualTo("总结");
        body(save(1,"",List.of()),200);
        assertThat(send("PUT",processPath,processInput("ASSESSMENT")).statusCode()).isEqualTo(200);
    }
    @Test void deletingProcessOrApplicationCascadesButPreservesOtherRounds() throws Exception {
        body(save(0,"总结",List.of(Map.of("question","第一轮"))),200);
        long other=createProcess(app,"INTERVIEW_2");
        body(send("PUT",path(app,other),Map.of("version",0,"questions",List.of(Map.of("question","第二轮")))),200);
        assertThat(send("DELETE","/api/applications/"+app+"/processes/"+process,null).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_question",Integer.class)).isEqualTo(1);
        assertThat(body(send("GET",path(app,other),null),200).get("questions").get(0).get("question").asString()).isEqualTo("第二轮");
        assertThat(send("DELETE","/api/applications/"+app,null).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_question",Integer.class)).isZero();
        assertThat(send("GET",path(app,other),null).statusCode()).isEqualTo(404);
    }
    long createApp() throws Exception { return body(send("POST","/api/applications",Map.of("companyName","面经测试","positionName","Java")),201).get("id").asLong(); }
    long createProcess(long appId,String stage) throws Exception { return body(send("POST","/api/applications/"+appId+"/processes",processInput(stage)),201).get("id").asLong(); }
    Map<String,String> processInput(String stage) { return Map.of("stage",stage,"timeMode","RECORD_ONLY","status","PENDING"); }
    String path(long appId,long processId) { return "/api/applications/"+appId+"/processes/"+processId+"/interview"; }
    HttpResponse<String> save(long version,String summary,List<?> questions) throws Exception { return send("PUT",path(app,process),Map.of("version",version,"summary",summary,"questions",questions)); }
    JsonNode body(HttpResponse<String> response,int status) { assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status); return json.readTree(response.body()); }
    HttpResponse<String> send(String method,String path,Object body) throws Exception {
        var publisher=body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+environment.getProperty("local.server.port")+path))
                .header("Content-Type","application/json").method(method,publisher).build(),HttpResponse.BodyHandlers.ofString());
    }
}
