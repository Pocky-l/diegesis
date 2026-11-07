<h1 align="center">Diegesis</h1>

<p align="center">
  Library for in-world block interfaces: the camera moves to the block and players work with its 3D parts directly,
  without a GUI.
</p>

<p align="center">
  <img alt="Minecraft 1.20.1" src="https://img.shields.io/badge/Minecraft-1.20.1-62B47A">
  <a href="https://files.minecraftforge.net"><img alt="Forge" src="https://img.shields.io/badge/Loader-Forge-1E2D3D"></a>
  <a href="https://fabricmc.net"><img alt="Fabric" src="https://img.shields.io/badge/Loader-Fabric-DBD0B4"></a>
  <img alt="License MIT" src="https://img.shields.io/badge/License-MIT-blue">
</p>

Diegesis adds nothing to the game by itself. It is a bridge for mods: a mod describes an interface as a **station**
(which blocks, what state, what rules - Java) and a **scene** (what is drawn where and what can be touched - a JSON
file), and the library does the rest: intercepts the block's use, flies the camera to the block, lets the player drag
items onto it, carry objects, flip levers and turn knobs, keeps everything in sync with the server and shows it to the
players around.

<p align="center"><img alt="The cooking pot of Hands-On Delight, built with Diegesis" src="docs/showcase/soup.gif"></p>

## Features

- **Camera focus** - using a station block flies the camera to a rig defined in the scene, with its own field of view;
  the player's body stays visible to others, who see them look at the block.
- **Scenes from Blockbench** - nodes show Blockbench block models (`models/diegesis/` loads by itself), items or block
  states; nodes nest, move, turn, scale, fade and glow.
- **Items on the block, clicked like a vanilla GUI** - the vanilla inventory panel sits under the block; left and right
  clicks pick up, put down, split and swap, shift-click moves between inventory and block, double click gathers,
  dragging a stack spreads it. Items lie, stand or float in the block's slots; results (crafting) float beside them.
- **The inventory in the scene** - a scene may lay the player's 36 slots out in the world instead of the screen panel:
  floating by the block as see-through black slots, or on any surface; stack counts are written next to every item.
  It can stay hidden until Tab (it comes up row by row, and by itself while an item is on the cursor).
- **Everything is the mod's choice** - an `interface` block in the scene decides what the screen hides (HUD, chat, the
  player's own body), whether counts and shadows are drawn, how the held stack moves, the highlight colour, sounds.
- **Hover outline** - with an empty hand, the object under the cursor that the player can use gets a thin outline round
  its silhouette.
- **Debug view** - hit boxes of every node coloured by role, node ids, inventory slots, the work plane and the camera
  target: F3+B, `/diegesis debug` or `DiegesisClient.setDebugView(true)`.
- **The held stack is in the world** - over the block it follows the cursor, sways as it moves and casts a shadow of its
  own shape; it can also settle onto the slot under it (`carry_stick`).
- **Carried objects** - grabbable nodes are held by their grip point (a vial by its top), follow the cursor with weight
  and lean, cast a shadow of their shape, fly back home or stay where they are put. An object may move in a plane of its
  own (vials in an upright plane between their rack and the flask: never over the table). Carry planes raise them over parts
  of the scene (a vial climbs over a tall flask instead of sliding along the table into it) or close areas to them (a
  torch stays outside the burner it lights).
- **Liquids and pouring** - station tanks hold liquids and mixtures as real volumes that stay level, rock and ripple;
  glass stays clear. Point a vessel at a tank that takes its liquid and it tips until the liquid reaches the spout, the
  wheel sets the flow and a stream runs across (paced and checked by the server); nothing is pulled in from afar.
- **Inventory drawer** - an icon at any side of the screen; pointing there slides the inventory over the scene, and
  while it is out it comes first.
- **Particles, light, sounds** - scene particles (sparks, bubbles, drops, splashes, steam, smoke, glints, dust, flame or
  your own) from emitters, effects and code, also inside a liquid (bubbles rising to its surface); a boiling surface
  churns; nodes that shine brighten what is near and light the surface; a set of quiet
  station sounds a resource pack can replace.
- **Controls** - toggles, buttons, knobs (grabbed anywhere and turned by circling the mouse round them, or the wheel,
  with detents) and sliders drive named values; locked
  controls are set only by the station's logic (a burner that is lit, a progress gauge) and shown like any other.
- **Ready controls** - a lever, a rocker switch, a push button, a knob on a dial and a slider ship with the library:
  one line in a scene (`"template": "diegesis:control/knob"`) gives the model, its still base and the movement; scale
  and tint them like any node.
