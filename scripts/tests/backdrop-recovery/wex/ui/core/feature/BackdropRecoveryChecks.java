package wex.ui.core.feature;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import com.tencent.mm.dynamicbackground.view.DynamicBackgroundGLSurfaceView;
import com.tencent.mm.plugin.multitask.ui.bg.DynamicBgContainer;
import com.tencent.mm.ui.conversation.MainUIView;
import java.lang.reflect.Field;
import java.util.List;

/** Executes the actual production sampler/layer; the private Android doubles only inject faults and observe calls. */
public final class BackdropRecoveryChecks {
    private static final Rect SAMPLE = new Rect(20, 30, 100, 90);
    private static final RectF GLASS = new RectF(20f, 30f, 100f, 90f);
    private static int assertions, cases, failures;

    public static void main(String[] args) {
        run("first transient View.draw failure retries the same generation after 80ms", BackdropRecoveryChecks::firstTransientFailure);
        run("A success / B failure preserves the complete published A node", BackdropRecoveryChecks::failedReplacementPreservesA);
        run("endRecording failure is contained, discarded and never published", BackdropRecoveryChecks::endRecordingFailure);
        run("beginRecording failure and empty display list recover without revision advance", BackdropRecoveryChecks::beginAndEmptyFailures);
        run("each verified source revision permits only three failures; changed/retry never replenish it", BackdropRecoveryChecks::boundedSamplerRetries);
        run("real host dirty edge restores an exhausted budget, but a failing capture cannot self-replenish", BackdropRecoveryChecks::sourceRevisionRecovery);
        run("observable parent background changes rerecord once without using overlay-dirtied parent", BackdropRecoveryChecks::parentBackgroundChanges);
        run("window mismatch and a failed new-window capture never reuse old-window pixels", BackdropRecoveryChecks::windowIsolation);
        run("size, density, font scale and layout direction changes prohibit old-environment reuse", BackdropRecoveryChecks::environmentIsolation);
        run("successful node swap advances revision and forces layer rerecord", BackdropRecoveryChecks::revisionForcesLayerRerecord);
        run("stop cancels retry, listeners and both display lists", BackdropRecoveryChecks::stopCancelsRetry);
        run("source detach cancels retry and reattach starts a fresh capture", BackdropRecoveryChecks::detachCancelsRetry);
        run("hidden/detached consumer receives no delayed invalidation", BackdropRecoveryChecks::inactiveConsumerDoesNotWake);
        run("blur 0 -> unavailable sample / requested blur > 0 -> restored effect", BackdropRecoveryChecks::unavailableBlurRecovery);
        run("unchanged params still rerecord and rebuild effect after !drawn", BackdropRecoveryChecks::unchangedParamsRecovery);
        run("density alone invalidates effect, independent of sampler revision", BackdropRecoveryChecks::densityInvalidatesEffect);
        run("effect creation/installation failures recover with requested params", BackdropRecoveryChecks::effectFailureRecovery);
        run("layer endRecording failure is contained and recovers on the next draw", BackdropRecoveryChecks::layerRecordingFailure);
        run("overlapping SurfaceView prevents reuse; removal restores recording/effect", BackdropRecoveryChecks::surfaceAvailabilityRecovery);
        run("home pull wallpaper does not block sampling the conversation UI", BackdropRecoveryChecks::conversationWallpaper);
        run("off-ROI pager/list trees over 768 do not spend the safety budget", BackdropRecoveryChecks::roiPrunesUnrelatedTrees);
        run("intersecting Texture and unclipped overflow remain unsafe; incomplete walks fail closed", BackdropRecoveryChecks::roiSafety);
        run("source outside the sample never publishes an empty success and recovers after layout", BackdropRecoveryChecks::outsideSource);
        run("short pager uses actual parent background without consumer or cached feedback", BackdropRecoveryChecks::parentBackgroundSource);
        run("parent-source rebind cleans the old retry, observers and display lists", BackdropRecoveryChecks::sourceRebind);
        run("structural availability transitions cannot reset the three-failure recording budget", BackdropRecoveryChecks::structuralBudget);
        run("20261115 N1 real list revision with a clean pager recovers through scheduled normal frames", BackdropRecoveryChecks::explicitSourceScheduledRecovery);
        run("20261115 N2 safe blocker removal rechecks without an external draw and permanent blockers stop", BackdropRecoveryChecks::scheduledAvailabilityRecovery);
        run("20261115 N3 lost display list revokes only success, retaining capture budget/backoff", BackdropRecoveryChecks::lostDisplayListBudget);
        run("20261115 N4 layer begin/effect failures wake without resampling and persistent failures stop", BackdropRecoveryChecks::scheduledLayerRecovery);
        run("20261115 N5 self-draw notification cannot mint source/layer budgets", BackdropRecoveryChecks::selfNotificationBudget);
        if (failures != 0) throw new AssertionError(failures + " of " + cases + " backdrop recovery cases failed");
        System.out.println("PASS: real backdrop sampler/layer recovery (" + cases + " cases, " + assertions + " assertions)");
    }

    private static final class Fixture {
        final View source, consumer = new View();
        final Canvas canvas = new Canvas();
        final ViewBackdropSampler sampler;
        Fixture() { this(new View()); }
        Fixture(View source) {
            RenderNode.reset(); RenderEffect.reset(); NavigationBarFeature.logs.clear();
            SystemClock.setUptimeMillis(1000L);
            this.source = source;
            consumer.windowToken = source.windowToken;
            sampler = new ViewBackdropSampler(source, consumer);
            sampler.start();
        }
        boolean prepare() { return sampler.prepare(canvas, SAMPLE); }
        void at(long time) { SystemClock.setUptimeMillis(time); consumer.dispatchDueCallbacks(); }
        RenderNode active() {
            Canvas observation = new Canvas(); sampler.draw(observation);
            check(observation.renderedNodes.size() == 1, "sampler draws exactly one published node");
            return observation.renderedNodes.get(0);
        }
        RenderNode pending() {
            RenderNode active = active();
            return RenderNode.instances.stream().filter(value -> value != active && value.name.startsWith("MindLink-home-backdrop"))
                    .findFirst().orElseThrow();
        }
    }

    private static void firstTransientFailure() {
        Fixture f = new Fixture(); f.source.failDraws = 1;
        check(!f.prepare(), "initial failed capture is unavailable");
        check(f.source.drawCalls == 1 && f.sampler.revision() == 0L, "failure cannot advance content revision");
        check(f.consumer.pendingCallbacks() == 1 && f.consumer.postedDelays.equals(List.of(80L)), "failure schedules one 80ms callback");
        check(f.consumer.nextCallbackAt() == 1080L, "first retry due time");
        long generation = generation(f.sampler);
        int invalidations = f.consumer.invalidations;
        f.at(1079L);
        check(f.consumer.invalidations == invalidations, "retry cannot invalidate before 80ms");
        check(!f.prepare() && f.source.drawCalls == 1, "early draw does not rerecord failed key");
        check(f.consumer.postCalls == 1, "early draw does not duplicate retry callback");
        f.at(1080L);
        check(f.consumer.invalidations == invalidations + 1, "due callback invalidates consumer once");
        check(generation(f.sampler) == generation, "retry invalidate does not increment generation");
        check(f.source.drawCalls == 1, "callback itself does not capture");
        check(f.prepare() && f.source.drawCalls == 2, "same generation succeeds on retry draw");
        check(f.sampler.revision() == 1L && f.consumer.pendingCallbacks() == 0, "only successful retry publishes revision and clears retry");
        check(hasContent(f.active(), "A"), "successful retry publishes actual recorded A commands");
        check(f.prepare() && f.source.drawCalls == 2 && f.sampler.revision() == 1L, "successful exact key reuses without rerecord");
    }

