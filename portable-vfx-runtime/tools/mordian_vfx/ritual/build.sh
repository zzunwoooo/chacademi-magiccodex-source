#!/bin/bash
# usage: build.sh <pack js> <texdir> <Name> <accent>
set -e
B=/home/claude/vfxwork/agentF/build; TK=/home/claude/vfxwork/agentF/vfx_toolkit
JS=$1; TEX=$2; NAME=$3; ACC=$4
cat $B/common.js $B/$JS > $B/spec_$NAME.js
OUT=$B/out_$NAME; FIN=$B/fin_$NAME; rm -rf $OUT $FIN; mkdir -p $OUT $FIN
NOTES=""; [ -f $B/notes_$NAME.md ] && NOTES="--notes $B/notes_$NAME.md"
python3 $TK/tools/make_bundle.py $B/spec_$NAME.js $B/$TEX $OUT --accent $ACC $NOTES 2>&1 | grep -v "^  " | tail -5 || true
python3 $TK/tools/client_compat.py fix $OUT/$NAME $FIN/$NAME | tail -4
python3 $TK/tools/client_compat.py check $FIN/$NAME
python3 $TK/tools/validate.py $FIN/$NAME | tail -2
python3 $TK/engine/build_preview.py $FIN/$NAME $FIN/$NAME/preview.html --accent $ACC | tail -1
(cd $FIN && rm -f $NAME.zip && zip -qr $NAME.zip $NAME) && ls -la $FIN/$NAME.zip
