package wex.ui.core.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rejects multiple or mutating SQL statements before they reach the host connection. */
final class ReadOnlySql {
    private ReadOnlySql() { }

    static boolean accepts(String sql) {
        if (sql == null) return false;
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean ended = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                flush(token, tokens);
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                flush(token, tokens);
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) return false;
                i = end + 1;
                continue;
            }
            if (Character.isWhitespace(c)) { flush(token, tokens); continue; }
            if (ended) return false;
            if (c == '\'' || c == '"' || c == '`' || c == '[') {
                flush(token, tokens);
                char closing = c == '[' ? ']' : c;
                boolean closed = false;
                while (++i < sql.length()) {
                    if (sql.charAt(i) == closing) {
                        if (c != '[' && i + 1 < sql.length() && sql.charAt(i + 1) == closing) { i++; continue; }
                        closed = true; break;
                    }
                }
                if (!closed) return false;
                continue;
            }
            if (c == ';') { flush(token, tokens); ended = true; continue; }
            if (Character.isLetterOrDigit(c) || c == '_') token.append(c);
            else flush(token, tokens);
        }
        flush(token, tokens);
        if (tokens.isEmpty() || !(tokens.get(0).equals("SELECT") || tokens.get(0).equals("WITH"))) return false;
        for (String value : tokens) {
            switch (value) {
                case "INSERT": case "UPDATE": case "DELETE": case "REPLACE": case "DROP":
                case "ALTER": case "CREATE": case "ATTACH": case "DETACH": case "VACUUM":
                case "REINDEX": case "ANALYZE": case "PRAGMA": case "LOAD_EXTENSION":
                    return false;
            }
        }
        return true;
    }

    private static void flush(StringBuilder value, List<String> tokens) {
        if (value.length() > 0) { tokens.add(value.toString().toUpperCase(Locale.ROOT)); value.setLength(0); }
    }
}
