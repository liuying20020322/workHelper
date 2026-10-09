package cc.liuying.workhelper.unapplied;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/unapplied-companies")
public class UnappliedController {
    private final UnappliedService service;
    public UnappliedController(UnappliedService service){this.service=service;}
    @GetMapping public List<UnappliedCompany> list(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String month){return service.list(search,month);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public UnappliedCompany create(@Valid @RequestBody UnappliedRequest r){return service.save(null,r);}
    @PutMapping("/{id}") public UnappliedCompany update(@PathVariable long id,@Valid @RequestBody UnappliedRequest r){return service.save(id,r);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id){service.delete(id);}
}
