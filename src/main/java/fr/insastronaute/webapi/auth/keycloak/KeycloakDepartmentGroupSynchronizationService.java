package fr.insastronaute.webapi.auth.keycloak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class KeycloakDepartmentGroupSynchronizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakDepartmentGroupSynchronizationService.class);
    private static final Set<String> MANAGED_GROUPS = Set.of("CFI", "EP", "GM", "ITI", "MECA", "MRIE");

    private final KeycloakGroupMembershipGateway gateway;
    private final boolean enabled;

    public KeycloakDepartmentGroupSynchronizationService(
            KeycloakGroupMembershipGateway gateway,
            @Value("${app.keycloak.department-group-sync.enabled:true}") boolean enabled
    ) {
        this.gateway = gateway;
        this.enabled = enabled;
    }

    public void synchronize(OidcUser user) {
        if (!enabled) {
            return;
        }

        List<String> departmentClaim = user.getClaimAsStringList("department");
        if (departmentClaim == null) {
            return;
        }

        try {
            synchronize(user.getSubject(), normalizeRequestedGroups(departmentClaim));
        } catch (RuntimeException exception) {
            LOGGER.warn("Keycloak department group synchronization failed for user {}", user.getSubject(), exception);
        }
    }

    private void synchronize(String userId, Set<String> requestedGroupNames) {
        Map<String, KeycloakGroupMembershipGateway.Group> realmGroups = new LinkedHashMap<>();
        for (KeycloakGroupMembershipGateway.Group group : gateway.listRealmGroups()) {
            String normalizedName = normalize(group.name());
            if (MANAGED_GROUPS.contains(normalizedName)) {
                realmGroups.putIfAbsent(normalizedName, group);
            }
        }

        Set<String> currentGroupIds = new LinkedHashSet<>();
        for (KeycloakGroupMembershipGateway.Group group : gateway.listUserGroups(userId)) {
            currentGroupIds.add(group.id());
        }

        for (Map.Entry<String, KeycloakGroupMembershipGateway.Group> entry : realmGroups.entrySet()) {
            boolean requested = requestedGroupNames.contains(entry.getKey());
            boolean current = currentGroupIds.contains(entry.getValue().id());

            if (requested && !current) {
                gateway.joinGroup(userId, entry.getValue().id());
            } else if (!requested && current) {
                gateway.leaveGroup(userId, entry.getValue().id());
            }
        }

        for (String requestedGroupName : requestedGroupNames) {
            if (!realmGroups.containsKey(requestedGroupName)) {
                LOGGER.warn("Managed Keycloak department group {} does not exist", requestedGroupName);
            }
        }
    }

    private Set<String> normalizeRequestedGroups(List<String> claimValues) {
        Set<String> requestedGroups = new LinkedHashSet<>();

        for (String claimValue : claimValues) {
            String normalizedValue = normalize(claimValue);
            if (MANAGED_GROUPS.contains(normalizedValue)) {
                requestedGroups.add(normalizedValue);
            } else if (!normalizedValue.isEmpty()) {
                LOGGER.warn("Ignoring unknown Keycloak department value {}", claimValue);
            }
        }

        return requestedGroups;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
