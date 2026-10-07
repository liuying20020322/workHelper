package cc.liuying.workhelper.application;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
    private final ApplicationService service;
    public ApplicationController(ApplicationService service) { this.service = service; }

    @GetMapping
    public List<JobApplication> list(@RequestParam(defaultValue = "") String search,
                                    @RequestParam(defaultValue = "") String stage) {
        return service.list(search, stage);
    }

    @GetMapping("/{id}")
    public JobApplication get(@PathVariable long id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobApplication create(@Valid @RequestBody ApplicationRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    public JobApplication update(@PathVariable long id, @Valid @RequestBody ApplicationRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { service.delete(id); }
}
