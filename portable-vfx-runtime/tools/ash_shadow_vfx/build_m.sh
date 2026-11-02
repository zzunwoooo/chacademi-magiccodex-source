#!/bin/bash
# usage: build_m.sh AM_PackName  → fin_m/<Name>/ (+ .zip) + checks (logs_m_<Name>.txt)
set -e
P=$1; TK=~/vfxM/vfx_toolkit; mkdir -p specs_m out_m fin_m
cat lib.js lib_m.js packs/$P.js > specs_m/$P.spec.js
N=$P
python3 $TK/tools/make_bundle.py specs_m/$P.spec.js tex_m out_m --accent "#ff7a2a" > logs_m_$P.txt 2>&1 || true
rm -rf fin_m/$N; python3 $TK/tools/client_compat.py fix out_m/$N fin_m/$N >> logs_m_$P.txt 2>&1
python3 $TK/tools/client_compat.py check fin_m/$N >> logs_m_$P.txt 2>&1 && echo CHECK_OK >> logs_m_$P.txt || echo CHECK_FAIL >> logs_m_$P.txt
python3 $TK/tools/validate.py fin_m/$N >> logs_m_$P.txt 2>&1 && echo VALID_OK >> logs_m_$P.txt || echo VALID_FAIL >> logs_m_$P.txt
python3 $TK/engine/build_preview.py fin_m/$N fin_m/$N/preview.html --accent "#ff7a2a" >> logs_m_$P.txt 2>&1
(cd fin_m && rm -f $N.zip && zip -qr $N.zip $N)
echo $N
