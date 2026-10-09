package cc.liuying.workhelper.unapplied;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UnappliedCompany(long id, String companyName, String reason, String otherReason,
                               LocalDate viewedDate, LocalDateTime createdAt, LocalDateTime updatedAt) {}
