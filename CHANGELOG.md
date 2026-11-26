# Changelog

All notable changes to this mod are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-beta.19.4+1.21.11] - 2026-11-25
### Fixed
- The slots over the hotbar at a station no longer keep the order of the last visit: opening a station again showed old places, other items among them, in a shuffled order. Each opening now starts from the inventory as it is, and the order holds while you work there.
- While you work at a station, other mods that look at the blocks around it once they are drawn (coloured-light mods looking for lamps, for one) see the station block and the blocks in the camera's way as they are: until now these looked like air to them, so a lit furnace or a lamp in the way lost its light.

## [1.0.0-beta.19.3+1.21.11] - 2026-11-25
### Added
- Thick masses in tanks (`tank.mass`, `tank.wall_gap`): a porridge, a dough or whipped butter that a whisk furrows and pushes aside, with what lies in it held there.
- `NodeView.setFloatingClearance`: how far floating things keep the others off, as a share of their size.
- Scene `tank.inset`: how far a liquid is drawn inside its vessel.

### Fixed
- A liquid as wide as the inside of its vessel no longer shows through the vessel's walls.
- Items of blocks that are drawn only by their block entity (Vinery's wine bottles) are no longer invisible when placed in a scene: they show their icon.

## [1.0.0-beta.19.1+1.21.11] - 2026-11-25
### Added
- `StationType.Builder.blocks(Supplier<Collection<Block>>)`: one station for a set of blocks looked up when first needed, such as every block of one kind another mod adds.

### Fixed
- A crash or a moved HUD when a station opened or closed while another mod's HUD left a pose pushed and popped it later.
- A mod that moves its part of the HUD and puts it back after drawing no longer shifts a station's screen, such as its slot row off the bottom edge, while the HUD is hidden at the station.

## [1.0.0-beta.19+1.21.11] - 2026-11-24
### Fixed
- Blocks beside a station block, or beside one that moves out of the camera's way, no longer show black faces at the station and while the camera flies.
- Block models made for mods that are not installed are no longer loaded, which takes their warnings out of the log.
- Another mod's part of the HUD that leaves its drawing position shifted no longer moves a station's hotbar and slot rows away from where they are clicked.

## [1.0.0-beta.18+1.21.11] - 2026-11-23
### Changed
- Blocks in the camera's way at a station, such as a wall cabinet over a counter, now fade out smoothly as the camera flies in and fade back as it flies out, instead of vanishing and popping back.
### Added
- `clear_view.fade` (`ClearViewOptions.withFade`): how much of the camera's flight those blocks take to fade; `0` keeps the old instant switch.

## [1.0.0-beta.17.2+1.21.11] - 2026-11-23
### Fixed
- An item with a special model, such as a banner or a shield, stands in a slot as an item frame shows it again, instead of lying
  upside down as a flat icon.

## [1.0.0-beta.17.1+1.21.11] - 2026-11-23
### Added
- Minecraft 1.21.11, on NeoForge and Fabric: the library as 1.0.0-beta.17.1 has it on 1.21.1 and 26.3, with the same scenes, stations and network format.
### Changed
- For addon authors on 1.21.11: ids are `Identifier`; `ScreenOverlay.render` takes `GuiGraphics` and `MenuAccess.click` a `ClickType`, as on 1.21.1.
- For addon authors on 1.21.11: node painters and `DiegesisClient.items` / `tinted` take `SceneBuffers`, as on 26.3.
- For addon authors on 1.21.11: `StationBehavior.onKey` gets GLFW key codes, as on 1.21.1 (compare with `InputConstants.KEY_*` to work on every version).
### Fixed
- The game no longer crashes when things are moved at a furnace or another menu station while Satchels (or another mod that adds slots to a menu when its screen opens) is installed.
- A lectern's book (or another station's book) behind the station being used no longer shows through the blocks in front of it.
- With a mod that tilts the camera, leaving a station no longer levels the view and then jerks it back: the camera flies back into the tilted view, and flies out of it on the way in.
### Not on 1.21.11 yet
- Smooth-pass supersampling, and fading of entities and particles in front of a station (ClearView).
- Other mods' HUD elements sliding away on Fabric.
- The outline of a part seen only from the station.
- Sodium and AsyncParticles compatibility.

