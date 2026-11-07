# Changelog

All notable changes to this mod are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-beta.1] - Unreleased

### Added
- Stations: blocks whose use flies the camera to the block instead of opening a GUI.
- Scenes in JSON files with Blockbench models, items and blocks; reloaded with F3+T.
- Item slots used like a vanilla container: the inventory panel under the block, left and right clicks, shift-click,
  double click and drag-spreading.
- Scenes can lay the player's inventory out in the world (floating see-through slots, or on a surface) instead of the
  screen panel, always or only after Tab (and while an item is on the cursor), coming up row by row.
- Interface options per scene: hide the HUD, the chat and the player's own body; counts, shadows, highlight colour,
  how the held stack moves, sounds, the inventory panel.
- An outline round the silhouette of the usable object under the cursor while the hand is empty (colour and switch per
  scene).
- Stations on other mods' blocks: slot groups bound to the block's own inventory (`SlotStorage`), `hidesBlock()` so the
  scene can stand in for the block's model, `onTaken` for what a result slot does, `openWhen` with the block's position.
- `drop_zone` nodes that take stacks into a whole slot group, `counts` on slots, `always_open` drawers,
  `NodeView.setLiquidColor`.
- `clear_view`: entities and world particles between the camera and the station dissolve softly while a player works there.
- `idleLogic()` shows the real state of a foreign block in idle scenes, with no traffic.
- Thick liquids (`LiquidLook.thick`), white steam and bubbles, bubbles that swell on the surface, pop and ring the liquid, particles dimmed
  by their depth, `spawnOnLiquid`, `ParticleStyle.fadeIn` and `surface`.
- `StationView.loopSound`: sounds that go on, gliding to the volume and pitch they are given.
- Uneven liquids (`setMottle`), liquids seething along the walls (`setEdgeBoil`, `spawnAtLiquidEdge`), trails on liquids (`leaveWake`, `setWakeLook`, `setFloatingWake`).
- Things floating on liquids (`NodeView.setFloating`, `Floater` with tintable sprites, `setCurrent`), see-through and glowing code shapes.
- `ScreenOverlay` and `StationView.showOverlay`: something flat laid over the scene, a map or a letter.
- `NodePainter` and `NodeView.setPainter`: drawing by code in the scene; `StationView.pointerOn`: the cursor on a node's plane.
- `StationBehavior.onItemClick` for using the stack on the cursor on a node, `StationView.cursorOn`, `highlight` on slots.
- Debug view of every hit box, node id, inventory slot, work plane and camera target (F3+B or `/diegesis debug`).
- Seats: places at a station, each with its own view; a player can sit down at one for everyone to see.
- Meshes: scene objects from OBJ files with any texture of the block atlas, next to Blockbench models.
- The station's own data, saved and synced, for what is no slot, tank or control.
- `repeat` on scene nodes, `hopTo` and `setEnabled` on node views, captions at the edge of the screen, and
  `onSceneReady` for behaviours.
- Free placing: items put down anywhere on a scene's surfaces, turned, tipped and sized by hand, standing on each other and
  kept for everyone to see, with a see-through preview on the spot; block items with a flat icon stand as their block, and
  what gives light shines and burns there. Light in such a scene stays in its room, also on the wall behind.
- Strokes: a node can take the mouse held on it, reported every frame with where the cursor is, for shaping and painting by hand.
- Shapes made by code: polygons rebuilt while they are on screen, and items whose look comes from their data, in every view.
- Stack counts shown on the block for slots, results, the inventory and the held stack.
- The held stack follows the cursor over the block, sways and casts a shadow of its shape.
- Grabbable objects held by a grip point, following the cursor with weight, lean and a shadow of their shape; carry
  planes lift them over parts of the scene or keep them out of areas; an object may move in a plane of its own.
- Slots whose item is drawn larger or smaller than the slot.
- Toggles, buttons, knobs turned by circling the mouse round them, and sliders; locked controls set only by the station's logic.
- Ready controls: a lever, a rocker switch, a push button, a knob on a dial and a slider, each one line in a scene.
- Node templates: reusable nodes in `diegesis/nodes/` that a scene starts from and changes.
- Controls with a still base and a pivot; a control's movement scales with its node.
- Trigger zones the cursor passes through.
- Contacts between a carried object (or just its touching part, like a torch's flame) and other hit boxes, for triggers
  and knocks.
- Stations spanning several blocks, and station blocks given as suppliers (blocks registered later).
- Tanks with liquids and mixtures that keep their volume when a vessel tips, in their full colour on an opaque liquid
  sprite (the look's alpha alone sets how thick they are), deepening with the depth, with a surface
  that reflects at a grazing angle, rocks at a pitch set by the vessel's size and carries ripples from jolts, drops and
  boiling.
- Pouring by pointing: aim the vessel at a tank that takes the liquid and it waits beside it, tips until the liquid
  reaches the spout and goes on tipping as it empties; the wheel sets the flow. A continuous stream runs into the other
  tank, the level rises smoothly and mixtures drift to their new colour; tanks that would take the liquid are marked,
  a bar by the cursor shows how full the target is, and players around see the pour.
- Glass visuals that stay clear face-on and thicken at grazing angles; see-through parts of a scene no longer cut each
  other off.
- Glances: other camera shots of a scene the look glides to when the cursor rests at an edge of the screen or code asks,
  and back when a stack is taken or the cursor reaches the opposite edge; a small arrow marks the edge.
- An inventory drawer: an icon at any side of the screen, pointing there slides the inventory over the scene, and
  while it is out nothing under it is hovered or clicked. Pinned with a click or Tab; vanilla panel or minimal look.
- Objects kept in the hand by a quick click as well as carried by a drag; stacks dragged out of a slot and let go over
  another.
- Scene particles (sparks, bubbles, drops, splashes, steam, smoke, glints, dust, flame) with a size per emitter, also
  inside a liquid (bubbles rising to the surface), a churning surface for boiling,
  flickering light sources that brighten the surface's own colours, and station sounds.
- Client-only stations, and server stations shared between players and shown to everyone around.
- Node tooltips (translation keys, changeable from code), decorative nodes without hit boxes, two-sided glass, light
  offsets, gliding controls (a gauge needle on a locked control is a display, not a control), a second camera shot
  while the scene's inventory is shown.
- Client animation helpers: spinning, shaking, swapping a node's visual, a node's place in the scene, particles inside
  a node's liquid.
- Slots and tanks that are not kept start over when the last player leaves, also on block entities.
- Strict scene files: a misspelt or unknown field is an error naming the closest known field; `$` keys are allowed
  for editors and notes.
- Documentation: a guide, scene and station references, a cookbook, a JSON Schema of scene and node template files, a
  one-page API reference for AI coding assistants, and a ready assistant skill with a project notes template.
- A check of a scene's faces (`tools/scenes/depth_check.py`) that finds what will flicker before the game is started.
- Forge and Fabric for Minecraft 1.20.1.
