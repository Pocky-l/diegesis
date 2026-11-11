"""Writes the JSON schemas of Diegesis scene and node template files, and checks them against the code.

    python tools/docs/schema.py          # write docs/schema/scene.schema.json and node.schema.json
    python tools/docs/schema.py --check  # also compare the fields with the codecs and validate the shipped files

The field descriptions below are the source; the JSON files are generated from them. When a codec in
common/src/main/java/com/pockyl/diegesis/api/scene gains, loses or renames a field, change it here too: --check fails
until both agree.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "schema"
BASE_URL = "https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/schema/"

# ---------------------------------------------------------------------------------------------------------------------
# Value types
# ---------------------------------------------------------------------------------------------------------------------

VEC3 = {"$ref": "#/definitions/vec3"}
XZ = {"$ref": "#/definitions/xz"}
COLOR = {"$ref": "#/definitions/color"}
ID = {"$ref": "#/definitions/resource_location"}
NODE_ID = {"$ref": "#/definitions/node_id"}
BLOCK_STATE = {"$ref": "#/definitions/block_state"}
BOX = {"$ref": "#/definitions/box"}
VISUAL = {"$ref": "#/definitions/visual"}
DISPLAY = {"type": "string", "enum": ["flat", "upright", "floating", "hidden"]}


def num(lo=None, hi=None, integer=False):
    schema = {"type": "integer" if integer else "number"}
    if lo is not None:
        schema["minimum"] = lo
    if hi is not None:
        schema["maximum"] = hi
    return schema


BOOL = {"type": "boolean"}
STRING = {"type": "string"}


def field(schema, description, default=None):
    out = dict(schema)
    out["description"] = description
    if default is not None:
        out["default"] = default
    return out


# Each object: (java file, description, {field: schema}, required fields, extra keywords)
OBJECTS = {}


def obj(name, java, description, fields, required=(), **extra):
    OBJECTS[name] = (java, description, fields, list(required), extra)


obj("scene", "SceneDefinition",
    "A Diegesis scene: what a station shows and lets the player touch. File: assets/<namespace>/diegesis/scenes/<path>.json."
    " Coordinates are Blockbench pixels (16 = one block) relative to the block's corner, for a block facing north"
    " (its front on the -Z side); the library turns the scene with the block.",
    {
        "camera": field({"$ref": "#/definitions/camera"}, "Where the camera goes while the player works here."),
        "work_plane": field(num(), "Height (pixels) of the surface carried objects and the held stack move over, usually"
                                   " the table top.", 16),
        "idle_visible": field(BOOL, "Draw the nodes while nobody works at the block (needs a block entity).", True),
        "nodes": field({"type": "array", "items": {"$ref": "#/definitions/node"}}, "The top-level nodes."),
        "inventory": field({"$ref": "#/definitions/inventory"}, "Lays the player's 36 inventory slots out in the scene"
                                                                " instead of the vanilla panel on the screen."),
        "interface": field({"$ref": "#/definitions/interface"}, "How the interface behaves and looks."),
        "carry_planes": field({"type": "array", "items": {"$ref": "#/definitions/carry_plane"}},
                              "Levels carried objects rise to over parts of the scene, or areas closed to them."),
        "inventory_drawer": field({"$ref": "#/definitions/inventory_drawer"},
                                  "Keeps the inventory in a drawer at a side of the screen; not together with inventory."),
        "pouring": field({"$ref": "#/definitions/pouring"}, "How pouring looks."),
        "captions": field({"$ref": "#/definitions/captions"}, "How the lines of StationView.showCaption look."),
        "clear_view": field({"$ref": "#/definitions/clear_view"}, "What stands between the camera and the station dissolves, or is left out of the picture."),
        "gains": field({"$ref": "#/definitions/gains"}, "What the player gets at the station flies into the inventory."),
        "put_away": field(BOOL, "A right click with a stack on the cursor that nothing in the scene takes puts the stack back"
                                " into the inventory, to the slot it came from while that is free.", True),
        "placing": field({"$ref": "#/definitions/placing"}, "Lets items be put down anywhere on the scene's surfaces, turned and"
                                                            " sized freely."),
        "hud": field({"$ref": "#/definitions/hud"}, "How the game's HUD slides away when the player starts to work here, and"
                                                    " back when they leave."),
    })

obj("camera", "CameraRig", "The camera rig used while a player works at the station.", {
    "position": field(VEC3, "Camera position, scene pixels.", [8, 36, -12]),
    "target": field(VEC3, "The point the camera looks at, scene pixels.", [8, 16, 7]),
    "fov": field(num(0, 170), "Vertical field of view in degrees while focused; 0 keeps the player's setting.", 0),
    "transition": field(num(0, 10), "Seconds the camera takes to fly in and out.", 0.6),
    "inventory": field({"$ref": "#/definitions/camera_shot"}, "A second shot the camera glides to while the scene's"
                                                              " inventory is shown, and back when it hides."),
    "seats": field({"type": "object", "additionalProperties": {"$ref": "#/definitions/camera_shot"}},
                   "The view from each seat of the station, by seat id: a player in a seat sees that shot. Who has no seat,"
                   " or a seat without a shot, sees the rig's own."),
    "glances": field({"type": "array", "items": {"$ref": "#/definitions/camera_glance"}},
                     "Other places the player's look can go to: the cursor resting at an edge of the screen, or code,"
                     " glides the camera there and back."),
    "neighbors": field({"$ref": "#/definitions/camera_neighbors"},
                       "Going over to a station standing next to this one: the cursor resting at the edge of the screen on"
                       " its side flies the camera there."),
})

obj("gains", "GainOptions", "What the player gets at the station, seen going into the inventory; every field is optional.", {
    "enabled": field(BOOL, "A stack that comes into the inventory, other than from the player's own cursor, flies from the station to"
                           " its slot, which bounces as it lands.", True),
    "time": field(num(0.05, 3), "Seconds a stack flies.", 0.45),
    "hold": field(num(0, 10), "Seconds a stack whose slot the drawer does not show stands next to the drawer.", 1.6),
    "sound": field(BOOL, "A soft pop as it lands.", True),
    "count": field(BOOL, "\"+N\" shows how many came: rising from the slot, or beside the stack by the drawer.", True),
})

obj("camera_neighbors", "CameraNeighbors", "Going over to neighbouring stations without leaving; every field is optional.", {
    "enabled": field(BOOL, "Neighbours are reachable from this station, and this station from theirs.", True),
    "range": field({"type": "integer", "minimum": 1, "maximum": 8},
                   "How far another station may stand, blocks along the floor; one up or down counts too.", 3),
    "zone": field(num(0.005, 0.3), "How far from the edge the cursor counts as being at it, as a part of the screen.", 0.03),
    "delay": field(num(0, 2), "Seconds the cursor must rest there, so that passing by moves nothing.", 0.3),
    "time": field(num(0.05, 5), "Seconds the camera takes to fly over.", 0.65),
    "hint": field(BOOL, "Draw a small arrow at an edge that leads to a neighbour, as the cursor comes near it.", True),
    "hint_color": field(COLOR, "Colour of the arrow; its alpha is the strongest it gets, with the cursor at the edge.",
                        "#D0FFFFFF"),
    "keys": field(BOOL, "The movement and arrow keys go over at once to the neighbour on their side: A, D and left,"
                         " right; W and up to the top edge, S and down to the bottom one.", True),
})

obj("camera_glance", "CameraGlance", "A place the player's look can go to while working at the station.", {
    "id": field(NODE_ID, "Unique in the scene; StationView.glance and SceneEvent.Glance use it."),
    "edge": field({"type": "string", "enum": ["left", "right", "top", "bottom"]},
                  "The edge of the screen the cursor rests at to look here; without one only code does. One glance per"
                  " edge, and not the inventory drawer's."),
    "zone": field(num(0.01, 0.5), "How far from that edge the cursor counts as being at it, as a part of the screen.", 0.1),
    "delay": field(num(0, 2), "Seconds the cursor must rest there, so that passing by moves nothing.", 0.12),
    "position": field(VEC3, "Camera position, scene pixels."),
    "target": field(VEC3, "The point the camera looks at, scene pixels."),
    "fov": field(num(0, 170), "Field of view in degrees; 0 keeps the rig's.", 0),
    "time": field(num(0, 10), "Seconds the camera takes to get there and back.", 0.45),
    "back_on_take": field(BOOL, "Look back as soon as a stack comes onto the cursor here.", True),
    "back_zone": field(num(0, 0.5), "The strip along the opposite edge that looks back, as a part of the screen; 0: no"
                                    " strip, only a taken stack or code ends the glance.", 0.12),
    "hint": field(BOOL, "Draw a small arrow at the edge the look can go to or come back from.", True),
    "hint_color": field(COLOR, "Colour of the arrow; its alpha is the strongest it gets, with the cursor at the edge.",
                        "#D0FFFFFF"),
}, required=["id", "position", "target"])

obj("clear_view", "ClearViewOptions", "Keeps the view of the station free while a player works at it; every field is optional.", {
    "entities": field(BOOL, "Entities dissolve.", True),
    "particles": field(BOOL, "The world's particles dissolve; the scene's own never do.", True),
    "radius": field(num(0, 256), "How wide the clear cone is at the station, in pixels.", 16),
    "soft": field(num(1, 256), "How far beyond radius things come back to full strength, in pixels.", 12),
    "blocks": field(BOOL, "Blocks the camera stands in, or that stand between it and the station's work surface (a wall cabinet"
                          " over the station, a low ceiling), are not drawn while the player works here. The shot stays as"
                          " written.", True),
})

obj("hud", "HudOptions", "How the game's HUD leaves and comes back; every field is optional.", {
    "slide": field(BOOL, "Every part of the HUD slides off to the screen edge nearest to it (the hotbar down, the chat left),"
                         " other mods' overlays too; false: it is gone and back at once.", True),
    "seconds": field(num(0.05, 3), "How long the slide takes.", 0.3),
})

obj("captions", "CaptionOptions", "How the lines of StationView.showCaption look; every field is optional.", {
    "edge": field({"enum": ["top", "bottom"]}, "The screen edge they sit at.", "top"),
    "color": field(COLOR, "The text's colour.", "#FFFFFFFF"),
    "background": field(COLOR, "The band behind the text; a zero alpha draws none.", "#80000000"),
})

obj("placing", "PlacingOptions", "Free placing: items of a slot group with placed() rules stand anywhere on these surfaces.", {
    "group": field(NODE_ID, "The slot group that holds the items."),
    "surfaces": field({"type": "array", "minItems": 1, "items": {"$ref": "#/definitions/place_surface"}}, "Where they can stand."),
    "item_size": field(num(0.5, 64), "Pixels the longest side of an item measures at size 1.", 6),
    "turn_step": field(num(1, 180), "Degrees of one notch of the wheel.", 15),
    "scale_range": field({"type": "array", "items": num(), "minItems": 2, "maxItems": 2}, "The sizes a player can give an item.", [0.5, 1.5]),
    "scale_step": field(num(0.01, 4), "What one notch of the wheel changes the size by.", 0.1),
    "turn_speed": field(num(0.05, 8), "Degrees an item turns per GUI pixel of a drag.", 1.2),
    "stacking": field(BOOL, "Items can stand on each other.", True),
    "block_models": field(BOOL, "A block item with a flat icon stands there as the block.", True),
    "item_light": field(BOOL, "Items of blocks that give light shine on the scene, candles and torches burn.", True),
    "light_radius": field(num(1, 256), "How far their light reaches, in pixels.", 11),
    "light_color": field(COLOR, "Colour of the pool of light they throw; alpha is its strength.", "#C0FFC888"),
    "shadows": field(BOOL, "Items cast their shape on what they stand on.", True),
    "snap": field(num(0, 16), "Positions are rounded to this many pixels; 0 keeps them.", 0.25),
    "preview_color": field(COLOR, "Colour of the see-through copy of the item in the hand.", "#A0FFFFFF"),
}, required=["group", "surfaces"])

obj("place_surface", "PlaceSurface", "A level surface items can stand on.", {
    "from": field(XZ, "One corner, [x, z]."),
    "to": field(XZ, "The opposite corner, [x, z]."),
    "height": field(num(), "Its height in scene pixels."),
    "clearance": field(num(0.5, 256), "Room above it, in pixels.", 64),
    "aim_height": field(num(0, 256), "0: the cursor points at the surface. Otherwise at a slope up to this height over the back edge.", 0),
    "wall": field(num(), "The z of a wall behind the surface: light in the room falls on it too."),
}, required=["from", "to", "height"])

obj("camera_shot", "CameraShot", "Another view of the scene, in the rig's coordinates.", {
    "position": field(VEC3, "Camera position, scene pixels."),
    "target": field(VEC3, "The point the camera looks at, scene pixels."),
    "fov": field(num(0, 170), "Field of view in degrees; 0 keeps the rig's.", 0),
}, required=["position", "target"])

obj("node", "NodeDefinition",
    "One object of the scene. It has a place, optionally a look, and behaviours. At most one of grabbable, slot,"
    " output, control (put others on child nodes). Children move with their parent.",
    {
        "id": field(NODE_ID, "Unique in the scene; behaviours and code refer to the node by it."),
        "template": field(ID, "A node template, assets/<namespace>/diegesis/nodes/<path>.json, whose fields this node"
                              " starts from: objects merge field by field, null removes a field. Built-in:"
                              " diegesis:control/lever, switch, button, knob, slider."),
        "repeat": field({"type": "object", "additionalProperties": False, "required": ["count", "step"],
                         "properties": {"count": VEC3, "step": VEC3}},
                        "The node once per cell of a grid: count [x, y, z] cells, each step [x, y, z] pixels further on, ids"
                        " <id>_0, <id>_1... (x counts fastest; children get the same ending)."),
        "pos": field(VEC3, "Position relative to the parent (or the block corner), pixels.", [0, 0, 0]),
        "rotation": field(VEC3, "Euler degrees, applied y (yaw), then x (pitch), then z (roll).", [0, 0, 0]),
        "scale": field({"oneOf": [num(), VEC3]}, "A factor: one number, or one per axis.", 1),
        "visual": field(VISUAL, "What is drawn; without one the node is an invisible pivot or hit zone."),
        "collider": field(BOX, "What the cursor can hit, in the node's own pixels; derived from the visual (or the"
                               " role) when absent. Give plane-made vanilla blocks (torches, plants) an explicit one."),
        "strokes": field(BOOL, "A press on the node starts a stroke: SceneEvent.Stroke every frame until the button is let go. Role-less nodes only.", False),
    "precise": field(BOOL, "A mesh visual is hit only where its polygons are, not anywhere in the collider.", False),
        "trigger": field(BOOL, "The collider is only a zone carried objects touch (SceneEvent.Contact); the cursor"
                               " passes through it.", False),
        "pickable": field(BOOL, "false: pure decoration without a hit box; the cursor and carried objects pass through"
                                " it (a needle, a part drawn on a grabbable parent).", True),
        "tooltip": field(STRING, "Text next to the cursor while it is over the node: a translation key (shown as"
                                 " written when no language has it). Code can change it (NodeView.setTooltip)."),
        "grabbable": field({"$ref": "#/definitions/grabbable"}, "The player can pick the node up and carry it."),
        "slot": field({"$ref": "#/definitions/slot"}, "The node is a place for the item of a station slot."),
        "drop_zone": field({"type": "string"}, "A slot group: a stack clicked or let go here goes into that group. The cursor passes through while nothing fits."),
        "output": field({"$ref": "#/definitions/output"}, "The node shows a station output; clicking takes it."),
        "control": field({"$ref": "#/definitions/control"}, "The node is a button, toggle, knob or slider driving a"
                                                            " station control."),
        "tank": field({"$ref": "#/definitions/tank"}, "Shows a station tank as liquid inside the node; with a spout"
                                                      " the node pours. Combines with any role."),
        "emitter": field({"$ref": "#/definitions/emitter"}, "Gives off scene particles."),
        "light": field({"$ref": "#/definitions/light"}, "Shines on the objects around it and lights the work surface."),
        "hidden": field(BOOL, "Starts invisible; code can show it (NodeView.setVisible).", False),
        "extra": field({"type": "object"}, "Free-form data for the mod's own code (NodeView.extra())."),
        "children": field({"type": "array", "items": {"$ref": "#/definitions/node"}}, "Nodes attached to this one."),
    }, required=["id"])

obj("visual", "Visual", "What a node looks like: exactly one of model, mesh, item, block.", {
    "mesh": field(ID, "A polygon model, assets/<namespace>/diegesis/meshes/<path>.obj; one unit is one pixel. Needs texture."),
    "texture": field(ID, "The mesh's texture, a sprite of the block atlas: mymod:block/chess/king for"
                         " assets/mymod/textures/block/chess/king.png, or any vanilla block texture."),
    "model": field(ID, "A block model id, e.g. mymod:diegesis/flask for assets/mymod/models/diegesis/flask.json"
                       " (every model under models/diegesis/ is loaded by itself; others need"
                       " DiegesisClient.registerModel)."),
    "item": field(ID, "An item id, drawn like an item frame shows it."),
    "block": field(BLOCK_STATE, "A block state as in commands: \"minecraft:lever[face=floor,facing=north]\"."),
    "anchor": field(VEC3, "The point of the model (its own pixels) that sits on the node's position; the default is the"
                          " centre of the model's bottom.", [8, 0, 8]),
    "tint": field(COLOR, "ARGB colour multiplied into the model; alpha below FF makes it see-through.", "#FFFFFFFF"),
    "translucent": field(BOOL, "The model has see-through textures (glass): drawn after solid parts, sorted.", False),
    "emissive": field(BOOL, "Drawn at full brightness.", False),
    "double_sided": field(BOOL, "Faces are drawn from behind too: the inner walls of an open glass vessel.", False),
    "glass": field(num(0, 1), "How clear the faces are where they look straight at the camera: they thicken towards a"
                              " grazing angle, as glass does. 0 draws the texture as it is. Implies translucent.", 0),
}, oneOf=[{"required": ["model"]}, {"required": ["mesh", "texture"]}, {"required": ["item"]}, {"required": ["block"]}])

obj("box", "Box", "An axis-aligned box, pixels.", {
    "from": field(VEC3, "One corner."),
    "to": field(VEC3, "The opposite corner."),
}, required=["from", "to"])

obj("grabbable", "Grabbable", "An object the player picks up with the cursor and carries.", {
    "lift": field(num(0, 64), "How high (pixels) the grip is held above the work surface.", 4),
    "sway": field(num(0, 4), "How much it leans while moving; 0 keeps it upright.", 1),
    "stay": field(BOOL, "Stays where it is dropped instead of flying back home.", False),
    "grip": field(VEC3, "The point it is held by, node pixels (a vial by its top); the middle of its hit box by"
                        " default."),
    "plane": field({"$ref": "#/definitions/grab_plane"}, "A plane of its own it moves in instead of the work surface"
                                                         " (lift and carry planes then do not apply)."),
    "contact": field(BOX, "The part that touches other things (a torch's flame), node pixels; the whole hit box when"
                          " absent."),
    "hold": field({"type": "string", "enum": ["auto", "drag", "click"]},
                  "auto: a quick click leaves it in the hand until the next click, pressing and dragging carries it until"
                  " the button is let go; drag or click: only that way.", "auto"),
})

obj("grab_plane", "GrabPlane", "A plane a grabbable moves in; its grip follows the cursor on it.", {
    "point": field(VEC3, "A point of the plane, scene pixels."),
    "normal": field(VEC3, "The plane's facing; [0, 0, -1] is an upright wall facing the block's front.", [0, 0, -1]),
    "bounds": field(BOX, "Where on the plane the grip may go, scene pixels; anywhere when absent."),
}, required=["point"])

obj("carry_plane", "CarryPlane",
    "With a height: a level carried objects rise to over an area (a vial goes up over a tall flask). Closed: an area"
    " objects may not enter (a torch stays outside a burner). Where planes overlap the highest wins.", {
        "from": field(XZ, "One corner of the area, [x, z] scene pixels."),
        "to": field(XZ, "The opposite corner."),
        "height": field(num(), "Height (scene pixels) of the object's grip over the area."),
        "blend": field(num(0, 64), "Pixels round the area over which objects climb to it (or stay away from a closed"
                                   " one).", 4),
        "closed": field(BOOL, "Objects may not enter the area.", False),
        "nodes": field({"type": "array", "items": NODE_ID}, "The grabbable nodes it applies to; all when empty."),
    }, required=["from", "to"])

obj("slot", "SlotBinding", "A place for an item of a station slot; clicked like a vanilla slot.", {
    "slot": field({"type": "string", "pattern": "^[a-z0-9_]+(\\[[0-9]+\\])?$"},
                  "The station slot: \"grid[4]\", or \"fuel\" for index 0."),
    "display": field(DISPLAY, "flat: lying on the surface; upright: standing, facing the camera side; floating:"
                              " hovering and turning.", "flat"),
    "size": field(num(1, 64), "Size of the slot (drop zone, highlight) in pixels; also of the item without"
                              " item_size.", 6),
    "item_size": field(num(0.5, 64), "Size of the shown item, when it differs from the slot's."),
    "counts": field({"type": "string", "enum": ["always", "hover", "never"]},
                    "When the stack's count is drawn; hover keeps slots close together readable.", "always"),
    "highlight": field(BOOL, "The square over the slot under the cursor; off for things that are no slots to the eye.", True),
}, required=["slot"])

obj("output", "OutputBinding", "Shows a result the station logic computes; clicking takes it.", {
    "output": field(STRING, "The station output's name."),
    "display": field(DISPLAY, "How the result is shown.", "floating"),
    "size": field(num(1, 64), "Size of the item and of the click zone, pixels.", 8),
}, required=["output"])

obj("control", "ControlBinding",
    "A control the player operates with the mouse. Knob/toggle turn round axis from range[0] to range[1] degrees;"
    " slider/button move along axis by that many pixels. Axis, range and pivot are in the node's own space.", {
        "control": field(STRING, "The station control (declared in the schema)."),
        "type": field({"type": "string", "enum": ["button", "toggle", "knob", "slider"]},
                      "button: click, springs back; toggle: click flips 0/1; knob: turned; slider: dragged."),
        "axis": field(VEC3, "Turn axis or travel direction. Defaults: button [0,-1,0], toggle [1,0,0], knob [0,1,0],"
                            " slider [1,0,0]."),
        "range": field({"type": "array", "items": num(), "minItems": 2, "maxItems": 2},
                       "Movement at the minimum and maximum value. Defaults: button [0,1], toggle [-25,25], knob"
                       " [135,-135], slider [0,8]."),
        "circular": field(BOOL, "A knob is grabbed anywhere and turned by circling the mouse round it; false turns it"
                                " by dragging up or right.", True),
        "pivot": field(VEC3, "The point a knob or toggle turns round, node pixels (a lever's hinge).", [0, 0, 0]),
        "base": field(VISUAL, "What stays still while the node moves (a lever's plate, a slider's track); drawn where"
                              " the node rests, not clickable."),
        "smoothing": field(num(0, 10), "Seconds the node takes to cover half the way to a new value; larger glides (a"
                                       " gauge needle on a locked control).", 0.05),
    }, required=["control", "type"])

obj("tank", "TankBinding", "Shows a station tank as liquid inside the node.", {
    "tank": field(STRING, "The station tank's name."),
    "inner": field(BOX, "The space the liquid fills, node pixels."),
    "round": field(BOOL, "The liquid is a cylinder standing in inner instead of a box.", False),
    "spout": field(VEC3, "Where liquid leaves when poured, node pixels; without one the node only receives."),
    "pour_range": field(num(0.5, 64), "How far (pixels) round another tank the cursor still counts as pointing at it.", 2.5),
    "pour_angle": field(num(0, 135), "How far (degrees) the node tips at most; it stops earlier once the liquid reaches"
                                     " the spout.", 100),
    "opening": field(VEC3, "Where poured liquid goes in, node pixels; the middle of inner's top when absent."),
    "pour_height": field(num(0, 32), "How high (pixels) over the other tank's opening the spout is held.", 3),
}, required=["tank", "inner"])

obj("emitter", "EmitterDefinition", "Gives off scene particles; code can switch it (NodeView.setEmitting).", {
    "particle": field(ID, "A particle style: diegesis:spark, bubble, droplet, splash, steam, smoke, glint, dust,"
                          " flame, or one registered with DiegesisClient.registerParticle."),
    "rate": field(num(0, 200), "Particles per second.", 4),
    "offset": field(VEC3, "Where they start, node pixels.", [0, 0, 0]),
    "velocity": field(VEC3, "Start velocity, pixels per second; turns with the node.", [0, 0, 0]),
    "spread": field(num(0, 64), "Random spread of the start position, pixels.", 0.5),
    "color": field(COLOR, "ARGB tint; the style's own colour when absent."),
    "enabled": field(BOOL, "Emits from the start.", True),
    "scale": field(num(0.05, 20), "Size factor over the style's size.", 1),
    "in_liquid": field(BOOL, "Start anywhere in the node's liquid and end at its surface (bubbles); offset and"
                             " spread are then unused.", False),
}, required=["particle"])

obj("light", "LightDefinition", "The node shines on the scene (also while carried).", {
    "level": field(num(0, 15, integer=True), "Brightness like a block's light.", 14),
    "radius": field(num(1, 256), "How far it reaches, pixels.", 16),
    "color": field(COLOR, "ARGB colour of the light pool on the surface; alpha is its strength, 0 draws none.",
                   "#80FFD9A0"),
    "flicker": field(num(0, 1), "How much it wavers: 0 a steady lamp, 1 a wild flame.", 0.12),
    "offset": field(VEC3, "Where the light comes from, node pixels (the middle of a long lamp).", [0, 0, 0]),
})

obj("inventory", "InventoryLayout",
    "The player's 36 slots laid out in the scene, vanilla order: hotbar the row nearest the player, slots 9-17 the"
    " farthest; column 0 on the player's left.", {
        "pos": field(VEC3, "Centre of the slot grid, scene pixels."),
        "rotation": field(VEC3, "The grid's turn in degrees (y, x, z); flat without one.", [0, 0, 0]),
        "spacing": field(num(0.5, 32), "Pixels between slot centres.", 2.6),
        "size": field(num(0.5, 32), "Size of the items, pixels.", 2.1),
        "surface": field(BLOCK_STATE, "A block drawn as a board under the slots; without one they float."),
        "slot_color": field(COLOR, "ARGB colour of each slot's square; alpha 0 hides them.", "#66000000"),
        "show": field({"type": "string", "enum": ["always", "toggle"]},
                      "toggle: hidden until Tab (Tab hides again), shown while an item is on the cursor.", "always"),
    }, required=["pos"])

obj("inventory_drawer", "InventoryDrawer",
    "The inventory in a drawer at a side of the screen: an icon sits there, pointing at the side slides the inventory out"
    " over the scene, and while it is out it comes first. Every field is optional.", {
        "side": field({"type": "string", "enum": ["left", "right", "top", "bottom"]}, "The side of the screen.", "right"),
        "align": field(num(0, 1), "Where along that side it sits: 0 at the start (left or top), 1 at the end.", 0.5),
        "icon": field(ID, "The item drawn on the icon.", "minecraft:bundle"),
        "icon_texture": field(ID, "A 16x16 texture drawn on the icon instead, e.g. mymod:textures/gui/bag.png."),
        "hover_zone": field(num(0, 64), "How wide (GUI pixels) the strip along the side is that opens it; 0: only the"
                                        " icon does.", 10),
        "open_delay": field(num(0, 2), "Seconds the cursor must stay there, so that passing by opens nothing.", 0.08),
        "close_delay": field(num(0, 5), "Seconds it waits after the cursor left.", 0.35),
        "slide_time": field(num(0, 2), "Seconds it takes to slide.", 0.18),
        "margin": field(num(0, 64), "Gap (GUI pixels) between the open drawer and the side of the screen.", 4),
        "style": field({"type": "string", "enum": ["panel", "minimal"]},
                       "panel: the vanilla inventory panel; minimal: dark see-through squares.", "panel"),
        "background": field(COLOR, "Background of the minimal style.", "#C0101216"),
        "slot_color": field(COLOR, "Colour of a slot in the minimal style.", "#66000000"),
        "always_open": field(BOOL, "It slides out when the station opens and stays out, without an icon; Escape leaves"
                                   " the station at once.", False),
        "compact": field(BOOL, "Only the stacks the station can use (StationView.setInventoryFilter), in rows of up to"
                               " nine, the drawer no bigger than they are.", False),
    })

obj("pouring", "PourOptions", "How pouring looks; every field is optional.", {
    "hints": field(BOOL, "While a vessel with liquid is in the hand, tanks that would take it are marked at their"
                         " openings.", True),
    "hint_color": field(COLOR, "Colour of those marks.", "#40FFFFFF"),
    "meter": field(BOOL, "A small bar next to the cursor shows how full the tank being poured into is.", True),
    "stream": field(BOOL, "The liquid runs in a continuous stream; false leaves only drops.", True),
    "stream_width": field(num(0.05, 8), "How wide (pixels) the stream leaves the spout at full flow.", 1.4),
})

obj("interface", "InterfaceOptions", "How the interface behaves and looks; every field is optional.", {
    "hide_hud": field(BOOL, "Hide the HUD (hotbar, health, crosshair).", True),
    "hide_chat": field(BOOL, "Hide the chat.", True),
    "hide_player": field(BOOL, "Do not draw the local player's own body (others still see it).", False),
    "inventory_panel": field(BOOL, "Without an inventory layout, show the vanilla inventory panel (stations with"
                                   " items); false shows no inventory.", True),
    "show_counts": field(BOOL, "Write stack counts next to items in the scene.", True),
    "counts_on_top": field(BOOL, "Draw counts over everything.", True),
    "highlight_color": field(COLOR, "ARGB colour over the slot under the cursor.", "#4DFFFFFF"),
    "shadows": field(BOOL, "Carried things cast a shadow of their shape.", True),
    "shadow_opacity": field(num(0, 1), "How dark those shadows are.", 0.5),
    "carry_lift": field(num(0, 32), "How high (pixels) the held stack floats over the work surface.", 2.5),
    "carry_stick": field(BOOL, "The held stack settles onto the slot under the cursor.", False),
    "carry_sway": field(num(0, 4), "How much the held stack leans as it moves.", 1),
    "carry_margin": field(num(0, 256), "How far (pixels) beyond the block's sides carried things may go; no limit"
                                       " when absent."),
    "sounds": field(BOOL, "The library's quiet sounds for picking up, putting down and controls.", True),
    "hover_outline": field(BOOL, "Outline the usable object under the cursor.", True),
    "outline_color": field(COLOR, "Colour of that outline.", "#FFE6E6E6"),
})

TYPES = {
    "vec3": {"type": "array", "items": {"type": "number"}, "minItems": 3, "maxItems": 3,
             "description": "[x, y, z] in Blockbench pixels (16 = one block)."},
    "xz": {"type": "array", "items": {"type": "number"}, "minItems": 2, "maxItems": 2,
           "description": "[x, z] in scene pixels."},
    "color": {"oneOf": [{"type": "string", "pattern": "^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$"}, {"type": "integer"}],
              "description": "\"#RRGGBB\", \"#AARRGGBB\" or an ARGB integer."},
    "resource_location": {"type": "string", "pattern": "^([a-z0-9_.-]+:)?[a-z0-9_./-]+$",
                          "description": "A namespaced id, e.g. \"mymod:diegesis/flask\"."},
    "node_id": {"type": "string", "pattern": "^[a-z0-9_]+$", "description": "Lower case letters, digits and _."},
    "block_state": {"oneOf": [{"type": "string"}, {"type": "object"}],
                    "description": "\"minecraft:lever[face=floor]\", or vanilla's {\"Name\": ..., \"Properties\": ...}."},
}

ROLES = ["grabbable", "slot", "output", "control"]
ONE_ROLE = {"not": {"anyOf": [{"required": [a, b]} for i, a in enumerate(ROLES) for b in ROLES[i + 1:]]}}


def build_object(name, loose):
    java, description, fields, required, extra = OBJECTS[name]
    props = {}
    for key, schema in fields.items():
        props[key] = {"anyOf": [schema, {"type": "null"}], "description": schema.get("description", "")} if loose else schema
    out = {"type": "object", "description": description, "properties": props,
           "patternProperties": {"^\\$": {}}, "additionalProperties": False}
    if not loose:
        if required:
            out["required"] = required
        out.update(extra)
        if name == "node":
            out.update(ONE_ROLE)
    return out


def schema_for(root, loose_root):
    """The scene schema (root = scene) or the template schema (root = a node without required fields)."""
    defs = dict(TYPES)
    for name in OBJECTS:
        defs[name] = build_object(name, False)
    # A node with a template: every field may be partial (it is merged first) or null (removes the template's).
    loose = {}
    for name in OBJECTS:
        if name in ("scene",):
            continue
        loose[name] = build_object(name, True)
    for name, schema in loose.items():
        defs[name + "_partial"] = rewrite_refs(schema)
    node = defs["node"]
    defs["node"] = {"if": {"type": "object", "required": ["template"]},
                    "then": {"allOf": [{"$ref": "#/definitions/node_partial"}], "required": ["id"]},
                    "else": node}
    defs["node_strict"] = node
    # draft-07 ignores keywords next to "$ref", so references that carry siblings go through allOf.
    top = {"allOf": [{"$ref": "#/definitions/" + ("node_partial" if loose_root else root)}]}
    out = {"$schema": "http://json-schema.org/draft-07/schema#",
           "$id": BASE_URL + ("node.schema.json" if loose_root else "scene.schema.json"),
           "title": "Diegesis node template" if loose_root else "Diegesis scene",
           "description": ("A node template, assets/<namespace>/diegesis/nodes/<path>.json: node fields without an id;"
                           " a scene node naming it with \"template\" starts from them." if loose_root
                           else OBJECTS["scene"][1])}
    out.update(top)
    out["definitions"] = defs
    return out


def rewrite_refs(schema):
    """In partial objects, nested objects are partial too (a template's control may lack its type)."""
    text = json.dumps(schema)
    for name in OBJECTS:
        if name not in ("scene", "node"):
            text = text.replace('"#/definitions/%s"' % name, '"#/definitions/%s_partial"' % name)
    return json.loads(text)


def write():
    OUT.mkdir(parents=True, exist_ok=True)
    for file, root, loose in (("scene.schema.json", "scene", False), ("node.schema.json", "node", True)):
        (OUT / file).write_text(json.dumps(schema_for(root, loose), indent=2) + "\n", encoding="utf-8", newline="\n")
        print("wrote", (OUT / file).relative_to(ROOT))


def codec_fields(java):
    path = ROOT / "common/src/main/java/com/pockyl/diegesis/api/scene" / (java + ".java")
    text = path.read_text(encoding="utf-8")
    names = set(re.findall(r'StrictCodecs\.optional\([^;]*?"([a-z_]+)"', text))
    names |= set(re.findall(r'fieldOf\("([a-z_]+)"\)', text))
    return names


def check():
    import jsonschema
    problems = []
    transform = codec_fields("Transform")
    for name, (java, _, fields, _, _) in OBJECTS.items():
        expected = codec_fields(java)
        if name == "node":
            expected |= transform | {"template", "repeat"}
        if expected != set(fields):
            problems.append("%s (%s.java): code has %s, schema has %s" % (
                name, java, sorted(expected - set(fields)), sorted(set(fields) - expected)))
    scene_schema = schema_for("scene", False)
    node_schema = schema_for("node", True)
    templates = list(ROOT.glob("common/src/main/resources/assets/*/diegesis/nodes/**/*.json"))
    for path, schema in [(p, node_schema) for p in templates]:
        errors = list(jsonschema.Draft7Validator(schema).iter_errors(json.loads(path.read_text(encoding="utf-8"))))
        for error in errors:
            problems.append("%s: %s at %s" % (path.relative_to(ROOT), error.message, list(error.absolute_path)))
    # The schema must accept a scene that uses the common parts, and reject what the codecs reject.
    good = {"interface": {"hide_hud": True}, "nodes": [
        {"id": "lever", "template": "diegesis:control/lever", "pos": [8, 12, 8]},
        {"id": "gem", "pos": [4, 10, 4], "visual": {"item": "minecraft:diamond"}}]}
    for error in jsonschema.Draft7Validator(scene_schema).iter_errors(good):
        problems.append("schema rejects a good scene: %s at %s" % (error.message, list(error.absolute_path)))
    bad = [{"nodes": [{"id": "a", "pos": [1, 2]}]}, {"interface": {"carry_lfit": 3}},
           {"nodes": [{"id": "a", "grabbable": {}, "slot": {"slot": "x"}}]},
           {"nodes": [{"id": "a", "visual": {"model": "x:y", "item": "minecraft:stick"}}]},
           {"nodes": [{"id": "Bad"}]}, {"nodes": [{"id": "k", "control": {"control": "gas"}}]},
           {"nodes": [{"template": "diegesis:control/knob"}]}]
    for sample in bad:
        if jsonschema.Draft7Validator(scene_schema).is_valid(sample):
            problems.append("schema accepts a bad scene: %s" % json.dumps(sample))
    good = {"$schema": BASE_URL + "scene.schema.json",
            "nodes": [{"id": "k", "template": "diegesis:control/knob", "control": {"control": "gas", "base": None}}]}
    if not jsonschema.Draft7Validator(scene_schema).is_valid(good):
        problems.append("schema rejects a templated node")
    print("checked %d templates" % len(templates))
    for problem in problems:
        print("PROBLEM:", problem)
    return not problems


if __name__ == "__main__":
    write()
    if "--check" in sys.argv and not check():
        sys.exit(1)
