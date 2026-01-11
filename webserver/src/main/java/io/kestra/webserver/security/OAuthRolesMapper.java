package io.kestra.webserver.security;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.oauth2.endpoint.authorization.state.State;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdAuthenticationMapper;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdClaims;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdTokenResponse;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * Generic OAuth2/OIDC Role Mapper for Kestra.
 * Supports multiple OAuth2 providers including Keycloak, Okta, Azure AD, Google, GitHub, etc.
 * 
 * This mapper extracts roles from various common claim locations used by different providers:
 * - Keycloak: realm_access.roles, resource_access.{client}.roles
 * - Okta: groups claim
 * - Azure AD: roles claim
 * - Generic: roles claim or custom claim paths
 */
@Slf4j
@Singleton
@Requires(property = "micronaut.security.enabled", value = "true")
@Requires(property = "micronaut.security.oauth2.enabled", value = "true")
public class OAuthRolesMapper implements OpenIdAuthenticationMapper {

    @Override
    public Publisher<AuthenticationResponse> createAuthenticationResponse(
            String providerName,
            OpenIdTokenResponse tokenResponse,
            OpenIdClaims openIdClaims,
            @Nullable State state) {
        
        try {
            // OpenIdClaims contains all the claims from the ID token
            Map<String, Object> claims = openIdClaims != null ? openIdClaims.getClaims() : new HashMap<>();
            
            // Extract username (works for most providers)
            String username = extractUsername(claims);
            
            // Extract roles from various claim locations
            List<String> roles = extractRoles(claims);
            
            log.info("User '{}' authenticated from provider '{}' with roles: {}", username, providerName, roles);
            log.debug("All claims from provider '{}': {}", providerName, claims.keySet());
            
            return Flux.just(AuthenticationResponse.success(username, roles, claims));
        } catch (Exception e) {
            log.error("Failed to create authentication from token", e);
            return Flux.just(AuthenticationResponse.failure("Failed to process authentication token"));
        }
    }

    /**
     * Extract username from claims using common claim names across different providers
     */
    private String extractUsername(Map<String, Object> claims) {
        // Try different username claims in order of preference
        return (String) claims.getOrDefault("preferred_username",  // Keycloak, OIDC standard
            claims.getOrDefault("username",                         // Generic
            claims.getOrDefault("email",                            // Most providers
            claims.getOrDefault("upn",                              // Azure AD
            claims.getOrDefault("unique_name",                      // Azure AD (old)
            claims.getOrDefault("sub", "unknown"))))));             // OIDC standard (fallback)
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Map<String, Object> claims) {
        Set<String> roles = new LinkedHashSet<>(); // Use Set to avoid duplicates
        
        // 1. Keycloak - Realm-level roles (realm_access.roles)
        extractKeycloakRealmRoles(claims, roles);
        
        // 2. Keycloak - Client-specific roles (resource_access.{client-id}.roles)
        extractKeycloakClientRoles(claims, roles);
        
        // 3. Okta - Groups claim
        extractOktaGroups(claims, roles);
        
        // 4. Azure AD - Roles claim
        extractAzureAdRoles(claims, roles);
        
        // 5. Generic - Direct 'roles' claim (many providers)
        extractDirectRoles(claims, roles);
        
        // 6. Generic - 'groups' claim (GitHub, Google, etc.)
        extractGroupsClaim(claims, roles);
        
        // 7. Custom claim path (configurable via application.yml)
        // Future enhancement: make claim path configurable
        
        log.debug("Extracted {} unique roles from token: {}", roles.size(), roles);
        
        List<String> rolesList = new ArrayList<>(roles);
        
        // If no roles found, assign a default role
        if (rolesList.isEmpty()) {
            log.warn("No roles found in token claims, assigning default ROLE_USER");
            rolesList.add("ROLE_USER");
        }
        
        return rolesList;
    }

