#!/bin/bash
# shots_t.sh TW_Name <times> <scenario>  → cap_t/<Name>/<scenario>.png
N=$1; TK=~/vfxA/vfx_toolkit; cd "$(dirname "$0")"; rm -rf cap_t/$N/$3; mkdir -p cap_t/$N/$3
node $TK/engine/capture.js fin_t/$N/preview.html cap_t/$N/$3 shots $2 $3 2>&1 | grep -iv "^ok$" | tail -3
F=""; for t in ${2//,/ }; do F="$F $(ls cap_t/$N/$3/shot_${3}*_$t.png)"; done
python3 $TK/engine/contact_sheet.py cap_t/$N/$3.png $F >/dev/null; echo cap_t/$N/$3.png
