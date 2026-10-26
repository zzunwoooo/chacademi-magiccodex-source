"""운명의 타로 미리보기: 실제 납품 PNG + TarotLayout.java 값으로 세 상태(선택·공개·결과)를 그린다.
usage: python3 -I preview.py <repo-root> <out.png>"""
import sys, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

R = sys.argv[1]; OUT = sys.argv[2]
T = R + '/magic-codex-fabric/src/main/resources/assets/magiccodex/textures/gui/tarot/'
F = R + '/magic-codex-fabric/src/main/resources/assets/magiccodex/font/'

# ---- TarotLayout.java 값 (같게 유지)
PANEL_W, PANEL_H = 1585, 992; CX = PANEL_W / 2
BACK_BOX = (107, 101, 918, 1398)
FRAME_BOX = [(96, 91, 928, 1409), (63, 40, 961, 1464), (59, 17, 965, 1474)]
ART_RECT = [(133, 128, 892, 1376), (115, 95, 910, 1417), (103, 95, 921, 1407)]
ORNAMENT_W, ORNAMENT_LINE_Y = 680, 104; ORNAMENT_BOX = (66, 223, 2106, 497); ORNAMENT_LINE_SRC = 360
TITLE_Y, TITLE_SIZE, GUIDE_Y, GUIDE_SIZE = 170, 50, 860, 34
SELECT_H, SELECT_SPACING, SELECT_Y, SIDE_DROP, SIDE_TILT, LIFT = 500, 286, 532, 18, 8, 24
RESULT_H, RESULT_Y = 380, 456
STAR_Y, STAR_SIZE, STAR_GAP = 230, 48, 64
STAR_BOX = [(67, 65, 1221, 1146), (70, 59, 1234, 1142), (120, 117, 1177, 1091)]
HX0, HY0, HX1, HY1, HST, HSB = 26, 234, 2145, 488, 335, 387
HINT_W, HINT_TOP, HINT_TEXT_W, HINT_PAD = 1150, 666, 1010, 18
BX0, BY0, BX1, BY1 = 125, 192, 2047, 532
BUTTON_W, BUTTON_Y, BUTTON_TEXT = 330, 870, 31
HINT_SIZE, HINT_SIZE_SMALL, LINE_GAP = 30, 26, 1.32
FLIP_START, FLIP_MS = 300, 560
FRAMES = ['card_frame_1_silverblue', 'card_frame_2_violetblue', 'card_frame_3_gold']
STARS = ['star-silver-blue', 'star-violet-blue', 'star-gold']
ILL = ["01-moonlit-lake", "02-mystical-eye", "03-sun-mountains", "04-shooting-star", "05-magic-book", "06-golden-key", "07-hourglass",
       "08-lantern", "09-feather", "10-purple-crystals", "11-red-rose", "12-ancient-tree", "13-black-cat-crescent", "14-owl",
       "15-two-silver-fish", "16-pearl-shell", "17-silver-chalice", "18-floating-castle-island", "19-moonlit-doorway", "20-hand-golden-flame"]

cache = {}
def img(p):
    if p not in cache: cache[p] = Image.open(T + p).convert('RGBA')
    return cache[p]

SW, SH = 1600, 900
S = min(SW * .70 / PANEL_W, SH * .94 / PANEL_H)
LEFT, TOP = (SW - PANEL_W * S) / 2, (SH - PANEL_H * S) / 2
def P(x, y): return LEFT + x * S, TOP + y * S

def background():
    bg = Image.new('RGBA', (SW, SH))
    d = ImageDraw.Draw(bg)
    for y in range(SH):
        t = y / SH
        d.line([(0, y), (SW, y)], fill=(int(20 + 30 * t), int(32 + 50 * t), int(70 + 40 * t), 255))
    # 게임 장면 대신: 성 실루엣 몇 개 (패널 알파가 비치는지 보기용)
    for i in range(14):
        x = i * 125 + 20; h = 180 + (i * 53) % 220
        d.rectangle([x, SH - h, x + 70, SH], fill=(14, 22, 44, 255))
        d.polygon([(x - 6, SH - h), (x + 35, SH - h - 70), (x + 76, SH - h)], fill=(14, 22, 44, 255))
        for k in range(3): d.rectangle([x + 18 + k * 14, SH - h + 40, x + 26 + k * 14, SH - h + 56], fill=(240, 196, 110, 255))
    d.ellipse([1300, 60, 1400, 160], fill=(236, 232, 210, 255))
    return bg

