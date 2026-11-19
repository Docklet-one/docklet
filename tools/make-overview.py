from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "docs" / "screenshots"
BG = (246, 241, 232)
INK = (36, 31, 25)
MUTED = (125, 115, 103)
LINE = (233, 224, 211)

APPS = [
    ("示例模块", "专注", "shell-module-saved.png", (217, 121, 43)),
    ("清单", "效率", "notes-final.png", (94, 124, 106)),
    ("专注时钟", "专注", "focus-running.png", (91, 107, 158)),
    ("画板", "创作", "canvas-drawn.png", (63, 125, 140)),
    ("小算盘", "工具", "calc-key.png", (79, 100, 114)),
    ("一日一问", "学习", "quiz-answer.png", (123, 93, 142)),
    ("网络实验", "网络", "netlab-sse.png", (47, 111, 106)),
]

FONT_DIR = Path("C:/Windows/Fonts")
FONT_REG = FONT_DIR / "msyh.ttc"
FONT_BOLD = FONT_DIR / "msyhbd.ttc"

MARGIN = 36
GAP = 18
COLS = 4
TILE_W, TILE_H = 258, 573
LABEL_H = 62
TITLE_H = 124
W = MARGIN * 2 + COLS * TILE_W + (COLS - 1) * GAP

rows = (len(APPS) + COLS - 1) // COLS
H = TITLE_H + rows * (TILE_H + LABEL_H) + (rows - 1) * GAP + MARGIN

canvas = Image.new("RGB", (W, H), BG)
draw = ImageDraw.Draw(canvas)

font_title = ImageFont.truetype(str(FONT_BOLD), 34)
font_sub = ImageFont.truetype(str(FONT_REG), 16)
font_label = ImageFont.truetype(str(FONT_BOLD), 18)
font_kind = ImageFont.truetype(str(FONT_REG), 12)

draw.text((MARGIN, 26), "Docklet · 7 个示例应用", font=font_title, fill=INK)
draw.text(
    (MARGIN, 72),
    "CrashGuard · remote · background · network · files · clipboard",
    font=font_sub,
    fill=MUTED,
)
for i, app in enumerate(APPS):
    color = app[3]
    x = MARGIN + i * 16
    draw.rounded_rectangle((x, 101, x + 9, 105), radius=4, fill=color)

for index, (name, kind, filename, accent) in enumerate(APPS):
    row = index // COLS
    count_in_row = min(COLS, len(APPS) - row * COLS)
    row_width = count_in_row * TILE_W + (count_in_row - 1) * GAP
    start_x = (W - row_width) // 2
    col = index % COLS
    x = start_x + col * (TILE_W + GAP)
    y = TITLE_H + row * (TILE_H + LABEL_H + GAP)
    source = Image.open(SHOTS / filename).convert("RGB")
    tile = ImageOps.fit(source, (TILE_W, TILE_H), method=Image.Resampling.LANCZOS)
    mask = Image.new("L", (TILE_W, TILE_H), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, TILE_W, TILE_H), radius=18, fill=255)
    canvas.paste(tile, (x, y), mask)
    draw.rounded_rectangle(
        (x, y, x + TILE_W - 1, y + TILE_H - 1),
        radius=18,
        outline=LINE,
        width=2,
    )
    label_y = y + TILE_H + 10
    draw.text((x + 2, label_y), name, font=font_label, fill=INK)
    kind_x = x + 2 + int(draw.textlength(name, font=font_label)) + 8
    draw.rounded_rectangle(
        (kind_x, label_y + 3, kind_x + 42, label_y + 22),
        radius=10,
        fill=tuple(int(BG[i] + (accent[i] - BG[i]) * 0.16) for i in range(3)),
    )
    draw.text((kind_x + 10, label_y + 4), kind, font=font_kind, fill=accent)

out = SHOTS / "apps-overview.png"
canvas.save(out, optimize=True)
print(out, canvas.size)
