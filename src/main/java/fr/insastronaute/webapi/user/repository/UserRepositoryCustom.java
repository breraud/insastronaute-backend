package fr.insastronaute.webapi.user.repository;

import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;
import java.util.UUID;

public interface UserRepositoryCustom {

    Page<AppUser> searchAdminUsers(
            String query,
            UUID departmentId,
            Gender gender,
            Boolean hiddenFromLeaderboards,
            Boolean staff,
            Set<String> staffSubs,
            OnboardingStatus onboardingStatus,
            Boolean deleted,
            Boolean perf,
            Pageable pageable
    );
}
