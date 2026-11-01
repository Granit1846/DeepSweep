#!/usr/bin/env bash
# Creates the Pro licence key pair (ECDSA P-256). Run once, then back the private key up.
#
# The private key signs activation codes; the public key is embedded in
# app/src/main/java/com/skeorz/lab/deepsweep/meta/License.kt. Losing the private key
# means no new codes can be issued; leaking it means anyone can issue them.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
keys="$here/keys"
mkdir -p "$keys"

if [[ -e "$keys/license-private.pem" ]]; then
    echo "Key already exists: $keys/license-private.pem" >&2
    echo "Delete it only if you are ready to ship an app update with a new public key." >&2
    exit 1
fi

openssl ecparam -name prime256v1 -genkey -noout -out "$keys/license-private.pem"
chmod 600 "$keys/license-private.pem" 2>/dev/null || true

echo "Private key: $keys/license-private.pem"
echo "Back it up offline (password manager or USB) and never commit it."
echo
echo "Public key for License.kt:"
openssl ec -in "$keys/license-private.pem" -pubout -outform DER 2>/dev/null | base64 -w0
echo
