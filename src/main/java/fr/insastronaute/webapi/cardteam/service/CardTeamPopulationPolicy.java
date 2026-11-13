package fr.insastronaute.webapi.cardteam.service;

import fr.insastronaute.webapi.common.web.CodedConflictException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

@Component
public class CardTeamPopulationPolicy {

    private final RealmRoleDirectory roleDirectory;

    public CardTeamPopulationPolicy(RealmRoleDirectory roleDirectory) {
        this.roleDirectory = roleDirectory;
    }

    public void requireHomogeneous(Collection<AppUser> users) {
        Set<String> staff = knownStaff();
        boolean hasStaff = false;
        boolean hasPlayer = false;
        for (AppUser user : users) {
            if (staff.contains(user.getKeycloakSub())) {
                hasStaff = true;
            } else {
                hasPlayer = true;
            }
        }
        if (hasStaff && hasPlayer) {
            throw new CodedConflictException(
                    "CARD_TEAM_STAFF_PLAYER_MIX",
                    "Les comptes staff et joueurs ne peuvent pas appartenir a la meme equipe."
            );
        }
    }

    public Set<String> knownStaff() {
        if (!roleDirectory.isKnown()) {
            throw new CardTeamRoleDirectoryUnavailableException();
        }
        return roleDirectory.teamStaff();
    }
}
