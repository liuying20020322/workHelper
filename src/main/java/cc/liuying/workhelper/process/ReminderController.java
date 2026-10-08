package cc.liuying.workhelper.process;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderService service;
    public ReminderController(ReminderService service) { this.service = service; }

    @GetMapping
    public ReminderService.Page list(@RequestParam(defaultValue="RECENT") ReminderService.View view) {
        return service.list(view);
    }
}
