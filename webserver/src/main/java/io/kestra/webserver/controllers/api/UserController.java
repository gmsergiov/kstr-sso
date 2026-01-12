package io.kestra.webserver.controllers.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

@Slf4j
@Controller("/api/v1")
public class UserController {

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/auth/me")
    @Operation(
        tags = {"Auth"},
        summary = "Get current user profile",
        description = "Returns the authenticated user's profile including username and roles. " +
                      "Works with both Basic Auth (OSS) and OAuth/OIDC authentication."
    )
    @ApiResponse(
        responseCode = "200",
        description = "User profile retrieved successfully",
        content = {@Content(schema = @Schema(implementation = UserProfile.class))}
    )
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated"
    )
    public UserProfile getCurrentUser(HttpRequest<?> request, @Nullable Principal principal) {
        String username = "unknown";
        List<String> roles = Collections.emptyList();

        // Get username from Principal (works with both Basic Auth and OAuth)
        if (principal != null) {
            username = principal.getName();
            log.debug("User principal: {}", username);
        }

        // Try to extract roles from request attributes (set by OAuth mapper or security filters)
        Object rolesAttr = request.getAttribute("roles").orElse(null);
        if (rolesAttr instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> rolesList = (List<String>) rolesAttr;
            roles = rolesList;
        }

        // If no roles in attributes, check if user context has roles (for OAuth)
        if (roles.isEmpty()) {
            // In OAuth mode, roles would be in the authentication object
            // For basic auth in OSS, we'll return empty roles list
            // Enterprise edition will override this with proper role checking
            roles = Collections.emptyList();
        }

        log.debug("Returning profile for user '{}' with {} roles", username, roles.size());
        
        return UserProfile.builder()
            .username(username)
            .roles(roles)
            .authenticated(principal != null)
            .build();
    }

    /**
     * User profile data transfer object
     */
    @Value
    @Builder
    public static class UserProfile {
        /**
         * The username of the authenticated user
         */
        String username;
        
        /**
         * List of roles assigned to the user (e.g., ROLE_ADMIN, ROLE_USER).
         * May be empty in OSS mode with basic auth.
         */
        List<String> roles;

        /**
         * Whether the user is authenticated
         */
        boolean authenticated;
    }
}
