# Testing OAuth SSO Role-Based Authentication

## Quick Test Guide

### 1. Start Kestra with OAuth Configuration

#### Option A: With Keycloak (Recommended for Testing)

```bash
# Start Keycloak in Docker
docker run -p 8180:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev

# Configure Keycloak:
# 1. Open http://localhost:8180
# 2. Login with admin/admin
# 3. Create a new realm called "kestra"
# 4. Create a client called "kestra-app"
# 5. Set valid redirect URIs: http://localhost:8080/*
# 6. Create users with different roles:
#    - admin-user with role: ROLE_ADMIN
#    - read-user with role: ROLE_USER
```

#### Configure Kestra

Create `cli/src/main/resources/application-override.yml`:

```yaml
micronaut:
  security:
    enabled: true
    oauth2:
      enabled: true
      clients:
        keycloak:
          client-id: kestra-app
          client-secret: YOUR_CLIENT_SECRET
          openid:
            issuer: http://localhost:8180/realms/kestra
    redirect:
      login-failure: /ui/login?error=true

kestra:
  repository:
    type: h2
  queue:
    type: h2
```

#### Start Kestra

```bash
./gradlew runLocal
```

Or for standalone:

```bash
./gradlew run --args="server standalone"
```

### 2. Test User Profile Endpoint

#### Test with cURL

```bash
# First, login via browser and copy the JWT token from cookies

# Then test the endpoint
curl -X GET http://localhost:8080/api/v1/auth/me \
  -H "Cookie: JWT=YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json"

# Expected response:
{
  "username": "admin@example.com",
  "roles": ["ROLE_ADMIN", "ROLE_USER"],
  "authenticated": true
}
```

#### Test with Browser DevTools

1. Login to Kestra at http://localhost:8080
2. Open Browser DevTools (F12)
3. Go to Console tab
4. Run:

```javascript
fetch('/api/v1/auth/me', {
  credentials: 'include'
})
.then(r => r.json())
.then(console.log)
```

Expected output:
```json
{
  "username": "your-username",
  "roles": ["ROLE_ADMIN"],
  "authenticated": true
}
```

### 3. Test Role-Based Authorization

#### Test as ROLE_ADMIN User

1. Login with admin user
2. Navigate to Flows page
3. **Expected**: See "Create" button
4. **Expected**: See "Edit" button on existing flows
5. **Expected**: See "Delete" button
6. Click "Create Flow"
7. **Expected**: Successfully creates flow

#### Test as ROLE_USER (Non-Admin)

1. Login with read-only user
2. Navigate to Flows page
3. **Expected**: "Create" button is HIDDEN or DISABLED
4. **Expected**: "Edit" button is HIDDEN or DISABLED
5. **Expected**: "Delete" button is HIDDEN or DISABLED
6. **Expected**: Can view and search flows
7. Try to call API directly from DevTools:

```javascript
fetch('/api/v1/flows', {
  method: 'POST',
  headers: {'Content-Type': 'application/x-yaml'},
  body: 'id: test\nnamespace: test\ntasks: []',
  credentials: 'include'
})
.then(r => r.text())
.then(console.log)
```

**Expected**: 403 Forbidden error with message: "You don't have permission to perform this action..."

### 4. Test UI Error Handling

#### Test 403 Forbidden Display

1. Login as non-admin user
2. Open Browser DevTools
3. Try to create a flow via API:

```javascript
fetch('/api/v1/flows', {
  method: 'POST',
  headers: {'Content-Type': 'application/x-yaml'},
  body: 'id: unauthorized-test\nnamespace: test\ntasks: []',
  credentials: 'include'
})
```

4. **Expected**: See toast notification with message: "You don't have permission to perform this action. Please contact your administrator if you need access."

### 5. Test Role Extraction from Different Providers

#### Keycloak

Verify roles are extracted from:
- `realm_access.roles` (realm-level roles)
- `resource_access.{client-id}.roles` (client-specific roles)

#### Okta

Verify roles are extracted from:
- `groups` claim

#### Azure AD

Verify roles are extracted from:
- `roles` claim

#### Check Logs

