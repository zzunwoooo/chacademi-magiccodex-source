#!/bin/bash
# cap.sh <html> <sheetname> "<times>:<scenario>[:view]" ...
H=$1; N=$2; shift 2; S=/tmp/claude-0/-home-claude/ad8c3688-651d-53aa-91b4-d92e4daf81e1/scratchpad
rm -rf $S/$N; mkdir -p $S/$N; i=0
for a in "$@"; do IFS='|' read -r T SC V <<< "$a"; mkdir -p $S/$N/$i; node /home/claude/vfxwork/agentA/vfx_toolkit/engine/capture.js $H $S/$N/$i shots $T $SC $V >/dev/null; i=$((i+1)); done
python /home/claude/vfxwork/agentA/vfx_toolkit/engine/contact_sheet.py $S/$N.png $(for d in $(ls $S/$N | sort -n); do ls $S/$N/$d/*.png | sort -t_ -k3 -g; done)
