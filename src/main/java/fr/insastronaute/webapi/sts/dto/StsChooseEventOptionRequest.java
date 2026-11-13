package fr.insastronaute.webapi.sts.dto;

import java.util.List;

public record StsChooseEventOptionRequest(
        String optionId,
        List<String> selectedCardInstanceIds
) {
}
