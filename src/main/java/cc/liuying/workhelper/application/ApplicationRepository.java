package cc.liuying.workhelper.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ApplicationRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<JobApplication> MAPPER = (rs, row) -> new JobApplication(
            rs.getLong("id"), rs.getString("company_name"), rs.getString("position_name"),
            rs.getString("location"), rs.getString("requirements"), rs.getObject("applied_at", LocalDateTime.class),
            rs.getString("channel"), rs.getString("job_url"), rs.getString("notes"), "APPLIED",
            rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));

    public ApplicationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<JobApplication> findAll(String search) {
        // Escape LIKE metacharacters so user input is always a literal substring.
        String pattern = "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return jdbc.query("""
                SELECT * FROM job_application
                WHERE LOWER(company_name) LIKE LOWER(?) ESCAPE '!'
                   OR LOWER(position_name) LIKE LOWER(?) ESCAPE '!'
                   OR LOWER(location) LIKE LOWER(?) ESCAPE '!'
                ORDER BY applied_at DESC, id DESC
                """, MAPPER, pattern, pattern, pattern);
    }

    public Optional<JobApplication> findById(long id) {
        return jdbc.query("SELECT * FROM job_application WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public long insert(ApplicationRequest r, LocalDateTime now) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO job_application
                    (company_name, position_name, location, requirements, applied_at, channel, job_url, notes, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, r.companyName()); ps.setString(2, r.positionName());
            ps.setString(3, r.location()); ps.setString(4, r.requirements());
            ps.setObject(5, r.appliedAt()); ps.setString(6, r.channel());
            ps.setString(7, r.jobUrl()); ps.setString(8, r.notes());
            ps.setObject(9, now); ps.setObject(10, now);
            return ps;
        }, keys);
        return java.util.Objects.requireNonNull(keys.getKey()).longValue();
    }

    public int update(long id, ApplicationRequest r, LocalDateTime now) {
        return jdbc.update("""
                UPDATE job_application SET company_name=?, position_name=?, location=?, requirements=?,
                applied_at=?, channel=?, job_url=?, notes=?, updated_at=? WHERE id=?
                """, r.companyName(), r.positionName(), r.location(), r.requirements(), r.appliedAt(),
                r.channel(), r.jobUrl(), r.notes(), now, id);
    }

    public int delete(long id) { return jdbc.update("DELETE FROM job_application WHERE id=?", id); }
}
