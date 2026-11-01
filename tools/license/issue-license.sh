#!/usr/bin/env bash
# Issues one Pro activation code and records it in issued.csv.
#
# Usage: ./issue-license.sh <order-id>
# Example: ./issue-license.sh DS-0001
#
# The private key is read from tools/license/keys/license-private.pem, or from
# $DEEPSWEEP_LICENSE_KEY if set (recommended once you move the key offline).
# The output code is what the buyer pastes into the app: Shop -> Premium -> "У меня есть код".
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
key="${DEEPSWEEP_LICENSE_KEY:-$here/keys/license-private.pem}"

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <order-id>" >&2
    exit 2
fi
if [[ ! -f "$key" ]]; then
    echo "Private key not found: $key (set DEEPSWEEP_LICENSE_KEY)" >&2
    exit 2
fi

order="$1"
issued="$(date +%s)"
payload="v1|deepsweep.pro|${order}|${issued}"

b64url() { base64 -w0 | tr '+/' '-_' | tr -d '='; }

payload_b64="$(printf '%s' "$payload" | b64url)"
signature="$(printf '%s' "$payload" | openssl dgst -sha256 -sign "$key" | b64url)"
code="DSWP1.${payload_b64}.${signature}"

printf '%s\n' "$code"
printf '%s,%s,%s\n' "$order" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$code" >> "$here/issued.csv"
echo "recorded in $here/issued.csv" >&2
