package cc.liuying.workhelper.unapplied;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.time.*;
import java.util.*;

@Repository
public class UnappliedRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<UnappliedCompany> MAPPER=(rs,i)->new UnappliedCompany(rs.getLong("id"),rs.getString("company_name"),rs.getString("reason"),rs.getString("other_reason"),rs.getObject("viewed_date",LocalDate.class),rs.getObject("created_at",LocalDateTime.class),rs.getObject("updated_at",LocalDateTime.class));
    private static final String SELECT="SELECT * FROM unapplied_company";
    public UnappliedRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<UnappliedCompany> list(String search, YearMonth month) {
        String pattern="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        if(month==null) return jdbc.query(SELECT+" WHERE LOWER(company_name) LIKE LOWER(?) ESCAPE '!' ORDER BY viewed_date DESC,id DESC",MAPPER,pattern);
        return jdbc.query(SELECT+" WHERE LOWER(company_name) LIKE LOWER(?) ESCAPE '!' AND viewed_date BETWEEN ? AND ? ORDER BY viewed_date DESC,id DESC",MAPPER,pattern,month.atDay(1),month.atEndOfMonth());
    }
    public Optional<UnappliedCompany> get(long id) { return jdbc.query(SELECT+" WHERE id=?",MAPPER,id).stream().findFirst(); }
    public long insert(UnappliedRequest r,LocalDateTime now) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO unapplied_company(company_name,reason,other_reason,viewed_date,created_at,updated_at) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,r.companyName());ps.setString(2,r.reason());ps.setString(3,r.otherReason());ps.setObject(4,r.viewedDate());ps.setObject(5,now);ps.setObject(6,now);return ps;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    public void update(long id,UnappliedRequest r,LocalDateTime now) { jdbc.update("UPDATE unapplied_company SET company_name=?,reason=?,other_reason=?,viewed_date=?,updated_at=? WHERE id=?",r.companyName(),r.reason(),r.otherReason(),r.viewedDate(),now,id); }
    public void delete(long id) { jdbc.update("DELETE FROM unapplied_company WHERE id=?",id); }
}
