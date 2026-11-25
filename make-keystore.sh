#!/usr/bin/env bash
# Membuat keystore rilis pribadi. SIMPAN file & password ini baik-baik:
# update aplikasi di masa depan harus ditandatangani dengan kunci yang sama.
set -euo pipefail
OUT="${1:-nyieuncv-release.jks}"
keytool -genkeypair -v -keystore "$OUT" -alias nyieuncv \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=NyieunCV, O=NyieunCV, C=ID"
echo
echo "Keystore dibuat: $OUT"
echo "Untuk GitHub Actions, isi secret KEYSTORE_B64 dengan:"
echo "  base64 -w0 $OUT"
echo "dan secret KEYSTORE_PASSWORD, KEY_ALIAS (=nyieuncv), KEY_PASSWORD."
