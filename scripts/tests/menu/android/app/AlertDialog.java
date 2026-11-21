package android.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class AlertDialog implements android.content.DialogInterface {
    public static AlertDialog lastShown;
    private android.content.DialogInterface.OnDismissListener dismiss;
    public void setOnDismissListener(android.content.DialogInterface.OnDismissListener value) { dismiss = value; }
    public boolean isShowing() { return shown; }
    public void dismiss() { shown = false; if (dismiss != null) dismiss.onDismiss(this); }
    public static final int BUTTON_POSITIVE = -1, BUTTON_NEGATIVE = -2, BUTTON_NEUTRAL = -3;
    private final Context context;
    private final Window window;
    private final Button[] buttons = new Button[3];
    public boolean shown;

    public AlertDialog(Context context) {
        this.context = context;
        this.window = new Window(context);
        for (int i = 0; i < buttons.length; i++) buttons[i] = new Button(context);
    }
    public Context getContext() { return context; }
    public Window getWindow() { return window; }
    public Button getButton(int which) { return buttons[-which - 1]; }

    public static class Builder {
        private final AlertDialog dialog;
        public Builder(Context context) { dialog = new AlertDialog(context); }
        public Builder setView(View view) { ((ViewGroup) dialog.window.getDecorView()).addView(view); return this; }
        public Builder setTitle(String title) {
            TextView text = new TextView(dialog.context);
            text.setId(dialog.context.getResources().getIdentifier("alertTitle", "id", "android"));
            text.setText(title);
            return setView(text);
        }
        public Builder setPositiveButton(String title, final Runnable action) {
            Button button = dialog.getButton(BUTTON_POSITIVE);
            button.setText(title);
            button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { action.run(); }
            });
            return this;
        }
        public Builder setPositiveButton(String title, final android.content.DialogInterface.OnClickListener action) {
            Button button = dialog.getButton(BUTTON_POSITIVE);
            button.setText(title);
            button.setOnClickListener(view -> { if (action != null) action.onClick(dialog, BUTTON_POSITIVE); });
            return this;
        }
        public Builder setNegativeButton(String title, final android.content.DialogInterface.OnClickListener action) {
            Button button = dialog.getButton(BUTTON_NEGATIVE);
            button.setText(title);
            button.setOnClickListener(view -> { if (action != null) action.onClick(dialog, BUTTON_NEGATIVE); dialog.dismiss(); });
            return this;
        }
        public AlertDialog show() { dialog.shown = true; lastShown = dialog; return dialog; }
    }
}
