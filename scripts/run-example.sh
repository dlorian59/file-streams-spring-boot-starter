#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/.." && pwd -P)"
cd "$repo_dir"
# EN: Generate a local-only development certificate; never commit the private key.
# RU: Создаём локальный сертификат разработки; закрытый ключ не попадает в Git.
mkdir -p build/tls
if [[ ! -f build/tls/cert.pem || ! -f build/tls/key.pem ]] \
    || ! openssl x509 -checkend 3600 -noout -in build/tls/cert.pem >/dev/null 2>&1; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 2 \
    -keyout build/tls/key.pem -out build/tls/cert.pem \
    -subj /CN=localhost -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1'
fi
# EN: Credentials come from the standard AWS chain, including environment variables or a local profile.
# RU: Credentials берутся из стандартной цепочки AWS, включая окружение или локальный профиль.
export DEMO_TLS_CERT="${DEMO_TLS_CERT:-$repo_dir/build/tls/cert.pem}"
export DEMO_TLS_KEY="${DEMO_TLS_KEY:-$repo_dir/build/tls/key.pem}"
exec ./gradlew "${1:-:example:run}" --no-daemon --max-workers=2
