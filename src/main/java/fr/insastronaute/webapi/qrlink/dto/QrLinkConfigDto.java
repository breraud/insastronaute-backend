package fr.insastronaute.webapi.qrlink.dto;

public record QrLinkConfigDto(
        String publicUrl,
        String redirectUrl,
        boolean challengeModeEnabled
) {
}
