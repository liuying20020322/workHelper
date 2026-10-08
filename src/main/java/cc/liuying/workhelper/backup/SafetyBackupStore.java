package cc.liuying.workhelper.backup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import static java.nio.file.StandardOpenOption.*;

@Component
public class SafetyBackupStore {
    private static final Pattern NAME=Pattern.compile("before-restore-\\d{8}T\\d{6}Z-[0-9a-f-]{36}\\.json");
    private final Path directory;
    public SafetyBackupStore(@Value("${app.backup-directory:backups}") String directory) { this.directory=Path.of(directory).toAbsolutePath().normalize(); }
    public record FileInfo(String name,long bytes,Instant createdAt) {}
    public String save(byte[] bytes) {
        String name="before-restore-"+DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now())+"-"+UUID.randomUUID()+".json";
        Path temp=directory.resolve(name+".tmp");
        try {
            Files.createDirectories(directory);
            try(var channel=FileChannel.open(temp,CREATE_NEW,WRITE)) {
                var buffer=ByteBuffer.wrap(bytes); while(buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            try { Files.move(temp,directory.resolve(name),StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,directory.resolve(name)); }
            return name;
        } catch(IOException e) {
            try { Files.deleteIfExists(temp); } catch(IOException ignored) { }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"恢复前自动备份写入失败，未替换数据。请检查 backups 目录权限和磁盘空间");
        }
    }
    public List<FileInfo> list() {
        if(!Files.exists(directory)) return List.of();
        try(var files=Files.list(directory)) {
            return files.filter(p -> NAME.matcher(p.getFileName().toString()).matches() && Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                    .map(p -> { try { return new FileInfo(p.getFileName().toString(),Files.size(p),Files.getLastModifiedTime(p).toInstant()); }
                        catch(IOException e) { throw new java.io.UncheckedIOException(e); } })
                    .sorted(Comparator.comparing(FileInfo::createdAt).reversed()).toList();
        } catch(IOException | java.io.UncheckedIOException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"无法读取本地自动备份目录，请检查权限"); }
    }
    public byte[] read(String name) {
        if(!NAME.matcher(name).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"备份文件名无效");
        Path file=directory.resolve(name);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"自动备份文件不存在");
        try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=input.readNBytes(BackupCodec.MAX_BYTES+1);
            if(bytes.length>BackupCodec.MAX_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"自动备份文件超过大小限制");
            return bytes;
        } catch(IOException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"读取自动备份失败，请检查文件权限"); }
    }
}
