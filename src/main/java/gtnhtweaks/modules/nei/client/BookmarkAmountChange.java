package gtnhtweaks.modules.nei.client;

import java.lang.reflect.Method;
import java.math.BigInteger;

import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkItem;

/** Uses NEI's own item/recipe update path, replacing wheel-step snapping with an exact number of unit steps. */
public final class BookmarkAmountChange {

    private static final ThreadLocal<BookmarkAmountChange> ACTIVE = new ThreadLocal<>();
    private static final Method FROM_AMOUNT = method("getMultiplierFromAmount");
    private static final Method GET_AMOUNT = method("getAmount");
    private final BookmarkGrid grid;
    private final long delta;

    private BookmarkAmountChange(BookmarkGrid grid, long delta) {
        this.grid = grid;
        this.delta = delta;
    }

    private static Method method(String name) {
        try {
            return BookmarkItem.class.getMethod(name, long.class);
        } catch (NoSuchMethodException e) {
            return null; // NEI 2.7 uses amount/factor; NEI 2.8 stores multipliers and chance separately.
        }
    }

    public static long multiplier(BookmarkItem item, long count) {
        if (item.factor <= 0) throw new IllegalArgumentException("This bookmark has no adjustable quantity");
        try {
            long amount = Math.multiplyExact(count, Math.max(1, item.fluidCellAmount));
            long multiplier = FROM_AMOUNT == null ? BigInteger.valueOf(amount)
                .add(BigInteger.valueOf(item.factor - 1))
                .divide(BigInteger.valueOf(item.factor))
                .longValueExact() : (Long) FROM_AMOUNT.invoke(item, amount);
            if (multiplier < 0 || multiplier > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Quantity exceeds NEI's multiplier limit");
            }
            long actual = GET_AMOUNT == null ? Math.multiplyExact(item.factor, multiplier)
                : (Long) GET_AMOUNT.invoke(item, multiplier);
            if (item.getStackSize(actual) != count) {
                throw new IllegalArgumentException("Use a quantity matching a whole recipe / wheel step");
            }
            return multiplier;
        } catch (ReflectiveOperationException | ArithmeticException e) {
            throw new IllegalArgumentException("Quantity cannot be represented by this NEI bookmark");
        }
    }

    public static void apply(BookmarkGrid grid, int index, long multiplier) {
        long delta = multiplier - grid.getCalculatedItem(index)
            .getMultiplier();
        if (delta == 0) return;
        ACTIVE.set(new BookmarkAmountChange(grid, delta));
        try {
            grid.shiftItemAmount(index, 1);
        } finally {
            ACTIVE.remove();
        }
    }

    public static Long override(Object grid, long multiplier, int minimum) {
        BookmarkAmountChange change = ACTIVE.get();
        if (change == null || change.grid != grid) return null;
        if (multiplier + change.delta < minimum) {
            throw new IllegalArgumentException("This recipe must have at least one craft");
        }
        return Math.min(Integer.MAX_VALUE, Math.max(minimum, multiplier + change.delta));
    }
}
