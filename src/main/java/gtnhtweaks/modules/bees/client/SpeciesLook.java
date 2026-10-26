package gtnhtweaks.modules.bees.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;

import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.api.genetics.IClassification;
import gtnhtweaks.common.model.Shapes;

/**
 * How the bees of a species look: their colors, from the colors Forestry draws the species with, and what they wear,
 * hold and how they feel, from the words of its name, its id and its branch. The bees of other mods are found by the
 * same words: a GregTech iron bee is told by "iron" as a metal bee, a Magic Bees arcane bee by "arcane" as a magic
 * one. A species no word tells about still carries what it produces, and gets a face of its own.
 */
final class SpeciesLook {

    private static final Map<String, SpeciesLook> LOOKS = new HashMap<>();
    /** Words of the ids that tell nothing about the species. */
    private static final Set<String> NOISE = new HashSet<>(
        Arrays.asList(
            "species",
            "bee",
            "bees",
            "branch",
            "the",
            "of",
            "for",
            "gt",
            "gregtech",
            "magicbees",
            "extrabees",
            "forestry",
            "binnie",
            "genetics"));
    private static final List<Rule> RULES = new ArrayList<>();
    /** Where words break: camelCase and digits apart too, "speciesMeteoricIron" is "species", "meteoric", "iron". */
    private static final Pattern WORD_BREAKS = Pattern.compile(
        "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])|[^A-Za-z0-9]+");

    final int body, stripe;
    final Hat hat;
    final int hatColor;
    final FaceGear face;
    final Held held;
    final Aura aura;
    final Skin skin;
    final Expression expression;
    final int eyes;
    final boolean eyesGlow, antennaGlow;
    /** What it produces, carried when it holds nothing else. Null if it produces nothing. */
    final ItemStack product;

    private SpeciesLook(IAlleleBeeSpecies species) {
        Set<String> words = words(species);
        Rule look = new Rule();
        for (Rule rule : RULES) {
            if (rule.matches(words)) look.fill(rule);
        }
        int primary = species.getIconColour(0) & 0xFFFFFF;
        int secondary = species.getIconColour(1) & 0xFFFFFF;
        skin = look.skin != null ? look.skin : Skin.NORMAL;
        if (skin == Skin.COSMIC) {
            body = 0x15112A;
            stripe = primary;
        } else {
            body = primary;
            stripe = distance(primary, secondary) > 80 && brightness(secondary) < 235 ? secondary
                : Shapes.mix(primary, 0x1E1610, 0.55F);
        }
        hat = look.hat != null ? look.hat : Hat.NONE;
        if (look.hatColor >= 0) hatColor = look.hatColor;
        else if (hat == Hat.WIZARD_HAT) hatColor = Shapes.mix(primary, 0x1A1030, 0.55F);
        else hatColor = hat.color;
        face = look.face != null ? look.face : FaceGear.NONE;
        product = product(species);
        held = look.held != null ? look.held : product != null ? Held.PRODUCT : Held.NONE;
        aura = look.aura != null ? look.aura : species.hasEffect() ? Aura.SPARKLES : Aura.NONE;
        expression = look.expression != null ? look.expression
            : Expression.ANY[Math.floorMod(
                species.getUID()
                    .hashCode(),
                Expression.ANY.length)];
        eyes = look.eyes >= 0 ? look.eyes : 0x161616;
        eyesGlow = look.eyes >= 0;
        antennaGlow = look.antennaGlow;
    }

    static SpeciesLook of(IAlleleBeeSpecies species) {
        return LOOKS.computeIfAbsent(species.getUID(), uid -> new SpeciesLook(species));
    }

    /** The words of the name, the id and the branch of the species, lower case. */
    private static Set<String> words(IAlleleBeeSpecies species) {
        Set<String> words = new HashSet<>();
        addWords(words, species.getUID(), true);
        addWords(words, species.getUnlocalizedName(), true);
        addWords(words, species.getName(), false);
        IClassification branch = species.getBranch();
        if (branch != null) {
            addWords(words, branch.getUID(), true);
            addWords(words, branch.getName(), false);
        }
        words.removeAll(NOISE);
        return words;
    }

    /**
     * @param id true for an id, whose first part is the mod it comes from: "magicbees.speciesArcane" is "arcane", not
     *           "magic"
     */
    private static void addWords(Set<String> words, String text, boolean id) {
        if (text == null) return;
        if (id && text.indexOf('.') > 0) text = text.substring(text.indexOf('.') + 1);
        for (String word : WORD_BREAKS.split(text)) {
            if (!word.isEmpty()) words.add(word.toLowerCase(Locale.ROOT));
        }
    }

