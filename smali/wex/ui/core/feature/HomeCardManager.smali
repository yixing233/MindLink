.class public final Lwex/ui/core/feature/HomeCardManager;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation build Landroidx/compose/runtime/internal/StabilityInferred;
    parameters = 0x0
.end annotation

.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u000b\n\u0002\u0008\u0003\n\u0002\u0010\u000e\n\u0002\u0008\u0003\n\u0002\u0018\u0002\n\u0002\u0008\u0008\u0008\u00c7\u0002\u0018\u00002\u00020\u0001B\t\u0008\u0002\u00a2\u0006\u0004\u0008\u0002\u0010\u0003J\u0017\u0010\u0007\u001a\u00020\u00062\u0006\u0010\u0005\u001a\u00020\u0004H\u0002\u00a2\u0006\u0004\u0008\u0007\u0010\u0008J1\u0010\u0011\u001a\u00020\u00062\u0006\u0010\n\u001a\u00020\t2\u0008\u0010\u000c\u001a\u0004\u0018\u00010\u000b2\u0006\u0010\u000e\u001a\u00020\r2\u0006\u0010\u0010\u001a\u00020\u000fH\u0002\u00a2\u0006\u0004\u0008\u0011\u0010\u0012J\u0017\u0010\u0015\u001a\u00020\u00062\u0006\u0010\u0014\u001a\u00020\u0013H\u0002\u00a2\u0006\u0004\u0008\u0015\u0010\u0016J\u001f\u0010\u001a\u001a\u00020\u00062\u0006\u0010\u0018\u001a\u00020\u00172\u0008\u0010\u0019\u001a\u0004\u0018\u00010\u0013\u00a2\u0006\u0004\u0008\u001a\u0010\u001bR\u0014\u0010\u001c\u001a\u00020\u00138\u0002X\u0082T\u00a2\u0006\u0006\n\u0004\u0008\u001c\u0010\u001dR\u0018\u0010\u001e\u001a\u0004\u0018\u00010\u00138\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008\u001e\u0010\u001d\u00a8\u0006\u001f"
    }
    d2 = {
        "Lwex/ui/core/feature/HomeCardManager;",
        "",
        "<init>",
        "()V",
        "Landroid/app/Activity;",
        "act",
        "Lj3/x;",
        "insertCards",
        "(Landroid/app/Activity;)V",
        "Landroid/widget/LinearLayout;",
        "root",
        "Landroid/view/View;",
        "card",
        "",
        "d",
        "",
        "isFirst",
        "addChild",
        "(Landroid/widget/LinearLayout;Landroid/view/View;FZ)V",
        "",
        "msg",
        "log",
        "(Ljava/lang/String;)V",
        "Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;",
        "lpparam",
        "modPath",
        "init",
        "(Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;Ljava/lang/String;)V",
        "TAG_INSERTED",
        "Ljava/lang/String;",
        "modulePath",
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

.field public static final INSTANCE:Lwex/ui/core/feature/HomeCardManager;

.field private static final TAG_INSERTED:Ljava/lang/String; = "w\u0435\u0445_home_cards"

.field private static modulePath:Ljava/lang/String;

.field private static initialized:Z


# direct methods
.method static constructor <clinit>()V
    .locals 1

    new-instance v0, Lwex/ui/core/feature/HomeCardManager;

    invoke-direct {v0}, Lwex/ui/core/feature/HomeCardManager;-><init>()V

    sput-object v0, Lwex/ui/core/feature/HomeCardManager;->INSTANCE:Lwex/ui/core/feature/HomeCardManager;

    const/16 v0, 0x8

    sput v0, Lwex/ui/core/feature/HomeCardManager;->$stable:I

    return-void
.end method

.method private constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static synthetic a(Landroid/view/ViewGroup;Landroid/app/Activity;)V
    .locals 0

    invoke-static {p0, p1}, Lwex/ui/core/feature/HomeCardManager;->insertCards$lambda$0(Landroid/view/ViewGroup;Landroid/app/Activity;)V

    return-void
.end method

.method public static final synthetic access$insertCards(Lwex/ui/core/feature/HomeCardManager;Landroid/app/Activity;)V
    .locals 0

    invoke-direct {p0, p1}, Lwex/ui/core/feature/HomeCardManager;->insertCards(Landroid/app/Activity;)V

    return-void
.end method

.method private final addChild(Landroid/widget/LinearLayout;Landroid/view/View;FZ)V
    .locals 2

    if-nez p2, :cond_0

    return-void

    :cond_0
    invoke-virtual {p2}, Landroid/view/View;->getParent()Landroid/view/ViewParent;

    move-result-object p0

    instance-of v0, p0, Landroid/view/ViewGroup;

    if-eqz v0, :cond_1

    check-cast p0, Landroid/view/ViewGroup;

    goto :goto_0

    :cond_1
    const/4 p0, 0x0

    :goto_0
    if-eqz p0, :cond_2

    invoke-virtual {p0, p2}, Landroid/view/ViewGroup;->removeView(Landroid/view/View;)V

    :cond_2
    new-instance p0, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v0, -0x1

    const/4 v1, -0x2

    invoke-direct {p0, v0, v1}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    if-nez p4, :cond_3

    const/16 p4, 0xa

    int-to-float p4, p4

    mul-float/2addr p4, p3

    float-to-int p3, p4

    iput p3, p0, Landroid/widget/LinearLayout$LayoutParams;->topMargin:I

    :cond_3
    invoke-virtual {p1, p2, p0}, Landroid/view/ViewGroup;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V

    return-void
.end method

.method private final insertCards(Landroid/app/Activity;)V
    .locals 2

    invoke-virtual {p1}, Landroid/app/Activity;->getWindow()Landroid/view/Window;

    move-result-object p0

    const/4 v0, 0x0

    if-eqz p0, :cond_0

    invoke-virtual {p0}, Landroid/view/Window;->getDecorView()Landroid/view/View;

    move-result-object p0

    goto :goto_0

    :cond_0
    move-object p0, v0

    :goto_0
    instance-of v1, p0, Landroid/view/ViewGroup;

    if-eqz v1, :cond_1

    move-object v0, p0

    check-cast v0, Landroid/view/ViewGroup;

    :cond_1
    if-nez v0, :cond_2

    return-void

    :cond_2
    new-instance p0, Lx4/g0;

    const/4 v1, 0x0

    invoke-direct {p0, v1}, Lx4/g0;-><init>(I)V

    iput-object v0, p0, Lx4/g0;->d:Landroid/view/ViewGroup;

    iput-object p1, p0, Lx4/g0;->e:Landroid/app/Activity;

    invoke-static {}, Ljava/lang/invoke/VarHandle;->storeStoreFence()V

    invoke-virtual {v0, p0}, Landroid/view/View;->post(Ljava/lang/Runnable;)Z

    return-void
.end method

.method private static final insertCards$lambda$0(Landroid/view/ViewGroup;Landroid/app/Activity;)V
    .locals 0

    # 旧首页卡片/侧栏桥已下线；新抽屉由 HomeSidePanelFeature 独立维护。
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


# virtual methods
.method public final init(Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;Ljava/lang/String;)V
    .locals 2

    invoke-static {p2}, Lwex/ui/core/feature/HomeSidebar;->configure(Ljava/lang/String;)V

    sget-boolean v0, Lwex/ui/core/feature/HomeCardManager;->initialized:Z

    if-eqz v0, :init_cards

    return-void

    :init_cards
    invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    sput-object p2, Lwex/ui/core/feature/HomeCardManager;->modulePath:Ljava/lang/String;

    sget-object p1, Lwex/ui/core/feature/ImageCardFeature;->INSTANCE:Lwex/ui/core/feature/ImageCardFeature;

    invoke-virtual {p1, p2}, Lwex/ui/core/feature/ImageCardFeature;->setModulePath(Ljava/lang/String;)V

    sget-object p1, Lwex/ui/core/feature/CalendarCardFeature;->INSTANCE:Lwex/ui/core/feature/CalendarCardFeature;

    invoke-virtual {p1, p2}, Lwex/ui/core/feature/CalendarCardFeature;->setModulePath(Ljava/lang/String;)V

    sget-object p1, Lx4/u0;->a:Lx4/u0;

    sput-object p2, Lx4/u0;->e:Ljava/lang/String;

    :try_start_0
    const-class p1, Landroid/app/Activity;

    const-string p2, "onResume"

    new-instance v0, Lx4/f0;

    const/4 v1, 0x7

    invoke-direct {v0, v1}, Lx4/f0;-><init>(I)V

    filled-new-array {v0}, [Ljava/lang/Object;

    move-result-object v0

    invoke-static {p1, p2, v0}, Ldev/mindlink/xposed/XposedHelpers;->findAndHookMethod(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Ldev/mindlink/xposed/XC_MethodHook$Unhook;

    const/4 v0, 0x1

    sput-boolean v0, Lwex/ui/core/feature/HomeCardManager;->initialized:Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    return-void

    :catchall_0
    move-exception p1

    invoke-virtual {p1}, Ljava/lang/Throwable;->getMessage()Ljava/lang/String;

    move-result-object p1

    const-string p2, "\u9996\u9875\u5361\u7247\u7ba1\u7406\u5668 Hook \u5931\u8d25: "

    invoke-static {p2, p1}, Landroidx/activity/b;->m(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    invoke-direct {p0, p1}, Lwex/ui/core/feature/HomeCardManager;->log(Ljava/lang/String;)V

    return-void
.end method
