package fr.insastronaute.webapi.onboarding.service;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.CodedConflictException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.onboarding.dto.OnboardingStateDto;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public OnboardingService(UserRepository userRepository, DepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public OnboardingStateDto state(AppUser user) {
        return new OnboardingStateDto(
                user.getOnboardingStatus(),
                user.getOnboardingGenderChosenAt() != null,
                user.getGender(),
                user.getDisplayName(),
                user.getEmail(),
                departmentName(user),
                user.getOnboardingRejectionReason()
        );
    }

    @Transactional
    public void chooseGender(AppUser user, Gender gender) {
        requireNotStarted(user);
        user.setGender(gender);
        user.setOnboardingGenderChosenAt(TimeTravelContext.instantNow());
        userRepository.save(user);
    }

    @Transactional
    public void submit(AppUser user) {
        requireNotStarted(user);

        // C'est l'horodatage du choix, et non la valeur du genre, qui autorise la
        // soumission : NEUTRAL est a la fois un choix legitime et le defaut de la
        // colonne, donc la valeur seule ne dit pas si le joueur a choisi.
        if (user.getOnboardingGenderChosenAt() == null) {
            throw new CodedBadRequestException(
                    "ONBOARDING_GENDER_REQUIRED",
                    "Choisis ton genre avant de soumettre ton inscription."
            );
        }

        user.setOnboardingStatus(OnboardingStatus.PENDING);
        user.setOnboardingSubmittedAt(TimeTravelContext.instantNow());
        userRepository.save(user);
    }

    /**
     * Un compte refuse recoit son propre message.
     *
     * <p>Lui repondre que son inscription a deja ete envoyee serait faux et le
     * mettrait sur une fausse piste : il attendrait une decision qui est deja tombee,
     * alors que la suite pour lui est humaine, pas logicielle.</p>
     */
    private void requireNotStarted(AppUser user) {
        if (user.getOnboardingStatus() == OnboardingStatus.NOT_STARTED) {
            return;
        }

        if (user.getOnboardingStatus() == OnboardingStatus.REJECTED) {
            throw new CodedConflictException(
                    "ONBOARDING_REJECTED",
                    "Ton inscription a ete refusee. Rapproche-toi d'un respo."
            );
        }

        throw new CodedConflictException(
                "ONBOARDING_ALREADY_SUBMITTED",
                "Ton inscription a deja ete envoyee."
        );
    }

    private String departmentName(AppUser user) {
        if (user.getDepartment() == null) {
            return null;
        }
        return departmentRepository.findById(user.getDepartment())
                .map(Department::getShortName)
                .orElse(null);
    }
}
