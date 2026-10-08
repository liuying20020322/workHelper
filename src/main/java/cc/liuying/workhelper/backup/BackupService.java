package cc.liuying.workhelper.backup;

import cc.liuying.workhelper.common.DataWriteLock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@Service
public class BackupService {
    private final BackupRepository repository;
    private final BackupCodec codec;
    private final SafetyBackupStore safety;
    private final DataWriteLock writes;
    public BackupService(BackupRepository repository,BackupCodec codec,SafetyBackupStore safety,DataWriteLock writes) {
        this.repository=repository; this.codec=codec; this.safety=safety; this.writes=writes;
    }
    public record Preview(String sha256,long currentRevision,String timeZone,Instant exportedAt,BackupData.Counts incoming,BackupData.Counts current) {}
    public record Restored(String safetyBackup,BackupData.Counts restored) {}
    @Transactional
    public byte[] export() { writes.lock(); return codec.encode(repository.snapshot()); }
    @Transactional
    public Preview preview(byte[] bytes) {
        var data=codec.decode(bytes);
        long revision=writes.lock();
        return new Preview(codec.hash(bytes),revision,data.timeZone(),data.exportedAt(),data.counts(),repository.snapshot().counts());
    }
    @Transactional
    public Restored restore(byte[] bytes,long expectedRevision,String expectedHash,String confirmation) {
        if(!"REPLACE_ALL".equals(confirmation)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请先校验并确认替换全部业务数据");
        var data=codec.decode(bytes);
        if(!codec.hash(bytes).equals(expectedHash)) throw new ResponseStatusException(HttpStatus.CONFLICT,"备份文件已改变，请重新校验");
        if(writes.lock()!=expectedRevision) throw new ResponseStatusException(HttpStatus.CONFLICT,"校验后业务数据已变化，请重新校验并核对数量");
        String file=safety.save(codec.encode(repository.snapshot()));
        repository.replace(data);
        writes.changed();
        return new Restored(file,data.counts());
    }
}