    private static void failedReplacementPreservesA() {
        Fixture f = new Fixture();
        check(f.prepare(), "A capture succeeds");
        RenderNode a = f.active(), pending = f.pending();
        List<String> aCommands = a.content();
        int aBegins = a.beginCalls, aDiscards = a.discardCalls;
        int left = a.left, top = a.top, right = a.right, bottom = a.bottom;
        long revision = f.sampler.revision();
        f.source.content = "B"; f.source.scrollX = 19; f.source.scrollY = -8;
        f.source.failDraws = 1; f.sampler.changed();
        check(f.prepare(), "A remains available when B draw fails in the same environment");
        check(f.active() == a && a.hasDisplayList(), "failure does not exchange the active node");
        check(a.content().equals(aCommands) && hasContent(a, "A") && !hasContent(a, "B"), "all A commands are retained, with no partial B contamination");
        check(a.beginCalls == aBegins && a.discardCalls == aDiscards, "failed replacement never rerecords/discards published A");
        check(a.left == left && a.top == top && a.right == right && a.bottom == bottom, "published A position is untouched");
        check(pending.beginCalls == 1 && !pending.hasDisplayList(), "only scratch node was recorded, then discarded");
        check(f.sampler.revision() == revision, "failed B does not advance revision");
        f.at(1080L);
        check(f.prepare() && f.active() == pending, "successful B retry exchanges the two nodes");
        check(f.sampler.revision() == revision + 1L && hasContent(pending, "B"), "successful exchange publishes B and exactly one new revision");
        check(pending.content().contains("translate:-19.0,8.0"), "real sampler applies negative source scroll transform");
        check(a.content().equals(aCommands), "old A node remains intact until reused as future scratch");
    }

    private static void endRecordingFailure() {
        Fixture first = new Fixture();
        RenderNode initialPublished = first.active(), scratch = first.pending();
        scratch.failEnds = 1;
        check(!first.prepare(), "initial endRecording exception does not escape and does not publish");
        check(first.active() == initialPublished && first.sampler.revision() == 0L, "failed endRecording cannot swap or advance revision");
        check(!scratch.hasDisplayList(), "partial list published by throwing endRecording is discarded");
        first.at(1080L);
        check(first.prepare() && first.active() == scratch && first.sampler.revision() == 1L, "recording guard clears and initial end failure retries successfully");

        Fixture f = new Fixture(); check(f.prepare(), "A before end failure");
        RenderNode a = f.active(), pending = f.pending();
        List<String> commands = a.content();
        pending.failEnds = 1; f.source.content = "B"; f.sampler.changed();
        check(f.prepare(), "A remains available after B endRecording throws");
        check(f.active() == a && a.content().equals(commands), "end failure does not exchange/damage A");
        check(f.sampler.revision() == 1L && !pending.hasDisplayList(), "end failure discards B and retains revision");
        check(f.consumer.postedDelays.equals(List.of(80L)), "end failure participates in explicit retry");
        f.at(1080L);
        check(f.prepare() && f.active() == pending && hasContent(pending, "B"), "B end failure recovers via actual second recording");
        check(f.sampler.revision() == 2L, "successful B end increments revision once");
    }

    private static void beginAndEmptyFailures() {
        Fixture begin = new Fixture(); RenderNode pending = begin.pending(); pending.failBegins = 1;
        check(!begin.prepare(), "begin exception is contained");
        check(pending.endCalls == 0 && begin.source.drawCalls == 0, "no endRecording or View.draw if begin fails");
        check(begin.sampler.revision() == 0L && begin.consumer.pendingCallbacks() == 1, "begin failure has no revision and schedules retry");
        begin.at(1080L);
        check(begin.prepare() && begin.sampler.revision() == 1L && pending.endCalls == 1, "begin failure recovers on retry");

        Fixture empty = new Fixture(); RenderNode scratch = empty.pending(); scratch.emptyEnds = 1;
        check(!empty.prepare(), "empty successful end is not a successful capture");
        check(empty.sampler.revision() == 0L && empty.consumer.postedDelays.equals(List.of(80L)), "empty list uses the same explicit failed retry path");
        empty.at(1080L);
        check(empty.prepare() && empty.sampler.revision() == 1L && empty.source.drawCalls == 2, "empty list retries and succeeds");
    }

    private static void boundedSamplerRetries() {
        Fixture f = new Fixture(); f.source.failDraws = 30;
        check(!f.prepare(), "first persistent failure");
        for (int i = 0; i < 8; i++) {
            f.sampler.changed();
            check(!f.prepare(), "new generation/scroll still waits for first backoff");
        }
        check(f.source.drawCalls == 1 && f.consumer.pendingCallbacks() == 1 && f.consumer.postCalls == 1,
                "generation changes cannot duplicate capture or scheduled callback");
        long beforeRetry = generation(f.sampler);
        f.at(1080L);
        check(generation(f.sampler) == beforeRetry, "first due callback keeps generation");
        f.sampler.changed(); check(!f.prepare(), "second persistent failure");
        check(f.source.drawCalls == 2 && f.consumer.postedDelays.equals(List.of(80L, 240L)), "second failure schedules 240ms backoff");
        f.at(1319L); f.sampler.changed();
        check(!f.prepare() && f.source.drawCalls == 2, "new generation cannot bypass 240ms backoff");
        beforeRetry = generation(f.sampler); f.at(1320L);
        check(generation(f.sampler) == beforeRetry, "second callback keeps generation");
        f.sampler.changed(); check(!f.prepare(), "third persistent failure");
        check(f.source.drawCalls == 3 && f.consumer.pendingCallbacks() == 0 && f.consumer.postCalls == 2,
                "third failure stops retry scheduling entirely");
        for (int i = 0; i < 20; i++) {
            f.at(5000L + i * 1000L); f.sampler.changed();
            check(!f.prepare() && f.source.drawCalls == 3, "exhausted source never resets on consumer generation/time");
        }
        check(f.sampler.revision() == 0L, "all failures leave content revision unchanged");
        f.source.getResources().getDisplayMetrics().density = 2f;
        check(!f.prepare() && f.source.drawCalls == 4, "density change starts an independent capture budget");
        check(f.consumer.pendingCallbacks() == 1 && f.consumer.postedDelays.get(2) == 80L, "new density starts with 80ms backoff");
        Object nextWindow = new Object(); f.source.windowToken = f.consumer.windowToken = nextWindow;
        check(!f.prepare() && f.source.drawCalls == 5, "new window starts another independent budget");
        check(f.consumer.pendingCallbacks() == 1 && f.consumer.removedCallbacks == 1,
                "window reset cancels stale environment callback before posting replacement");
    }

