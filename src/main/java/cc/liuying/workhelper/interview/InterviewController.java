package cc.liuying.workhelper.interview;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/applications/{applicationId}/processes/{processId}/interview")
public class InterviewController {
    private final InterviewService service;
    public InterviewController(InterviewService service) { this.service=service; }
    public record QuestionInput(Long id,
            @NotBlank(message="问题不能为空") @Size(max=20000,message="问题最多20000字") String question,
            @Size(max=50000,message="回答最多50000字") String answer,
            @Size(max=50000,message="复盘补充最多50000字") String review) {}
    public record Input(@NotNull @Min(0) Long version,
            @Size(max=50000,message="总结最多50000字") String summary,
            @NotNull @Size(max=200,message="每轮最多200条问答") List<@NotNull @Valid QuestionInput> questions) {}
    @GetMapping public InterviewService.Detail get(@PathVariable long applicationId,@PathVariable long processId) {
        return service.get(applicationId,processId);
    }
    @PutMapping public InterviewService.Detail save(@PathVariable long applicationId,@PathVariable long processId,
            @Valid @RequestBody Input input) { return service.save(applicationId,processId,input); }
}
