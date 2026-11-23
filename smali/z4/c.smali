.class public final Lz4/c;
.super Ldev/mindlink/xposed/XC_MethodHook;
.source "SourceFile"


# instance fields
.field public final synthetic a:Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;

.field public final synthetic b:Lwex/ui/core/xposed/HookEntry;


# direct methods
.method public constructor <init>(Lwex/ui/core/xposed/HookEntry;Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;)V
    .locals 0

    iput-object p2, p0, Lz4/c;->a:Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;

    iput-object p1, p0, Lz4/c;->b:Lwex/ui/core/xposed/HookEntry;

    invoke-direct {p0}, Ldev/mindlink/xposed/XC_MethodHook;-><init>()V

    return-void
.end method


# virtual methods
.method public final afterHookedMethod(Ldev/mindlink/xposed/XC_MethodHook$MethodHookParam;)V
    .locals 4

    invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    const/4 v2, 0x0

    invoke-virtual {p1}, Ldev/mindlink/xposed/XC_MethodHook$MethodHookParam;->hasThrowable()Z

    move-result v3

    if-nez v3, :attached_context_done

    iget-object v3, p1, Ldev/mindlink/xposed/XC_MethodHook$MethodHookParam;->thisObject:Ljava/lang/Object;

    instance-of v0, v3, Landroid/content/Context;

    if-eqz v0, :attached_context_done

    check-cast v3, Landroid/content/Context;

    move-object v2, v3

    :attached_context_done
    iget-object p1, p0, Lz4/c;->a:Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;

    iget-object v0, p1, Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;->processName:Ljava/lang/String;

    if-eqz v0, :cond_0

    const-string v1, "com.tencent.mm"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_0

    return-void

    :cond_0
    iget-object p0, p0, Lz4/c;->b:Lwex/ui/core/xposed/HookEntry;

    invoke-static {p0}, Lwex/ui/core/xposed/HookEntry;->access$verifyModule(Lwex/ui/core/xposed/HookEntry;)Z

    move-result v0

    if-eqz v0, :cond_1

    if-eqz v2, :legacy_init

    :bootstrap_try_start
    invoke-static {p1, v2}, Lwex/ui/core/feature/FeatureBootstrap;->init(Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;Landroid/content/Context;)V
    :bootstrap_try_end
    .catch Ljava/lang/Throwable; {:bootstrap_try_start .. :bootstrap_try_end} :bootstrap_failed

    goto :legacy_init

    :bootstrap_failed
    move-exception v3

    :legacy_init
    invoke-static {p0, p1}, Lwex/ui/core/xposed/HookEntry;->access$doInit(Lwex/ui/core/xposed/HookEntry;Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;)V

    return-void

    :cond_1
    sget-object p0, Lwex/ui/core/xposed/HookEntry;->Companion:Lz4/b;

    invoke-virtual {p0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    const-string p0, "\u7b7e\u540d\u6821\u9a8c\u5931\u8d25\uff0c\u529f\u80fd\u5df2\u7981\u7528"

    invoke-static {p0}, Lz4/b;->a(Ljava/lang/String;)V

    return-void
.end method
