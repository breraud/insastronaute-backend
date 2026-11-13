package fr.insastronaute.webapi.leaderboard.service;

import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardConfig;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardEntry;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardSnapshot;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardConfigRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardEntryRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardSnapshotRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardEnrollmentServiceTest {

    @Mock
    private LeaderboardConfigRepository configRepository;

    @Mock
    private LeaderboardSnapshotRepository snapshotRepository;

    @Mock
    private LeaderboardEntryRepository entryRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private LeaderboardEnrollmentService enrollmentService;

    @Test
    void addsNewVisibleUserAtZeroToLatestUserSnapshots() {
        UUID departmentId = UUID.randomUUID();
        AppUser user = user(departmentId, false);
        LeaderboardConfig userConfig = config(LeaderboardScope.USER);
        LeaderboardConfig departmentConfig = config(LeaderboardScope.DEPARTMENT);
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        ArgumentCaptor<LeaderboardEntry> entryCaptor = ArgumentCaptor.forClass(LeaderboardEntry.class);

        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "ITI")));
        when(configRepository.findAll()).thenReturn(List.of(userConfig, departmentConfig));
        when(snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(userConfig)).thenReturn(Optional.of(snapshot));
        when(entryRepository.countBySnapshot(snapshot)).thenReturn(5L);

        enrollmentService.addNewUserToLatestUserSnapshots(user);

        verify(entryRepository).save(entryCaptor.capture());
        LeaderboardEntry entry = entryCaptor.getValue();

        assertThat(entry.getRank()).isEqualTo(6);
        assertThat(entry.getName()).isEqualTo("New User");
        assertThat(entry.getDepartment()).isEqualTo("ITI");
        assertThat(entry.getScore()).isZero();
    }

    @Test
    void doesNotAddHiddenUserToLatestUserSnapshots() {
        enrollmentService.addNewUserToLatestUserSnapshots(user(UUID.randomUUID(), true));

        verify(configRepository, never()).findAll();
        verify(entryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static LeaderboardConfig config(LeaderboardScope scope) {
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(scope);
        config.setMetric(LeaderboardMetric.TOURNAMENT_POINTS);

        return config;
    }

    private static Department department(UUID id, String shortName) {
        Department department = new Department();
        department.setId(id);
        department.setShortName(shortName);

        return department;
    }

    private static AppUser user(UUID departmentId, boolean hidden) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("new@example.com");
        user.setDisplayName("New User");
        user.setDepartment(departmentId);
        user.setHiddenFromLeaderboards(hidden);

        return user;
    }
}
