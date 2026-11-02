#!/bin/bash
# usage: build_a.sh <PackName without AS_>  → fin_a/AS_<Name>/ (+ .zip) + checks in logs_a/
set -e
P=$1; TK=~/vfxA/vfx_toolkit; mkdir -p specs_a out_a fin_a logs_a
cat lib_a.js packs/AS_$P.js > specs_a/AS_$P.spec.js
N=AS_$P; L=logs_a/$P.txt
python3 $TK/tools/make_bundle.py specs_a/AS_$P.spec.js tex_a out_a --accent "#ff7a2a" --notes packs/_NOTES_A.md > $L 2>&1 || true
rm -rf fin_a/$N; python3 $TK/tools/client_compat.py fix out_a/$N fin_a/$N >> $L 2>&1
python3 $TK/tools/client_compat.py check fin_a/$N >> $L 2>&1 && echo CHECK_OK >> $L || echo CHECK_FAIL >> $L
python3 $TK/tools/validate.py fin_a/$N >> $L 2>&1 && echo VALID_OK >> $L || echo VALID_FAIL >> $L
python3 $TK/engine/build_preview.py fin_a/$N fin_a/$N/preview.html --accent "#ff7a2a" >> $L 2>&1
(cd fin_a && rm -f $N.zip && zip -qr $N.zip $N)
grep -E "CHECK_|VALID_|rror|WARN|no visual" $L | head -8