    /** What it produces: its specialty if any, else its most likely product. */
    private static ItemStack product(IAlleleBeeSpecies species) {
        ItemStack best = best(species.getSpecialtyChances());
        if (best == null) best = best(species.getProductChances());
        return best == null ? null : best.copy();
    }

    private static ItemStack best(Map<ItemStack, Float> chances) {
        if (chances == null) return null;
        ItemStack best = null;
        float bestChance = -1;
        for (Map.Entry<ItemStack, Float> e : chances.entrySet()) {
            if (e.getKey() == null || e.getKey()
                .getItem() == null) continue;
            float chance = e.getValue() == null ? 0 : e.getValue();
            if (chance > bestChance) {
                best = e.getKey();
                bestChance = chance;
            }
        }
        return best;
    }

    private static int distance(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
            + Math.abs((a & 255) - (b & 255));
    }

    private static int brightness(int rgb) {
        return (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3;
    }

    /**
     * Words and what a bee whose species has one of them looks like. The rules are tried in order and each fills only
     * what the rules before it left open, so the more telling words come first.
     */
    private static final class Rule {

        String[] words = {};
        Hat hat;
        int hatColor = -1;
        FaceGear face;
        Held held;
        Aura aura;
        Skin skin;
        Expression expression;
        int eyes = -1;
        boolean antennaGlow;

        /**
         * A word of the species matches a word of the rule when it is that word, or when it contains it and the word is
         * long enough not to be found by chance: "tungstensteel" has "steel", but "environ" does not have "iron".
         */
        boolean matches(Set<String> species) {
            for (String word : words) {
                if (species.contains(word)) return true;
                if (word.length() < 6) continue;
                for (String s : species) {
                    if (s.contains(word)) return true;
                }
            }
            return false;
        }

        void fill(Rule rule) {
            if (hat == null && rule.hat != null) {
                hat = rule.hat;
                hatColor = rule.hatColor;
            }
            if (face == null) face = rule.face;
            if (held == null) held = rule.held;
            if (aura == null) aura = rule.aura;
            if (skin == null) skin = rule.skin;
            if (expression == null) expression = rule.expression;
            if (eyes < 0) eyes = rule.eyes;
            antennaGlow |= rule.antennaGlow;
        }

        Rule hat(Hat hat) {
            this.hat = hat;
            return this;
        }

        Rule hat(Hat hat, int color) {
            this.hat = hat;
            this.hatColor = color;
            return this;
        }

        Rule face(FaceGear face) {
            this.face = face;
            return this;
        }

        Rule held(Held held) {
            this.held = held;
            return this;
        }

        Rule aura(Aura aura) {
            this.aura = aura;
            return this;
        }

        Rule skin(Skin skin) {
            this.skin = skin;
            return this;
        }

        Rule expression(Expression expression) {
            this.expression = expression;
            return this;
        }

        Rule eyes(int rgb) {
            this.eyes = rgb;
            return this;
        }

        Rule antennaGlow() {
            this.antennaGlow = true;
            return this;
        }
    }

    private static Rule rule(String words) {
        Rule rule = new Rule();
        rule.words = words.split(" ");
        RULES.add(rule);
        return rule;
    }

    static {
        // the end of the game: dark as space with stars
        rule("infinity neutronium cosmic eternity universium spacetime transcendent magnetohydrodynamically")
            .skin(Skin.COSMIC)
            .aura(Aura.SPARKLES)
            .expression(Expression.SMUG);
        rule("draconic dragon drake wyvern awakened chaotic").hat(Hat.HORNS, 0x3A1F5C)
            .aura(Aura.ENDER)
            .eyes(0xCC4BFF)
            .expression(Expression.ANGRY);
        rule(
            "space moon lunar mars martian jupiter venus mercury saturn uranus neptune pluto titan io europa ganymede "
                + "callisto enceladus triton haumea makemake ceres phobos deimos oberon miranda proteus kuiper "
                + "asteroid asteroids meteor meteoric meteorite comet galaxy galactic stellar orbit centauri "
                + "barnarda tceti tcetie vega rocket astro nebula planet planetary desh ledox quantium mytryl")
                    .hat(Hat.ASTRONAUT);
        rule(
            "radioactive nuclear uranium plutonium thorium naquadah naquadria americium radium radon neptunium "
                + "curium californium polonium enriched isotope fission fallout irradiated").face(FaceGear.GASMASK)
                    .aura(Aura.RADIATION)
                    .skin(Skin.GLOW);
        rule(
            "energy energetic excited ecstatic lapotron electrotine redstone electric electrical charged battery "
                + "power lightning static thunder spark sparky volt voltage signalum").aura(Aura.SPARKS)
                    .antennaGlow()
                    .expression(Expression.GRIN);
        rule(
            "fire firey fiery blaze blazing lava magma molten smouldering smoldering volcanic burning ember inferno "
                + "pyro pyrotheum flame flaming firestone ignis scorched hot").hat(Hat.FLAME)
                    .aura(Aura.FLAMES)
                    .skin(Skin.GLOW)
                    .expression(Expression.GRIN);
        rule(
            "sinister fiendish demonic infernal hellish nether hateful spiteful malicious vindictive vengeful wicked "
                + "evil imp devil demon hellfire netherrack").hat(Hat.HORNS)
                    .expression(Expression.ANGRY);
        rule("merry festive christmas santa xmas yule").hat(Hat.SANTA_HAT)
            .aura(Aura.SNOW)
            .expression(Expression.GRIN);
        rule(
            "wintry icy glacial frozen freezing arctic artic frosty frost snow snowy ice cold chilly snowqueen "
                + "blizzard cryotheum polar tundra").hat(Hat.BEANIE)
                    .aura(Aura.SNOW);
        rule("tipsy drunk alcohol alcoholic boozy wine beer brew").hat(Hat.PARTY_HAT)
            .aura(Aura.NOTES)
            .expression(Expression.DIZZY);
        rule("celebratory jubilant party birthday festival fun joyful cheerful playful").hat(Hat.PARTY_HAT)
            .aura(Aura.CONFETTI)
            .expression(Expression.GRIN);
        rule("tricky spooky halloween pumpkin scary haunted").hat(Hat.PUMPKIN)
            .expression(Expression.GRIN);
        rule(
            "spectral phantasmal ghost ghostly ghastly ghast spirit spirits soul souls wispy wisp ethereal phantom "
                + "shade specter spectre wraith").skin(Skin.GHOST)
                    .expression(Expression.SURPRISED);
        rule("ender end ended endium enderium void abyss oblivion nameless chorus teleport warp enderman")
            .aura(Aura.ENDER)
            .eyes(0xCC4BFF);
        rule(
            "zombie rotten decomposing undead withering wither skulking skeleton bone bony graveyard necrotic lich "
                + "ghoul mummy putrid").skin(Skin.UNDEAD)
                    .expression(Expression.DIZZY);
        rule("creeper creepy explosive gunpowder").expression(Expression.CREEPER)
            .aura(Aura.SMOKE);
        // thaumic metals: goggles of revealing under a wizard hat
        rule("thaumium thauminite thaumic salis tainted taint essentia shadowmetal voidmetal").face(FaceGear.GOGGLES);
        rule(
            "mystical sorcerous unusual attuned eldritch esoteric mysterious arcane charmed enchanted supernatural "
                + "magic magical magician wizard witch witchy sorcery spell mana vis thaum rune runic occult mystic "
                + "botania alfheim elven fairy astral aura essence druid aspect primal infused").hat(Hat.WIZARD_HAT)
                    .held(Held.WAND)
                    .aura(Aura.SPARKLES);
        rule(
            "pupil scholarly savant doctoral brainy aware studious student scholar professor scientist science "
                + "research sage wise wisdom knowledge learned genius smart timely").hat(Hat.GRAD_CAP)
                    .face(FaceGear.GLASSES)
                    .held(Held.BOOK);
        rule("noble majestic imperial regal royal lordly aristocratic posh rich wealthy").hat(Hat.TOP_HAT)
            .face(FaceGear.MONOCLE)
            .expression(Expression.SMUG);
        rule("steadfast valiant heroic brave knight avenging guardian paladin defender warrior champion hero")
            .hat(Hat.KNIGHT_HELMET)
            .held(Held.SWORD)
            .expression(Expression.DETERMINED);
        rule(
            "diligent unweary industrious industrial machinist worker engineer mechanic factory assembly builder "
                + "construction steam steamy gear cog").hat(Hat.HARD_HAT)
                    .held(Held.WRENCH)
                    .expression(Expression.DETERMINED);
        rule("holy pious divine heavenly angelic angel blessed sacred saint order pure purity sanctified celestial")
            .hat(Hat.HALO)
            .expression(Expression.CALM);
        rule("monastic secluded hermitic hermit monk shadow shadowed darkened dark sneaky thief rogue stealth")
            .hat(Hat.HOOD)
            .expression(Expression.CALM);
        rule(
            "water watery aqua aquatic ocean sea marine fish fishy river lake tidal coral prismarine squid drowned "
                + "aqueous wet damp").face(FaceGear.SNORKEL)
                    .aura(Aura.BUBBLES);
        rule("windy airy air sky cloud aer storm breeze gale").face(FaceGear.GOGGLES);
        rule("tropical exotic edenic jungle paradise island coconut banana tiki rainforest").hat(Hat.FLOWER)
            .face(FaceGear.SUNGLASSES)
            .expression(Expression.SMUG);
        rule("modest frugal austere arid desert sandy sand dune barren desolate dry cactus mesa").hat(Hat.SOMBRERO);
        rule("marshy miry boggy swamp swampy fungal fungus mushroom mycelium spore spores mossy moss bog")
            .hat(Hat.MUSHROOM)
            .aura(Aura.SPORES);
        rule(
            "sugary fruity sweet candy chocolate cocoa milk milky coffee sandwich bacon beef pork chicken mutton "
                + "food cake cookie bread jam berry apple juice vanilla caramel toffee cheese pizza burger tasty "
                + "delicious ripening ripe sour salt salty spice spicy pepper butter edible cuisine").hat(Hat.CHEF_HAT);
        rule("leporine bunny rabbit hare easter").hat(Hat.BUNNY_EARS);
        rule(
            "diamond emerald ruby sapphire olivine garnet amethyst topaz tanzanite amber certus fluix jasper opal "
                + "peridot jade crystal crystalline gem gems jewel onyx malachite lapis apatite monazite quartzite "
                + "diamantine prismatic").hat(Hat.MINER_HELMET)
                    .skin(Skin.CRYSTAL)
                    .aura(Aura.SPARKLES);
        rule(
            "metal metallic iron copper tin lead nickel zinc silver gold golden aluminium aluminum titanium chrome "
                + "chromium manganese tungsten tungstensteel platinum iridium osmium steel bronze brass electrum "
                + "invar cobalt magnesium molybdenum palladium ruthenium rhodium niobium vanadium gallium indium "
                + "bismuth antimony arsenic cadmium tantalum thallium mithril adamantium adamantite orichalcum "
                + "vibranium duranium tritanium trinium lutetium europium samarium neodymium yttrium beryllium "
                + "lithium uranium alloy ingot ore ores").hat(Hat.MINER_HELMET)
                    .skin(Skin.METAL);
        rule("coal peat lignite graphite carbon charcoal coke soot ash ashen").hat(Hat.MINER_HELMET)
            .aura(Aura.SMOKE);
        rule("oil oily tar creosote fuel diesel petroleum naphtha benzene gasoline").hat(Hat.HARD_HAT)
            .aura(Aura.SMOKE);
        rule(
            "stone rock rocky granite basalt marble mineral cobble cobblestone andesite diorite clay dirt earth "
                + "earthy soil mud gravel flint obsidian slate").hat(Hat.MINER_HELMET)
                    .skin(Skin.STONE);
        rule("slime slimy slimeball sticky viscous glutinous resin resinous rubber gummy latex glue gelatin goo")
            .skin(Skin.SLIME);
        rule("forest leafy leaf sylvan woody wood wooded oak tree timber verdant sapling bark").hat(Hat.LEAF)
            .aura(Aura.LEAVES);
        rule("meadows meadow flower flowery floral blossom bloom petal rose daisy tulip garden").hat(Hat.FLOWER_CROWN);
        rule(
            "rural farmerly farmed farm agrarian cultivated harvest wheat crop crops straw hay barn pastoral "
                + "farmer").hat(Hat.STRAW_HAT);
        rule(
            "dye pigment paint color colour red yellow blue green black white brown orange cyan purple gray grey "
                + "pink lime magenta rainbow chromatic artist artistic").hat(Hat.BERET);
        rule("roman greek classical ancient primeval prehistoric fossil fossilised fossilized antique")
            .face(FaceGear.MUSTACHE);
        rule("lovely love romantic valentine heart hearts").aura(Aura.HEARTS);
        // what the name tells of the character alone
        rule(
            "aggressive angry wrathful hostile furious rage wild savage hydra urghast avenger feral fierce "
                + "vicious").expression(Expression.ANGRY);
        rule("forlorn abandoned lonely sad gloomy melancholy sorrow weeping tears withered lost")
            .expression(Expression.SAD);
        rule("happy jolly lucky sunny bright").expression(Expression.GRIN);
        rule("curious wondrous shocked startled").expression(Expression.SURPRISED);
        rule("serene peaceful zen calm lazy sleepy common").expression(Expression.CALM);
    }
}
