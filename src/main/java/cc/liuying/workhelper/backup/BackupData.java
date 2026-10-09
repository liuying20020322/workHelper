package cc.liuying.workhelper.backup;

import cc.liuying.workhelper.application.JobApplication;
import cc.liuying.workhelper.process.ProcessRecord;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Version 2 adds unapplied companies; version 1 remains readable. No credentials. */
public record BackupData(String format, int version, Instant exportedAt, String timeZone,
                         List<JobApplication> applications, List<ProcessData> processes, List<QuestionData> questions, List<cc.liuying.workhelper.unapplied.UnappliedCompany> unappliedCompanies) {
    public BackupData(String format,int version,Instant exportedAt,String timeZone,List<JobApplication> applications,List<ProcessData> processes,List<QuestionData> questions) { this(format,version,exportedAt,timeZone,applications,processes,questions,List.of()); }
    public record ProcessData(ProcessRecord record, String summary, long interviewVersion) {}
    public record QuestionData(long id, long processId, String question, String answer, String review,
                               int sortOrder, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record Counts(int applications, int processes, int questions, int unappliedCompanies) {}
    public Counts counts() { return new Counts(applications.size(),processes.size(),questions.size(),unappliedCompanies.size()); }
}
