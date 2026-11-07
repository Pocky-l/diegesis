<!-- Copy this section into your project's CLAUDE.md and fill in the <...> parts. -->

## Diegesis interfaces

This mod uses [Diegesis](https://github.com/Pocky-l/diegesis) (Minecraft 1.20.1, <Forge/Fabric>) for in-world block
interfaces. API reference on one page: https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/llms.txt (fetch it
before changing a station or scene); the `diegesis` skill has the procedure.

- Stations (Java, registered on both sides): `<path/to/MyStations.java>`; logic classes next to them.
- Scenes: `src/main/resources/assets/<modid>/diegesis/scenes/<station>.json`; node templates in `diegesis/nodes/`.
- Scene models (Blockbench, Java Block format): `assets/<modid>/models/diegesis/`, referenced as
  `"<modid>:diegesis/<name>"`.
- Client behaviours (registered in client setup only): `<path/to/client/>`.
- Scene space: Blockbench pixels (16 = a block), block facing north (front = -Z), +X is the screen's left for the
  player; table top y = 16.
- Scene files are strict: unknown fields break the scene. Start every scene with
  `"$schema": "https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/schema/scene.schema.json"`.
- Testing in game: F3+T reloads scenes (errors: `Scene <id> (<file>) is broken` in the log), F3+B or `/diegesis debug`
  shows hit boxes and node ids.