    private static void sourceRevisionRecovery() {
        Fixture f = new Fixture(); f.source.failDraws = 30;
        check(!f.prepare(), "first source failure");
        f.at(1080L); check(!f.prepare(), "second source failure");
        f.at(1320L); check(!f.prepare() && f.source.drawCalls == 3, "third source failure exhausts budget");
        f.source.failDraws = 0; f.at(2000L);
        check(!f.prepare() && f.source.drawCalls == 3, "time alone does not reset exhausted source");
        f.source.content = "new-host-content"; f.source.dirty = true;
        f.source.getViewTreeObserver().dispatchOnPreDraw();
        check(f.prepare() && f.source.drawCalls == 4 && hasContent(f.active(), "new-host-content"),
                "actual clean-to-dirty host revision recovers without restart or resize");
        f.source.dirty = false; f.source.getViewTreeObserver().dispatchOnPreDraw();
        int draws = f.source.drawCalls;
        for (int i = 0; i < 10; i++) { f.source.getViewTreeObserver().dispatchOnPreDraw(); check(f.prepare(), "clean host reuses"); }
        check(f.source.drawCalls == draws, "no full-screen recording on unchanged host frames");
        for (int i = 0; i < 3; i++) {
            f.source.content = "host-refresh-" + i; f.source.dirty = true;
            f.source.getViewTreeObserver().dispatchOnPreDraw();
            check(f.prepare() && hasContent(f.active(), "host-refresh-" + i), "successful host capture follows next real dirty edge");
            f.source.dirty = false; f.source.getViewTreeObserver().dispatchOnPreDraw();
            int refreshed = f.source.drawCalls; check(f.prepare() && f.source.drawCalls == refreshed, "same content is not recorded twice");
        }
        f.sampler.stop();

        Fixture self = new Fixture(); self.source.failDraws = 30; self.source.dirtyOnDraw = true;
        check(!self.prepare(), "capture itself dirties source before throwing");
        self.source.getViewTreeObserver().dispatchOnPreDraw();
        self.at(1080L); check(!self.prepare(), "self-dirty second failure");
        self.source.getViewTreeObserver().dispatchOnPreDraw();
        self.at(1320L); check(!self.prepare(), "self-dirty third failure");
        long content = contentGeneration(self.sampler);
        for (int i = 0; i < 30; i++) {
            self.at(2000L + i * 100L); self.sampler.changed(); self.source.getViewTreeObserver().dispatchOnPreDraw();
            check(!self.prepare() && self.source.drawCalls == 3, "continuous failing/self dirty plus changed never resets budget");
        }
        check(contentGeneration(self.sampler) == content && self.consumer.pendingCallbacks() == 0,
                "capture-owned dirty does not produce an external source token or endless retry");
        self.source.dirty = false; self.source.getViewTreeObserver().dispatchOnPreDraw();
        self.source.content = "real-new-source"; self.source.dirty = true;
        self.source.getViewTreeObserver().dispatchOnPreDraw();
        check(!self.prepare() && self.source.drawCalls == 4, "subsequent real dirty edge grants a new finite budget");
        self.at(4980L); check(!self.prepare() && self.source.drawCalls == 5, "fresh budget second attempt");
        self.at(5220L); check(!self.prepare() && self.source.drawCalls == 6, "fresh budget third attempt");
        self.at(6000L); self.sampler.changed(); self.source.getViewTreeObserver().dispatchOnPreDraw();
        check(!self.prepare() && self.source.drawCalls == 6 && self.consumer.pendingCallbacks() == 0, "second exhausted budget stays finite");
        self.sampler.stop();
    }

    private static void parentBackgroundChanges() {
        ViewGroup parent = new ViewGroup(); parent.height = 800;
        android.graphics.drawable.ColorDrawable background = new android.graphics.drawable.ColorDrawable(0xFF112233);
        parent.background = background;
        View pager = new View(); pager.height = 120; parent.add(pager);
        Fixture f = new Fixture(pager); parent.add(f.consumer); f.consumer.y = 500; f.sampler.source(parent, pager);
        check(f.prepare() && hasContent(f.active(), "color:ff112233"), "initial real parent color recorded");
        parent.dirty = true;
        long revision = f.sampler.revision(), content = contentGeneration(f.sampler);
        for (int i = 0; i < 30; i++) { parent.getViewTreeObserver().dispatchOnPreDraw(); check(f.prepare(), "unchanged parent sample reuses"); }
        check(pager.drawCalls == 1 && f.sampler.revision() == revision && contentGeneration(f.sampler) == content,
                "parent dirtied by overlay changes neither revision nor budget");
        background.setColor(0xFF445566); parent.getViewTreeObserver().dispatchOnPreDraw();
        check(f.prepare() && pager.drawCalls == 2 && hasContent(f.active(), "color:ff445566"), "same Drawable independent color change rerecords");
        check(f.prepare() && pager.drawCalls == 2, "unchanged color does not rerecord again");
        background.setState(new int[]{7}); parent.getViewTreeObserver().dispatchOnPreDraw();
        check(f.prepare() && pager.drawCalls == 3, "drawable state mutation is observable");
        background.setState(new int[]{7}); parent.getViewTreeObserver().dispatchOnPreDraw();
        check(f.prepare() && pager.drawCalls == 3, "equivalent state array is not a change");
        background.level=2; check(f.prepare() && pager.drawCalls == 4, "drawable level change recaptures even without pre-draw");
        background.alpha=240; check(f.prepare() && pager.drawCalls == 5, "drawable alpha change recaptures once");
        parent.background = new android.graphics.drawable.ColorDrawable(0xFF778899);
        check(f.prepare() && pager.drawCalls == 6 && hasContent(f.active(), "color:ff778899"), "drawable identity replacement recaptures");
        f.sampler.stop();
    }

