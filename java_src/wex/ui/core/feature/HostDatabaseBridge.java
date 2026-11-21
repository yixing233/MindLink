package wex.ui.core.feature;

import android.content.Context;
import android.database.Cursor;
import android.os.Looper;
import java.lang.ref.WeakReference;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Captures WeChat's initialized database wrapper and exposes read-only queries. */
public final class HostDatabaseBridge {
    private static final String TAG = "WEX: 数据库桥: ";
    private static volatile WeakReference<Object> database = new WeakReference<>(null);
    private static final Object STATE = new Object(), EVIDENCE = new Object(), METADATA = new Object();
    private static volatile long epoch;
    private static boolean captureQueued, capturePending, recoverPending;
    private static long maxPendingCaptures, metadataMisses, negativeMetadataHits;
    private static final Map<Class<?>, DbMetadata> DB_METADATA = new java.util.IdentityHashMap<>();
    private static final Map<Class<?>, HolderMetadata> HOLDERS = new java.util.IdentityHashMap<>();
    private static volatile HolderMetadata coreMetadata, configMetadata;
    private static volatile ProbeSnapshot coreProbe, configProbe;
    private static final class ProbeSnapshot {
        final Class<?> type;
        final Field[] fields;
        final WeakReference<Object>[] values;
        @SuppressWarnings("unchecked")
        ProbeSnapshot(Object owner, HolderMetadata metadata) throws IllegalAccessException {
            type = metadata.type;
            List<Field> selected = new ArrayList<>();
            // Worker learns actual DB holders even when their declared interface/base/wrapper
            // has negative metadata. Hook validation only reads these already-proven Fields.
            for (Field field : metadata.fields) {
                Object value = field.get(owner);
                boolean actualDatabase = value != null && databaseMetadata(value.getClass()).query != null;
                boolean declaredProbe = java.util.Arrays.asList(metadata.probes).contains(field);
                if (actualDatabase || declaredProbe) selected.add(field);
            }
            if (selected.size() > 8) throw new IllegalStateException("Database holder evidence bound exceeded");
            fields = selected.toArray(new Field[0]); values = new WeakReference[fields.length];
            for (int i = 0; i < fields.length; i++) values[i] = new WeakReference<>(fields[i].get(owner));
        }
        boolean matches(Object owner) throws IllegalAccessException {
            if (owner == null || owner.getClass() != type) return false;
            for (int i = 0; i < fields.length; i++) if (fields[i].get(owner) != values[i].get()) return false;
            return true;
        }
    }
    private static volatile Field configHolder;
    private static volatile Class<?> configHolderType;
    private static final class DbMetadata {
        final Method query, open;
        DbMetadata(Method query, Method open) { this.query = query; this.open = open; }
    }
    private static final class HolderMetadata {
        final Class<?> type;
        final Field[] fields;
        final Field[] probes;
        HolderMetadata(Class<?> type, Field[] fields, Field[] probes) { this.type = type; this.fields = fields; this.probes = probes; }
    }
    private static volatile Method rawQuery;
    private static volatile boolean installed;
    private static final List<java.lang.ref.WeakReference<Runnable>> READY = new ArrayList<>();
    private static volatile String status = "UNINITIALIZED";
    private static final java.util.Set<java.lang.reflect.Member> HOOKS = new HashSet<>();
    private static volatile Class<?> storageClass;
    private static volatile Class<?> kernelClass;
    private static volatile Field kernelCoreField, kernelAccountField, accountStateField;
    private static volatile java.lang.ref.WeakReference<Object> observedKernel = new java.lang.ref.WeakReference<>(null);
    private static volatile java.lang.ref.WeakReference<Object> observedCore = new java.lang.ref.WeakReference<>(null);
    private static final ThreadLocal<Boolean> CAPTURING = new ThreadLocal<>();
    private static volatile java.lang.ref.WeakReference<Object> currentAccount = new java.lang.ref.WeakReference<>(null);
    private static volatile java.lang.ref.WeakReference<Object> currentConfig = new java.lang.ref.WeakReference<>(null);
    private static volatile long captureVersion;
    private static volatile boolean kernelRevoked;
    private static long lastDemand;
    private static int demandAttempts;
    private static String lastProfileGate = "";
    private static long captureEvents;
    // Four passive callback routes, entry/result each capped at three: <=24 new events/process.
    private static final int[] CALLBACK_EVENTS = new int[8];

    private static void callbackEvidence(int slot, String stage) {
        int count;
        synchronized (EVIDENCE) {
            if (CALLBACK_EVENTS[slot] >= 3) return;
            count = ++CALLBACK_EVENTS[slot];
        }
        log(stage + " count=" + count);
    }

    private HostDatabaseBridge() {
    }

