# OAuth SSO Role-Based Authentication Implementation

## Summary
This implementation adds proper role-based authorization to the Kestra UI when using OAuth/OIDC SSO authentication.

## Changes Made

### Backend Changes

#### 1. **UserController.java** - New User Profile Endpoint
- **Location**: `webserver/src/main/java/io/kestra/webserver/controllers/api/UserController.java`
- **Endpoint**: `GET /api/v1/auth/me`
- **Purpose**: Returns the authenticated user's profile including username and roles
- **Response**:
  ```json
  {
    "username": "user@example.com",
    "roles": ["ROLE_ADMIN", "ROLE_USER"]
  }
  ```

### Frontend Changes

#### 2. **Auth Store** - Role Management
- **Location**: `ui/src/override/stores/auth.ts`
- **Changes**:
  - Added `username` and `roles` properties to `Me` class
  - Added `hasRole()` method to check for specific roles
  - Added `loadUser()` action to fetch user profile from backend
  - Updated authorization methods to check for `ROLE_ADMIN` on write operations (CREATE, UPDATE, DELETE)
  - Read operations are allowed for all authenticated users

#### 3. **Main.js** - User Profile Loading
- **Location**: `ui/src/main.js`
- **Changes**:
  - Imported `useAuthStore`
  - Added user profile loading after authentication check
  - Loads user roles before rendering protected routes

#### 4. **Axios Interceptor** - 403 Error Handling
- **Location**: `ui/src/utils/axios.ts`
- **Changes**:
  - Added specific handling for 403 Forbidden responses
  - Shows user-friendly error message when permissions are insufficient
  - Message: "You don't have permission to perform this action. Please contact your administrator if you need access."

## How It Works

### Authentication Flow
1. User logs in via OAuth provider (Keycloak, Okta, Azure AD, etc.)
2. `OAuthRolesMapper` extracts roles from OAuth token claims
3. Micronaut Security authenticates the user with roles
4. Frontend fetches user profile via `GET /api/v1/auth/me`
5. Auth store stores username and roles
6. UI components check roles before showing/hiding buttons

### Authorization Flow
1. User attempts an action (e.g., Create Flow)
2. Frontend checks if user has `ROLE_ADMIN` role
3. If yes, button is enabled
4. If no, button is disabled/hidden
5. Backend always validates permissions (defense in depth)
6. If user bypasses UI, backend returns 403 Forbidden
7. Frontend shows friendly error message

## Role-Based Permissions

### Write Operations (Require ROLE_ADMIN)
- Create Flow
- Update Flow
- Delete Flow
- Bulk Update Flows
- Update Namespace Flows

### Read Operations (All Authenticated Users)
- View Flows
- Search Flows
- View Flow Graph
- View Flow Revisions
- Export Flows

## Testing

### Test with ROLE_ADMIN User
1. Log in with a user having `ROLE_ADMIN` role
2. Verify all buttons are visible (Create, Edit, Delete)
3. Verify operations succeed

### Test with ROLE_USER User
1. Log in with a user having only `ROLE_USER` role
2. Verify Create/Edit/Delete buttons are hidden/disabled
3. Verify read operations work (viewing, searching)
4. Try to call API directly → Should get 403 Forbidden

### Test Error Handling
1. Use browser DevTools to call protected endpoint without permissions
2. Verify 403 error shows user-friendly message
3. Verify no crashes or unexpected behavior

## Configuration

### Backend (application.yml)
```yaml
micronaut:
  security:
    enabled: true
    oauth2:
      enabled: true
      clients:
        keycloak:
          client-id: ${OAUTH_CLIENT_ID}
          client-secret: ${OAUTH_CLIENT_SECRET}
          openid:
            issuer: ${OAUTH_ISSUER_URL}
```

### OAuth Provider Setup
Ensure your OAuth provider sends roles in one of these claim formats:
- Keycloak: `realm_access.roles` or `resource_access.{client}.roles`
- Okta: `groups`
- Azure AD: `roles`
- Generic: `roles` or `groups`

## Security Notes

1. **Defense in Depth**: Frontend checks are for UX only. Backend always validates permissions.
2. **Token Validation**: Micronaut Security validates OAuth tokens on every request.
3. **Role Normalization**: All roles are normalized to `ROLE_UPPERCASE` format.
4. **Default Role**: Users without roles get `ROLE_USER` by default.

## Troubleshooting

### Users Don't Have Roles
- Check OAuth provider configuration
- Verify roles are included in token claims
- Check `OAuthRolesMapper` logs for extracted roles
- Ensure role claim path matches your provider

### Buttons Still Visible to Non-Admin Users
- Clear browser cache and reload
- Check browser console for errors loading user profile
- Verify `/api/v1/auth/me` endpoint returns correct roles
- Check auth store is properly imported and used

### 403 Errors After Login
- User doesn't have required permissions
- Contact administrator to assign `ROLE_ADMIN` role
- Check OAuth provider role mappings

## Next Steps

### Optional Enhancements
1. **Fine-grained Permissions**: Add namespace-level permissions
2. **Custom Roles**: Support custom role names beyond ROLE_ADMIN
3. **Role Management UI**: Allow admins to assign roles via UI
4. **Audit Logging**: Log permission denials for security monitoring
5. **Role Caching**: Cache user roles to reduce API calls

## Files Modified

### Backend
- `webserver/src/main/java/io/kestra/webserver/controllers/api/UserController.java` (new)
- `webserver/src/main/java/io/kestra/webserver/security/OAuthRolesMapper.java` (already exists)
- `webserver/src/main/java/io/kestra/webserver/controllers/api/FlowController.java` (already has @Secured)

### Frontend
- `ui/src/override/stores/auth.ts`
- `ui/src/main.js`
- `ui/src/utils/axios.ts`

## API Documentation

### GET /api/v1/auth/me
**Description**: Get current user profile

**Authentication**: Required (Bearer token or Basic Auth)

**Response**: 200 OK
```json
{
  "username": "john.doe@company.com",
  "roles": ["ROLE_ADMIN", "ROLE_USER"]
}
```

**Error Responses**:
- 401 Unauthorized: Not authenticated
- 500 Internal Server Error: Server error

**Example**:
```bash
curl -X GET http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer YOUR_TOKEN"
```
