package cc.liuying.workhelper.unapplied;

import cc.liuying.workhelper.common.DataWriteLock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class UnappliedService {
    public static final Set<String> REASONS=Set.of("CET4","CET6","NO_POSITION","EDUCATION","EXPERIENCE","LOCATION","OTHER");
    private final UnappliedRepository repository;
    private final DataWriteLock writes;
    private final Clock clock;
    public UnappliedService(UnappliedRepository repository,DataWriteLock writes,Clock clock) {this.repository=repository;this.writes=writes;this.clock=clock;}
    public List<UnappliedCompany> list(String search,String month) {
        if(search.length()>200) throw bad("搜索内容最多200字");
        YearMonth parsed=null;
        if(!month.isEmpty()) {try {parsed=YearMonth.parse(month);}catch(DateTimeParseException e){throw bad("月份格式须为 YYYY-MM");} if(parsed.getYear()<1000||parsed.getYear()>9999)throw bad("月份年份无效");}
        return repository.list(search.strip(),parsed);
    }
    public UnappliedCompany get(long id) {return repository.get(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"未投记录不存在或已被删除"));}
    public static void validate(UnappliedRequest r) {
        if(r.companyName()==null||r.companyName().isBlank()||r.companyName().length()>200)throw bad("公司名称必填且最多200字");
        if(r.reason()==null||!REASONS.contains(r.reason()))throw bad("请选择有效的未投原因");
        if(r.otherReason()==null||r.otherReason().length()>1000)throw bad("具体原因最多1000字");
        if(r.reason().equals("OTHER")&&r.otherReason().isBlank())throw bad("选择其他时请填写具体原因");
        if(!r.reason().equals("OTHER")&&!r.otherReason().isEmpty())throw bad("仅其他原因可以填写具体原因");
        if(r.viewedDate()==null||r.viewedDate().getYear()<1000||r.viewedDate().getYear()>9999)throw bad("请填写有效的看过日期");
    }
    @Transactional public UnappliedCompany save(Long id,UnappliedRequest input) {
        writes.changed(); if(id!=null)get(id);
        var r=new UnappliedRequest(input.companyName().strip(),input.reason(),input.reason().equals("OTHER")?input.otherReason().strip():"",input.viewedDate());validate(r);
        try {if(id==null)id=repository.insert(r,LocalDateTime.now(clock));else repository.update(id,r,LocalDateTime.now(clock));}
        catch(DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"该公司已有未投记录，请编辑原记录");}
        return get(id);
    }
    @Transactional public void delete(long id) {writes.changed();get(id);repository.delete(id);}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
