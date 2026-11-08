# Changelog

All notable changes to this mod are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
