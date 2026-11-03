#!/bin/bash
# usage: cap.sh <work> <Name> <times> <scen1> [scen2 ...]  -> sheet at <work>/sheet.png
TK=/home/claude/vfxwork/agentB/vfx_toolkit; M=/home/claude/vfxwork/agentB/mordian
W=$1; NAME=$2; T=$3; shift 3
D=$M/$W/shots; rm -rf $D; mkdir -p $D
for s in "$@"; do node $TK/engine/capture.js $M/$W/fin/$NAME/preview.html $D shots $T $s; done
python3 $TK/engine/contact_sheet.py $M/$W/sheet.png $(ls $D/*.png | sort -t_ -k2,2 -s) >/dev/null && echo sheet
