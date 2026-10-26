"""행운의 뽑기판 미리보기: 실제 납품 PNG + DrawBoardLayout.java 값으로 (1) 진행 중 판 (2) 100개 미만 안내 (3) 칸 상태 3종 확대(같은 정사각형 확인)를 그린다.
usage: python3 -I preview.py <repo-root> <out.png>
아이템 자리는 실제 게임에서는 등록한 ItemStack 렌더러로 그린다 — 여기서는 색 보석 모양 대신 그림."""
import sys, math
from PIL import Image, ImageDraw, ImageFont

R = sys.argv[1]; OUT = sys.argv[2]
T = R + '/magic-codex-fabric/src/main/resources/assets/magiccodex/textures/gui/draw100/'
F = R + '/magic-codex-fabric/src/main/resources/assets/magiccodex/font/'

# ---- DrawBoardLayout.java 값 (같게 유지)
PANEL = TEX = 1254; CX = PANEL / 2
TILE, GAP = 80, 8; GRID = 10 * TILE + 9 * GAP
GRID_X, GRID_Y = CX - GRID / 2, 266
TITLE_Y, TITLE_SIZE, GUIDE_Y, GUIDE_SIZE = 172, 46, 224, 25
CLOSE_X, CLOSE_Y, CLOSE_SIZE = 1118, 172, 40
HIDDEN_FRAME, HOVER_FRAME = (154, 158, 947, 897), (140, 150, 975, 940)
REVEALED_FRAME, FX_FRAME = (82, 88, 1091, 1052), (206, 235, 838, 788)
ITEM_FRACTION = .6
TITLE_RGB, GUIDE_RGB = (0xF3, 0xDD, 0xA0), (0xE8, 0xE2, 0xD0)

S = 0.62  # 미리보기 배율 (게임에서는 화면에 맞춘 한 배율)
img = {n: Image.open(T + n + '.png').convert('RGBA') for n in ('panel', 'tile_hidden', 'tile_hover', 'tile_revealed', 'reveal_fx')}

def fit(frame, x, y, size):
    sx, sy = size / frame[2], size / frame[3]  # frame = (x, y, w, h)
    return x - frame[0] * sx, y - frame[1] * sy, sx, sy

def sprite(dst, name, frame, x, y, size, k, alpha=1.0, wscale=1.0):
    """캔버스 전체를 frame→(x,y,size) 변환으로. wscale = 뒤집기 가로 배율(칸 가운데 기준)."""
    tx, ty, sx, sy = fit(frame, x, y, size)
    cx = x + size / 2
    sx2 = sx * wscale; tx2 = cx + (tx - cx) * wscale
    w, h = max(1, round(TEX * sx2 * k)), max(1, round(TEX * sy * k))
    im = img[name].resize((w, h), Image.LANCZOS)
    if alpha < 1: im.putalpha(im.split()[3].point(lambda a: int(a * alpha)))
    dst.alpha_composite(im, (round(tx2 * k), round(ty * k)))

def font(face, size, k): return ImageFont.truetype(F + face, max(6, round(size * k)))

def gem(d, x, y, size, k, color):
    s = size * ITEM_FRACTION; cx, cy = (x + size / 2) * k, (y + size / 2) * k; r = s / 2 * k
    d.polygon([(cx, cy - r), (cx + r * .8, cy), (cx, cy + r), (cx - r * .8, cy)], fill=color + (255,), outline=(255, 255, 255, 200))

