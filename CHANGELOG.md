# Changelog

All notable changes to this mod are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-beta.19+1.20.1] - Unreleased
### Fixed
- With Fabulous graphics, slime, honey, ice and stained glass show again on a station's page (such as a recipe in [Hands-On Delight](https://www.curseforge.com/minecraft/mc-mods/hands-on-delight)'s book), and the rest of the page no longer jumps out four times larger over the station.
- A nether star or an enchanted item on a station's page no longer makes every page lose its smooth edges for the rest of the session, and it shows with its glint.
- The faces next to a block that leaves the camera's way at a station, or next to a station block, are no longer black: they get the light of the open air around them, at the station and while the block fades.
- Models for stations of mods that are not installed are no longer loaded: no more pages of missing texture warnings in the log.
- A station's screen stays where it is clicked when another mod's HUD drawing leaves its position changed.

## [1.0.0-beta.18+1.20.1] - 2026-11-23
### Changed
- Blocks in the camera's way at a station, such as a wall cabinet over a counter, now fade out smoothly as the camera flies in and fade back as it flies out, instead of vanishing and popping back.
### Added
- `clear_view.fade` (`ClearViewOptions.withFade`): how much of the camera's flight those blocks take to fade; `0` keeps the old instant switch.

## [1.0.0-beta.17.1+1.20.1] - 2026-11-23
### Fixed
- A shallow liquid seen from low down no longer shows dark wedges over its far half, and no dark lines run down the front of a pan or along a vessel's walls.
- The game no longer crashes when things are moved at a furnace or another menu station while Satchels (or another mod that adds slots to a menu when its screen opens) is installed.
- The faded items that show what goes into an empty slot no longer let the world behind the station show through: cobblestone in a furnace now shows the furnace's back wall behind it.
- A lectern's book (or another station's book) behind the station being used no longer shows through the blocks in front of it.

## [1.0.0-beta.17+1.20.1] - 2026-11-22
### Fixed
- Stations no longer show from far away when a zoom or FOV mod is installed: the station keeps its own field of view at any FOV setting.

