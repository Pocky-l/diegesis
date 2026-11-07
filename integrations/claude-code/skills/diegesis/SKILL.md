---
name: diegesis
description: Build in-world block interfaces with the Diegesis library (Minecraft 1.20.1, Forge/Fabric) - a station in Java (blocks, schema of slots/tanks/controls/outputs, server logic), a scene JSON (camera, nodes, ready controls, grabbables, pouring, particles, light), an optional client behaviour. Use when the user wants a block that opens a Diegesis interface, edits a file under assets/*/diegesis/scenes or diegesis/nodes, or mentions Diegesis, stations or scenes.
---

# Diegesis interfaces

Diegesis replaces a block's GUI with an interface in the world: the camera flies to the block and the player works
with its 3D parts. A Diegesis interface has up to three parts:

1. **Station** (Java, common code, registered on both sides) - which blocks, what state, what rules.
2. **Scene** (`assets/<ns>/diegesis/scenes/<path>.json`, same id as the station) - camera, nodes, what can be touched.
3. **Behaviour** (Java, client setup only, optional) - animations, sounds, effects reacting to the state.

The full API on one page: https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/llms.txt - fetch it before
writing code if it is not in context yet. Field-level reference: `docs/scenes.md`, `docs/stations.md`,
`docs/client.md` in the same repo. JSON Schema: `docs/schema/scene.schema.json`.

## Procedure

1. **Decide the authority.** Items move between the player and the block, or other players must see it? `SERVER`
   (default). Pure visuals on any server, no items? `clientOnly()`.
2. **Write the schema first.** Name every slot group, tank, control and output ([a-z0-9_]+). Use `ControlSpec.toggle`
   for levers/switches, `stepped` for dials with detents, `range` for smooth knobs/sliders, `button()` for buttons,
   `.locked()` for values only the logic sets (progress, "lit"). Items or tanks that must stay in the block between uses
   need `.kept()` and a block entity implementing `StationHost`.
3. **Sketch the layout on the block top** in Blockbench pixels before writing JSON: block facing north, front = -Z,
   the player looks towards +Z, so **+X is on the screen's left**. Top surface y = 16. Keep 1-2 px between hit areas.
4. **Write the scene.** Start with `"$schema"` and the camera (`position` in front and above, e.g. `[8, 36, -10]`,
   `target` on the table `[8, 16, 6]`, `fov` 55). One role per node (grabbable | slot | output | control); put
   extra parts on children. Use ready controls: `"template": "diegesis:control/<lever|switch|button|knob|slider>"`,
   `"scale"` about 0.5, plus `"control": {"control": "<name>"}`. Models of your own go in
   `assets/<ns>/models/diegesis/` (loaded automatically) and are referenced as `"model": "<ns>:diegesis/<name>"`.
5. **Write the logic** only for what the schema cannot say: recipes (`updateOutputs` + `takeOutput`), reactions to
   buttons, controls and pours, custom actions from the client. Never trust `onCustomAction` data.
6. **Write the behaviour** for looks: show/hide nodes, `rotateTo` needles, tint lamps, emitter rates, sounds. Drive
   looks from `onStateChanged`/`frame` reading `view.state()` so every player near the block sees the same thing.
7. **Register**: `Diegesis.register(type)` in common setup; `DiegesisClient.registerBehavior(type.id(), ...)`,
   `registerLiquid`, `registerParticle`, `registerEffect` in client setup only.
8. **Check** the scene against the schema if a validator is available, run `python tools/scenes/depth_check.py` from the
   library's repository (`--assets` your mod's assets; the block's models in `tools/scenes/blocks.json`) and fix every
   error and warning, build, then ask the user to test in game:
   F3+T reloads scenes, errors appear in the log as `Scene <id> (<file>) is broken: <reason>`; F3+B or
   `/diegesis debug` shows hit boxes and ids.

## Rules that prevent most bugs

- Every key in a scene file is checked: an unknown or misspelt field breaks the whole scene. Use only fields from the
  reference; `"$comment"` is allowed for notes.
- `visual` has exactly one of `model`, `mesh` (+ `texture`), `item`, `block`. The anchor default `[8, 0, 8]` means `pos` is where the object
  stands.
- A full-size block or item model is 16 px: scale props down (0.2-0.6). Ready controls are 16 px models too.
- Vanilla blocks drawn from planes (torch, flowers) need an explicit small `collider`.
- Nothing is solid: carried objects pass through others. Use `carry_planes` (`height` to rise over a tall object,
  `closed` to keep out of an area) or a grabbable's own `plane`.
- A busy scene: `"inventory_drawer": {"side": "right"}` keeps the inventory off the scene until the player points there.
- Something shaped by hand: a role-less node with `"strokes": true`, `SceneEvent.Stroke` every frame, `NodeView.setShape`, and
  `DiegesisClient.registerItemShape` for the finished item.
- Free arranging (a shelf, a display table): `.slots("items", 64, SlotRules.DEFAULT.maxStack(1).placed().kept())` and a scene
  `"placing"` with surfaces; no node per item. A shelf seen from the front needs `aim_height` on its surfaces.
- More than one frame holds: `camera.glances` moves the look to another shot when the cursor rests at a screen edge (the
  inventory below the table on `"edge": "bottom"`) and back when a stack is taken.
- Glass vessels: `"glass": 0.8, "double_sided": true` on the visual keeps what is inside readable.
- Flicker: solid faces of a scene stay 0.05 px apart after the node's scale, also across nodes and the block's model. A
  model that is clean alone can fight in a scene. See-through faces may lie on solid ones, not on each other.
- Pouring: the source node needs `tank` + `spout` + `grabbable`; the target needs a `tank` whose spec accepts the
  liquid, with an `opening` at its mouth. Give liquids looks on the client (`registerLiquid`).
- Trigger zones: a node with `collider` and `"trigger": true`; react to `SceneEvent.Contact` in the behaviour and send
  an action to the server when it must change the state.
- Models from Blender or the internet: OBJ at `assets/<ns>/diegesis/meshes/<path>.obj` (units = pixels, base at y 0), `"visual": {"mesh": ..., "texture": "<ns>:block/..."}`.
- Sides at a table: `.seat(Seat.sitting(id, x, y, z))` in the station and `camera.seats` in the scene; the logic asks `seatOf(player)`.
- Values that are no slot, tank or control: `state.setData(tag)`, read in `onStateChanged` and `onSceneReady` (behaviours also run for passers-by).
- Many equal nodes: `"repeat": {"count": [8, 1, 8], "step": [3, 0, 3]}`, ids `<id>_0`, `<id>_1`...
- Client-only stations cannot have slots or outputs. A station on a block with a GUI replaces that GUI.
- Never call `DiegesisClient` from common code (dedicated servers have no client classes).
