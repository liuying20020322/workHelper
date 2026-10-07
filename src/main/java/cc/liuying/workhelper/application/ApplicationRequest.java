package cc.liuying.workhelper.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ApplicationRequest(
        @NotBlank(message = "公司名称不能为空") @Size(max = 200, message = "公司名称最多200字") String companyName,
        @NotBlank(message = "岗位名称不能为空") @Size(max = 200, message = "岗位名称最多200字") String positionName,
        @Size(max = 200, message = "工作地点最多200字") String location,
        @Size(max = 10000, message = "职位要求最多10000字") String requirements,
        LocalDateTime appliedAt,
        @Size(max = 200, message = "投递渠道最多200字") String channel,
        @Size(max = 2000, message = "职位链接最多2000字")
        @Pattern(regexp = "(?i)^(https?://[^\\s]+)?$", message = "职位链接请填写完整的 http:// 或 https:// 地址") String jobUrl,
        @Size(max = 10000, message = "备注最多10000字") String notes) {
}
