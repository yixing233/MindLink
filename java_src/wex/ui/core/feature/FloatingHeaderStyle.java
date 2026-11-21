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
        CardState(View view, ViewOutlineProvider owned) {
            this.view=view; background=view.getBackground(); outline=view.getOutlineProvider(); clip=view.getClipToOutline();
            elevation=view.getElevation(); translation=view.getTranslationZ(); animator=view.getStateListAnimator(); this.owned=owned;
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
            ViewOutlineProvider owned = new ViewOutlineProvider() {
                @Override public void getOutline(View target, Outline outline) {
                    outline.setRoundRect(0, 0, target.getWidth(), Math.max(1, target.getHeight()), radius);
                }
            };
            state = new CardState(view, owned);
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
        if (view.getOutlineProvider() == state.owned) {
            if (view.getBackground() instanceof ChatGlassSurface) ((ChatGlassSurface) view.getBackground()).stop();
            view.setBackground(state.background); view.setOutlineProvider(state.outline); view.setClipToOutline(state.clip);
            view.setElevation(state.elevation); view.setTranslationZ(state.translation); view.setStateListAnimator(state.animator);
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
        final BackPosition back;
        int geometryLogs; String geometrySignature;
        SeparatedState(View h, RelativeLayout r, FrameLayout l, SeparatedDecoration d, List<Visual> v){
            header=h;root=r;layer=l;decoration=d;visuals=v;
            title = new TitleState(d.titleHost);
            back = new BackPosition(d.backHost);
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

    private static final class BackPosition {
        final View view; float original, applied;
        BackPosition(View view) { this.view=view;original=applied=view.getTranslationX(); }
        void apply(SeparatedDecoration decoration) {
            float current=view.getTranslationX();
            if(current!=applied)original=current;
            decoration.backIcon(view,decoration.left);
            float center=decoration.left.isEmpty()?decoration.bounds(view).centerX():decoration.left.centerX();
            float nativeCenter=center-current+original;
            float density=decoration.getResources().getDisplayMetrics().density;
            float inset=decoration.gap*density;
            float targetCenter=inset+decoration.root.getHeight()/2f;
            float next=original+Math.max(0f,targetCenter-nativeCenter);
            if(current!=next)view.setTranslationX(next);
            applied=next;
        }
        void restore() { if(view.getTranslationX()==applied)view.setTranslationX(original); }
    }

    private static final class TitleState {
        final View view;
        ViewGroup.LayoutParams params;
        int originalWidth, appliedWidth=-1;
        final List<TextWidth> texts=new ArrayList<>();
        TitleState(View view) {
            this.view=view;
            ArrayDeque<View> queue=new ArrayDeque<>();queue.add(view);
            for(int count=0;!queue.isEmpty()&&count<64;count++){
                View child=queue.removeFirst();
                if(child instanceof android.widget.TextView)texts.add(new TextWidth((android.widget.TextView)child));
                if(child instanceof ViewGroup){ViewGroup group=(ViewGroup)child;for(int i=0;i<group.getChildCount();i++)queue.add(group.getChildAt(i));}
            }
        }
        void apply(int width) {
            ViewGroup.LayoutParams current=view.getLayoutParams();
            if(current==null||width<=0)return;
            if(params!=current){params=current;originalWidth=current.width;appliedWidth=-1;}
            if(current.width!=width){current.width=width;view.setLayoutParams(current);}
            appliedWidth=width;
            for(TextWidth text:texts)text.apply(Math.max(1,width-view.getPaddingLeft()-view.getPaddingRight()));
        }
        void restore() {
            if(params!=null&&view.getLayoutParams()==params&&params.width==appliedWidth){
                params.width=originalWidth;view.setLayoutParams(params);
            }
            for(TextWidth text:texts)text.restore();
        }
    }

    private static final class TextWidth {
        final android.widget.TextView view; final int original; int applied=-1;
        TextWidth(android.widget.TextView view){this.view=view;original=view.getMaxWidth();}
        void apply(int width){if(view.getMaxWidth()!=width)view.setMaxWidth(width);applied=width;}
        void restore(){if(view.getMaxWidth()==applied)view.setMaxWidth(original);}
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
        if (backdrop != null) decoration.glass.backdrop(backdrop, backdrop);
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
        state.decoration.geometry();
        android.graphics.RectF painted = new android.graphics.RectF(0,0,state.root.getWidth(),state.root.getHeight());
        painted.union(state.decoration.left);
        if(!state.decoration.title.isEmpty())painted.union(state.decoration.title);
        if(!state.decoration.right.isEmpty())painted.union(state.decoration.right);
        int left=(int)Math.floor(painted.left)-shadowPadding, top=(int)Math.floor(painted.top)-shadowPadding;
        int right=(int)Math.ceil(painted.right)+shadowPadding, bottom=(int)Math.ceil(painted.bottom)+shadowPadding;
        state.decoration.offsetX=-left;state.decoration.offsetY=-top;
        String signature = left+"x"+top+"x"+right+"x"+bottom;
        if (!signature.equals(state.geometrySignature) && state.geometryLogs++ < 6) {
            state.geometrySignature = signature;
            de.robv.android.xposed.XposedBridge.log("WEX: 悬浮顶栏: stage=split-geometry"
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
        int[] parentOrigin=new int[2], rootOrigin=new int[2];
        parent.getLocationInWindow(parentOrigin);
        state.root.getLocationInWindow(rootOrigin);
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
        android.graphics.RectF shadow=new android.graphics.RectF(rootOrigin[0]+left,rootOrigin[1]+top,rootOrigin[0]+right,rootOrigin[1]+bottom);
        state.syncClips();
        for(ClipState clip:state.clips)clip.apply(shadow);
        state.decoration.geometry();
        state.title.apply(Math.max(0,Math.round(state.decoration.title.width()-2*gap*density)));
        state.decoration.stroke(density, SettingsTheme.isDark(state.root.getContext()));
    }

    static void restoreSeparated(SeparatedState state) {
        if (state == null) return;
        state.decoration.glass.stop();
        state.decoration.leftShadow.release();
        state.decoration.titleShadow.release();
        state.decoration.rightShadow.release();
        if (state.layer.getParent() instanceof ViewGroup) {
            ((ViewGroup) state.layer.getParent()).removeView(state.layer);
        }
        state.title.restore();
        state.back.restore();
        for (Visual visual : state.visuals) visual.restore();
        for (Visual visual : state.ancestors) visual.restore();
        for (ClipState clip : state.clips) clip.restore();
    }

    private static View[] parts(RelativeLayout root) {
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
        final LiquidGlassDecoration leftShadow=new LiquidGlassDecoration(), titleShadow=new LiquidGlassDecoration(), rightShadow=new LiquidGlassDecoration();
        final android.graphics.RectF left=new android.graphics.RectF(),title=new android.graphics.RectF(),right=new android.graphics.RectF();
        final View backHost,titleHost,menuHost;
        int corner,gap,elevation,shadowPadding,offsetX,offsetY; float strokeWidth; int strokeColor;
        SeparatedDecoration(android.content.Context context,RelativeLayout root,View[] parts){super(context);this.root=root;setLayerType(LAYER_TYPE_HARDWARE,null);setClickable(false);backHost=parts[0];titleHost=parts[1];menuHost=parts[2];stroke.setStyle(android.graphics.Paint.Style.STROKE);}
        void update(int c,int g,int e,int padding){corner=c;gap=g;elevation=e;shadowPadding=padding;invalidate();}
        /** WeKit strokes pills 1dp 0x24FFFFFF in dark mode only; light mode keeps shadow-only definition. */
        void stroke(float density,boolean dark){float width=dark?Math.max(1f,density):0f;int color=0x24FFFFFF;if(strokeWidth!=width||strokeColor!=color){strokeWidth=width;strokeColor=color;invalidate();}}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            if(backHost.getParent()!=root||titleHost.getParent()!=root||menuHost.getParent()!=root)return;
            geometry();
            float d=getResources().getDisplayMetrics().density;
            if(root.getHeight()<=0)return;
            // Geometry is relative to the native root; the drawing layer starts outside it.
            left.offset(offsetX,offsetY);
            if(!title.isEmpty())title.offset(offsetX,offsetY);
            if(!right.isEmpty())right.offset(offsetX,offsetY);
            int color=sampleColor(root);boolean dark=SettingsTheme.isDark(root.getContext());
            glass.configure(color, corner * d, d, SettingsTheme.isDark(root.getContext()),
                    ChatGlassSurface.mode(), ChatGlassSurface.blurRadiusDp(ChatGlassSurface.mode()));
            pill(canvas, leftShadow, left, corner * d, d, dark);
            pill(canvas, titleShadow, title, corner * d, d, dark);
            pill(canvas, rightShadow, right, corner * d, d, dark);
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
            float reserve=Math.max(leftReserve,rightReserve);title.set(reserve,0,root.getWidth()-reserve,h);
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
        /** Union of visible action circles under the right host; empty slots (INVISIBLE, no content) skipped.
         *  The right part wraps one menu container (WeChat's WXActionMenuView); actions live inside it. */
        void menuActions(View host,float diameter){
            if(!(host instanceof ViewGroup))return;
            ViewGroup group=(ViewGroup)host,menu=group;
            for(int i=0;i<group.getChildCount();i++){
                View child=group.getChildAt(i);
                if(child instanceof ViewGroup){menu=(ViewGroup)child;break;}
            }
            for(int i=0;i<menu.getChildCount();i++){
                View action=menu.getChildAt(i);
                if(!visibleMenuAction(action))continue;
                android.graphics.RectF b=bounds(action);float center=b.centerX();
                android.graphics.RectF card=new android.graphics.RectF(center-diameter/2,0,center+diameter/2,diameter);
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
        void draw(android.graphics.Canvas c,android.graphics.Paint p){float r=corner*getResources().getDisplayMetrics().density;if(!left.isEmpty())c.drawRoundRect(left,left.height()/2,left.height()/2,p);if(!title.isEmpty())c.drawRoundRect(title,r,r,p);if(!right.isEmpty())c.drawRoundRect(right,right.height()/2,right.height()/2,p);}
        private void pill(android.graphics.Canvas canvas, LiquidGlassDecoration shadow, android.graphics.RectF rect,
                          float radius, float density, boolean dark) {
            if (rect.isEmpty()) return;
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
