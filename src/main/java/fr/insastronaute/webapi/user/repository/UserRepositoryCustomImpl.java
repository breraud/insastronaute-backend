package fr.insastronaute.webapi.user.repository;

import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private static final Map<String, String> SORT_PROPERTIES = Map.ofEntries(
            Map.entry("id", "id"),
            Map.entry("keycloakSub", "keycloakSub"),
            Map.entry("email", "email"),
            Map.entry("displayName", "displayName"),
            Map.entry("gender", "gender"),
            Map.entry("department", "departmentId"),
            Map.entry("departmentId", "departmentId"),
            Map.entry("tokens", "tokens"),
            Map.entry("virtualCollectionPoints", "virtualCollectionPoints"),
            Map.entry("physicalCollectionPoints", "physicalCollectionPoints"),
            Map.entry("challengePoints", "challengePoints"),
            Map.entry("hiddenFromLeaderboards", "hiddenFromLeaderboards"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedAt", "updatedAt")
    );
    private static final Set<String> STRING_SORT_PROPERTIES = Set.of("keycloakSub", "email", "displayName");

    private final EntityManager entityManager;

    public UserRepositoryCustomImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<AppUser> searchAdminUsers(
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
    ) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AppUser> criteria = criteriaBuilder.createQuery(AppUser.class);
        Root<AppUser> root = criteria.from(AppUser.class);
        criteria.select(root);

        List<Predicate> predicates = adminUserPredicates(
                criteriaBuilder,
                root,
                query,
                departmentId,
                gender,
                hiddenFromLeaderboards,
                staff,
                staffSubs,
                onboardingStatus,
                deleted,
                perf
        );
        if (!predicates.isEmpty()) {
            criteria.where(predicates.toArray(Predicate[]::new));
        }
        if (pageable.getSort().isSorted()) {
            criteria.orderBy(sortOrders(criteriaBuilder, root, pageable.getSort()));
        }

        jakarta.persistence.TypedQuery<AppUser> usersQuery = entityManager.createQuery(criteria);
        if (pageable.isPaged()) {
            usersQuery.setFirstResult((int) pageable.getOffset());
            usersQuery.setMaxResults(pageable.getPageSize());
        }
        List<AppUser> users = usersQuery.getResultList();

        CriteriaQuery<Long> countCriteria = criteriaBuilder.createQuery(Long.class);
        Root<AppUser> countRoot = countCriteria.from(AppUser.class);
        countCriteria.select(criteriaBuilder.count(countRoot));
        List<Predicate> countPredicates = adminUserPredicates(
                criteriaBuilder,
                countRoot,
                query,
                departmentId,
                gender,
                hiddenFromLeaderboards,
                staff,
                staffSubs,
                onboardingStatus,
                deleted,
                perf
        );
        if (!countPredicates.isEmpty()) {
            countCriteria.where(countPredicates.toArray(Predicate[]::new));
        }
        long total = entityManager.createQuery(countCriteria).getSingleResult();

        return new PageImpl<>(users, pageable, total);
    }

    private List<Predicate> adminUserPredicates(
            CriteriaBuilder criteriaBuilder,
            Root<AppUser> root,
            String query,
            UUID departmentId,
            Gender gender,
            Boolean hiddenFromLeaderboards,
            Boolean staff,
            Set<String> staffSubs,
            OnboardingStatus onboardingStatus,
            Boolean deleted,
            Boolean perf
    ) {
        List<Predicate> predicates = new ArrayList<>();

        // Un joueur supprime dont le registre retient la fiche reste en base, et se
        // melangeait aux vivants. Il se demande maintenant a part.
        if (deleted != null) {
            predicates.add(deleted
                    ? criteriaBuilder.isNotNull(root.get("deletedAt"))
                    : criteriaBuilder.isNull(root.get("deletedAt")));
        }

        if (onboardingStatus != null) {
            predicates.add(criteriaBuilder.equal(root.get("onboardingStatus"), onboardingStatus));
        }

        // Le tri staff/joueur porte sur des identifiants Keycloak, que la base ne connait
        // pas : ils arrivent de l'annuaire. Un ensemble vide -- Keycloak muet, ou aucun
        // membre -- rend « aucun staff » et « tout le monde est joueur », ce qui est la
        // lecture juste plutot qu'un filtre ignore en silence.
        if (staff != null && staffSubs != null) {
            if (staffSubs.isEmpty()) {
                predicates.add(staff ? criteriaBuilder.disjunction() : criteriaBuilder.conjunction());
            } else {
                Predicate isStaff = root.get("keycloakSub").in(staffSubs);
                predicates.add(staff ? isStaff : criteriaBuilder.not(isStaff));
            }
        }

        if (StringUtils.hasText(query)) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("displayName"), "")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("keycloakSub")), pattern)
            ));
        }
        if (departmentId != null) {
            predicates.add(criteriaBuilder.equal(root.get("departmentId"), departmentId));
        }
        if (gender != null) {
            predicates.add(criteriaBuilder.equal(root.get("gender"), gender));
        }
        if (hiddenFromLeaderboards != null) {
            predicates.add(criteriaBuilder.equal(root.get("hiddenFromLeaderboards"), hiddenFromLeaderboards));
        }
        if (perf != null) {
            predicates.add(criteriaBuilder.equal(root.get("perf"), perf));
        }

        return predicates;
    }

    private List<Order> sortOrders(CriteriaBuilder criteriaBuilder, Root<AppUser> root, Sort sort) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            String property = SORT_PROPERTIES.get(order.getProperty());
            if (property == null) {
                throw new IllegalArgumentException("Unsupported user sort property: " + order.getProperty());
            }

            Expression<?> expression = sortExpression(criteriaBuilder, root, property, order.isIgnoreCase());
            orders.add(order.isAscending() ? criteriaBuilder.asc(expression) : criteriaBuilder.desc(expression));
        }
        return orders;
    }

    private Expression<?> sortExpression(
            CriteriaBuilder criteriaBuilder,
            Root<AppUser> root,
            String property,
            boolean ignoreCase
    ) {
        if (ignoreCase && STRING_SORT_PROPERTIES.contains(property)) {
            return criteriaBuilder.lower(root.get(property));
        }
        return root.get(property);
    }
}
