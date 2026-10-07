package cc.liuying.workhelper.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@RestController
public class AppInfoController {
    private final ZoneId zone;
    public AppInfoController(@Value("${app.time-zone}") String zone) { this.zone = ZoneId.of(zone); }

    @GetMapping("/api/info")
    public Map<String, String> info() {
        return Map.of("application", "workHelper", "timeZone", zone.getId(), "now", LocalDateTime.now(zone).toString());
    }
}