    /**
     * Extract Keycloak realm-level roles from realm_access.roles
     */
    @SuppressWarnings("unchecked")
    private void extractKeycloakRealmRoles(Map<String, Object> claims, Set<String> roles) {
        if (claims.containsKey("realm_access")) {
            Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                List<String> realmRoles = (List<String>) realmAccess.get("roles");
                if (realmRoles != null) {
                    realmRoles.forEach(role -> roles.add(normalizeRole(role)));
                }
            }
        }
    }

    /**
     * Extract Keycloak client-specific roles from resource_access.{client-id}.roles
     */
    @SuppressWarnings("unchecked")
    private void extractKeycloakClientRoles(Map<String, Object> claims, Set<String> roles) {
        if (claims.containsKey("resource_access")) {
            Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");
            if (resourceAccess != null) {
                resourceAccess.forEach((clientId, value) -> {
                    if (value instanceof Map) {
                        Map<String, Object> clientRoles = (Map<String, Object>) value;
                        if (clientRoles.containsKey("roles")) {
                            List<String> clientRolesList = (List<String>) clientRoles.get("roles");
                            if (clientRolesList != null) {
                                clientRolesList.forEach(role -> roles.add(normalizeRole(role)));
                            }
                        }
                    }
                });
            }
        }
    }

    /**
     * Extract Okta groups (Okta uses 'groups' claim for roles)
     */
    @SuppressWarnings("unchecked")
    private void extractOktaGroups(Map<String, Object> claims, Set<String> roles) {
        if (claims.containsKey("groups")) {
            Object groupsObj = claims.get("groups");
            if (groupsObj instanceof List) {
                List<String> groups = (List<String>) groupsObj;
                groups.forEach(group -> roles.add(normalizeRole(group)));
            }
        }
    }

    /**
     * Extract Azure AD roles from 'roles' claim
     */
    @SuppressWarnings("unchecked")
    private void extractAzureAdRoles(Map<String, Object> claims, Set<String> roles) {
        if (claims.containsKey("roles")) {
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List) {
                List<String> azureRoles = (List<String>) rolesObj;
                azureRoles.forEach(role -> roles.add(normalizeRole(role)));
            }
        }
    }

    /**
     * Extract direct 'roles' claim (generic provider)
     */
    @SuppressWarnings("unchecked")
    private void extractDirectRoles(Map<String, Object> claims, Set<String> roles) {
        // This might overlap with Azure AD, but that's okay - we use a Set
        if (claims.containsKey("roles") && claims.get("roles") instanceof List) {
            List<String> directRoles = (List<String>) claims.get("roles");
            directRoles.forEach(role -> roles.add(normalizeRole(role)));
        }
    }

    /**
     * Extract generic 'groups' claim (GitHub, Google Workspace, etc.)
     */
    @SuppressWarnings("unchecked")
    private void extractGroupsClaim(Map<String, Object> claims, Set<String> roles) {
        // This might overlap with Okta, but that's okay - we use a Set
        if (claims.containsKey("groups") && claims.get("groups") instanceof List) {
            List<String> groups = (List<String>) claims.get("groups");
            groups.forEach(group -> roles.add(normalizeRole(group)));
        }
    }

    /**
     * Normalize role name to Spring Security format (ROLE_UPPERCASE)
     * Handles various input formats:
     * - "admin" -> "ROLE_ADMIN"
     * - "ADMIN" -> "ROLE_ADMIN"
     * - "ROLE_ADMIN" -> "ROLE_ADMIN"
     * - "Admin User" -> "ROLE_ADMIN_USER"
     * - "kestra-admin" -> "ROLE_KESTRA_ADMIN"
     */
    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "ROLE_USER";
        }
        
        String normalized = role.trim().toUpperCase();
        
        // Replace spaces and hyphens with underscores
        normalized = normalized.replaceAll("[\\s-]", "_");
        
        // Add ROLE_ prefix if not present
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        
        return normalized;
    }
}