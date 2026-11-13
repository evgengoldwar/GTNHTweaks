package gtnhtweaks.modules.chunkreveal;

/** The animation state GTNHTweaks adds to the renderers of the pieces of chunks, WorldRenderer. */
public interface ChunkRevealState {

    /** When the piece starts to appear, in the milliseconds of {@link ChunkReveal#now}; Long.MIN_VALUE for never. */
    long gtnhtweaks$revealStart();
}