def paste(canvas, im, x, y, w, h, alpha=1.0):
    w, h = max(1, round(w)), max(1, round(h))
    r = im.resize((w, h), Image.LANCZOS)
    if alpha < 1:
        a = r.getchannel('A').point(lambda v: int(v * alpha)); r.putalpha(a)
    canvas.alpha_composite(r, (round(x), round(y)))

def font(name, size): return ImageFont.truetype(F + name, max(6, round(size * S)))
def text(canvas, s, x, y, size, rgb, face='maruburi_semibold.ttf', alpha=1.0):
    layer = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(P(x, y), s, font=font(face, size), fill=rgb + (int(255 * alpha),), anchor='mm')
    canvas.alpha_composite(layer)

def width(s, size): return font('pretendard_medium.ttf', size).getlength(s) / S
def wrap(t, mx, size):
    out, line = [], ''
    for w in t.split():
        j = w if not line else line + ' ' + w
        if width(j, size) <= mx: line = j; continue
        if line: out.append(line); line = ''
        while width(w, size) > mx:
            n = 1
            while n < len(w) and width(w[:n + 1], size) <= mx: n += 1
            out.append(w[:n]); w = w[n:]
        line = w
    if line: out.append(line)
    return out
def fit_hint(t):
    l = wrap(t, HINT_TEXT_W, HINT_SIZE)
    if len(l) <= 3: return l, HINT_SIZE
    l = wrap(t, HINT_TEXT_W, HINT_SIZE_SMALL)
    if len(l) <= 4: return l, HINT_SIZE_SMALL
    l = l[:4]; last = l[3]
    while last and width(last + '…', HINT_SIZE_SMALL) > HINT_TEXT_W: last = last[:-1]
    l[3] = last.rstrip() + '…'; return l, HINT_SIZE_SMALL
def natural(): return HINT_W * (HY1 - HY0) / (HX1 - HX0)
def panel_h(lines, size): return max(natural(), len(lines) * size * LINE_GAP + 2 * HINT_PAD)
def button_y(ph): return min(max(BUTTON_Y, HINT_TOP + ph + 44), 884)

def card_image(h_panel, front=None, glow=0.0):
    """카드 한 장을 (보이는 높이 h_panel 패널 px) 화면 픽셀 크기의 RGBA 로. front = (grade, illustration)"""
    hp = h_panel * S
    if front is None:
        box, base = BACK_BOX, img('cards/card_back.png')
    else:
        g, il = front; box = FRAME_BOX[g]; base = img('cards/' + FRAMES[g] + '.png')
    s = hp / (box[3] - box[1])
    pad = 40
    wv = (box[2] - box[0]) * s
    layer = Image.new('RGBA', (round(wv + 2 * pad), round(hp + 2 * pad)), (0, 0, 0, 0))
    ox, oy = pad - box[0] * s, pad - box[1] * s
    if front is not None:
        r = ART_RECT[g]; art = img('illustrations/' + ILL[il] + '.png')
        rw, rh = r[2] - r[0], r[3] - r[1]; want = rw / rh; have = 1024 / 1536
        if have > want: cw = 1536 * want; crop = ((1024 - cw) / 2, 0, (1024 + cw) / 2, 1536)
        else: ch = 1024 / want; crop = (0, (1536 - ch) / 2, 1024, (1536 + ch) / 2)
        a = art.crop(tuple(round(v) for v in crop))
        paste(layer, a, ox + r[0] * s, oy + r[1] * s, rw * s, rh * s)
    if glow > 0:
        rad = 28 * s
        g1 = Image.new('RGBA', layer.size, (0, 0, 0, 0))
        ImageDraw.Draw(g1).rounded_rectangle([pad, pad, pad + wv, pad + hp], radius=rad, outline=(243, 196, 90, int(170 * glow)), width=round(16 * S))
        layer.alpha_composite(g1.filter(ImageFilter.GaussianBlur(9 * S)))
    paste(layer, base, ox, oy, 1024 * s, 1536 * s)
    if glow > 0:
        ImageDraw.Draw(layer).rounded_rectangle([pad + 1, pad + 1, pad + wv - 1, pad + hp - 1], radius=28 * s, outline=(248, 218, 134, int(255 * glow)), width=max(1, round(3.5 * S)))
    return layer