## [1.0.0-beta.17.1+26.3] - Unreleased
### Added
- `Diegesis.addListener` and `DiegesisClient.addBehavior`: a mod can watch any station, refuse what players do there and add client code beside the station's own.
- Effect and particle style files (`diegesis/effects/*.json`, `diegesis/particles/*.json`), played by id anywhere.
- Scene option `feedback`: swap or silence the library's sounds, play effects when something happens in the scene, and mark nodes as fire or liquid for carried things.
- `StationView.shareEffect` and shared reactions: an effect played on one client is seen by everyone near the station.
- Scene events from the placing physics, landing streams of liquid and things held into liquids or fire; a `switch` over `SceneEvent` needs a `default` branch.
- Orientation `PLAYER_FREE` and `StationView.turn()`: a station's scene faces the player at their own angle round the block.
- Slot options `ghost`, `ghost_alpha`, `ghost_period` and `NodeView.setGhostItems`: an empty slot shows what goes into it, see-through.
- Free placing: `right_puts_one`, surface options `fill: "middle"` and `flat_rest: "lie"`, and `stack_*`, `turn_detent*`, `pick_min`, `grip_blend`, `physics.*` for the old behaviour.
- Scene options `clear_view.surface: "placing"` and `hud.keep_vignette`.
### Changed
- The network format changed: client and server need the same Diegesis.
- Free placing is calmer: piles move as one, things stack where the cursor points and never pass through each other.
- With physics, heaps of placed things come to rest and stay there, flat things rest at any angle, and things fall at real speed (`placing.physics.gravity: 200` gives the old fall).
- A placed item is picked by its pixels and stays under the cursor when dragged, and a shadow marks where it will land.
- The vignette, frost, a carved pumpkin's view and the portal's swirl no longer vanish at a station.
- Stations away from the player cost far less: scenes out of view are not drawn and move in coarser steps, and far liquids and floating things are drawn in less detail.
### Fixed
- Tools that take a right click on a block, such as a wrench, now work on station blocks.
- A block that another mod opens its own screen for no longer flies the camera back behind that screen.
- A menu station used from the edge of reach no longer opens the block's vanilla screen.
- A block taken out of the camera's way takes along what its block entity draws.
- `DiegesisClient.tinted` fades items drawn as entity models too, so a ghost banner is see-through.
- A door or lid lit as a block no longer flickers as the camera moves, and a swinging door is lit by the room it faces.
- A shallow liquid seen from low down no longer shows dark wedges over its far half, and no dark lines run down the front of a pan or along a vessel's walls.
- Many smaller fixes of free placing with physics.
### Not on 26.3 yet
- NeoForge.
- Smooth-pass supersampling, fading of entities and particles in front of a station (ClearView), other mods' HUD elements sliding away, the outline of a part seen only from the station, and Sodium and AsyncParticles compatibility.