    public static synchronized void initialize(XC_LoadPackage.LoadPackageParam lpparam,
                                               Context context) {
        HostTargetResolver.initialize(lpparam, context);
        if (installed) {
            return;
        }
        installed = true;
        status = "RESOLVING";
        installPinnedEarlyObservers(lpparam);
        log("stage=resolve started");
        HostTargetResolver.resolveMethod("database_storage_get",
                new HostTargetResolver.MethodSpec()
                        .usingStrings("mCoreStorage not initialized!")
                        .parameterCount(0),
                new HostTargetResolver.MethodCallback() {
                    @Override
                    public void onResolved(Method method) {
                        try {
                            Class<?> owner = method.getReturnType();
                            if (owner == void.class || owner.isPrimitive() || owner.isInterface())
                                throw new IllegalStateException("CoreStorage target has no concrete return type");
                            storageClass = owner;
                            registerHook(method, new XC_MethodHook() {
                                @Override protected void afterHookedMethod(MethodHookParam param) {
                                    callbackEvidence(6, "stage=storage-getter-entry");
                                    boolean available = !param.hasThrowable() && owner.isInstance(param.getResult());
                                    callbackEvidence(7, "stage=storage-getter-result threw=" + param.hasThrowable()
                                            + " availableCore=" + available);
                                    if (available) capture(param.getResult());
                                }
                            });
                            status = isReady() ? status : "HOOKED";
                            log("stage=hook registered " + method.getDeclaringClass().getName()
                                    + "#" + method.getName());
                            observeStorage(owner);
                            try { observeKernel(method); }
                            catch (Throwable error) { log("stage=kernel-observe failed " + error.getClass().getSimpleName()); }
                        } catch (Throwable error) {
                            installed = false;
                            status = "FAILED:" + error.getClass().getSimpleName();
                            log("stage=hook failed " + error.getClass().getSimpleName());
                        }
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        installed = false;
                        status = "FAILED:" + error.getClass().getSimpleName();
                        log("数据库入口不可用 " + error.getClass().getSimpleName());
                    }
                });
    }

    /** Class identity metadata only: never initializes or retrieves a Kernel singleton. */
    static boolean matchesRuntimeKernel(ClassLoader loader) {
        Class<?> observed = kernelClass;
        if (loader == null || observed == null) return false;
        try { return Class.forName("hm0.j1", false, loader) == observed; }
        catch (Throwable ignored) { return false; }
    }

