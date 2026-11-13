package fr.insastronaute.webapi.challenge.auto.types.alldailycompleted;

import java.time.LocalDate;

public record AllDailyCompletedParameters(LocalDate startDate, LocalDate endDate) {
}
