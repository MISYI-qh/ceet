#!/usr/bin/env bash
# Menjalankan semua pemeriksaan yang bisa dijalankan tanpa Electron dan Android SDK.
set -euo pipefail
cd "$(dirname "$0")/.."
echo "== Rust: tes"
cargo test --manifest-path engine/Cargo.toml -q
echo "== Rust: build rilis"
cargo build --release --manifest-path engine/Cargo.toml -q
echo "== Vektor emas"
node tools/gen_vectors.js >/dev/null
git diff --quiet shared/vectors/golden.json 2>/dev/null || echo "(vektor berubah: periksa dan commit)"
echo "== Ikon Android sinkron"
python3 tools/gen_icons.py --check
echo "== Desktop: sintaks dan tes"
for f in desktop/src/*.js desktop/ui/*.js desktop/ui/pages/*.js; do node --check "$f"; done
(cd desktop && node --test test/*.test.js)
echo "Semua pemeriksaan lolos."
