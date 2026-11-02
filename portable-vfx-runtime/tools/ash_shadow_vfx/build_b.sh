#!/bin/bash
# usage: build_b.sh AS_Name  (그룹 B: tex_b, toolkit ~/vfxB) → fin_b/<Name>/ (+ .zip) + 검사 로그
set -e
P=$1; TK=~/vfxB/vfx_toolkit; cd "$(dirname "$0")"; mkdir -p specs_b out_b fin_b logs_b
cat lib.js ashlib_b.js packs/$P.js > specs_b/$P.spec.js
NOTES="--notes packs/_NOTES_B.md"
python3 $TK/tools/make_bundle.py specs_b/$P.spec.js tex_b out_b --accent "#ff7a2a" $NOTES > logs_b/$P.txt 2>&1 || true
rm -rf fin_b/$P; python3 $TK/tools/client_compat.py fix out_b/$P fin_b/$P >> logs_b/$P.txt 2>&1
python3 $TK/tools/client_compat.py check fin_b/$P >> logs_b/$P.txt 2>&1 && echo CHECK_OK >> logs_b/$P.txt || echo CHECK_FAIL >> logs_b/$P.txt
python3 $TK/tools/validate.py fin_b/$P >> logs_b/$P.txt 2>&1 && echo VALID_OK >> logs_b/$P.txt || echo VALID_FAIL >> logs_b/$P.txt
python3 $TK/engine/build_preview.py fin_b/$P fin_b/$P/preview.html --accent "#ff7a2a" >> logs_b/$P.txt 2>&1
(cd fin_b && rm -f $P.zip && zip -qr $P.zip $P)
grep -E "CHECK_|VALID_|Error|error|FIX|WARN" logs_b/$P.txt | head -12
