package cc.liuying.workhelper;

import cc.liuying.workhelper.unapplied.*;
import cc.liuying.workhelper.backup.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:unapplied;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.backup-directory=target/test-unapplied-backups"})
class UnappliedTests {
    @Autowired UnappliedService service;
    @Autowired BackupService backups;
    @Autowired BackupCodec codec;
    @Autowired JdbcTemplate jdbc;
    UnappliedRequest input(String name,String date) {return new UnappliedRequest(name,"NO_POSITION","",LocalDate.parse(date));}
    @BeforeEach void clear(){jdbc.update("DELETE FROM unapplied_company");jdbc.update("DELETE FROM job_application");}
    @Test void monthSearchIncludesBoundariesAndCombinesWithLiteralCompanySearch(){
        service.save(null,input("九月公司","2026-09-30"));
        var first=service.save(null,input("公司%_一","2026-10-01"));
        var last=service.save(null,input("十月公司","2026-10-31"));
        service.save(null,input("十一月公司","2026-11-01"));
        assertThat(service.list("","2026-10")).extracting(UnappliedCompany::id).containsExactly(last.id(),first.id());
        assertThat(service.list("%_","2026-10")).extracting(UnappliedCompany::id).containsExactly(first.id());
        assertThatThrownBy(()->service.list("","2026-13")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void duplicateOtherValidationEditAndDelete(){
        var original=service.save(null,input(" 公司 ","2026-10-09"));
        assertThat(original.companyName()).isEqualTo("公司");
        assertThatThrownBy(()->service.save(null,input("公司","2026-10-08"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.save(null,new UnappliedRequest("另一家","OTHER","  ",LocalDate.now()))).isInstanceOf(ResponseStatusException.class);
        var edited=service.save(original.id(),new UnappliedRequest("公司","OTHER","暂未招聘",LocalDate.parse("2026-09-01")));
        assertThat(edited.createdAt()).isEqualTo(original.createdAt());
        assertThat(edited.otherReason()).isEqualTo("暂未招聘");
        service.delete(edited.id());assertThat(service.list("","")).isEmpty();
    }
    @Test void backupRoundTripOldVersionAndRevisionProtection(){
        var item=service.save(null,input("备份公司","2026-10-09"));
        byte[] bytes=backups.export();assertThat(codec.decode(bytes).version()).isEqualTo(2);
        var stale=backups.preview(bytes);
        service.save(item.id(),input("改名","2026-10-08"));
        assertThatThrownBy(()->backups.restore(bytes,stale.currentRevision(),stale.sha256(),"REPLACE_ALL")).isInstanceOf(ResponseStatusException.class);
        var preview=backups.preview(bytes);
        backups.restore(bytes,preview.currentRevision(),preview.sha256(),"REPLACE_ALL");
        assertThat(service.get(item.id())).isEqualTo(item);
        var json=JsonMapper.builder().build();var old=(ObjectNode)json.readTree(bytes);old.put("version",1);old.remove("unappliedCompanies");
        byte[] legacy=json.writeValueAsBytes(old);var oldPreview=backups.preview(legacy);
        assertThat(oldPreview.incoming().unappliedCompanies()).isZero();
        backups.restore(legacy,oldPreview.currentRevision(),oldPreview.sha256(),"REPLACE_ALL");
        assertThat(service.list("","")).isEmpty();
        var broken=(ObjectNode)json.readTree(bytes);broken.remove("unappliedCompanies");
        assertThatThrownBy(()->codec.decode(json.writeValueAsBytes(broken))).isInstanceOf(ResponseStatusException.class);
    }
}
