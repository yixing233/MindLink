.class public final Lwex/ui/core/feature/ConversationFilterFeature;
.super Ljava/lang/Object;
.source "SourceFile"

# 固定四分类已升级为可编辑用户分组（ConversationGroupingFeature）。
# 本类保留为兼容入口：init 立即转发新实现，历史配置读取由新功能负责。

.field public static final INSTANCE:Lwex/ui/core/feature/ConversationFilterFeature;

.method static constructor <clinit>()V
    .locals 1
    new-instance v0, Lwex/ui/core/feature/ConversationFilterFeature;
    invoke-direct {v0}, Lwex/ui/core/feature/ConversationFilterFeature;-><init>()V
    sput-object v0, Lwex/ui/core/feature/ConversationFilterFeature;->INSTANCE:Lwex/ui/core/feature/ConversationFilterFeature;
    return-void
.end method

.method private constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public final init(Lde/robv/android/xposed/callbacks/XC_LoadPackage$LoadPackageParam;)V
    .locals 1
    sget-object v0, Lwex/ui/core/feature/ConversationGroupingFeature;->INSTANCE:Lwex/ui/core/feature/ConversationGroupingFeature;
    invoke-virtual {v0, p1}, Lwex/ui/core/feature/ConversationGroupingFeature;->init(Lde/robv/android/xposed/callbacks/XC_LoadPackage$LoadPackageParam;)V
    return-void
.end method

.method public static onHomeReady(Landroid/app/Activity;)V
    .locals 1
    sget-object v0, Lwex/ui/core/feature/ConversationGroupingFeature;->INSTANCE:Lwex/ui/core/feature/ConversationGroupingFeature;
    invoke-virtual {v0, p0}, Lwex/ui/core/feature/ConversationGroupingFeature;->onHomeReady(Landroid/app/Activity;)V
    return-void
.end method

.method public static a()V
    .locals 0
    return-void
.end method

.method public static b(ILjava/util/ArrayList;Landroid/view/View;)V
    .locals 0
    return-void
.end method

.method public static releaseList(Landroid/widget/ListView;)V
    .locals 1
    sget-object v0, Lwex/ui/core/feature/ConversationGroupingFeature;->INSTANCE:Lwex/ui/core/feature/ConversationGroupingFeature;
    invoke-virtual {v0, p0}, Lwex/ui/core/feature/ConversationGroupingFeature;->releaseList(Landroid/widget/ListView;)V
    return-void
.end method
