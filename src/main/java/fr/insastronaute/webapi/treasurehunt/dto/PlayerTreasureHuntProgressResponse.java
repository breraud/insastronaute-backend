package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.List;

public record PlayerTreasureHuntProgressResponse(List<PlayerPathProgressResponse> paths) { }
