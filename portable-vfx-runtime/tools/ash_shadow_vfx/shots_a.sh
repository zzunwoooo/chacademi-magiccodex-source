#!/bin/bash
# shots_a.sh <PackName without AS_> <times> <scenario>
N=AS_$1; TK=~/vfxA/vfx_toolkit; rm -rf cap_a/$N/$3; mkdir -p cap_a/$N/$3
node $TK/engine/capture.js fin_a/$N/preview.html cap_a/$N/$3 shots $2 $3 2>&1 | grep -iv "^ok$" | tail -3
F=""; for t in ${2//,/ }; do F="$F $(ls cap_a/$N/$3/shot_${3}*_$t.png)"; done
python3 $TK/engine/contact_sheet.py cap_a/$N/$3.png $F >/dev/null; echo cap_a/$N/$3.png
