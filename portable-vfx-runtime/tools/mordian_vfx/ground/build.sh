#!/bin/bash
# usage: build.sh <spec.js> <Name> <workname>  (texdir = mordian/tex_<workname>)
set -e
TK=/home/claude/vfxwork/agentB/vfx_toolkit; M=/home/claude/vfxwork/agentB/mordian
SPEC=$1; NAME=$2; W=$3
TEX=$M/tex_$W; rm -rf $TEX; cp -r $M/tex $TEX
OUT=$M/$W/out; FIN=$M/$W/fin; rm -rf $OUT $FIN; mkdir -p $OUT $FIN
NOTES=""; [ -f $M/$W/NOTES.md ] && NOTES="--notes $M/$W/NOTES.md"
python3 $TK/tools/make_bundle.py $SPEC $TEX $OUT --accent '#a35cff' $NOTES 2>&1 | grep -v Traceback | tail -4 || true
python3 $TK/tools/client_compat.py fix $OUT/$NAME $FIN/$NAME | tail -5
python3 $TK/tools/client_compat.py check $FIN/$NAME
python3 $TK/tools/validate.py $FIN/$NAME | tail -2
python3 $TK/engine/build_preview.py $FIN/$NAME $FIN/$NAME/preview.html --accent '#a35cff' | tail -1
cd $FIN && rm -f $NAME.zip && zip -qr $NAME.zip $NAME && ls -la $FIN/$NAME.zip
