package cc.liuying.workhelper.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Shared database lock: all business writes and backups participate, including across app instances. */
@Component
public class DataWriteLock {
    private final JdbcTemplate jdbc;
    public DataWriteLock(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional(propagation=Propagation.MANDATORY)
    public long lock() {
        return jdbc.queryForObject("SELECT revision FROM data_revision WHERE id=1 FOR UPDATE",Long.class);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void changed() {
        lock();
        jdbc.update("UPDATE data_revision SET revision=revision+1 WHERE id=1");
    }
}
