# Client

Client code is optional: a station works with only its scene. Add it for animations, sounds and effects that react to
the state and to what the player does. Call `DiegesisClient` from client setup only (Forge: behind
`FMLEnvironment.dist == Dist.CLIENT` or in `FMLClientSetupEvent`; Fabric: `onInitializeClient`) - a dedicated server
has no client classes.

## Behaviours

```java
DiegesisClient.registerBehavior(MyStations.STILL.id(), StillBehavior::new);
```

One behaviour object is created per station shown on this client - in use by the local player, by someone nearby, or
idle with `idle_visible` - so it may keep per-station fields. Every method runs on the render thread and has an empty
default.

| Method | When |
|---|---|
| `onOpen(view)` / `onClose(view)` | The local player starts / stops working at the station. |
| `tick(view)` | Every client tick while the station is shown. |
| `frame(view, deltaSeconds)` | Every frame before the station is drawn: smooth animation. |
| `holdsBlock(view)` | Whether the scene still stands in for a hidden block after the last player left: a lid falling shut. The block stays hidden while it returns true; asked every frame. |
| `onStateChanged(view, changes)` | Slots, controls, tanks or outputs changed, by any player or by the logic. `changes.slots()`, `controls()`, `outputs()`, `tanks()` name what. |
| `onEvent(view, event)` | The local player did something (below). |
| `onItemClick(view, node, stack, button, shift)` | The local player clicked a node with `stack` on the cursor (or let it go there), before it goes anywhere: return true when you handled it, usually with `sendAction`, and no slot sees the click. A bowl dipped into a soup, a tool touched to a part; `shift` for the whole stack at once. |
| `onKey(view, keyCode, scanCode, modifiers)` | A key went down while the player works at the station, before the library's own keys (leaving, going over, slot and drop keys); repeats of a held key too. Return true to take it. Take only what the station needs at that moment, such as typing into a field it shows. |
| `onChar(view, character, modifiers)` | A character typed while the player works at the station. Return true to take it. |
| `onScroll(view, node, amount)` | The mouse wheel over the scene, `node` under the cursor (null over nothing), before the library turns a control or a placed object: return true when you handled it. Going up or down a tower, leafing through something; `amount` positive away from the player. |
| `onInventoryShiftClick(view, slot, stack)` | Shift + click on a stack of the inventory: return true when the station takes it itself (a stack laid on a board), false for the usual move into its slots. |
| `onEffect(view, effect, node, data)` | The logic played an effect (`StationContext.effect`), after the effect itself. |

### Behaviours added by other mods

`DiegesisClient.addBehavior(stationId, factory)` adds a behaviour to a station type that has its own already, yours or
another mod's; `addBehavior(factory)` adds one to every station. It is a whole `StationBehavior`, created per shown station
like the station's own, and runs after it in the order added. Hooks that take input (`onItemClick`, `onScroll`, `onKey`,
`onChar`, `onInventoryShiftClick`) reach it only when no behaviour before it took the input, so it can add a gesture but never
take one away; `holdsBlock` holds while any says so. One that throws is logged once and the others still run.

```java
DiegesisClient.addBehavior(new ResourceLocation("othermod", "anvil"), view -> new StationBehavior() {
    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.ButtonPressed) {
            view.spawnParticles(DiegesisParticles.SPARK, null, 6, 0);
        }
    }
});
```

For a client-only station the behaviour is the whole client logic. For a server station it is purely visual: real
changes go through the server with `view.sendAction(action, data)` (arrives in `StationLogic.onCustomAction`).

Because every player near a server station runs its behaviour on the same synced state, looks driven by
`onStateChanged` and `frame` (a lamp that lights with a lever) are seen by everyone around.

### StationView