- **Node templates** - reuse a node of your own across scenes (`assets/<mod>/diegesis/nodes/<name>.json`); the scene
  only changes what differs.
- **Triggers** - a carried object (or only its touching part, such as a torch's flame) reports when it starts and stops
  touching another node's hit box: knocks, trigger zones (`"trigger": true` - the cursor passes through them), a torch
  that lights a burner.
- **Multiblocks** - a station may span several blocks (a long table, a machine): every part opens the one station at
  its main block, and the scene reaches over the neighbouring blocks.
- **Client-only or server** - client stations work on any server (visual stations, animations); server stations check
  every action (no duplication, slot rules, ranges), share one state between players and relay what each player holds.
- **Hot reload** - scene files reload with F3+T while the game runs; mistakes in them (also a misspelt field name)
  are reported with the reason.
- **Documented for people and assistants** - a guide, a JSON Schema for editor completion in scene files, the whole
  API on one page for AI coding assistants, and a ready skill for Claude Code.
- **Shader friendly** - scenes draw with the same render types as entities and item frames.

## Quick start

Common code (both sides):

```java
public static final StationType LAB_TABLE = Diegesis.station(new ResourceLocation("mymod", "lab_table"))
        .blocks(MyBlocks.LAB_TABLE.get())
        .schema(StationSchema.builder()
                .slots("rack", 3)
                .control("gas", ControlSpec.range(0, 1, 0))
                .build())
        .logic(LabTableLogic::new)      // optional server rules: outputs, buttons, custom actions
        .register();
```

Scene, `assets/mymod/diegesis/scenes/lab_table.json` (Blockbench pixels, block facing north):

```json
{
  "camera": {"position": [8, 32, -6], "target": [8, 16, 8], "fov": 55},
  "nodes": [
    {"id": "flask", "pos": [8, 16, 9], "visual": {"model": "mymod:diegesis/flask", "translucent": true},
     "grabbable": {"lift": 3}},
    {"id": "rack_0", "pos": [3, 16, 12], "slot": {"slot": "rack[0]", "display": "upright"}},
    {"id": "gas_knob", "template": "diegesis:control/knob", "pos": [13, 16, 3], "scale": 0.6,
     "control": {"control": "gas"}}
  ]
}
```

Ready controls: `diegesis:control/lever`, `switch`, `button`, `knob` and `slider`. A node template is a node file
without an `id` in `assets/<namespace>/diegesis/nodes/`; the scene's own fields are laid over it (objects merge field
by field, `null` removes one). Your own control looks the same way: a moving `visual`, and `"base"` inside `control`
for the part that stays still, with `"pivot"` for the point a lever or knob turns round.

Interface options (all optional, defaults shown):

```json
"interface": {
  "hide_hud": true, "hide_chat": true, "hide_player": false,
  "inventory_panel": true, "show_counts": true, "counts_on_top": true, "highlight_color": "#4DFFFFFF",
  "shadows": true, "shadow_opacity": 0.5,
  "carry_lift": 2.5, "carry_stick": false, "carry_sway": 1.0, "sounds": true
}
```

Add `"carry_margin": 6` to keep carried things within 6 pixels of the block's sides (no limit by default).

The inventory can live in the scene instead of the screen panel:
`"inventory": {"pos": [8, 15.2, -5.4], "rotation": [-18, 0, 0], "spacing": 2.2, "size": 1.75, "slot_color": "#66000000"}`
(add `"surface": "<block state>"` for a board under the slots).

Client code (optional): `DiegesisClient.registerBehavior(id, MyBehavior::new)` for animations, sounds and effects that
react to the state and to what the player does.

## Documentation

- [Getting started](docs/getting-started.md) - a first station and scene.
- [Scenes](docs/scenes.md), [Stations](docs/stations.md), [Client](docs/client.md), [Cookbook](docs/cookbook.md).
- [JSON Schema](docs/schema/scene.schema.json) of scene files: add
  `"$schema": "https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/schema/scene.schema.json"` to a scene.
- [`docs/llms.txt`](docs/llms.txt) - the API on one page for AI coding assistants;
  [Claude Code skill and project notes](integrations/claude-code/README.md).

## Installation

1. Install [Forge](https://files.minecraftforge.net) or [Fabric](https://fabricmc.net) (with
   [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)) for Minecraft 1.20.1.
2. Put the jar into the `mods` folder of the client, and of the server for server stations.

## Building

```sh
./gradlew build                  # forge/build/libs and fabric/build/libs
./gradlew :forge:runGameTestServer
```

## Credits

- Author: **Pocky**.

## License

[MIT](LICENSE)