    private static void installPinnedEarlyObservers(XC_LoadPackage.LoadPackageParam load) {
        try {
            ClassLoader selected = HostTargetResolver.hostClassLoader();
            if (load == null || load.appInfo == null || load.appInfo.sourceDir == null || selected == null) return;
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream input = new java.io.FileInputStream(load.appInfo.sourceDir)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) >= 0) if (count > 0) digest.update(buffer, 0, count);
            }
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            if (!"04792eb19a566395a9fabee9a50be80245826cb774ec64637a22f7f28acd4054".equals(hash.toString())) {
                log("stage=early-observe skipped hostMatched=false"); return;
            }
            Class<?> kernel = Class.forName("hm0.j1", false, selected);
            Class<?> core = Class.forName("hm0.b0", false, selected);
            Class<?> account = Class.forName("hm0.m", false, selected);
            Class<?> config = Class.forName("com.tencent.mm.storage.m3", false, selected);
            Class<?> task = Class.forName("hm0.f", false, selected);
            Method instance = kernel.getDeclaredMethod("i");
            Method storage = kernel.getDeclaredMethod("u");
            Method accountGetter = kernel.getDeclaredMethod("b");
            Method configGetter = core.getDeclaredMethod("c");
            Method ready = task.getDeclaredMethod("run");
            if (!Modifier.isPublic(instance.getModifiers()) || !Modifier.isStatic(instance.getModifiers()) || instance.getReturnType() != kernel
                    || !Modifier.isPublic(storage.getModifiers()) || !Modifier.isStatic(storage.getModifiers()) || storage.getReturnType() != core
                    || !Modifier.isPublic(accountGetter.getModifiers()) || !Modifier.isStatic(accountGetter.getModifiers()) || accountGetter.getReturnType() != account
                    || !Modifier.isPublic(configGetter.getModifiers()) || configGetter.getReturnType() != config || Modifier.isStatic(configGetter.getModifiers())
                    || !Runnable.class.isAssignableFrom(task) || !Modifier.isPublic(ready.getModifiers()) || ready.getReturnType() != void.class || Modifier.isStatic(ready.getModifiers()))
                throw new IllegalStateException("Pinned host signature mismatch");
            Field coreField = directField(kernel, core), accountField = directField(kernel, account);
            Field configField = directField(core, config), taskField = directField(task, account);
            Field phase = account.getDeclaredField("o");
            if (coreField == null || accountField == null || configField == null || taskField == null
                    || !"d".equals(coreField.getName()) || !"e".equals(accountField.getName())
                    || !"g".equals(configField.getName()) || !"d".equals(taskField.getName())
                    || !Modifier.isPublic(coreField.getModifiers()) || !Modifier.isPublic(accountField.getModifiers())
                    || !Modifier.isPublic(configField.getModifiers()) || !Modifier.isPublic(taskField.getModifiers())
                    || Modifier.isStatic(phase.getModifiers()) || !Modifier.isVolatile(phase.getModifiers()) || !phase.getType().isEnum())
                throw new IllegalStateException("Pinned host field mismatch");
            phase.setAccessible(true);
            storageClass = core; kernelClass = kernel;
            kernelCoreField = coreField; kernelAccountField = accountField; accountStateField = phase;
            log("stage=runtime-loader source=selected kernelMatched=" + matchesRuntimeKernel(selected));
            registerHook(instance, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    callbackEvidence(0, "stage=early-instance-entry");
                    boolean available = !param.hasThrowable() && kernel.isInstance(param.getResult());
                    callbackEvidence(1, "stage=early-instance-result threw=" + param.hasThrowable()
                            + " availableKernel=" + available);
                    if (available) captureKernel(param.getResult());
                }
            });
            for (java.lang.reflect.Constructor<?> constructor : kernel.getDeclaredConstructors()) {
                registerHook(constructor, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        callbackEvidence(2, "stage=early-ctor-entry");
                        boolean available = !param.hasThrowable() && kernel.isInstance(param.thisObject);
                        callbackEvidence(3, "stage=early-ctor-result threw=" + param.hasThrowable()
                                + " availableKernel=" + available);
                        if (!available) return;
                        observedKernel = new java.lang.ref.WeakReference<>(param.thisObject);
                        log("stage=early-kernel observed available=true");
                    }
                });
            }
            registerHook(ready, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    callbackEvidence(4, "stage=early-ready-entry");
                    Object existing = observedKernel.get();
                    callbackEvidence(5, "stage=early-ready-result threw=" + param.hasThrowable()
                            + " availableKernel=" + (existing != null) + " availableTask=" + (param.thisObject != null));
                    if (param.hasThrowable() || param.thisObject == null || existing == null) return;
                    try { if (taskField.get(param.thisObject) == accountField.get(existing)) captureKernel(existing); }
                    catch (Throwable error) { profileGate("stage=early-ready failed " + error.getClass().getSimpleName()); }
                }
            });
            log("stage=early-observe registered hostMatched=true");
        } catch (Throwable error) { log("stage=early-observe failed " + error.getClass().getSimpleName()); }
    }

    private static synchronized void registerHook(java.lang.reflect.Member member, XC_MethodHook callback) {
        if (HOOKS.contains(member)) return;
        XposedBridge.hookMethod(member, callback);
        HOOKS.add(member);
    }

    private static void observeKernel(Method storageGetter) throws Exception {
        Class<?> kernel = storageGetter.getDeclaringClass();
        List<Method> accounts = HostTargetResolver.findMethods(new HostTargetResolver.MethodSpec()
                .declaredClass(kernel.getName()).usingStrings("mCoreAccount not initialized!")
                .parameterCount(0).modifiers(Modifier.PUBLIC | Modifier.STATIC));
        Method accountGetter = HostTargetResolver.unique(accounts, "Kernel account storage getter");
        Class<?> account = accountGetter.getReturnType();
        if (account.isPrimitive() || account.isInterface() || account == Object.class
                || !Modifier.isStatic(accountGetter.getModifiers()))
            throw new IllegalStateException("Kernel account storage metadata unavailable");
        Field coreField = directField(kernel, storageClass);
        Field accountField = directField(kernel, account);
        Field stateField = null;
        for (Field field : account.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !Modifier.isVolatile(field.getModifiers())
                    || !field.getType().isEnum()) continue;
            if (stateField != null) throw new IllegalStateException("Ambiguous account readiness field");
            field.setAccessible(true); stateField = field;
        }
        if (coreField == null || accountField == null || stateField == null)
            throw new IllegalStateException("Kernel storage fields unavailable");
        Method instanceGetter = null;
        for (Method method : kernel.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
                    || method.getParameterTypes().length != 0 || method.getReturnType() != kernel) continue;
            if (instanceGetter != null) throw new IllegalStateException("Ambiguous initialized Kernel getter");
            instanceGetter = method;
        }
        if (instanceGetter == null) throw new IllegalStateException("Initialized Kernel getter unavailable");
        // Only a successful host invocation supplies the existing Kernel instance.
        // Reflecting a Class/Method alone never permits a static singleton read or initialization.
        kernelClass = kernel; kernelCoreField = coreField; kernelAccountField = accountField; accountStateField = stateField;
        registerHook(instanceGetter, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                callbackEvidence(0, "stage=kernel-instance-entry");
                boolean available = !param.hasThrowable() && kernel.isInstance(param.getResult());
                callbackEvidence(1, "stage=kernel-instance-result threw=" + param.hasThrowable()
                        + " availableKernel=" + available);
                if (!available) return;
                captureKernel(param.getResult());
            }
        });
        log("stage=kernel-observe registered");
        try { observeAccountReady(account); }
        catch (Throwable error) { log("stage=account-observe failed " + error.getClass().getSimpleName()); }
        replayProfileCapture();
    }

    private static void observeAccountReady(Class<?> account) throws Exception {
        Method ready = HostTargetResolver.unique(HostTargetResolver.findMethods(new HostTargetResolver.MethodSpec()
                .usingStrings("changeToAccountHasReady").name("run").returnType("void")
                .parameterCount(0).modifiers(Modifier.PUBLIC)), "Account readiness publication");
        if (Modifier.isStatic(ready.getModifiers()) || ready.getReturnType() != void.class
                || ready.getParameterTypes().length != 0 || !Runnable.class.isAssignableFrom(ready.getDeclaringClass())
                || directField(ready.getDeclaringClass(), account) == null)
            throw new IllegalStateException("Account readiness publication metadata differs");
        final Field taskAccount = directField(ready.getDeclaringClass(), account);
        registerHook(ready, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                callbackEvidence(4, "stage=account-ready-entry");
                Object kernel = observedKernel.get();
                callbackEvidence(5, "stage=account-ready-result threw=" + param.hasThrowable()
                        + " availableKernel=" + (kernel != null) + " availableTask=" + (param.thisObject != null));
                if (param.hasThrowable() || param.thisObject == null || kernel == null) return;
                try {
                    Object current = kernelAccountField.get(kernel);
                    if (taskAccount != null && current != null && taskAccount.get(param.thisObject) == current) captureKernel(kernel);
                } catch (Throwable error) { profileGate("stage=account-capture failed " + error.getClass().getSimpleName()); }
            }
        });
        log("stage=account-observe registered");
    }

    private static Field directField(Class<?> owner, Class<?> valueType) {
        Field found = null;
        for (Field field : owner.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != valueType) continue;
            if (found != null) throw new IllegalStateException("Ambiguous direct storage field");
            field.setAccessible(true); found = field;
        }
        return found;
    }

    static boolean profileReadInProgress() {
        return Boolean.TRUE.equals(CAPTURING.get()) || HostProfileBridge.readInProgress();
    }

    private static void captureKernel(Object kernel) {
        if (profileReadInProgress() || kernelClass == null || !kernelClass.isInstance(kernel)) return;
        try {
            Object account = kernelAccountField.get(kernel);
            Object phase = account == null ? null : accountStateField.get(account);
            Object core = kernelCoreField.get(kernel);
            boolean ready = phase instanceof Enum<?> && "AccountHasReady".equals(((Enum<?>) phase).name());
            boolean changed;
            synchronized (STATE) {
                changed = observedKernel.get() != kernel || observedCore.get() != core || currentAccount.get() != account;
                observedKernel = new WeakReference<>(kernel);
            }
            if (!ready || core == null || !storageClass.isInstance(core)) {
                invalidateKernelCapture();
                profileGate("stage=kernel-capture waiting accountReady=false"); return;
            }
            if (changed || !currentCapture()) { invalidateKernelCapture(); changed = true; }
            synchronized (STATE) {
                // Fixed instance fields only on the callback thread. No metadata discovery/host call.
                if (observedKernel.get() != kernel) return;
                currentAccount = new WeakReference<>(account); observedCore = new WeakReference<>(core);
                // Closed worker capture is latched until a genuine new host callback/demand.
                kernelRevoked = false;
            }
            if (changed || database.get() == null || currentConfig.get() == null || holdersChanged(core, currentConfig.get())) queueCapture(false);
        } catch (Throwable error) { invalidateKernelCapture(); profileGate("stage=kernel-capture failed " + error.getClass().getSimpleName()); }
    }

    static long captureVersion() { return captureVersion; }

    private static void invalidateKernelCapture() {
        boolean changed;
        synchronized (STATE) {
            changed = database.get() != null || currentConfig.get() != null || currentAccount.get() != null || observedCore.get() != null;
            database = new WeakReference<>(null); rawQuery = null;
            currentConfig = new WeakReference<>(null); currentAccount = new WeakReference<>(null);
            observedCore = new WeakReference<>(null); coreProbe = null; configProbe = null; kernelRevoked = true;
            epoch++;
            if (changed) { captureVersion++; demandAttempts = 0; lastDemand = 0L; }
        }
        if (changed) {
            status = installed ? "HOOKED" : status;
            HostProfileBridge.invalidate();
            log("stage=capture invalidated available=false");
        }
    }

    private static boolean readyTuple(Object kernel, Object core, Object account) throws IllegalAccessException {
        if (kernel == null || kernelCoreField == null || kernelAccountField == null || accountStateField == null) return false;
        Object phase = account == null ? null : accountStateField.get(account);
        return observedKernel.get() == kernel && kernelCoreField.get(kernel) == core
                && kernelAccountField.get(kernel) == account && phase instanceof Enum<?>
                && "AccountHasReady".equals(((Enum<?>) phase).name());
    }

    /** O(known pinned fields); no metadata discovery, scanning or host methods on MAIN. */
    static boolean currentCapture() {
        Object kernel = observedKernel.get(), core = observedCore.get(), account = currentAccount.get();
        if (kernel == null || kernelClass == null) return !kernelRevoked;
        try {
            if (!readyTuple(kernel, core, account) || core == null) { invalidateKernelCapture(); return false; }
            Object config = currentConfig.get(); Field holder = configHolder;
            if (config != null && holder != null && holder.get(core) != config) { invalidateKernelCapture(); return false; }
            Object db = database.get();
            if (db != null && (holdersChanged(core, config)
                    || !containsDatabase(core, coreProbe, db) && !containsDatabase(config, configProbe, db))) {
                invalidateKernelCapture(); return false;
            }
            return !kernelRevoked;
        } catch (Throwable error) { invalidateKernelCapture(); return false; }
    }

    private static boolean holdersChanged(Object core, Object config) throws IllegalAccessException {
        ProbeSnapshot a = coreProbe, b = configProbe;
        return a == null || !a.matches(core) || config != null && (b == null || !b.matches(config));
    }

    private static boolean containsDatabase(Object owner, ProbeSnapshot evidence, Object db) throws IllegalAccessException {
        if (owner == null || evidence == null || owner.getClass() != evidence.type) return false;
        // Both declared and learned actual holders share the same bounded identity evidence.
        for (Field field : evidence.fields) if (field.get(owner) == db) return true;
        return false;
    }

    static boolean readCaptureCurrent() {
        if (Looper.myLooper() == Looper.getMainLooper() || !currentCapture()) return false;
        return database.get() == null || isReady();
    }

    static boolean configCurrent(Object candidate) {
        return candidate != null && currentConfig.get() == candidate && currentCapture();
    }

    /** Single pending bit and one worker; weak observations, never a queue of host objects. */
    private static void queueCapture(boolean recover) {
        synchronized (STATE) {
            capturePending = true; recoverPending |= recover; maxPendingCaptures = 1;
            if (captureQueued) return;
            captureQueued = true;
        }
        submitCapture();
    }

    private static void submitCapture() {
        try { HostTargetResolver.background(HostDatabaseBridge::drainCapture); }
        catch (Throwable error) {
            synchronized (STATE) { captureQueued = false; capturePending = false; recoverPending = false; }
            profileGate("stage=demand-submit failed " + error.getClass().getSimpleName());
        }
    }

    private static void drainCapture() {
        try {
            for (int pass = 0; pass < 2; pass++) {
                boolean recover;
                synchronized (STATE) {
                    if (!capturePending) break;
                    capturePending = false; recover = recoverPending; recoverPending = false;
                }
                CAPTURING.set(true);
                try { scanCapture(recover); }
                catch (Throwable error) { profileGate("stage=capture failed " + error.getClass().getSimpleName()); }
                finally { CAPTURING.remove(); }
            }
        } finally {
            boolean again;
            synchronized (STATE) { again = capturePending; if (!again) captureQueued = false; }
            if (again) submitCapture();
        }
    }

    static void requestProfileCapture() {
        if (profileReadInProgress()) return;
        Object kernel = observedKernel.get();
        if (kernel == null || kernelClass == null) { profileGate("stage=demand waiting existingKernel=false"); return; }
        captureKernel(kernel);
        synchronized (STATE) {
            long now = System.nanoTime();
            if (lastDemand != 0L && now - lastDemand < 2_000_000_000L) return;
            lastDemand = now;
        }
        if (currentConfig.get() == null || database.get() == null) queueCapture(true);
    }

    private static void scanCapture(boolean recover) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Capture requires worker");
        Object kernel = observedKernel.get(), core = observedCore.get(), account = currentAccount.get();
        if (kernel != null && !readyTuple(kernel, core, account) || core == null) return;
        long ticket = epoch, version = captureVersion;
        captureEvents++;
        Class<?> configType = HostProfileBridge.storageType();
        if (configType != configHolderType) {
            Field holder = configType == null || storageClass == null ? null : directField(storageClass, configType);
            configHolder = holder; configHolderType = configType;
        }
        Object config = configHolder == null ? null : configHolder.get(core);
        if (config == null && recover) config = recoverConfigGetter(core);
        HolderMetadata coreInfo = holderMetadata(core.getClass());
        HolderMetadata configInfo = config == null ? null : holderMetadata(config.getClass());
        ProbeSnapshot nextCore = new ProbeSnapshot(core, coreInfo);
        ProbeSnapshot nextConfig = config == null ? null : new ProbeSnapshot(config, configInfo);
        Object db = directDatabase(core, coreInfo);
        if (db == null && config != null) db = directDatabase(config, configInfo);
        if (storageClass == null && db == null) db = locateDatabase(core, 0);
        DbMetadata metadata = db == null ? null : databaseMetadata(db.getClass());
        boolean open = db != null && metadata.query != null && openDatabase(db);
        if (db != null && !open) { if (epoch == ticket && captureVersion == version) invalidateKernelCapture(); return; }
        if (!nextCore.matches(core) || nextConfig != null && !nextConfig.matches(config)) return;
        // Every host instance invocation and all reflection discovery precede the short publication lock.
        if (epoch != ticket || captureVersion != version || kernel != null && !readyTuple(kernel, core, account)) return;
        if (configHolder != null && configHolder.get(core) != config) return;
        if (db != null && storageClass != null && !containsDatabase(core, nextCore, db) && !containsDatabase(config, nextConfig, db)) return;
        boolean dbChanged, configChanged;
        synchronized (STATE) {
            if (epoch != ticket || captureVersion != version || observedCore.get() != core || currentAccount.get() != account) return;
            // Recheck pinned account state and holders, no host methods or discovery under lock.
            if (kernel != null && !readyTuple(kernel, core, account)) return;
            if (configHolder != null && configHolder.get(core) != config) return;
            if (db != null && storageClass != null && !containsDatabase(core, nextCore, db) && !containsDatabase(config, nextConfig, db)) return;
            dbChanged = database.get() != (open ? db : null);
            configChanged = currentConfig.get() != config;
            if (dbChanged || configChanged) { captureVersion++; epoch++; }
            coreMetadata = coreInfo; configMetadata = configInfo;
            coreProbe = nextCore; configProbe = nextConfig;
            currentConfig = new WeakReference<>(config);
            database = new WeakReference<>(open ? db : null); rawQuery = open ? metadata.query : null;
            kernelRevoked = false;
            if (open) status = "READY:" + db.getClass().getName();
        }
        if (config != null) HostProfileBridge.publishObserved(config);
        if (open && dbChanged) { log("stage=capture ready " + db.getClass().getName()); notifyReady(); }
    }

    private static Object recoverConfigGetter(Object core) throws Exception {
        Class<?> configType = HostProfileBridge.storageType();
        if (configType == null || !accountReady(core) || configHolder == null) return null;
        synchronized (STATE) { if (demandAttempts >= 3) return null; demandAttempts++; }
        Method getter = null;
        for (Method method : storageClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers()) || method.isSynthetic()
                    || method.getParameterTypes().length != 0 || method.getReturnType() != configType) continue;
            if (getter != null) throw new IllegalStateException("Ambiguous ConfigStorage getter");
            getter = method;
        }
        if (getter == null || !accountReady(core) || observedCore.get() != core) return null;
        getter.setAccessible(true);
        Object config = getter.invoke(core);
        if (!accountReady(core) || observedCore.get() != core || !configType.isInstance(config)) return null;
        Object direct = configHolder.get(core);
        return direct == config ? config : null;
    }

    static void replayProfileCapture() {
        if (profileReadInProgress()) return;
        Object kernel = observedKernel.get();
        if (kernel != null && kernelClass != null) { captureKernel(kernel); if (observedCore.get() != null) queueCapture(false); }
    }

    private static boolean accountReady(Object core) throws IllegalAccessException {
        return readyTuple(observedKernel.get(), core, currentAccount.get());
    }

    private static void profileGate(String stage) {
        if (stage.startsWith("stage=demand")) {
            stage += " weakKernel=" + (observedKernel.get() != null)
                    + " currentCore=" + (observedCore.get() != null)
                    + " configReader=" + (HostProfileBridge.storageType() != null)
                    + " typeRegistered=" + (storageClass != null && kernelClass != null);
        }
        synchronized (HostDatabaseBridge.class) {
            if (stage.equals(lastProfileGate)) return;
            lastProfileGate = stage;
        }
        log(stage);
    }

    private static void observeStorage(Class<?> owner) {
        XC_MethodHook observe = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (profileReadInProgress() || param.hasThrowable() || !owner.isInstance(param.thisObject)) return;
                try { HostProfileBridge.observe(param.getResult()); }
                catch (Throwable error) { log("stage=config-capture failed " + error.getClass().getSimpleName()); }
                capture(param.thisObject);
            }
        };
        for (java.lang.reflect.Constructor<?> constructor : owner.getDeclaredConstructors()) {
            try { registerHook(constructor, observe); }
            catch (Throwable error) { log("stage=storage-constructor unavailable " + error.getClass().getSimpleName()); }
        }
        int count = 0;
        for (Method method : owner.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())
                    || method.isSynthetic() || method.getParameterTypes().length != 0) continue;
            Class<?> result = method.getReturnType();
            if (result.isPrimitive() || result == void.class || result.getName().startsWith("java.")
                    || result.getName().startsWith("android.")) continue;
            try { registerHook(method, observe); count++; }
            catch (Throwable error) { log("stage=storage-observe unavailable " + error.getClass().getSimpleName()); }
        }
        log("stage=storage-observe registered=" + count);
    }

    public static void subscribeReady(Runnable listener) {
        if (listener == null) return;
        boolean ready;
        synchronized (HostDatabaseBridge.class) {
            READY.add(new java.lang.ref.WeakReference<>(listener));
                    ready = database.get() != null && rawQuery != null;
        }
        if (ready) try { listener.run(); } catch (Throwable ignored) { }
    }
    public static String status() { return status; }

    public static boolean isReady() {
        if (!currentCapture()) return false;
        Object db = database.get();
        if (db == null || rawQuery == null) return false;
        if (Looper.myLooper() == Looper.getMainLooper()) return true;
        long version = captureVersion;
        try {
            if (!openDatabase(db)) { invalidateKernelCapture(); return false; }
            return version == captureVersion && database.get() == db && currentCapture();
        } catch (Throwable error) { invalidateKernelCapture(); return false; }
    }

    public static Cursor rawQuery(String sql, String[] args) throws Exception {
        if (!isReadOnly(sql)) {
            throw new SecurityException("Only SELECT and read-only PRAGMA statements are allowed");
        }
        if (Looper.myLooper() == Looper.getMainLooper() || !isReady()) throw new IllegalStateException("Database requires current worker capture");
        long version = captureVersion;
        Object db = database.get();
        Method query = rawQuery;
        if (db == null || query == null) {
            throw new IllegalStateException("WeChat database is not ready");
        }
        Object[] invocation = new Object[query.getParameterTypes().length];
        invocation[0] = sql;
        if (invocation.length > 1) {
            Class<?> argType = query.getParameterTypes()[1];
            if (argType == String[].class) {
                invocation[1] = args;
            } else if (argType.isArray()) {
                invocation[1] = args == null ? null : args;
            } else {
                invocation[1] = args;
            }
        }
        for (int i = 2; i < invocation.length; i++) {
            Class<?> type = query.getParameterTypes()[i];
            invocation[i] = type == int.class ? Integer.valueOf(0)
                    : type == boolean.class ? Boolean.FALSE : null;
        }
        Cursor result = (Cursor) query.invoke(db, invocation);
        if (version != captureVersion || database.get() != db || !currentCapture()) {
            if (result != null) result.close();
            throw new IllegalStateException("Database capture changed during query");
        }
        return result;
    }

    public static List<Map<String, Object>> query(String sql, String[] args) throws Exception {
        Cursor cursor = rawQuery(sql, args);
        try {
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            String[] names = cursor.getColumnNames();
            while (cursor.moveToNext()) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                for (int i = 0; i < names.length; i++) {
                    switch (cursor.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL:
                            row.put(names[i], null);
                            break;
                        case Cursor.FIELD_TYPE_INTEGER:
                            row.put(names[i], Long.valueOf(cursor.getLong(i)));
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            row.put(names[i], Double.valueOf(cursor.getDouble(i)));
                            break;
                        case Cursor.FIELD_TYPE_BLOB:
                            row.put(names[i], cursor.getBlob(i));
                            break;
                        default:
                            row.put(names[i], cursor.getString(i));
                            break;
                    }
                }
                rows.add(row);
            }
            return rows;
        } finally {
            cursor.close();
        }
    }

    public static List<String> queryStrings(String sql, String[] args) throws Exception {
        Cursor cursor = rawQuery(sql, args);
        try {
            List<String> result = new ArrayList<String>();
            while (cursor.moveToNext()) {
                String value = cursor.getString(0);
                if (value != null && value.length() > 0) {
                    result.add(value);
                }
            }
            return result;
        } finally {
            cursor.close();
        }
    }

    static boolean isReadOnly(String sql) {
        return ReadOnlySql.accepts(sql);
    }

    private static void capture(Object storage) {
        if (profileReadInProgress() || storage == null || storageClass != null && !storageClass.isInstance(storage)) return;
        Object kernel = observedKernel.get();
        if (kernel != null && kernelClass != null) { captureKernel(kernel); return; }
        if (storageClass != null) return; // A getter return alone does not prove account readiness.
        observedCore = new WeakReference<>(storage);
        queueCapture(false);
    }

    static void captureConfigStorage(Object config) {
        if (profileReadInProgress() || !HostProfileBridge.accepts(config)) return;
        Object kernel = observedKernel.get();
        if (kernel == null || kernelClass == null) return;
        captureKernel(kernel);
        // A new actual Config instance or late DB still requests a merged worker probe.
        if (currentConfig.get() != config || database.get() == null) queueCapture(false);
    }

    private static HolderMetadata holderMetadata(Class<?> owner) {
        synchronized (METADATA) {
            HolderMetadata cached = HOLDERS.get(owner);
            if (cached != null) return cached;
        }
        List<Field> fields = new ArrayList<>(), probes = new ArrayList<>();
        // Preserve the resolved-storage capture boundary: erased Object holders are not
        // account evidence. Legacy storageClass==null still has its separate locate path.
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Class<?> declared = field.getType();
                if (Modifier.isStatic(field.getModifiers()) || declared.isPrimitive() || declared == Object.class
                        || declared.getName().startsWith("java.")
                        || declared.getName().startsWith("android.")) continue;
                field.setAccessible(true); fields.add(field);
                DbMetadata metadata = databaseMetadata(declared);
                if (metadata.query != null) probes.add(field);
            }
        }
        // If a host layout is too wide, fail closed rather than adding an unbounded hook scan.
        if (probes.size() > 8) throw new IllegalStateException("Database holder probe bound exceeded");
        HolderMetadata result = new HolderMetadata(owner, fields.toArray(new Field[0]), probes.toArray(new Field[0]));
        synchronized (METADATA) { HOLDERS.put(owner, result); }
        return result;
    }

    private static Object directDatabase(Object storage, HolderMetadata metadata) throws IllegalAccessException {
        Object found = null;
        for (Field field : metadata.fields) {
            Object candidate = field.get(storage);
            if (candidate == null || databaseMetadata(candidate.getClass()).query == null) continue;
            if (found != null && found != candidate) throw new IllegalStateException("Ambiguous CoreStorage database");
            found = candidate;
        }
        return found;
    }

    private static DbMetadata databaseMetadata(Class<?> type) {
        synchronized (METADATA) {
            DbMetadata cached = DB_METADATA.get(type);
            if (cached != null) { if (cached.query == null) negativeMetadataHits++; return cached; }
            metadataMisses++;
        }
        Method query = null, open = null;
        try {
            Method path = type.getMethod("getPath");
            open = type.getMethod("isOpen");
            if (path.getReturnType() == String.class && open.getReturnType() == boolean.class) query = findRawQuery(type);
        } catch (ReflectiveOperationException ignored) { }
        if (storageClass == null && query == null) query = findRawQuery(type);
        if (query != null) query.setAccessible(true);
        if (open != null) open.setAccessible(true);
        DbMetadata result = new DbMetadata(query, open);
        synchronized (METADATA) {
            DbMetadata previous = DB_METADATA.get(type);
            if (previous != null) return previous;
            DB_METADATA.put(type, result);
        }
        return result;
    }

    private static boolean openDatabase(Object db) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("isOpen requires worker");
        DbMetadata metadata = databaseMetadata(db.getClass());
        return metadata.open == null ? storageClass == null : Boolean.TRUE.equals(metadata.open.invoke(db));
    }

    private static Object locateDatabase(Object object, int depth) {
        if (object == null || depth > 3) {
            return null;
        }
        if (findRawQuery(object.getClass()) != null) {
            return object;
        }
        Class<?> type = object.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || field.getType().isPrimitive()) {
                    continue;
                }
                String name = field.getType().getName();
                if (name.startsWith("java.") || name.startsWith("android.")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object child = field.get(object);
                    Object result = locateDatabase(child, depth + 1);
                    if (result != null) {
                        return result;
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Method findRawQuery(Class<?> type) {
        if (type == null) return null;
        for (Class<?> arguments : new Class<?>[]{String[].class, Object[].class}) {
            Method method = queryMethod(type, "rawQuery", String.class, arguments);
            if (method != null) return method;
        }
        // The verified 8.0.76 wrapper's g(..., 0) is its ordinary read-only query path.
        Method plain = queryMethod(type, "g", String.class, String[].class, int.class);
        if (plain != null) return plain;
        plain = queryMethod(type, "B", String.class, String[].class);
        if (plain != null) return plain;
        Method found = null;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (Modifier.isStatic(method.getModifiers()) || !Cursor.class.isAssignableFrom(method.getReturnType())
                        || parameters.length < 2 || parameters.length > 3 || parameters[0] != String.class
                        || parameters[1] != String[].class && parameters[1] != Object[].class
                        || parameters.length == 3 && parameters[2] != int.class) continue;
                if (found != null && (!found.getName().equals(method.getName())
                        || !java.util.Arrays.equals(found.getParameterTypes(), parameters)
                        || found.getReturnType() != method.getReturnType())) return null;
                if (found == null) found = method;
            }
        }
        return found;
    }

    private static Method queryMethod(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                if (!Modifier.isStatic(method.getModifiers()) && Cursor.class.isAssignableFrom(method.getReturnType()))
                    return method;
            } catch (NoSuchMethodException ignored) { }
        }
        try {
            Method method = type.getMethod(name, parameters);
            return !Modifier.isStatic(method.getModifiers()) && Cursor.class.isAssignableFrom(method.getReturnType()) ? method : null;
        } catch (NoSuchMethodException ignored) { return null; }
    }

    private static void notifyReady() {
        List<Runnable> listeners = new ArrayList<>();
        synchronized (HostDatabaseBridge.class) {
            for (int i = READY.size() - 1; i >= 0; i--) {
                Runnable listener = READY.get(i).get();
                if (listener == null) READY.remove(i); else listeners.add(listener);
            }
        }
        for (Runnable listener : listeners) try { listener.run(); } catch (Throwable ignored) { }
    }

    private static void log(String message) {
        HostStageLog.write("database", message);
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }
}
