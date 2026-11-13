package fr.insastronaute.webapi.card.print.repository;

import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CardPrintTemplateRepository extends JpaRepository<CardPrintTemplate, UUID> {
}
