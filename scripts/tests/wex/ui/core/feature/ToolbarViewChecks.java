package wex.ui.core.feature;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

public final class ToolbarViewChecks {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static LinearLayout vertical() {
        LinearLayout layout = new LinearLayout();
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static void main(String[] args) {
        check(ChatToolbarViews.findInputContainer(null) == null, "null input");
        ViewGroup footer = new ViewGroup();
        check(ChatToolbarViews.findInputContainer(footer) == null, "not yet inflated");
        LinearLayout input = vertical();
        input.addView(new EditText());
        footer.addView(input);
        check(ChatToolbarViews.findInputContainer(footer) == input,
                "non-FrameLayout footer with input child");
        check(ChatToolbarViews.findInputContainer(input) == input, "root is valid container");
        LinearLayout horizontal = new LinearLayout();
        horizontal.addView(new EditText());
        check(ChatToolbarViews.findInputContainer(horizontal) == null, "do not insert into horizontal input row");
        LinearLayout empty = vertical();
        empty.addView(new View());
        check(ChatToolbarViews.findInputContainer(empty) == null, "decorative layout is not input container");
        ViewGroup nestedFooter = new ViewGroup();
        ViewGroup wrapper = new ViewGroup();
        wrapper.addView(input);
        nestedFooter.addView(empty);
        nestedFooter.addView(wrapper);
        check(ChatToolbarViews.findInputContainer(nestedFooter) == input, "nested input container");
        footer.setVisibility(View.GONE);
        check(ChatToolbarViews.findInputContainer(footer) == null, "known/fallback paths reject a hidden root");
        footer.setVisibility(View.VISIBLE);
        LinearLayout wide = vertical();
        for (int i=0;i<600;i++) {
            View hidden = new View(); hidden.setVisibility(View.GONE); wide.addView(hidden);
        }
        wide.addView(new EditText());
        check(ChatToolbarViews.findInputContainer(wide) == wide, "hidden siblings do not consume the eligible-node budget");
        LinearLayout decoy = vertical();
        android.widget.AbsListView list = new android.widget.AbsListView(); list.addView(new EditText()); decoy.addView(list);
        check(ChatToolbarViews.findInputContainer(decoy) == null, "list editors are not toolbar hosts");
        LinearLayout hiddenEditor = vertical(); EditText editor = new EditText();
        editor.setVisibility(View.GONE); hiddenEditor.addView(editor);
        check(ChatToolbarViews.findInputContainer(hiddenEditor) == null, "hidden editor is not an input target");
        System.out.println("PASS: toolbar input selection, visibility, hidden sibling budget, list/horizontal/decorative exclusion");
    }
}
