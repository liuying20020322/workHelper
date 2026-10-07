package cc.liuying.workhelper.process;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ProcessRequest(
        @NotNull(message="请选择流程阶段") Stage stage,
        @Size(max=200, message="轮次名称最多200字") String roundName,
        @NotNull(message="请选择时间方式") TimeMode timeMode,
        LocalDateTime startAt, LocalDateTime endAt, LocalDateTime deadlineAt,
        @NotNull(message="请选择安排状态") Status status,
        @Size(max=2000, message="地点或链接最多2000字") String location,
        @Size(max=10000, message="备注最多10000字") String notes,
        LocalDateTime occurredAt) {
    public enum TimeMode { SCHEDULED, DEADLINE, RECORD_ONLY }
    public enum Status { PENDING, COMPLETED, CANCELLED }
}
