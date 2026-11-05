# Draws the NINE'tok launcher icon: a white "9" with TikTok-style cyan/red offset shadows on black.
# Usage: python make_icon.py <font.ttf> <out.png>
import sys
from PIL import Image, ImageChops, ImageDraw, ImageFont

SIZE = 1024
CYAN = (37, 244, 238)
RED = (254, 44, 85)
# Launchers show the middle two thirds (683 px) of this layer. TikTok's note fills 66.5% of that
# height, about 454 px, so the 9 with both shadows is sized to match it.
OFFSET = 19          # shadow shift in px, like the note's split colors
GLYPH_HEIGHT = 416


def layer(font, color, dx, dy):
    img = Image.new("RGB", (SIZE, SIZE), (0, 0, 0))
    draw = ImageDraw.Draw(img)
    left, top, right, bottom = draw.textbbox((0, 0), "9", font=font)
    x = (SIZE - (right - left)) / 2 - left + dx
    y = (SIZE - (bottom - top)) / 2 - top + dy
    draw.text((x, y), "9", font=font, fill=color)
    return img


def main(font_path, out_path):
    # Find the font size that makes the glyph GLYPH_HEIGHT tall.
    size = 100
    while True:
        font = ImageFont.truetype(font_path, size)
        left, top, right, bottom = ImageDraw.Draw(Image.new("RGB", (1, 1))).textbbox((0, 0), "9", font=font)
        if bottom - top >= GLYPH_HEIGHT:
            break
        size += 4

    cyan = layer(font, CYAN, -OFFSET, -OFFSET)
    red = layer(font, RED, OFFSET, OFFSET)
    icon = ImageChops.add(cyan, red)          # overlap turns near-white, as in the logo
    white = layer(font, (255, 255, 255), 0, 0)
    icon = ImageChops.lighter(icon, white)
    icon.save(out_path)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
