package cc.liuying.workhelper.application;

import java.time.LocalDateTime;

public record JobApplication(long id, String companyName, String positionName, String location,
        String requirements, LocalDateTime appliedAt, String channel, String jobUrl, String notes,
        String currentStage, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