def place_card(canvas, layer, cx, cy, tilt=0, wscale=1.0, alpha=1.0):
    if wscale < 1:
        layer = layer.resize((max(1, round(layer.width * wscale)), layer.height), Image.LANCZOS)
    if tilt: layer = layer.rotate(-tilt, resample=Image.BICUBIC, expand=True)
    if alpha < 1:
        a = layer.getchannel('A').point(lambda v: int(v * alpha)); layer.putalpha(a)
    x, y = P(cx, cy)
    canvas.alpha_composite(layer, (round(x - layer.width / 2), round(y - layer.height / 2)))

def chrome(canvas):
    x, y = P(0, 0); paste(canvas, img('ui-parts/main-panel-v2.png'), x, y, PANEL_W * S, PANEL_H * S)
    k = ORNAMENT_W / (ORNAMENT_BOX[2] - ORNAMENT_BOX[0]); o = img('ui-parts/title-ornament.png')
    x, y = P(CX - (ORNAMENT_BOX[0] + ORNAMENT_BOX[2]) / 2 * k, ORNAMENT_LINE_Y - ORNAMENT_LINE_SRC * k)
    paste(canvas, o, x, y, o.width * k * S, o.height * k * S)
    text(canvas, '운명의 타로', CX, TITLE_Y, TITLE_SIZE, (0xF3, 0xDD, 0xA0))

def sel_x(i): return CX + (i - 1) * SELECT_SPACING
def sel_y(i): return SELECT_Y + (0 if i == 1 else SIDE_DROP)
def smooth(t): t = max(0, min(1, t)); return t * t * (3 - 2 * t)

def state_select():
    c = background(); chrome(c)
    for i in (0, 2, 1):
        hot = i == 1
        place_card(c, card_image(SELECT_H, glow=1.0 if hot else 0), sel_x(i), sel_y(i) - (LIFT if hot else 0), tilt=(i - 1) * SIDE_TILT)
    text(c, '카드 한 장을 고르세요.', CX, GUIDE_Y, GUIDE_SIZE, (0xE8, 0xE2, 0xD0))
    return c

def state_reveal(t=640, chosen=0, grade=2, il=13):
    c = background(); chrome(c)
    fade = 1 - min(1, t / 320)
    for i in range(3):
        if i != chosen and fade > 0: place_card(c, card_image(SELECT_H), sel_x(i), sel_y(i), tilt=(i - 1) * SIDE_TILT, alpha=fade)
    p = smooth(t / 860)
    x = sel_x(chosen) + (CX - sel_x(chosen)) * p; y = sel_y(chosen) - LIFT + (RESULT_Y - sel_y(chosen) + LIFT) * p
    h = SELECT_H + (RESULT_H - SELECT_H) * p; tilt = (chosen - 1) * SIDE_TILT * (1 - p)
    deg = 180 * smooth((t - FLIP_START) / FLIP_MS); w = abs(math.cos(math.radians(deg)))
    if deg > 90: place_card(c, card_image(h, front=(grade, il)), x, y, wscale=w)
    else: place_card(c, card_image(h), x, y, tilt=tilt, wscale=w)
    return c

