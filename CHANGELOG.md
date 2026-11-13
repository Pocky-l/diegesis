# Changelog

All notable changes to this mod are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-beta.8+1.21.1] - Unreleased
### Fixed
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
