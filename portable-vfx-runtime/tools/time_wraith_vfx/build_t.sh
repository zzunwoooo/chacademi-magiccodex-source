#!/bin/bash
# usage: build_t.sh TW_Name → fin_t/<Name>/ (+ .zip) + 검사 로그 logs_t/
set -e
P=$1; TK=~/vfxA/vfx_toolkit; cd "$(dirname "$0")"; mkdir -p specs_t out_t fin_t logs_t
cat lib.js timelib.js packs/$P.js > specs_t/$P.spec.js
python3 $TK/tools/make_bundle.py specs_t/$P.spec.js tex_t out_t --accent "#7ff3ff" --notes packs/_NOTES.md > logs_t/$P.txt 2>&1 || true
rm -rf fin_t/$P; python3 $TK/tools/client_compat.py fix out_t/$P fin_t/$P >> logs_t/$P.txt 2>&1
python3 $TK/tools/client_compat.py check fin_t/$P >> logs_t/$P.txt 2>&1 && echo CHECK_OK >> logs_t/$P.txt || echo CHECK_FAIL >> logs_t/$P.txt
python3 $TK/tools/validate.py fin_t/$P >> logs_t/$P.txt 2>&1 && echo VALID_OK >> logs_t/$P.txt || echo VALID_FAIL >> logs_t/$P.txt
python3 $TK/engine/build_preview.py fin_t/$P fin_t/$P/preview.html --accent "#7ff3ff" >> logs_t/$P.txt 2>&1
(cd fin_t && rm -f $P.zip && zip -qr $P.zip $P)
grep -E "CHECK_|VALID_|Error|error|WARN|no visual" logs_t/$P.txt | head -12
