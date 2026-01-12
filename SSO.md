# Security Policy

## Supported Versions

We provide security updates for the following versions of Kestra:

- The `latest` release 
- Up to two previous minor versions released as a backport upon customer request.

If you are using an unsupported version, we recommend upgrading to the `latest` version to receive security fixes.

## Reporting a Vulnerability

If you discover a security vulnerability in Kestra, please report it to us privately to ensure a responsible disclosure process. You can contact our security team at:

**security@kestra.io**

### Guidelines for Reporting
- Provide a detailed description of the issue, including steps to reproduce it if possible.
- Do not disclose the vulnerability publicly until we have confirmed and patched the issue.
- If you believe the issue has critical severity, please indicate so in your report to help us prioritize.

## Our Commitment

- We will acknowledge your report within **2 business days**.
- We will work to verify and address the issue as quickly as possible.
- Once the issue is resolved, we will notify you of the fix.

## Acknowledgments

We are happy to credit those who report vulnerabilities responsibly in our release notes, unless you prefer to remain anonymous. If you would like to be acknowledged, please include this in your report.

Thank you for helping to make Kestra more secure!

# 1. Build the image
docker build -t kestra-sso:test .

# 2. Run Keycloak first
docker network create kestra-net

docker run -d --name keycloak \
  --network kestra-net \
  -p 8180:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev

# 3. Configure Keycloak (via UI at http://localhost:8180)
# - Create realm 'kestra'
# - Create client 'kestra-client'
# - Get the client secret

# 4. Run Kestra with OAuth
docker run -d --name kestra \
  --network kestra-net \
  -p 8080:8080 \
  -e OAUTH_CLIENT_ID="kestra-client" \
  -e OAUTH_CLIENT_SECRET="your-client-secret" \
  -e OAUTH_ISSUER_URL="http://keycloak:8080/realms/kestra" \
  kestra-sso:test

# 5. Check logs
docker logs -f kestra

# 6. Access Kestra
# Open http://localhost:8080 and you should be redirected to Keycloak login