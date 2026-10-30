package gtnhtweaks.modules.chunkengine;

import java.util.List;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

/**
 * Stands in for the chunk generator of a world while the server loads a chunk whose terrain a thread made: the call
 * that would make the terrain gets that chunk instead. It puts the generator of the world back at that call, so it is
 * only in place between the start of the load and the making of the terrain; all its other calls go to the generator.
 */
public final class PreparedProvider implements IChunkProvider {

    final IChunkProvider generator;
    private final ChunkProviderServer server;
    private final int x, z;
    private Chunk chunk;

    private PreparedProvider(ChunkProviderServer server, IChunkProvider generator, Chunk chunk) {
        this.server = server;
        this.generator = generator;
        this.chunk = chunk;
        this.x = chunk.xPosition;
        this.z = chunk.zPosition;
    }

    /** The generator itself, also while a chunk made by a thread stands in for it. */
    public static IChunkProvider unwrap(IChunkProvider provider) {
        return provider instanceof PreparedProvider ? ((PreparedProvider) provider).generator : provider;
    }

    /** At the start of a chunk load: puts the chunk made by a thread in place of the terrain, if there is one. */
    public static void install(ChunkProviderServer server, WorldServer world, int x, int z) {
        if (server.currentChunkProvider instanceof PreparedProvider) restore(server);
        Chunk chunk = TerrainWorkers.take(world, x, z);
        if (chunk != null) server.currentChunkProvider = new PreparedProvider(server, server.currentChunkProvider, chunk);
    }

    /** At the end of a chunk load: the generator of the world again, also when the chunk came from disk. */
    public static void restore(ChunkProviderServer server) {
        if (!(server.currentChunkProvider instanceof PreparedProvider)) return;
        PreparedProvider prepared = (PreparedProvider) server.currentChunkProvider;
        server.currentChunkProvider = prepared.generator;
        if (prepared.chunk != null) ChunkEngine.WASTED_TERRAIN.add();
    }

    @Override
    public Chunk provideChunk(int chunkX, int chunkZ) {
        if (server.currentChunkProvider == this) server.currentChunkProvider = generator;
        Chunk made = chunk;
        if (made == null || chunkX != x || chunkZ != z) return generator.provideChunk(chunkX, chunkZ);
        chunk = null;
        WorldServer world = server.worldObj;
        if (TerrainWorkers.verifying(world)) {
            // the usual chunk is the one used, the made one is only compared with it
            Chunk usual = generator.provideChunk(chunkX, chunkZ);
            TerrainWorkers.verify(world, made, usual);
            return usual;
        }
        // as for a chunk read from disk: the structures that reach into it are known to the generator again
        generator.recreateStructures(chunkX, chunkZ);
        ChunkEngine.THREAD_TERRAIN.add();
        return made;
    }

    @Override
    public Chunk loadChunk(int chunkX, int chunkZ) {
        return provideChunk(chunkX, chunkZ);
    }

    @Override
    public boolean chunkExists(int chunkX, int chunkZ) {
        return generator.chunkExists(chunkX, chunkZ);
    }

    @Override
    public void populate(IChunkProvider provider, int chunkX, int chunkZ) {
        generator.populate(provider, chunkX, chunkZ);
    }

    @Override
    public boolean saveChunks(boolean all, IProgressUpdate progress) {
        return generator.saveChunks(all, progress);
    }

    @Override
    public boolean unloadQueuedChunks() {
        return generator.unloadQueuedChunks();
    }

    @Override
    public boolean canSave() {
        return generator.canSave();
    }

    @Override
    public String makeString() {
        return generator.makeString();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return generator.getPossibleCreatures(type, x, y, z);
    }

    @Override
    public ChunkPosition func_147416_a(World world, String structure, int x, int y, int z) {
        return generator.func_147416_a(world, structure, x, y, z);
    }

    @Override
    public int getLoadedChunkCount() {
        return generator.getLoadedChunkCount();
    }

    @Override
    public void recreateStructures(int chunkX, int chunkZ) {
        generator.recreateStructures(chunkX, chunkZ);
    }

    @Override
    public void saveExtraData() {
        generator.saveExtraData();
    }
}