def state_result(grade=2, il=0, hint='카드가 또렷하게 속삭여요… 「보름달이 뜬 밤 호숫가에서 물에 비친 달을 바라본다」 그러면 새 마법을 깨칠 수 있을 거예요.'):
    c = background(); chrome(c)
    place_card(c, card_image(RESULT_H, front=(grade, il)), CX, RESULT_Y)
    n = grade + 1; b = STAR_BOX[grade]; st = img('ui-parts/' + STARS[grade] + '.png')
    for i in range(n):
        k = STAR_SIZE / (b[3] - b[1]); sx = CX + (i - (n - 1) / 2) * STAR_GAP
        x, y = P(sx - (b[0] + b[2]) / 2 * k, STAR_Y - (b[1] + b[3]) / 2 * k)
        paste(c, st, x, y, st.width * k * S, st.height * k * S)
    lines, size = fit_hint(hint); ph = panel_h(lines, size)
    hp = img('ui-parts/hint-panel-v3.png'); k = HINT_W / (HX1 - HX0)
    total = round(ph / k); top_h, bot_h = HST - HY0, HY1 - HSB; mid = max(1, total - top_h - bot_h)
    strip = Image.new('RGBA', (HX1 - HX0, top_h + mid + bot_h), (0, 0, 0, 0))
    strip.alpha_composite(hp.crop((HX0, HY0, HX1, HST)), (0, 0))
    strip.alpha_composite(hp.crop((HX0, HST, HX1, HSB)).resize((HX1 - HX0, mid)), (0, top_h))
    strip.alpha_composite(hp.crop((HX0, HSB, HX1, HY1)), (0, top_h + mid))
    x, y = P(CX - HINT_W / 2, HINT_TOP); paste(c, strip, x, y, HINT_W * S, strip.height * k * S)
    lh = size * LINE_GAP; cy = HINT_TOP + ph / 2 - (len(lines) - 1) * lh / 2
    for i, l in enumerate(lines): text(c, l, CX, cy + i * lh, size, (0xF4, 0xF0, 0xE4), face='pretendard_medium.ttf')
    by = button_y(ph); kb = BUTTON_W / (BX1 - BX0); bh = (BY1 - BY0) * kb
    btn = img('ui-parts/confirm-normal.png').crop((BX0, BY0, BX1, BY1))
    x, y = P(CX - BUTTON_W / 2, by - bh / 2); paste(c, btn, x, y, BUTTON_W * S, bh * S)
    text(c, '확인', CX, by, BUTTON_TEXT, (0xF6, 0xE7, 0xBF))
    return c

def label(im, s):
    d = ImageDraw.Draw(im); f = ImageFont.truetype(F + 'pretendard_bold.ttf', 34)
    d.rectangle([0, 0, 640, 56], fill=(8, 12, 26, 210)); d.text((18, 8), s, font=f, fill=(240, 220, 160, 255))
    return im

shots = [label(state_select(), '01 선택 (가운데 카드에 마우스)'),
         label(state_reveal(), '02 공개 (고른 뒤 0.64초, 2성)'),
         label(state_result(), '03 결과 (3성 예시, 1~3줄)'),
         label(state_result(grade=0, il=8, hint='아직 깨치지 못한 빛 마법 하나가 어딘가에서 당신을 기다리고 있는 것 같아요…'), '결과 1성 예시'),
         label(state_result(grade=1, il=14, hint='그러고 보니 예전에 어떤 마법사가 보름달이 뜬 밤 물가에서 무언가를 몇 번 하더니 빛이 났다더라.'), '결과 2성 예시'),
         label(state_result(grade=2, il=19, hint='카드가 또렷하게 속삭여요… 「' + '보름달이 뜬 밤 호숫가에서 물에 비친 달을 바라보며 열 번 숨을 고르고 달빛 아래에서 지팡이를 높이 든 채 기다린다' + '」 그러면 새 마법을 깨칠 수 있을 거예요.'), '결과 긴 힌트 (4줄·작은 글자)')]
sheet = Image.new('RGBA', (SW * 2, SH * 3), (0, 0, 0, 255))
for i, s in enumerate(shots): sheet.alpha_composite(s, ((i % 2) * SW, (i // 2) * SH))
sheet = sheet.resize((SW, SH * 3 // 2), Image.LANCZOS)
sheet.convert('RGB').save(OUT, optimize=True)
print('saved', OUT, sheet.size)
