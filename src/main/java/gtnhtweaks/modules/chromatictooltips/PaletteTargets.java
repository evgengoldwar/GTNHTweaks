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
 * tooltip and returned as its target while it is drawn, which is when Chromatic Spectrum reads it; the content of the
 * tooltip, built before, does not change.
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
    private static boolean screenFailed;
    private static boolean questsFailed;
    private static boolean loggedFound;

    private PaletteTargets() {}

    /** Called when a tooltip is requested, before Chromatic Tooltips builds it. */
    public static void beginRequest(TooltipRequest request) {
        if (request == null || !isBare(request.target)) {
            requested = null;
            return;
        }
        TooltipTarget target = hoveredOnScreen();
        if (target == null && BETTER_QUESTING && "betterquesting".equals(request.context)) {
            target = questIcon(request);
        }
        requested = target;
        if (target != null && !loggedFound) {
            loggedFound = true;
            GTNHTweaks.LOG.info("Coloring a Chromatic Tooltips tooltip of bare lines by its {}", describe(target));
        }
    }

    private static String describe(TooltipTarget target) {
        if (target.isFluid()) return "fluid " + target.getFluid()
            .getFluid()
            .getName();
        return "item " + target.getItem();
    }

    public static void endRequest() {
        requested = null;
    }

    /** The target found for the tooltip being requested, kept by the tooltip built from it. */
    public static TooltipTarget requested() {
        return requested;
    }

    public static boolean isBare(TooltipTarget target) {
        return target == null || !target.isItem() && !target.isFluid();
    }

    /**
     * The fluid or item of the ModularUI widget under the mouse, whose tooltip is shown. Also called while the tooltip
     * is drawn, for tooltips built without a request.
     */
    public static TooltipTarget hoveredOnScreen() {
        if (screenFailed) return null;
        try {
            TooltipTarget target = null;
            if (MODULAR_UI2) target = ModularUI2Hover.hoveredTarget();
            if (target == null && MODULAR_UI) target = ModularUIHover.hoveredTarget();
            return target;
        } catch (Throwable t) {
            // An API of ModularUI changed: leave the tooltips gray instead of failing every frame
            screenFailed = true;
            GTNHTweaks.LOG.error("Failed to find the hovered ModularUI widget, its tooltips stay gray", t);
            return null;
        }
    }

    private static TooltipTarget questIcon(TooltipRequest request) {
        if (questsFailed) return null;
        try {
            return QuestIcons.targetFor(request.tooltip);
        } catch (Throwable t) {
            questsFailed = true;
            GTNHTweaks.LOG.error("Failed to find the icon of the hovered quest, quest tooltips stay gray", t);
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