## [1.0.0-beta.13.2+26.3] - 2026-11-22
### Added
- Gamepad support with [Controlify](https://modrinth.com/mod/controlify): its virtual cursor works at a station and snaps to the inventory slots and the station's parts. Contributed by rubenbarreroa ([#12](https://github.com/Pocky-l/diegesis/pull/12)).
### Fixed
- Closing a screen another mod opened over a station, such as a [QuestLog](https://modrinth.com/mod/questlog) quest popup, no longer leaves the player unable to move or open the menu.
- Items whose model another mod builds with an empty part no longer fail with an error when placed in a scene.

## [1.0.0-beta.13.1+26.3] - 2026-11-21
### Fixed
- Stations no longer show from far away with ZoomX or OnlyZoom+ installed: the station keeps its own field of view at any FOV setting.
- Items in a scene cast shadows of their own shape again, no longer dark squares.

## [1.0.0-beta.13+26.3] - 2026-11-20
### Added
- Minecraft 26.3, on Fabric. Ported by jayvie2007 ([#10](https://github.com/Pocky-l/diegesis/pull/10)).
### Not on 26.3 yet
- NeoForge.
- Smooth-pass supersampling, fading of entities and particles in front of a station (ClearView), other mods' HUD elements sliding away, the outline of a part seen only from the station, and Sodium and AsyncParticles compatibility.

## [1.0.0-beta.13+1.21.1] - 2026-11-18
### Added
- Slot option `idle_highlight`: the slot's square shows faintly while the cursor is elsewhere.
- `NodeView.setTooltipItem`: a node that draws an item itself shows its tooltip and takes recipe viewers' keys, as a slot does.
- `DiegesisClient.items(buffers)`: buffers for an item a node painter draws, with depth and its enchantment glint.
- Menu stations go over to neighbouring stations: the movement keys and the screen edge reach a furnace beside a crafting table.
- Interface option `carry_count_facing`: which way the count of the stack in the hand faces.
### Changed
- The square under the cursor on an `upright` slot stands in the item's plane, behind it, no longer flat at its foot.
- The count of the stack in the hand is turned to the camera (`"carry_count_facing": "up"` keeps the old look).
### Fixed
- Enchanted items in a scene show their glint again.
- Stack counts drawn on top are no longer cut by a block standing in front of them.
- A multiblock station's scene shown at rest is drawn once, at its main block, no longer again at each of its other blocks.
- A block model lit as a block takes its light from the world block it stands in, so a multiblock's scene is shaded block by block.
- A multiblock's scene stays until all its blocks are back, and its own blocks are never cleared from the camera's way: no holes as the camera flies in or out.
- Stirring a shallow liquid no longer flattens its waves as if under a lid.

## [1.0.0-beta.12+1.21.1] - 2026-11-17
### Added
- `StationView.setPlacingReachable`: placed items stay in sight but out of reach, behind the shut glass door of an oven.
### Fixed
- A block beside the station no longer disappears when the camera only looks past its corner: `clear_view` leaves out
  only blocks that stand in the way.
- The game no longer crashes on start with [AsyncParticles](https://modrinth.com/mod/asyncparticles), and particles in
  front of a station fade with its GPU particles on too.
- Claim mods such as [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims) and
  spawn protection keep a station closed to players who may not use the block.
- No sliver of the hand stays in view at a station under an [Iris](https://modrinth.com/mod/iris) shader pack.
- A station straight above or below (a fridge on its freezer) is reached from the top or bottom edge of the screen, by
  the cursor or by W and S, no longer from the left edge.
- A neighbour on the side of the inventory drawer is reached by its key, where the cursor would be over the inventory.
- Items that come into a `placing` without a place (a shift-click, a hopper) stand in the middle of a row first, where
  they are seen.

Thanks to LeDok for the fixes for AsyncParticles, claim mods and Iris ([#5](https://github.com/Pocky-l/diegesis/pull/5), [#6](https://github.com/Pocky-l/diegesis/pull/6),
[#7](https://github.com/Pocky-l/diegesis/pull/7), [#8](https://github.com/Pocky-l/diegesis/pull/8)).

## [1.0.0-beta.11+1.21.1] - 2026-11-16
### Added
- `placing.physics`: placed items have weight. They fall, tip over, push each other and drop off open edges into the
  player's inventory.
- With placing physics, items dragged or turned by hand move as solid things: a stone block shoves an apple aside, an apple
  hardly moves the block.
- `placing.counts`: the count of a placed stack written beside it (`always`, `hover` or `never`).
- Station orientation `BLOCK_OPPOSITE`, for blocks whose facing names their back.
- `StationView.setSurfaceTransform`, `setPlacingVisible` and `setSurfaceVisible`: shelves move and hide with a drawer or a door.
- `StationView.mirrorCamera(x)`: the station's camera mirrored, for blocks that come in a left and a right hand.
- `StationView.hasRoomFor(stack)`. Items brought in without a place stand on the first free spot of a surface.
- `placing.frame_pose` and `placing.hover_lift`: 3D items stand upright as modelled, and a hovered item no longer jumps up.
- `StationBehavior.onKey` and `onChar`: a station takes keys and typed characters, for a search field.
- `MenuBinding.Builder.alsoAccepts(names)`: a menu station also takes another mod's menu of the same slots, such as
  [Visual Workbench](https://modrinth.com/mod/visual-workbench)'s crafting table.
- `count_facing: camera` for slots and outputs, `count_facing` for outputs.
- [Mouse Tweaks](https://modrinth.com/mod/mouse-tweaks) works at every station as its config sets it.
- The mouse wheel over a slot with a stack in the hand puts one in or takes one back; the middle drag shares a stack over
  the slots it crosses.

### Changed
- Lights in a scene brighten the models around them and flicker slowly and smoothly.

### Fixed
- At a large render distance the recipe book and the cooking badge of
  [Hands-On Delight](https://www.curseforge.com/minecraft/mc-mods/hands-on-delight) vanished and clicks could miss.
- A scene that stands in for its block no longer blinks when the block comes back
  ([Hands-On Delight #30](https://github.com/Pocky-l/handson-delight/issues/30)).
- A block a scene stands in for no longer hides the faces of the blocks around it.
- Counts turned to the camera are no longer cut by a chest, glass or particles next to the station.
- A resource pack that retextures white concrete no longer shows through the slot highlights.
- The stack in the hand stays at the front of an upright station, and no longer fills the screen for a frame when taken
  from the drawer.
- Many smaller fixes with placing physics.

## [1.0.0-beta.10+1.21.1] - 2026-11-15
### Added
- Node option `takes_items`: while a stack is on the cursor the node is hovered too and its click goes to `onItemClick`, for
  places a station lays things on by code.
- `StationBehavior.onScroll(view, node, amount)`: the mouse wheel over the scene goes to the station's code first.
- `StationView.moveCamera(x, y, z)`: code moves the station's whole camera, which glides there, for work at another height.
- `StationType.Builder.hidesBlocks(Span)`: a multiblock station hides its other blocks with the main one while it is
  in use, so its scene can lift a tower apart.
- `NodeView.setOutlineSeenOnly(true)`: the hover outline goes round the part of the node the player sees, not its whole
  shape through what stands in front of it (one basket of a stack). Under a shader pack the whole shape stays outlined.
- Menu stations (`StationType.Builder.menu(MenuBinding)`, `Authority.MENU`): a station that works through the block's own
  vanilla menu, opened on the server by the block's normal use and never shown. Its slots show the menu's slots and every
  click is the one the vanilla screen sends, so it moves items on any server, with or without Diegesis there.
  `StationView.menu()` (`MenuAccess`), `StationBehavior.onMenuOpened` and `onMenuRefused`.
- At a menu station the keys of a vanilla container screen work over a slot: 1-9 and the off-hand key swap, Q throws one
  (Ctrl+Q the stack), the pick button copies a stack in creative mode, and a click past the block throws the stack in the
  hand.
- `DiegesisClient.toScreen(world)` and `stationScreenClass()`, for laying recipe viewers' overlays round a station.
- A drag that spreads the stack in the hand over several slots shows, as a vanilla screen does, what each slot will hold
  and how many stay in the hand.
- `DiegesisClient.registerChildScreen`: a screen such as a recipe viewer's page may open over a station without ending it;
  the player is back at the station when it returns. `DiegesisClient.hoveredStack()` (the item under the cursor at a station)
  and `isStationScreen(screen)` for recipe viewers' keys.
- At a menu station the mouse wheel moves one item at a time, as Mouse Tweaks does: down over a stack sends one to the other
  side (inventory and station), up brings one more of the same into it.
- `MenuAccess.placeRecipe(recipe, all)`: lays a recipe into a menu station the way the vanilla recipe book does.
- Drawer option `layout: hotbar`: the hotbar stays where the HUD has it and takes clicks, the off hand beside it at a
  menu station, and over it a square for each other stack the station can use, in the order they had when the station
  opened, with one empty square always: nothing moves while the player works.
- Drawer option `carry_size`: a stack taken from the inventory drawer is carried over the scene at that size, as big as the
  station's own items, instead of the size of the last stack carried.
- Slot option `count_facing: front`: the count stands at the item's foot facing the block's front, readable when the camera
  looks into a hollow of a wall rather than down onto a table.
- Camera option `interactive_at`: clicks count from that share of the flight in (default 1: once the camera has arrived).

### Fixed
- With InvMove, the keys that go over to a neighbouring station also walked the player away from it:
  the player stands still while working at a station.
- Items in a scene keep their depth when tipped: a bucket being poured, or anything leaning, no longer shows dark lines
  across its face where its one-pixel sides were drawn over it.
- The shadow of a carried item is even: it no longer shows darker stripes and bands where the item's front and back overlapped.
- Where a graphics driver refuses the targets smooth painters are drawn into, they are still drawn over the finished frame,
  only without smoothing; before, they went back into the scene, where a block beside the station could cover them.
- A station whose open check fails no longer crashes the game when its block is used: the block keeps its own use and
  the error is logged once.
- Opening a station with a named entity or another player in the camera's way could crash the game with Iris on Sodium (#3). Name
  labels, lines and glints of things in the way stay solid now; their bodies still fade.
- Smooth painters that draw text (Hands-On Delight's recipe book) are drawn smooth with Iris on Sodium too; before, they fell
  back to the scene's own drawing.
- With a shader pack, things standing in the camera's way cast their whole shadow.

## [1.0.0-beta.9+1.21.1] - 2026-11-14
### Added
- `NodeView.setFoam(coverage, color)`: foam on a tank's liquid, clinging to the walls first and lying in patches further in,
  growing and settling over a second: a beer's head, scum on a stock.
- `NodeView.holdLevel(seconds, over)`: a tank's liquid keeps the level it shows for a while and then rises or falls to
  what the tank holds over the given time, so that a bucket brought over and tipped fills it as its stream runs.
- `StationBehavior.holdsBlock(view)`: a station that hides its block keeps it hidden after the last player left for as
  long as this says, so that its scene can finish closing, as a lid falling shut.
- `NodeView.showLiquid(liquid, fraction)`: a tank shows a liquid at a level set by code, whatever the station's tank
  holds: a mug filled at a tap.
- A node drawn over everything (`setOnTop`) takes its tank's liquid along, so that a mug held low before a keg's tap is
  not cut by the floor.
  A stream poured into such a node is drawn with it, so that it runs into the vessel instead of ending at its rim.
- `StationView.isBlockHidden()`: whether a station's block is left out of the world in this frame, for the scene's copy of
  it to show exactly then.
- Visual option `lit_as_block` (`Visual.asLitAsBlock()`): a block model lit as the world lights the station's block,
  faces shaded as a block's, the light around it and smooth lighting's corner shadows, so that a scene's copy of a block
  it hides no longer looks lit up beside the block.

### Changed
- Splashes and drops are as big as vanilla's and splashes last longer, so that they are seen from a station's camera.
  Splashes are vanilla's in white, so that they take a liquid's colour: in honey they were black.
- A full tank shows its liquid's surface, foam and patches as a fuller one does: at 100% the plain top of the box stood in
  for it.
- Ripples show on a cloudy or foamy liquid too (`setMottle`, `setFoam`, trails): their slopes catch the light as on a
  clear one, where before they only moved its surface unseen.

### Fixed
- With [Sodium](https://modrinth.com/mod/sodium) 0.5.11, opening a station while smoke, flames or other particles are in
  view, such as by a lit stove or a campfire, could crash the game with "does not implement interface
  VertexBufferWriter". Particles in the way of the camera now fade through the particle itself, with any renderer.
- A station that hides its block swaps it for its scene in one frame: the block's chunk is rebuilt at once, as for a
  block a player places, instead of a few frames later, so the block no longer blinks or shows twice as a player comes
  and goes.

## [1.0.0-beta.8+1.21.1] - 2026-11-13
### Added
- `DiegesisClient.arrival()`: how far the camera has come to the station worked at, so that what hangs by a station can
  appear and go in step with the camera.
- `DiegesisClient.isSceneDrawn(pos)`: whether a station's scene is drawn in this frame, for a block entity renderer
  that the scene stands in for.
- Camera option `keep_width`, 16:9 by default: a window narrower than that, such as 4:3 or a game snapped to half a
  screen, sees the whole width of the station's shot with more above and below, instead of losing its sides.

### Changed
- The camera flies to a station and back faster: 0.4 seconds instead of 0.6, and 0.45 instead of 0.65 over to a
  neighbour. It sets off at once and lands softly, on a slight arc, and the zoom, the inventory and the first-person
  hand go in step with it: the hand goes down and comes back up instead of vanishing and popping back. Scenes that set
  their own `transition` keep it.

### Fixed
- Using a station again while the camera flies back from it no longer jerks the view round.
- A stack whose data another mod changes all the time, such as food a spoilage mod ages every second, no longer
  flies into the inventory with "+N" again and again while you work at a station.
- With [Real Camera](https://www.curseforge.com/minecraft/mc-mods/real-camera) the camera keeps the station's view: Real
  Camera rests while you work at a station and takes the camera back when you leave. Before, the view ended at odd
  angles with the work off screen.
- With [Leawind's Third Person](https://www.curseforge.com/minecraft/mc-mods/leawind-third-person) the station's view
  wins too, and the camera flies in from the third-person view and back to it.
- Stations on structures that [Sable](https://modrinth.com/mod/sable) moves now open on Fabric too, such as builds of
  [Sable Beyond](https://modrinth.com/mod/sable_beyond): before, such a block kept its own menu there.

## [1.0.0-beta.7+1.21.1] - 2026-11-11
### Added
- Stations on structures that [Sable](https://modrinth.com/mod/sable) moves, such as the ships of
  [Create Aeronautics](https://modrinth.com/mod/create-aeronautics), on NeoForge: using the block opens the station, and the scene, the camera, clicks and
  sounds stay with the ship as it flies and turns. Before, such a block kept its own menu.
- `StationView.pourStream`: a stream of liquid between two points of a scene, run by code, that looks and lands like the
  stream of a vessel poured by hand. For a bottle an animation tips over a pot.
- `NodeView.setOnTop`: a node drawn over everything while the player works at the station, as the recipe book of a
  station is, so that a block or a neighbour never hides it.
- The HUD slides away when you start to work at a station and comes back when you leave: the hotbar down, the chat to
  the left, every other part and other mods' overlays to the edge of the screen nearest to them. Scene option `hud`
  (`slide`, `seconds`); `"slide": false` hides it at once as before.

### Fixed
- Smoke and flames of a campfire under a station no longer pop in at the edge of the clear view: world particles shrink
  as they fade there, so the ones drawn without transparency come back smoothly too.
- Items in a scene take their tint: an item meant to be see-through, such as a knife or a bowl offered from the
  inventory, is see-through instead of solid.

## [1.0.0-beta.6+1.21.1] - 2026-11-10
### Fixed
- Wall cabinets, a low ceiling or a wall over a station no longer block the camera: the blocks in its way are hidden
  while you work there and come back when you leave. Works with Sodium. Scene option `clear_view.blocks`.
- Joining a world on Fabric no longer crashes.
- Armourer's Workshop skins no longer turn invisible after work at a station.

## [1.0.0-beta.4+1.21.1]
### Added
- `NodePainter.smooth()`: a painter hanging in front of the scene, such as a book before the camera, is drawn
  supersampled over the frame while the player works at the station, so its edges have no steps.
- `StationView.screenOn(x, y, height)`: where a point of the player's screen lies in the scene, for things that come
  from the player's side, such as food from the inventory at the bottom of the screen.
- Gains show how many came as "+N": it rises from the slot as the stack lands, or stands beside the stack by the
  drawer. `"count": false` in `gains` turns it off. A stack by the drawer now shrinks away instead of vanishing.

### Fixed
- `GLINT` was green whatever colour it was given: it used the game's bone meal sparkle. It now twinkles out as a
  firework spark and takes the colour asked for.
- A or D pressed while the camera flies over to a neighbour was lost. It now turns the camera on to the next station
  at once, so two quick presses go two stations on. A held key no longer repeats, and a key already held when a
  station opens does nothing.

## [1.0.0-beta.3+1.21.1] - 2026-11-09
### Added
- Minecraft 1.21.1, on NeoForge and Fabric. Everything 1.0.0-beta.3 does on 1.20.1 works the same: scenes, options and API,
  neighbouring stations, gains and put-away included.

### Changed
- For mod authors: `StationState.save(registries)` and `load(tag, registries)` take the registries, as block entities do
  on 1.21.1.

## [1.0.0-beta.1] - 2026-11-07

First beta. The API may still change before 1.0.0.

### Added
- Stations. Using the block moves the camera to it, and the player works with the block's parts instead of a menu.
- Scenes in JSON with Blockbench models, items and blocks. F3+T reloads them, and a mistyped field is reported.
- Item slots that work like a vanilla container, with shift-click, double click and drag to spread.
- Objects the player picks up and carries, levers, buttons, knobs and sliders. Ready-made controls take one line in a
  scene.
- Liquids in tanks that sway and ripple, and pouring from one vessel into another.
- Particles, light and sounds in scenes.
- Stations on blocks from other mods, using the block's own inventory.
- Seats, stations over several blocks, client-only stations and server stations that everyone nearby sees.
- A guide, a cookbook and a JSON schema for scene files.
- Forge and Fabric for Minecraft 1.20.1.
