package wex.ui.core.feature;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Restorable integrated and separated header visuals, following WeKit's geometry. */
final class FloatingHeaderStyle {
    private static final String TAG_SHADOW_LAYER = "wex.floating_header.shadow_layer";
    private FloatingHeaderStyle() { }

    static final class CardState {
        final View view; final Drawable background; final ViewOutlineProvider outline;
        final boolean clip; final float elevation, translation; final android.animation.StateListAnimator animator;
        final ViewOutlineProvider owned;
        /** Card height limit in px read by {@link #owned}; -1 = the whole view. */
        final int[] heightLimit;
        CardState(View view, ViewOutlineProvider owned, int[] heightLimit) {
            this.view=view; background=view.getBackground(); outline=view.getOutlineProvider(); clip=view.getClipToOutline();
            elevation=view.getElevation(); translation=view.getTranslationZ(); animator=view.getStateListAnimator(); this.owned=owned;
            this.heightLimit=heightLimit;
        }
    }

    static CardState applyCard(View view, CardState state, int cornerDp, int elevationDp) {
        return applyCard(view, state, cornerDp, elevationDp, ChatGlassSurface.PLAIN, 0);
    }

    /** Same card fit, optionally carrying the home-bar liquid glass instead of a solid fill. */
    static CardState applyCard(View view, CardState state, int cornerDp, int elevationDp,
                               int glassMode, int glassBlurDp) {
        final float density = view.getResources().getDisplayMetrics().density;
        final float radius = cornerDp * density;
        if (state == null || state.view != view) {
            // Never snapshot our own material as the host's background: a stale glass
            // surface must not come back on restore.
            if (view.getBackground() instanceof ChatGlassSurface) {
                ((ChatGlassSurface) view.getBackground()).stop();
                view.setBackground(null);
            }
            final int[] limit = {-1};
            ViewOutlineProvider owned = new ViewOutlineProvider() {
                @Override public void getOutline(View target, Outline outline) {
                    int height = limit[0] > 0 ? Math.min(limit[0], target.getHeight()) : target.getHeight();
                    outline.setRoundRect(0, 0, target.getWidth(), Math.max(1, height), radius);
                }
            };
            state = new CardState(view, owned, limit);
        }
        int color = sampleColor(view);
        Drawable current = view.getBackground();
        if (glassMode != ChatGlassSurface.PLAIN) {
            // The header keeps its own outline clip and elevation; only the fill changes.
            if (current instanceof ChatGlassSurface) {
                ((ChatGlassSurface) current).configure(color, radius, density,
                        SettingsTheme.isDark(view.getContext()), glassMode, glassBlurDp);
            } else {
                ChatGlassSurface surface = new ChatGlassSurface(view);
                surface.configure(color, radius, density, SettingsTheme.isDark(view.getContext()),
                        glassMode, glassBlurDp);
                view.setBackground(surface);
            }
        } else if (!(current instanceof GradientDrawable) || current == state.background) {
            if (current instanceof ChatGlassSurface) ((ChatGlassSurface) current).stop();
            GradientDrawable surface = new GradientDrawable(); surface.setColor(color);
            surface.setCornerRadius(radius); view.setBackground(surface);
        }
        view.setOutlineProvider(state.owned); view.setClipToOutline(true);
        view.setStateListAnimator(null); view.setElevation(elevationDp * view.getResources().getDisplayMetrics().density);
        return state;
    }

    static void restore(CardState state) {
        if (state == null || state.view == null) return;
        View view = state.view;
        state.heightLimit[0] = -1;
        if (view.getOutlineProvider() == state.owned) {
            if (view.getBackground() instanceof ChatGlassSurface) ((ChatGlassSurface) view.getBackground()).stop();
            view.setBackground(state.background); view.setOutlineProvider(state.outline); view.setClipToOutline(state.clip);
            view.setElevation(state.elevation); view.setTranslationZ(state.translation); view.setStateListAnimator(state.animator);
        }
    }

    /**
     * Limits the card to its top {@code height} px: the outline (clip and shadow) and the glass
     * stop there while the host view itself may stay taller. {@code height <= 0} or a height not
     * below the view's own releases the limit.
     */
    static void clampCard(CardState state, int height) {
        if (state == null || state.view == null) return;
        View view = state.view;
        int limit = height > 0 && height < view.getHeight() ? height : -1;
        if (state.heightLimit[0] != limit) { state.heightLimit[0] = limit; view.invalidateOutline(); }
        if (view.getBackground() instanceof ChatGlassSurface) {
            ((ChatGlassSurface) view.getBackground()).visibleHeight(limit > 0 ? limit : view.getHeight());
        }
    }

    static final class Visual {
        final View view; final Drawable background; final ViewOutlineProvider outline;
        final boolean clip; final float elevation, translation; final android.animation.StateListAnimator animator;
        Visual(View view){this.view=view;background=view.getBackground();outline=view.getOutlineProvider();clip=view.getClipToOutline();elevation=view.getElevation();translation=view.getTranslationZ();animator=view.getStateListAnimator();}
        void restore(){view.setBackground(background);view.setOutlineProvider(outline);view.setClipToOutline(clip);view.setElevation(elevation);view.setTranslationZ(translation);view.setStateListAnimator(animator);}
    }

    static final class SeparatedState {
        final View header; final RelativeLayout root; final FrameLayout layer; final SeparatedDecoration decoration;
        final List<Visual> visuals;
        final List<Visual> ancestors = new ArrayList<>();
        final List<ClipState> clips = new ArrayList<>();
        final TitleState title;
        final EdgePosition back, menu;
        int geometryLogs; String geometrySignature;
        SeparatedState(View h, RelativeLayout r, FrameLayout l, SeparatedDecoration d, List<Visual> v){
            header=h;root=r;layer=l;decoration=d;visuals=v;
            title = new TitleState(d.titleHost);
            back = new EdgePosition(d.backHost, false);
            menu = new EdgePosition(d.menuHost, true);
            int depth=0;
            for (View current = r; current != null && depth++ < 32;
                    current = current.getParent() instanceof View ? (View) current.getParent() : null) {
                if (current instanceof ViewGroup) clips.add(new ClipState((ViewGroup) current));
            }
            // A host card wrapper above the pills paints its own rectangular elevation
            // shadow even after the header itself is neutralized. Strip only nearby
            // wrappers (never the message surface) and restore them on exit.
            View current = h.getParent() instanceof View ? (View) h.getParent() : null;
            for (int level = 0; current != null && level < 4 && current != r; level++) {
                boolean card = current.getElevation() > 0f || opaque(current.getBackground());
                if (card && current.getHeight() <= Math.max(1, h.getHeight()) * 3) {
                    ancestors.add(new Visual(current));
                    current.setBackground(new ColorDrawable(Color.TRANSPARENT));
                    current.setOutlineProvider(null);
                    current.setClipToOutline(false);
                    current.setElevation(0f);
                    current.setTranslationZ(0f);
                    current.setStateListAnimator(null);
                }
                current = current.getParent() instanceof View ? (View) current.getParent() : null;
            }
        }

        /** The host re-parents the header between frames (notification entry, panel
         *  switch, style switch), so the clipping ancestor chain must be re-walked
         *  instead of captured once. A newly inserted ancestor that keeps clipping
         *  would cut the shadow into a straight-edged rectangle. */
        void syncClips() {
            for (View current = root; current != null && clips.size() < 64;
                    current = current.getParent() instanceof View ? (View) current.getParent() : null) {
                if (!(current instanceof ViewGroup)) continue;
                ViewGroup group = (ViewGroup) current;
                boolean known = false;
                for (ClipState clip : clips) if (clip.view == group) { known = true; break; }
                if (!known) clips.add(new ClipState(group));
            }
        }
    }

    private static boolean opaque(Drawable background) {
        return background instanceof ColorDrawable
                && Color.alpha(((ColorDrawable) background).getColor()) >= 128;
    }

