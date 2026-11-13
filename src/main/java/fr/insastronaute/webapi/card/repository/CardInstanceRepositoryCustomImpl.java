package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.CardInstance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CardInstanceRepositoryCustomImpl implements CardInstanceRepositoryCustom {

    private final EntityManager entityManager;

    public CardInstanceRepositoryCustomImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<CardInstance> searchAdmin(
            String search,
            UUID cardId,
            String publicId,
            UUID ownerUserId,
            String type,
            Integer number,
            String assignmentStatus,
            Pageable pageable
    ) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        String normalizedType = type == null || type.isBlank() ? null : type.trim().toLowerCase(Locale.ROOT);
        String normalizedPublicId = publicId == null || publicId.isBlank() ? null : publicId.trim();

        StringBuilder whereClause = new StringBuilder(" where 1 = 1");

        if (normalizedSearch != null) {
            whereClause.append(" and (lower(instance.publicId) like :searchPattern or lower(card.name) like :searchPattern or lower(owner.displayName) like :searchPattern or lower(owner.email) like :searchPattern)");
        }
        if (cardId != null) {
            whereClause.append(" and card.id = :cardId");
        }
        if (normalizedPublicId != null) {
            whereClause.append(" and instance.publicId = :publicId");
        }
        if (ownerUserId != null) {
            whereClause.append(" and owner.id = :ownerUserId");
        }
        if (normalizedType != null) {
            whereClause.append(" and (lower(card.category) like :typePattern or lower(card.name) like :typePattern)");
        }
        if (number != null) {
            whereClause.append(" and card.displayOrder = :number");
        }
        if ("ASSIGNED".equalsIgnoreCase(assignmentStatus)) {
            whereClause.append(" and owner is not null");
        } else if ("UNASSIGNED".equalsIgnoreCase(assignmentStatus)) {
            whereClause.append(" and owner is null");
        }

        String dataJpql = "select instance from CardInstance instance join fetch instance.card card left join fetch instance.owner owner"
                + whereClause
                + " order by instance.createdAt desc";

        TypedQuery<CardInstance> query = entityManager.createQuery(dataJpql, CardInstance.class);

        String countJpql = "select count(instance) from CardInstance instance join instance.card card left join instance.owner owner"
                + whereClause;

        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);

        if (normalizedSearch != null) {
            String searchPattern = "%" + normalizedSearch + "%";
            query.setParameter("searchPattern", searchPattern);
            countQuery.setParameter("searchPattern", searchPattern);
        }
        if (cardId != null) {
            query.setParameter("cardId", cardId);
            countQuery.setParameter("cardId", cardId);
        }
        if (normalizedPublicId != null) {
            query.setParameter("publicId", normalizedPublicId);
            countQuery.setParameter("publicId", normalizedPublicId);
        }
        if (ownerUserId != null) {
            query.setParameter("ownerUserId", ownerUserId);
            countQuery.setParameter("ownerUserId", ownerUserId);
        }
        if (normalizedType != null) {
            String typePattern = "%" + normalizedType + "%";
            query.setParameter("typePattern", typePattern);
            countQuery.setParameter("typePattern", typePattern);
        }
        if (number != null) {
            query.setParameter("number", number);
            countQuery.setParameter("number", number);
        }

        long total = countQuery.getSingleResult();

        if (pageable.isPaged()) {
            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());
        }

        List<CardInstance> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<CardInstance> searchAdmin(UUID cardId, String publicId, UUID ownerUserId, String type, Integer number) {
        String normalizedType = type == null || type.isBlank() ? null : type.trim().toLowerCase(Locale.ROOT);
        StringBuilder jpql = new StringBuilder("""
                select instance
                from CardInstance instance
                join fetch instance.card card
                left join fetch instance.owner owner
                where 1 = 1
                """);

        if (cardId != null) {
            jpql.append(" and card.id = :cardId");
        }
        if (publicId != null) {
            jpql.append(" and instance.publicId = :publicId");
        }
        if (ownerUserId != null) {
            jpql.append(" and owner.id = :ownerUserId");
        }
        if (normalizedType != null) {
            jpql.append(" and (lower(card.category) like :typePattern or lower(card.name) like :typePattern)");
        }
        if (number != null) {
            jpql.append(" and card.displayOrder = :number");
        }
        jpql.append(" order by instance.createdAt desc");

        TypedQuery<CardInstance> query = entityManager.createQuery(jpql.toString(), CardInstance.class);
        if (cardId != null) {
            query.setParameter("cardId", cardId);
        }
        if (publicId != null) {
            query.setParameter("publicId", publicId);
        }
        if (ownerUserId != null) {
            query.setParameter("ownerUserId", ownerUserId);
        }
        if (normalizedType != null) {
            query.setParameter("typePattern", "%" + normalizedType + "%");
        }
        if (number != null) {
            query.setParameter("number", number);
        }

        return query.getResultList();
    }
}