`type()`, `level()`, `pos()`, `facing()`, `state()` (read only), `isFocused()`, `isInUse()` (anybody works there now),
`isBlockHidden()` (a `hidesBlock()` station's block is left out of the world in this frame: show the scene's copy of the block
exactly then, and the two swap in one frame without a blink), `age()` (seconds shown),
`node(id)`, `toWorld(x, y, z)` (scene pixels to world), `sendAction(action, data)`, `isShiftDown()` (shift as the
station's clicks see it: read it for a `Click` that does more with shift, not `Screen.hasShiftDown()`),
`spawnParticles(style, node, count, color)`, `spawnParticles(style, at, velocity, spread, count, color)`,
`spawnInLiquid(style, node, count, color, velocity)` (anywhere inside a tank node's liquid, wherever the node is now -
also carried or tipped - rising to the surface: glints in a reacting brew; they dim with their depth, and a style with a `surface`
goes on there), `spawnOnLiquid(style, node, count, color, velocity)` (leaving the surface at random points: steam off a soup),
`spawnAtLiquidEdge(style, node, count, color, scale)` (small bubbles on the surface along the walls: use `BUBBLE_POP`),
`pourStream(key, from, to, color, flow, liquidNode)` (a stream of liquid from one point to another, in scene pixels, as a vessel poured
by hand runs: call it every frame while it runs, and it lets go of `from` and falls the rest of the way once the calls stop; it rings
`liquidNode`'s liquid where it lands, or only splashes for `null`: a bottle an animation tips over a pot),
`loopSound(name, sound, volume, pitch)` (a sound that goes on while it is wanted, gliding to the volume and pitch asked for and fading out at 0),
`cursorOn(height)` (where the cursor points on the level plane at that height, in scene pixels: a spoon following the hand),
`pointerOn(node, height)` (the cursor on the plane through a node, in its own pixels: what a painted page was clicked at),
`showOverlay(overlay)` (a `ScreenOverlay` the mod draws in GUI pixels over the scene: a book of recipes, a map; it has the mouse until Escape, a
click it refuses or `showOverlay(null)`),
`playSound(sound, node, volume, pitch)`,
`setInventoryOpen(boolean)` and `isInventoryOpen()` (the `inventory_drawer` or a toggled laid-out inventory),
`setInventoryFilter(predicate)` (which stacks a `compact` drawer shows: the ones the station can use),
`setSurfaceTransform(surface, matrix)` (what stands on a surface of the `placing` moves with a part: a drawer sliding out, the
shelves of a door; write the surface where the part stands open) and `setPlacingVisible(boolean)` (the placed items are drawn or not:
off while they are shut away; set it every frame it should hold), `setPlacingReachable(boolean)` (the placed items can be
picked and the surfaces take items: off while they are seen but out of reach, behind the shut glass door of an oven; set it every
frame it should hold),
`setCarriedHidden(boolean)` (the library leaves out the stack on the cursor while it is over the scene, so the station draws it
as the tool it is: a knife that comes down when clicked; set it every frame it should hold),
`glance(id)`, `glanceBack()` and `currentGlance()` (the camera's `glances`: show the player something at a moment of your
choosing; a glance without an `edge` exists for this), `moveCamera(x, y, z)` and `cameraMove()` (the whole camera, its
shot, seat, inventory shot and glances, moved by that many scene pixels and gliding there: work at another height of a tower),
`mirrorCamera(x)` and `cameraMirror()` (the whole camera mirrored across the upright plane at scene x: a block that comes in two
hands, such as a cupboard whose door hangs on either side, is seen from the side the door leaves free; `NaN` turns it off).

### NodeView

Offsets set by code are added on top of the node's place from the scene and of what the player does with it; they
belong to this client only.

| Method | Effect |
|---|---|
| `setVisible(boolean)` | Shows or hides the node and its children. |
| `moveTo(x, y, z, seconds)` | Moves it by that many pixels from its place, eased (0 seconds = at once). |
| `rotateTo(x, y, z, seconds)` | Extra Euler degrees (y, x, z), eased. |
| `scaleTo(factor, seconds)` | Extra scale, eased. |
| `spin(x, y, z)` | Keeps it turning about its own axes at that many degrees per second (a stirrer, a fan); `spin(0, 0, 0)` stops it where it is. |
| `shake(pixels, seconds)` | Shakes it by up to that many pixels, dying away (a bump, a burst, a tapped glass). |
| `setVisual(visual)` | Draws another visual (`Visual.ofItem(..)`, `Visual.ofModel(..)`); `null` goes back to the scene's. The hit box stays the scene's. |
| `setTooltip(component)` | The text next to the cursor over the node; `null` goes back to the scene's `tooltip`. |
| `setTint(argb)` | Colour multiplied into its visual; alpha fades it. |
| `setEmitting(boolean)`, `setEmitRate(perSecond)` | Its emitter. |
| `setAgitation(0..1)` | How restless its liquid surface is: 1 is a rolling boil. |
| `setLiquidColor(argb)` | Draws its liquid in another colour whatever it is made of (0: its own again); it drifts there. A soup that takes the colour of what is in it. |
| `ripple(x, z, strength)` | Sends ripples over its liquid from a point in the node's pixels: something dropped in, a stirring rod. `strength` is about the wave's height in pixels. |
| `setFloating(floaters, count)` | Floats `count` things on its liquid, in turns from a list of `Floater(sprite, color, size[, solid])`: slices of voxels built from the sprite's pixels (or, not solid, the flat sprite), riding the waves; a strong current turns them over now and then. They come up from below one by one, overshoot and ring the surface; things no longer in the list leave. Built-in tintable sprites: `Floater.COIN`, `CHUNK`, `NUGGET`, `SHRED`, `LEAF`, `DROP`. In a mass they are held in it instead: berries in a porridge, leaves in a salad. |
| `setFloatingObstacles(x, z, radius, ...)` | What stands in its liquid, in the node's pixels: what floats is pushed aside and goes round it, as round a spoon. Call it every frame while it moves. Floaters push each other by themselves. |
| `setOnTop(true)` | Draws its own visual and shape over everything while the local player works at the station, smooth as a `NodePainter` with `smooth()`: a badge beside an object that the block or a neighbour must not hide. A tank's liquid comes along inside its walls: a mug held low before a keg's tap, not cut by the floor. Others see it drawn with the scene. |
| `setOutlineSeenOnly(true)` | The hover outline goes round only what the player sees of the node: one basket of a stack outlines as its own band, not as its whole shape through the baskets over it. Off by default (the whole silhouette, as a glowing entity's); under a shader pack the whole silhouette stays. |
| `setPainter(painter)` | Draws by code at the node every frame, with the scene (`NodePainter.paint(poseStack, buffers, light)`, in the node's pixels): a vanilla model, text and items on a page. `DiegesisClient.tinted(buffers, argb)` tints or fades what is drawn through it, block items too. |
| `leaveWake(x, z[, width, strength])` | Leaves a trail on its liquid: call it as something moves through. A furrow in the liquid's own colour, lit on one side, that stays in the liquid, goes round with `setCurrent`, runs out and fades. `width` in the node's pixels, `strength` 0 to 1. Square tanks seen from above. In a mass (`setMass`) it pushes the mass aside: a real furrow with a ridge at each side and a heap where the stroke stops, which flows back as firmly as the mass is made. |
| `setWakeLook(seconds, contrast)` | How long trails stay on its liquid and how clearly they stand out, 0 to 1 (default 2.5 and 0.6): thin water forgets a stir at once, a thick soup keeps it. |
| `setFloatingWake(0..1)` | How clear the trails of what floats on its liquid are (default 0.7): pieces leave them as they move, the faster the clearer, and none on calm water. 0 leaves none. |
| `setEdgeBoil(0..1)` | How hard its liquid seethes along the walls, where a pot starts to boil: specks of foam come and go there. Square tanks seen from above. |
| `showLiquid(id, 0..1)` | Shows that liquid in its tank at that fraction of the box, whatever the station's tank holds: a mug filled at a tap by code. The level eases there; `null` follows the tank again. |
| `setFoam(0..1, argb)` | Foam on its liquid: clings to the walls first, lies in patches further in, grows and settles over a second. A beer's head. Square tanks seen from above. |
| `holdLevel(seconds, over)` | Its liquid keeps the level it shows for `seconds`, then goes to the tank's amount over about `over` seconds: a stream poured by hand that has yet to land. |
| `setMottle(0..1)` | How uneven its liquid looks: lighter and darker patches on the surface, pixel by pixel, that go round with `setCurrent`. Square tanks seen from above. |
| `setCurrent(radiansPerSecond)` | How fast its liquid turns, the sign saying which way round: it carries what floats on it, and a mass's relief and texture. |
| `setMass(MassLook)` | Shows what its tank holds as a thick mass instead of a runny liquid: a batter, a porridge, a dough, whipped butter, ice cream (`null`: as the scene's `tank.mass` says, a liquid without one). One lit mesh with relief that `leaveWake` furrows, the look's texture at the scene's texel density turning with `setCurrent`, grain, sheen on ridges turned to the light by its `gloss`, a rise and a darker edge where it meets the walls. What floats (`setFloating`) is held in it partly sunk: it neither bobs nor turns over, goes round with the mass and stays where it is pushed. Ripples die as it thickens; it does not slosh. `MassLook.of(texture).firm(0..1).glossy(0..1).grainy(0..1).tinted(rgb).tiled(pixels)`, fields as in [`tank.mass`](scenes.md#tankmass). Square and round tanks seen from above. |
| `setThickness(0..1)` | How far its liquid has turned into its mass (default 1): lumps of the mass form in the liquid and grow together, the liquid takes the mass's colour, the relief grows in. A milk whisked into porridge, water and flour into dough. It drifts there over a few frames; nothing changes without a mass. |
| `mound(x, z, radius, height)` | Raises its mass into a mound at that point, `radius` and `height` in the node's pixels: a scoop of ice cream, a dollop of cream. A negative height takes a hollow out, as a spoon does. It flows as the mass flows. |
| `setLight(0..15)` | Its light. |
| `extra()` | The node's `extra` JSON from the scene. |
| `sceneMatrix()`, `scenePosition()` | Where it is drawn this frame in scene pixels (before the facing turn), also while carried: points of the node go through the matrix for `spawnParticles`. |
| `worldPosition()`, `visual()`, `isHovered()`, `isHeld()`, `isVisible()` | Queries. |

### Scene events

`SceneEvent` is a sealed interface that gains events in new versions; test it with `instanceof` patterns, or give a
`switch` over it a `default` branch:

| Event | When |
|---|---|
| `Hover(node)` | The cursor moved onto a node, or off every node (`node == null`). |
| `Click(node, button)` | A node without a role but with a collider was clicked. |
| `Grab(node)` | A grabbable was picked up. |
| `Release(node, target)` | It was let go; `target` is the node under it (or null). |
| `Contact(node, other, touching, speed)` | A carried node's contact box started or stopped touching another node's collider; `speed` in pixels per second. Trigger nodes (`"trigger": true`) are zones for exactly this. |
| `Pour(node, target, pouring)` | The vessel the local player holds started or stopped pouring into `target`. |
| `InventoryDrawer(open)` | The inventory drawer slid out or back. |
| `Glance(glance, looking)` | The camera set off for a glance, or back from it. |
| `ControlMoved(control, value)` | The local player moved a control. |
| `ButtonPressed(control)` | The local player pressed a button. |
| `Impact(slot, other, speed, mass, point)` | With `placing.physics`: a placed thing knocked into another (`other` its slot) or into a wall or the floor (`other` null), at least `feedback.impact_min_speed` fast. At most every 0.1 second per thing, never while things rest; a put-down landing is a `Landed` instead. |
| `Landed(slot, on, speed, mass, flat)` | With physics: a thing put down touched what it lands on (`on` its slot, null for a surface), once, also with the landing sound off. |
| `FellOff(slot)` | With physics: a placed thing fell off its surfaces; it goes back to the inventory next. |
| `StreamLanded(source, target, point, color)` | A stream of liquid reached where it lands, once per stream: the player's pour (`source` the vessel) or `pourStream` (`source` null); `target` the node it fills. |
| `Submerge(node, tank, under, depth)` | A carried node went under the liquid of `tank`'s node, or came out (also when let go). `depth` in pixels below the surface. |
| `ItemContact(stack, other, touching, speed)` | The stack in the hand started or stopped touching a node that is a `trigger` or has marks: judged as the player sees it, the stack over the node on the screen and not far in front of or behind it. |
| `Exposed(node, mark, source, exposed)` | A carried node (`node`), or the stack in the hand (`node` null), started touching a node with `mark`, or left the last one (also when let go). Marks are labels; the logic decides what they do. |

The scene, physics and liquid events are seen by the local player's client only: the server has no scene and no physics,
so it cannot check them. Show them to others with `shareEffect`, and let real changes go through `sendAction`, where the
logic checks what it can (the item is in the player's hand, the burner is on).

```java
@Override
public void onEvent(StationView view, SceneEvent event) {
    if (event instanceof SceneEvent.Contact contact && contact.touching() && "burner_mouth".equals(contact.other())
            && "torch".equals(contact.node())) {
        view.sendAction("ignite", new CompoundTag());   // the server decides whether it lights
    }
}
```

## Shapes made by code

`NodeView.setShape(shape, texture)` draws polygons made by code at a node, and `DiegesisClient.registerItemShape(item, stack -> shape,
texture)` makes them the look of an item, from its data: in inventories, hands, frames and on shelves. Build a `MeshShape` with
`MeshShape.builder().corner(x, y, z, u, v, nx, ny, nz)`, four corners per face, counter-clockwise from outside, in pixels; the
texture is a sprite of the block atlas, and colour can be nothing but texture coordinates on a sheet. Build a new one when the
shape changes: every frame of a stroke is fine. The item still needs a model file, for its display transforms.

## Strokes

A role-less node with `"strokes": true` reports `SceneEvent.Stroke(node, phase, button, point)` while a button is held on it:
`START`, then `MOVE` every frame, also when the mouse rests, then `END`; with no button down, `HOVER` every frame while the cursor
is over the node, to show what a press would do. `point` is the cursor on the upright plane through the
node's origin that faces the camera, in scene pixels, also beside the node and in the air. It is what shaping, painting or wiping
needs: disable the node (`setEnabled(false)`) while there is nothing to work on, and send the result with `sendAction` a few
times a second, not every frame.

A long stroke need not keep the button down. Calling `view.holdStroke()` while the behaviour hears an `END` (after a short
click, say) starts the stroke again with `START`; it goes on until the next click, which ends it with `END` and does nothing
else: a spoon taken by a click stirs while the mouse moves and is put down by the next click.

## Moving things by code

`NodeView.moveTo` goes in a straight line; `hopTo(x, y, z, height, seconds)` over an arc, like a piece picked up and set
down, and `isMoving()` says whether it arrived. `setEnabled(false)` keeps a node visible but lets the cursor pass through
it: pieces while it is the other player's turn. `setOutlined(true)` draws the hover outline by code: the piece picked.

Nodes with a `collider` and no role report clicks as `SceneEvent.Click`: choosing things by clicks needs no grabbable. For
choosing a place, a preview (a see-through copy shown on `SceneEvent.Hover`) reads better than an object dragged after the
cursor, and the place clicked is exactly the one under the cursor.

Behaviours exist for every station shown, also for people walking by. Put the scene in order from the state in
`onSceneReady` (the first time and after F3+T) and `onStateChanged`, not in `onOpen`.

`StationView.showCaption(text, seconds)` shows a short line at the edge of the screen while the local player works at the
station: whose turn it is, what just happened. 0 seconds keeps it until it is replaced; the scene's `captions` set its look.

## Effects

An effect is something the logic plays for everyone who sees the station (`station.effect(id, node, data)`). Register
what it does on the client:

```java
DiegesisClient.registerEffect(new ResourceLocation("mymod", "pop"), (view, node, data) -> {
    view.spawnParticles(DiegesisParticles.SPARK, node, data.getInt("count"), 0);
    view.playSound(SoundEvents.FIRECHARGE_USE, node, 0.3F, 1.5F);
});
```

The built-in particle styles are effects already: `station.effect(DiegesisParticles.STEAM, "flask")`.

An effect can also be a file, `assets/<namespace>/diegesis/effects/<path>.json`, made of the steps of
[scenes](scenes.md#effect-steps): particles, a sound, a flash of light, a shake, a while of a node's emitter, ripples. F3+T
reloads it, and a file wins over an effect registered in code under the same id, so a resource pack can restyle it.

```json
{"steps": [
  {"type": "particles", "style": "diegesis:dust", "count": 6, "spread": 1.0},
  {"type": "sound", "id": "minecraft:block.stone.hit", "volume": 0.3, "pitch": 1.2}]}
```

Data an effect gets from a reaction, and may get from the logic: `other` (the event's other node), `x`, `y`, `z` (its point in
scene pixels), `speed`, and `count`, `color` for particle styles played as effects.

`view.shareEffect(id, node, data)` plays an effect here at once and has the server show it to everyone near: for what only
this client knows, such as a knock in the scene's physics. Only the look; at most 10 a second reach the others, with up to
256 bytes of data. The others' behaviours hear it in `onEffect`, and server listeners in `StationListener.onEffect`.

## Particles

Scene particles are drawn inside the interface, in scene space: they turn with the station, stay put relative to the
block and are drawn over its see-through parts, sorted, writing no depth. Built-in styles (`DiegesisParticles`): `SPARK`, `BUBBLE`,
`BUBBLE_POP`, `DROPLET`, `SPLASH`, `STEAM`, `SMOKE`, `GLINT`, `DUST`, `FLAME`. `BUBBLE` is white, so a tint shows as it is: rising in a
liquid it dims with its depth, and on the surface it turns into `BUBBLE_POP`, which swells, pops and sends a small ring over the
liquid. `STEAM` is white too and fades in; `SMOKE` is the grey one. A style of your own uses any sprites of the particle atlas
(textures in `assets/<namespace>/textures/particle/` are `<namespace>:<name>`), with `fadeIn(part)` to appear softly and
`surface(styleId)` for what it becomes where it reaches a liquid's surface:

```java
DiegesisClient.registerParticle(new ResourceLocation("mymod", "ember"),
        ParticleStyle.builder(new ResourceLocation("minecraft", "flame"))
                .color(0xFFFF9040).size(0.2F, 0.4F).life(0.4F, 0.9F)
                .gravity(-8.0F).drag(0.9F).emissive().wobble(0.2F).build());
```

Builder: `color`, `size(min, max)` (pixels), `life(min, max)` (seconds), `gravity` (pixels/s^2; negative rises),
`drag` (0..1, the part of the speed lost per second), `emissive()`, `stretch()` (along the motion: sparks, drops),
`grow(factor)` (how many times larger by the end), `pop()` (vanishes at the end instead of fading), `wobble(pixels)`
(sideways wandering). Several sprites play as an animation over the particle's life.

A style can be a file too, `assets/<namespace>/diegesis/particles/<path>.json`, with the builder's fields: `sprites` (a list,
or `{"base": "minecraft:spark", "count": 8}` for `spark_0` .. `spark_7`), `color`, `size`, `life`, `gravity`, `drag`,
`emissive`, `stretch`, `grow`, `fade` (`false` pops), `wobble`, `fade_in`, `surface`. A file wins over a style registered in
code under the same id, and every style is an effect id as well.

```json
{"sprites": ["minecraft:flame"], "color": "#FFFF9040", "size": [0.2, 0.4], "life": [0.4, 0.9],
 "gravity": -8.0, "drag": 0.9, "emissive": true, "wobble": 0.2}
```

## Liquids

Water and lava have looks; describe any other liquid id your tanks hold:

```java
DiegesisClient.registerLiquid(new ResourceLocation("mymod", "ember_tincture"), LiquidLook.of(0xD0FF6020).glowing());
```

`LiquidLook(color, sprite, emissive)`: the ARGB colour (alpha = how thick the liquid is: `0xFF` hides what lies behind
it, `0xD0` reads as a solid colour with the bottom still showing, `0x60` stays clear; it deepens with the depth below the
surface), a block-atlas sprite drawn on the liquid (`LiquidLook.LIQUID_SPRITE`, the default of `LiquidLook.of`, shows the
colour as it is; `LiquidLook.WATER_SPRITE` is vanilla's, grey and see-through by itself, so it comes out dimmer and
thinner), glowing. `.thick(0..1)` sets how thick it moves: waves run slower, die sooner and catch less light, and the surface rocks
less (a soup 0.6). A mixture blends its parts by amount and drifts to a new colour;
`DiegesisClient.liquidColor(contents)` gives that colour for particles of the same tint.

## Sounds

`DiegesisSounds` holds quiet station sounds for client code: `PICK_UP`, `PUT_DOWN`, `GRAB`, `SET_DOWN`, `TOGGLE`,
`BUTTON`, `DETENT`, `POUR`, `SLOSH`, `BUBBLE`, `SPARK`, `STEAM` (`view.playSound(DiegesisSounds.POUR, "flask", 0.5F,
1.0F)`). The library plays the item, grab and control sounds by itself unless the scene sets
`"interface": {"sounds": false}`. A scene swaps, changes or silences each of them for its station, a node for itself
(`feedback.sounds`, see [scenes](scenes.md#feedback)), and a resource pack can replace any of them everywhere
(`assets/diegesis/sounds.json`).

## Other client calls

| Call | |
|---|---|
| `DiegesisClient.registerScene(id, scene)` | A scene built in code (a JSON file with the same id wins). |
| `DiegesisClient.registerModel(id)` | Load a block model outside `models/diegesis/` for scenes. |
| `DiegesisClient.setDebugView(on)` / `isDebugView()` | The debug view. |
| `DiegesisClient.isFocused()`, `focusedStation()`, `station(pos)` | What the local player works at; a shown station. |
| `DiegesisClient.arrival()` | How far the camera has come to the station worked at, 0 to 1: rising as it flies in, falling as it flies back after `onClose`. A book or a badge that hangs by the station can appear and go with it, in step with the camera. |
| `DiegesisClient.isSceneDrawn(pos)` | Whether the station's scene is drawn this frame. A block entity renderer the scene stands in for stops exactly then: a station is kept a little farther away than its scene is drawn. |

## Rendering and shaders

Scenes are drawn after the world's entities with the same render types as entities and item frames (block atlas,
cutout and translucent), so shader packs (Iris, Oculus) light them like other entities. Scene objects do not cast
shader shadows; the library draws its own soft shadows for carried things.