    private static final class ClipState {
        final ViewGroup view; final boolean children, padding, outline; final android.graphics.Rect bounds;
        ClipState(ViewGroup view) {
            this.view=view;children=view.getClipChildren();padding=view.getClipToPadding();outline=view.getClipToOutline();
            android.graphics.Rect current=view.getClipBounds();
            bounds=current==null?null:new android.graphics.Rect(current);
        }
        /** The drawing layer always outdents by its shadow padding, so every ancestor
         *  rectangle would cut the blur into straight edges. Release unconditionally. */
        void apply(android.graphics.RectF shadow) {
            if (view.getClipToOutline()) view.setClipToOutline(false);
            if(view.getClipChildren())view.setClipChildren(false);
            if(view.getClipToPadding())view.setClipToPadding(false);
            if(view.getClipBounds()!=null)view.setClipBounds(null);
        }
        void restore() {
            view.setClipChildren(children);view.setClipToPadding(padding);view.setClipToOutline(outline);
            view.setClipBounds(bounds);
        }
    }

    /** Keep the painted outer edges on the header margin, moving the native hit hosts with them. */
    private static final class EdgePosition {
        final View view; final boolean right; float original, applied;
        EdgePosition(View view, boolean right) {
            this.view=view;this.right=right;original=applied=view.getTranslationX();
        }
        void apply(SeparatedDecoration decoration) {
            float current=view.getTranslationX();
            if(current!=applied)original=current;
            float delta;
            if(right){
                decoration.right.setEmpty();
                decoration.menuActions(view,decoration.root.getHeight());
                if(decoration.right.isEmpty()){restore();applied=view.getTranslationX();return;}
                delta=decoration.root.getWidth()-decoration.right.right;
            }else{
                decoration.backIcon(view,decoration.left);
                float center=decoration.left.isEmpty()?decoration.bounds(view).centerX():decoration.left.centerX();
                delta=decoration.root.getHeight()/2f-center;
            }
            float next=current+delta;
            if(current!=next)view.setTranslationX(next);
            applied=next;
        }
        void restore() { if(view.getTranslationX()==applied)view.setTranslationX(original); }
    }

    /**
     * Title text widths under the centred title host.
     *
     * <p>WeChat's chat title is not a platform TextView: it is {@code NoMeasuredTextView}
     * (extends X2CView). Its {@code setText} bakes the ellipsis into the stored text using
     * {@code min(maxWidth, measuredWidth)} at that moment, and WeChat gives it a maxWidth from the
     * native header geometry. Inside a floating card that limit can be narrower than the space the
     * view really has, so even "对方正在输入…" came out truncated next to visible free room. Its
     * measured width always equals the width its parent offers, so pinning maxWidth to the view's
     * own laid-out width removes the stale limit without ever letting text overflow.</p>
     *
     * <p>The host adds and swaps title views (typing status, subtitles), so the tree is rescanned
     * on every apply; each view keeps the first original it was seen with.</p>
     */
    static final class TitleState {
        final View view;
        ViewGroup.LayoutParams params;
        int originalWidth, appliedWidth=-1;
        /** Horizontal shift that centres the host in a pill wider on one side; 0 for the centred pill. */
        float originalShift, appliedShift; boolean shifted;
        final java.util.Map<View, TextFit> texts=new java.util.WeakHashMap<>();
        /** Fixed widths inside the title host that capped the text below the pill: {host value}. */
        final java.util.Map<View, int[]> fixed=new java.util.WeakHashMap<>();
        /** Host horizontal padding before we cleared it, and the values we wrote (left, right). */
        int padLeft, padRight; boolean padOwned;
        int diagnoseLogs; String diagnosed;
        TitleState(View view) { this.view=view; scan(); }
        private void scan() {
            ArrayDeque<View> queue=new ArrayDeque<>();queue.add(view);
            for(int count=0;!queue.isEmpty()&&count<64;count++){
                View child=queue.removeFirst();
                if(!texts.containsKey(child)){
                    if(child instanceof android.widget.TextView)texts.put(child,new TextWidth((android.widget.TextView)child));
                    else if(NoMeasuredWidth.matches(child))texts.put(child,new NoMeasuredWidth(child));
                }
                if(child instanceof ViewGroup){ViewGroup group=(ViewGroup)child;for(int i=0;i<group.getChildCount();i++)queue.add(group.getChildAt(i));}
            }
        }
        void apply(int width) { apply(width, 0f); }
        void apply(int width, float shift) {
            ViewGroup.LayoutParams current=view.getLayoutParams();
            if(current==null||width<=0)return;
            if(params!=current){params=current;originalWidth=current.width;appliedWidth=-1;}
            // The host keeps its own side padding (it re-applies it on every layout, so clearing
            // it only raced it): the host is made wider by that padding instead, so the content
            // area between the paddings is exactly the pill, whichever of the two wins a frame.
            int padL=view.getPaddingLeft(), padR=view.getPaddingRight();
            int outer=width+padL+padR;
            if(current.width!=outer){current.width=outer;view.setLayoutParams(current);}
            appliedWidth=outer;
            float now=view.getTranslationX();
            if(!shifted){originalShift=now;shifted=true;}
            else if(now!=appliedShift)originalShift=now; // the host moved it since
            // Unequal paddings move the content off the host centre; keep the content on the pill.
            float next=originalShift+shift-(padL-padR)/2f;
            if(now!=next)view.setTranslationX(next);
            appliedShift=next;
            restorePadding();
            int inner=Math.max(1,width);
            liftFixed(inner);
            lift(inner,true);
            diagnose(width);
        }
        /**
         * WeChat pads the centred title host on both sides by the width of the native side
         * buttons (168 px each on this device) and writes that padding again during layout. The
         * previous build cleared it before each draw; the host restored it in the next measure,
         * so most opens settled at 342 px content (pill 678 - 2 x 168) and only some at 678.
         * Padding cleared by that build is given back so the host's own value is used again.
         */
        private void restorePadding() {
            if(!padOwned)return;
            padOwned=false;
            if(view.getPaddingLeft()==0&&view.getPaddingRight()==0)
                view.setPadding(padLeft,view.getPaddingTop(),padRight,view.getPaddingBottom());
        }
        /**
         * WeChat sizes parts of the title (wrapper layouts, the name view) to px computed from the
         * native header. Inside the wider floating pill those fixed widths became the real limit:
         * past it the name was cut although the pill had room. Containers and text views with a
         * fixed width below the pill wrap their content instead; icons keep their size.
         */
        private void liftFixed(int width) {
            ArrayDeque<View> queue=new ArrayDeque<>();queue.add(view);
            for(int count=0;!queue.isEmpty()&&count<64;count++){
                View child=queue.removeFirst();
                if(child!=view&&(child instanceof ViewGroup||texts.containsKey(child))){
                    ViewGroup.LayoutParams p=child.getLayoutParams();
                    if(p!=null&&p.width>0&&p.width<width){
                        if(!fixed.containsKey(child))fixed.put(child,new int[]{p.width});
                        else fixed.get(child)[0]=p.width; // the host wrote a new fixed width
                        p.width=ViewGroup.LayoutParams.WRAP_CONTENT;child.setLayoutParams(p);
                    }
                }
                if(child instanceof ViewGroup){ViewGroup group=(ViewGroup)child;for(int i=0;i<group.getChildCount();i++)queue.add(group.getChildAt(i));}
            }
        }
        /** One line per distinct title layout (max 8): widths and limits down the title tree. */
        private void diagnose(int width) {
            if(diagnoseLogs>=8)return;
            StringBuilder out=new StringBuilder("pill=").append(width);
            ArrayDeque<View> queue=new ArrayDeque<>();queue.add(view);ArrayDeque<Integer> depths=new ArrayDeque<>();depths.add(0);
            for(int count=0;!queue.isEmpty()&&count<16;count++){
                View child=queue.removeFirst();int depth=depths.removeFirst();
                if(!child.isShown())continue;
                ViewGroup.LayoutParams p=child.getLayoutParams();
                out.append(" |").append(depth).append(':').append(child.getClass().getSimpleName())
                        .append(" w=").append(child.getWidth()).append(" lp=").append(p==null?"none":String.valueOf(p.width))
                        .append(" pad=").append(child.getPaddingLeft()).append(',').append(child.getPaddingRight());
                TextFit fit=texts.get(child);
                if(fit!=null)out.append(' ').append(fit.describe());
                if(child instanceof ViewGroup){ViewGroup group=(ViewGroup)child;for(int i=0;i<group.getChildCount();i++){queue.add(group.getChildAt(i));depths.add(depth+1);}}
            }
            String line=out.toString();
            if(line.equals(diagnosed))return;
            diagnosed=line;diagnoseLogs++;
            TitleSource.log("title tree "+line);
        }
        /**
         * True when a shown title text would be cut in a host {@code hostWidth} wide: its whole
         * text (for WeChat's title the uncut source plus its kept suffix such as "(211)") and what
         * its siblings in the host take now. Measured against the centred width, never against
         * the current one, so widening the pill cannot flip the decision back next frame.
         */
        boolean overflows(int hostWidth) {
            if(hostWidth<=0)return false;
            scan();
            int host=view.getWidth();
            for(java.util.Map.Entry<View,TextFit> entry:texts.entrySet()){
                View text=entry.getKey();
                if(!text.isShown()||text.getWidth()<=0)continue;
                float needed=entry.getValue().needed();
                if(needed<=0f)continue;
                int others=text==view||host<=0?0:Math.max(0,host-text.getWidth());
                if(needed+others>hostWidth+0.5f)return true;
            }
            return false;
        }
        /**
         * Lifts text limits without resizing the host. {@code textViews} false leaves platform
         * TextViews to WeChat (integrated card, where the host keeps its native width).
         */
        void lift(int width, boolean textViews) {
            scan();
            for(TextFit text:texts.values()){
                if(text instanceof NoMeasuredWidth)text.apply(0);
                else if(textViews)text.apply(width);
            }
        }
        void restore() {
            if(params!=null&&view.getLayoutParams()==params&&params.width==appliedWidth){
                params.width=originalWidth;view.setLayoutParams(params);
            }
            if(shifted&&view.getTranslationX()==appliedShift)view.setTranslationX(originalShift);
            shifted=false;
            if(padOwned&&view.getPaddingLeft()==0&&view.getPaddingRight()==0)
                view.setPadding(padLeft,view.getPaddingTop(),padRight,view.getPaddingBottom());
            padOwned=false;
            for(java.util.Map.Entry<View,int[]> entry:fixed.entrySet()){
                ViewGroup.LayoutParams p=entry.getKey().getLayoutParams();
                if(p!=null&&p.width==ViewGroup.LayoutParams.WRAP_CONTENT){p.width=entry.getValue()[0];entry.getKey().setLayoutParams(p);}
            }
            fixed.clear();
            for(TextFit text:texts.values())text.restore();
            texts.clear();
        }
    }

