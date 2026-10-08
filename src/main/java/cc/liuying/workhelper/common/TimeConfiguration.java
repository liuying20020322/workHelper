package cc.liuying.workhelper.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {
    @Bean
    public Clock businessClock(@Value("${app.time-zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
