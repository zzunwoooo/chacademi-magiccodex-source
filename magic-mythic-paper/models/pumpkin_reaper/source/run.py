import sys, os
sys.path.insert(0,'.')
import models as M
from gen import export, anim
from anims import anims
O='/mnt/user-data/outputs/'
if os.path.exists(O+'hitboxes.json'): os.remove(O+'hitboxes.json')
export('pumpkin_reaper', M.build(), anims, 7, O+'pumpkin_reaper.bbmodel', M.HITBOX)
M.apply_palette(M.SHINY)
export('pumpkin_reaper_shiny', M.build(), anims, 7, O+'pumpkin_reaper_shiny.bbmodel', M.HITBOX)
