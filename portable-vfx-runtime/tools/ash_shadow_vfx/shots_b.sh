#!/bin/bash
# shots_b.sh <Name> <times> <scenario>
N=$1; TK=~/vfxB/vfx_toolkit; cd "$(dirname "$0")"; rm -rf cap_b/$N/$3; mkdir -p cap_b/$N/$3
node $TK/engine/capture.js fin_b/$N/preview.html cap_b/$N/$3 shots $2 $3 $4 2>&1 | grep -iv "^ok$" | tail -3
F=""; for t in ${2//,/ }; do F="$F $(ls cap_b/$N/$3/shot_${3}*_$t.png)"; done
python3 $TK/engine/contact_sheet.py cap_b/$N/$3.png $F >/dev/null; echo cap_b/$N/$3.png
