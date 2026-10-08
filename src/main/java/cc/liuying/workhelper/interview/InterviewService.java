package cc.liuying.workhelper.interview;

import cc.liuying.workhelper.application.ApplicationService;
import cc.liuying.workhelper.application.JobApplication;
import cc.liuying.workhelper.process.ProcessRecord;
import cc.liuying.workhelper.process.ProcessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import cc.liuying.workhelper.common.DataWriteLock;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class InterviewService {
    private final JdbcTemplate jdbc;
    private final ProcessRepository processes;
    private final ApplicationService applications;
    private final Clock clock;
    private final DataWriteLock writes;
    public InterviewService(JdbcTemplate jdbc,ProcessRepository processes,ApplicationService applications,Clock clock,DataWriteLock writes) {
        this.jdbc=jdbc; this.processes=processes; this.applications=applications; this.clock=clock; this.writes=writes;
    }
    public record Question(long id,String question,String answer,String review,int sortOrder,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record Detail(JobApplication application,ProcessRecord process,String timeZone,String summary,long version,List<Question> questions) {}
    private ProcessRecord requireInterview(long appId,long processId) {
        var process=processes.get(appId,processId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"投递或流程不存在，可能已被删除"));
        if(!process.stage().isInterview()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅面试流程可记录面经");
        return process;
    }
    @Transactional(readOnly=true)
    public Detail get(long appId,long processId) {
        var process=requireInterview(appId,processId);
        var questions=jdbc.query("SELECT * FROM interview_question WHERE process_id=? ORDER BY sort_order,id",(rs,i) ->
                new Question(rs.getLong("id"),rs.getString("question"),rs.getString("answer"),rs.getString("review"),
                        rs.getInt("sort_order"),rs.getObject("created_at",LocalDateTime.class),rs.getObject("updated_at",LocalDateTime.class)),processId);
        return jdbc.queryForObject("SELECT interview_summary,interview_version FROM application_process WHERE id=?",(rs,i) ->
                new Detail(applications.get(appId),process,clock.getZone().getId(),text(rs.getString(1)),rs.getLong(2),questions),processId);
    }
    @Transactional
    public Detail save(long appId,long processId,InterviewController.Input input) {
        writes.changed();
        // Same parent lock as process updates/deletes; serialize changes to this interview and its stage.
        if(!processes.lockApplication(appId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"投递不存在");
        requireInterview(appId,processId);
        long version=jdbc.queryForObject("SELECT interview_version FROM application_process WHERE id=?",Long.class,processId);
        if(version!=input.version()) throw new ResponseStatusException(HttpStatus.CONFLICT,"面经已在其他页面更新，请保留当前输入，重新载入后核对再保存");
        Set<Long> existing=new HashSet<>(jdbc.queryForList("SELECT id FROM interview_question WHERE process_id=?",Long.class,processId));
        Set<Long> retained=new HashSet<>();
        for(var q:input.questions()) {
            if(text(q.question()).isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"问题不能为空");
            if(q.id()!=null && (!existing.contains(q.id()) || !retained.add(q.id())))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"问答归属不正确或重复，请重新载入核对");
        }
        var now=LocalDateTime.now(clock);
        for(long id:existing) if(!retained.contains(id)) jdbc.update("DELETE FROM interview_question WHERE id=? AND process_id=?",id,processId);
        int order=0;
        for(var q:input.questions()) {
            if(q.id()==null) jdbc.update("INSERT INTO interview_question(process_id,question,answer,review,sort_order,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
                    processId,text(q.question()),text(q.answer()),text(q.review()),order,now,now);
            else jdbc.update("UPDATE interview_question SET question=?,answer=?,review=?,sort_order=?,updated_at=? WHERE id=? AND process_id=?",
                    text(q.question()),text(q.answer()),text(q.review()),order,now,q.id(),processId);
            order++;
        }
        jdbc.update("UPDATE application_process SET interview_summary=?,interview_version=interview_version+1,question_count=?,updated_at=? WHERE id=?",
                text(input.summary()),order,now,processId);
        jdbc.update("UPDATE job_application SET updated_at=? WHERE id=?",now,appId);
        return get(appId,processId);
    }
    private static String text(String value) { return value==null ? "" : value.strip(); }
}
