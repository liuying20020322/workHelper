package cc.liuying.workhelper.process;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ProcessRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    static final RowMapper<ProcessRecord> MAPPER = (rs, i) -> new ProcessRecord(
            rs.getLong("id"), rs.getLong("application_id"), Stage.valueOf(rs.getString("stage")),
            rs.getString("round_name"), ProcessRequest.TimeMode.valueOf(rs.getString("time_mode")),
            rs.getObject("start_at", LocalDateTime.class), rs.getObject("end_at", LocalDateTime.class),
            rs.getObject("deadline_at", LocalDateTime.class), ProcessRequest.Status.valueOf(rs.getString("status")),
            rs.getString("location"), rs.getString("notes"), rs.getObject("occurred_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class),
            rs.getInt("question_count") > 0 || (rs.getString("interview_summary") != null && !rs.getString("interview_summary").isBlank()));

    public ProcessRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; this.named = new NamedParameterJdbcTemplate(jdbc); }

    // All process mutations lock their parent before reading/writing: serialized stage recalculation.
    public boolean lockApplication(long id) {
        return !jdbc.queryForList("SELECT id FROM job_application WHERE id=? FOR UPDATE", Long.class, id).isEmpty();
    }
    public boolean applicationExists(long id) {
        return !jdbc.queryForList("SELECT id FROM job_application WHERE id=?", Long.class, id).isEmpty();
    }
    public List<ProcessRecord> list(long appId) {
        return jdbc.query("SELECT * FROM application_process WHERE application_id=? ORDER BY occurred_at DESC, id DESC", MAPPER, appId);
    }
    public List<ProcessRecord> listAll() {
        return jdbc.query("SELECT * FROM application_process ORDER BY id", MAPPER);
    }
    public Optional<ProcessRecord> get(long appId, long id) {
        return jdbc.query("SELECT * FROM application_process WHERE application_id=? AND id=?", MAPPER, appId, id).stream().findFirst();
    }
    private MapSqlParameterSource parameters(long appId, ProcessRequest r, LocalDateTime now) {
        return new MapSqlParameterSource().addValue("appId", appId).addValue("stage", r.stage().name())
                .addValue("round", r.roundName()).addValue("mode", r.timeMode().name())
                .addValue("start", r.startAt()).addValue("end", r.endAt()).addValue("deadline", r.deadlineAt())
                .addValue("status", r.status().name()).addValue("location", r.location()).addValue("notes", r.notes())
                .addValue("occurred", r.occurredAt()).addValue("now", now);
    }
    public long insert(long appId, ProcessRequest r, LocalDateTime now) {
        var keys = new GeneratedKeyHolder();
        named.update("""
                INSERT INTO application_process(application_id,stage,round_name,time_mode,start_at,end_at,deadline_at,
                  status,location,notes,occurred_at,created_at,updated_at)
                VALUES(:appId,:stage,:round,:mode,:start,:end,:deadline,:status,:location,:notes,:occurred,:now,:now)
                """, parameters(appId,r,now), keys, new String[]{"id"});
        return java.util.Objects.requireNonNull(keys.getKey()).longValue();
    }
    public void update(long appId, long id, ProcessRequest r, LocalDateTime now) {
        named.update("""
                UPDATE application_process SET stage=:stage, round_name=:round, time_mode=:mode,
                start_at=:start,end_at=:end,deadline_at=:deadline,status=:status,location=:location,
                notes=:notes,occurred_at=:occurred,updated_at=:now WHERE application_id=:appId AND id=:id
                """, parameters(appId,r,now).addValue("id",id));
    }
    public void status(long appId,long id,ProcessRequest.Status status,LocalDateTime now) {
        jdbc.update("UPDATE application_process SET status=?,updated_at=? WHERE application_id=? AND id=?",status.name(),now,appId,id);
    }
    public void delete(long appId,long id) { jdbc.update("DELETE FROM application_process WHERE application_id=? AND id=?",appId,id); }
    public void setStage(long appId,String stage,LocalDateTime now) {
        jdbc.update("UPDATE job_application SET current_stage=?,updated_at=? WHERE id=?",stage,now,appId);
    }
}
