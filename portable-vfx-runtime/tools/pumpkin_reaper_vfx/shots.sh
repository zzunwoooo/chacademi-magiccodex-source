#!/bin/bash
# shots.sh <Name> <times> <scenario> [view]
N=$1; TK=~/vfx/vfx_toolkit; rm -rf cap/$N/$3; mkdir -p cap/$N/$3
node $TK/engine/capture.js fin/$N/preview.html cap/$N/$3 shots $2 $3 $4 2>&1 | grep -iv "^ok$" | tail -3
F=""; for t in ${2//,/ }; do F="$F $(ls cap/$N/$3/shot_${3}*_$t.png)"; done
python3 $TK/engine/contact_sheet.py cap/$N/$3.png $F >/dev/null
