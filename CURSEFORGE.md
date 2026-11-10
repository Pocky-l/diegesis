<!-- CurseForge description of Diegesis. HTML: paste it in the editor's HTML (source) mode. -->
<h1 style="text-align:center">Diegesis</h1>

<p style="text-align:center"><em>A library for interfaces that live on the block itself.</em></p>

<p style="text-align:center"><strong>Using a block flies the camera to it. The player then works with real 3D objects on that block: moves items, pulls levers, turns knobs, pours liquids. Everyone standing nearby sees it happen.</strong></p>

<p style="text-align:center">Diegesis adds nothing to the game on its own. Install it when a mod asks for it, such as <a href="https://www.curseforge.com/minecraft/mc-mods/hands-on-delight">Hands-On Delight</a>.</p>

<p style="text-align:center"><img src="https://media.forgecdn.net/attachments/description/1790227/description_c76d1d6e-f167-4b44-a83c-b2e8e5041ba1.gif" width="640" alt="Soup cooked and stirred by hand in a station built with Diegesis"></p>

<p style="text-align:center"><em>The cooking pot of Hands-On Delight, built with Diegesis.</em></p>

<hr>

<h2 style="text-align:center">🎮 For players</h2>
<p style="text-align:center">Put it in the mods folder next to the mod that needs it. Minecraft 1.20.1 on <a href="https://files.minecraftforge.net">Forge</a>, 1.21.1 on <a href="https://neoforged.net">NeoForge</a>, and both on <a href="https://fabricmc.net">Fabric</a> (with <a href="https://www.curseforge.com/minecraft/mc-mods/fabric-api">Fabric API</a>).</p>

<h2 style="text-align:center">🛠️ For modders</h2>
<p style="text-align:center">An interface is two things. A <strong>station</strong> in Java says which blocks it opens on, what state it keeps and what the rules are. A <strong>scene</strong> in JSON says what is drawn where and what can be touched. Models come straight from <a href="https://www.blockbench.net">Blockbench</a>, and scenes reload with F3+T while the game runs.</p>

<ul>
<li>🎥 <strong>Camera and scenes:</strong> a camera rig per scene, nodes that nest, move, turn, scale, fade and glow.</li>
<li>📦 <strong>Items on the block:</strong> clicks work as in a vanilla menu, including shift-click, splitting and dragging a stack across slots. The inventory can be a drawer or laid out in the world.</li>
<li>✋ <strong>Carried objects:</strong> held by a grip point, with weight and lean, a shadow of their own shape and triggers on touch.</li>
<li>💧 <strong>Liquids and pouring:</strong> tanks hold real volumes and mixtures that rock and ripple. The server paces and checks every pour.</li>
<li>🎛️ <strong>Ready controls:</strong> lever, rocker switch, push button, knob and slider, one line of JSON each.</li>
<li>✨ <strong>Particles, light and sound:</strong> emitters, boiling surfaces, glowing nodes that light what is near.</li>
<li>🪑 <strong>Seats and multiblocks:</strong> a station can span several blocks and seat several players, each with their own view.</li>
<li>🌐 <strong>Client-only or server:</strong> client stations work on any server. Server stations check every action, so items cannot be duplicated, and share one state between players.</li>
</ul>

<p style="text-align:center">📚 Guides, a cookbook and a JSON schema for scene files are on <a href="https://github.com/Pocky-l/diegesis">GitHub</a>.</p>

<h3 style="text-align:center">Compatibility</h3>
<p style="text-align:center"><a href="https://www.curseforge.com/minecraft/mc-mods/embeddium"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/compat/embeddium.png" width="24" alt="Embeddium"></a> ✅ <a href="https://www.curseforge.com/minecraft/mc-mods/embeddium"><strong>Embeddium</strong></a> and <a href="https://www.curseforge.com/minecraft/mc-mods/oculus"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/compat/oculus.png" width="24" alt="Oculus"></a> ✅ <a href="https://www.curseforge.com/minecraft/mc-mods/oculus"><strong>Oculus</strong></a>: scenes, liquids and particles draw as usual, with or without a shader pack</p>
<p style="text-align:center"><a href="https://modrinth.com/mod/blur-forge"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/compat/blur.png" width="24" alt="Blur"></a> ✅ <a href="https://modrinth.com/mod/blur-forge"><strong>Blur (Forge)</strong></a>, <a href="https://modrinth.com/mod/reblured"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/compat/blur.png" width="24" alt="ReBlured"></a> ✅ <a href="https://modrinth.com/mod/reblured"><strong>ReBlured</strong></a> and <a href="https://modrinth.com/mod/blur-plus"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/compat/blur_plus.png" width="24" alt="Blur+"></a> ✅ <a href="https://modrinth.com/mod/blur-plus"><strong>Blur+</strong></a>: stations stay sharp, other screens keep their blur</p>

<hr>

<!-- more-mods:start icons -->
<p style="text-align:center"><b>More mods by Pocky</b></p>
<p style="text-align:center">
<a href="https://www.curseforge.com/minecraft/mc-mods/hoarfrost-snow-blizzards"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/more-mods/hoarfrost.png" width="48" height="48" alt="Hoarfrost: Snow &amp; Blizzards" title="Hoarfrost: Snow &amp; Blizzards - seven kinds of snowfall, a living wind and soft snow on the land"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/more-mods/petrichor.png" width="48" height="48" alt="Petrichor: Rain &amp; Storms" title="Petrichor: Rain &amp; Storms - realistic rain, puddles, drips and branching lightning"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/rustling-leaves"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/more-mods/rustling_leaves.png" width="48" height="48" alt="Rustling Leaves" title="Rustling Leaves - falling leaves and leaf piles you can wade through and rake, client-side"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/turbo-for-distant-horizons"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/more-mods/dhturbo.png" width="48" height="48" alt="Turbo for Distant Horizons" title="Turbo for Distant Horizons - Distant Horizons terrain generated several times faster"></a>
<a href="https://www.curseforge.com/minecraft/shaders/pocky-shaders"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/more-mods/pocky_shaders.png" width="48" height="48" alt="Pocky Shaders" title="Pocky Shaders - a cosy, natural shader pack that shows the weather of these mods"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/hextale"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/more-mods/hextale.png" width="48" height="48" alt="HexTale" title="HexTale - hextech magic to research, distil and bind with runes, inspired by Arcane and Thaumcraft"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/mekanism-advancedgenerators"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/more-mods/mekanismadvancedgenerators.png" width="48" height="48" alt="Mekanism Advanced Generators" title="Mekanism Advanced Generators - nine tiers of solar panels, big solar generators, cubes and cables for Mekanism"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/inventory-backups"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/more-mods/inventorybackups.png" width="48" height="48" alt="Inventory Backups" title="Inventory Backups - inventory and ender chest backups with one-click restore for admins"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/hands-on-delight"><img src="https://raw.githubusercontent.com/Pocky-l/diegesis/main/docs/more-mods/handson_delight.png" width="48" height="48" alt="Hands-On Delight" title="Hands-On Delight - cook in Farmer's Delight with your own hands, right on its blocks"></a>
</p>
<!-- more-mods:end -->

<p style="text-align:center"><span style="font-size:12px">Made by Pocky. Source on <a href="https://github.com/Pocky-l/diegesis">GitHub</a> under the MIT license.</span></p>
