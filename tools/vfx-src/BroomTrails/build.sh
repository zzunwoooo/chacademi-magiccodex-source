#!/bin/bash
# usage: build.sh <Name> <texdir> <accent>   → final/<Name>/ + final/<Name>.zip (client-compatible, preview rebuilt from the fixed vfx.json)
set -e
cd "$(dirname "$0")"; TK=../vfx_toolkit; N=$1
rm -rf out/$N out/$N.zip fixed/$N final/$N final/$N.zip; mkdir -p final
python3 $TK/tools/make_bundle.py $N.js $2 $PWD/out --accent "$3" > /dev/null 2>&1 || true
test -f out/$N/vfx.json
python3 $TK/tools/client_compat.py fix out/$N fixed/$N > /dev/null
python3 $TK/engine/build_preview.py fixed/$N fixed/$N/preview.html --accent "$3"
python3 $TK/tools/client_compat.py check fixed/$N
cp -r fixed/$N final/$N
(cd final && zip -qr $N.zip $N)
python3 - "$PWD/final/$N" <<'EOF'
import sys, os, struct
d = sys.argv[1] + "/textures"; tot = 0
for f in os.listdir(d):
    w, h = struct.unpack('>II', open(os.path.join(d, f), 'rb').read(24)[16:24]); tot += w * h * 4
print(f"RGBA {tot/1048576:.2f} MiB, zip {os.path.getsize(sys.argv[1] + '.zip')//1024} KB")
EOF