    interface TextFit {
        void apply(int width); void restore();
        /** Width the whole text needs including the view's own horizontal padding; 0 when unknown. */
        float needed();
        /** Short diagnostic: limit and shown/source text lengths, no text content. */
        String describe();
    }

    private static final class TextWidth implements TextFit {
        final android.widget.TextView view; final int original; int applied=-1;
        TextWidth(android.widget.TextView view){this.view=view;original=view.getMaxWidth();}
        @Override public void apply(int width){if(view.getMaxWidth()!=width)view.setMaxWidth(width);applied=width;}
        @Override public void restore(){if(view.getMaxWidth()==applied)view.setMaxWidth(original);}
        @Override public String describe(){CharSequence t=view.getText();return "max="+view.getMaxWidth()+" len="+(t==null?0:t.length())+" need="+Math.round(needed());}
        @Override public float needed(){
            CharSequence text=view.getText();
            if(text==null||text.length()==0)return 0f;
            android.text.TextPaint paint=view.getPaint();
            if(paint==null)return 0f;
            return paint.measureText(text,0,text.length())+view.getPaddingLeft()+view.getPaddingRight();
        }
    }

    /** WeChat's {@code com.tencent.mm.ui.base.NoMeasuredTextView}: public {@code setMaxWidth(int)}, limit in public fields v / w. */
    private static final class NoMeasuredWidth implements TextFit {
        private static final String NAME="com.tencent.mm.ui.base.NoMeasuredTextView";
        private static final java.util.Map<Class<?>, Boolean> TYPES=new java.util.HashMap<>();
        final View view; final java.lang.reflect.Method setter, setText; final java.lang.reflect.Field max, enabled;
        final int originalMax; final boolean originalEnabled; int applied=-1;
        int waits;
        static boolean matches(View view){
            Class<?> type=view.getClass(); Boolean known=TYPES.get(type);
            if(known==null){
                boolean found=false;
                for(Class<?> current=type;current!=null&&!found;current=current.getSuperclass())found=NAME.equals(current.getName());
                TYPES.put(type,known=found);
            }
            return known;
        }
        NoMeasuredWidth(View view){
            this.view=view;
            java.lang.reflect.Method method=null,text=null;java.lang.reflect.Field m=null,e=null;int om=Integer.MAX_VALUE;boolean oe=false;
            try{method=view.getClass().getMethod("setMaxWidth",int.class);}catch(ReflectiveOperationException ignored){}
            try{text=view.getClass().getMethod("setText",CharSequence.class);}catch(ReflectiveOperationException ignored){}
            try{
                // Fields are optional (obfuscated names may move); they only make restore exact.
                java.lang.reflect.Field fm=view.getClass().getField("v"),fe=view.getClass().getField("w");
                if(fm.getType()==int.class&&fe.getType()==boolean.class){m=fm;e=fe;om=fm.getInt(view);oe=fe.getBoolean(view);}
            }catch(ReflectiveOperationException|RuntimeException ignored){}
            setter=method;setText=text;max=m;enabled=e;originalMax=om;originalEnabled=oe;
            TitleSource.install(view.getClass());
        }
        private int current(){
            try{return max!=null&&enabled!=null&&enabled.getBoolean(view)?max.getInt(view):-1;}
            catch(ReflectiveOperationException ignored){return applied;}
        }
        /**
         * The view's own laid-out width: its parent's offer, never more. Raising the limit is
         * not enough on its own: the view stores only the text already cut at set time (often
         * while the floating card was still being laid out, which gave "🏓…(211)"). So once the
         * laid-out width differs from the one the text was cut for, the host's original text
         * (and suffix, e.g. the member count) is set again and cut for the real width.
         */
        @Override public void apply(int ignored){
            int width=view.getWidth();
            if(setter==null||width<=0||!view.isShown())return;
            int now=max==null?applied:current();
            if(now!=width){
                try{setter.invoke(view,width);applied=width;}
                catch(ReflectiveOperationException ignoredError){return;}
            }
            TitleSource source=TitleSource.of(view);
            if(source==null||setText==null)return;
            // Re-cut when the stored text was cut for another width, or when it was cut well short
            // of the room it has now (cut while the card was still laying out, or by the host's
            // own suffix callback with native widths). Bounded per text and width.
            boolean stale=source.width!=width||source.underfilled(view,width);
            if(!stale){waits=0;return;}
            // A title relaid out every frame must not starve the re-cut forever.
            if(view.isLayoutRequested()&&++waits<=2)return;
            waits=0;
            source.reapply(view,setText,width);
        }
        @Override public String describe(){
            TitleSource source=TitleSource.of(view);
            CharSequence shown=shown(view);
            return "max="+(max==null?applied:current())+" len="+(shown==null?0:shown.length())
                    +" src="+(source==null||source.text==null?"none":String.valueOf(source.text.length()))
                    +" sfx="+(source==null||source.suffix==null?0:source.suffix.length())
                    +" cutW="+(source==null?-2:source.width)+" need="+Math.round(needed());
        }
        /** The uncut title plus its kept suffix when the source is known, else the shown text. */
        @Override public float needed(){
            android.text.TextPaint paint=TitleSource.paint(view);
            if(paint==null)return 0f;
            TitleSource source=TitleSource.of(view);
            CharSequence text=null;
            if(source!=null&&source.text!=null&&source.text.length()>0)
                text=source.suffix==null?source.text:android.text.TextUtils.concat(source.text,source.suffix);
            if(text==null){
                try{Object value=view.getClass().getMethod("getText").invoke(view);if(value instanceof CharSequence)text=(CharSequence)value;}
                catch(ReflectiveOperationException|RuntimeException ignored){}
            }
            if(text==null||text.length()==0)return 0f;
            return paint.measureText(text,0,text.length())+TitleSource.horizontalPadding(view);
        }
        @Override public void restore(){
            if(applied<0||max==null||enabled==null)return;
            try{
                if(current()!=applied)return; // WeChat set its own limit since; keep it.
                max.setInt(view,originalMax);enabled.setBoolean(view,originalEnabled);
                view.requestLayout();view.invalidate();
            }catch(ReflectiveOperationException ignored){}
        }
    }

