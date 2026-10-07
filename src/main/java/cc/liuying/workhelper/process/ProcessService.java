package cc.liuying.workhelper.process;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
public class ProcessService {
    private final ProcessRepository repository;
    private final ZoneId zone;
    public ProcessService(ProcessRepository repository,@Value("${app.time-zone}") String zone) {
        this.repository=repository; this.zone=ZoneId.of(zone);
    }
    public List<ProcessRecord> list(long appId) {
        if(!repository.applicationExists(appId)) throw missing();
        return repository.list(appId);
    }
    public ProcessRecord get(long appId,long id) { return repository.get(appId,id).orElseThrow(ProcessService::missing); }
    @Transactional
    public ProcessRecord create(long appId,ProcessRequest r) {
        lock(appId);
        var now=LocalDateTime.now(zone);
        long id=repository.insert(appId,normalize(r,now),now);
        recalculate(appId,now);
        return get(appId,id);
    }
    @Transactional
    public ProcessRecord update(long appId,long id,ProcessRequest r) {
        lock(appId);
        var existing=get(appId,id);
        var now=LocalDateTime.now(zone);
        repository.update(appId,id,normalize(r,existing.occurredAt()),now);
        recalculate(appId,now);
        return get(appId,id);
    }
    @Transactional
    public ProcessRecord status(long appId,long id,ProcessRequest.Status status) {
        lock(appId); get(appId,id);
        var now=LocalDateTime.now(zone);
        repository.status(appId,id,status,now);
        recalculate(appId,now);
        return get(appId,id);
    }
    @Transactional
    public void delete(long appId,long id) {
        lock(appId); get(appId,id);
        repository.delete(appId,id);
        recalculate(appId,LocalDateTime.now(zone));
    }
    private void lock(long appId) { if(!repository.lockApplication(appId)) throw missing(); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"投递或流程记录不存在，可能已被删除"); }
    private static void require(boolean valid,String message) {
        if(!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }
    private void recalculate(long appId,LocalDateTime now) {
        var records=repository.list(appId);
        // Cancellation cancels an appointment, not the recruiting stage. Ties resolve by stable ID.
        String current=records.stream().filter(r -> r.stage().isResult())
                .max(Comparator.comparing(ProcessRecord::occurredAt).thenComparingLong(ProcessRecord::id))
                .map(r -> r.stage().name()).orElseGet(() -> records.stream().map(ProcessRecord::stage)
                        .max(Comparator.naturalOrder()).map(Enum::name).orElse("APPLIED"));
        repository.setStage(appId,current,now);
    }
    private ProcessRequest normalize(ProcessRequest r,LocalDateTime fallback) {
        var start=r.startAt(); var end=r.endAt(); var deadline=r.deadlineAt();
        switch(r.timeMode()) {
            case RECORD_ONLY -> { start=null; end=null; deadline=null; }
            case SCHEDULED -> {
                require(start!=null,"定时安排必须填写开始时间");
                require(end==null || !end.isBefore(start),"结束时间不能早于开始时间");
                deadline=null;
            }
            case DEADLINE -> {
                require(deadline!=null,"截止任务必须填写截止时间");
                require(start==null || !deadline.isBefore(start),"截止时间不能早于开始时间");
                end=null;
            }
        }
        var occurred=r.occurredAt()==null ? fallback : r.occurredAt();
        for(var time : new LocalDateTime[]{start,end,deadline,occurred})
            require(time==null || (time.getYear()>=1000 && time.getYear()<=9999),"日期年份须在1000至9999之间");
        String round=r.stage().isInterview() ? clean(r.roundName()) : "";
        if(round.isEmpty()) round=r.stage().defaultRoundName();
        return new ProcessRequest(r.stage(),round,r.timeMode(),start,end,deadline,r.status(),clean(r.location()),clean(r.notes()),occurred);
    }
    private static String clean(String text) { return text==null ? "" : text.strip(); }
}
