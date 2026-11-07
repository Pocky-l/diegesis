# Diegesis for Claude Code

Two files that let [Claude Code](https://docs.anthropic.com/en/docs/claude-code) write Diegesis stations and scenes in
your mod:

- [`skills/diegesis/SKILL.md`](skills/diegesis/SKILL.md) - a skill with the procedure and the rules that prevent
  most mistakes. Copy the `diegesis` folder into your project's `.claude/skills/` (or `~/.claude/skills/` for every
  project).
- [`CLAUDE.template.md`](CLAUDE.template.md) - a section for your project's `CLAUDE.md` that says where your stations,
  scenes and models live. Copy it in and fill in the paths.

Both point to [`docs/llms.txt`](../../docs/llms.txt), the whole API on one page; other assistants can read that file
directly. Then ask for an interface in plain words, for example: "make the alchemy table a Diegesis station: three
vial slots, a flask that takes water poured from the vials, a gas knob and a lever that lights the burner".
