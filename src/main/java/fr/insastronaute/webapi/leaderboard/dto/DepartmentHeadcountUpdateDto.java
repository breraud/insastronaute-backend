package fr.insastronaute.webapi.leaderboard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DepartmentHeadcountUpdateDto(@NotNull @Positive Integer theoreticalHeadcount) { }
