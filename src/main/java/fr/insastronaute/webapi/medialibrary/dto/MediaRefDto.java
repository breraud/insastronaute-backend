package fr.insastronaute.webapi.medialibrary.dto;

public record MediaRefDto(@jakarta.validation.constraints.NotNull java.util.UUID submissionId,
                          @jakarta.validation.constraints.Min(0) int index) { }
