package android.content;

public class Intent {
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public final String action;
    public final android.net.Uri uri;
    public Intent(String action) { this(action, null); }
    public Intent(String action, android.net.Uri uri) { this.action = action; this.uri = uri; }
}
