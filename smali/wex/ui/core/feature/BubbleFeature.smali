.class public final Lwex/ui/core/feature/BubbleFeature;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation build Landroidx/compose/runtime/internal/StabilityInferred;
    parameters = 0x0
.end annotation

.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0008\u0003\n\u0002\u0010\u0008\n\u0002\u0008\u0002\n\u0002\u0010\u000e\n\u0002\u0008\u0006\n\u0002\u0010#\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\"\n\u0002\u0008\u0002\n\u0002\u0010\u000b\n\u0002\u0008\u0005\u0008\u00c7\u0002\u0018\u00002\u00020\u0001B\t\u0008\u0002\u00a2\u0006\u0004\u0008\u0002\u0010\u0003J\u0017\u0010\u0007\u001a\u00020\u00062\u0006\u0010\u0005\u001a\u00020\u0004H\u0002\u00a2\u0006\u0004\u0008\u0007\u0010\u0008J\u000f\u0010\t\u001a\u00020\u0006H\u0002\u00a2\u0006\u0004\u0008\t\u0010\u0003J\u000f\u0010\u000b\u001a\u00020\nH\u0002\u00a2\u0006\u0004\u0008\u000b\u0010\u000cJ\u0017\u0010\u000f\u001a\u00020\u00062\u0006\u0010\u000e\u001a\u00020\rH\u0002\u00a2\u0006\u0004\u0008\u000f\u0010\u0010J\u0015\u0010\u0011\u001a\u00020\u00062\u0006\u0010\u0005\u001a\u00020\u0004\u00a2\u0006\u0004\u0008\u0011\u0010\u0008R\u0014\u0010\u0012\u001a\u00020\r8\u0002X\u0082T\u00a2\u0006\u0006\n\u0004\u0008\u0012\u0010\u0013R8\u0010\u0018\u001a&\u0012\u000c\u0012\n \u0016*\u0004\u0018\u00010\u00150\u0015 \u0016*\u0012\u0012\u000c\u0012\n \u0016*\u0004\u0018\u00010\u00150\u0015\u0018\u00010\u00170\u00148\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008\u0018\u0010\u0019R\u0016\u0010\u001b\u001a\u00020\u001a8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008\u001b\u0010\u001cR\u0014\u0010\u001d\u001a\u00020\r8\u0002X\u0082T\u00a2\u0006\u0006\n\u0004\u0008\u001d\u0010\u0013R\u0014\u0010\u001e\u001a\u00020\r8\u0002X\u0082T\u00a2\u0006\u0006\n\u0004\u0008\u001e\u0010\u0013\u00a8\u0006\u001f"
    }
    d2 = {
        "Lwex/ui/core/feature/BubbleFeature;",
        "",
        "<init>",
        "()V",
        "Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;",
        "lpparam",
        "Lj3/x;",
        "hookBubble",
        "(Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;)V",
        "migrateOldSetting",
        "",
        "getBlurRadius",
        "()I",
        "",
        "msg",
        "log",
        "(Ljava/lang/String;)V",
        "init",
        "BUBBLE_CLASS",
        "Ljava/lang/String;",
        "",
        "Landroid/view/View;",
        "kotlin.jvm.PlatformType",
        "",
        "doneViews",
        "Ljava/util/Set;",
        "",
        "registered",
        "Z",
        "KEY_BUBBLE",
        "KEY_ENABLED",
        "app"
    }
    k = 0x1
    mv = {
        0x2,
        0x3,
        0x0
    }
    xi = 0x30
.end annotation


# static fields
.field public static final $stable:I

.field private static final BUBBLE_CLASS:Ljava/lang/String; = "com.tencent.mm.ui.widget.MMNeat7extView"

.field public static final INSTANCE:Lwex/ui/core/feature/BubbleFeature;

.field private static final KEY_BUBBLE:Ljava/lang/String; = "bubble_blur_radius"

.field private static final KEY_ENABLED:Ljava/lang/String; = "bubble_enabled"

.field private static final doneViews:Ljava/util/Set;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Set<",
            "Landroid/view/View;",
            ">;"
        }
    .end annotation
.end field

.field private static registered:Z


# direct methods
.method static constructor <clinit>()V
    .locals 1

    new-instance v0, Lwex/ui/core/feature/BubbleFeature;

    invoke-direct {v0}, Lwex/ui/core/feature/BubbleFeature;-><init>()V

    sput-object v0, Lwex/ui/core/feature/BubbleFeature;->INSTANCE:Lwex/ui/core/feature/BubbleFeature;

    new-instance v0, Ljava/util/WeakHashMap;

    invoke-direct {v0}, Ljava/util/WeakHashMap;-><init>()V

    invoke-static {v0}, Ljava/util/Collections;->newSetFromMap(Ljava/util/Map;)Ljava/util/Set;

    move-result-object v0

    sput-object v0, Lwex/ui/core/feature/BubbleFeature;->doneViews:Ljava/util/Set;

    const/16 v0, 0x8

    sput v0, Lwex/ui/core/feature/BubbleFeature;->$stable:I

    return-void
.end method

.method private constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static final synthetic access$getBlurRadius(Lwex/ui/core/feature/BubbleFeature;)I
    .locals 0

    invoke-direct {p0}, Lwex/ui/core/feature/BubbleFeature;->getBlurRadius()I

    move-result p0

    return p0
.end method

