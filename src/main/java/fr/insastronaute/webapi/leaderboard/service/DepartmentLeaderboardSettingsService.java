package fr.insastronaute.webapi.leaderboard.service;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.leaderboard.domain.DepartmentLeaderboardSettings;
import fr.insastronaute.webapi.leaderboard.dto.AdminDepartmentLeaderboardSettingsDto;
import fr.insastronaute.webapi.leaderboard.repository.DepartmentLeaderboardSettingsRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DepartmentLeaderboardSettingsService {
    private final DepartmentRepository departments; private final DepartmentLeaderboardSettingsRepository settings; private final UserRepository users; private final RealmRoleDirectory roles;
    public DepartmentLeaderboardSettingsService(DepartmentRepository departments, DepartmentLeaderboardSettingsRepository settings, UserRepository users, @org.springframework.beans.factory.annotation.Autowired(required=false) RealmRoleDirectory roles) { this.departments=departments; this.settings=settings; this.users=users; this.roles=roles; }
    @Transactional(readOnly=true) public List<AdminDepartmentLeaderboardSettingsDto> list() { Map<UUID,Long> counts=counts(); Map<UUID,DepartmentLeaderboardSettings> configured=new HashMap<>(); settings.findAll().forEach(s->configured.put(s.getDepartmentId(),s)); return departments.findAll().stream().sorted(Comparator.comparing(Department::getShortName)).map(d->new AdminDepartmentLeaderboardSettingsDto(d.getId(),d.getName(),d.getShortName(),Optional.ofNullable(configured.get(d.getId())).map(DepartmentLeaderboardSettings::getTheoreticalHeadcount).orElse(null),counts.getOrDefault(d.getId(),0L))).toList(); }
    @Transactional public AdminDepartmentLeaderboardSettingsDto update(UUID id, int headcount) { Department d=departments.findById(id).orElseThrow(()->new NotFoundException("Departement introuvable.")); DepartmentLeaderboardSettings s=settings.findById(id).orElseGet(DepartmentLeaderboardSettings::new); s.setDepartment(d); s.setTheoreticalHeadcount(headcount); settings.save(s); return list().stream().filter(row->row.departmentId().equals(id)).findFirst().orElseThrow(); }
    private Map<UUID,Long> counts(){ Map<UUID,Long> r=new HashMap<>(); users.findAll().stream().filter(this::rankable).filter(u->u.getDepartment()!=null).forEach(u->r.merge(u.getDepartment(),1L,Long::sum)); return r; }
    private boolean rankable(AppUser u){ if(Boolean.TRUE.equals(u.getHiddenFromLeaderboards()))return false; if(roles==null||!roles.isKnown())return true; return u.getKeycloakSub()!=null&&roles.students().contains(u.getKeycloakSub())&&!roles.teamStaff().contains(u.getKeycloakSub()); }
}
