# Scenes

A scene is everything a station shows and lets the player touch: a camera rig and a tree of nodes. It lives in
`assets/<namespace>/diegesis/scenes/<path>.json`; a station uses the scene with its own id unless
`StationType.Builder.scene(id)` names another. Scenes are resources: **F3+T** reloads them while the game runs, and a
resource pack can restyle them. A scene can also be built in code (`SceneDefinition.builder()`,
`DiegesisClient.registerScene`); a JSON file with the same id still wins.

Machine-readable reference: [`schema/scene.schema.json`](schema/scene.schema.json) (JSON Schema draft-07). Put
`"$schema": "https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/schema/scene.schema.json"` at the top of a
scene for completion and checks in VS Code, IntelliJ and other editors.

## Rules of the file

- **Units**: positions and sizes are Blockbench pixels; 16 = one block. Angles are degrees.
- **Space**: relative to the block's lower north-west corner, for a block **facing north** - its front is the -Z side,
  as Blockbench shows a block model. `x` 0..16 runs from the block's west to east side, `y` up, `z` from front to back.
  Seen by a player standing in front, +X is on the screen's **left**. The library turns the scene with the block's
  facing (see `orientation` in [Stations](stations.md)). Coordinates past 0..16 reach into neighbouring blocks
  (multiblocks, a camera in front of the block).
- **Strict**: a mistake makes the whole scene fail to load with a message in the log - a wrong value, a missing
  required field, or an unknown field (`Unknown field "lfit" (did you mean "lift"?)`). Keys starting with `$`
  (`"$schema"`, `"$comment"`) are allowed anywhere and ignored.
- **Colours** are `"#RRGGBB"`, `"#AARRGGBB"` or an ARGB integer.
- **Ids** of nodes are lower case letters, digits and `_`, unique in the scene.

## Scene

| Field | Default | Meaning |
|---|---|---|
| `camera` | see below | Where the camera goes while the player works here. |
| `work_plane` | `16` | Height of the surface carried objects and the held stack move over (the table top). |
| `idle_visible` | `true` | Draw the nodes while nobody works at the block. Needs a block entity at the block. |
| `nodes` | `[]` | The top-level nodes. |
| `inventory` | none | Lay the player's inventory out in the scene instead of the screen panel. |
| `inventory_drawer` | none | Keep the inventory in a drawer at a side of the screen. Not together with `inventory`. |
| `clear_view` | see below | What stands between the camera and the station dissolves. |
| `hud` | see below | How the game's HUD slides away when the player starts to work here. |
| `put_away` | `true` | A right click with a stack on the cursor that nothing in the scene takes (no slot, zone or station code) puts the stack back into the inventory, to the slot it came from while that is free. Code: `StationView.putAwayCarried()` does the same, for a station that uses right clicks itself. |
| `interface` | defaults | How the interface behaves and looks. |
| `carry_planes` | `[]` | Levels carried objects rise to over parts of the scene, or areas closed to them. |
| `pouring` | defaults | How pouring looks. |
| `placing` | none | Items put down anywhere on surfaces, see [placing](#placing). |
| `feedback` | defaults | The library's sounds at this station and effects played on events, see [feedback](#feedback). |

### camera

| Field | Default | Meaning |
|---|---|---|
| `position` | `[8, 36, -12]` | The camera's position: by default in front of the block, above it. |
| `target` | `[8, 16, 7]` | The point it looks at. |
| `fov` | `0` | Vertical field of view while focused, 0..170; 0 keeps the player's setting. |
| `transition` | `0.4` | Seconds the camera takes to fly in and out. |
| `inventory` | none | A second shot, `{"position", "target", "fov"}`, the camera glides to while the scene's inventory is shown (Tab with `"show": "toggle"`, or an item on the cursor) and back when it hides. Frame the inventory together with the slots it fills, so the laid-out inventory never has to cover the controls. |
| `glances` | `[]` | Other places the player's look can go to, see below. |
| `neighbors` | on | Going over to a station standing next to this one, see below. |
| `interactive_at` | `1` | The share of the flight in, 0..1, from which clicks already count: `1` waits until the camera has arrived. A station used all the time can take clicks during the last part of the flight (`0.6`). |
| `keep_width` | `1.7778` | The narrowest window shape (width / height) that sees the whole width of the shot, 16:9 by default. A narrower window (4:3, a game snapped to half a screen) gets a wider vertical field of view instead of losing the sides. Only with the rig's own `fov`; `0` keeps the vertical field of view in any window. |

Frame the shot with the debug view on (F3+B): it marks the camera target. A camera much closer than ~16 pixels to the
objects makes carried things look huge.

### camera.glances

A glance moves the look without leaving the station: the rig shows the table top, the cursor rests at the bottom of the
screen, the camera glides down to the inventory, and taking a stack there glides it back.

```json
"camera": {
  "position": [8, 37, -4], "target": [8, 16, 8], "fov": 55,
  "glances": [
    {"id": "inventory", "edge": "bottom", "position": [8, 31, -12.2], "target": [8, 15, -9.4]}
  ]
}
```

| Field | Default | Meaning |
|---|---|---|
| `id` | required | Unique in the scene, `[a-z0-9_]+`. Code and `SceneEvent.Glance` use it. |
| `edge` | none | `left`, `right`, `top` or `bottom`: where the cursor rests to look here. Without one only code does. One glance per edge, and not the edge of the `inventory_drawer`. |
| `zone` | `0.1` | How far from that edge the cursor counts as being at it, as a part of the screen. |
| `delay` | `0.12` | Seconds the cursor must rest there, so that passing by moves nothing. |
| `position`, `target` | required | The camera's place and the point it looks at. |
| `fov` | `0` | Field of view; 0 keeps the rig's. |
| `time` | `0.45` | Seconds the camera takes to get there and back. |
| `back_on_take` | `true` | Look back as soon as a stack comes onto the cursor here. |
| `back_zone` | `0.12` | The strip along the opposite edge that looks back; 0: no strip, only a taken stack or code ends the glance. |
| `hint` | `true` | Draw a small arrow at the edge the look can go to, or come back from. It grows stronger as the cursor comes near. |
| `hint_color` | `#D0FFFFFF` | The arrow's colour; its alpha is the strongest it gets. |

The cursor stays free the whole time: once the camera is there the player moves it over what the glance shows, and only
the opposite edge or a taken stack brings the look back. A stack put down keeps the camera there, so several slots can
be filled in a row. With a stack on the cursor the edge works the same way: that is how it gets back into the inventory.

A cursor still at an edge after coming back has to leave it first. A carried object, a control being turned, a spreading
drag and an open drawer never move the camera. With the screen's inventory panel at the bottom use another edge: the
panel covers that strip.

Client code: `StationView.glance(id)`, `glanceBack()`, `currentGlance()`, `SceneEvent.Glance`. In code:
`rig.withGlance(CameraGlance.at("inventory", Side.BOTTOM, position, target))`.

### gains

What the player gets at the station is seen going into the inventory: a stack that comes into it while they work there,
other than from their own cursor (a soup scooped with a bowl, pieces cut off, a dish taken), flies from the middle of the
station to its slot, which bounces as it lands. When the drawer does not show that slot (a `compact` drawer shows only
what the station uses), the stack stands next to the drawer for a moment. How many came shows as "+N": rising from
the slot as it lands, or beside the stack by the drawer. On by default.

| Field | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Gains are shown. |
| `time` | `0.45` | Seconds a stack flies. |
| `hold` | `1.6` | Seconds a stack whose slot is not on show stands next to the drawer. |
| `sound` | `true` | A soft pop as it lands. |
| `count` | `true` | "+N" shows how many came. |

In code: `SceneDefinition.builder().gains(GainOptions.OFF)`.

### camera.neighbors

Stations standing side by side work as one place: the cursor resting at the edge of the screen on the side another
station stands flies the camera over to it, and the player works there without leaving. The edge is the side the
station is on as the rig sees it: left, right, top for one further away, bottom for one behind. One straight above is on
the top edge and one straight below on the bottom edge (a fridge on its freezer). Both scenes must allow it; it is on by
default.

```json
"camera": {
  "position": [10, 32, -12], "target": [10, 1, 5.5],
  "neighbors": {"range": 2, "delay": 0.4}
}
```

| Field | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Neighbours are reachable from this station, and this station from theirs. |
| `range` | `3` | How far another station may stand, blocks along the floor; one up or down counts too. The nearest one on each edge wins. |
| `zone` | `0.03` | How far from the edge the cursor counts as being at it, as a part of the screen. |
| `delay` | `0.3` | Seconds the cursor must rest there, so that passing by moves nothing. |
| `time` | `0.45` | Seconds the camera takes to fly over. |
| `hint` | `true` | Draw a small arrow at an edge that leads to a neighbour; it shows only as the cursor comes near. |
| `hint_color` | `#D0FFFFFF` | The arrow's colour; its alpha is the strongest it gets. |
| `keys` | `true` | The movement and arrow keys go over at once to the neighbour on their side: A, D and left, right; W and up to the top edge, S and down to the bottom one. Where the cursor is does not matter. |

A station counts when the player could open it from where they stand: its open condition holds and it is within its
reach. An edge used by a glance stays the glance's. On the edge of the `inventory_drawer` the cursor stays over the inventory:
only the keys go over to a neighbour there, and no arrow is drawn. Nothing moves while a stack is on the cursor,
an object is in the hand or a glance is away, by the cursor or by a key. A cursor still at the edge on arrival has to leave it first, so the camera
never flies on by itself. The server ends the use of the station left as it opens the next one; the cursor stack goes
back to the inventory as on leaving. In code: `rig.withNeighbors(CameraNeighbors.NONE)`.

## Nodes

A node has a place, optionally a look, at most **one role** (grabbable, slot, output, control) and any number of
extras (tank, emitter, light). Children move, turn and scale with their parent - put a second role on a child node.

| Field | Default | Meaning |
|---|---|---|
| `id` | required | Unique in the scene. |
| `template` | none | Start from a node template (below). |
| `pos` | `[0, 0, 0]` | Position relative to the parent, or to the block corner for top-level nodes. |
| `rotation` | `[0, 0, 0]` | Euler degrees, applied y (yaw), then x (pitch), then z (roll). |
| `scale` | `1` | One factor, or `[x, y, z]`. |
| `visual` | none | What is drawn. A node without one is an invisible pivot or hit zone. |
| `collider` | derived | What the cursor can hit: `{"from": [x,y,z], "to": [x,y,z]}` in the node's own pixels. Derived from the visual's real model bounds or from the role when absent. |
| `trigger` | `false` | The collider is only a zone carried objects touch (`SceneEvent.Contact`); the cursor passes through it. |
| `pickable` | `true` | `false`: pure decoration with no hit box at all - the cursor and carried objects pass through it (a gauge needle, a coloured band drawn on a grabbable parent). Not allowed together with a role or `trigger`. |
| `drop_zone` | none | The name of a slot group: a stack clicked or let go on the node goes into it, merging with what is there or into the first empty slot, as into the mouth of a pot. The cursor passes through while the hand is empty or nothing fits, so slots behind it stay reachable. Needs a `collider`, no role. |
| `takes_items` | `false` | While a stack is on the cursor the node is hovered and clicked as with an empty hand, and the click goes to the behaviour's `onItemClick`: places a station lays things on by code show under the stack where it would go. Without it a carried stack sees only slots and drop zones. |
| `tooltip` | none | Text next to the cursor while it is over the node: a translation key (shown as written when no language has it). Line breaks (`
`) start new lines. Code changes it with `NodeView.setTooltip(Component)` (a gauge reading, what a vessel holds). A node with a tooltip can be hovered even when it does nothing else. |
| `grabbable` | none | Role: an object the player carries. |
| `slot` | none | Role: shows a station slot's item. |
| `output` | none | Role: shows a station output; clicking takes it. |
| `control` | none | Role: a button, toggle, knob or slider. |
| `tank` | none | Shows a station tank as liquid (or a thick mass) in the node. |
| `emitter` | none | Gives off particles. |
| `light` | none | Shines on what is near. |
| `hidden` | `false` | Starts invisible; code shows it with `NodeView.setVisible(true)`. |
| `extra` | `{}` | Free-form JSON for your own code: `NodeView.extra()`. |
| `children` | `[]` | Nodes attached to this one. |
| `feedback` | none | The library's sounds for this node (`sounds`, as in [feedback](#feedback)) and its `marks`: what touching it means. |

Vanilla blocks drawn from planes (torches, flowers, rails) have a full 16-pixel model box: give them an explicit
`collider`, or they catch clicks meant for their neighbours.

What the cursor stops at: the nearest hit box along the cursor's ray among the nodes that can be used (a role, an
explicit `collider`, or a `tooltip`), and the laid-out inventory's slots while it is shown. A node with only a visual
and no explicit collider is never hit by the cursor (but carried objects still touch it), trigger zones never are,
and neither are nodes bound to a locked control unless they have a tooltip. Overlapping boxes: the one the ray enters
first wins, so a slot on a vessel's mouth must stick out above the vessel's own box. While an item is on the cursor only
slots are hit.

