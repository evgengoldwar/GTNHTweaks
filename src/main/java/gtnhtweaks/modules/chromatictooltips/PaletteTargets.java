package gtnhtweaks.modules.chromatictooltips;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraftforge.fluids.FluidStack;

import com.slprime.chromatictooltips.api.TooltipRequest;
import com.slprime.chromatictooltips.api.TooltipTarget;

import cpw.mods.fml.common.Loader;
import gtnhtweaks.GTNHTweaks;

/**
 * What Chromatic Spectrum takes the colors of a tooltip from when the tooltip has only text. Chromatic Tooltips Compat
 * passes ModularUI and BetterQuesting tooltips as bare lines, so Chromatic Spectrum, which colors by the item or fluid
 * of the tooltip, falls back to its gray palette. The target is found when the tooltip is requested, kept with the
 * tooltip and given to Chromatic Spectrum while it is drawn; the content of the tooltip does not change.
 */
public final class PaletteTargets {

    private static final boolean MODULAR_UI = Loader.isModLoaded("modularui");
    private static final boolean MODULAR_UI2 = Loader.isModLoaded("modularui2");
    private static final boolean BETTER_QUESTING = Loader.isModLoaded("betterquesting");

    /** Fluid getters of ModularUI fluid widgets: getFluidStack() in ModularUI 2, getContent() in ModularUI. */
    private static final String[] FLUID_GETTERS = { "getFluidStack", "getContent" };
    private static final Method NO_METHOD;
    private static final Map<Class<?>, Method> FLUID_METHODS = new ConcurrentHashMap<>();

    static {
        Method none = null;
        try {
            none = Object.class.getDeclaredMethod("hashCode");
        } catch (NoSuchMethodException ignored) {}
        NO_METHOD = none;
    }

    private static TooltipTarget requested;
    private static TooltipTarget drawn;
    private static boolean failed;

    private PaletteTargets() {}

    /** Called when a tooltip is requested, before Chromatic Tooltips builds it. */
    public static void beginRequest(TooltipRequest request) {
        requested = request != null && isBare(request.target) ? find(request) : null;
    }

    public static void endRequest() {
        requested = null;
    }

    /** The target found for the tooltip being requested, kept by the tooltip built from it. */
    public static TooltipTarget requested() {
        return requested;
    }

    public static void beginDraw(TooltipTarget target) {
        drawn = target;
    }

    public static void endDraw() {
        drawn = null;
    }

    /** The target Chromatic Spectrum colors by: the found one in place of a bare target. */
    public static TooltipTarget forPalette(TooltipTarget target) {
        return drawn != null && isBare(target) ? drawn : target;
    }

    private static boolean isBare(TooltipTarget target) {
        return target == null || !target.isItem() && !target.isFluid();
    }

    private static TooltipTarget find(TooltipRequest request) {
        if (failed) return null;
        try {
            TooltipTarget target = null;
            if (MODULAR_UI2) target = ModularUI2Hover.hoveredTarget();
            if (target == null && MODULAR_UI) target = ModularUIHover.hoveredTarget();
            if (target == null && BETTER_QUESTING && "betterquesting".equals(request.context)) {
                target = QuestIcons.targetFor(request.tooltip);
            }
            return target;
        } catch (Throwable t) {
            // An API of ModularUI or BetterQuesting changed: leave the tooltips gray instead of failing every frame
            failed = true;
            GTNHTweaks.LOG.error("Failed to find what to color a Chromatic Tooltips tooltip by, coloring is off", t);
            return null;
        }
    }

    /** The fluid of a fluid widget of ModularUI or ModularUI 2, or null for other widgets. */
    static TooltipTarget fluidOf(Object widget) {
        if (widget == null) return null;
        Method getter = FLUID_METHODS.computeIfAbsent(widget.getClass(), PaletteTargets::findFluidGetter);
        if (getter == NO_METHOD) return null;
        try {
            FluidStack fluid = (FluidStack) getter.invoke(widget);
            return fluid != null && fluid.getFluid() != null ? TooltipTarget.ofFluid(fluid) : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method findFluidGetter(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : FLUID_GETTERS) {
                try {
                    Method method = c.getDeclaredMethod(name);
                    if (method.getReturnType() == FluidStack.class) {
                        method.setAccessible(true);
                        return method;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
        }
        return NO_METHOD;
    }

    /** Splits lines the way {@link com.slprime.chromatictooltips.api.TooltipLines} does. */
    static void splitLines(List<String> lines, List<Object> into) {
        for (String line : lines) {
            if (line == null) continue;
            for (String part : line.split("\n")) into.add(part);
        }
    }
}
