package fr.insastronaute.webapi.qrlink.controller;

import fr.insastronaute.webapi.qrlink.dto.QrLinkConfigDto;
import fr.insastronaute.webapi.qrlink.dto.QrLinkConfigUpdateDto;
import fr.insastronaute.webapi.qrlink.service.QrLinkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/qr-link")
public class AdminQrLinkController {

    private final QrLinkService qrLinkService;

    public AdminQrLinkController(QrLinkService qrLinkService) {
        this.qrLinkService = qrLinkService;
    }

    @GetMapping
    public QrLinkConfigDto getConfig() {
        return qrLinkService.getConfig();
    }

    @PutMapping
    public QrLinkConfigDto updateConfig(@RequestBody QrLinkConfigUpdateDto update) {
        return qrLinkService.updateConfig(update);
    }
}
