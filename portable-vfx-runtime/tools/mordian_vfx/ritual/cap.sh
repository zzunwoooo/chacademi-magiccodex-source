#!/bin/bash
# usage: cap.sh <Name> <scenario> <times> <sheetname> [view]
TK=/home/claude/vfxwork/agentF/vfx_toolkit; B=/home/claude/vfxwork/agentF/build
D=$B/shots_$1_$2; rm -rf $D; mkdir -p $D
node $TK/engine/capture.js $B/fin_$1/$1/preview.html $D shots $3 $2 $5
python3 $TK/engine/contact_sheet.py $B/$4.png $(ls $D/*.png | sort -t_ -k4 -g)
