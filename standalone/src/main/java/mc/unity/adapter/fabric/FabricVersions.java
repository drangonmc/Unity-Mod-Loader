package mc.unity.adapter.fabric;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import mc.unity.adapter.common.FmlDescriptor;
public final class FabricVersions {
    private static final Pattern TERM = Pattern.compile("(>=|<=|>|<|=|~|\\^)?(.+)");
    public static void check(String actual, String range) throws IOException {
        for (var alternative : range.split("\\|\\|", -1)) {
            if (alternative.isBlank())
                throw new IOException("Empty Fabric version alternative");
            boolean ok = true;
            for (String term : alternative.strip().split("\\s+")) ok &= test(actual, term);
            if (ok)
                return;
        }
        throw new IOException("Version " + actual + " does not satisfy Fabric constraint " + range);
    }
    private static boolean test(String actual, String term) throws IOException {
        if (term.equals("*"))
            return true;
        var m = TERM.matcher(term);
        if (!m.matches())
            throw new IOException("Invalid version predicate " + term);
        String op = Objects.requireNonNullElse(m.group(1), "="), value = m.group(2);
        if (value.matches("[0-9]+(?:\\.[0-9]+)*\\.[xX*]")) {
            if (!op.equals("="))
                throw new IOException("Invalid wildcard predicate " + term);
            String[] parts = value.split("\\.");
            String[] nums = actual.split("[-+]")[0].split("\\.");
            for (int i = 0; i < parts.length - 1; i++)
                if (!parts[i].equals(i < nums.length ? nums[i] : "0"))
                    return false;
            return true;
        }
        int c;
        try {
            if (value.endsWith("-")) {
                String boundary = value.substring(0, value.length() - 1);
                c = FmlDescriptor.compare(actual.split("[-+]", 2)[0], boundary);
                if (c == 0) c = 1;
            } else c = FmlDescriptor.compare(actual, value);
        } catch (IOException e) {
            if (op.equals("="))
                return actual.equals(value);
            throw e;
        }
        String[] b = value.split("[-+]")[0].split("\\.");
        return switch (op) {
            case ">=" -> c >= 0;
            case "<=" -> c <= 0;
            case ">" -> c > 0;
            case "<" -> c < 0;
            case "=" -> c == 0;
            case "^", "~" -> c >= 0 && belowUpper(actual, b, op.equals("^"));
            default -> false;
        };
    }
    // Unity's requested npm-style upper bounds, including partial and pre-1.0 releases.
    private static boolean belowUpper(String actual, String[] base, boolean caret) throws IOException {
        int bump = caret ? 0 : Math.min(1, base.length - 1);
        if (caret)
            while (bump < Math.min(base.length - 1, 2) && new java.math.BigInteger(base[bump]).signum() == 0)
                bump++;
        var upper = new ArrayList<String>();
        for (int i = 0; i < bump; i++) upper.add(base[i]);
        upper.add(new java.math.BigInteger(base[bump]).add(java.math.BigInteger.ONE).toString());
        while (upper.size() < 3) upper.add("0");
        return FmlDescriptor.compare(actual, String.join(".", upper) + "-0") < 0;
    }
}
