package cc.liuying.workhelper.backup;

import cc.liuying.workhelper.application.ApplicationRequest;
import cc.liuying.workhelper.interview.InterviewController.QuestionInput;
import cc.liuying.workhelper.process.ProcessRequest;
import cc.liuying.workhelper.process.Stage;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.security.MessageDigest;

@Component
public class BackupCodec {
    public static final int MAX_BYTES=32*1024*1024;
    private final Validator validator;
    private final Clock clock;
    private final JsonMapper json=JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    public BackupCodec(Validator validator,Clock clock) { this.validator=validator; this.clock=clock; }
    public BackupData decode(byte[] bytes) {
        check(bytes.length>0 && bytes.length<=MAX_BYTES,"备份文件必须在 32 MB 以内且不能为空");
        BackupData data;
        try { data=json.readValue(bytes,BackupData.class); }
        catch (RuntimeException e) { throw invalid("备份 JSON 格式或字段不正确，请使用本工具导出的完整文件"); }
        validate(data);
        return data;
    }
    public byte[] encode(BackupData data) {
        validate(data);
        byte[] bytes=json.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        check(bytes.length<=MAX_BYTES,"当前数据超过 32 MB 备份上限，请联系维护者扩展容量后再备份或恢复");
        return bytes;
    }
    public String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void validate(BackupData d) {
        check(d!=null && "workHelper-backup".equals(d.format()) && d.version()==1,"不支持的备份类型或版本，仅支持 workHelper 版本 1 备份");
        check(d.exportedAt()!=null,"备份缺少导出时间");
        check(clock.getZone().getId().equals(d.timeZone()),"备份业务时区与当前配置不一致，请先核对并调整应用时区，再恢复");
        check(d.applications()!=null && d.processes()!=null && d.questions()!=null,"备份缺少投递、流程或问答列表");
        check(d.applications().size()<=100000 && d.processes().size()<=100000 && d.questions().size()<=100000,"单类记录不能超过十万条");
        Set<Long> apps=new HashSet<>();
        for(var a:d.applications()) {
            check(a!=null,"投递记录不能为 null"); id(a.id()); check(apps.add(a.id()),"投递编号重复");
            texts(a.companyName(),a.positionName(),a.location(),a.requirements(),a.channel(),a.jobUrl(),a.notes());
            valid(new ApplicationRequest(a.companyName(),a.positionName(),a.location(),a.requirements(),a.appliedAt(),a.channel(),a.jobUrl(),a.notes()));
            check(!a.companyName().isBlank() && !a.positionName().isBlank(),"公司和岗位不能为空");
            check(a.currentStage()!=null && (a.currentStage().equals("APPLIED") || Arrays.stream(Stage.values()).anyMatch(s -> s.name().equals(a.currentStage()))),"投递阶段无效");
            date(a.appliedAt()); date(a.createdAt()); date(a.updatedAt());
        }
        Map<Long,BackupData.ProcessData> processes=new HashMap<>();
        for(var entry:d.processes()) {
            check(entry!=null && entry.record()!=null,"流程记录不能为 null"); var p=entry.record();
            id(p.id()); check(processes.put(p.id(),entry)==null,"流程编号重复"); check(apps.contains(p.applicationId()),"流程引用了不存在的投递");
            texts(p.roundName(),p.location(),p.notes(),entry.summary());
            valid(new ProcessRequest(p.stage(),p.roundName(),p.timeMode(),p.startAt(),p.endAt(),p.deadlineAt(),p.status(),p.location(),p.notes(),p.occurredAt()));
            check(entry.summary().length()<=50000,"面试总结最多50000字");
            check(entry.interviewVersion()>=0 && entry.interviewVersion()<9007199254740990L,"面经版本无效");
            check(p.stage().isInterview() || entry.summary().isEmpty(),"非面试流程不能包含面试总结");
            date(p.occurredAt()); date(p.createdAt()); date(p.updatedAt());
            if(p.startAt()!=null) date(p.startAt()); if(p.endAt()!=null) date(p.endAt()); if(p.deadlineAt()!=null) date(p.deadlineAt());
            switch(p.timeMode()) {
                case RECORD_ONLY -> check(p.startAt()==null && p.endAt()==null && p.deadlineAt()==null,"仅记录阶段不能包含安排时间");
                case SCHEDULED -> check(p.startAt()!=null && p.deadlineAt()==null && (p.endAt()==null || !p.endAt().isBefore(p.startAt())),"定时安排的开始或结束时间无效");
                case DEADLINE -> check(p.deadlineAt()!=null && p.endAt()==null && (p.startAt()==null || !p.deadlineAt().isBefore(p.startAt())),"截止任务时间无效");
            }
        }
        Set<Long> ids=new HashSet<>(); Map<Long,Set<Integer>> orders=new HashMap<>();
        for(var q:d.questions()) {
            check(q!=null,"问答不能为 null"); id(q.id()); check(ids.add(q.id()),"问答编号重复");
            var owner=processes.get(q.processId()); check(owner!=null && owner.record().stage().isInterview(),"问答必须关联有效的面试流程");
            texts(q.question(),q.answer(),q.review()); valid(new QuestionInput(q.id(),q.question(),q.answer(),q.review()));
            check(!q.question().isBlank(),"问题不能为空"); date(q.createdAt()); date(q.updatedAt());
            var positions=orders.computeIfAbsent(q.processId(),ignored -> new HashSet<>());
            check(q.sortOrder()>=0 && q.sortOrder()<200 && positions.add(q.sortOrder()),"每轮最多200条问答，排序序号不能重复");
        }
        for(var positions:orders.values()) for(int i=0;i<positions.size();i++) check(positions.contains(i),"问答排序必须从零开始连续编号");
    }
    private void valid(Object value) {
        var errors=validator.validate(value); if(!errors.isEmpty()) throw invalid("备份校验失败："+errors.iterator().next().getMessage());
    }
    private static void id(long id) { check(id>0 && id<=9007199254740991L,"记录编号必须是有效的正整数"); }
    private static void texts(String... values) { for(String value:values) check(value!=null,"备份文本字段缺失，请勿手工删除字段"); }
    private static void date(LocalDateTime value) { check(value!=null && value.getYear()>=1000 && value.getYear()<=9999 && value.getNano()%1000==0,"日期必须在1000至9999年之间，且最多保留六位小数"); }
    private static void check(boolean condition,String message) { if(!condition) throw invalid(message); }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
