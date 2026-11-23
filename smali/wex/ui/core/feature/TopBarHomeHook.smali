.class public final Lwex/ui/core/feature/TopBarHomeHook;
.super Ldev/mindlink/xposed/XC_MethodHook;
.source "TopBarHomeHook.java"


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ldev/mindlink/xposed/XC_MethodHook;-><init>()V

    return-void
.end method


# virtual methods
.method public beforeHookedMethod(Ldev/mindlink/xposed/XC_MethodHook$MethodHookParam;)V
    .locals 0

    invoke-static {p1}, Lwex/ui/core/feature/TopBarFeature;->onTitleText(Ldev/mindlink/xposed/XC_MethodHook$MethodHookParam;)V

    return-void
.end method

.method public afterHookedMethod(Ldev/mindlink/xposed/XC_MethodHook$MethodHookParam;)V
    .locals 2

    :try_start_0
    iget-object v0, p1, Ldev/mindlink/xposed/XC_MethodHook$MethodHookParam;->thisObject:Ljava/lang/Object;

    instance-of v1, v0, Landroid/app/Activity;

    if-eqz v1, :done

    check-cast v0, Landroid/app/Activity;

    # Disabled must still reach apply so already-inserted views can be removed.
    sget-object v1, Lwex/ui/core/feature/TopBarFeature;->INSTANCE:Lwex/ui/core/feature/TopBarFeature;

    invoke-static {v1, v0}, Lwex/ui/core/feature/TopBarFeature;->access$applyTopBar(Lwex/ui/core/feature/TopBarFeature;Landroid/app/Activity;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    :done
    return-void

    :catchall_0
    move-exception v0

    const-string v1, "WEX: TopBar lifecycle apply failed"

    invoke-static {v1}, Ldev/mindlink/xposed/XposedBridge;->log(Ljava/lang/String;)V

    return-void
.end method
