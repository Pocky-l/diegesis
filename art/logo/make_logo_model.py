"""Writes docs/logo.geo.json and docs/logo_texture.png: a stone station block with the library's lever and a push button on
its control panel.

    python art/logo/make_logo_model.py
    python ../../tools/blockbench/render_logo.py docs/logo.geo.json docs/logo_texture.png common/src/main/resources/logo.png 145 34 0 3d5fb8 111833 a8c4ff

Every face has its own region of the texture, painted at its exact size, so borders and highlights line up with the edges.
"""
import json
import math
import pathlib
import random

from PIL import Image

DOCS = pathlib.Path(__file__).resolve().parents[2] / "docs"
ATLAS_WIDTH = 64

# Light, base, shade, dark per material.
STONE = ["#c3cad6", "#a9b1bf", "#929aa9", "#6c7383"]
STONE_TOP = ["#dfe4ec", "#ccd3de", "#b9c1ce", "#8f97a6"]
PANEL = ["#4b5f92", "#2c3a62", "#25315a", "#161e3a"]
IRON = ["#e2e6ec", "#b9c0ca", "#9aa2ae", "#6d7480"]
PLATE = ["#6a7180", "#4c525e", "#41464f", "#2b2f36"]
RED = ["#ff7a5c", "#e0402f", "#c22f26", "#86191a"]
GREEN = ["#c8ffb4", "#6ee05a", "#43b83c", "#22742a"]


def rgba(colour):
    return tuple(int(colour[k:k + 2], 16) for k in (1, 3, 5)) + (255,)


def noise(img, ramp, rng, amount=0.18):
    """Fills with the base colour and a sparse speckle of its neighbours."""
    for y in range(img.height):
        for x in range(img.width):
            r = rng.random()
            img.putpixel((x, y), rgba(ramp[2] if r < amount else ramp[0] if r > 1 - amount / 3 else ramp[1]))


def bevel(img, ramp, inset=0):
    """Light top and left edge, dark bottom and right edge."""
    w, h = img.size
    x0, y0, x1, y1 = inset, inset, w - 1 - inset, h - 1 - inset
    for x in range(x0, x1 + 1):
        img.putpixel((x, y0), rgba(ramp[0]))
        img.putpixel((x, y1), rgba(ramp[3]))
    for y in range(y0, y1 + 1):
        img.putpixel((x0, y), rgba(ramp[0]))
        img.putpixel((x1, y), rgba(ramp[3]))


def sunken(img, ramp, inset):
    """A recessed frame: dark top and left, light bottom and right."""
    w, h = img.size
    x0, y0, x1, y1 = inset, inset, w - 1 - inset, h - 1 - inset
    for x in range(x0, x1 + 1):
        img.putpixel((x, y0), rgba(ramp[3]))
        img.putpixel((x, y1), rgba(ramp[0]))
    for y in range(y0, y1 + 1):
        img.putpixel((x0, y), rgba(ramp[3]))
        img.putpixel((x1, y), rgba(ramp[0]))


def paint_stone_side(img, rng):
    noise(img, STONE, rng, 0.12)
    bevel(img, STONE)
    sunken(img, STONE, 2)


def paint_stone_top(img, rng):
    noise(img, STONE_TOP, rng, 0.12)
    bevel(img, STONE_TOP)


def paint_panel(img, rng):
    noise(img, PANEL, rng, 0.08)
    bevel(img, PANEL)


def plain(ramp, edge=True):
    def paint(img, rng):
        noise(img, ramp, rng, 0.1)
        if edge and img.width > 2 and img.height > 2:
            bevel(img, ramp)
    return paint


def paint_plate_top(img, rng):
    plain(PLATE)(img, rng)
    w, h = img.size
    for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        img.putpixel((x, y), rgba(IRON[1]))
    for y in range(3, h - 3):
        img.putpixel((w // 2 - 1, y), rgba(PLATE[3]))
        img.putpixel((w // 2, y), rgba(PLATE[3]))


def paint_button_top(img, rng):
    noise(img, GREEN, rng, 0.0)
    bevel(img, GREEN)
    w, h = img.size
    img.putpixel((1, 1), rgba(GREEN[0]))
    if w > 3:
        img.putpixel((2, 1), rgba(GREEN[0]))


def paint_red_top(img, rng):
    noise(img, RED, rng, 0.1)
    bevel(img, RED)
    for x, y in ((1, 1), (2, 1), (1, 2), (2, 2)):
        img.putpixel((x, y), rgba("#ffb59e"))


class Atlas:
    """Shelf packer: each face gets its own region painted at its size."""

    def __init__(self):
        self.faces, self.x, self.y, self.row = [], 0, 0, 0
        self.rng = random.Random(7)

    def add(self, w, h, painter):
        w, h = max(1, math.ceil(w)), max(1, math.ceil(h))
        if self.x + w > ATLAS_WIDTH:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        u, v = self.x, self.y
        self.x += w
        self.row = max(self.row, h)
        self.faces.append((u, v, w, h, painter))
        return u, v

    def image(self):
        height = 1 << max(4, math.ceil(math.log2(self.y + self.row)))
        atlas = Image.new("RGBA", (ATLAS_WIDTH, height), (0, 0, 0, 0))
        for u, v, w, h, painter in self.faces:
            face = Image.new("RGBA", (w, h))
            painter(face, self.rng)
            atlas.paste(face, (u, v))
        return atlas


def cube(atlas, origin, size, side, top=None, bottom=None):
    w, h, d = size
    dims = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    paint = {"up": top or side, "down": bottom or side}
    uv = {}
    for face, (fw, fh) in dims.items():
        u, v = atlas.add(fw, fh, paint.get(face, side))
        uv[face] = {"uv": [u, v], "uv_size": [fw, fh]}
    return {"origin": origin, "size": size, "uv": uv}


def main():
    atlas = Atlas()
    stem = plain(IRON)
    bones = [
        {"name": "block", "pivot": [0, 0, 0], "cubes": [
            cube(atlas, [-8, 0, -8], [16, 16, 16], paint_stone_side, paint_stone_top, plain(STONE, False))]},
        {"name": "panel", "pivot": [0, 16, 0], "cubes": [
            cube(atlas, [-6.5, 16, -6.5], [13, 0.5, 13], plain(PANEL, False), paint_panel)]},
        {"name": "base", "pivot": [0, 16.5, 0], "cubes": [
            cube(atlas, [-3, 16.5, -5], [6, 1.5, 8], plain(PLATE, False), paint_plate_top)]},
        {"name": "lever", "pivot": [0, 18, -1], "rotation": [-24, 0, 0], "cubes": [
            cube(atlas, [-1, 17, -2], [2, 8, 2], stem),
            cube(atlas, [-2.5, 24.5, -3.5], [5, 5, 5], plain(RED), paint_red_top)]},
        {"name": "button", "pivot": [0, 16.5, 0], "cubes": [
            cube(atlas, [-5.5, 16.5, 1.5], [4, 0.75, 4], plain(PLATE, False), plain(PLATE)),
            cube(atlas, [-5, 17.25, 2], [3, 1, 3], plain(GREEN, False), paint_button_top)]},
    ]
    image = atlas.image()
    image.save(DOCS / "logo_texture.png")
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.diegesis_logo", "texture_width": image.width, "texture_height": image.height,
                        "visible_bounds_width": 3, "visible_bounds_height": 3.4, "visible_bounds_offset": [0, 1.2, 0]},
        "bones": bones}]}
    (DOCS / "logo.geo.json").write_text(json.dumps(geo, indent=1) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
