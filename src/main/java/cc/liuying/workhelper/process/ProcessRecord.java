package cc.liuying.workhelper.process;

import java.time.LocalDateTime;
import cc.liuying.workhelper.process.ProcessRequest.Status;
import cc.liuying.workhelper.process.ProcessRequest.TimeMode;

public record ProcessRecord(long id, long applicationId, Stage stage, String roundName, TimeMode timeMode,
        LocalDateTime startAt, LocalDateTime endAt, LocalDateTime deadlineAt, Status status,
        String location, String notes, LocalDateTime occurredAt, LocalDateTime createdAt, LocalDateTime updatedAt,
        boolean hasInterview) {}
