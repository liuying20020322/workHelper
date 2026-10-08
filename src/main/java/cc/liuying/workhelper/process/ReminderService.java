package cc.liuying.workhelper.process;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import cc.liuying.workhelper.process.ProcessRequest.Status;
import cc.liuying.workhelper.process.ProcessRequest.TimeMode;

@Service
public class ReminderService {
    public enum View { RECENT, ALL, COMPLETED }
    public record Reminder(ProcessRecord process, String companyName, String positionName, String jobLocation,
                           LocalDateTime effectiveAt, boolean overdue, boolean inProgress, String dayLabel) {}
    public record Page(String timeZone, LocalDate today, LocalDate throughDate, LocalDateTime now,
                       List<Reminder> overdue, List<Reminder> items) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReminderService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Page list(View view) {
        // Capture time once: rows on a midnight boundary must use the same business date.
        var now = LocalDateTime.now(clock);
        var today = now.toLocalDate();
        var endExclusive = today.plusDays(3).atStartOfDay();
        String status = view == View.COMPLETED ? "COMPLETED" : "PENDING";
        var rows = jdbc.query("""
                SELECT p.*, a.company_name, a.position_name, a.location AS job_location
                FROM application_process p JOIN job_application a ON a.id=p.application_id
                WHERE p.status=? AND p.time_mode IN ('SCHEDULED','DEADLINE')
                """, (rs, row) -> {
            var process = ProcessRepository.MAPPER.mapRow(rs, row);
            var effective = process.timeMode() == TimeMode.DEADLINE ? process.deadlineAt() : process.startAt();
            var expires = process.timeMode() == TimeMode.DEADLINE ? process.deadlineAt()
                    : process.endAt() == null ? process.startAt() : process.endAt();
            boolean overdue = process.status() == Status.PENDING && expires.isBefore(now);
            boolean inProgress = process.status() == Status.PENDING && process.timeMode() == TimeMode.SCHEDULED
                    && !process.startAt().isAfter(now) && process.endAt() != null && !process.endAt().isBefore(now);
            return new Reminder(process, rs.getString("company_name"), rs.getString("position_name"),
                    rs.getString("job_location"), effective, overdue, inProgress, label(effective.toLocalDate(), today));
        }, status).stream().sorted(Comparator.comparing(Reminder::effectiveAt)
                .thenComparingLong(item -> item.process().id())).toList();

        var overdue = rows.stream().filter(Reminder::overdue).toList();
        // A cross-day appointment still in progress stays visible even if it started before today.
        var items = rows.stream().filter(item -> !item.overdue())
                .filter(item -> view != View.RECENT || item.effectiveAt().isBefore(endExclusive))
                .toList();
        return new Page(clock.getZone().getId(), today, today.plusDays(2), now, overdue, items);
    }

    private static String label(LocalDate date, LocalDate today) {
        if (date.equals(today)) return "今天";
        if (date.equals(today.plusDays(1))) return "明天";
        if (date.equals(today.plusDays(2))) return "后天";
        return date.toString();
    }
}
