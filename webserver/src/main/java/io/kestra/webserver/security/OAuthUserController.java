package io.kestra.webserver.security;

import io.kestra.webserver.controllers.api.UserController;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * OAuth-enabled version of UserController that properly extracts roles from OAuth authentication.
 * This replaces the base UserController when OAuth is enabled.
 */
@Slf4j
@Controller("/api/v1")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Requires(property = "micronaut.security.enabled", value = "true")
@Requires(property = "micronaut.security.oauth2.enabled", value = "true")
@Replaces(UserController.class)
public class OAuthUserController {

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/auth/me")
    @Operation(
        tags = {"Auth"},
        summary = "Get current user profile (OAuth)",
        description = "Returns the authenticated user's profile including username and roles from OAuth provider"
    )
    @ApiResponse(
        responseCode = "200",
        description = "User profile retrieved successfully",
        content = {@Content(schema = @Schema(implementation = UserController.UserProfile.class))}
    )
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated"
    )
    public UserController.UserProfile getCurrentUser(@Nullable Authentication authentication) {
        if (authentication == null) {
            log.warn("No authentication found in request");
            return UserController.UserProfile.builder()
                .username("anonymous")
                .roles(Collections.emptyList())
                .authenticated(false)
                .build();
        }

        String username = authentication.getName();
        Collection<String> rolesCollection = authentication.getRoles();
        List<String> roles = rolesCollection != null ? 
            new ArrayList<>(rolesCollection) : 
            Collections.emptyList();

        log.info("User '{}' authenticated via OAuth with {} roles: {}", username, roles.size(), roles);
        
        return UserController.UserProfile.builder()
            .username(username)
            .roles(roles)
            .authenticated(true)
            .build();
    }
}