### visual

Exactly one of `model`, `item`, `block`.

| Field | Default | Meaning |
|---|---|---|
| `model` | - | A block model id: `mymod:diegesis/flask` = `assets/mymod/models/diegesis/flask.json`. Every model under `models/diegesis/` loads by itself; others need `DiegesisClient.registerModel(id)`. |
| `item` | - | An item id, drawn like an item frame shows it. |
| `block` | - | A block state, as in commands: `"minecraft:lever[face=floor,facing=north]"`. |
| `anchor` | `[8, 0, 8]` | The point of the model (its own pixels) that sits on the node's `pos`. The default, the middle of the model's bottom, makes `pos` the point the object stands on. |
| `tint` | `"#FFFFFFFF"` | Colour multiplied into the model. Alpha below FF makes it see-through. |
| `translucent` | `false` | The textures are see-through (glass): drawn after solid parts. Set by itself for a tint with alpha. |
| `emissive` | `false` | Drawn at full brightness. |
| `double_sided` | `false` | Faces are drawn from behind too, in the same pass: an open glass beaker shows its far walls through the near ones, and the liquid inside stays sorted with them. Block models only. |
| `glass` | `0` | How clear the faces are where they look straight at the camera, 0..1: they thicken towards a grazing angle, as glass does, so what stands behind or inside stays clear. `0.8` suits a vessel. Implies `translucent`. |
| `lit_as_block` | `false` | A block model or block state lit as the world lights the station's block: its faces shaded as a block's, the light of the blocks around and smooth lighting's shadows in its corners. Use it on the scene's copy of a block the station hides (`hidesBlock`), so that block and copy look the same as they swap; other models are lit as models. |

Models made in Blockbench ("Java Block/Item" format, exported as JSON) work as they are, with textures from your
mod's or vanilla's block atlas (`"texture": "mymod:block/flask_glass"`). A scale below 1 shrinks a 16-pixel model to
a prop on the table: `"scale": 0.4`.

### grabbable

The player picks the object up with the cursor and carries it. It follows the cursor with a little lag, leans into
its movement and casts a shadow of its shape.

| Field | Default | Meaning |
|---|---|---|
| `lift` | `4` | Height of the grip above the work surface while carried, 0..64. |
| `sway` | `1` | How much it leans while moving, 0..4. |
| `stay` | `false` | Stays where it is dropped instead of flying home. The new place is kept on this client only and is not saved. |
| `grip` | hit box middle | The point it is held by, in the node's pixels: a vial by its top. Tilts pivot round it. |
| `plane` | none | A plane of its own to move in instead of over the work surface: `{"point": [x,y,z], "normal": [0,0,-1], "bounds": {"from": .., "to": ..}}` (scene pixels; the default normal is an upright wall facing the front). Lift and carry planes then do not apply. |
| `contact` | whole hit box | The part that touches other nodes, a box in the node's pixels (a torch's flame). |
| `hold` | `"auto"` | How the hand keeps it. `auto`: a quick click leaves it in the hand until the next click, pressing and dragging carries it until the button is let go. `drag` or `click`: only that way. |

When it is let go, the behaviour gets `SceneEvent.Release(node, target)` with the node under it. While carried, its
`contact` box reports `SceneEvent.Contact` when it starts and stops touching another node's collider.

### carry_planes

Shape where carried objects go. Each entry:

| Field | Default | Meaning |
|---|---|---|
| `from`, `to` | required | Opposite corners of an area, `[x, z]` scene pixels. |
| `height` | none | The grip's height over the area: a vial rises over a tall flask instead of sliding into it. |
| `blend` | `4` | Pixels round the area over which objects climb to `height`, or stay away from a closed area. |
| `closed` | `false` | Objects may not enter the area (a torch stays outside the burner it lights). |
| `nodes` | all | The grabbable node ids it applies to. |