## [1.0.0-beta.16+1.20.1] - 2026-11-22
### Changed
- Stations away from the player cost far less: scenes out of view are not drawn and move in coarser steps, and far liquids are drawn in less detail.
- A town full of cooking pots costs about half as much again: floating things are drawn with fewer corners, and far stations in view move in steps of a thirtieth of a second.
### Fixed
- Closing a screen another mod opened over a station, such as a [QuestLog](https://modrinth.com/mod/questlog) quest popup, no longer leaves the player unable to move or open the menu.

## [1.0.0-beta.15+1.20.1] - 2026-11-20
### Added
- `Diegesis.addListener`: a mod can watch any station, its own or another mod's, hear what players do there and refuse it.
- `DiegesisClient.addBehavior`: client code added to any station beside its own behaviour, for one station type or all of them.
- `StationView.isShiftDown()`: whether shift is held, as the station's own clicks see it.
- Effect files (`diegesis/effects/*.json`): particles, a sound, a flash of light, a shake, a node's emitter or ripples, played by id anywhere.
- Particle style files (`diegesis/particles/*.json`), with every field of the builder.
- Scene option `feedback.sounds`, and the same on a node: swap, change or silence each of the library's own sounds.
- Scene option `feedback.reactions`: play an effect when something happens in the scene, written in the scene file alone.
- `StationView.shareEffect` and shared reactions: an effect played on one client is seen by everyone near the station.
- `StationListener.onEffect` hears every effect played at a station.
- Scene events `Impact`, `Landed` and `FellOff` from the placing physics, `StreamLanded` where a stream of liquid lands, and `Submerge` when a carried thing goes under a liquid.
- Scene option `feedback.impact_min_speed`: how hard placed things must knock together to count.
- Scene option `clear_view.surface: "placing"` keeps the placing surfaces in view, so an open drawer no longer hides the block on top of it.
- Node option `feedback.marks` and the events `Exposed` and `ItemContact`: a carried thing or the held stack put into fire or liquid is reported.
- Scene options `placing.physics.push_out`, `wake` and `fall_ease`.
### Changed
- The network format changed: client and server need the same Diegesis.
- `SceneEvent` gains new events in this version: a `switch` over it needs a `default` branch.
- Placed things fall at real speed: `placing.physics.gravity` is 157 by default, `200` gives the old fall.
- Heaps of placed things come to rest and stay there: no more jumping, spinning or creeping, and things put inside each other glide apart.
- Putting a thing down or taking one wakes only what it touches (`wake: near`); `wake: all` keeps the old way.
- A pile rides its base when the hand moves the base; when the base only settles or is knocked, what stands on it comes to
  rest by the physics instead of being carried into its neighbours.
### Fixed
- A block that another mod opens its own screen for (Amendments' lectern) no longer flies the camera back behind that screen.

## [1.0.0-beta.14+1.20.1] - 2026-11-20
### Added
- Orientation `PLAYER_FREE` and `StationView.turn()`: a station's scene faces the player at their own angle round the block.
- Slot options `ghost`, `ghost_alpha`, `ghost_period` and `NodeView.setGhostItems`: an empty slot shows what goes into it, see-through.
- Free placing option `right_puts_one` (on by default): a right click puts one of the stack in the hand down, as in a chest.
- Surface option `fill: "middle"` sets out shift-clicked things from the middle of the surface, as on a plate.
- Surface option `flat_rest: "lie"` lays flat things on their back, for drawers and boxes seen from above.
- Scene option `hud.keep_vignette`: `false` lets the vignette and other whole-screen overlays go with the HUD.
- New `stack_*`, `turn_detent*`, `pick_min`, `grip_blend` and `physics.*` placing options bring back the old behaviour where wanted.
### Changed
- Free placing is calmer: piles move as one, things stack where the cursor points and never pass through each other.
- A placed item is picked by its pixels, stays under the cursor when dragged and goes back where it stood when put again.
- With physics, flat things turn to any angle and rest as they fall: standing, lying flat or leaning on a neighbour.
- With physics, a put-down sounds as it lands, a shadow marks where it will land and other players see a long drag as it goes.
- An item put on another shrinks at most a quarter to fit, and a turn by hand let go of near square settles onto it.
- `ItemPose` has a `wanted` size. The network format of poses changed: client and server need the same Diegesis.
- On Forge, the vignette, frost and other mods' whole-screen overlays no longer vanish at a station.
### Fixed
- Tools that take a right click on a block, such as [Create Aeronautics](https://modrinth.com/mod/create-aeronautics)' honey glue or a wrench, now work on station blocks.
- A menu station used from the edge of reach no longer opens the block's vanilla screen.
- A menu station left and used again at once no longer opens with its menu shut on the server.
- A block taken out of the camera's way takes along what stands on it and what its block entity draws.
- `DiegesisClient.tinted` fades items drawn as entity models too, so a ghost banner is see-through.
- A door or lid lit as a block no longer flickers as the camera moves, and a swinging door is lit by the room it faces.
- With physics, placed things no longer jump back, jitter in stacks or start moving again when the station changes.
- With physics, a box pushed part way over an open edge sits flat until it passes its balance point.

## [1.0.0-beta.13+1.20.1] - 2026-11-18
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
- The game no longer crashes on start with [AsyncParticles](https://modrinth.com/mod/asyncparticles), and its GPU particles fade in front of a station.
- Claim mods such as [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims) and spawn protection keep a station closed to players who may not use the block.
- No sliver of the hand stays in view at a station under an [Iris](https://modrinth.com/mod/iris) or [Oculus](https://modrinth.com/mod/oculus) shader pack.

Thanks to LeDok for the AsyncParticles, claim and shader pack fixes ([#5](https://github.com/Pocky-l/diegesis/pull/5),
[#6](https://github.com/Pocky-l/diegesis/pull/6), [#7](https://github.com/Pocky-l/diegesis/pull/7), [#8](https://github.com/Pocky-l/diegesis/pull/8)).

## [1.0.0-beta.12+1.20.1] - 2026-11-17
### Added
- `StationView.setPlacingReachable`: placed items stay in sight but out of reach, behind the shut glass door of an oven.
### Fixed
- A block beside the station no longer disappears when the camera only looks past its corner: `clear_view` leaves out
  only blocks that stand in the way.
- A station straight above or below (a fridge on its freezer) is reached from the top or bottom edge of the screen, by
  the cursor or by W and S, no longer from the left edge.
- A neighbour on the side of the inventory drawer is reached by its key, where the cursor would be over the inventory.
- Items that come into a `placing` without a place (a shift-click, a hopper) stand in the middle of a row first, where
  they are seen.

## [1.0.0-beta.11+1.20.1] - 2026-11-16
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

## [1.0.0-beta.10+1.20.1] - 2026-11-15
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
- Opening a station with a named entity or another player in the camera's way crashed with Iris on Sodium 0.5 (#3). Name
  labels, lines and glints of things in the way stay solid now; their bodies still fade.
- Smooth painters that draw text (Hands-On Delight's recipe book) are drawn smooth with Iris on Sodium too; before, they fell
  back to the scene's own drawing.
- With a shader pack, things standing in the camera's way cast their whole shadow.
- Where a graphics driver refuses the targets smooth painters are drawn into, they are still drawn over the finished frame,
  only without smoothing; before, they went back into the scene, where a block beside the station could cover them.
- A station whose open check fails no longer crashes the game when its block is used: the block keeps its own use and
  the error is logged once.

## [1.0.0-beta.9+1.20.1] - 2026-11-14
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
- A station that hides its block swaps it for its scene in one frame: the block's chunk is rebuilt at once, as for a
  block a player places, instead of a few frames later, so the block no longer blinks or shows twice as a player comes
  and goes.

## [1.0.0-beta.8+1.20.1] - 2026-11-13
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
- With [Sodium](https://modrinth.com/mod/sodium) 0.5.11 or older, or [Rubidium](https://modrinth.com/mod/rubidium),
  opening a station while smoke, flames or other particles are in view, such as by a lit stove or a campfire, no longer
  crashes the game with "does not implement interface VertexBufferWriter". Particles in the way of the camera still
  fade, now with any renderer. With Sodium 0.5.3 or older and Rubidium the same crash came from an item or a mob
  standing in the way: there they now stay solid.
- Using a station again while the camera flies back from it no longer jerks the view round.
- A stack whose data another mod changes all the time, such as food a spoilage mod ages every second, no longer
  flies into the inventory with "+N" again and again while you work at a station.
- With [Real Camera](https://www.curseforge.com/minecraft/mc-mods/real-camera) the camera keeps the station's view: Real
  Camera rests while you work at a station and takes the camera back when you leave. Before, the view ended at odd
  angles with the work off screen.
- With [Leawind's Third Person](https://www.curseforge.com/minecraft/mc-mods/leawind-third-person) the station's view
  wins too, and the camera flies in from the third-person view and back to it.

## [1.0.0-beta.7+1.20.1] - 2026-11-11
### Added
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

## [1.0.0-beta.6+1.20.1] - 2026-11-10
### Fixed
- Wall cabinets, a low ceiling or a wall over a station no longer block the camera: the blocks in its way are hidden
  while you work there and come back when you leave. Works with Embeddium and Sodium. Scene option `clear_view.blocks`.
- Joining a world on Fabric no longer crashes.
- Armourer's Workshop skins no longer turn invisible after work at a station.
- On Fabric with Sodium 0.5, a tossed skillet no longer leaves a second, still pan behind.

## [1.0.0-beta.5]
### Fixed
- With Blur (Forge), ReBlured or Blur+ on Minecraft 1.20.1, the whole station was blurred while the player worked at
  it, and only the slots stayed sharp. Station screens are now on their exclusion lists, so stations stay sharp and
  other screens keep their blur.

## [1.0.0-beta.4]
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

## [1.0.0-beta.3] - 2026-11-08
### Added
- Stations standing next to each other: the cursor at the edge of the screen, or A and D, moves the camera over to the
  next one without leaving. Scene option `camera.neighbors`.
- What the player gets at a station flies into the inventory, and the slot bounces as it lands. Scene option `gains`.
- A right click with a stack that nothing in the scene takes puts it back into the slot it came from. Scene option
  `put_away`.
- `compact` inventory drawers show only the stacks a station can use.
- Calls for stations that use the stack on the cursor as a tool: `StationView.takeFromInventory`, `setCarriedHidden`,
  `setInventoryFilter`, `openedByUse`, `isInUse`, `holdStroke`, `putAwayCarried`, plus
  `StationBehavior.onInventoryShiftClick` and `DiegesisClient.goingOver`.

### Changed
- The server tells joining players which stations it has, so on a server without an addon its blocks keep their own
  menus. Client and server need the same version of Diegesis.
- A stack taken from the inventory onto the cursor goes back to its slot when the player leaves the station.

### Fixed
- Block items, such as a carrot or a mushroom, did not fade when drawn through `DiegesisClient.tinted`.
- A move the server turned down could stay on show on the client for slots bound to a block's own inventory.
- An action that arrived in the same tick as its block was broken could put items into a block that was gone.

## [1.0.0-beta.2] - 2026-11-08
### Fixed
- The game crashed at startup with the Forge version.

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