def board(revealed, hover=-1, flip=None, fx=None, message=None, k=S):
    W = round(PANEL * k)
    out = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    out.alpha_composite(img['panel'].resize((W, W), Image.LANCZOS))
    d = ImageDraw.Draw(out)
    d.text((CX * k, TITLE_Y * k), '행운의 뽑기판', font=font('maruburi_semibold.ttf', TITLE_SIZE, k), fill=TITLE_RGB + (255,), anchor='mm')
    d.text((CX * k, GUIDE_Y * k), '원하는 칸을 눌러 아이템을 확인하세요.', font=font('pretendard_medium.ttf', GUIDE_SIZE, k), fill=GUIDE_RGB + (255,), anchor='mm')
    h = CLOSE_SIZE / 2 * k
    for a, b in (((-1, -1), (1, 1)), ((-1, 1), (1, -1))):
        d.line([(CLOSE_X * k + a[0] * h, CLOSE_Y * k + a[1] * h), (CLOSE_X * k + b[0] * h, CLOSE_Y * k + b[1] * h)], fill=(0xE8, 0xCF, 0x96, 255), width=max(2, round(3.2 * k)))
    colors = [(220, 60, 70), (90, 170, 240), (240, 200, 90), (150, 110, 230), (90, 210, 150)]
    for cell in range(100):
        x, y = GRID_X + cell % 10 * (TILE + GAP), GRID_Y + cell // 10 * (TILE + GAP)
        a = .35 if message else 1
        if flip and flip[0] == cell:
            t = flip[1]; w = abs(math.cos(math.pi * t))
            if t < .5: sprite(out, 'tile_hidden', HIDDEN_FRAME, x, y, TILE, k, a, w)
            else: sprite(out, 'tile_revealed', REVEALED_FRAME, x, y, TILE, k, a, w)
            continue
        if cell in revealed:
            sprite(out, 'tile_revealed', REVEALED_FRAME, x, y, TILE, k, a)
            gem(ImageDraw.Draw(out), x, y, TILE, k, colors[cell % len(colors)])
        else:
            sprite(out, 'tile_hidden', HIDDEN_FRAME, x, y, TILE, k, a)
            if cell == hover: sprite(out, 'tile_hover', HOVER_FRAME, x, y, TILE, k, a)
        if fx and fx[0] == cell: sprite(out, 'reveal_fx', FX_FRAME, x, y, TILE, k, fx[1])
    if message:
        d = ImageDraw.Draw(out)
        bw, bh = 760, 150
        d.rounded_rectangle([(CX - bw / 2) * k, (700 - bh / 2) * k, (CX + bw / 2) * k, (700 + bh / 2) * k], radius=14 * k, fill=(8, 18, 44, 235), outline=(0xE8, 0xCF, 0x96, 255), width=max(1, round(2 * k)))
        for i, line in enumerate(message):
            d.text((CX * k, (676 + i * 48) * k), line, font=font('pretendard_medium.ttf', 28 if i == 0 else 22, k), fill=(TITLE_RGB if i == 0 else GUIDE_RGB) + (255,), anchor='mm')
    return out

def states(k=1.6):
    """칸 하나의 숨김·호버·공개·반짝임: 시안 사각형 = 같은 목적지 정사각형."""
    size, pad = 160, 70
    W = round((4 * size + 5 * pad) / 1)
    out = Image.new('RGBA', (W, size + 2 * pad + 40), (14, 20, 38, 255))
    names = [('tile_hidden', HIDDEN_FRAME, '숨김'), ('tile_hover', HOVER_FRAME, '호버(겹침)'), ('tile_revealed', REVEALED_FRAME, '공개'), ('reveal_fx', FX_FRAME, '반짝임')]
    d = ImageDraw.Draw(out)
    for i, (n, fr, label) in enumerate(names):
        x, y = pad + i * (size + pad), pad
        if n == 'tile_hover': sprite(out, 'tile_hidden', HIDDEN_FRAME, x, y, size, 1)
        sprite(out, n, fr, x, y, size, 1)
        d = ImageDraw.Draw(out)
        d.rectangle([x, y, x + size - 1, y + size - 1], outline=(0, 255, 255, 255), width=1)
        d.text((x + size / 2, y + size + 34), label, font=font('pretendard_medium.ttf', 24, 1), fill=(240, 230, 200, 255), anchor='mm')
    return out

a = board({2, 17, 31, 45, 68, 83, 99, 54}, hover=40, flip=(22, .3), fx=(54, .8))
b = board(set(), message=['등록된 아이템 37/100개', '/뽑기판 관리 에서 100개를 채우면 판이 시작돼요'])
c = states()
W = a.width + b.width + 30
out = Image.new('RGBA', (W, max(a.height, b.height) + c.height + 30), (20, 26, 44, 255))
out.alpha_composite(a, (0, 0)); out.alpha_composite(b, (a.width + 30, 0)); out.alpha_composite(c, ((W - c.width) // 2, a.height + 30))
out.convert('RGB').save(OUT, optimize=True)
print('saved', OUT, out.size)
