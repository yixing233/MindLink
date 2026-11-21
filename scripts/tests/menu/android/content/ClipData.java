package android.content;

public class ClipData {
    public final CharSequence label, text;
    private ClipData(CharSequence label, CharSequence text) { this.label = label; this.text = text; }
    public static ClipData newPlainText(CharSequence label, CharSequence text) { return new ClipData(label, text); }
}
