package fr.insastronaute.webapi.qrlink.dto;

public record QrLinkConfigUpdateDto(
        String redirectUrl,
        boolean challengeModeEnabled
) {
}
