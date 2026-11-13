package fr.insastronaute.webapi.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "INSAstronaute Web API",
                version = "v1",
                description = "Protected API documentation for administrators."
        ),
        tags = {
                @Tag(name = "Authentication", description = "Session and authenticated user bootstrap endpoints."),
                @Tag(name = "Security", description = "CSRF and browser security support endpoints."),
                @Tag(name = "Users", description = "Current user profile endpoints."),
                @Tag(name = "Departments", description = "Public department reference data."),
                @Tag(name = "Challenges", description = "Player challenge discovery, submission, and result endpoints."),
                @Tag(name = "Friends", description = "Friend search, friend request, and friendship management endpoints."),
                @Tag(name = "Notifications", description = "Player notification listing and read-state endpoints."),
                @Tag(name = "Cards", description = "Player collectible card deck endpoints."),
                @Tag(name = "Proof Uploads", description = "Proof draft upload and cleanup endpoints."),
                @Tag(name = "Place", description = "Player r/place canvas snapshot and pixel-placement endpoints."),
                @Tag(name = "Moderation", description = "Submission review queue, proof access, review locks, and decisions."),
                @Tag(name = "Leaderboards", description = "Public leaderboard read endpoints."),
                @Tag(name = "Admin Articles", description = "Article draft and publication administration endpoints."),
                @Tag(name = "Admin Challenges", description = "Challenge administration endpoints."),
                @Tag(name = "Admin Challenge Categories", description = "Challenge category administration endpoints."),
                @Tag(name = "Admin Cards", description = "Collectible card administration endpoints."),
                @Tag(name = "Admin Users", description = "User administration endpoints."),
                @Tag(name = "Admin Departments", description = "Department moderation link administration endpoints."),
                @Tag(name = "Admin Leaderboards", description = "Leaderboard configuration and release administration endpoints."),
                @Tag(name = "Admin Place", description = "r/place event configuration administration endpoints."),
                @Tag(name = "Admin Tournaments", description = "Tournament attendance, point settlement, and victory tier administration endpoints."),
                @Tag(name = "Achievements", description = "Player achievement list, progress, unlock popup queue, and friend completion endpoints."),
                @Tag(name = "Admin Achievements", description = "Achievement authoring endpoints and the catalogue of available key phrases."),
                @Tag(name = "Admin Action Events", description = "Read-only, append-only register of administration actions.")
        }
)
public class OpenApiConfig {

    @Bean
    GroupedOpenApi appOpenApi() {
        return GroupedOpenApi.builder()
                .group("app")
                .pathsToExclude("/api/admin/**", "/api/moderation/**")
                .addOpenApiCustomizer(openApi -> setInfo(
                        openApi,
                        "INSAstronaute App API",
                        "Protected API documentation for player-facing endpoints."
                ))
                .build();
    }

    @Bean
    GroupedOpenApi adminOpenApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/api/admin/**")
                .addOpenApiCustomizer(openApi -> setInfo(
                        openApi,
                        "INSAstronaute Admin API",
                        "Protected API documentation for administrator endpoints."
                ))
                .build();
    }

    @Bean
    GroupedOpenApi moderationOpenApi() {
        return GroupedOpenApi.builder()
                .group("moderation")
                .pathsToMatch("/api/moderation/**")
                .addOpenApiCustomizer(openApi -> setInfo(
                        openApi,
                        "INSAstronaute Moderation API",
                        "Protected API documentation for moderation endpoints."
                ))
                .build();
    }

    private static void setInfo(OpenAPI openApi, String title, String description) {
        openApi.setInfo(new io.swagger.v3.oas.models.info.Info()
                .title(title)
                .version("v1")
                .description(description));

        Set<String> usedTagNames = new HashSet<>();
        openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    if (operation.getTags() != null) {
                        usedTagNames.addAll(operation.getTags());
                    }
                })
        );

        if (openApi.getTags() != null) {
            openApi.setTags(openApi.getTags().stream()
                    .filter(tag -> usedTagNames.contains(tag.getName()))
                    .toList());
        }
    }
}
