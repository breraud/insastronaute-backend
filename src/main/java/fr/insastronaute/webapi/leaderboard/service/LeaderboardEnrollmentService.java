package fr.insastronaute.webapi.leaderboard.service;

import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardEntry;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardSnapshot;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardConfigRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardEntryRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardSnapshotRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LeaderboardEnrollmentService {

    private final LeaderboardConfigRepository configRepository;
    private final LeaderboardSnapshotRepository snapshotRepository;
    private final LeaderboardEntryRepository entryRepository;
    private final DepartmentRepository departmentRepository;

    public LeaderboardEnrollmentService(
            LeaderboardConfigRepository configRepository,
            LeaderboardSnapshotRepository snapshotRepository,
            LeaderboardEntryRepository entryRepository,
            DepartmentRepository departmentRepository
    ) {
        this.configRepository = configRepository;
        this.snapshotRepository = snapshotRepository;
        this.entryRepository = entryRepository;
        this.departmentRepository = departmentRepository;
    }

    public void addNewUserToLatestUserSnapshots(AppUser user) {
        if (Boolean.TRUE.equals(user.getHiddenFromLeaderboards())) {
            return;
        }

        Map<UUID, String> departments = departmentRepository.findAllByVirtualFalse().stream()
                .collect(Collectors.toMap(Department::getId, Department::getShortName));

        configRepository.findAll().stream()
                .filter(config -> config.getScope() == LeaderboardScope.USER)
                .forEach(config -> snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config)
                        .ifPresent(snapshot -> entryRepository.save(zeroEntry(snapshot, user, departments))));
    }

    private LeaderboardEntry zeroEntry(
            LeaderboardSnapshot snapshot,
            AppUser user,
            Map<UUID, String> departments
    ) {
        LeaderboardEntry entry = new LeaderboardEntry();

        entry.setSnapshot(snapshot);
        entry.setRank((int) entryRepository.countBySnapshot(snapshot) + 1);
        entry.setSubjectId(user.getId());
        entry.setName(displayName(user));
        entry.setDepartment(user.getDepartment() == null ? null : departments.get(user.getDepartment()));
        entry.setScore(0L);

        return entry;
    }

    private String displayName(AppUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }

        return user.getEmail();
    }
}
