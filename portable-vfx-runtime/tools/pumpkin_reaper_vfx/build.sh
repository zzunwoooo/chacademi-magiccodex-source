#!/bin/bash
# usage: build.sh PackName  → out/<SceneName>/ ... + checks
set -e
P=$1; TK=~/vfx/vfx_toolkit; mkdir -p specs out fin
cat lib.js packs/$P.js > specs/$P.spec.js
N=$(grep -o 'name: "PR_[A-Za-z]*"' packs/$P.js | head -1 | sed 's/name: "//;s/"//')
NOTES="--notes packs/_NOTES.md"
python3 $TK/tools/make_bundle.py specs/$P.spec.js tex out --accent "#ff8a2a" $NOTES > logs_$P.txt 2>&1 || true
rm -rf fin/$N; python3 $TK/tools/client_compat.py fix out/$N fin/$N >> logs_$P.txt 2>&1
python3 $TK/tools/client_compat.py check fin/$N >> logs_$P.txt 2>&1 && echo CHECK_OK >> logs_$P.txt || echo CHECK_FAIL >> logs_$P.txt
python3 $TK/tools/validate.py fin/$N >> logs_$P.txt 2>&1 && echo VALID_OK >> logs_$P.txt || echo VALID_FAIL >> logs_$P.txt
python3 $TK/engine/build_preview.py fin/$N fin/$N/preview.html --accent "#ff8a2a" >> logs_$P.txt 2>&1
(cd fin && rm -f $N.zip && zip -qr $N.zip $N)
echo $N
