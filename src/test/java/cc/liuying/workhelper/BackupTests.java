package cc.liuying.workhelper;

import cc.liuying.workhelper.backup.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:backups;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa","spring.datasource.password=","app.time-zone=Asia/Shanghai",
        "app.backup-directory=target/test-safety-backups"
})
class BackupTests {
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    @Autowired BackupCodec codec;
    @MockitoSpyBean BackupRepository repository;
    @MockitoSpyBean SafetyBackupStore safety;
    final HttpClient client=HttpClient.newHttpClient();
    final JsonMapper json=JsonMapper.builder().build();
    long app,process;
    @BeforeEach void setup() throws Exception {
        reset(repository,safety);
        jdbc.update("DELETE FROM job_application");
        app=createApplication("备份测试");
        var p=send("POST","/api/applications/"+app+"/processes",json.writeValueAsString(Map.of("stage","INTERVIEW_2","roundName","技术二面","timeMode","SCHEDULED","startAt","2026-10-10T10:00:00","endAt","2026-10-10T11:00:00","status","PENDING","notes","流程\n换行")),Map.of());
        process=body(p,201).get("id").asLong();
        body(send("PUT",interviewPath(),json.writeValueAsString(Map.of("version",0,"summary","总结\n第二行","questions",List.of(Map.of("question","中文问题\n"+"长文本".repeat(3000),"answer","","review","复盘"),Map.of("question","第二个问题","answer","回答\n第二行")))),Map.of()),200);
    }
    @Test void fullRoundTripPreservesRelationsTextTimestampsAndCreatesRecoverableSafetyBackup() throws Exception {
        String backup=export();
        var original=json.readTree(backup);
        long extra=createApplication("恢复前新增");
        var preview=preview(backup);
        assertThat(preview.get("incoming").get("questions").asInt()).isEqualTo(2);
        assertThat(preview.get("current").get("applications").asInt()).isEqualTo(2);
        var result=body(restore(backup,preview),200);
        var restored=json.readTree(export());
        assertThat(restored.get("applications")).isEqualTo(original.get("applications"));
        assertThat(restored.get("questions")).isEqualTo(original.get("questions"));
        var oldProcess=original.get("processes").get(0); var newProcess=restored.get("processes").get(0);
        assertThat(newProcess.get("record")).isEqualTo(oldProcess.get("record"));
        assertThat(newProcess.get("summary")).isEqualTo(oldProcess.get("summary"));
        assertThat(newProcess.get("interviewVersion").asLong()).isGreaterThan(oldProcess.get("interviewVersion").asLong());
        var safetyResponse=send("GET","/api/backups/safety/"+result.get("safetyBackup").asString(),null,Map.of());
        assertThat(body(safetyResponse,200).get("applications").size()).isEqualTo(2);
        // The automatically saved file is itself restorable.
        body(restore(safetyResponse.body(),preview(safetyResponse.body())),200);
        assertThat(send("GET","/api/applications/"+extra,null,Map.of()).statusCode()).isEqualTo(200);
        long fresh=createApplication("恢复后新增"); assertThat(fresh).isGreaterThan(extra);
    }
    @Test void corruptedUnknownVersionDuplicateIdsBrokenRelationsAndInvalidTimesAreRejected() throws Exception {
        String backup=export();
        assertRejected("{broken");
        assertRejected(backup+" {}");
        var root=(ObjectNode)json.readTree(backup); root.put("version",99); assertRejected(root.toString());
        root=(ObjectNode)json.readTree(backup); root.put("timeZone","UTC"); assertRejected(root.toString());
        root=(ObjectNode)json.readTree(backup); root.put("unknown","unsupported"); assertRejected(root.toString());
        root=(ObjectNode)json.readTree(backup); ((ObjectNode)root.get("processes").get(0).get("record")).put("applicationId",999999); assertRejected(root.toString());
        root=(ObjectNode)json.readTree(backup); ((ObjectNode)root.get("processes").get(0).get("record")).put("endAt","2026-10-01T10:00:00"); assertRejected(root.toString());
        root=(ObjectNode)json.readTree(backup); ((ObjectNode)root.get("questions").get(1)).put("id",root.get("questions").get(0).get("id").asLong()); assertRejected(root.toString());
        root=(ObjectNode)json.readTree(backup); ((ObjectNode)root.get("questions").get(0)).put("sortOrder",10); assertRejected(root.toString());
        root=(ObjectNode)json.readTree(backup); ((ObjectNode)root.get("questions").get(0)).put("question","　"); assertRejected(root.toString());
        root=(ObjectNode)json.readTree(backup); ((ObjectNode)root.get("applications").get(0)).put("companyName","x".repeat(201)); assertRejected(root.toString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_question",Integer.class)).isEqualTo(2);
    }
    @Test void previewIsInvalidatedByWritesAndFileChangesAndRequiresConfirmation() throws Exception {
        String backup=export(); var before=preview(backup);
        createApplication("预览后新增");
        assertThat(restore(backup,before).statusCode()).isEqualTo(409);
        var current=preview(backup);
        assertThat(restore(backup+" ",current).statusCode()).isEqualTo(409);
        assertThat(send("POST","/api/backups/restore",backup,Map.of()).statusCode()).isEqualTo(400);
        var headers=headers(current); headers.put("X-Backup-Confirmation","no");
        assertThat(send("POST","/api/backups/restore",backup,headers).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_application",Integer.class)).isEqualTo(2);
    }
    @Test void failedSafetyWriteDoesNotDeleteAnything() throws Exception {
        String backup=export(); var p=preview(backup);
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"模拟磁盘写入失败")).when(safety).save(any());
        assertThat(restore(backup,p).statusCode()).isEqualTo(503);
        verify(repository,never()).replace(any());
        assertBusinessEquals(backup,export());
    }
    @Test void failureAfterReplacementRollsBackAllTablesAndRevisionButKeepsSafetyFile() throws Exception {
        String backup=export(); createApplication("回滚必须保留"); String before=export(); var p=preview(backup);
        int files=safety.list().size();
        doAnswer(invocation -> { invocation.callRealMethod(); throw new DataIntegrityViolationException("simulated late failure"); }).when(repository).replace(any());
        assertThat(restore(backup,p).statusCode()).isEqualTo(503);
        assertBusinessEquals(before,export());
        assertThat(preview(backup).get("currentRevision")).isEqualTo(p.get("currentRevision"));
        assertThat(safety.list().size()).isEqualTo(files+1);
    }
    @Test void concurrentRestoreOnlyOneCanUseTheSamePreview() throws Exception {
        String backup=export(); var p=preview(backup);
        var first=CompletableFuture.supplyAsync(() -> uncheckedRestore(backup,p));
        var second=CompletableFuture.supplyAsync(() -> uncheckedRestore(backup,p));
        assertThat(List.of(first.join(),second.join())).containsExactlyInAnyOrder(200,409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_question",Integer.class)).isEqualTo(2);
    }
    @Test void emptyBackupRequiresExplicitRestoreAndDerivedStageIsRecomputed() throws Exception {
        var root=(ObjectNode)json.readTree(export());
        ((ObjectNode)root.get("applications").get(0)).put("currentStage","OFFER");
        String changed=root.toString(); body(restore(changed,preview(changed)),200);
        assertThat(body(send("GET","/api/applications/"+app,null,Map.of()),200).get("currentStage").asString()).isEqualTo("INTERVIEW_2");
        root.putArray("applications"); root.putArray("processes"); root.putArray("questions");
        String empty=root.toString(); var p=preview(empty);
        assertThat(p.get("incoming").get("applications").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_application",Integer.class)).isEqualTo(1);
        body(restore(empty,p),200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_question",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_application",Integer.class)).isZero();
    }
    @Test void oversizedFileAndArbitrarySafetyFileNamesAreRejected() throws Exception {
        String large=" ".repeat(BackupCodec.MAX_BYTES+1);
        assertThat(send("POST","/api/backups/preview",large,Map.of()).statusCode()).isEqualTo(413);
        assertThat(send("GET","/api/backups/safety/application.yml",null,Map.of()).statusCode()).isEqualTo(400);
        assertThat(send("GET","/api/backups/safety/before-restore-20261008T000000Z-00000000-0000-0000-0000-000000000000.json",null,Map.of()).statusCode()).isEqualTo(404);
    }
    void assertBusinessEquals(String left,String right) {
        var a=json.readTree(left); var b=json.readTree(right);
        for(String field:List.of("applications","processes","questions")) assertThat(b.get(field)).isEqualTo(a.get(field));
    }
    void assertRejected(String backup) throws Exception { assertThat(send("POST","/api/backups/preview",backup,Map.of()).statusCode()).isEqualTo(400); }
    int uncheckedRestore(String backup,JsonNode p) { try { return restore(backup,p).statusCode(); } catch(Exception e) { throw new RuntimeException(e); } }
    String interviewPath() { return "/api/applications/"+app+"/processes/"+process+"/interview"; }
    long createApplication(String company) throws Exception { return body(send("POST","/api/applications",json.writeValueAsString(Map.of("companyName",company,"positionName","Java","notes","备注\n多行")),Map.of()),201).get("id").asLong(); }
    String export() throws Exception { var response=send("GET","/api/backups",null,Map.of()); body(response,200); return response.body(); }
    JsonNode preview(String backup) throws Exception { return body(send("POST","/api/backups/preview",backup,Map.of()),200); }
    Map<String,String> headers(JsonNode p) { return new HashMap<>(Map.of("X-Backup-Revision",p.get("currentRevision").asString(),"X-Backup-SHA256",p.get("sha256").asString(),"X-Backup-Confirmation","REPLACE_ALL")); }
    HttpResponse<String> restore(String backup,JsonNode p) throws Exception { return send("POST","/api/backups/restore",backup,headers(p)); }
    JsonNode body(HttpResponse<String> r,int status) { assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(status); return json.readTree(r.body()); }
    HttpResponse<String> send(String method,String path,String body,Map<String,String> headers) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+environment.getProperty("local.server.port")+path)).header("Content-Type","application/json");
        headers.forEach(builder::header);
        return client.send(builder.method(method,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
