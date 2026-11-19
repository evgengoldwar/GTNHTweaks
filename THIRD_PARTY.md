# Extra Botany maid outfits

The maid model geometry and textures are from Extra Botany by ExtraMeteorP and contributors:
https://github.com/ExtraMeteorP/Extra-Botany

Source revision: `4ad2cc9b86620801409c17366511156a3ba1b45e` (Minecraft 1.12).

Original files:

- `src/main/java/com/meteor/extrabotany/client/model/ModelArmorCombatMaid.java`
- `src/main/java/com/meteor/extrabotany/client/model/ModelArmorCosmeticMaid.java`
- `src/main/resources/assets/extrabotany/textures/model/armor_combatmaid.png`
- `src/main/resources/assets/extrabotany/textures/model/armor_cosmeticmaid.png`

GTNHTweaks adapts the Java package, equipment-slot API and model superclass for
Minecraft 1.7.10 player rendering. Box geometry, UV coordinates, rotations and
part hierarchy are preserved. The PNG files are unmodified. No Extra Botany
armor items, abilities or server-side mechanics are included.

The player-pose adapter follows the already posed player in both vanilla and
backported crouching. While crouched, it keeps the skirt attached at the waist
and removes torso pitch from the skirt; the original geometry remains unchanged.
