#!/bin/sh
set -e

echo 'Asteptam ca Vault sa porneasca complet...'
sleep 5

echo 'Verificam daca secretele exista deja...'
if vault kv get secret/sb-ecom >/dev/null 2>&1; then
    echo 'Secretele exista deja in Vault. Sarim initializarea.'
    exit 0
fi

echo 'Injectam secretele in Vault...'
vault kv put secret/sb-ecom \
    spring.security.oauth2.client.registration.github.client-id="$GITHUB_CLIENT_ID" \
    spring.security.oauth2.client.registration.github.client-secret="$GITHUB_CLIENT_SECRET" \
    spring.security.oauth2.client.registration.google.client-id="$GOOGLE_CLIENT_ID" \
    spring.security.oauth2.client.registration.google.client-secret="$GOOGLE_CLIENT_SECRET" \
    stripe.secret.key="$STRIPE_SECRET_KEY" \
    spring.app.jwtSecret="$JWT_SECRET" \
    spring.ai.openai.api-key="$OPENAI_API_KEY" \
    spring.datasource.password="$DB_PASSWORD"

echo '!!! SECRETELE AU FOST SALVATE IN VAULT CU SUCCES !!!'