    private static CharSequence shown(View view){
        try{Object value=view.getClass().getMethod("getText").invoke(view);return value instanceof CharSequence?(CharSequence)value:null;}
        catch(ReflectiveOperationException|RuntimeException ignored){return null;}
    }

    /**
     * The uncut title WeChat last gave a header {@code NoMeasuredTextView}: its text plus the
     * keep-at-end suffix set through {@code g(String,int,int)} (public fields L / M / N, used once
     * by the next set). Recorded by a hook on the view's internal text setter; calls made by the
     * view itself from {@code onLayout} (its deferred re-cut of the stored text) are not sources.
     * The hook is installed at header-feature init, before the first chat title is set. Only
     * views inside an action bar (or not attached yet, as during inflation) are recorded, so the
     * conversation list's many NoMeasuredTextViews pay one short parent walk and nothing else.
     */
    static final class TitleSource {
        private static final java.util.Map<View, TitleSource> SOURCES=new java.util.WeakHashMap<>();
        private static final java.util.Set<Class<?>> HOOKED=new java.util.HashSet<>();
        private static final java.util.Map<View, Boolean> LAYOUT=new java.util.WeakHashMap<>();
        private static int logs;
        CharSequence text; String suffix;
        /** Laid-out width when the text was last cut; -1 = unknown. */
        int width=-1;
        /** Re-cuts spent on this source text at {@link #recutWidth}; a host that keeps cutting it wins after 3. */
        int recuts, recutWidth=-1;
        private boolean reapplying;

        static TitleSource of(View view){return SOURCES.get(view);}

        private static boolean header(View view){
            if(!view.isAttachedToWindow())return true;
            Object parent=view.getParent();
            for(int depth=0;parent instanceof View&&depth<16;depth++,parent=((View)parent).getParent()){
                String name=parent.getClass().getName();
                if("androidx.appcompat.widget.ActionBarContainer".equals(name)||"androidx.appcompat.widget.Toolbar".equals(name))return true;
            }
            return false;
        }

        static void install(Class<?> type){
            Class<?> owner=null;
            for(Class<?> current=type;current!=null;current=current.getSuperclass())if(NAME_OF(current))owner=current;
            if(owner==null||!HOOKED.add(owner))return;
            java.lang.reflect.Method setter=null;
            for(java.lang.reflect.Method method:owner.getDeclaredMethods()){
                Class<?>[] p=method.getParameterTypes();
                // h(CharSequence, BufferType-like enum): every public setText funnels into it.
                if(p.length==2&&p[0]==CharSequence.class&&p[1].isEnum()&&method.getReturnType()==void.class){setter=method;break;}
            }
            try{
                if(setter==null)setter=owner.getDeclaredMethod("setText",CharSequence.class);
                dev.mindlink.xposed.XposedBridge.hookMethod(setter,new dev.mindlink.xposed.XC_MethodHook(){
                    @Override protected void beforeHookedMethod(MethodHookParam param){record((View)param.thisObject,param.args[0]);}
                    @Override protected void afterHookedMethod(MethodHookParam param){cut((View)param.thisObject);}
                });
                java.lang.reflect.Method layout=owner.getDeclaredMethod("onLayout",boolean.class,int.class,int.class,int.class,int.class);
                dev.mindlink.xposed.XposedBridge.hookMethod(layout,new dev.mindlink.xposed.XC_MethodHook(){
                    @Override protected void beforeHookedMethod(MethodHookParam param){if(SOURCES.get(param.thisObject)!=null)LAYOUT.put((View)param.thisObject,Boolean.TRUE);}
                    @Override protected void afterHookedMethod(MethodHookParam param){LAYOUT.remove(param.thisObject);}
                });
            }catch(ReflectiveOperationException|RuntimeException|LinkageError error){
                log("title source hook unavailable "+error.getClass().getSimpleName());
            }
        }

        private static boolean NAME_OF(Class<?> type){return "com.tencent.mm.ui.base.NoMeasuredTextView".equals(type.getName());}

        private static void record(View view,Object value){
            if(LAYOUT.containsKey(view))return;
            TitleSource source=SOURCES.get(view);
            if(source!=null&&source.reapplying)return;
            if(source==null&&!header(view))return;
            if(source==null){source=new TitleSource();SOURCES.put(view,source);}
            source.text=value instanceof CharSequence?(CharSequence)value:""; // spans (emoji) kept as given
            source.suffix=stringField(view,"L");
            source.width=-1;
            source.recuts=0;
        }

        /**
         * After WeChat's set: note the width the stored text was cut for. A text that came out
         * shorter than its source was cut, possibly by WeChat's own narrower limit or callback
         * widths rather than the view's width; mark it unknown so the next apply re-cuts it once.
         */
        private static void cut(View view){
            TitleSource source=SOURCES.get(view);
            if(source==null||source.reapplying)return;
            int full=(source.text==null?0:source.text.length())+(source.suffix==null?0:source.suffix.length());
            CharSequence shown=null;
            try{Object value=view.getClass().getMethod("getText").invoke(view);if(value instanceof CharSequence)shown=(CharSequence)value;}
            catch(ReflectiveOperationException|RuntimeException ignored){}
            boolean truncated=shown==null||shown.length()<full;
            source.width=!truncated&&view.getMeasuredWidth()>0?view.getWidth():-1;
        }

        /**
         * Cuts the title for {@code target} ourselves rather than through the host's layout
         * callback, whose width inputs (M / N) come from the native header geometry: the name is
         * ellipsized to what is left after the suffix, and the suffix ("(211)") always stays whole.
         * Without a suffix the view's own cut is used; its limit is already {@code target}.
         */
        /**
         * The stored text is a cut of the source although the whole source fits in
         * {@code width}, or the cut stops more than two characters short of the room.
         */
        boolean underfilled(View view,int width){
            if(text==null||text.length()==0||reapplying)return false;
            if(recutWidth==width&&recuts>=3)return false;
            CharSequence shownText=shown(view);
            if(shownText==null)return false;
            CharSequence full=suffix==null?text:android.text.TextUtils.concat(text,suffix);
            if(shownText.toString().equals(full.toString()))return false;
            android.text.TextPaint paint=paint(view);
            if(paint==null)return false;
            float avail=width-horizontalPadding(view);
            if(paint.measureText(full,0,full.length())<=avail)return true;
            return paint.measureText(shownText,0,shownText.length())<avail-2f*paint.getTextSize();
        }

        void reapply(View view,java.lang.reflect.Method setText,int target){
            if(text==null||text.length()==0)return;
            if(recutWidth!=target){recutWidth=target;recuts=0;}
            if(recuts>=3){width=target;return;}
            recuts++;
            reapplying=true;
            try{
                CharSequence value=text;
                android.text.TextPaint paint=paint(view);
                if(suffix!=null&&paint!=null){
                    float avail=target-horizontalPadding(view);
                    CharSequence full=android.text.TextUtils.concat(text,suffix);
                    if(paint.measureText(full,0,full.length())<=avail)value=full;
                    else{
                        float room=Math.max(0f,avail-paint.measureText(suffix));
                        value=android.text.TextUtils.concat(android.text.TextUtils.ellipsize(text,paint,room,android.text.TextUtils.TruncateAt.END),suffix);
                    }
                }
                // No pending suffix: the host callback must not cut the prepared text again.
                setField(view,"L",null);
                setText.invoke(view,value);
                width=target;
                if(logs<4){logs++;log("title re-cut width="+target+" text="+text.length()+" suffix="+(suffix==null?"none":suffix.length())+" shown="+value.length());}
            }catch(ReflectiveOperationException|RuntimeException error){
                width=target; // do not retry every frame
                log("title re-cut failed "+error.getClass().getSimpleName());
            }finally{reapplying=false;}
        }

