#!/bin/bash
TK=/home/claude/vfxwork/agentD/vfx_toolkit
H=$1; O=$2; S=$3; shift 3
rm -rf $O; mkdir -p $O; L=""
for spec in "$@"; do IFS=: read sc ts v1 v2 <<< "$spec"; node $TK/engine/capture.js $H $O shots $ts $sc ${v1:+$v1:$v2} 2>&1 | grep -i err
  for t in ${ts//,/ }; do L="$L $O/shot_${sc}${v1:+_v$v1-$v2}_$t.png"; done; done
python $TK/engine/contact_sheet.py $S $L
