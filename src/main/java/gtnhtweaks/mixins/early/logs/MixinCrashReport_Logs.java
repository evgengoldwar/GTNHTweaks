package gtnhtweaks.mixins.early.logs;

import java.io.File;

import net.minecraft.crash.CrashReport;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.logs.LogsModule;

/** Copies every crash report Minecraft saves into the GTNHTweaks crash logs. */
@Mixin(CrashReport.class)
public abstract class MixinCrashReport_Logs {

    @Inject(method = "saveToFile", at = @At("RETURN"))
    private void gtnhtweaks$copyCrashReport(File file, CallbackInfoReturnable<Boolean> cir) {
        try {
            CrashReport report = (CrashReport) (Object) this;
            LogsModule.onCrashReportSaved(report, report.getCompleteReport(), file);
        } catch (Throwable t) {
            // Saving the crash report must never fail because of the copy
        }
    }
}