```bash
# In Kestra logs, you should see:
grep "User '.*' authenticated from provider" logs/kestra.log

# Example output:
User 'admin@example.com' authenticated from provider 'keycloak' with roles: [ROLE_ADMIN, ROLE_USER]
```

### 6. Test Frontend Auth Store

#### Open Browser Console

```javascript
// Check if auth store loaded user profile
const authStore = window.$nuxt?.$store?.state?.auth || {};
console.log('User:', authStore.user);
console.log('Roles:', authStore.user?.roles);

// Test role checking
authStore.user?.hasRole('ROLE_ADMIN')  // Should return true for admin users
authStore.user?.hasRole('ROLE_USER')   // Should return true for any authenticated user
```

### 7. Integration Test

Run the provided test:

```bash
./gradlew :webserver:test --tests UserControllerTest
```

Expected output:
```
UserControllerTest > getCurrentUser() PASSED
UserControllerTest > userProfileContainsRoles() PASSED
```

## Troubleshooting

### Issue: 401 Unauthorized on /auth/me

**Cause**: User not authenticated

**Fix**: 
1. Verify OAuth configuration
2. Check if user is logged in
3. Verify cookie or JWT token is present

### Issue: Empty roles array in response

**Cause**: Roles not properly extracted from OAuth token

**Fix**:
1. Check OAuth provider includes roles in token claims
2. Verify `OAuthRolesMapper` is logging extracted roles
3. Check Keycloak/Okta role mappings
4. Enable debug logging:

```yaml
logger:
  levels:
    io.kestra.webserver.security.OAuthRolesMapper: DEBUG
```

### Issue: Buttons still visible for non-admin users

**Cause**: Frontend not properly checking roles

**Fix**:
1. Clear browser cache
2. Hard refresh (Ctrl+Shift+R)
3. Check browser console for errors
4. Verify `/api/v1/auth/me` returns correct roles
5. Check auth store initialization:

```javascript
// In browser console
window.localStorage.clear()
location.reload()
```

### Issue: "Cannot resolve io.micronaut.security" compilation error

**Cause**: IDE needs to refresh dependencies

**Fix**:
```bash
./gradlew clean build
# Then refresh IDE project
```

### Issue: OAuth login redirect loop

**Cause**: Redirect URI mismatch

**Fix**:
1. In OAuth provider, add: `http://localhost:8080/oauth/callback/keycloak`
2. Verify `micronaut.security.redirect.login-failure` is set
3. Check OAuth client configuration

## Verification Checklist

- [ ] `/api/v1/auth/me` returns user profile with roles
- [ ] Admin users see all CRUD buttons
- [ ] Non-admin users don't see Create/Edit/Delete buttons
- [ ] Backend returns 403 for unauthorized actions
- [ ] UI shows friendly error message on 403
- [ ] Roles are properly extracted from OAuth tokens
- [ ] Both OSS and OAuth modes work correctly
- [ ] Tests pass successfully

## Next Steps After Testing

1. **Production Configuration**: Update OAuth client secrets
2. **Role Management**: Configure roles in OAuth provider
3. **Namespace Permissions**: Consider adding namespace-level authorization
4. **Audit Logging**: Enable security event logging
5. **Session Management**: Configure session timeout and refresh
6. **Multi-Tenancy**: Test with multiple tenants if applicable

## Debugging Commands

```bash
# Enable debug logging for security
export MICRONAUT_LOGGER_LEVELS_IO_KESTRA_WEBSERVER_SECURITY=DEBUG

# Check JWT token claims (decode at jwt.io)
# Copy JWT from browser cookies and paste at https://jwt.io

# Test with curl verbose mode
curl -v http://localhost:8080/api/v1/auth/me \
  -H "Cookie: JWT=YOUR_TOKEN"

# Check network requests in browser
# Open DevTools → Network tab → Filter by "auth"
```

## Support

If you encounter issues:

1. Check logs: `tail -f logs/kestra.log`
2. Verify OAuth configuration in application.yml
3. Test OAuth provider connectivity
4. Review Kestra documentation: https://kestra.io/docs
5. Join Kestra Slack: https://kestra.io/slack
