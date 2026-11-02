package dev.chatchronicle.core;

import com.google.re2j.Pattern;
import java.util.Locale;

/** RE2 avoids unbounded regex backtracking on the archive worker. */
public record SearchQuery(String text, String fromDay, String toDay, boolean regex,
                          boolean caseSensitive, boolean wholeWord) {
    public Pattern compile() {
        String expression = regex ? text : Pattern.quote(text);
        if (wholeWord) expression = "(^|[^\\p{L}\\p{N}_])(?:" + expression + ")($|[^\\p{L}\\p{N}_])";
        return Pattern.compile(expression, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
    }
    public String indexedLiteral() {
        return !regex && text.codePointCount(0, text.length()) >= 3
                ? text.toLowerCase(Locale.ROOT) : null;
    }
}
