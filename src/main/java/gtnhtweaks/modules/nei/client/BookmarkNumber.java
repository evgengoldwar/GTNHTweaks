package gtnhtweaks.modules.nei.client;

import java.math.BigDecimal;

/** Locale-independent editing: separators belong only to the integer part, never the fraction. */
public final class BookmarkNumber {

    private BookmarkNumber() {}

    public static String format(String input) {
        String raw = input.replace(",", "");
        if (!raw.matches("[0-9]*(\\.[0-9]*)?")) throw new IllegalArgumentException("Use digits and one decimal point");
        int dot = raw.indexOf('.');
        int end = dot < 0 ? raw.length() : dot;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (i > 0 && (end - i) % 3 == 0) result.append(',');
            result.append(raw.charAt(i));
        }
        if (dot >= 0) result.append(raw.substring(dot));
        return result.toString();
    }

    public static long amount(String input) {
        format(input);
        try {
            return new BigDecimal(input.replace(",", "")).longValueExact();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a quantity");
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("NEI requires a whole quantity within the 64-bit range");
        }
    }

    public static int rawCursor(String text, int cursor) {
        return text.substring(0, cursor).replace(",", "").length();
    }

    public static int formattedCursor(String text, int rawCursor) {
        int i = 0, count = 0;
        while (i < text.length() && count < rawCursor) if (text.charAt(i++) != ',') count++;
        return i;
    }
}
