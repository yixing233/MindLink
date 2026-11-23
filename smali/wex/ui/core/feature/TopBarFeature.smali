.class public final Lwex/ui/core/feature/TopBarFeature;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation build Landroidx/compose/runtime/internal/StabilityInferred;
    parameters = 0x0
.end annotation

.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000B\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0005\n\u0002\u0010\u000e\n\u0002\u0008\u0003\n\u0002\u0018\u0002\n\u0002\u0008\r\u0008\u00c7\u0002\u0018\u00002\u00020\u0001B\t\u0008\u0002\u00a2\u0006\u0004\u0008\u0002\u0010\u0003J\u0011\u0010\u0005\u001a\u0004\u0018\u00010\u0004H\u0002\u00a2\u0006\u0004\u0008\u0005\u0010\u0006J\u0017\u0010\n\u001a\u00020\t2\u0006\u0010\u0008\u001a\u00020\u0007H\u0002\u00a2\u0006\u0004\u0008\n\u0010\u000bJ\u0017\u0010\r\u001a\u00020\u000c2\u0006\u0010\u0008\u001a\u00020\u0007H\u0002\u00a2\u0006\u0004\u0008\r\u0010\u000eJ\u001f\u0010\u0011\u001a\u00020\t2\u0006\u0010\u0008\u001a\u00020\u00072\u0006\u0010\u0010\u001a\u00020\u000fH\u0002\u00a2\u0006\u0004\u0008\u0011\u0010\u0012J\u0011\u0010\u0013\u001a\u0004\u0018\u00010\u0004H\u0002\u00a2\u0006\u0004\u0008\u0013\u0010\u0006J\u0019\u0010\u0014\u001a\u0004\u0018\u00010\u000c2\u0006\u0010\u0008\u001a\u00020\u0007H\u0002\u00a2\u0006\u0004\u0008\u0014\u0010\u000eJ\u0017\u0010\u0017\u001a\u00020\t2\u0006\u0010\u0016\u001a\u00020\u0015H\u0002\u00a2\u0006\u0004\u0008\u0017\u0010\u0018J\u001f\u0010\u001c\u001a\u00020\t2\u0006\u0010\u001a\u001a\u00020\u00192\u0008\u0010\u001b\u001a\u0004\u0018\u00010\u0015\u00a2\u0006\u0004\u0008\u001c\u0010\u001dR\u0014\u0010\u001e\u001a\u00020\u00158\u0002X\u0082T\u00a2\u0006\u0006\n\u0004\u0008\u001e\u0010\u001fR\u0014\u0010 \u001a\u00020\u00158\u0002X\u0082T\u00a2\u0006\u0006\n\u0004\u0008 \u0010\u001fR\u0018\u0010!\u001a\u0004\u0018\u00010\u000c8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008!\u0010\"R\u0018\u0010#\u001a\u0004\u0018\u00010\u00158\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008#\u0010\u001fR\u0018\u0010$\u001a\u0004\u0018\u00010\u00048\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008$\u0010%\u00a8\u0006&"
    }
    d2 = {
        "Lwex/ui/core/feature/TopBarFeature;",
        "",
        "<init>",
        "()V",
        "Landroid/graphics/Bitmap;",
        "loadSearchIcon",
        "()Landroid/graphics/Bitmap;",
        "Landroid/app/Activity;",
        "act",
        "Lj3/x;",
        "applyTopBar",
        "(Landroid/app/Activity;)V",
        "Landroid/view/View;",
        "buildSearchBar",
        "(Landroid/app/Activity;)Landroid/view/View;",
        "Landroid/view/ViewGroup;",
        "decor",
        "insertProfile",
        "(Landroid/app/Activity;Landroid/view/ViewGroup;)V",
        "loadAvatar",
        "buildProfile",
        "",
        "msg",
        "log",
        "(Ljava/lang/String;)V",
        "Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;",
        "lpparam",
        "modPath",
        "init",
        "(Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;Ljava/lang/String;)V",
        "TAG_SEARCH",
        "Ljava/lang/String;",
        "TAG_PROFILE",
        "searchBtn",
        "Landroid/view/View;",
        "modulePath",
        "cachedIcon",
        "Landroid/graphics/Bitmap;",
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

.field public static final INSTANCE:Lwex/ui/core/feature/TopBarFeature;

.field private static final TAG_PROFILE:Ljava/lang/String; = "w\u0435\u0445_top_profile"

.field private static final TAG_SEARCH:Ljava/lang/String; = "w\u0435\u0445_top_search"

.field private static cachedIcon:Landroid/graphics/Bitmap;

.field private static modulePath:Ljava/lang/String;

.field private static searchBtn:Ljava/lang/ref/WeakReference;

.field private static searchHeader:Ljava/lang/ref/WeakReference;

.field private static searchList:Ljava/lang/ref/WeakReference;

.field private static searchVisibility:I

.field private static resumeHookRegistered:Z

.field private static titleHookRegistered:Z

.field private static updatingTitle:Z

.field private static emptyProfileReported:Z


# direct methods
.method static constructor <clinit>()V
    .locals 1

    new-instance v0, Lwex/ui/core/feature/TopBarFeature;

    invoke-direct {v0}, Lwex/ui/core/feature/TopBarFeature;-><init>()V

    sput-object v0, Lwex/ui/core/feature/TopBarFeature;->INSTANCE:Lwex/ui/core/feature/TopBarFeature;

    const/16 v0, 0x8

    sput v0, Lwex/ui/core/feature/TopBarFeature;->$stable:I

    return-void
.end method

.method private constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static synthetic a(Landroid/view/View;)V
    .locals 0

    invoke-static {p0}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$lambda$1$1(Landroid/view/View;)V

    return-void
.end method

.method public static final synthetic access$applyTopBar(Lwex/ui/core/feature/TopBarFeature;Landroid/app/Activity;)V
    .locals 0

    invoke-direct {p0, p1}, Lwex/ui/core/feature/TopBarFeature;->applyTopBar(Landroid/app/Activity;)V

    return-void
.end method

.method public static final synthetic access$buildProfile$dp(FF)I
    .locals 0

    invoke-static {p0, p1}, Lwex/ui/core/feature/TopBarFeature;->buildProfile$dp(FF)I

    move-result p0

    return p0
.end method

.method public static isHomeActivity(Landroid/app/Activity;)Z
    .locals 2

    if-eqz p0, :not_home

    invoke-virtual {p0}, Landroid/app/Activity;->isFinishing()Z

    move-result v0

    if-nez v0, :not_home

    invoke-virtual {p0}, Landroid/app/Activity;->isDestroyed()Z

    move-result v0

    if-nez v0, :not_home

    invoke-virtual {p0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/Class;->getName()Ljava/lang/String;

    move-result-object v0

    const-string v1, "com.tencent.mm.ui.LauncherUI"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    return v0

    :not_home
    const/4 v0, 0x0

    return v0
.end method

.method private final applyTopBar(Landroid/app/Activity;)V
    .locals 2

    invoke-static {p1}, Lwex/ui/core/feature/TopBarFeature;->isHomeActivity(Landroid/app/Activity;)Z

    move-result v0

    if-eqz v0, :done

    invoke-virtual {p1}, Landroid/app/Activity;->getWindow()Landroid/view/Window;

    move-result-object v0

    if-eqz v0, :done

    invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;

    move-result-object v0

    instance-of v1, v0, Landroid/view/ViewGroup;

    if-eqz v1, :done

    check-cast v0, Landroid/view/ViewGroup;

    new-instance p0, Lx4/g0;

    const/4 v1, 0x1

    invoke-direct {p0, v1}, Lx4/g0;-><init>(I)V

    iput-object v0, p0, Lx4/g0;->d:Landroid/view/ViewGroup;

    iput-object p1, p0, Lx4/g0;->e:Landroid/app/Activity;

    invoke-static {}, Ljava/lang/invoke/VarHandle;->storeStoreFence()V

    invoke-virtual {v0, p0}, Landroid/view/View;->post(Ljava/lang/Runnable;)Z

    :done
    return-void
.end method

.method private static final applyTopBar$lambda$0(Landroid/view/ViewGroup;Landroid/app/Activity;)V
    .locals 4

    :try_start_gate
    invoke-static {p1}, Lwex/ui/core/feature/TopBarFeature;->isHomeActivity(Landroid/app/Activity;)Z

    move-result v0

    if-eqz v0, :done

    invoke-virtual {p0}, Landroid/view/View;->isAttachedToWindow()Z

    move-result v0

    if-eqz v0, :done

    sget-object v0, Lwex/ui/core/WexStore;->INSTANCE:Lwex/ui/core/WexStore;

    const-string v1, "top_profile_enabled"

    const/4 v2, 0x0

    invoke-virtual {v0, v1, v2}, Lwex/ui/core/WexStore;->getBool(Ljava/lang/String;Z)Z

    move-result v2
    :try_end_gate
    .catchall {:try_start_gate .. :try_end_gate} :catch_gate

    :try_start_title
    sget-object v3, Lwex/ui/core/feature/TopBarFeature;->INSTANCE:Lwex/ui/core/feature/TopBarFeature;

    invoke-direct {v3, p0, v2}, Lwex/ui/core/feature/TopBarFeature;->updateTitle(Landroid/view/ViewGroup;Z)V
    :try_end_title
    .catchall {:try_start_title .. :try_end_title} :catch_title

    goto :apply_profile

    :catch_title
    move-exception v0

    const-string v0, "WEX: TopBar title refresh failed"

    invoke-static {v0}, Ldev/mindlink/xposed/XposedBridge;->log(Ljava/lang/String;)V

    :apply_profile
    :try_start_profile
    sget-object v3, Lwex/ui/core/feature/TopBarFeature;->INSTANCE:Lwex/ui/core/feature/TopBarFeature;

    if-eqz v2, :remove_profile

    invoke-direct {v3, p1, p0}, Lwex/ui/core/feature/TopBarFeature;->insertProfile(Landroid/app/Activity;Landroid/view/ViewGroup;)V

    goto :profile_done

    :remove_profile
    const/4 v0, 0x0

    sput-boolean v0, Lwex/ui/core/feature/TopBarFeature;->emptyProfileReported:Z

    const-string v0, "w\u0435\u0445_top_profile"

    invoke-virtual {p0, v0}, Landroid/view/View;->findViewWithTag(Ljava/lang/Object;)Landroid/view/View;

    move-result-object v0

    invoke-static {v0}, Lwex/ui/core/feature/TopBarFeature;->removeOwnedView(Landroid/view/View;)V

    :profile_done
    :try_end_profile
    .catchall {:try_start_profile .. :try_end_profile} :catch_profile

    goto :apply_search

    :catch_profile
    move-exception v0

    const-string v0, "WEX: TopBar profile refresh failed"

    invoke-static {v0}, Ldev/mindlink/xposed/XposedBridge;->log(Ljava/lang/String;)V

    :apply_search
    :try_start_search
    sget-object v3, Lwex/ui/core/feature/TopBarFeature;->INSTANCE:Lwex/ui/core/feature/TopBarFeature;

    invoke-direct {v3, p0, p1, v2}, Lwex/ui/core/feature/TopBarFeature;->updateSearch(Landroid/view/ViewGroup;Landroid/app/Activity;Z)V
    :try_end_search
    .catchall {:try_start_search .. :try_end_search} :catch_search

    goto :apply_sidebar

    :catch_search
    move-exception v0

    const-string v0, "WEX: TopBar search refresh failed"

    invoke-static {v0}, Ldev/mindlink/xposed/XposedBridge;->log(Ljava/lang/String;)V

    :apply_sidebar
    # 旧 HomeSidebar Dialog 已由 HomeSidePanelFeature 的 LauncherUI 内抽屉浮层替换。
    goto :done

    :done
    return-void

    :catch_sidebar
    move-exception v0

    return-void

    :catch_gate
    move-exception v0

    const-string v0, "WEX: TopBar configuration unavailable"

    invoke-static {v0}, Ldev/mindlink/xposed/XposedBridge;->log(Ljava/lang/String;)V

    return-void
.end method

.method private static isHomeTitle(Landroid/widget/TextView;)Z
    .locals 4

    invoke-virtual {p0}, Landroid/view/View;->getContext()Landroid/content/Context;

    move-result-object v0

    :unwrap
    instance-of v1, v0, Landroid/app/Activity;

    if-nez v1, :activity

    instance-of v1, v0, Landroid/content/ContextWrapper;

    if-eqz v1, :no

    check-cast v0, Landroid/content/ContextWrapper;

    invoke-virtual {v0}, Landroid/content/ContextWrapper;->getBaseContext()Landroid/content/Context;

    move-result-object v1

    if-eq v0, v1, :no

    move-object v0, v1

    goto :unwrap

    :activity
    check-cast v0, Landroid/app/Activity;

    invoke-static {v0}, Lwex/ui/core/feature/TopBarFeature;->isHomeActivity(Landroid/app/Activity;)Z

    move-result v0

    if-eqz v0, :no

    move-object v0, p0

    :parent
    invoke-virtual {v0}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v1

    const-string v2, "w\u0435\u0445_top_profile"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :no

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/Class;->getName()Ljava/lang/String;

    move-result-object v1

    const-string v2, "Toolbar"

    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v1

    if-nez v1, :yes

    invoke-virtual {v0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;

    move-result-object v1

    instance-of v2, v1, Landroid/view/View;

    if-eqz v2, :no

    move-object v0, v1

    check-cast v0, Landroid/view/View;

    goto :parent

    :yes
    const/4 v0, 0x1

    return v0

    :no
    const/4 v0, 0x0

    return v0
.end method

.method public static onTitleText(Ldev/mindlink/xposed/XC_MethodHook$MethodHookParam;)V
    .locals 7

    :try_start
    sget-boolean v0, Lwex/ui/core/feature/TopBarFeature;->updatingTitle:Z

    if-nez v0, :done

    iget-object v0, p0, Ldev/mindlink/xposed/XC_MethodHook$MethodHookParam;->thisObject:Ljava/lang/Object;

    instance-of v1, v0, Landroid/widget/TextView;

    if-eqz v1, :done

    check-cast v0, Landroid/widget/TextView;

    invoke-static {v0}, Lwex/ui/core/feature/TopBarFeature;->isHomeTitle(Landroid/widget/TextView;)Z

    move-result v1

    if-eqz v1, :done

    iget-object v1, p0, Ldev/mindlink/xposed/XC_MethodHook$MethodHookParam;->args:[Ljava/lang/Object;

    array-length v2, v1

    if-eqz v2, :done

    const/4 v2, 0x0

    aget-object v3, v1, v2

    instance-of v4, v3, Ljava/lang/CharSequence;

    if-eqz v4, :done

    const-string v4, "wex.top.originalTitle"

    invoke-static {v0, v4}, Ldev/mindlink/xposed/XposedHelpers;->getAdditionalInstanceField(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v5

    if-nez v5, :known_title

    invoke-virtual {v3}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object v5

    const-string v6, "\u5fae\u4fe1"

    invoke-virtual {v6, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v5

    if-eqz v5, :done

    :known_title
    invoke-static {v0, v4, v3}, Ldev/mindlink/xposed/XposedHelpers;->setAdditionalInstanceField(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lwex/ui/core/WexStore;->INSTANCE:Lwex/ui/core/WexStore;

    const-string v4, "top_profile_enabled"

    invoke-virtual {v3, v4, v2}, Lwex/ui/core/WexStore;->getBool(Ljava/lang/String;Z)Z

    move-result v4

    if-eqz v4, :done

    const-string v4, "top_title"

    const-string v5, ""

    invoke-virtual {v3, v4, v5}, Lwex/ui/core/WexStore;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/String;->length()I

    move-result v4

    if-eqz v4, :done

    aput-object v3, v1, v2
    :try_end
    .catchall {:try_start .. :try_end} :catch_title

    :done
    return-void

    :catch_title
    move-exception v0

    const-string v0, "WEX: TopBar title hook skipped"

    invoke-static {v0}, Ldev/mindlink/xposed/XposedBridge;->log(Ljava/lang/String;)V

    return-void
.end method

.method private final updateTitle(Landroid/view/ViewGroup;Z)V
    .locals 2

    sget-object v0, Lwex/ui/core/core/ViewFinder;->INSTANCE:Lwex/ui/core/core/ViewFinder;

    const-string v1, "Toolbar"

    invoke-virtual {v0, p1, v1}, Lwex/ui/core/core/ViewFinder;->findViewByClassName(Landroid/view/View;Ljava/lang/String;)Landroid/view/View;

    move-result-object v0

    if-eqz v0, :done

    invoke-static {v0, p2}, Lwex/ui/core/feature/TopBarFeature;->updateTitleTree(Landroid/view/View;Z)V

    :done
    return-void
.end method

.method private static updateTitleTree(Landroid/view/View;Z)V
    .locals 6

    invoke-virtual {p0}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    const-string v1, "w\u0435\u0445_top_profile"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :done

    instance-of v0, p0, Landroid/widget/TextView;

    if-eqz v0, :children

    move-object v0, p0

    check-cast v0, Landroid/widget/TextView;

    invoke-static {v0}, Lwex/ui/core/feature/TopBarFeature;->isHomeTitle(Landroid/widget/TextView;)Z

    move-result v1

    if-eqz v1, :done

    const-string v1, "wex.top.originalTitle"

    invoke-static {v0, v1}, Ldev/mindlink/xposed/XposedHelpers;->getAdditionalInstanceField(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v2

    if-nez v2, :have_original

    if-eqz p1, :done

    invoke-virtual {v0}, Landroid/widget/TextView;->getText()Ljava/lang/CharSequence;

    move-result-object v2

    if-eqz v2, :done

    invoke-interface {v2}, Ljava/lang/CharSequence;->toString()Ljava/lang/String;

    move-result-object v3

    const-string v4, "\u5fae\u4fe1"

    invoke-virtual {v4, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :done

    invoke-static {v0, v1, v2}, Ldev/mindlink/xposed/XposedHelpers;->setAdditionalInstanceField(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;

    :have_original
    check-cast v2, Ljava/lang/CharSequence;

    if-eqz p1, :set_title

    sget-object v3, Lwex/ui/core/WexStore;->INSTANCE:Lwex/ui/core/WexStore;

    const-string v4, "top_title"

    const-string v5, ""

    invoke-virtual {v3, v4, v5}, Lwex/ui/core/WexStore;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/String;->length()I

    move-result v4

    if-eqz v4, :set_title

    move-object v2, v3

    :set_title
    const/4 v3, 0x1

    sput-boolean v3, Lwex/ui/core/feature/TopBarFeature;->updatingTitle:Z

    :try_start_set
    invoke-virtual {v0, v2}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
    :try_end_set
    .catchall {:try_start_set .. :try_end_set} :catch_set

    const/4 v3, 0x0

    sput-boolean v3, Lwex/ui/core/feature/TopBarFeature;->updatingTitle:Z

    return-void

    :catch_set
    move-exception v0

    const/4 v3, 0x0

    sput-boolean v3, Lwex/ui/core/feature/TopBarFeature;->updatingTitle:Z

    throw v0

    :children
    instance-of v0, p0, Landroid/view/ViewGroup;

    if-eqz v0, :done

    check-cast p0, Landroid/view/ViewGroup;

    invoke-virtual {p0}, Landroid/view/ViewGroup;->getChildCount()I

    move-result v0

    const/4 v1, 0x0

    :next
    if-ge v1, v0, :done

    invoke-virtual {p0, v1}, Landroid/view/ViewGroup;->getChildAt(I)Landroid/view/View;

    move-result-object v2

    invoke-static {v2, p1}, Lwex/ui/core/feature/TopBarFeature;->updateTitleTree(Landroid/view/View;Z)V

    add-int/lit8 v1, v1, 0x1

    goto :next

    :done
    return-void
.end method

.method private static removeOwnedView(Landroid/view/View;)V
    .locals 2

    if-eqz p0, :done

    invoke-virtual {p0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;

    move-result-object v0

    instance-of v1, v0, Landroid/view/ViewGroup;

    if-eqz v1, :done

    check-cast v0, Landroid/view/ViewGroup;

    invoke-virtual {v0, p0}, Landroid/view/ViewGroup;->removeView(Landroid/view/View;)V

    :done
    return-void
.end method

.method private static restoreSearchButton()V
    .locals 2

    sget-object v0, Lwex/ui/core/feature/TopBarFeature;->searchBtn:Ljava/lang/ref/WeakReference;

    if-eqz v0, :done

    invoke-virtual {v0}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/view/View;

    if-eqz v0, :clear

    sget v1, Lwex/ui/core/feature/TopBarFeature;->searchVisibility:I

    invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V

    :clear
    const/4 v0, 0x0

    sput-object v0, Lwex/ui/core/feature/TopBarFeature;->searchBtn:Ljava/lang/ref/WeakReference;

    :done
    return-void
.end method

.method private final updateSearch(Landroid/view/ViewGroup;Landroid/app/Activity;Z)V
    .locals 10

    const/4 v0, 0x0

    sget-object v1, Lwex/ui/core/feature/TopBarFeature;->searchHeader:Ljava/lang/ref/WeakReference;

    if-eqz v1, :header_loaded

    invoke-virtual {v1}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/view/View;

    :header_loaded
    const/4 v1, 0x0

    sget-object v2, Lwex/ui/core/feature/TopBarFeature;->searchList:Ljava/lang/ref/WeakReference;

    if-eqz v2, :list_loaded

    invoke-virtual {v2}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Landroid/widget/ListView;

    :list_loaded
    if-eqz p3, :disabled

    sget-object v2, Lwex/ui/core/WexStore;->INSTANCE:Lwex/ui/core/WexStore;

    const-string v3, "top_search_bar"

    const/4 v4, 0x0

    invoke-virtual {v2, v3, v4}, Lwex/ui/core/WexStore;->getBool(Ljava/lang/String;Z)Z

    move-result v2

    if-eqz v2, :disabled

    sget-object v2, Lwex/ui/core/core/ViewFinder;->INSTANCE:Lwex/ui/core/core/ViewFinder;

    invoke-virtual {v2, p1}, Lwex/ui/core/core/ViewFinder;->findListView(Landroid/view/View;)Landroid/view/View;

    move-result-object v3

    instance-of v4, v3, Landroid/widget/ListView;

    if-eqz v4, :missing_list

    check-cast v3, Landroid/widget/ListView;

    if-eq v1, v3, :same_list

    if-eqz v0, :old_removed

    if-eqz v1, :old_removed

    invoke-virtual {v1, v0}, Landroid/widget/ListView;->removeHeaderView(Landroid/view/View;)Z

    :old_removed
    invoke-static {}, Lwex/ui/core/feature/TopBarFeature;->restoreSearchButton()V

    const/4 v0, 0x0

    :same_list
    move-object v1, v3

    if-nez v0, :have_header

    const-string v3, "w\u0435\u0445_top_search"

    invoke-virtual {p1, v3}, Landroid/view/View;->findViewWithTag(Ljava/lang/Object;)Landroid/view/View;

    move-result-object v0

    :have_header
    const-string v3, "\u641c\u7d22"

    const/4 v4, 0x0

    invoke-virtual {v2, p1, v3, v4}, Lwex/ui/core/core/ViewFinder;->findViewByDesc(Landroid/view/View;Ljava/lang/String;Ljava/lang/Class;)Landroid/view/View;

    move-result-object v2

    if-eqz v2, :missing_button

    if-nez v0, :refresh_hint

    invoke-direct {p0, p2}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar(Landroid/app/Activity;)Landroid/view/View;

    move-result-object v0

    const/4 v3, 0x0

    invoke-virtual {v1, v0, v3, v3}, Landroid/widget/ListView;->addHeaderView(Landroid/view/View;Ljava/lang/Object;Z)V

    :refresh_hint
    new-instance v3, Ljava/lang/ref/WeakReference;

    invoke-direct {v3, v0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v3, Lwex/ui/core/feature/TopBarFeature;->searchHeader:Ljava/lang/ref/WeakReference;

    new-instance v3, Ljava/lang/ref/WeakReference;

    invoke-direct {v3, v1}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v3, Lwex/ui/core/feature/TopBarFeature;->searchList:Ljava/lang/ref/WeakReference;

    const-string v3, "wex_top_search_hint"

    invoke-virtual {v0, v3}, Landroid/view/View;->findViewWithTag(Ljava/lang/Object;)Landroid/view/View;

    move-result-object v3

    instance-of v4, v3, Landroid/widget/TextView;

    if-eqz v4, :hide_button

    check-cast v3, Landroid/widget/TextView;

    sget-object v4, Lwex/ui/core/WexStore;->INSTANCE:Lwex/ui/core/WexStore;

    const-string v5, "top_search_hint"

    const-string v6, ""

    invoke-virtual {v4, v5, v6}, Lwex/ui/core/WexStore;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v4}, Ljava/lang/String;->length()I

    move-result v5

    if-nez v5, :set_hint

    const-string v4, "\u641c\u7d22"

    :set_hint
    invoke-virtual {v3, v4}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    :hide_button
    const/4 v3, 0x0

    sget-object v4, Lwex/ui/core/feature/TopBarFeature;->searchBtn:Ljava/lang/ref/WeakReference;

    if-eqz v4, :compare_button

    invoke-virtual {v4}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;

    move-result-object v3

    :compare_button
    if-eq v3, v2, :hide_current

    invoke-static {}, Lwex/ui/core/feature/TopBarFeature;->restoreSearchButton()V

    invoke-virtual {v2}, Landroid/view/View;->getVisibility()I

    move-result v3

    sput v3, Lwex/ui/core/feature/TopBarFeature;->searchVisibility:I

    new-instance v3, Ljava/lang/ref/WeakReference;

    invoke-direct {v3, v2}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v3, Lwex/ui/core/feature/TopBarFeature;->searchBtn:Ljava/lang/ref/WeakReference;

    :hide_current
    const/16 v3, 0x8

    invoke-virtual {v2, v3}, Landroid/view/View;->setVisibility(I)V

    return-void

    :missing_list
    const-string v2, "TopBar: home ListView unavailable; native search retained"

    invoke-direct {p0, v2}, Lwex/ui/core/feature/TopBarFeature;->log(Ljava/lang/String;)V

    goto :disabled

    :missing_button
    const-string v2, "TopBar: native search button unavailable"

    invoke-direct {p0, v2}, Lwex/ui/core/feature/TopBarFeature;->log(Ljava/lang/String;)V

    :disabled
    invoke-static {}, Lwex/ui/core/feature/TopBarFeature;->restoreSearchButton()V

    if-eqz v0, :clear

    if-eqz v1, :clear

    invoke-virtual {v1, v0}, Landroid/widget/ListView;->removeHeaderView(Landroid/view/View;)Z

    :clear
    const/4 v0, 0x0

    sput-object v0, Lwex/ui/core/feature/TopBarFeature;->searchHeader:Ljava/lang/ref/WeakReference;

    sput-object v0, Lwex/ui/core/feature/TopBarFeature;->searchList:Ljava/lang/ref/WeakReference;

    return-void
.end method

.method public static synthetic b(Landroid/view/ViewGroup;Landroid/app/Activity;)V
    .locals 0

    invoke-static {p0, p1}, Lwex/ui/core/feature/TopBarFeature;->applyTopBar$lambda$0(Landroid/view/ViewGroup;Landroid/app/Activity;)V

    return-void
.end method

.method private final buildProfile(Landroid/app/Activity;)Landroid/view/View;
    .locals 1

    # 统一交给 ProfileView：圆形头像 / 首字占位 / 昵称 / 状态圆点，
    # 版式参照 WeKit 的 HomeSidePanelHome.kt（GPL-3.0）。
    invoke-static {p1}, Lwex/ui/core/feature/ProfileView;->buildBar(Landroid/app/Activity;)Landroid/view/View;

    move-result-object p0

    return-object p0
.end method

.method private static final buildProfile$dp(FF)I
    .locals 0

    mul-float/2addr p1, p0

    float-to-int p0, p1

    return p0
.end method

.method private final buildSearchBar(Landroid/app/Activity;)Landroid/view/View;
    .locals 10

    invoke-virtual {p1}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    invoke-virtual {v0}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;

    move-result-object v0

    iget v0, v0, Landroid/util/DisplayMetrics;->density:F

    new-instance v1, Landroid/widget/LinearLayout;

    invoke-direct {v1, p1}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V

    const/4 v2, 0x0

    invoke-virtual {v1, v2}, Landroid/widget/LinearLayout;->setOrientation(I)V

    const/16 v3, 0x10

    invoke-virtual {v1, v3}, Landroid/widget/LinearLayout;->setGravity(I)V

    const-string v3, "w\u0435\u0445_top_search"

    invoke-virtual {v1, v3}, Landroid/view/View;->setTag(Ljava/lang/Object;)V

    const/high16 v3, 0x41800000    # 16.0f

    invoke-static {v0, v3}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result v4

    const/high16 v5, 0x41000000    # 8.0f

    invoke-static {v0, v5}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result v6

    invoke-static {v0, v3}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result v3

    invoke-static {v0, v5}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result v5

    invoke-virtual {v1, v4, v6, v3, v5}, Landroid/view/View;->setPadding(IIII)V

    const/16 v3, 0x11

    invoke-static {p1, v2, v3}, Landroidx/activity/b;->i(Landroid/app/Activity;II)Landroid/widget/LinearLayout;

    move-result-object v4

    const/high16 v5, 0x41600000    # 14.0f

    invoke-static {v0, v5}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result v6

    const/high16 v7, 0x41100000    # 9.0f

    invoke-static {v0, v7}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result v8

    invoke-static {v0, v5}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result v9

    invoke-static {v0, v7}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result v7

    invoke-virtual {v4, v6, v8, v9, v7}, Landroid/view/View;->setPadding(IIII)V

    const/4 v6, 0x1

    invoke-virtual {v4, v6}, Landroid/view/View;->setClipToOutline(Z)V

    new-instance v7, Lh1/q2;

    const/4 v8, 0x3

    invoke-direct {v7, v8}, Lh1/q2;-><init>(I)V

    invoke-virtual {v4, v7}, Landroid/view/View;->setOutlineProvider(Landroid/view/ViewOutlineProvider;)V

    new-instance v7, Landroid/graphics/drawable/GradientDrawable;

    invoke-direct {v7}, Landroid/graphics/drawable/GradientDrawable;-><init>()V

    const-string v8, "#F2F2F2"

    invoke-static {v8}, Landroid/graphics/Color;->parseColor(Ljava/lang/String;)I

    move-result v8

    invoke-virtual {v7, v8}, Landroid/graphics/drawable/GradientDrawable;->setColor(I)V

    const/high16 v8, 0x41a00000    # 20.0f

    invoke-static {v0, v8}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result v8

    int-to-float v8, v8

    invoke-virtual {v7, v8}, Landroid/graphics/drawable/GradientDrawable;->setCornerRadius(F)V

    invoke-virtual {v4, v7}, Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V

    invoke-virtual {v4, v6}, Landroid/view/View;->setClickable(Z)V

    new-instance v6, Lx4/m0;

    const/16 v7, 0x9

    invoke-direct {v6, v7}, Lx4/m0;-><init>(I)V

    invoke-virtual {v4, v6}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    invoke-direct {p0}, Lwex/ui/core/feature/TopBarFeature;->loadSearchIcon()Landroid/graphics/Bitmap;

    move-result-object p0

    if-eqz p0, :cond_0

    new-instance v6, Landroid/widget/ImageView;

    invoke-direct {v6, p1}, Landroid/widget/ImageView;-><init>(Landroid/content/Context;)V

    invoke-virtual {v6, p0}, Landroid/widget/ImageView;->setImageBitmap(Landroid/graphics/Bitmap;)V

    sget-object p0, Landroid/widget/ImageView$ScaleType;->FIT_CENTER:Landroid/widget/ImageView$ScaleType;

    invoke-virtual {v6, p0}, Landroid/widget/ImageView;->setScaleType(Landroid/widget/ImageView$ScaleType;)V

    new-instance p0, Landroid/widget/LinearLayout$LayoutParams;

    const/high16 v7, 0x41900000    # 18.0f

    invoke-static {v0, v7}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result v8

    invoke-static {v0, v7}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result v7

    invoke-direct {p0, v8, v7}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    invoke-virtual {v6, p0}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    invoke-virtual {v4, v6}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    :cond_0
    new-instance p0, Landroid/widget/TextView;

    invoke-direct {p0, p1}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    sget-object p1, Lwex/ui/core/WexStore;->INSTANCE:Lwex/ui/core/WexStore;

    const-string v6, "wex_top_search_hint"

    invoke-virtual {p0, v6}, Landroid/view/View;->setTag(Ljava/lang/Object;)V

    const-string v6, "top_search_hint"

    const-string v7, ""

    invoke-virtual {p1, v6, v7}, Lwex/ui/core/WexStore;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    invoke-interface {p1}, Ljava/lang/CharSequence;->length()I

    move-result v6

    if-nez v6, :cond_1

    const-string p1, "\u641c\u7d22"

    :cond_1
    invoke-virtual {p0, p1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    invoke-virtual {p0, v5}, Landroid/widget/TextView;->setTextSize(F)V

    const-string p1, "#666666"

    invoke-static {p1}, Landroid/graphics/Color;->parseColor(Ljava/lang/String;)I

    move-result p1

    invoke-virtual {p0, p1}, Landroid/widget/TextView;->setTextColor(I)V

    invoke-virtual {p0, v3}, Landroid/widget/TextView;->setGravity(I)V

    const/high16 p1, 0x40c00000    # 6.0f

    invoke-static {v0, p1}, Lwex/ui/core/feature/TopBarFeature;->buildSearchBar$dp(FF)I

    move-result p1

    invoke-virtual {p0, p1, v2, v2, v2}, Landroid/widget/TextView;->setPadding(IIII)V

    invoke-virtual {v4, p0}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    new-instance p0, Landroid/widget/LinearLayout$LayoutParams;

    const/4 p1, -0x1

    const/4 v0, -0x2

    invoke-direct {p0, p1, v0}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    invoke-virtual {v1, v4, p0}, Landroid/view/ViewGroup;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V

    return-object v1
.end method

.method private static final buildSearchBar$dp(FF)I
    .locals 0

    mul-float/2addr p1, p0

    float-to-int p0, p1

    return p0
.end method

.method private static final buildSearchBar$lambda$1$1(Landroid/view/View;)V
    .locals 2

    :try_start_0
    sget-object p0, Lwex/ui/core/feature/TopBarFeature;->searchBtn:Ljava/lang/ref/WeakReference;

    if-eqz p0, :cond_0

    invoke-virtual {p0}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;

    move-result-object p0

    check-cast p0, Landroid/view/View;

    if-eqz p0, :cond_0

    invoke-virtual {p0}, Landroid/view/View;->performClick()Z
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :cond_0
    return-void

    :catch_0
    move-exception p0

    sget-object v0, Lwex/ui/core/feature/TopBarFeature;->INSTANCE:Lwex/ui/core/feature/TopBarFeature;

    invoke-virtual {p0}, Ljava/lang/Throwable;->getMessage()Ljava/lang/String;

    move-result-object p0

    const-string v1, "\u8c03\u8d77\u539f\u751f\u641c\u7d22\u5931\u8d25: "

    invoke-static {v1, p0}, Landroidx/activity/b;->m(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p0

    invoke-direct {v0, p0}, Lwex/ui/core/feature/TopBarFeature;->log(Ljava/lang/String;)V

    return-void
.end method

.method private final insertProfile(Landroid/app/Activity;Landroid/view/ViewGroup;)V
    .locals 4

    sget-object v0, Lwex/ui/core/core/ViewFinder;->INSTANCE:Lwex/ui/core/core/ViewFinder;

    const-string v1, "Toolbar"

    invoke-virtual {v0, p2, v1}, Lwex/ui/core/core/ViewFinder;->findViewByClassName(Landroid/view/View;Ljava/lang/String;)Landroid/view/View;

    move-result-object p2

    instance-of v0, p2, Landroid/view/ViewGroup;

    if-eqz v0, :cond_0

    check-cast p2, Landroid/view/ViewGroup;

    goto :goto_0

    :cond_0
    const/4 p2, 0x0

    :goto_0
    if-nez p2, :cond_1

    const-string p1, "\u9876\u680f\uff1a\u672a\u627e\u5230 Toolbar"

    invoke-direct {p0, p1}, Lwex/ui/core/feature/TopBarFeature;->log(Ljava/lang/String;)V

    return-void

    :cond_1
    const-string v0, "w\u0435\u0445_top_profile"

    invoke-virtual {p2, v0}, Landroid/view/View;->findViewWithTag(Ljava/lang/Object;)Landroid/view/View;

    move-result-object v0

    # Build first, then replace the owned tag in its existing parent.
    invoke-direct {p0, p1}, Lwex/ui/core/feature/TopBarFeature;->buildProfile(Landroid/app/Activity;)Landroid/view/View;

    move-result-object p1

    if-nez p1, :profile_ready

    invoke-static {v0}, Lwex/ui/core/feature/TopBarFeature;->removeOwnedView(Landroid/view/View;)V

    return-void

    :profile_ready
    if-eqz v0, :cond_2

    invoke-virtual {v0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;

    move-result-object v1

    instance-of v2, v1, Landroid/view/ViewGroup;

    if-eqz v2, :cond_2

    check-cast v1, Landroid/view/ViewGroup;

    invoke-virtual {v0}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v2

    invoke-virtual {v1, v0}, Landroid/view/ViewGroup;->indexOfChild(Landroid/view/View;)I

    move-result v3

    invoke-virtual {v1, v0}, Landroid/view/ViewGroup;->removeView(Landroid/view/View;)V

    invoke-virtual {v1, p1, v3, v2}, Landroid/view/ViewGroup;->addView(Landroid/view/View;ILandroid/view/ViewGroup$LayoutParams;)V

    return-void

    :cond_2
    invoke-virtual {p2}, Landroid/view/ViewGroup;->getChildCount()I

    move-result v0

    const/4 v1, 0x0

    :goto_1
    if-ge v1, v0, :cond_4

    invoke-virtual {p2, v1}, Landroid/view/ViewGroup;->getChildAt(I)Landroid/view/View;

    move-result-object v2

    instance-of v3, v2, Landroid/widget/RelativeLayout;

    if-eqz v3, :cond_3

    move-object p2, v2

    check-cast p2, Landroid/view/ViewGroup;

    goto :goto_2

    :cond_3
    add-int/lit8 v1, v1, 0x1

    goto :goto_1

    :cond_4
    :goto_2
    instance-of v0, p2, Landroid/widget/RelativeLayout;

    const/4 v1, -0x1

    const/4 v2, -0x2

    if-eqz v0, :cond_6

    new-instance v0, Landroid/widget/RelativeLayout$LayoutParams;

    invoke-direct {v0, v2, v1}, Landroid/widget/RelativeLayout$LayoutParams;-><init>(II)V

    const/16 v1, 0x9

    invoke-virtual {v0, v1}, Landroid/widget/RelativeLayout$LayoutParams;->addRule(I)V

    const/16 v1, 0xf

    invoke-virtual {v0, v1}, Landroid/widget/RelativeLayout$LayoutParams;->addRule(I)V

    invoke-virtual {p1, v0}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    goto :goto_4

    :cond_6
    new-instance v0, Landroid/view/ViewGroup$LayoutParams;

    invoke-direct {v0, v2, v1}, Landroid/view/ViewGroup$LayoutParams;-><init>(II)V

    invoke-virtual {p1, v0}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    :goto_4
    invoke-virtual {p2, p1}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    const-string p1, "\u9876\u680f\uff1a\u5934\u50cf\u6635\u79f0\u5df2\u63d2\u5165"

    invoke-direct {p0, p1}, Lwex/ui/core/feature/TopBarFeature;->log(Ljava/lang/String;)V

    return-void
.end method

.method private final loadAvatar()Landroid/graphics/Bitmap;
    .locals 3

    sget-object v0, Lwex/ui/core/WexStore;->INSTANCE:Lwex/ui/core/WexStore;

    const-string v1, "top_avatar_path"

    const-string v2, ""

    invoke-virtual {v0, v1, v2}, Lwex/ui/core/WexStore;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-interface {v0}, Ljava/lang/CharSequence;->length()I

    move-result v1

    if-lez v1, :cond_0

    :try_start_0
    new-instance v1, Ljava/io/File;

    invoke-direct {v1, v0}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1}, Ljava/io/File;->exists()Z

    move-result v1

    if-eqz v1, :cond_0

    invoke-static {v0}, Landroid/graphics/BitmapFactory;->decodeFile(Ljava/lang/String;)Landroid/graphics/Bitmap;

    move-result-object p0
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-object p0

    :catch_0
    move-exception v0

    invoke-virtual {v0}, Ljava/lang/Throwable;->getMessage()Ljava/lang/String;

    move-result-object v0

    const-string v1, "\u81ea\u5b9a\u4e49\u5934\u50cf\u52a0\u8f7d\u5931\u8d25: "

    invoke-static {v1, v0}, Landroidx/activity/b;->m(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-direct {p0, v0}, Lwex/ui/core/feature/TopBarFeature;->log(Ljava/lang/String;)V

    :cond_0
    const/4 p0, 0x0

    return-object p0
.end method

.method private final loadSearchIcon()Landroid/graphics/Bitmap;
    .locals 6

    # --- Tabler 矢量图标：命中即用 ---
    sget-object v0, Lwex/ui/core/feature/TopBarFeature;->cachedIcon:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_tabler_render

    return-object v0

    :cond_tabler_render
    const/high16 v1, 0x41900000    # 18.0f

    invoke-static {v1}, Lwex/ui/core/feature/LucideIcons;->toPx(F)I

    move-result v1

    const-string v2, "topbar_search.png"

    invoke-static {v2}, Lwex/ui/core/feature/LucideIcons;->colorOf(Ljava/lang/String;)I

    move-result v3

    invoke-static {v2, v1, v3}, Lwex/ui/core/feature/LucideIcons;->bitmap(Ljava/lang/String;II)Landroid/graphics/Bitmap;

    move-result-object v1

    if-eqz v1, :cond_tabler_null

    sput-object v1, Lwex/ui/core/feature/TopBarFeature;->cachedIcon:Landroid/graphics/Bitmap;

    return-object v1

    :cond_tabler_null
    const/4 v1, 0x0

    return-object v1
    # --- Tabler 分支结束 ---


    const-class v0, Landroid/content/res/AssetManager;

    sget-object v1, Lwex/ui/core/feature/TopBarFeature;->cachedIcon:Landroid/graphics/Bitmap;

    if-eqz v1, :cond_0

    return-object v1

    :cond_0
    sget-object v1, Lwex/ui/core/feature/TopBarFeature;->modulePath:Ljava/lang/String;

    const/4 v2, 0x0

    if-nez v1, :cond_1

    return-object v2

    :cond_1
    :try_start_0
    invoke-virtual {v0}, Ljava/lang/Class;->newInstance()Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Landroid/content/res/AssetManager;

    const-string v4, "addAssetPath"

    const-class v5, Ljava/lang/String;

    filled-new-array {v5}, [Ljava/lang/Class;

    move-result-object v5

    invoke-virtual {v0, v4, v5}, Ljava/lang/Class;->getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v0

    filled-new-array {v1}, [Ljava/lang/Object;

    move-result-object v1

    invoke-virtual {v0, v3, v1}, Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;

    const-string v0, "topbar_search.png"

    invoke-virtual {v3, v0}, Landroid/content/res/AssetManager;->open(Ljava/lang/String;)Ljava/io/InputStream;

    move-result-object v0
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :try_start_1
    invoke-static {v0}, Landroid/graphics/BitmapFactory;->decodeStream(Ljava/io/InputStream;)Landroid/graphics/Bitmap;

    move-result-object v1
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    :try_start_2
    invoke-static {v0, v2}, Ln4/a;->m(Ljava/io/Closeable;Ljava/lang/Throwable;)V

    if-eqz v1, :cond_2

    sput-object v1, Lwex/ui/core/feature/TopBarFeature;->cachedIcon:Landroid/graphics/Bitmap;
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_0

    return-object v1

    :catch_0
    move-exception v0

    goto :goto_0

    :cond_2
    return-object v2

    :catchall_0
    move-exception v1

    :try_start_3
    throw v1
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_1

    :catchall_1
    move-exception v3

    :try_start_4
    invoke-static {v0, v1}, Ln4/a;->m(Ljava/io/Closeable;Ljava/lang/Throwable;)V

    throw v3
    :try_end_4
    .catch Ljava/lang/Exception; {:try_start_4 .. :try_end_4} :catch_0

    :goto_0
    invoke-virtual {v0}, Ljava/lang/Throwable;->getMessage()Ljava/lang/String;

    move-result-object v0

    const-string v1, "\u641c\u7d22\u56fe\u6807\u52a0\u8f7d\u5931\u8d25: "

    invoke-static {v1, v0}, Landroidx/activity/b;->m(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-direct {p0, v0}, Lwex/ui/core/feature/TopBarFeature;->log(Ljava/lang/String;)V

    return-object v2
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
.method public final declared-synchronized init(Ldev/mindlink/xposed/callbacks/XC_LoadPackage$LoadPackageParam;Ljava/lang/String;)V
    .locals 6

    invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    sput-object p2, Lwex/ui/core/feature/TopBarFeature;->modulePath:Ljava/lang/String;

    sget-boolean v0, Lwex/ui/core/feature/TopBarFeature;->resumeHookRegistered:Z

    if-nez v0, :goto_0

    :try_start_0
    const-class p1, Landroid/app/Activity;

    const-string p2, "onResume"

    new-instance v0, Lwex/ui/core/feature/TopBarHomeHook;

    invoke-direct {v0}, Lwex/ui/core/feature/TopBarHomeHook;-><init>()V

    filled-new-array {v0}, [Ljava/lang/Object;

    move-result-object v0

    invoke-static {p1, p2, v0}, Ldev/mindlink/xposed/XposedHelpers;->findAndHookMethod(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Ldev/mindlink/xposed/XC_MethodHook$Unhook;

    const/4 v0, 0x1

    sput-boolean v0, Lwex/ui/core/feature/TopBarFeature;->resumeHookRegistered:Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    goto :goto_0

    :catchall_0
    move-exception p1

    invoke-virtual {p1}, Ljava/lang/Throwable;->getMessage()Ljava/lang/String;

    move-result-object p1

    const-string p2, "\u9876\u680f\u6539\u9020 Hook \u5931\u8d25: "

    invoke-static {p2, p1}, Landroidx/activity/b;->m(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    invoke-direct {p0, p1}, Lwex/ui/core/feature/TopBarFeature;->log(Ljava/lang/String;)V

    :goto_0
    sget-boolean v0, Lwex/ui/core/feature/TopBarFeature;->titleHookRegistered:Z

    if-nez v0, :goto_1

    :try_start_1
    const-class p1, Landroid/widget/TextView;

    const-string p2, "setText"

    const-class v0, Ljava/lang/CharSequence;

    const-class v1, Landroid/widget/TextView$BufferType;

    sget-object v2, Ljava/lang/Boolean;->TYPE:Ljava/lang/Class;

    sget-object v3, Ljava/lang/Integer;->TYPE:Ljava/lang/Class;

    new-instance v4, Lwex/ui/core/feature/TopBarHomeHook;

    invoke-direct {v4}, Lwex/ui/core/feature/TopBarHomeHook;-><init>()V

    filled-new-array {v0, v1, v2, v3, v4}, [Ljava/lang/Object;

    move-result-object v0

    invoke-static {p1, p2, v0}, Ldev/mindlink/xposed/XposedHelpers;->findAndHookMethod(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Ldev/mindlink/xposed/XC_MethodHook$Unhook;

    const/4 v0, 0x1

    sput-boolean v0, Lwex/ui/core/feature/TopBarFeature;->titleHookRegistered:Z
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_1

    goto :goto_1

    :catchall_1
    move-exception p1

    invoke-virtual {p1}, Ljava/lang/Throwable;->getMessage()Ljava/lang/String;

    move-result-object p1

    const-string p2, "\u9876\u680f\u6807\u9898 setText Hook \u5931\u8d25: "

    invoke-static {p2, p1}, Landroidx/activity/b;->m(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    invoke-direct {p0, p1}, Lwex/ui/core/feature/TopBarFeature;->log(Ljava/lang/String;)V

    :goto_1
    return-void
.end method