.method public static final synthetic access$getDoneViews$p()Ljava/util/Set;
    .locals 1

    sget-object v0, Lwex/ui/core/feature/BubbleFeature;->doneViews:Ljava/util/Set;

    return-object v0
.end method

.method public static final synthetic access$log(Lwex/ui/core/feature/BubbleFeature;Ljava/lang/String;)V
    .locals 0

    invoke-direct {p0, p1}, Lwex/ui/core/feature/BubbleFeature;->log(Ljava/lang/String;)V

    return-void
.end method

.method private final getBlurRadius()I
    .locals 2

    :try_start_0
    sget-object p0, Lwex/ui/core/WexStore;->INSTANCE:Lwex/ui/core/WexStore;

    const-string v0, "bubble_blur_radius"

    const-string v1, "4"

    invoke-virtual {p0, v0, v1}, Lwex/ui/core/WexStore;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p0

    invoke-static {p0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I

    move-result p0

    const/4 v0, 0x0

    const/4 v1, 0x6

    invoke-static {p0, v0, v1}, La/a;->s(III)I

    move-result p0
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return p0

    :catch_0
    const/4 p0, 0x4

    return p0
.end method

.method private final hookBubble(Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;)V
    .locals 2

    :try_start_0
    iget-object p1, p1, Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;->classLoader:Ljava/lang/ClassLoader;

    const-string v0, "android.view.View"

    invoke-virtual {p1, v0}, Ljava/lang/ClassLoader;->loadClass(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object p1

    const-string v0, "setBackground"

    const-class v1, Landroid/graphics/drawable/Drawable;

    filled-new-array {v1}, [Ljava/lang/Class;

    move-result-object v1

    invoke-static {p1, v0, v1}, Ldev/mindlink/xposed/XposedHelpers;->findMethodExact(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object p1

    new-instance v0, Lx4/f;

    const/16 v1, 0xa

    invoke-direct {v0, v1}, Lx4/f;-><init>(I)V

    invoke-static {p1, v0}, Ldev/mindlink/xposed/XposedBridge;->hookMethod(Ljava/lang/reflect/Member;Ldev/mindlink/xposed/XC_MethodHook;)Ldev/mindlink/xposed/XC_MethodHook$Unhook;

    const/4 v0, 0x1

    sput-boolean v0, Lwex/ui/core/feature/BubbleFeature;->registered:Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    return-void

    :catchall_0
    move-exception p1

    invoke-virtual {p1}, Ljava/lang/Throwable;->getMessage()Ljava/lang/String;

    move-result-object p1

    const-string v0, "bubble hook fail: "

    invoke-static {v0, p1}, Landroidx/activity/b;->m(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    invoke-direct {p0, p1}, Lwex/ui/core/feature/BubbleFeature;->log(Ljava/lang/String;)V

    return-void
.end method

.method private final log(Ljava/lang/String;)V
    .locals 1

    new-instance p0, Ljava/lang/StringBuilder;

    const-string v0, "W\u0415\u0425: "

    invoke-direct {p0, v0}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {p0}, Ldev/mindlink/xposed/XposedBridge;->log(Ljava/lang/String;)V

    :try_start_0
    sget-object p0, Lwex/ui/core/WexStore;->INSTANCE:Lwex/ui/core/WexStore;

    invoke-virtual {p0, p1}, Lwex/ui/core/WexStore;->appendLog(Ljava/lang/String;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :catch_0
    return-void
.end method

.method private final migrateOldSetting()V
    .locals 4

    sget-object p0, Lwex/ui/core/WexStore;->INSTANCE:Lwex/ui/core/WexStore;

    const-string v0, "bubble_enabled"

    const-string v1, ""

    invoke-virtual {p0, v0, v1}, Lwex/ui/core/WexStore;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    invoke-interface {v2}, Ljava/lang/CharSequence;->length()I

    move-result v2

    if-lez v2, :cond_0

    return-void

    :cond_0
    const-string v2, "bubble_blur_radius"

    invoke-virtual {p0, v2, v1}, Lwex/ui/core/WexStore;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-interface {v1}, Ljava/lang/CharSequence;->length()I

    move-result v1

    const/4 v3, 0x1

    if-lez v1, :cond_2

    const-string v1, "4"

    invoke-virtual {p0, v2, v1}, Lwex/ui/core/WexStore;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    const-string v2, "0"

    invoke-static {v1, v2}, Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_1

    goto :goto_0

    :cond_1
    const/4 v3, 0x0

    goto :goto_0

    :cond_2
    const-string v1, "bubble_glass_enabled"

    invoke-virtual {p0, v1, v3}, Lwex/ui/core/WexStore;->getBool(Ljava/lang/String;Z)Z

    move-result v3

    :goto_0
    invoke-virtual {p0, v0, v3}, Lwex/ui/core/WexStore;->putBool(Ljava/lang/String;Z)V

    return-void
.end method


# virtual methods
.method public final declared-synchronized init(Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;)V
    .locals 1

    invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-direct {p0}, Lwex/ui/core/feature/BubbleFeature;->migrateOldSetting()V

    sget-boolean v0, Lwex/ui/core/feature/BubbleFeature;->registered:Z

    if-nez v0, :done

    invoke-direct {p0, p1}, Lwex/ui/core/feature/BubbleFeature;->hookBubble(Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;)V

    :done
    return-void
.end method
