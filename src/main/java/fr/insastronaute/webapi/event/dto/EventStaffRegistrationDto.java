package fr.insastronaute.webapi.event.dto;

/** @param blocked le staff ne peut plus prendre de place sur un événement */
public record EventStaffRegistrationDto(boolean blocked) {
}
