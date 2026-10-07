package cc.liuying.workhelper.process;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/applications/{applicationId}/processes")
public class ProcessController {
    private final ProcessService service;
    public ProcessController(ProcessService service) { this.service=service; }
    @GetMapping public List<ProcessRecord> list(@PathVariable long applicationId) { return service.list(applicationId); }
    @GetMapping("/{id}") public ProcessRecord get(@PathVariable long applicationId,@PathVariable long id) { return service.get(applicationId,id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProcessRecord create(@PathVariable long applicationId,@Valid @RequestBody ProcessRequest request) { return service.create(applicationId,request); }
    @PutMapping("/{id}")
    public ProcessRecord update(@PathVariable long applicationId,@PathVariable long id,@Valid @RequestBody ProcessRequest request) { return service.update(applicationId,id,request); }
    public record StatusRequest(@NotNull(message="请选择安排状态") ProcessRequest.Status status) {}
    @PatchMapping("/{id}/status")
    public ProcessRecord status(@PathVariable long applicationId,@PathVariable long id,@Valid @RequestBody StatusRequest request) { return service.status(applicationId,id,request.status()); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long applicationId,@PathVariable long id) { service.delete(applicationId,id); }
}
