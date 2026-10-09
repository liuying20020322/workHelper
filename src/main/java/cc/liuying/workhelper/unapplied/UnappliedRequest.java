package cc.liuying.workhelper.unapplied;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UnappliedRequest(@NotBlank @Size(max=200) String companyName,
                              @NotBlank @Size(max=40) String reason,
                              @NotNull @Size(max=1000) String otherReason,
                              @NotNull LocalDate viewedDate) {}