        private static android.text.TextPaint paint(View view){
            try{Object p=view.getClass().getMethod("getPaint").invoke(view);return p instanceof android.text.TextPaint?(android.text.TextPaint)p:null;}
            catch(ReflectiveOperationException|RuntimeException ignored){return null;}
        }
        private static int horizontalPadding(View view){
            try{
                Object l=view.getClass().getMethod("getCompoundPaddingLeft").invoke(view),r=view.getClass().getMethod("getCompoundPaddingRight").invoke(view);
                if(l instanceof Integer&&r instanceof Integer)return (Integer)l+(Integer)r;
            }catch(ReflectiveOperationException|RuntimeException ignored){}
            return view.getPaddingLeft()+view.getPaddingRight();
        }

        private static String stringField(View view,String name){
            try{java.lang.reflect.Field f=view.getClass().getField(name);Object v=f.get(view);return v instanceof String&&!((String)v).isEmpty()?(String)v:null;}
            catch(ReflectiveOperationException|RuntimeException ignored){return null;}
        }
        private static void setField(View view,String name,Object value){
            try{view.getClass().getField(name).set(view,value);}catch(ReflectiveOperationException|RuntimeException ignored){}
        }
        private static void log(String message){
            try{dev.mindlink.xposed.XposedBridge.log("WEX: 聊天顶栏: "+message);}catch(Throwable ignored){}
        }
    }

    static SeparatedState applySeparated(View header, SeparatedState state, int corner, int gap, int elevation) {
        return applySeparated(header, state, corner, gap, elevation, chatLayout(header));
    }

