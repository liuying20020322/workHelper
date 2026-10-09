package cc.liuying.workhelper.backup;

import cc.liuying.workhelper.application.ApplicationRepository;
import cc.liuying.workhelper.process.ProcessRepository;
import cc.liuying.workhelper.process.ProcessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class BackupRepository {
    private final JdbcTemplate jdbc;
    private final ApplicationRepository applications;
    private final ProcessRepository processes;
    private final Clock clock;
    private final cc.liuying.workhelper.unapplied.UnappliedRepository unapplied;
    public BackupRepository(JdbcTemplate jdbc,ApplicationRepository applications,ProcessRepository processes,Clock clock,cc.liuying.workhelper.unapplied.UnappliedRepository unapplied) {
        this.unapplied=unapplied;
        this.jdbc=jdbc; this.applications=applications; this.processes=processes; this.clock=clock;
    }
    public BackupData snapshot() {
        record Notes(long id,String summary,long version) {}
        var notes=jdbc.query("SELECT id,interview_summary,interview_version FROM application_process",(rs,i) ->
                new Notes(rs.getLong(1),Objects.requireNonNullElse(rs.getString(2),""),rs.getLong(3)))
                .stream().collect(Collectors.toMap(Notes::id,n -> n));
        var entries=processes.listAll().stream().map(p -> new BackupData.ProcessData(p,notes.get(p.id()).summary(),notes.get(p.id()).version())).toList();
        var questions=jdbc.query("SELECT * FROM interview_question ORDER BY process_id,sort_order,id",(rs,i) ->
                new BackupData.QuestionData(rs.getLong("id"),rs.getLong("process_id"),rs.getString("question"),rs.getString("answer"),rs.getString("review"),
                        rs.getInt("sort_order"),rs.getObject("created_at",LocalDateTime.class),rs.getObject("updated_at",LocalDateTime.class)));
        return new BackupData("workHelper-backup",2,clock.instant(),clock.getZone().getId(),applications.findAll(""),entries,questions,unapplied.list("",null));
    }
    public void replace(BackupData data) {
        // Never TRUNCATE or alter auto-increment here: MySQL DDL would break rollback guarantees.
        long previousVersion=jdbc.queryForObject("SELECT COALESCE(MAX(interview_version),0) FROM application_process",Long.class);
        long restoredVersion=Math.max(previousVersion,data.processes().stream().mapToLong(BackupData.ProcessData::interviewVersion).max().orElse(0))+1;
        jdbc.update("DELETE FROM unapplied_company");
        batch("INSERT INTO unapplied_company(id,company_name,reason,other_reason,viewed_date,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
                data.unappliedCompanies().stream().map(r->new Object[]{r.id(),r.companyName(),r.reason(),r.otherReason(),r.viewedDate(),r.createdAt(),r.updatedAt()}).toList());
        jdbc.update("DELETE FROM job_application");
        var grouped=data.processes().stream().map(BackupData.ProcessData::record).collect(Collectors.groupingBy(p -> p.applicationId()));
        batch("""
                INSERT INTO job_application(id,company_name,position_name,location,requirements,applied_at,channel,job_url,notes,current_stage,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """,data.applications().stream().map(a -> new Object[]{a.id(),a.companyName(),a.positionName(),a.location(),a.requirements(),a.appliedAt(),a.channel(),a.jobUrl(),a.notes(),
                        ProcessService.calculateStage(grouped.getOrDefault(a.id(),List.of())),a.createdAt(),a.updatedAt()}).toList());
        var counts=data.questions().stream().collect(Collectors.groupingBy(BackupData.QuestionData::processId,Collectors.counting()));
        batch("""
                INSERT INTO application_process(id,application_id,stage,round_name,time_mode,start_at,end_at,deadline_at,status,location,notes,
                    occurred_at,created_at,updated_at,interview_summary,interview_version,question_count)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,data.processes().stream().map(entry -> {
                    var p=entry.record(); return new Object[]{p.id(),p.applicationId(),p.stage().name(),p.roundName(),p.timeMode().name(),p.startAt(),p.endAt(),p.deadlineAt(),p.status().name(),p.location(),p.notes(),
                            p.occurredAt(),p.createdAt(),p.updatedAt(),entry.summary(),restoredVersion,counts.getOrDefault(p.id(),0L)};
                }).toList());
        batch("INSERT INTO interview_question(id,process_id,question,answer,review,sort_order,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                data.questions().stream().map(q -> new Object[]{q.id(),q.processId(),q.question(),q.answer(),q.review(),q.sortOrder(),q.createdAt(),q.updatedAt()}).toList());
    }
    private void batch(String sql,List<Object[]> rows) {
        for(int offset=0;offset<rows.size();offset+=500) jdbc.batchUpdate(sql,rows.subList(offset,Math.min(rows.size(),offset+500)));
    }
}
