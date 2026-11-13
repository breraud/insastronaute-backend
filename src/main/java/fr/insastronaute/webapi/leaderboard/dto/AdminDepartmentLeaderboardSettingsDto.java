package fr.insastronaute.webapi.leaderboard.dto;

import java.util.UUID;

public record AdminDepartmentLeaderboardSettingsDto(UUID departmentId, String name, String shortName, Integer theoreticalHeadcount, long registeredCount) { }
