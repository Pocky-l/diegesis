#!/usr/bin/env python3
"""Finds the faces of a scene that will flicker, before anyone sees it in the game.

A model on its own can be clean and still fight in a scene: a liquid's walls lie on the walls of the sink, a glass stands
with its open bottom on the table. This puts every face of a scene in one space - the station block's model, the models of
all nodes through their transforms, the walls of every tank - and looks for parallel faces that lie on each other.

    python tools/scenes/depth_check.py [scene.json ...] [--assets DIR ...] [--blocks FILE] [--notes]

Without scene files it checks every scene under the assets. The station's block is not in a scene: name its models in
tools/scenes/blocks.json ("namespace:scene": [{"model": "namespace:block/name", "at": [x, y, z]}]).

What it reports, and what the game does with each kind:
  ERROR    solid on solid in the same plane (or within COPLANAR px), both seen from one side: flickers at any distance
  WARNING  solid on solid closer than NEAR px: flickers from the distance given
  WARNING  see-through on see-through in one plane: these write no depth, so the batch's sort decides and the order pops
  note     see-through on solid (--notes): settled by the see-through layer's polygon offset, nothing to do

Faces back to back (a bottle's underside on the table) are fine: only one of them can face the camera.
Exit code 1 if there are errors.
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ASSETS = ["common/src/main/resources/assets"]
COPLANAR = 0.002   # px: the same plane
NEAR = 0.05        # px: closer than this fights within the distance a station is drawn from
MIN_AREA = 0.05    # px^2: slivers never show
PARALLEL = 0.9999
NEAR_PLANE = 0.05  # blocks
DEPTH_BITS = 24
ROUND_SEGMENTS = 14
DEFAULT_ANCHOR = (8.0, 0.0, 8.0)


class Face:
    __slots__ = ("points", "normal", "offset", "low", "high", "owner", "name", "clear", "two_sided")

    def __init__(self, points, owner, name, clear, two_sided):
        self.points = points
        normal = np.zeros(3)
        for i in range(len(points)):
            normal += np.cross(points[i], points[(i + 1) % len(points)])
        length = np.linalg.norm(normal)
        self.normal = normal / length if length > 1e-9 else None
        self.offset = float(self.normal @ points[0]) if self.normal is not None else 0.0
        self.low = points.min(axis=0)
        self.high = points.max(axis=0)
        self.owner = owner
        self.name = name
        self.clear = clear
        self.two_sided = two_sided


def flicker_distance(gap_px):
    """From how far (blocks) two faces gap_px apart start to fight in a 24 bit depth buffer."""
    return math.sqrt(gap_px / 16.0 * NEAR_PLANE * 2 ** DEPTH_BITS)


def rotation_yxz(degrees):
    x, y, z = (math.radians(v) for v in degrees)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return ry @ rx @ rz


def node_matrix(node):
    """Translate, rotate (y, x, z), scale: as Transform and NodeInstance build it."""
    matrix = np.eye(4)
    scale = node.get("scale", 1.0)
    scale = [scale] * 3 if isinstance(scale, (int, float)) else scale
    matrix[:3, :3] = rotation_yxz(node.get("rotation", [0, 0, 0])) @ np.diag(scale)
    matrix[:3, 3] = node.get("pos", [0, 0, 0])
    return matrix


def transform(matrix, points):
    return points @ matrix[:3, :3].T + matrix[:3, 3]


def split_id(resource):
    namespace, _, path = resource.partition(":")
    return (namespace, path) if path else ("minecraft", namespace)


class Assets:
    def __init__(self, roots):
        self.roots = [Path(root) for root in roots]
        self.missing = set()

    def find(self, kind, resource):
        namespace, path = split_id(resource)
        for root in self.roots:
            file = root / namespace / kind / (path + ".json")
            if file.is_file():
                return file
        return None

    def read(self, kind, resource):
        file = self.find(kind, resource)
        return json.loads(file.read_text(encoding="utf-8")) if file else None

    def elements(self, resource):
        """The model's elements, its parents' if it has none; None if the model is not among the assets."""
        seen = set()
        while resource and resource not in seen:
            seen.add(resource)
            model = self.read("models", resource)
            if model is None:
                if split_id(resource)[0] != "minecraft":
                    self.missing.add(resource)
                return [] if len(seen) > 1 else None
            if model.get("elements"):
                return model["elements"]
            resource = model.get("parent")
        return []

    def scenes(self):
        for root in self.roots:
            yield from sorted(root.glob("*/diegesis/scenes/**/*.json"))


def element_faces(element):
    """Corners of the element's faces, with its rotation applied, in the model's pixels."""
    x0, y0, z0 = (float(v) for v in element["from"])
    x1, y1, z1 = (float(v) for v in element["to"])
    corners = {
        "down": [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
        "north": [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)],
        "south": [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
        "west": [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
        "east": [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)],
    }
    rotation = element.get("rotation")
    for side in element.get("faces", {}):
        points = np.array(corners[side], dtype=float)
        if rotation and rotation.get("angle"):
            origin = np.array(rotation["origin"], dtype=float)
            degrees = [0, 0, 0]
            degrees["xyz".index(rotation["axis"])] = rotation["angle"]
            points = (points - origin) @ rotation_yxz(degrees).T + origin
        yield side, points


def box_faces(low, high):
    """Faces of a box, turned outwards."""
    element = {"from": low, "to": high, "faces": dict.fromkeys(("down", "up", "north", "south", "west", "east"), {})}
    return element_faces(element)


def round_faces(low, high):
    """Faces of the cylinder a round tank is drawn as."""
    low, high = np.array(low, dtype=float), np.array(high, dtype=float)
    middle = (low + high) / 2.0
    half = (high - low) / 2.0
    ring = []
    for i in range(ROUND_SEGMENTS):
        angle = 2.0 * math.pi * i / ROUND_SEGMENTS
        ring.append((middle[0] + math.cos(angle) * half[0], middle[2] + math.sin(angle) * half[2]))
    for i in range(ROUND_SEGMENTS):
        (ax, az), (bx, bz) = ring[i], ring[(i + 1) % ROUND_SEGMENTS]
        yield f"wall {i}", np.array([(ax, low[1], az), (ax, high[1], az), (bx, high[1], bz), (bx, low[1], bz)])
    yield "bottom", np.array([(x, low[1], z) for x, z in ring])
    yield "top", np.array([(x, high[1], z) for x, z in reversed(ring)])


def merged(base, over):
    result = dict(base)
    for key, value in over.items():
        result[key] = merged(result[key], value) if isinstance(value, dict) and isinstance(result.get(key), dict) else value
    return result


class Scene:
    def __init__(self, assets):
        self.assets = assets
        self.faces = []
        self.skipped = []

    def add(self, points_by_side, matrix, owner, clear, two_sided):
        for side, points in points_by_side:
            face = Face(transform(matrix, points), owner, side, clear, two_sided)
            if face.normal is not None:
                self.faces.append(face)

    def add_visual(self, visual, matrix, owner):
        clear = bool(visual.get("translucent")) or visual.get("glass", 0) > 0
        two_sided = bool(visual.get("double_sided"))
        anchor = np.eye(4)
        anchor[:3, 3] = -np.array(visual.get("anchor", DEFAULT_ANCHOR), dtype=float)
        placed = matrix @ anchor
        if "model" in visual:
            elements = self.assets.elements(visual["model"])
            if elements is None:
                self.skipped.append(f"{owner}: model {visual['model']} not found")
                return
            for element in elements:
                self.add(element_faces(element), placed, owner, clear, two_sided)
        elif "block" in visual:
            # A plain block: its full cube is what most of them are.
            self.add(box_faces([0, 0, 0], [16, 16, 16]), placed, owner, clear, two_sided)
        else:
            self.skipped.append(f"{owner}: item and mesh visuals are not checked")

    def add_node(self, node, parent):
        if "template" in node:
            template = self.assets.read("diegesis/nodes", node["template"])
            if template is None:
                self.skipped.append(f"{node.get('id')}: template {node['template']} not found")
            else:
                node = merged(template, node)
        matrix = parent @ node_matrix(node)
        name = node.get("id", "?")
        if node.get("hidden"):
            self.skipped.append(f"{name}: hidden")
        else:
            for key in ("visual", "base"):
                if isinstance(node.get(key), dict):
                    self.add_visual(node[key], matrix, name)
            tank = node.get("tank")
            if tank and "inner" in tank:
                inner = tank["inner"]
                faces = round_faces(inner["from"], inner["to"]) if tank.get("round") else box_faces(inner["from"], inner["to"])
                self.add(faces, matrix, f"{name} (liquid)", True, True)
        for child in node.get("children", []):
            self.add_node(child, matrix)


def plane_axes(normal):
    axis = np.eye(3)[int(np.argmin(np.abs(normal)))]
    first = np.cross(normal, axis)
    first /= np.linalg.norm(first)
    return first, np.cross(normal, first)


def clip(subject, clipper):
    """Part of a convex polygon inside another (Sutherland-Hodgman), both 2D and counter-clockwise."""
    output = subject
    for i in range(len(clipper)):
        a, b = clipper[i], clipper[(i + 1) % len(clipper)]
        edge = b - a
        points, output = output, []
        for j in range(len(points)):
            p, q = points[j], points[(j + 1) % len(points)]
            dp = edge[0] * (p[1] - a[1]) - edge[1] * (p[0] - a[0])
            dq = edge[0] * (q[1] - a[1]) - edge[1] * (q[0] - a[0])
            if dp >= 0.0:
                output.append(p)
            if (dp >= 0.0) != (dq >= 0.0):
                output.append(p + (q - p) * (dp / (dp - dq)))
        if not output:
            return []
    return output


def area(polygon):
    total = 0.0
    for i in range(len(polygon)):
        p, q = polygon[i], polygon[(i + 1) % len(polygon)]
        total += p[0] * q[1] - q[0] * p[1]
    return total / 2.0


def flat(face, first, second):
    polygon = [np.array((point @ first, point @ second)) for point in face.points]
    return polygon if area(polygon) > 0.0 else polygon[::-1]


def overlap(a, b):
    first, second = plane_axes(a.normal)
    return abs(area(clip(flat(a, first, second), flat(b, first, second)) or []))


def pairs(faces):
    for i, a in enumerate(faces):
        for b in faces[i + 1:]:
            if a.owner == b.owner and a.clear and b.clear:
                continue
            if np.any(a.low > b.high + NEAR) or np.any(b.low > a.high + NEAR):
                continue
            facing = float(a.normal @ b.normal)
            if abs(facing) < PARALLEL:
                continue
            gap = abs(float(a.normal @ b.points[0]) - a.offset)
            if gap >= NEAR - 1e-5:
                continue
            # Back to back only one can face the camera, unless the other one shows from both sides.
            if facing < 0.0 and not a.two_sided and not b.two_sided:
                continue
            shared = overlap(a, b)
            if shared >= MIN_AREA:
                yield a, b, gap, shared


def describe(a, b, gap, shared):
    point = np.round(a.points.mean(axis=0), 2).tolist()
    return f"{a.owner} [{a.name}] / {b.owner} [{b.name}]: gap {gap:.3f} px, {shared:.2f} px2, near {point}"


def check(scene_file, assets, blocks, notes):
    parts = scene_file.as_posix().split("/diegesis/scenes/", 1)
    scene_id = f"{parts[0].rsplit('/', 1)[-1]}:{parts[1][:-5]}"
    scene = Scene(assets)
    for block in blocks.get(scene_id, []):
        elements = assets.elements(block["model"])
        if elements is None:
            scene.skipped.append(f"block model {block['model']} not found")
            continue
        at = np.eye(4)
        at[:3, 3] = block.get("at", [0, 0, 0])
        for element in elements:
            scene.add(element_faces(element), at, f"block {block['model'].split('/')[-1]}", False, False)
    for node in json.loads(scene_file.read_text(encoding="utf-8")).get("nodes", []):
        scene.add_node(node, np.eye(4))

    errors, warnings, settled = [], [], []
    for a, b, gap, shared in pairs(scene.faces):
        text = describe(a, b, gap, shared)
        if a.clear != b.clear:
            settled.append(text)
        elif a.clear:
            if gap < COPLANAR:
                warnings.append("sort tie: " + text)
        elif gap < COPLANAR:
            errors.append(text)
        else:
            warnings.append(f"from {flicker_distance(gap):.0f} blocks: {text}")
    print(f"{scene_id}: {len(scene.faces)} faces, {len(errors)} errors, {len(warnings)} warnings, {len(settled)} settled"
          + ("" if scene_id in blocks else " (no block model named in blocks.json)"))
    for text in errors:
        print("  ERROR   " + text)
    for text in warnings:
        print("  WARNING " + text)
    if notes:
        for text in settled:
            print("  note    " + text)
        for text in scene.skipped:
            print("  skipped " + text)
    return len(errors)


def main():
    parser = argparse.ArgumentParser(description="Finds faces of a scene that flicker.")
    parser.add_argument("scenes", nargs="*", type=Path)
    parser.add_argument("--assets", action="append", help="an assets directory; repeatable")
    parser.add_argument("--blocks", type=Path, default=Path(__file__).with_name("blocks.json"))
    parser.add_argument("--notes", action="store_true", help="also list what the game settles and what was not checked")
    args = parser.parse_args()
    assets = Assets(args.assets or [ROOT / path for path in DEFAULT_ASSETS])
    blocks = json.loads(args.blocks.read_text(encoding="utf-8")) if args.blocks.is_file() else {}
    errors = sum(check(file, assets, blocks, args.notes) for file in args.scenes or assets.scenes())
    for model in sorted(assets.missing):
        print("missing model: " + model)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