    static SeparatedState applySeparated(View header, SeparatedState state, int corner, int gap, int elevation, View backdrop) {
        View cachedToolbar = state == null || state.visuals.size() < 2 ? null : state.visuals.get(1).view;
        ViewGroup headerParent = header.getParent() instanceof ViewGroup ? (ViewGroup) header.getParent() : null;
        if (state != null && state.header == header && headerParent != null
                && state.layer.getParent() == headerParent
                && headerParent.indexOfChild(state.layer) + 1 == headerParent.indexOfChild(header)
                && state.layer.getChildCount() == 1 && state.layer.getChildAt(0) == state.decoration
                && cachedToolbar == findUnique(header, "androidx.appcompat.widget.Toolbar", 128)
                && cachedToolbar != null && cachedToolbar.getParent() == header
                && state.root.getParent() == cachedToolbar
                && state.decoration.backHost.getParent() == state.root
                && state.decoration.titleHost.getParent() == state.root
                && state.decoration.menuHost.getParent() == state.root) {
            removeShadowLayers(headerParent, header, state.layer);
            // WeKit re-neutralizes the host card visuals every frame (applyCard may have
            // re-fit a full-width white card onto the header between frames); without this
            // the stale card paints behind the three pills as a white bar.
            neutralize(state);
            refresh(state, corner, gap, elevation);
            return state;
        }
        restoreSeparated(state);
        if (headerParent == null) return null;
        View toolbar = findUnique(header, "androidx.appcompat.widget.Toolbar", 128);
        if (!(toolbar instanceof ViewGroup)) return null;
        RelativeLayout root = null;
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(toolbar);
        for (int visited=0;!queue.isEmpty()&&visited<128;visited++) {
            View view=queue.removeFirst();
            if(view instanceof RelativeLayout && parts((RelativeLayout)view)!=null){if(root!=null)return null;root=(RelativeLayout)view;}
            if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)queue.addLast(g.getChildAt(i));}
        }
        if (root == null) return null;
        List<Visual> visuals = new ArrayList<>();
        for (View view : new View[]{header, toolbar, root}) visuals.add(new Visual(view));
        // Classify the three header parts before mounting the decoration. Keep the
        // drawing layer next to the header, not inside it: an offscreen header layer
        // still clips every child bitmap to the header rectangle even when clipChildren
        // is false. The sibling layer follows the header when the feature reparents it.
        View[] classified = parts(root);
        if (classified == null) return null;
        FrameLayout layer = new FrameLayout(header.getContext());
        layer.setTag(TAG_SHADOW_LAYER);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        layer.setClickable(false);
        layer.setFocusable(false);
        layer.setFocusableInTouchMode(false);
        layer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        SeparatedDecoration decoration = new SeparatedDecoration(header.getContext(), root, classified);
        if (backdrop != null) {
            decoration.glass.backdrop(backdrop, backdrop);
            decoration.bandBackdrop.backdrop(backdrop, backdrop);
        }
        layer.addView(decoration, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        SeparatedState next = new SeparatedState(header, root, layer, decoration, visuals);
        removeShadowLayers(headerParent, header, layer);
        int headerIndex = Math.max(0, headerParent.indexOfChild(header));
        try {
            headerParent.addView(layer, headerIndex, layerParams(headerParent,
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            neutralize(next);
            refresh(next, corner, gap, elevation);
            return next;
        } catch (Throwable ignored) {
            restoreSeparated(next);
            return null;
        }
    }

    private static View chatLayout(View header) {
        if (!(header.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) header.getParent();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if ("com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout".equals(child.getClass().getName())) return child;
        }
        return null;
    }

    private static ViewGroup.MarginLayoutParams layerParams(ViewGroup parent, int width, int height) {
        if (parent instanceof RelativeLayout) return new RelativeLayout.LayoutParams(width, height);
        if (parent instanceof FrameLayout) return new FrameLayout.LayoutParams(width, height);
        return new ViewGroup.MarginLayoutParams(width, height);
    }

    private static void removeShadowLayers(ViewGroup parent, View header, View keep) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child != header && child != keep && TAG_SHADOW_LAYER.equals(child.getTag())) parent.removeView(child);
        }
        if (header instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) header;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (TAG_SHADOW_LAYER.equals(child.getTag())) group.removeView(child);
            }
        }
    }

    /** Strip host card visuals from the header chain; idempotent per frame, WeKit-neutralize semantics. */
    private static void neutralize(SeparatedState state) {
        for (Visual visual : state.visuals) {
            View view = visual.view;
            if (!(view.getBackground() instanceof ColorDrawable)
                    || ((ColorDrawable) view.getBackground()).getColor() != Color.TRANSPARENT) {
                view.setBackground(new ColorDrawable(Color.TRANSPARENT));
            }
            if (view.getOutlineProvider() != null) view.setOutlineProvider(null);
            if (view.getClipToOutline()) view.setClipToOutline(false);
            if (view.getElevation() != 0f) view.setElevation(0f);
            if (view.getTranslationZ() != 0f) view.setTranslationZ(0f);
            if (view.getStateListAnimator() != null) view.setStateListAnimator(null);
        }
    }

    /** Hardware-layer bounds include every pill plus the glass shadow's RenderNode padding. */
    private static void refresh(SeparatedState state, int corner, int gap, int elevation) {
        float density = state.root.getResources().getDisplayMetrics().density;
        int shadowPadding = Math.max(2, Math.round(30f * density));
        state.decoration.update(corner, gap, elevation, shadowPadding);
        if (state.root.getWidth() <= 0 || state.root.getHeight() <= 0) return;
        state.back.apply(state.decoration);
        state.menu.apply(state.decoration);
        state.decoration.geometry();
        // Long titles take the room the centred pill leaves unused on the narrower side; short
        // ones stay centred. Decided against the centred width (see TitleState.overflows).
        float inset = gap * density;
        android.graphics.RectF centred = state.decoration.centred;
        boolean wide = !centred.isEmpty()
                && state.title.overflows(Math.max(0, Math.round(centred.width() - 2 * inset)));
        if (wide != state.decoration.wide) {
            state.decoration.wide = wide;
            state.decoration.geometry();
            state.decoration.invalidate();
        }
        android.graphics.RectF painted = new android.graphics.RectF(0,0,state.root.getWidth(),state.root.getHeight());
        painted.union(state.decoration.left);
        if(!state.decoration.title.isEmpty())painted.union(state.decoration.title);
        if(!state.decoration.right.isEmpty())painted.union(state.decoration.right);
        int[] rootOrigin = new int[2];
        state.root.getLocationInWindow(rootOrigin);
        int bandTop = -Math.max(0, Math.min(rootOrigin[1], Math.round(128f * density)));
        int bandBottom = bandTop + Math.round((state.root.getHeight() - bandTop) * ProgressiveTopBlur.HEIGHT_FACTOR);
        painted.union(0, bandTop, state.root.getWidth(), bandBottom);
        int left=(int)Math.floor(painted.left)-shadowPadding, top=(int)Math.floor(painted.top)-shadowPadding;
        int right=(int)Math.ceil(painted.right)+shadowPadding, bottom=(int)Math.ceil(painted.bottom)+shadowPadding;
        state.decoration.offsetX=-left;state.decoration.offsetY=-top;
        String signature = left+"x"+top+"x"+right+"x"+bottom;
        if (!signature.equals(state.geometrySignature) && state.geometryLogs++ < 6) {
            state.geometrySignature = signature;
            dev.mindlink.xposed.XposedBridge.log("WEX: 悬浮顶栏: stage=split-geometry"
                    + " root=" + state.root.getWidth() + "x" + state.root.getHeight()
                    + " canvas=" + left + "," + top + "," + right + "," + bottom
                    + " shadow=" + shadowPadding
                    + " left=" + Math.round(state.decoration.left.left) + ".." + Math.round(state.decoration.left.right)
                    + " title=" + Math.round(state.decoration.title.left) + ".." + Math.round(state.decoration.title.right)
                    + " right=" + Math.round(state.decoration.right.left) + ".." + Math.round(state.decoration.right.right)
                    + " backDx=" + Math.round(state.back.applied));
        }
        ViewGroup.LayoutParams params = state.layer.getLayoutParams();
        if (params == null) return;
        int width = right-left;
        int height = bottom-top;
        ViewGroup parent = state.layer.getParent() instanceof ViewGroup ? (ViewGroup) state.layer.getParent() : null;
        if (parent == null) return;
        int[] parentOrigin=new int[2];
        parent.getLocationInWindow(parentOrigin);
        int layerLeft=left+rootOrigin[0]-parentOrigin[0]-parent.getPaddingLeft();
        int layerTop=top+rootOrigin[1]-parentOrigin[1]-parent.getPaddingTop();
        boolean marginsDiffer = !(params instanceof ViewGroup.MarginLayoutParams)
                || ((ViewGroup.MarginLayoutParams) params).leftMargin != layerLeft
                || ((ViewGroup.MarginLayoutParams) params).topMargin != layerTop;
        if (params.width != width || params.height != height || marginsDiffer) {
            params.width = width;
            params.height = height;
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) params).leftMargin = layerLeft;
                ((ViewGroup.MarginLayoutParams) params).topMargin = layerTop;
            }
            state.layer.setLayoutParams(params);
        }
        // ActionBarOverlayLayout measures only its native content and action bar. A
        // sibling decoration otherwise stays 0x0 on notification entry even with valid
        // LayoutParams. Size our own layer before drawing, using the live title origin.
        state.layer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        int x = layerLeft + parent.getPaddingLeft(), y = layerTop + parent.getPaddingTop();
        state.layer.layout(x, y, x + width, y + height);
        android.graphics.RectF shadow=new android.graphics.RectF(rootOrigin[0]+left,rootOrigin[1]+top,rootOrigin[0]+right,rootOrigin[1]+bottom);
        state.syncClips();
        for(ClipState clip:state.clips)clip.apply(shadow);
        state.decoration.geometry();
        android.graphics.RectF pill = state.decoration.title;
        float shift = pill.isEmpty() ? 0f : pill.centerX() - state.root.getWidth() / 2f;
        state.title.apply(Math.max(0, Math.round(pill.width() - 2 * inset)), shift);
        state.decoration.stroke(density, SettingsTheme.isDark(state.root.getContext()));
    }

    static void restoreSeparated(SeparatedState state) {
        if (state == null) return;
        state.decoration.glass.stop();
        state.decoration.bandBackdrop.stop();
        state.decoration.band.release();
        state.decoration.leftShadow.release();
        state.decoration.titleShadow.release();
        for (LiquidGlassDecoration shadow : state.decoration.rightShadows) shadow.release();
        if (state.layer.getParent() instanceof ViewGroup) {
            ((ViewGroup) state.layer.getParent()).removeView(state.layer);
        }
        state.title.restore();
        state.back.restore();
        state.menu.restore();
        for (Visual visual : state.visuals) visual.restore();
        for (Visual visual : state.ancestors) visual.restore();
        for (ClipState clip : state.clips) clip.restore();
    }

    /**
     * The header's native RelativeLayout holding back / centred title / menu, or null when the
     * toolbar is missing or ambiguous. Same discovery as {@link #applySeparated}.
     */
    static RelativeLayout headerRoot(View header) {
        View toolbar = findUnique(header, "androidx.appcompat.widget.Toolbar", 128);
        if (!(toolbar instanceof ViewGroup)) return null;
        RelativeLayout root = null;
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(toolbar);
        for (int visited=0;!queue.isEmpty()&&visited<128;visited++) {
            View view=queue.removeFirst();
            if(view instanceof RelativeLayout && parts((RelativeLayout)view)!=null){if(root!=null)return null;root=(RelativeLayout)view;}
            if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)queue.addLast(g.getChildAt(i));}
        }
        return root;
    }

    /** {back, title, menu} children of a native header root, or null. */
    static View[] parts(RelativeLayout root) {
        View title=null,right=null,left=null;
        for(int i=0;i<root.getChildCount();i++){
            View child=root.getChildAt(i); if(!(child.getLayoutParams() instanceof RelativeLayout.LayoutParams))continue;
            RelativeLayout.LayoutParams p=(RelativeLayout.LayoutParams)child.getLayoutParams();
            if(p.getRule(RelativeLayout.CENTER_IN_PARENT)!=0)title=child;
            else if(p.getRule(RelativeLayout.ALIGN_PARENT_RIGHT)!=0||p.getRule(RelativeLayout.ALIGN_PARENT_END)!=0)right=child;
        }
        for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);if(child!=title&&child!=right){left=child;break;}}
        return left==null||title==null||right==null?null:new View[]{left,title,right};
    }

    private static final class SeparatedDecoration extends View {
        final RelativeLayout root; final android.graphics.Paint stroke=new android.graphics.Paint(1);
        final ChatGlassSurface glass=new ChatGlassSurface(this);
        final ChatGlassSurface bandBackdrop=new ChatGlassSurface(this);
        final ProgressiveTopBlur band=new ProgressiveTopBlur();
        final LiquidGlassDecoration leftShadow=new LiquidGlassDecoration(), titleShadow=new LiquidGlassDecoration();
        final List<LiquidGlassDecoration> rightShadows = new ArrayList<>();
        final List<android.graphics.RectF> rightButtons = new ArrayList<>();
        final List<Float> rightCenters = new ArrayList<>();
        final android.graphics.RectF left=new android.graphics.RectF(),title=new android.graphics.RectF(),right=new android.graphics.RectF();
        /** The symmetric title pill; {@link #title} equals it unless {@link #wide}. */
        final android.graphics.RectF centred=new android.graphics.RectF();
        /** The title does not fit the centred pill: span everything between the circles. */
        boolean wide;
        final View backHost,titleHost,menuHost;
        int corner,gap,elevation,shadowPadding,offsetX,offsetY,windowY; float strokeWidth; int strokeColor;
        SeparatedDecoration(android.content.Context context,RelativeLayout root,View[] parts){super(context);this.root=root;setLayerType(LAYER_TYPE_HARDWARE,null);setClickable(false);backHost=parts[0];titleHost=parts[1];menuHost=parts[2];stroke.setStyle(android.graphics.Paint.Style.STROKE);}
        void update(int c,int g,int e,int padding){corner=c;gap=g;elevation=e;shadowPadding=padding;invalidate();}
        /** WeKit strokes pills 1dp 0x24FFFFFF in dark mode only; light mode keeps shadow-only definition. */
        void stroke(float density,boolean dark){float width=dark?Math.max(1f,density):0f;int color=0x24FFFFFF;if(strokeWidth!=width||strokeColor!=color){strokeWidth=width;strokeColor=color;invalidate();}}
        /** Live window-space offsets; a sibling layer awaiting layout must not draw a stale frame. */
        private boolean syncOffsets(){
            if(root==null||!root.isShown()||!root.isAttachedToWindow()||getWindowToken()!=root.getWindowToken()
                    ||root.getWidth()<=0||root.getHeight()<=0||getWidth()<=0||getHeight()<=0) return false;
            int[] rootAt=new int[2],selfAt=new int[2];
            root.getLocationInWindow(rootAt); getLocationInWindow(selfAt);
            int x=rootAt[0]-selfAt[0],y=rootAt[1]-selfAt[1];
            if(x+root.getWidth()<=0||y+root.getHeight()<=0||x>=getWidth()||y>=getHeight()) return false;
            offsetX=x;offsetY=y;windowY=rootAt[1];return true;
        }

        @Override protected void onDraw(android.graphics.Canvas canvas){
            if(backHost.getParent()!=root||titleHost.getParent()!=root||menuHost.getParent()!=root)return;
            if(!syncOffsets())return;
            geometry();
            float d=getResources().getDisplayMetrics().density;
            if(root.getHeight()<=0)return;
            // Geometry is relative to the native root; the drawing layer starts outside it.
            left.offset(offsetX,offsetY);
            if(!title.isEmpty())title.offset(offsetX,offsetY);
            for (android.graphics.RectF button : rightButtons) button.offset(offsetX,offsetY);
            int color=sampleColor(root);boolean dark=SettingsTheme.isDark(root.getContext());
            int material = ChatGlassSurface.mode(), blur = ChatGlassSurface.blurRadiusDp(material);
            int top = -Math.max(0, Math.min(windowY, Math.round(128f * d)));
            int bottom = top + Math.round((root.getHeight() - top) * ProgressiveTopBlur.HEIGHT_FACTOR);
            band.draw(canvas, bandBackdrop.sharedSampler(), offsetX, root.getWidth(), offsetY + top, offsetY + bottom, d, dark, 1f);
            pill(canvas, leftShadow, left, left.height() / 2, d, dark, color, material, blur);
            pill(canvas, titleShadow, title, corner * d, d, dark, color, material, blur);
            for (int i = 0; i < rightButtons.size(); i++) {
                if (rightShadows.size() == i) rightShadows.add(new LiquidGlassDecoration());
                android.graphics.RectF button = rightButtons.get(i);
                pill(canvas, rightShadows.get(i), button, button.height() / 2, d, dark, color, material, blur);
            }
            if(strokeWidth>0f){stroke.setStrokeWidth(strokeWidth);stroke.setColor(strokeColor);draw(canvas,stroke);}
        }
        void geometry(){
            float d=getResources().getDisplayMetrics().density,h=root.getHeight();if(h<=0)return;
            float diameter=h,space=gap*d;
            // Left circle follows WeKit: anchored on the visible back icon, not the host view center.
            backIcon(backHost,left);float leftCenter=left.isEmpty()?bounds(backHost).centerX():left.centerX();
            left.set(leftCenter-diameter/2,0,leftCenter+diameter/2,h);
            // Right circles follow WeKit: only visible menu actions reserve title width; empty
            // action slots (kept across style switches by the host) must not squeeze the title.
            right.setEmpty();menuActions(menuHost,diameter);
            float leftReserve=left.right+space;
            float rightReserve=right.isEmpty()?leftReserve:root.getWidth()-right.left+space;
            float reserve=Math.max(leftReserve,rightReserve);
            centred.set(reserve,0,root.getWidth()-reserve,h);
            if(centred.right<=centred.left)centred.setEmpty();
            // Wide: from the left circle to the right one (or to the edge inset without actions).
            if(wide)title.set(leftReserve,0,root.getWidth()-(right.isEmpty()?space:rightReserve),h);
            else title.set(centred);
            if(title.right<=title.left)title.setEmpty();
        }
        /** Bounds of a view in this decoration's coordinate space. */
        android.graphics.RectF bounds(View v){int[] r=new int[2],x=new int[2];root.getLocationInWindow(r);v.getLocationInWindow(x);return new android.graphics.RectF(x[0]-r[0],x[1]-r[1],x[0]-r[0]+v.getWidth(),x[1]-r[1]+v.getHeight());}
        void backIcon(View host,android.graphics.RectF out){
            ArrayDeque<View> queue=new ArrayDeque<>();queue.add(host);
            for(int count=0;!queue.isEmpty()&&count<64;count++){
                View child=queue.removeFirst();
                if(!child.isShown())continue;
                if(child instanceof android.widget.ImageView){
                    android.graphics.RectF b=bounds(child);
                    if(b.width()>child.getPaddingLeft()+child.getPaddingRight()){
                        out.set(b.left+child.getPaddingLeft(),b.top,b.right-child.getPaddingRight(),b.bottom);
                        return;
                    }
                }
                if(child instanceof ViewGroup){ViewGroup group=(ViewGroup)child;for(int i=0;i<group.getChildCount();i++)queue.add(group.getChildAt(i));}
            }
            out.setEmpty();
        }
        /** Paint visible actions separately and reserve the whole group when sizing the title. */
        void menuActions(View host,float diameter){
            rightButtons.clear();
            rightCenters.clear();
            right.setEmpty();
            if(!(host instanceof ViewGroup))return;
            ViewGroup group=(ViewGroup)host,menu=group;
            for(int i=0;i<group.getChildCount();i++){
                View child=group.getChildAt(i);
                if(child instanceof ViewGroup){menu=(ViewGroup)child;break;}
            }
            for(int i=0;i<menu.getChildCount();i++){
                View action=menu.getChildAt(i);
                if(!visibleMenuAction(action))continue;
                rightCenters.add(bounds(action).centerX());
            }
            rightCenters.sort(Float::compare);
            float spacing = 4f * getResources().getDisplayMetrics().density;
            for (int i = 0; i < rightCenters.size(); i++) {
                float center = rightCenters.get(i), size = diameter;
                if (i > 0) size = Math.min(size, center - rightCenters.get(i - 1) - spacing);
                if (i + 1 < rightCenters.size()) size = Math.min(size, rightCenters.get(i + 1) - center - spacing);
                if (size <= 0f) continue;
                android.graphics.RectF card = new android.graphics.RectF(center - size / 2f, (diameter - size) / 2f,
                        center + size / 2f, (diameter + size) / 2f);
                rightButtons.add(card);
                if(right.isEmpty())right.set(card);else right.union(card);
            }
        }
        boolean visibleMenuAction(View action) {
            if(!action.isShown()||action.getWidth()<=0||action.getHeight()<=0)return false;
            if(!"com.tencent.mm.ui.widget.WXActionMenuView".equals(action.getClass().getName()))return true;
            ArrayDeque<View> queue=new ArrayDeque<>();queue.add(action);
            for(int visited=0;!queue.isEmpty()&&visited<64;visited++){
                View content=queue.removeFirst();
                if(!content.isShown())continue;
                if(content instanceof android.widget.ImageView
                        &&((android.widget.ImageView)content).getDrawable()!=null)return true;
                if(content instanceof android.widget.TextView){
                    CharSequence text=((android.widget.TextView)content).getText();
                    if(text!=null&&text.length()>0)return true;
                }
                if(content instanceof ViewGroup){ViewGroup group=(ViewGroup)content;for(int i=0;i<group.getChildCount();i++)queue.add(group.getChildAt(i));}
            }
            return false;
        }
        void draw(android.graphics.Canvas c,android.graphics.Paint p){float r=corner*getResources().getDisplayMetrics().density;if(!left.isEmpty())c.drawRoundRect(left,left.height()/2,left.height()/2,p);if(!title.isEmpty())c.drawRoundRect(title,r,r,p);for(android.graphics.RectF button:rightButtons)c.drawRoundRect(button,button.height()/2,button.height()/2,p);}
        private void pill(android.graphics.Canvas canvas, LiquidGlassDecoration shadow, android.graphics.RectF rect,
                          float radius, float density, boolean dark, int color, int material, int blur) {
            if (rect.isEmpty()) return;
            glass.configure(color, radius, density, dark, material, blur);
            shadow.dropShadow(canvas, rect, radius, density, dark);
            glass.setBounds((int)Math.floor(rect.left),(int)Math.floor(rect.top),
                    (int)Math.ceil(rect.right),(int)Math.ceil(rect.bottom));
            glass.draw(canvas);
        }
    }

    static final class WindowHeaderState {
        final WeakReference<View> overlay, body; final Boolean overlayMode; final int bodyTop;
        WindowHeaderState(View overlay, Boolean mode, View body){this.overlay=new WeakReference<>(overlay);this.overlayMode=mode;this.body=new WeakReference<>(body);this.bodyTop=body==null?0:body.getPaddingTop();}
    }

    static WindowHeaderState applyWindowHeader(View layout, View header, Activity activity, WindowHeaderState state) {
        if (header == null || header.getParent() == layout) return state;
        View overlay=findAncestor(header,"androidx.appcompat.widget.ActionBarOverlayLayout");
        View body=activity.findViewById(android.R.id.content);
        if(state==null||state.overlay.get()!=overlay||state.body.get()!=body){restoreWindowHeader(state);Boolean mode=readOverlayMode(overlay);state=new WindowHeaderState(overlay,mode,body);}
        if(overlay!=null)invokeOverlayMode(overlay,true);
        if(body!=null&&body.getPaddingTop()!=0)body.setPadding(body.getPaddingLeft(),0,body.getPaddingRight(),body.getPaddingBottom());
        return state;
    }

    static void restoreWindowHeader(WindowHeaderState state){if(state==null)return;View overlay=state.overlay.get(),body=state.body.get();if(overlay!=null&&state.overlayMode!=null)invokeOverlayMode(overlay,state.overlayMode);if(body!=null&&body.getPaddingTop()==0)body.setPadding(body.getPaddingLeft(),state.bodyTop,body.getPaddingRight(),body.getPaddingBottom());}
    static Boolean isOverlayMode(View overlay) { return readOverlayMode(overlay); }
    private static Boolean readOverlayMode(View overlay){if(overlay==null)return null;try{java.lang.reflect.Method m=overlay.getClass().getMethod("isInOverlayMode");Object value=m.invoke(overlay);return value instanceof Boolean?(Boolean)value:null;}catch(Throwable ignored){return null;}}
    private static void invokeOverlayMode(View overlay,boolean enabled){if(overlay==null)return;try{java.lang.reflect.Method m=overlay.getClass().getMethod("setOverlayMode",boolean.class);m.invoke(overlay,enabled);}catch(Throwable ignored){}}

    static final class TopEdgeState {
        final WeakReference<View> layout, wrapper; final int layoutTop, wrapperTop; final Window window; final Object wrapperStrategy;
        TopEdgeState(View layout, View wrapper, Window window) {
            this.layout=new WeakReference<>(layout);this.wrapper=new WeakReference<>(wrapper);this.window=window;
            layoutTop=layout.getPaddingTop();wrapperTop=wrapper==null?0:wrapper.getPaddingTop();wrapperStrategy=wrapper==null?null:statusStrategy(wrapper);
        }
    }

    static TopEdgeState applyTopEdgeToEdge(View layout, Activity activity, TopEdgeState state) {
        Window window = activity.getWindow(); if (window == null) return state;
        View wrapper=findAncestor(layout,"com.tencent.mm.ui.widget.EdgeToEdgeWrapperLayout");
        if(state==null||state.layout.get()!=layout||state.window!=window){
            ChatWindowInsetsCoordinator.acquire(activity,ChatWindowInsetsCoordinator.TOP);
            state=new TopEdgeState(layout,wrapper,window);
        }
        window.setStatusBarColor(Color.TRANSPARENT);
        WindowInsetsController controller=window.getInsetsController();
        if(controller!=null){int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;int value=SettingsTheme.isDark(activity)?0:mask;controller.setSystemBarsAppearance(value,mask);}
        if(layout.getPaddingTop()!=0)layout.setPadding(layout.getPaddingLeft(),0,layout.getPaddingRight(),layout.getPaddingBottom());
        if(wrapper!=null){setStatusStrategy(wrapper,"ALWAYS_HIDE");if(wrapper.getPaddingTop()!=0)wrapper.setPadding(wrapper.getPaddingLeft(),0,wrapper.getPaddingRight(),wrapper.getPaddingBottom());}
        return state;
    }

    static void restoreTopEdge(TopEdgeState state) {
        if(state==null)return;View layout=state.layout.get(),wrapper=state.wrapper.get();
        if(layout!=null&&layout.getPaddingTop()==0)layout.setPadding(layout.getPaddingLeft(),state.layoutTop,layout.getPaddingRight(),layout.getPaddingBottom());
        if(wrapper!=null){if(state.wrapperStrategy!=null)setStatusStrategy(wrapper,state.wrapperStrategy);if(wrapper.getPaddingTop()==0)wrapper.setPadding(wrapper.getPaddingLeft(),state.wrapperTop,wrapper.getPaddingRight(),wrapper.getPaddingBottom());}
        ChatWindowInsetsCoordinator.release(state.window,ChatWindowInsetsCoordinator.TOP);
    }

    private static Object statusStrategy(View wrapper){try{return wrapper.getClass().getMethod("getStatusBarStrategy").invoke(wrapper);}catch(Throwable ignored){return null;}}
    private static void setStatusStrategy(View wrapper,String name){try{java.lang.reflect.Method method=null;for(java.lang.reflect.Method candidate:wrapper.getClass().getMethods())if("setStatusBarStrategy".equals(candidate.getName())&&candidate.getParameterTypes().length==1){method=candidate;break;}if(method==null)return;for(Object value:method.getParameterTypes()[0].getEnumConstants())if(value instanceof Enum&&name.equals(((Enum<?>)value).name())){method.invoke(wrapper,value);return;}}catch(Throwable ignored){}}
    private static void setStatusStrategy(View wrapper,Object value){if(value==null)return;try{wrapper.getClass().getMethod("setStatusBarStrategy",value.getClass()).invoke(wrapper,value);}catch(Throwable ignored){}}
    private static int sampleColor(View view) {
        Drawable background=view.getBackground();
        if(background instanceof ColorDrawable&&Color.alpha(((ColorDrawable)background).getColor())>=128)return ((ColorDrawable)background).getColor();
        return SettingsTheme.isDark(view.getContext())?0xFF242424:0xFFF7F7F7;
    }
    private static View findAncestor(View view,String name){for(View current=view;current!=null;current=current.getParent() instanceof View?(View)current.getParent():null)if(name.equals(current.getClass().getName()))return current;return null;}
    private static View findUnique(View root,String name,int limit){View found=null;ArrayDeque<View> q=new ArrayDeque<>();q.add(root);for(int n=0;!q.isEmpty()&&n<limit;n++){View v=q.removeFirst();if(name.equals(v.getClass().getName())){if(found!=null&&found!=v)return null;found=v;}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)q.addLast(g.getChildAt(i));}}return found;}
}
