# Changelog

All notable changes to this mod are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-beta.8+1.20.1] - Unreleased
### Added
- `NodeView.setFoam(coverage, color)`: foam on a tank's liquid, clinging to the walls first and lying in patches further in,
  growing and settling over a second: a beer's head, scum on a stock.
- `NodeView.holdLevel(seconds, over)`: a tank's liquid keeps the level it shows for a while and then rises or falls to
  what the tank holds over the given time, so that a bucket brought over and tipped fills it as its stream runs.
- `StationBehavior.holdsBlock(view)`: a station that hides its block keeps it hidden after the last player left for as
  long as this says, so that its scene can finish closing, as a lid falling shut.
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
