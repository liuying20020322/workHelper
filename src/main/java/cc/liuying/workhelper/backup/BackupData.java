package cc.liuying.workhelper.backup;

import cc.liuying.workhelper.application.JobApplication;
import cc.liuying.workhelper.process.ProcessRecord;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Version 1: all business records, with stable IDs and local business timestamps. No credentials. */
public record BackupData(String format, int version, Instant exportedAt, String timeZone,
                         List<JobApplication> applications, List<ProcessData> processes, List<QuestionData> questions) {
    public record ProcessData(ProcessRecord record, String summary, long interviewVersion) {}
    public record QuestionData(long id, long processId, String question, String answer, String review,
                               int sortOrder, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record Counts(int applications, int processes, int questions) {}
    public Counts counts() { return new Counts(applications.size(),processes.size(),questions.size()); }
}
