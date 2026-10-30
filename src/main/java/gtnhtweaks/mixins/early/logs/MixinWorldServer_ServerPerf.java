package gtnhtweaks.mixins.early.logs;

import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.logs.ServerPerf;

/** The tick of every dimension, its world and its entities apart, as the profiler names all of them alike. */
@Mixin(WorldServer.class)
public abstract class MixinWorldServer_ServerPerf {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$tickBegins(CallbackInfo ci) {
        ServerPerf.worldTickBegins((WorldServer) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$tickEnds(CallbackInfo ci) {
        ServerPerf.worldTickEnds((WorldServer) (Object) this);
    }

    @Inject(method = "updateEntities", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$entitiesBegin(CallbackInfo ci) {
        ServerPerf.entitiesBegin((WorldServer) (Object) this);
    }

    @Inject(method = "updateEntities", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$entitiesEnd(CallbackInfo ci) {
        ServerPerf.entitiesEnd((WorldServer) (Object) this);
    }
}