Nothing in a scene is solid: without carry planes, a carried object passes through other objects at its lift height.

The stack in the hand goes by the planes without `nodes` too: an upright station puts a plane with a `height` over its
block, so the stack stays at its front instead of following the floor behind it.

### slot

Shows the item of a station slot. It is clicked exactly like a vanilla container slot: left click picks up or puts
down the stack, right click splits or puts one, shift-click moves between the slot and the inventory, double click
gathers, dragging over several slots spreads.

| Field | Default | Meaning |
|---|---|---|
| `slot` | required | The station slot: `"grid[4]"`, or `"fuel"` for `fuel[0]`. |
| `display` | `"flat"` | `flat` lying on the surface, `upright` standing (facing the front), `hidden` not drawn (the scene shows what is in the slot its own way), `floating` hovering and turning. |
| `size` | `6` | Size of the slot (drop zone, highlight), 1..64; also of the item without `item_size`. |
| `item_size` | `size` | Size of the shown item when it differs. |
| `counts` | `"always"` | When the stack's count is drawn: `always`, `hover` (slots close together stay readable), `never`. |
| `highlight` | `true` | The square over the slot under the cursor. Off for things that are no slots to the eye, like food in a pot; the hover outline stays. An `upright` slot's square stands in the item's plane, behind it. |
| `idle_highlight` | `0` | How strongly the square shows while the cursor is elsewhere, 0..1 of the hovered one, so the places to put things are seen at a glance. |
| `ghost` | `[]` | What goes here, shown see-through and still in the empty slot to the player at the station, as vanilla's screens outline an empty armour slot: item ids or `#tags` (`["minecraft:coal", "#minecraft:logs"]`); several take turns. Ids of mods that are not installed are left out. A behaviour can set its own with `NodeView.setGhostItems`. |
| `ghost_alpha` | `0.4` | How opaque the ghost is, 0..1. |
| `ghost_period` | `1.5` | Seconds each of several ghosts is shown before the next. |
| `count_facing` | `"up"` | `up`: the count lies on the slot, read from above; `front`: it stands at the item's foot facing the block's front, for an item standing in a hollow the camera looks into (a furnace's mouth); `camera`: at the item's foot, always turned to the camera like a name tag. |

A slot node needs no visual: it is an invisible pad on the surface. Put it where the block's texture shows a cell.

### output

Shows a result the station logic computes (`state().setOutput`); clicking it calls `StationLogic.takeOutput`.

| Field | Default | Meaning |
|---|---|---|
| `output` | required | The output's name. |
| `display` | `"floating"` | As for slots. |
| `size` | `8` | Size of the item and of its click zone. |
| `count_facing` | `"up"` | Which way the count faces: `up`, `front` or `camera`, as for a slot. |

### control

The node moves with the value of a station control: a knob or toggle turns round `axis` from `range[0]` (the
control's minimum) to `range[1]` (its maximum) in degrees; a slider or button moves along `axis` by that many pixels.
Axis, range and pivot are in the node's own space, after its rotation and scale.

| Field | Default | Meaning |
|---|---|---|
| `control` | required | The station control, declared in the schema. |
| `type` | required | `button` (click; springs back), `toggle` (click flips 0/1), `knob` (turned), `slider` (dragged). |
| `axis` | by type | button `[0,-1,0]`, toggle `[1,0,0]`, knob `[0,1,0]`, slider `[1,0,0]`. |
| `range` | by type | button `[0,1]`, toggle `[-25,25]`, knob `[135,-135]`, slider `[0,8]`. |
| `circular` | `true` | A knob is grabbed anywhere and turned by circling the mouse round it. `false`: drag up or right. The mouse wheel also turns knobs. |
| `pivot` | `[0, 0, 0]` | The point a knob or toggle turns round, in the node's pixels: a lever's hinge. |
| `base` | none | A visual that stays still while the node moves (a lever's plate, a slider's track), drawn where the node rests. |
| `smoothing` | `0.05` | Seconds the node takes to cover half the way to a new value. `0.05` follows the hand; `0.2`-`0.5` glides, for a needle bound to a value the logic sets in steps. |

Stepped controls (`ControlSpec.stepped`) snap with a detent sound. Locked controls (`ControlSpec.locked()`) are shown
but only the logic changes them: a lit burner, a progress gauge. A node bound to a locked control is a display - no
outline, no clicks, the cursor passes through it - so a gauge needle is just a knob node on the locked value:

```json
{"id": "needle", "pos": [0, 7, -0.2], "visual": {"model": "mymod:diegesis/needle"},
 "control": {"control": "heat", "type": "knob", "axis": [0, 0, 1], "range": [-90, 90], "circular": false, "smoothing": 0.25}}
```

### Ready controls

| Template | Type | Notes |
|---|---|---|
| `diegesis:control/lever` | toggle | Hinge plate + lever, turns round its hinge. |
| `diegesis:control/switch` | toggle | Rocker switch in a bezel, +-12 degrees. |
| `diegesis:control/button` | button | Steel bezel with a red cap, travels 0.8 pixels. |
| `diegesis:control/knob` | knob | Red knob on a dial with tick marks; minimum on the screen's left. |
| `diegesis:control/slider` | slider | Grip on steel rails; the value grows to the screen's right, +-4 pixels. |

Each is a full 16-pixel model; scale it down to fit: `"scale": 0.5` is a typical table-top control. They need only an
`id`, a `pos` and the control: `{"id": "gas", "template": "diegesis:control/knob", "pos": [12, 16, 4], "scale": 0.55,
"control": {"control": "gas"}}`. `"tint"` inside `visual` recolours one.

### tank

Shows a station tank as liquid inside the node. It fills `inner` from the bottom as a real volume: tipping the vessel
moves the liquid but never changes how much there is. The surface rocks and carries ripples when the vessel is moved or
something falls in; a narrow tube chatters, a wide bowl swings slowly.

With a `spout`, a grabbable node pours. The player points it at another tank that takes the liquid - the cursor
anywhere on that tank or just above its opening. The vessel waits beside the tank, then tips until the liquid reaches
the spout, so a full vessel pours early and an emptying one goes on tipping. How far the liquid stands over the spout
is the flow; the **mouse wheel** asks for more or less. A stream runs into the other tank, raising ripples where it
lands. When the tank fills or the vessel runs dry, the vessel rights itself in place. Nothing is pulled in from afar: the
zone is the tank itself plus `pour_range`. The server paces pouring at the tanks' `pourRate`.

| Field | Default | Meaning |
|---|---|---|
| `tank` | required | The station tank. |
| `inner` | required | The box the liquid fills, node pixels. |
| `round` | `false` | A cylinder standing in `inner` instead of a box. |
| `spout` | none | Where liquid leaves, node pixels. Without one the node only receives. |
| `pour_range` | `2.5` | How far (pixels) round another tank the cursor still counts as pointing at it, 0.5..64. |
| `pour_angle` | `100` | How far the node tips at most, 0..135; it stops earlier once the liquid reaches the spout. Above 90 a bottle with a neck empties completely. |
| `opening` | top of `inner` | Where poured liquid goes in: the top of a flask's neck. |
| `pour_height` | `3` | How high over the other tank's opening the spout is held, 0..32: the stream is in plain view. |
| `mass` | none | Shows what the tank holds as a thick mass instead of a runny liquid: see [`tank.mass`](#tankmass). |
| `wall_gap` | `0` | How far (pixels, 0..8) the vessel's walls stand outside `inner`. A mass reaches across the gap and meets them with a little rise; the liquid stays inside `inner`, where its sides cannot show through the walls. |
| `inset` | `0.05` | How far (pixels, 0..2) the drawn liquid keeps off the sides of `inner`, and a mass off the walls: a liquid as wide as the vessel's inside shows through its walls where they meet. |

### tank.mass

A thick mass in the tank: a batter, a porridge, a dough, whipped butter, ice cream. Whatever moves through it
(`NodeView.leaveWake`, a whisk, a spoon) leaves its trail behind it, shaded on the flat surface as on a liquid's, fading as
`setWakeLook` says. With `relief` above 0 it pushes the mass aside instead into a real furrow with a ridge at each side and a
heap where the stroke stops, which flows back as firmly as it is made: a batter levels in a second or two, a porridge keeps
the furrows, ice cream keeps its peaks. A swirl (`setCurrent`) carries the relief and the texture round. It is lit by the
scene's light, rises a little and darkens where it meets the walls, and holds what floats in it (`setFloating`) partly sunk.
The level is the liquid's. Code shows a mass with `NodeView.setMass` and lets a liquid turn into it with `setThickness`
(lumps of the mass form in the liquid and grow together); a mass from the scene file is whole from the start.

```json
"tank": {"tank": "bowl", "inner": {"from": [3.3, 0.1, 3.3], "to": [12.7, 7.5, 12.7]}, "wall_gap": 0.3,
         "mass": {"texture": "mymod:block/porridge", "firmness": 0.45, "gloss": 0.3, "grain": 0.5}}
```

| Field | Default | Meaning |
|---|---|---|
| `texture` | required | A block-atlas sprite with the mass's own colours, drawn without light or shade (the relief makes those), repeated over the surface from the tank's middle. |
| `tint` | `#FFFFFF` | Multiplied into the texture. |
| `firmness` | `0.5` | How well it keeps its shape, 0..1: 0 a batter that levels in about a second, 0.5 a porridge that keeps a spoon's furrows, 1 ice cream or whipped butter that keep peaks and swirls. |
| `gloss` | `0.3` | How glossy, 0..1: 0 dough or mince, 1 butter or ice cream with soft highlights on the ridges turned to the light. |
| `grain` | `0.3` | How lumpy at a small scale, 0..1: 0 smooth, 1 oat flakes, crumbs, chopped leaves. |
| `tile` | `16` | How many pixels one repeat of the texture covers, 1..256: 16 is one texel a pixel, the density of the scene's models. |
| `cell` | `0` | How many pixels one cell of its surface covers, 0..16: the finest step its relief bends at. 0 is one texel of its texture (`tile / 16`), so the relief is as coarse as the pixels drawn on it. |
| `relief` | `0` | How far what moves through it shapes its surface, 0..1: 0 keeps it flat and shades the trail behind whatever moved through it, as on a liquid; 1 digs real furrows with a ridge at each side, which flow back as firmly as it is made. Grain and the lumps of a thickening liquid stand up only as far. |

### emitter

| Field | Default | Meaning |
|---|---|---|
| `particle` | required | A style: `diegesis:spark`, `bubble`, `bubble_pop`, `droplet`, `splash`, `steam`, `smoke`, `glint`, `dust`, `flame`, or your own (`DiegesisClient.registerParticle`). |
| `rate` | `4` | Particles per second, 0..200 (`NodeView.setEmitRate` changes it). |
| `offset` | `[0, 0, 0]` | Where they start, node pixels. |
| `velocity` | `[0, 0, 0]` | Start velocity, pixels per second; turns with the node. |
| `spread` | `0.5` | Random spread of the start, pixels. |
| `color` | style's | ARGB tint. |
| `enabled` | `true` | Emits from the start (`NodeView.setEmitting`). |
| `scale` | `1` | Size factor over the style's size. |
| `in_liquid` | `false` | Start anywhere in the node's liquid and die at its surface: bubbles. |

The built-in styles' sizes, in pixels at `scale` 1, so you can pick a `scale` for your props (a flame under a burner
drawn at scale 0.5 reads at `scale` 3-4):

| Style | Size (px) | Life (s) | Moves |
|---|---|---|---|
| `spark` | 0.5-0.8 | 0.25-0.6 | flies and falls, stretched |
| `bubble` | 0.55-0.95 | 0.6-1.4 | rises, wobbles, dims with its depth; on the surface it becomes `bubble_pop` |
| `bubble_pop` | 0.8-1.3, swells | 0.45-0.85 | sits on the surface, pops and rings the liquid |
| `droplet` | 0.3-0.45 | 0.5-0.9 | falls fast, stretched |
| `splash` | 0.4-0.7 | 0.25-0.45 | falls |
| `steam` | 0.9-1.5, grows | 1.1-1.9 | white, appears softly, rises slowly |
| `smoke` | 0.8-1.3, grows | 1.5-2.5 | rises slowly |
| `glint` | 0.4-0.7 | 0.3-0.7 | stays, glows |
| `dust` | 0.15-0.3 | 1.0-2.0 | drifts down |
| `flame` | 0.35-0.6 | 0.25-0.5 | rises, glows |

### light

| Field | Default | Meaning |
|---|---|---|
| `level` | `14` | Brightness like a block's light, 0..15 (`NodeView.setLight`). |
| `radius` | `16` | How far it reaches, pixels. |
| `color` | `"#80FFD9A0"` | Colour of the soft light pool on the work surface; alpha is its strength, 0 draws none. |
| `flicker` | `0.12` | 0 for a steady lamp, up to 1. |
| `offset` | `[0, 0, 0]` | Where the light comes from, in the node's pixels: the middle of a long lamp whose model is anchored at its corner. |

The light brightens nodes near it (also while it is carried) and lights the surface under it. It does not light the
world: it is a scene effect.

## inventory

Without `inventory`, a station with slots or outputs shows the vanilla inventory panel at the bottom of the screen
(`interface.inventory_panel`). With it, the player's 36 slots lie in the scene: 4 rows of 9 in vanilla order - the
hotbar is the row nearest the player, slots 9-17 the farthest, column 0 on the player's left. They work like the
panel: clicks, shift-clicks, drags, tooltips, counts.

| Field | Default | Meaning |
|---|---|---|
| `pos` | required | Centre of the grid, scene pixels. |
| `rotation` | `[0, 0, 0]` | Turn of the grid, degrees; flat by default, `[-18, 0, 0]` tilts it towards a camera in front. |
| `spacing` | `2.6` | Pixels between slot centres. |
| `size` | `2.1` | Size of the items. |
| `surface` | none | A block state drawn as a board under the slots; floating slots without one. |
| `slot_color` | `"#66000000"` | Colour of each slot's square; alpha 0 hides them. |
| `show` | `"always"` | `toggle`: hidden until Tab (Tab hides it again), shown by itself while an item is on the cursor. |

## inventory_drawer

Keeps the inventory off the scene until it is wanted. An icon sits at a side of the screen; pointing at that side
slides the inventory out **over** the scene, and it slides back when the cursor leaves. A click on the icon or Tab pins it
open (Escape or Tab lets it go). While it is out it comes first: whatever lies under it in the scene is neither hovered
nor clicked, and the wheel does nothing there.

A stack on the cursor opens it like the cursor alone does, so an item taken from the scene can be put away; an object
in the hand or a control being turned does not, so that dragging a vessel to the edge opens nothing. Stacks can be
dragged straight out of it and let go over a slot in the scene.

```json
"inventory_drawer": {"side": "right", "style": "minimal"}
```

| Field | Default | Meaning |
|---|---|---|
| `side` | `"right"` | `left`, `right`, `top`, `bottom`. |
| `align` | `0.5` | Where along that side it sits: 0 at the start (left or top), 1 at the end. |
| `icon` | `"minecraft:bundle"` | The item on the icon. |
| `icon_texture` | none | A 16x16 texture on the icon instead, such as `"mymod:textures/gui/bag.png"`. |
| `hover_zone` | `10` | How wide (GUI pixels) the strip along the side is that opens it; 0: only the icon does. |
| `open_delay` | `0.08` | Seconds the cursor must stay there: passing by opens nothing. |
| `close_delay` | `0.35` | Seconds it waits after the cursor left: overshooting closes nothing. |
| `slide_time` | `0.18` | Seconds it takes to slide. |
| `margin` | `4` | Gap (GUI pixels) between the open drawer and the side of the screen. |
| `style` | `"panel"` | `panel`: the vanilla inventory panel. `minimal`: dark see-through squares. |
| `background` | `"#C0101216"` | Background of the `minimal` style. |
| `slot_color` | `"#66000000"` | Colour of a slot in the `minimal` style. |
| `always_open` | `false` | It slides out when the station opens and stays out, without an icon; Escape leaves the station at once. For stations worked from the inventory all the time. |
| `compact` | `false` | Only the stacks the station can use, in rows of up to nine, the drawer no bigger than they are; the `minimal` look. Client code picks them with `StationView.setInventoryFilter`, without one every stack that is not empty shows. A stack on the cursor gets one empty slot to go into. It changes shape only while the cursor is away from it. |
| `carry_size` | none | Size (pixels) of a stack taken from the drawer while it is carried over the scene, as big as the station's own items look. Without it the stack keeps the size of the last one carried. |
| `layout` | `"rows"` | `rows`: the drawer as above. `hotbar`: the hotbar stays where the HUD draws it and takes clicks (the HUD's own is gone at once and back at once), the off hand beside it at a menu station; over it a square for each other stack the station can use (`StationView.setInventoryFilter`), in the order they had when the station opened, with one empty square always. `side`, `align`, `icon`, `style` and `compact` do not apply. |

Client code: `StationView.setInventoryOpen(boolean)`, `isInventoryOpen()`, `setInventoryFilter(Predicate<ItemStack>)`, `SceneEvent.InventoryDrawer`.

## pouring

How pouring looks; every field is optional.

| Field | Default | Meaning |
|---|---|---|
| `hints` | `true` | While a vessel with liquid is in the hand, tanks that would take it are marked at their openings. |
| `hint_color` | `"#40FFFFFF"` | Colour of those marks. |
| `meter` | `true` | A small bar next to the cursor shows how full the tank being poured into is. |
| `stream` | `true` | The liquid runs in a continuous stream; `false` leaves only drops. |
| `stream_width` | `1.4` | How wide (pixels) the stream leaves the spout at full flow. |

## interface

Every field is optional.

| Field | Default | Meaning |
|---|---|---|
| `hide_hud` | `true` | Hide the HUD (hotbar, health, crosshair, effects). |
| `hide_chat` | `true` | Hide the chat. |
| `hide_player` | `false` | Do not draw the local player's own body (others still see it). |
| `inventory_panel` | `true` | Without an `inventory` layout, show the vanilla panel; `false` shows no inventory. |
| `show_counts` | `true` | Write stack counts next to items in the scene. |
| `counts_on_top` | `true` | Counts are never hidden by a model. |
| `highlight_color` | `"#4DFFFFFF"` | Colour over the slot under the cursor. |
| `shadows` | `true` | Carried things cast a shadow of their shape on the work surface. |
| `shadow_opacity` | `0.5` | How dark, 0..1. |
| `carry_lift` | `2.5` | How high the held stack floats over the work surface. |
| `carry_stick` | `false` | The held stack settles onto the slot under the cursor. |
| `carry_sway` | `1` | How much the held stack leans, 0..4. |
| `carry_margin` | none | How far beyond the block's sides carried things may go; no limit when absent. |
| `sounds` | `true` | The library's quiet sounds for picking up, putting down and controls. |
| `hover_outline` | `true` | Outline the usable object under the cursor while the hand is empty. |
| `outline_color` | `"#FFE6E6E6"` | Colour of that outline. |
| `carry_count_facing` | `"camera"` | Which way the held stack's count faces: `camera` (turned to the camera, as a name tag), `up` (lying over the stack) or `front` (standing towards the front). |

## clear_view

While a player works at the station, entities and the world's particles between the camera and the station dissolve: softly, towards
the edge of a cone round the line of sight, and they come back as the camera leaves. A figure in the way stays as a faint ghost at
the edge of the shot and never covers the scene; a campfire's smoke under a pot stops at the cone. Blocks in the camera's way are
left out of the picture: the blocks the camera stands in, from any of the scene's shots, and those between it and the work surface,
such as a wall cabinet over the station or a low ceiling; a neighbour that only grazes the edge of the view stays. Their
neighbours show their faces, light stays as it was, and the blocks fade out as the camera flies in and fade back as it flies back. The shot never moves, so what the scene hangs before the camera stays where it was
framed. Nobody else sees a change, and the scene's own particles stay. On by default; every field is optional.

| Field | Default | Meaning |
|---|---|---|
| `entities` | `true` | Entities dissolve. |
| `particles` | `true` | World particles dissolve. |
| `radius` | `16` | How wide the clear cone is at the station, pixels. |
| `soft` | `12` | How far beyond `radius` things come back to full strength, pixels. |
| `blocks` | `true` | Blocks in the camera's way are not drawn: those within 0.3 blocks of the camera in any shot of the scene (the rig's, seats', the inventory's, glances'), and those that stand before the work surface: before the camera's target, or before at least a third of the surface. The work surface is the block's top at the camera target's height and 4 pixels over it, 2 pixels in from the sides: put the target on the surface the player works on. A neighbour whose corner only grazes the view past the station's side stays. Blocks count by their visual shape, as for the vanilla third-person camera (glass never does); the station's own block always stays. Works with Embeddium and Sodium. |
| `surface` | `"block"` | The work surface `blocks` keeps in view: `block`, the block's middle at the camera target's height as above; `placing`, the scene's `placing` surfaces where they are written, each on its own (a block counts when it stands before a third of one of them, checked across its width and depth at its height and 4 pixels over it), or the block's middle when the scene has none. For a station worked on outside its block, such as a drawer pulled out before it: counted over the block, the block on top of it is seen past the drawer's own front and goes from the picture though it hides nothing. |
| `fade` | `1` | How much of the camera's flight time, 0 to 1, the blocks of `blocks` take to fade out: over the first share of the flight in, and back in over the last share of the flight back; `1` is the whole flight. Going over to a neighbour, the new station's blocks fade out while the old one's come back. What a block entity draws there (a chest, the food on a board) fades with its block; text on it stays solid. `0` takes them out at once as the flight starts and puts them back as it ends. |

## hud

When a player starts to work at the station, the game's HUD leaves the screen: every part of it slides off to the edge nearest
to it, the hotbar and the health down, the chat to the left, effects and a scoreboard to the right, boss bars up. Other mods'
overlays go the same way, each to the edge nearest to where it is drawn: on Forge every overlay by itself, on Fabric what mods
draw through `HudRenderCallback` as one part. What covers most of the screen (the vignette, frost) does not slide and stays
while the rest of the HUD is gone (`keep_vignette`). The HUD slides back while the camera flies back. Whether the HUD and the chat go at all is `interface.hide_hud` and
`interface.hide_chat`; a player who hid the HUD with F1 sees no slide. Every field is optional.

| Field | Default | Meaning |
|---|---|---|
| `slide` | `true` | The parts slide away and back; `false`: they are gone and back at once. |
| `seconds` | `0.3` | How long the slide takes. |
| `keep_vignette` | `true` | What covers the whole screen stays while the rest of the HUD is gone: the vignette, frost, the view through a carved pumpkin, the portal's swirl, other mods' screen tints (on Forge each such overlay; on Fabric the game's own). `false`: they go with the HUD. |

## feedback

How the scene answers what the player does. Nothing changes by default; every field is optional.

| Field | Default | Meaning |
|---|---|---|
| `sounds` | `{}` | The library's own sounds at this station, by name: `pick_up`, `put_down`, `grab`, `set_down`, `toggle`, `button`, `detent`, `pour`, `landing`. Each is a sound id (`"mymod:station.clink"`), `false` for silence, or `{"id": ..., "volume": 0.5, "pitch": 1.2}`, where `volume` and `pitch` multiply what the library plays (a hard landing still sounds louder, a fuller vessel higher) and `id` may be left out to keep the sound. A missing name sounds as it is; `interface.sounds: false` still silences them all. A node's own `feedback.sounds` (`grab`, `set_down`, `toggle`, `button`, `detent`, `pour`) wins over the scene's. |
| `reactions` | `[]` | Effects played when something happens, below. |
| `impact_min_speed` | `12` | How fast (pixels a second) placed things must knock together for an `impact`. |

```json
"feedback": {
  "sounds": {"put_down": "mymod:station.clink", "landing": false, "grab": {"pitch": 1.3}},
  "reactions": [
    {"on": "contact", "other": "grindstone", "min_speed": 20, "steps": [
      {"type": "particles", "style": "diegesis:spark", "count": 6, "at": "point"},
      {"type": "sound", "id": "minecraft:block.grindstone.use", "volume": 0.3, "pitch_by_speed": 0.2}]},
    {"on": "impact", "min_speed": 30, "effect": "mymod:clatter", "shared": true}]
}
```

### feedback.reactions

A reaction plays an effect when an event happens in the scene, before the behaviour's `onEvent`, at the event's node (at
the other node for the stack in the hand, which has none). It is only the look: it never changes the station's state. It is seen by the local player, and with `shared` by everyone near the station.

| Field | Default | Meaning |
|---|---|---|
| `on` | required | The event, below. |
| `node` | any | Only for this node: the one held, clicked, landed, or a control's node. |
| `other` | any | Only when the other node is this one: what was touched, poured into, landed on. |
| `mark` | any | Only when the other node carries this mark (`contact`, `exposed`, `unexposed`). |
| `min_speed` | `0` | Only at this speed or faster, pixels a second (events with a speed). |
| `effect` | none | An effect id: an effect file, one registered in code, or a particle style (a burst). |
| `steps` | none | The effect written out here instead of `effect`: a list of steps, below. |
| `cooldown` | `0.1` | Seconds before it plays again. |
| `shared` | `false` | Everyone near the station sees it too, through the server (at most 10 a second). Needs `effect`: the others play it from their own files. |

| `on` | When | `node` / `other` |
|---|---|---|
| `click` | A node without a role was clicked. | the node |
| `grab`, `release` | A grabbable was picked up, let go. | it / the node under it |
| `contact`, `contact_end` | A carried node started, stopped touching another. Speed: how fast it moved. | it / the touched node |
| `pour`, `pour_end` | A held vessel started, stopped pouring. | it / the vessel poured into |
| `control`, `button` | A control was moved, a button pressed. | the control's node |
| `impact` | Placed things knocked together, or into a wall (`placing.physics`). Speed: how hard; point: where. | the moving one / the one hit (none for a wall) |
| `landed` | A placed thing came to rest where it was put. Speed: how hard it landed. | it / what it landed on |
| `fell_off` | A placed thing fell off its surfaces. | it |
| `stream_landed` | A stream of liquid reached where it lands. Point: where. | the vessel it came from (none for code's streams) / the node it fills |
| `submerge`, `emerge` | A carried node went under a liquid's surface, came out again. | it / the tank's node |
| `item_contact`, `item_contact_end` | The stack in the hand started, stopped touching a trigger or marked node. Speed: how fast it moved. | none / the node |
| `exposed`, `unexposed` | A carried node or the stack in the hand started touching a node with a mark, left the last one with that mark. | it (none for the stack) / the marked node |

### Effect steps

An effect is a list of steps, written in a reaction's `steps` or in an effect file
(`assets/<namespace>/diegesis/effects/<path>.json`, `{"steps": [...]}`; its id plays it anywhere an effect id is taken). Each
step has a `type` and `at`: `node` (default: the effect's node), `other` (the event's other node) or `point` (where the event
happened; the node without one).

| `type` | Fields | Does |
|---|---|---|
| `particles` | `style` (required), `count` (`8`), `color`, `where` (`place`, `in_liquid`, `on_liquid`, `liquid_edge`), `offset` (`[0,0,0]`), `velocity`, `spread` (`0.4`) | A burst of scene particles. The liquid kinds need a node with a tank. Without `velocity`, falling kinds burst up a little and light ones drift. |
| `sound` | `id` (required), `volume` (`1`), `pitch` (`1`), `pitch_by_speed` (`0`) | A sound; `pitch_by_speed` raises it per 100 pixels a second of the event's speed. |
| `light` | `level` (`15`), `seconds` (`0.4`) | The node's own `light` flashes up and fades back. A node without `light` has nothing to flash. |
| `shake` | `pixels` (`0.3`), `seconds` (`0.3`) | The node shakes, calming down. |
| `emit` | `seconds` (`1`) | The node's own `emitter` runs for a while, also when it is switched off. |
| `ripple` | `strength` (`0.5`) | Ripples over the node's liquid, from the event's point when it has one. |

### Marks

A node's `feedback.marks` say what touching it means: `diegesis:fire`, `diegesis:hot`, `diegesis:wet`, `diegesis:sharp` or
any id of your own. A tank's node holding liquid is `diegesis:wet` and its liquid's id (`minecraft:lava`) by itself. Marks are
labels: nothing burns or gets wet unless the station's logic says so. A carried node or the stack in the hand touching a marked
node gives `SceneEvent.Exposed`; the behaviour sends an action and the logic decides what it means:

```json
{"id": "burner_flame", "collider": {"from": [7, 9, 7], "to": [9, 12, 9]}, "trigger": true,
 "feedback": {"marks": ["diegesis:fire"]}}
```

## Meshes

A visual can be any polygon model, not only Blockbench cubes: export OBJ from Blender (or any 3D tool), put it at
`assets/<namespace>/diegesis/meshes/<path>.obj` with one unit per pixel, and name it with a texture of the block atlas:

```json
"visual": {"mesh": "mymod:chess/king", "texture": "mymod:block/chess/white_king"}
```

- `v`, `vt`, `vn` and `f` are read (triangles, quads, larger polygons); materials and groups are ignored, one mesh has one
  texture. Normals are used as they are, so smooth shading comes through; without `vn` faces are flat.
- A texture under `textures/block/` is in the block atlas by itself. UVs stay inside the sprite: no tiling.
- The default `anchor` is the file's own origin. Keep the model's base at y 0 and the object stands on the node's `pos`.
- The same file can take any texture: white and black chess pieces are one mesh each, two sprites.
- Tint, `translucent`, `emissive`, `glass`, `double_sided`, shadows and the hover outline work as on block models.
- Keep them small: a mesh is drawn every frame, 100 to 300 faces per object is plenty.
- Objects close together, seen at an angle: `"precise": true` on the node hits the mesh's own polygons, so a box round one
  object does not catch a click meant for the one behind it. The `collider` stays the first, rough test.

## Repeating a node

`repeat` lays a node out on a grid: the 64 squares of a board are one entry. Each cell is the node `step` further on, with
the id `<id>_0`, `<id>_1`... (x counts fastest, then y, then z; child nodes get the same ending):

```json
{"id": "mark", "pos": [26.5, 14.7, 5.5], "repeat": {"count": [8, 1, 8], "step": [-3, 0, 3]}, "pickable": false, "hidden": true}
```

## Views from seats

A station with seats (see [Stations](stations.md#seats)) gives each its own view. Whoever holds no seat, or a seat without
a shot, sees `position` and `target`:

```json
"camera": {
  "position": [54, 44, 16], "target": [16, 14, 16],
  "seats": {
    "white": {"position": [16, 47, -13], "target": [16, 14, 13.5], "fov": 40},
    "black": {"position": [16, 47, 45], "target": [16, 14, 18.5], "fov": 40}
  }
}
```

## placing

Free placing: the player puts items down anywhere on the scene's surfaces, turned and sized as they like, and they stay there
for everyone to see. It shows a slot group with `placed()` rules; every item of the group is an object of the scene, so the
file has no node per item.

```json
"placing": {
  "group": "items",
  "item_size": 6,
  "surfaces": [
    {"from": [2, 2.5], "to": [30, 14.5], "height": 12, "clearance": 8, "aim_height": 7, "wall": 15}
  ]
}
```

| Field | Default | Meaning |
|---|---|---|
| `group` | required | The slot group that holds the items. |
| `surfaces` | required | Where they can stand, see below. |
| `item_size` | 6 | Pixels the longest side of an item measures at size 1. |
| `turn_step` | 15 | Degrees of one notch of the wheel. |
| `scale_range` | [0.5, 1.5] | The sizes a player can give an item, as factors of `item_size`. |
| `scale_step` | 0.1 | What one notch of the wheel changes the size by. |
| `turn_speed` | 1.2 | Degrees an item turns per GUI pixel of a drag. |
| `stacking` | true | Items can stand on each other; what stood on an item comes down when it is taken away. |
| `block_models` | true | A block item with a flat icon (a lantern, a torch, a flower pot) stands there as the block; one whose block model draws nothing (it is drawn by a block entity renderer) keeps its icon. |
| `item_light` | true | Items of blocks that give light shine on the scene, candles and torches burn. |
| `light_radius` | 11 | How far their light reaches, in pixels. |
| `light_color` | `#C0FFC888` | Colour of the pool of light they throw; alpha is its strength. |
| `shadows` | true | Items cast their shape on what they stand on. |
| `snap` | 0.25 | Positions are rounded to this many pixels; 0 keeps them as they are. |
| `preview_color` | `#A0FFFFFF` | Colour of the see-through copy that shows where the item in the hand would stand. |
| `counts` | `"never"` | When the count of a stack of more than one is written at its foot, facing the front: `always`, `hover` (while the cursor is on it), `never`. For places that keep whole stacks, such as the shelves of a cupboard. |
| `physics` | none | Items fall, tip over and push each other, see below. Without it they stand exactly as they are put. |
| `hover_lift` | `0` | Pixels an item rises while the cursor is on it. 0 keeps it still; the outline says what the cursor is on. |
| `frame_pose` | `false` | Every item stands as an item frame shows it. By default an item with a 3D model (a can, a jar, a bottle) stands as it is modelled, upright, where a frame would lay it on its back to face out of the wall; flat icons stand as in a frame either way. |
| `stack_low` | `0.45` | An item lower than this share of `item_size` is all top: pointed at anywhere, the stack in the hand goes on it (a lying book, a plate). |
| `stack_from` | `0.5` | Of a taller item, the share of its height from its foot up from which pointing at it means on top of it; lower, the stack goes in front of it. |
| `stack_snap` | `0.3` | Put on a wider thing, the stack in the hand goes where the cursor points on its top; within this share of that thing's half size from its middle (each way) it goes to the exact middle, so same things pile up neatly. 1: always the middle. |
| `stack_edge` | `0` | How far in from the edge of what it goes on the middle of the stack in the hand stays, as a share of its own half size: 0 lets its middle reach the edge, no further; 0.5 keeps half of it over the top. |
| `stack_align` | `12` | Degrees: a flat thing laid on a flat thing turned within this much of it (or of a quarter turn from it) takes its turn, so a pile of sheets lies square. 0: never. |
| `stack_shrink` | `0.75` | Of the size the player gave it, how small a thing put on another is made at most to fit under what is over it (never below `scale_range`). Where it does not fit at that, the mark shows it beside the pile and the pile's top gives a short nod. Taken again later, a squeezed thing has its own size back. 1: never smaller. |
| `right_puts_one` | `true` | A right click with a stack in the hand that does not move the mouse puts one of it down, as in a chest; the rest stays in the hand. Held and moved, the right button turns the stack as before. `false`: the right button only turns. |
| `turn_detents` | `90` | Degrees between the turns about the vertical a turn by hand (the right button held) settles on: square. |
| `turn_detent` | `3` | Degrees: a turn by hand let go of within this much of one of those settles onto it with a tiny turn; anywhere else it stays as it is. 0: never. The wheel keeps its `turn_step` notches. |
| `grip_blend` | `12` | GUI pixels of the cursor's travel over which an item just taken goes from where it stood to under the cursor. Taken and put again without moving, it stands exactly where it stood; a drag of a placed item (with `physics`) keeps the point grabbed under the cursor, so small nudges work. 0: it jumps under the cursor at once. |
| `pick_min` | `0.75` | Pixels each side of an item's hit box measures at least for the cursor: a lying sheet seen edge-on or the tip of a slanted icon is still hit, though only where the cursor is on no item exactly. A slanted icon (a carrot, a sword) is hit on its narrow box round its pixels, not on its square. 0: the exact boxes. |

A surface:

| Field | Default | Meaning |
|---|---|---|
| `from`, `to` | required | Two corners, `[x, z]`. |
| `height` | required | Its height. |
| `clearance` | 64 | Room above it: an item that would not fit is made smaller. |
| `aim_height` | 0 | How the cursor finds a place. 0: it points at the surface itself, right for a table seen from above. A shelf seen from the front is too thin a sliver for that: with a height, the cursor points at a slope from the front edge (the side of the smaller z) up to this height over the back edge, so that low in the opening means at the front and high in the opening means at the back. |
| `wall` | none | The z of a wall behind the surface: light in the room falls on it too. |
| `open` | `[]` | With `physics`, the sides with no wall: `front` (-z), `back`, `left` (+x, the viewer's left), `right`. A thing pushed or pulled over such an edge falls off, onto a surface below or, with none there, out of the station into the player's inventory, and onto the ground in front of the station where the inventory is full. |
| `flat_rest` | `"stand"` | How a flat thing (an icon: bread, paper, a sword) is put down here unless the player turns it otherwise: `stand`, standing facing the front, right for a shelf seen from the front; `lie`, lying on its back with its top towards the far side, right for a drawer or box seen from above, where it reads whole and piles up. The stack in the hand takes the way of each surface it goes over; things that come without a place lie there too, and the same flat things go onto a pile of them first. |
| `fill` | `"back"` | Where a thing that comes without a place (shift-clicked in, brought by a hopper) is set out on this surface: `back`, from the back row to the front, each row from its middle out, to the side away from the camera first, right for a shelf; `middle`, as near the middle of the surface as there is room, then round it, nearest first, right for a plate or a turntable. |

What the player does: a stack on the cursor stands see-through where the cursor points. A click puts it down (as much of it as
a slot takes: with `maxStack(1)` one item, and the rest stays in the hand for the next place). A click on an item takes it,
with the turn and size it had; a drag moves it. The wheel turns what is in the hand or under the cursor, Shift+wheel tips it
towards the player, Control+wheel sizes it; the right button held turns it freely with the mouse. A right click without moving the mouse puts one of the stack down (`right_puts_one`, default true), the rest stays in the hand. Items settle on their lowest
point whichever way they are turned. Shift-click takes an item back into the inventory.

### placing.physics

```json
"placing": {"group": "items", "surfaces": [...], "physics": {}}
```

Things put down are solid. One let go of on its edge falls onto a side unless something holds it up; one put down where
another stands slides out of it (as a whole, so the other is not knocked over); one put on top of another stays there, and
comes down when the one below is taken away. Each item is a box the size of its model; the surface's edges are walls and its
clearance a ceiling; a flat icon drawn on the slant (a carrot, a fish) collides with the narrow box round its pixels, turned with them. Flat items (food icons) stand as they were turned and rock when bumped, as a roly-poly toy does, coming
back to that turn. The player who moves things works it out on their side and sends where everything came to rest; everyone
else sees the items glide there.

Each thing is a solid body with a weight: its size times how dense it is (a block by what it is made of: metal, stone, wood,
wool; anything else as water), and hard to turn as a solid box of that weight is. Things hit each other as such bodies do: a
stone block shoves an apple aside, an apple hardly moves the block; what hits stops dead (`bounce` gives a little back). A
thing grips what it stands on: pushed less hard than `friction` times its weight it stays where it is, pushed harder it
slides and soon stops; nothing slides on by itself.

What fits in is a matter of room, not slots. A thing put down where there is no room pushes the others as any thing does,
and what is crowded over an open edge falls out: into the inventory of the player at the station, and onto the ground in
front of the station where the inventory is full. An item that comes without a place (shift-clicked in, brought by a hopper)
stands on the first free spot, from the back of each surface to the front, made smaller where it does not fit at its size;
`StationView.hasRoomFor` says whether one more stack would fit somewhere.

The grip is exact, the body is physical. The stack in the hand is held by its top: the point held follows the cursor closely
(`carry_lag`) and alone decides where it lands. Below the grip it is a weight: it floats `hover` pixels over what is under it,
sways a little as it is carried (`swing`, at most `swing_angle`) and hangs still within `swing_calm` once the hand is still.
Carrying never disturbs what stands there: over an item it rises onto it at `climb` where the surface's `headroom` leaves
room for it on top, and where there is no room it keeps beside the item, at any speed, so it lands where it is seen. With
`hand_pushes` it is solid instead: it rises only when brought near gently, and swept in fast, or where there is no room
above, it pushes the item. Going to another surface (a shelf, a door's bin) it hops over in a short arc (`hop_time`),
touching nothing on the way. Let go of, it drops under the grip with the swing's tilt (a rigid item's tilt cut below its own
tipping angle, so the swing alone never knocks it over); with `toss` a moving hand tosses it on a little.

A pile is one thing: pointed at anywhere, the stack in the hand goes on its top, where the cursor points on that top (near
its middle on the middle, `stack_snap`), so two small things stand side by side on a big one and a pile can be fanned. A single item takes it on top where the
cursor is on its upper part (`stack_from`) or anywhere on a low thing (`stack_low`), and in front of it lower down.
Taken from a pile, a piece leaves what stood on it coming straight down by its height, still a pile.
Aimed at the top of an item, the stack in the hand goes on it, made as small as it must be to fit under the clearance (down
to `stack_shrink` of its size; smaller than that, it keeps beside the item). Put on another item, a thing stands as put
until something touches it or the item under it is moved or taken away.

Things move only when the hand means them to. The left button held on an item and moved drags it along its own surface (the
cursor past the edge keeps it there, it never drops onto a shelf behind); pressed into a wall at a slant it turns flat
against it. The right button held and moved turns it, `turn_power` times quicker than without physics. Either way the item
keeps its weight: the hand pulls it with `hand_force`, so what is in its way is pushed, tipped and knocked aside as far as
that force moves it, and what cannot give way (an item against a wall) stops it; let go of, it stays where it is (with `toss`, it slides and spins on a little).
A click without moving still takes it. Whatever is pushed, bumped or crowded over an open edge falls out (`fall_off: any`);
with `fall_off: held` only an item dragged by hand over it and let go of there does, and anything else meets the edge as a wall.

| Field | Default | Meaning |
|---|---|---|
| `gravity` | `157` | How fast things fall, pixels per second squared. 157 is the real one (9.81 m/s²) with a block a metre and 16 pixels a block; for a scene drawn at another scale, 9.81 times its pixels per metre. |
| `friction` | `0.6` | 0..1, how hard a thing grips what it stands on or touches, as a share of its weight: pushed less than that it stays put, pushed harder it slides and slows down. |
| `upright_flat` | `true` | Flat items (an icon: bread, a carrot) standing straight (within 10 degrees) stay standing and rock back when bumped; at a lean or lying they are thin rigid plates. `false`: thin rigid cards that fall over also standing straight. |
| `rock` | `25` | Degrees a flat item rocks at most when bumped; 0 keeps it rigidly upright. |
| `flat_depth` | `0` | Pixels of depth a flat item collides with at least (never under 0.2, and 0.02 more so that faces put against each other never share a plane and flicker). The physics, the stack in the hand and free spots all reckon with this one thickness. |
| `hover` | `1.5` | Pixels the stack in the hand floats over what is under it (0..8). |
| `carry_lag` | `0.035` | Seconds the point held takes to close half the way to the cursor. 0 follows at once. |
| `reach` | `4` | With `hand_pushes`, pixels around the stack in the hand within which it rises onto an item. |
| `climb` | `48` | Pixels a second the stack in the hand rises onto an item it comes near; it comes down three times as fast. |
| `swing` | `true` | The stack in the hand hangs from its top and swings as it is carried, as a weight does; `false` keeps it upright. |
| `swing_angle` | `12` | Degrees the stack in the hand swings out at most. |
| `swing_calm` | `0.15` | Seconds the swing takes to die down once the hand is still. |
| `hop_time` | `0.1` | Seconds the stack in the hand takes to hop over to another surface; 0 jumps there at once. |
| `toss` | `0` | 0..1, how much of the hand's speed a stack let go of while moving keeps (at most 24 pixels a second), and of its turning an item let go of while turned. 0 stops each where it is let go. |
| `hand_pushes` | `false` | The stack in the hand is solid: swept in fast it shoves and tips what it meets, and it rises onto items within `reach` only when brought near gently. `false`: it passes things by, rising onto them or keeping beside them. |
| `hand_force` | `40000` | How hard the hand pulls an item it drags or turns, as weight (cubic pixels times density) times pixels a second squared. An apple follows the cursor at once, a stone block comes slower, a row of them slower still, and what cannot give way (pressed against a wall) stops it: nothing is pressed into anything. |
| `turn_power` | `1.5` | How much quicker an item turns by hand with physics than `placing.turn_speed` says. |
| `fall_off` | `any` | What may go over an open edge: `any`, anything pushed, bumped or crowded there; `held`, only what the hand drags there. |
| `max_speed` | `80` | Pixels per second a thing moves at most: a push never flings it far. |
| `bounce` | `0` | 0..0.5, how much of the speed a thing hits something with it bounces back with. 0: it stops dead where it lands or hits. |
| `landing_sound` | `true` | A thing put down sounds when it touches what it lands on, and as what it is: a block with its own place sound, a flat thing a light tick, anything else lower the heavier it is; louder the faster it comes down. A thing let go of after a drag sounds only if it falls. `false`: the same sound at the click for everything. |
| `flush` | `0.3` | Pixels: put down this close to a neighbour or a wall, a thing lands touching it, with no sliver of a gap (never more than this along each way, never into anything). Kept beside an item it cannot go on, it goes to the side the cursor is nearer to, except on a shelf seen from the front (`aim_height`), where it goes in front. 0: where it was let go. |
| `flat_poses` | `"free"` | How a flat thing (an icon: bread, paper) may be turned. `free`: to any angle by the wheel (Shift+wheel tilts it in `turn_step` notches) or by hand; let go of, it rests as it falls: standing straight it stays standing and rocks back when bumped, at a lean it falls flat or leans on what is beside it, lying it does not rock and comes to rest dead flat. `two`: standing up or lying down only; Shift+wheel goes over to the other in one notch, and a turn by hand that ends between settles on the nearer. |
| `pile_rides` | `true` | A pile is one thing: what stands on an item goes along with it, standing as it stood, when the hand drags, turns (also by the wheel) or pushes that item, and falls only when the item tips over (more than 8 degrees), the pile would go through a wall, or a dragged thing hits the pile itself. Moved by the physics alone (settling in a heap, knocked by a falling thing), what stands on it comes to rest by the physics too. `false`: what stands on an item holds on by friction alone. |
| `push_out` | `12` | Pixels per second a thing put down, carried or crowded inside another glides out of it at most, without taking speed from that: nothing is flung out of a crowd. A crowd that cannot all fit (a full drawer) is left wedged after a second and comes to rest. 0: out at once. |
| `wake` | `"near"` | What a change at the station wakes. `near`: what it touches, and on from there what touched something that then moves or goes: take a thing from under a heap and what lay on it comes down, a heap elsewhere stays exactly as it is. A thing laid gently on a heap rests on it; one falling hard knocks what it hits. `all`: every item, as before 1.0.0-beta.15. |
| `fall_ease` | `0.008` | Seconds a thing moved by the physics is drawn behind where the physics has it (half the way): small, so a fall looks as fast as it is and stops dead. Things set somewhere by the server or the wheel glide as before. |

A surface's `headroom` (pixels, the `clearance` when not given) is how high over it the stack in the hand may be lifted to
pass over what stands there: more than the clearance where open air is over the surface (a drawer pulled out).

Surfaces on parts that move (a drawer, the shelves of a door) are written where the part stands at one of its ends: open,
or shut for a door that stops part way open and would turn its shelves askew. The cursor, the see-through preview and the
pool of light find them where they are now; the station's code moves
what stands on them with the part (`StationView.setSurfaceTransform`) and hides it all while it is shut away
(`setPlacingVisible`), or keeps it in sight but out of reach behind a shut glass door (`setPlacingReachable`).

The light of lights in the scene stays in the room it is in: a pool on the surface under it and on its `wall`, cut off at the
surface's edges, not through a board to the shelf below. Models drawn `lit_as_block` (the scene's copy of a cupboard) take it
too: their walls and boards brighten round a lantern standing among them, faces turned away from it stay dark, and the
surfaces stand between it and what is on their other side.

## captions

How the lines of `StationView.showCaption` look: `edge` (`top` or `bottom`), `color` (`#FFFFFFFF`), `background`
(`#80000000`; a zero alpha draws no band).

## Node templates

A node template is a node without an `id`, in `assets/<namespace>/diegesis/nodes/<path>.json`
([`schema/node.schema.json`](schema/node.schema.json)). A scene node with `"template": "<namespace>:<path>"` starts
from it and lays its own fields over it:

- objects merge field by field (`"control": {"control": "gas"}` keeps the template's control type, axis and base);
- other values replace the template's - also lists: a node's own `children` replace the template's;
- `null` removes a field of the template (`"base": null`);
- a template may start from another template; a loop is an error.

```json
{
  "visual": {"model": "mymod:diegesis/valve"},
  "control": {"type": "knob", "range": [0, -270], "base": {"model": "mymod:diegesis/valve_base"}},
  "scale": 0.5
}
```

## Debugging a scene

- **F3+B**, `/diegesis debug` or `DiegesisClient.setDebugView(true)`: hit boxes coloured by role with node ids, the
  laid-out inventory slots, the work plane, the camera target.
- **F3+T** reloads every scene and template. Watch the log for `Scene <id> (<file>) is broken: <reason>`.
- A node you cannot click is usually covered by a bigger collider in front of it (the debug view shows which).
- **Flicker**: faces that lie on each other fight for the same pixels, and a model that is clean on its own can do it in a
  scene. Check before you look: `python tools/scenes/depth_check.py` (needs numpy) puts the block's model, every node's
  model and every tank's walls in one space and lists the faces that touch. Point it at your mod with `--assets
  src/main/resources/assets`, and name the block's models in `tools/scenes/blocks.json`, a scene does not know them.

Solid parts must stay 0.05 px apart, measured in the scene, after the node's `scale`: closer ones flicker from a distance
of the station's drawing range. See-through parts need no gap from solid ones: glass may stand on the table and a liquid may
fill a sink to its walls, the see-through layer always wins. Two see-through faces in one plane are a problem again, as
their order then depends on the camera.
