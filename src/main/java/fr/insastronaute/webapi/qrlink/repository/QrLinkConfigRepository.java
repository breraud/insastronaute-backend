package fr.insastronaute.webapi.qrlink.repository;

import fr.insastronaute.webapi.qrlink.domain.QrLinkConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QrLinkConfigRepository extends JpaRepository<QrLinkConfig, Short> {
}
