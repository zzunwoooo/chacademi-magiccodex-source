#!/bin/bash
# shots_m.sh <Name> <times> <scenario>
N=$1; TK=~/vfxM/vfx_toolkit; rm -rf cap_m/$N/$3; mkdir -p cap_m/$N/$3
node $TK/engine/capture.js fin_m/$N/preview.html cap_m/$N/$3 shots $2 $3 2>&1 | grep -iv "^ok$" | tail -3
F=""; for t in ${2//,/ }; do F="$F $(ls cap_m/$N/$3/shot_${3}*_$t.png)"; done
python3 $TK/engine/contact_sheet.py cap_m/$N/$3.png $F >/dev/null
