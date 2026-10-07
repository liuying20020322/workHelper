package cc.liuying.workhelper.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
public class ApplicationService {
    private static final Set<String> STAGES = Set.of("APPLIED", "ASSESSMENT", "WRITTEN_TEST", "INTERVIEW_1",
            "INTERVIEW_2", "INTERVIEW_3", "OFFER", "REJECTED", "WITHDRAWN");
    private final ApplicationRepository repository;
    private final ZoneId zone;

    public ApplicationService(ApplicationRepository repository, @Value("${app.time-zone}") String zone) {
        this.repository = repository;
        this.zone = ZoneId.of(zone);
    }

    public List<JobApplication> list(String search, String stage) {
        if (search.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搜索内容最多200字");
        if (!stage.isEmpty() && !STAGES.contains(stage))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的投递阶段");
        // No process records exist in phase one, so every application is APPLIED.
        if (!stage.isEmpty() && !stage.equals("APPLIED")) return List.of();
        return repository.findAll(search.strip());
    }

    public JobApplication get(long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "投递记录不存在或已被删除"));
    }

    @Transactional
    public JobApplication create(ApplicationRequest request) {
        var now = LocalDateTime.now(zone);
        return get(repository.insert(normalize(request, now), now));
    }

    @Transactional
    public JobApplication update(long id, ApplicationRequest request) {
        var existing = get(id);
        if (repository.update(id, normalize(request, existing.appliedAt()), LocalDateTime.now(zone)) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "投递记录不存在或已被删除");
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (repository.delete(id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "投递记录不存在或已被删除");
    }

    private ApplicationRequest normalize(ApplicationRequest r, LocalDateTime fallback) {
        if (r.appliedAt() != null && (r.appliedAt().getYear() < 1000 || r.appliedAt().getYear() > 9999))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "投递时间年份须在1000至9999之间");
        if (clean(r.companyName()).isBlank() || clean(r.positionName()).isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "公司名称和岗位名称不能为空");
        return new ApplicationRequest(clean(r.companyName()), clean(r.positionName()), clean(r.location()),
                clean(r.requirements()), r.appliedAt() == null ? fallback : r.appliedAt(),
                clean(r.channel()), clean(r.jobUrl()), clean(r.notes()));
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
