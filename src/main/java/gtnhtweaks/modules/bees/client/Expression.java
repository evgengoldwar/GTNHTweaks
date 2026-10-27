package gtnhtweaks.modules.bees.client;

/** The face a bee makes, from its character. How it feels where it is (asleep, hot, cold) is drawn over it. */
enum Expression {

    HAPPY,
    /** Laughing with its mouth open. */
    GRIN,
    /** Eyes closed in a smile. */
    CALM,
    SURPRISED,
    /** Frowning brows: the aggressive bees. */
    ANGRY,
    SAD,
    /** Half closed eyes and a smirk: the noble bees. */
    SMUG,
    /** Spiral eyes and a wavy mouth: the tipsy bees. */
    DIZZY,
    /** Narrowed eyes and a straight mouth: the heroic and working bees. */
    DETERMINED,
    /** The face of a creeper. */
    CREEPER;

    /** The faces given to the bees whose character is not told by their name, picked by the species. */
    static final Expression[] ANY = { HAPPY, HAPPY, GRIN, CALM, SURPRISED, SMUG };
}
