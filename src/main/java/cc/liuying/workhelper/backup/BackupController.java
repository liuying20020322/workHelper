package cc.liuying.workhelper.backup;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/backups")
public class BackupController {
    private final BackupService service;
    private final SafetyBackupStore safety;
    public BackupController(BackupService service,SafetyBackupStore safety) { this.service=service; this.safety=safety; }
    @GetMapping public ResponseEntity<byte[]> export() {
        String stamp=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        return download(service.export(),"workHelper-"+stamp+"Z.json");
    }
    @PostMapping(value="/preview",consumes=MediaType.APPLICATION_JSON_VALUE)
    public BackupService.Preview preview(HttpServletRequest request) { return service.preview(read(request)); }
    @PostMapping(value="/restore",consumes=MediaType.APPLICATION_JSON_VALUE)
    public BackupService.Restored restore(HttpServletRequest request,
            @RequestHeader("X-Backup-Revision") long revision,@RequestHeader("X-Backup-SHA256") String hash,
            @RequestHeader("X-Backup-Confirmation") String confirmation) {
        return service.restore(read(request),revision,hash,confirmation);
    }
    @GetMapping("/safety") public List<SafetyBackupStore.FileInfo> list() { return safety.list(); }
    @GetMapping("/safety/{name}") public ResponseEntity<byte[]> safety(@PathVariable String name) { return download(safety.read(name),name); }
    private ResponseEntity<byte[]> download(byte[] bytes,String filename) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"").body(bytes);
    }
    private byte[] read(HttpServletRequest request) {
        try {
            byte[] bytes=request.getInputStream().readNBytes(BackupCodec.MAX_BYTES+1);
            if(bytes.length>BackupCodec.MAX_BYTES) throw new ResponseStatusException(HttpStatus.valueOf(413),"备份文件不能超过 32 MB");
            return bytes;
        } catch(IOException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"备份文件读取失败，请重新选择文件"); }
    }
}
