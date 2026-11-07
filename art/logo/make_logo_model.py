"""Writes docs/logo.geo.json and docs/logo_texture.png: a stone block with the library's lever on it.

    python art/logo/make_logo_model.py
    python ../../tools/blockbench/render_logo.py docs/logo.geo.json docs/logo_texture.png common/src/main/resources/logo.png 35 28 0 4a6cb0 1f2f5c cfe0ff
"""
import json
import pathlib
import random

from PIL import Image

DOCS = pathlib.Path(__file__).resolve().parents[2] / "docs"
CELL = 16
RAMPS = {
    "top": ["#c9d4e6", "#d6dfee", "#bcc9de"],
    "side": ["#96a6c2", "#a5b3cc", "#8797b6"],
    "panel": ["#3a4a72", "#435480", "#334166"],
    "iron": ["#c2c8d2", "#d8dce4", "#a9b0bc"],
    "plate": ["#4a4f5c", "#565c6a", "#3f4450"],
    "red": ["#d8392f", "#ea5a44", "#b52a26"],
}
NAMES = list(RAMPS)


def texture():
    random.seed(7)
    atlas = Image.new("RGBA", (CELL * len(NAMES), CELL), (0, 0, 0, 0))
    for i, name in enumerate(NAMES):
        ramp = RAMPS[name]
        for y in range(CELL):
            for x in range(CELL):
                colour = ramp[0] if random.random() < 0.62 else random.choice(ramp[1:])
                atlas.putpixel((i * CELL + x, y), tuple(int(colour[k:k + 2], 16) for k in (1, 3, 5)) + (255,))
    atlas.save(DOCS / "logo_texture.png")
    return atlas


def cube(origin, size, cell, top=None):
    """A cube with every face on one cell of the atlas, `top` on another."""
    def face(name, w, h):
        index = NAMES.index(top if name == "up" and top else cell)
        return {"uv": [index * CELL, 0], "uv_size": [min(w, CELL), min(h, CELL)]}
    w, h, d = size
    return {"origin": origin, "size": size, "uv": {
        "north": face("north", w, h), "south": face("south", w, h), "east": face("east", d, h), "west": face("west", d, h),
        "up": face("up", w, d), "down": face("down", w, d)}}


def main():
    atlas = texture()
    bones = [
        {"name": "block", "pivot": [0, 0, 0], "cubes": [cube([-8, 0, -8], [16, 16, 16], "side", "top")]},
        {"name": "panel", "pivot": [0, 16, 0], "cubes": [cube([-6, 16, -6], [12, 0.4, 12], "panel")]},
        {"name": "base", "pivot": [0, 16, 0], "cubes": [cube([-4, 16.4, -5], [8, 2, 10], "plate")]},
        {"name": "lever", "pivot": [0, 18, 0], "rotation": [-22, 0, 0], "cubes": [
            cube([-1.5, 17, -1.5], [3, 7, 3], "iron"), cube([-3, 24, -3], [6, 6, 6], "red")]},
    ]
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.diegesis_logo", "texture_width": atlas.width, "texture_height": atlas.height,
                        "visible_bounds_width": 3, "visible_bounds_height": 3.4, "visible_bounds_offset": [0, 1.2, 0]},
        "bones": bones}]}
    (DOCS / "logo.geo.json").write_text(json.dumps(geo, indent=1) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