    private static long contentGeneration(ViewBackdropSampler sampler) {
        try { Field field = ViewBackdropSampler.class.getDeclaredField("contentGeneration"); field.setAccessible(true); return field.getLong(sampler); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void windowIsolation() {
        Fixture f = new Fixture(); LiquidGlassLayer layer = new LiquidGlassLayer();
        check(base(layer, f, new Canvas(), 8f, 1f, true), "initial window A base");
        RenderNode a = f.active();
        f.consumer.windowToken = new Object();
        Canvas mismatch = new Canvas();
        check(!base(layer, f, mismatch, 8f, 1f, true), "source/consumer window mismatch cannot sample");
        fallback(mismatch);
        check(f.source.drawCalls == 1 && f.sampler.revision() == 1L, "window mismatch neither captures nor reuses in output");
        f.source.windowToken = f.consumer.windowToken; f.source.content = "B"; f.source.failDraws = 1;
        Canvas switchedFailure = new Canvas();
        check(!base(layer, f, switchedFailure, 8f, 1f, true), "failed first capture in new window cannot reuse A");
        fallback(switchedFailure);
        check(!a.hasDisplayList() && f.sampler.revision() == 1L, "window boundary discards old-window pixels but does not advance success revision");
        f.at(1080L);
        check(base(layer, f, new Canvas(), 8f, 1f, true), "new-window retry succeeds");
        check(f.sampler.revision() == 2L && hasContent(f.active(), "B"), "only new-window B is now published");
    }

    private static void environmentIsolation() {
        String[] changes = { "width", "height", "density", "font scale", "layout direction" };
        for (String change : changes) {
            Fixture f = new Fixture(); LiquidGlassLayer layer = new LiquidGlassLayer();
            check(base(layer, f, new Canvas(), 8f, 1f, true), "initial A before " + change);
            switch (change) {
                case "width": f.source.width++; break;
                case "height": f.source.height++; break;
                case "density": f.source.getResources().getDisplayMetrics().density = 2f; break;
                case "font scale": f.source.getResources().getConfiguration().fontScale = 1.2f; break;
                case "layout direction": f.source.layoutDirection = 1; break;
                default: throw new AssertionError(change);
            }
            f.source.content = "B"; f.source.failDraws = 1;
            Canvas failed = new Canvas();
            check(!base(layer, f, failed, 8f, 1f, true), "failed changed " + change + " cannot reuse A");
            fallback(failed);
            check(f.source.drawCalls == 2 && f.sampler.revision() == 1L, change + " failure leaves published success revision unchanged");
            f.at(1080L);
            check(base(layer, f, new Canvas(), 8f, 1f, true), change + " environment retry succeeds");
            check(f.sampler.revision() == 2L && hasContent(f.active(), "B"), "new " + change + " environment publishes B only after success");
        }
    }

    private static void revisionForcesLayerRerecord() {
        Fixture f = new Fixture(); LiquidGlassLayer layer = new LiquidGlassLayer();
        check(base(layer, f, new Canvas(), 8f, 1f, true), "layer records A initially");
        RenderNode glass = glassNode(), a = f.active();
        List<String> glassA = glass.content();
        check(glass.beginCalls == 1 && glass.successfulEffectSets == 1, "initial record and effect installation");
        long generation = generation(f.sampler);
        check(base(layer, f, new Canvas(), 8f, 1f, true), "unchanged layer draw succeeds");
        check(glass.beginCalls == 1 && f.source.drawCalls == 1, "unchanged sampler revision does not rerecord either node");
        f.source.content = "B"; f.source.failDraws = 1; f.sampler.changed();
        check(generation(f.sampler) > generation, "explicit source change advances generation");
        check(base(layer, f, new Canvas(), 8f, 1f, true), "layer continues A during failed B attempt");
        check(f.sampler.revision() == 1L && f.active() == a, "failed B keeps source A/revision");
        check(glass.beginCalls == 1 && glass.content().equals(glassA), "failed source capture does not rerecord layer from partial B");
        generation = generation(f.sampler); f.at(1080L);
        check(generation(f.sampler) == generation, "retry does not fake a layer change by bumping generation");
        check(base(layer, f, new Canvas(), 8f, 1f, true), "layer samples successful B retry");
        check(f.sampler.revision() == 2L && f.active() != a, "successful B swaps source and advances revision");
        check(glass.beginCalls == 2 && hasContent(glass, "B") && !glass.content().equals(glassA), "revision alone forces actual layer rerecord with B commands");
        check(glass.successfulEffectSets == 1, "content-only swap preserves already correct effect");
    }

    private static void stopCancelsRetry() {
        Fixture f = new Fixture(); check(f.prepare(), "A before stop");
        f.source.content = "B"; f.source.failDraws = 1; f.sampler.changed(); check(f.prepare(), "failed B keeps A before stop");
        check(f.consumer.pendingCallbacks() == 1, "pending retry before stop");
        f.sampler.stop();
        check(f.consumer.pendingCallbacks() == 0 && f.consumer.removedCallbacks == 1, "stop removes pending retry callback");
        check(f.source.attachListenerCount() == 0 && f.source.layoutListenerCount() == 0
                && f.source.getViewTreeObserver().listenerCount() == 0, "stop removes all sampler observers");
        check(RenderNode.instances.stream().noneMatch(RenderNode::hasDisplayList), "stop discards both capture nodes");
        int invalidations = f.consumer.invalidations, draws = f.source.drawCalls;
        f.at(2000L);
        check(f.consumer.invalidations == invalidations && !f.prepare() && f.source.drawCalls == draws, "stopped sampler never wakes/captures after retry time");
        check(f.sampler.revision() == 1L, "stop does not manufacture a content success revision");
        f.sampler.start(); f.sampler.start();
        check(f.source.attachListenerCount() == 1 && f.source.getViewTreeObserver().listenerCount() == 1, "restart is idempotent and observes once");
        check(f.prepare() && f.sampler.revision() == 2L && hasContent(f.active(), "B"), "restart captures B with a fresh state");
        f.sampler.stop();
    }

    private static void detachCancelsRetry() {
        Fixture f = new Fixture(); f.source.failDraws = 1; check(!f.prepare(), "initial failure before detach");
        f.source.detach();
        check(f.consumer.pendingCallbacks() == 0 && f.consumer.removedCallbacks == 1, "source detach cancels retry");
        check(f.source.getViewTreeObserver().listenerCount() == 0, "source detach unobserves pre-draw");
        check(RenderNode.instances.stream().noneMatch(RenderNode::hasDisplayList), "source detach discards both lists");
        int invalidations = f.consumer.invalidations;
        f.at(2000L);
        check(f.consumer.invalidations == invalidations && !f.prepare() && f.source.drawCalls == 1, "detached source receives no delayed capture/wakeup");
        f.source.attach();
        check(f.source.getViewTreeObserver().listenerCount() == 1, "reattach restores pre-draw observer");
        check(f.prepare() && f.source.drawCalls == 2 && f.sampler.revision() == 1L, "reattach clears failed state and recovers");
    }

    private static void inactiveConsumerDoesNotWake() {
        for (boolean hidden : new boolean[] { true, false }) {
            Fixture f = new Fixture(); f.source.failDraws = 1; check(!f.prepare(), "failure before consumer inactivity");
            if (hidden) f.consumer.shown = false; else f.consumer.attached = false;
            int invalidations = f.consumer.invalidations; long generation = generation(f.sampler);
            f.at(1080L);
            check(f.consumer.invalidations == invalidations, "inactive consumer does not receive retry invalidation");
            check(f.consumer.pendingCallbacks() == 0 && generation(f.sampler) == generation, "inactive callback expires without requeue/generation change");
            f.consumer.shown = true; f.consumer.attached = true;
            check(f.prepare() && f.sampler.revision() == 1L, "active consumer can recover with an explicit next draw");
        }
    }

    private static void unavailableBlurRecovery() {
        Fixture f = new Fixture(); LiquidGlassLayer layer = new LiquidGlassLayer();
        check(base(layer, f, new Canvas(), 0f, 1f, true), "blur-zero initial sample");
        RenderNode glass = glassNode();
        check(glass.renderEffect == null && glass.successfulEffectSets == 1, "blur zero installs a null effect successfully");
        long revision = f.sampler.revision();
        Canvas unavailable = new Canvas(); unavailable.hardwareAccelerated = false;
        check(!base(layer, f, unavailable, 14f, 1f, true), "blur 14 request while sample unavailable falls back");
        fallback(unavailable);
        check(glass.beginCalls == 1 && glass.successfulEffectSets == 1 && glass.renderEffect == null,
                "unavailable draw cannot commit requested blur as successfully installed params");
        check(base(layer, f, new Canvas(), 14f, 1f, true), "restored sample uses requested nonzero blur");
        check(f.source.drawCalls == 1 && f.sampler.revision() == revision, "restoration uses same capture/revision");
        check(glass.beginCalls == 2 && glass.successfulEffectSets == 2, "restoration rerecords and reinstalls effect");
        check(containsBlur(glass.renderEffect, 14f), "restored actual effect graph contains blur 14");
        check(lensUniform(glass.renderEffect, "refractionHeight") == 24f, "restored liquid lens uses current density");
    }

    private static void unchangedParamsRecovery() {
        Fixture f = new Fixture(); LiquidGlassLayer layer = new LiquidGlassLayer();
        check(base(layer, f, new Canvas(), 9f, 1f, true), "initial same-params effect");
        RenderNode glass = glassNode(); RenderEffect previous = glass.renderEffect;
        Canvas unavailable = new Canvas();
        check(!base(layer, f, unavailable, 9f, 1f, false), "useBackdrop off makes drawn false without param change");
        fallback(unavailable);
        check(base(layer, f, new Canvas(), 9f, 1f, true), "unchanged requested params recover once drawn resumes");
        check(glass.beginCalls == 2 && glass.successfulEffectSets == 2 && glass.renderEffect != previous,
                "!drawn forces both rerecord and effect recreation even with unchanged params");
        check(f.source.drawCalls == 1 && f.sampler.revision() == 1L, "same-params recovery does not require source revision change");
    }

    private static void densityInvalidatesEffect() {
        Fixture f = new Fixture(); LiquidGlassLayer layer = new LiquidGlassLayer();
        check(base(layer, f, new Canvas(), 8f, 1f, true), "density-one sample");
        RenderNode glass = glassNode(); RenderEffect previous = glass.renderEffect;
        check(lensUniform(previous, "refractionHeight") == 24f, "density-one lens height");
        check(base(layer, f, new Canvas(), 8f, 2f, true), "density-two request with same source capture");
        check(f.source.drawCalls == 1 && f.sampler.revision() == 1L, "effect density test has no source recapture/revision change");
        check(glass.beginCalls == 2 && glass.successfulEffectSets == 2 && glass.renderEffect != previous, "density alone invalidates effect");
        check(lensUniform(glass.renderEffect, "refractionHeight") == 48f
                && lensUniform(glass.renderEffect, "refractionAmount") == -48f, "density-two effect installs scaled current lens uniforms");
        check(containsBlur(glass.renderEffect, 8f), "density-only effect retains requested blur");
        f.source.getResources().getDisplayMetrics().density = 2f;
        check(base(layer, f, new Canvas(), 8f, 2f, true), "source density change also recaptures");
        check(f.source.drawCalls == 2 && f.sampler.revision() == 2L && glass.beginCalls == 3, "source density revision forces rerecord independently");
        check(glass.successfulEffectSets == 2, "same successful effect params remain reusable after source-only recapture");
        check(base(layer, f, new Canvas(), 0f, 2f, true) && glass.renderEffect == null, "returning to blur zero clears old effect");
    }

    private static void effectFailureRecovery() {
        Fixture f = new Fixture(); LiquidGlassLayer layer = new LiquidGlassLayer();
        check(base(layer, f, new Canvas(), 0f, 1f, true), "initial null effect before creation failure");
        RenderNode glass = glassNode(); RenderEffect.failBlurCreations = 1;
        Canvas creationFailure = new Canvas();
        check(!base(layer, f, creationFailure, 11f, 1f, true), "effect creation exception is contained");
        fallback(creationFailure);
        check(!glass.hasDisplayList() && glass.successfulEffectSets == 1, "failed creation discards layer list and does not commit params");
        check(f.source.drawCalls == 1 && f.sampler.revision() == 1L,
                "effect failure keeps source budget and content revision unchanged");
        f.at(1080L);
        check(base(layer, f, new Canvas(), 11f, 1f, true), "same requested blur recovers after bounded creation backoff");
        check(glass.beginCalls == 3 && glass.successfulEffectSets == 2 && containsBlur(glass.renderEffect, 11f), "creation recovery rerecords and installs blur 11");
        glass.failEffects = 1;
        Canvas installationFailure = new Canvas();
        check(!base(layer, f, installationFailure, 12f, 1f, true), "effect installation exception is contained");
        fallback(installationFailure);
        check(!glass.hasDisplayList() && glass.successfulEffectSets == 2 && containsBlur(glass.renderEffect, 11f),
                "failed installation never commits requested blur 12 as installed");
        f.at(1160L);
        check(base(layer, f, new Canvas(), 12f, 1f, true), "same blur 12 request retries installation after bounded backoff");
        check(glass.beginCalls == 5 && glass.successfulEffectSets == 3 && containsBlur(glass.renderEffect, 12f), "installation recovery installs requested graph");
        check(f.source.drawCalls == 1 && f.sampler.revision() == 1L, "effect recovery runs against unchanged real sampler capture");
    }

    private static void layerRecordingFailure() {
        Fixture f = new Fixture(); LiquidGlassLayer layer = new LiquidGlassLayer();
        check(base(layer, f, new Canvas(), 8f, 1f, true), "layer A before endRecording failure");
        RenderNode glass = glassNode(); glass.failEnds = 1;
        f.source.content = "B"; f.sampler.changed();
        Canvas failed = new Canvas();
        check(!base(layer, f, failed, 8f, 1f, true), "layer endRecording exception does not escape");
        fallback(failed);
        check(f.sampler.revision() == 2L && hasContent(f.active(), "B"), "successful source swap is independent from layer recording failure");
        check(!glass.hasDisplayList(), "failed layer recording is discarded rather than drawn");
        f.at(1080L);
        check(base(layer, f, new Canvas(), 8f, 1f, true), "next eligible draw recovers layer from existing B");
        check(f.source.drawCalls == 2 && glass.beginCalls == 3 && hasContent(glass, "B"), "layer retry rerecords B without recapturing source");
        check(glass.successfulEffectSets == 2, "layer failure invalidates successful effect cache for recovery");
    }

    private static void surfaceAvailabilityRecovery() {
        ViewGroup group = new ViewGroup(); Fixture f = new Fixture(group); LiquidGlassLayer layer = new LiquidGlassLayer();
        check(base(layer, f, new Canvas(), 8f, 1f, true), "capturable source before surface appears");
        RenderNode glass = glassNode(); SurfaceView surface = new SurfaceView();
        surface.windowToken = group.windowToken; group.children.add(surface);
        Canvas unavailable = new Canvas();
        check(!base(layer, f, unavailable, 8f, 1f, true), "overlapping uncapturable SurfaceView rejects even cached sample");
        fallback(unavailable);
        check(f.source.drawCalls == 1 && f.sampler.revision() == 1L, "uncapturable source does not attempt or publish a bogus capture");
        group.children.clear();
        check(base(layer, f, new Canvas(), 8f, 1f, true), "removing surface captures the current source again");
        check(f.source.drawCalls == 2 && f.sampler.revision() == 2L && glass.beginCalls == 2 && glass.successfulEffectSets == 2,
                "structural availability recovery captures fresh content, never resurrects pre-surface pixels");
    }

    private static void conversationWallpaper() {
        ViewGroup unrelated = new ViewGroup();
        DynamicBgContainer unrelatedBackground = new DynamicBgContainer();
        unrelatedBackground.add(new DynamicBackgroundGLSurfaceView());
        unrelated.add(unrelatedBackground);
        Fixture unrelatedFixture = new Fixture(unrelated);
        check(!unrelatedFixture.prepare() && unrelatedFixture.sampler.reason() == ViewBackdropSampler.Reason.SURFACE,
                "unrelated host remains blocked by the GL surface");
        unrelatedFixture.sampler.stop();

        MainUIView home = new MainUIView();
        DynamicBgContainer wallpaper = new DynamicBgContainer();
        DynamicBackgroundGLSurfaceView gl = new DynamicBackgroundGLSurfaceView();
        wallpaper.add(gl);
        home.add(wallpaper);
        home.children.remove(wallpaper);
        View list = new View();
        list.content = "list";
        home.add(list);
        Fixture f = new Fixture(home);
        home.add(f.consumer);
        f.sampler.source(home, list);
        check(f.prepare(), "conversation backdrop is available before the pull");
        home.add(wallpaper);
        wallpaper.shown = true;
        list.content = "pulled";
        f.sampler.sourceContentChanged();
        check(f.prepare() && hasContent(f.active(), "pulled"),
                "pull keeps sampling fresh native content with the wallpaper present");
        SurfaceView media = new SurfaceView();
        home.add(media);
        check(!f.prepare() && f.sampler.reason() == ViewBackdropSampler.Reason.SURFACE,
                "other overlapping surfaces are still rejected");
        home.children.remove(media);
        ViewGroup ordinary = new ViewGroup();
        ordinary.add(new DynamicBackgroundGLSurfaceView());
        home.add(ordinary);
        check(!f.prepare() && f.sampler.reason() == ViewBackdropSampler.Reason.SURFACE,
                "the same GL class outside the conversation wallpaper branch is still rejected");
        home.children.remove(ordinary);
        list.content = "settled";
        check(f.prepare() && hasContent(f.active(), "settled"),
                "return to the list captures current pixels");
        f.sampler.stop();
    }

    private static void roiPrunesUnrelatedTrees() {
        ViewGroup root = new ViewGroup(); root.x = 40; root.y = 70;
        for (int page = 0; page < 4; page++) {
            ViewGroup branch = new ViewGroup(); branch.x = 1000 + page * 300;
            for (int i = 0; i < 1100; i++) branch.add(new View());
            root.add(branch);
        }
        ViewGroup invisibleList = new ViewGroup(); invisibleList.shown = false;
        for (int i = 0; i < 1100; i++) invisibleList.add(new View());
        root.add(invisibleList);
        ViewGroup current = new ViewGroup(); current.x = 40; current.y = 70; root.add(current);
        View row = new View(); row.x = 40; row.y = 70; current.add(row);
        Fixture f = new Fixture(root); f.consumer.x = 40; f.consumer.y = 70;
        check(f.prepare() && f.sampler.reason() == ViewBackdropSampler.Reason.READY, "far-page and invisible list trees do not block capture");
        check(f.sampler.visited() == 3, "budget includes only root/current/visible row");
        check(f.source.drawCalls == 1 && hasContent(f.active(), "A"), "success records actual host content");
        f.sampler.stop();
    }

    private static void roiSafety() {
        ViewGroup root = new ViewGroup(); Fixture f = new Fixture(root);
        android.view.TextureView texture = new android.view.TextureView(); texture.x = 1000;
        root.add(texture);
        check(f.prepare(), "off-ROI Texture is safe");
        texture.x = 0;
        check(!f.prepare() && f.sampler.reason() == ViewBackdropSampler.Reason.SURFACE, "intersecting Texture rejects cached capture");
        check(f.consumer.pendingCallbacks() == 1 && f.consumer.nextCallbackAt() == 1080L,
                "structural failure has one bounded recheck, not a continuous poll");
        root.children.clear(); f.source.content = "B";
        f.source.getViewTreeObserver().dispatchOnPreDraw();
        check(f.prepare() && hasContent(f.active(), "B"), "removal recovers with fresh B content");
        ViewGroup overflow = new ViewGroup(); overflow.x = 1000; overflow.clipChildren = false;
        SurfaceView surface = new SurfaceView(); surface.x = 0; overflow.add(surface); root.add(overflow);
        check(!f.prepare() && f.sampler.reason() == ViewBackdropSampler.Reason.SURFACE, "unclipped far group cannot conceal overflowing Surface");
        overflow.clipChildren = true;
        check(f.prepare(), "clipped far group is correctly excluded");
        root.children.clear();
        for (int i = 0; i < 800; i++) root.add(new View());
        check(!f.prepare() && f.sampler.reason() == ViewBackdropSampler.Reason.TREE_BUDGET && f.sampler.visited() == 768,
                "unfinished relevant tree fails closed at exactly 768");
        root.children.clear(); View uncertain = new View(); uncertain.throwBounds = true; root.add(uncertain);
        check(!f.prepare() && f.sampler.reason() == ViewBackdropSampler.Reason.UNSAFE_BOUNDS, "unresolvable visible bounds fail closed");
        root.children.clear();
        for (int i = 0; i < 40; i++) { root.children.add(surface); f.prepare(); root.children.clear(); f.prepare(); }
        check(NavigationBarFeature.logs.size() <= 12, "reason transition diagnostics have a hard lifetime budget");
        f.sampler.stop();
    }

    private static void outsideSource() {
        Fixture f = new Fixture(); f.source.height = 20; f.consumer.y = 200;
        check(!f.prepare() && f.sampler.reason() == ViewBackdropSampler.Reason.NO_INTERSECTION, "outside source is not an empty successful draw");
        check(f.source.drawCalls == 0 && f.sampler.revision() == 0 && f.consumer.pendingCallbacks() == 1,
                "outside region never records/advances success and only posts a finite safety recheck");
        f.source.height = 400; f.source.content = "layout-recovered";
        f.source.getViewTreeObserver().dispatchOnPreDraw();
        check(f.prepare() && hasContent(f.active(), "layout-recovered"), "source layout intersection recovers actual pixels");
        check(f.active().bottom == 400 && f.sampler.offsetY() == 200, "real source dimensions and coordinate alignment retained");
        f.source.clippedBounds = new Rect(0, 0, 300, 190);
        check(!f.prepare() && f.sampler.reason() == ViewBackdropSampler.Reason.NO_INTERSECTION, "ancestor-clipped source is not accepted by raw dimensions");
        f.source.clippedBounds = null; f.source.x = 500; f.source.y = 900;
        f.consumer.x = 500; f.consumer.y = 920;
        check(f.prepare() && f.sampler.offsetY() == 20,
                "root-visible bounds map to window coordinates for a nonzero root origin");
        f.sampler.stop();
    }

    private static void parentBackgroundSource() {
        ViewGroup parent = new ViewGroup(); parent.height = 800;
        parent.background = new android.graphics.drawable.Drawable();
        View pager = new View(); pager.height = 120; pager.content = "pager-content";
        parent.add(pager);
        Fixture f = new Fixture(pager); parent.add(f.consumer); f.consumer.y = 500; f.consumer.content = "MODULE-MUST-NOT-RECORD";
        ViewGroup moduleChild = new ViewGroup(); moduleChild.add(new SurfaceView());
        // The module subtree is not part of the safety walk or the native recording.
        f.sampler.source(parent, pager);
        check(f.prepare(), "parent background supplies real sample below short pager");
        check(parent.drawCalls == 0 && f.consumer.drawCalls == 0, "parent drawChild/cache path and entire consumer draw are never invoked");
        check(pager.drawCalls == 1 && hasContent(f.active(), "host-background") && hasContent(f.active(), "pager-content"),
                "parent-source success contains actual background and native content");
        check(!hasContent(f.active(), "MODULE-MUST-NOT-RECORD") && f.active().bottom == 800, "no self content and actual parent-sized source");
        check(hasContent(f.active(), "background-bounds:300x800") && parent.background.getBounds().width() == 0,
                "real parent background gets source-sized bounds only during recording and original bounds are restored");
        check(f.consumer.visibility == View.VISIBLE && !f.sampler.isCapturing(), "recording never hides real consumer or leaves capture guard set");
        long generation = generation(f.sampler); int draws = pager.drawCalls;
        parent.dirty = true; parent.getViewTreeObserver().dispatchOnPreDraw();
        check(generation(f.sampler) == generation && f.prepare() && pager.drawCalls == draws, "parent dirtied by overlay is not a 60fps recapture trigger");
        parent.background = null;
        check(!f.prepare() && f.sampler.reason() == ViewBackdropSampler.Reason.NO_BACKGROUND, "larger transparent parent alone is not evidence of sample pixels");
        parent.background = new android.graphics.drawable.Drawable(); pager.failDraws = 1;
        check(!f.prepare() && !f.sampler.isCapturing() && f.consumer.visibility == View.VISIBLE, "failed parent-content draw always clears guard without hiding consumer");
        f.at(1080L); check(f.prepare(), "parent-content fault retains bounded retry recovery");
        f.sampler.stop();
    }

    private static void sourceRebind() {
        Fixture f = new Fixture(); f.source.failDraws = 1; check(!f.prepare(), "old source retry pending");
        ViewGroup next = new ViewGroup(); next.background = new android.graphics.drawable.Drawable();
        View pager = new View(); next.add(pager); next.add(f.consumer);
        f.sampler.source(next, pager);
        check(f.consumer.pendingCallbacks() == 0 && f.source.attachListenerCount() == 0
                && f.source.layoutListenerCount() == 0 && f.source.getViewTreeObserver().listenerCount() == 0,
                "rebind cancels old callback and all old source listeners");
        check(RenderNode.instances.stream().noneMatch(RenderNode::hasDisplayList), "rebind clears both old displaylists");
        check(next.attachListenerCount() == 1 && next.getViewTreeObserver().listenerCount() == 1, "new source observed once");
        check(f.prepare() && pager.drawCalls == 1, "new source captures independently");
        f.sampler.stop();
        check(next.attachListenerCount() == 0 && next.layoutListenerCount() == 0 && next.getViewTreeObserver().listenerCount() == 0,
                "stop cleans new source listeners");
    }

    private static void structuralBudget() {
        ViewGroup group = new ViewGroup(); Fixture f = new Fixture(group); group.failDraws = 30;
        check(!f.prepare(), "first recording fault");
        SurfaceView surface = new SurfaceView();
        for (long time : new long[] {1080L, 1320L, 5000L, 10000L}) {
            group.children.add(surface); check(!f.prepare(), "temporary surface availability blocker");
            group.children.clear(); f.at(time); f.sampler.changed(); check(!f.prepare(), "recording fault or exhausted budget after surface removal");
        }
        check(group.drawCalls == 3 && f.consumer.pendingCallbacks() == 0, "surface toggles never create a fourth draw fault or unbounded retry");
        f.sampler.stop();
    }

    /** Drains posted production callbacks and their resulting normal frame; never manually prepares after a timer. */
    private static final class Scheduled {
        final Fixture f;
        final LiquidGlassLayer layer;
        int frames;
        boolean sampled;
        boolean retryPending;
        long token;
        final Runnable retry;
        Scheduled(Fixture f, LiquidGlassLayer layer) {
            this.f = f; this.layer = layer;
            retry = () -> {
                retryPending = false;
                if (token == layerToken(layer) && f.consumer.isAttachedToWindow() && f.consumer.isShown())
                    f.consumer.invalidate();
            };
            f.consumer.onInvalidatedFrame = this::frame;
        }
        void frame() {
            frames++;
            f.source.getViewTreeObserver().dispatchOnPreDraw();
            sampled = layer == null ? f.prepare() : base(layer, f, new Canvas(), 8f, 1f, true);
            if (layer != null && !sampled && layerFailed(layer)) {
                long delay = layerDelay(layer);
                if (delay >= 0 && !retryPending) {
                    token = layerToken(layer); retryPending = true;
                    f.consumer.postDelayed(retry, Math.max(1L, delay));
                }
            }
        }
        void request() { f.consumer.invalidate(); }
        void drain() {
            int drained = 0;
            while (f.consumer.pendingCallbacks() > 0 && drained++ < 40) f.at(f.consumer.nextCallbackAt());
            check(f.consumer.pendingCallbacks() == 0, "actual scheduler task queue quiesces without infinite polling/frames");
        }
    }

    private static void explicitSourceScheduledRecovery() {
        ViewGroup parent = new ViewGroup(); parent.background = new android.graphics.drawable.Drawable();
        View pager = new View(); parent.add(pager);
        Fixture f = new Fixture(pager); parent.add(f.consumer); f.sampler.source(parent, pager);
        Scheduled tasks = new Scheduled(f, null); pager.failDraws = 30;
        tasks.request(); tasks.drain();
        check(pager.drawCalls == 3 && !tasks.sampled, "old clean pager exhausts exactly three source attempts");
        pager.failDraws = 0; pager.content = "list-position-top-empty-changed";
        check(!pager.dirty && !pager.layoutRequested, "actual list revision does not depend on dirty/layout at pager");
        long revision = f.sampler.revision(), content = contentGeneration(f.sampler);
        notifySource(f.sampler);
        check(f.sampler.revision() == revision && contentGeneration(f.sampler) == content + 1,
                "explicit list content token is independent from published capture revision");
        tasks.drain();
        check(tasks.sampled && pager.drawCalls == 4 && hasContent(f.active(), "list-position-top-empty-changed"),
                "source notification and its normal scheduled frame recover real content without manual prepare");
        long recovered = f.sampler.revision();
        for (int i = 0; i < 10; i++) { tasks.request(); tasks.drain(); }
        check(pager.drawCalls == 4 && f.sampler.revision() == recovered,
                "ordinary consumer frames never become a new content token or a capture clock");
        f.sampler.stop(); notifySource(f.sampler);
        check(contentGeneration(f.sampler) == content + 1 && f.consumer.pendingCallbacks() == 0,
                "stopped explicit source notification is refused");
    }

    private static void scheduledAvailabilityRecovery() {
        for (String blocker : new String[] { "surface", "texture", "background", "intersection", "bounds", "tree" }) {
            ViewGroup parent = new ViewGroup(); parent.background = new android.graphics.drawable.Drawable();
            View pager = new View(); pager.height = 240; parent.add(pager);
            Fixture f = new Fixture(pager); parent.add(f.consumer); f.sampler.source(parent, pager);
            Scheduled tasks = new Scheduled(f, null);
            tasks.request(); tasks.drain();
            check(tasks.sampled, "initial safe capture before " + blocker);
            RenderNode old = f.active();
            Runnable clear;
            switch (blocker) {
                case "surface": case "texture":
                    View surface = blocker.equals("surface") ? new SurfaceView() : new android.view.TextureView();
                    parent.add(surface); clear = () -> parent.children.remove(surface); break;
                case "background":
                    pager.height = 10; parent.background = null;
                    clear = () -> parent.background = new android.graphics.drawable.Drawable(); break;
                case "intersection": parent.clippedBounds = new Rect(0, 0, 5, 5); clear = () -> parent.clippedBounds = null; break;
                case "bounds": parent.matrix.identity = false; clear = () -> parent.matrix.identity = true; break;
                case "tree":
                    for (int i = 0; i < 800; i++) parent.add(new View());
                    clear = () -> parent.children.removeIf(view -> view != pager && view != f.consumer); break;
                default: throw new AssertionError(blocker);
            }
            tasks.request(); f.at(f.consumer.nextCallbackAt());
            check(!tasks.sampled && !old.hasDisplayList(), blocker + " refuses old pixels and does not retain unsafe frame");
            check(f.consumer.pendingCallbacks() == 1, blocker + " has one finite original-gate recheck");
            pager.content = "after-" + blocker; clear.run();
            tasks.drain();
            check(tasks.sampled && pager.drawCalls == 2 && hasContent(f.active(), "after-" + blocker),
                    blocker + " removal needs no external draw/preDraw/dirty signal after the blocked frame");
            f.sampler.stop();
        }
        ViewGroup group = new ViewGroup(); group.add(new SurfaceView()); Fixture permanent = new Fixture(group);
        Scheduled tasks = new Scheduled(permanent, null); tasks.request(); tasks.drain();
        check(group.drawCalls == 0 && permanent.consumer.postedDelays.equals(List.of(80L, 240L)),
                "permanent unsafe source has only 80/240ms bounded safety rechecks and no capture");
        for (int i = 0; i < 12; i++) { permanent.sampler.changed(); tasks.drain(); }
        check(permanent.consumer.postCalls == 2 && group.drawCalls == 0, "changed and gate jitter cannot replenish exhausted rechecks");
        permanent.sampler.stop();

        Fixture hidden = new Fixture(); Scheduled hiddenTasks = new Scheduled(hidden, null);
        hidden.source.shown = false; hiddenTasks.request(); hidden.at(hidden.consumer.nextCallbackAt());
        check(!hiddenTasks.sampled && hidden.consumer.pendingCallbacks() == 1, "attached hidden source permits bounded recheck");
        hidden.source.shown = true; hiddenTasks.drain();
        check(hiddenTasks.sampled && hidden.source.drawCalls == 1, "hidden source returns without an external frame");
        hidden.sampler.stop();

        ViewGroup jitter = new ViewGroup(); Fixture failed = new Fixture(jitter); jitter.failDraws = 30;
        Scheduled limited = new Scheduled(failed, null); limited.request(); failed.at(failed.consumer.nextCallbackAt());
        jitter.add(new SurfaceView()); failed.at(failed.consumer.nextCallbackAt());
        check(jitter.drawCalls == 1 && failed.consumer.pendingCallbacks() == 1,
                "capture retry encountering an unsafe source checks gates without spending capture attempt");
        jitter.children.clear(); limited.drain();
        check(jitter.drawCalls == 3 && failed.consumer.pendingCallbacks() == 0,
                "safety recheck returns to original finite capture budget and then stops");
        for (int i = 0; i < 8; i++) {
            jitter.children.add(new SurfaceView()); failed.sampler.changed(); limited.drain();
            jitter.children.clear(); failed.sampler.changed(); limited.drain();
        }
        check(jitter.drawCalls == 3 && failed.consumer.pendingCallbacks() == 0,
                "gate jitter and changed after capture exhaustion cannot grant rechecks or another attempt");
        failed.sampler.stop();

        ViewGroup cancelGroup = new ViewGroup(); cancelGroup.add(new SurfaceView()); Fixture cancel = new Fixture(cancelGroup);
        Scheduled cancelTasks = new Scheduled(cancel, null); cancelTasks.request(); cancel.at(cancel.consumer.nextCallbackAt());
        check(cancel.consumer.pendingCallbacks() == 1, "safe recheck queued before stop");
        int invalidations = cancel.consumer.invalidations; cancel.sampler.stop(); cancelTasks.drain();
        check(cancel.consumer.invalidations == invalidations && cancelGroup.drawCalls == 0,
                "stop cancels internal safety recheck and cannot wake/capture afterward");
    }

    private static void lostDisplayListBudget() {
        Fixture missing = new Fixture(); check(missing.prepare(), "A before node eviction");
        missing.active().discardDisplayList();
        check(missing.prepare() && missing.source.drawCalls == 2 && missing.sampler.revision() == 2,
                "successful key without a display list is not REUSE and must capture again");
        missing.sampler.stop();

        Fixture f = new Fixture(); check(f.prepare(), "retained A before B failure");
        f.source.content = "B"; f.source.failDraws = 30; f.sampler.changed(); check(f.prepare(), "B failure normally retains A");
        f.active().discardDisplayList();
        check(!f.prepare() && f.source.drawCalls == 2 && f.consumer.nextCallbackAt() == 1080L,
                "loss revokes success only: B failure and original 80ms timer remain");
        f.at(1080L); check(!f.prepare(), "second B failure after eviction");
        f.at(1320L); check(!f.prepare() && f.source.drawCalls == 4, "third B failure still exhausts budget");
        for (int i = 0; i < 12; i++) { f.sampler.changed(); f.at(5000L + i); check(!f.prepare(), "missing-node changed remains exhausted"); }
        check(f.source.drawCalls == 4 && f.consumer.pendingCallbacks() == 0, "eviction cannot reset failures or start a fourth B capture");
        f.sampler.stop();
    }

    private static void scheduledLayerRecovery() {
        for (boolean begin : new boolean[] { true, false }) {
            Fixture f = new Fixture(); LiquidGlassLayer layer = new LiquidGlassLayer();
            Scheduled tasks = new Scheduled(f, layer); RenderNode glass = glassNode();
            if (begin) glass.failBegins = 1; else glass.failEffects = 1;
            tasks.request(); f.at(f.consumer.nextCallbackAt());
            check(!tasks.sampled && layerFailed(layer) && layerDelay(layer) == 80L,
                    "layer " + (begin ? "beginRecording" : "setRenderEffect") + " failure exposes independent scheduled retry");
            check(f.sampler.reason() == ViewBackdropSampler.Reason.READY && f.source.drawCalls == 1,
                    "caller can distinguish layer failure from sampler not ready");
            long token = layerToken(layer);
            tasks.drain();
            check(tasks.sampled && !layerFailed(layer) && layerDelay(layer) == -1L && layerToken(layer) != token,
                    "normal timer-driven frame rebuilds layer and cancels stale retry token");
            check(f.source.drawCalls == 1 && f.sampler.revision() == 1L && glass.beginCalls == 2,
                    "layer retry uses the cached source and never resamples unchanged source");
            layer.release(); f.sampler.stop();

            Fixture persistent = new Fixture(); LiquidGlassLayer broken = new LiquidGlassLayer();
            Scheduled limited = new Scheduled(persistent, broken); RenderNode node = glassNode();
            if (begin) node.failBegins = 30; else node.failEffects = 30;
            limited.request(); limited.drain();
            check(!limited.sampled && layerFailed(broken) && layerDelay(broken) == -1L
                    && node.beginCalls == 3 && persistent.source.drawCalls == 1,
                    "persistent layer failure stops after three independent rebuilds while source stays cached");
            for (int i = 0; i < 12; i++) { limited.request(); limited.drain(); }
            check(node.beginCalls == 3 && persistent.consumer.postCalls == 2, "external frames cannot replenish layer retry budget");
            broken.release(); persistent.sampler.stop();
        }
        Fixture stopped = new Fixture(); LiquidGlassLayer layer = new LiquidGlassLayer(); Scheduled tasks = new Scheduled(stopped, layer);
        glassNode().failEffects = 1; tasks.request(); stopped.at(stopped.consumer.nextCallbackAt());
        long token = layerToken(layer); int frames = tasks.frames;
        layer.release(); stopped.sampler.stop(); tasks.drain();
        check(layerToken(layer) != token && layerDelay(layer) == -1L && tasks.frames == frames,
                "release invalidates old layer retry token and stopped sampler never wakes a capture");
    }

    private static void selfNotificationBudget() {
        Fixture f = new Fixture(); f.source.failDraws = 30; f.source.dirtyOnDraw = true;
        int[] selfNotifications = { 0 };
        f.source.onDraw = () -> { notifySource(f.sampler); selfNotifications[0]++; };
        Scheduled tasks = new Scheduled(f, null); long content = contentGeneration(f.sampler);
        tasks.request(); tasks.drain();
        check(selfNotifications[0] == 3 && f.source.drawCalls == 3
                && contentGeneration(f.sampler) == content && f.sampler.revision() == 0L,
                "recording-owned list notification and dirty signal cannot mint another source revision/budget");
        for (int i = 0; i < 20; i++) { f.sampler.changed(); tasks.drain(); }
        check(f.source.drawCalls == 3 && f.consumer.postCalls == 2, "self draw plus invalidation does not extend finite scheduler");
        f.sampler.stop();
    }

    private static Object api(Object target, String name) {
        try {
            java.lang.reflect.Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException error) { throw new AssertionError("production recovery API " + name + " unavailable/failed", error); }
    }
    private static void notifySource(ViewBackdropSampler sampler) { api(sampler, "sourceContentChanged"); }
    private static boolean layerFailed(LiquidGlassLayer layer) { return (Boolean) api(layer, "rebuildFailed"); }
    private static long layerDelay(LiquidGlassLayer layer) { return (Long) api(layer, "retryDelay"); }
    private static long layerToken(LiquidGlassLayer layer) { return (Long) api(layer, "retryBudget"); }

    private static boolean base(LiquidGlassLayer layer, Fixture f, Canvas output, float blur, float density, boolean useBackdrop) {
        return layer.drawBase(output, f.sampler, GLASS, 12f, blur, true, false, density, useBackdrop);
    }
    private static void fallback(Canvas output) {
        check(output.renderedNodes.isEmpty(), "fallback output never draws stale/source/layer node");
        check(!output.rectColors.isEmpty() && (output.rectColors.get(output.rectColors.size() - 1) >>> 24) == 255,
                "fallback is actually drawn fully opaque");
    }
    private static RenderNode glassNode() {
        return RenderNode.instances.stream().filter(value -> value.name.equals("MindLink-liquid-background")).findFirst().orElseThrow();
    }
    private static boolean hasContent(RenderNode node, String content) {
        return node.content().stream().anyMatch(value -> value.contains("content:" + content));
    }
    private static boolean containsBlur(RenderEffect effect, float radius) {
        return effect != null && ((effect.kind.equals("blur") && effect.blurX == radius && effect.blurY == radius)
                || containsBlur(effect.first, radius) || containsBlur(effect.second, radius));
    }
    private static float lensUniform(RenderEffect effect, String name) {
        if (effect == null) return Float.NaN;
        if (effect.shader != null && effect.shader.uniforms.containsKey(name)) return effect.shader.uniforms.get(name)[0];
        float first = lensUniform(effect.first, name);
        return Float.isNaN(first) ? lensUniform(effect.second, name) : first;
    }
    private static long generation(ViewBackdropSampler sampler) {
        // Observation only: recovery calls the real prepare/changed/callback paths, never a copied state machine.
        try { Field field = ViewBackdropSampler.class.getDeclaredField("generation"); field.setAccessible(true); return field.getLong(sampler); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void run(String name, Runnable test) {
        cases++; int before = assertions;
        try { test.run(); System.out.println("PASS: " + name + " (" + (assertions - before) + " assertions)"); }
        catch (Throwable error) { failures++; System.out.println("FAIL: " + name); error.printStackTrace(System.out); }
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
