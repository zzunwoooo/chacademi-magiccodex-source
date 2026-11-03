#!/bin/bash
# usage: build.sh <spec> <texdir> <Name> <workdir>
set -e
TK=/home/claude/vfxwork/agentD/vfx_toolkit
SPEC=$1; TEX=$2; NAME=$3; W=$4; NOTES=$5
rm -rf $W/out $W/fin; mkdir -p $W/out $W/fin
python $TK/tools/make_bundle.py $SPEC $TEX $W/out --accent '#b77cff' ${NOTES:+--notes $NOTES} 2>&1 | grep -v "^  " | tail -5 || true
python $TK/tools/client_compat.py fix $W/out/$NAME $W/fin/$NAME | tail -4
python $TK/tools/client_compat.py check $W/fin/$NAME
python $TK/tools/validate.py $W/fin/$NAME | tail -2
python $TK/engine/build_preview.py $W/fin/$NAME $W/fin/$NAME/preview.html --accent '#b77cff' | tail -1
(cd $W/fin && rm -f $NAME.zip && zip -qr $NAME.zip $NAME)
ls -la $W/fin
