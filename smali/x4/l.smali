.class public final synthetic Lx4/l;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Ljava/lang/Runnable;

# instance fields
.field public final synthetic c:I

.field public synthetic d:Landroid/view/View;

# direct methods
.method public synthetic constructor <init>(I)V
    .locals 0

    iput p1, p0, Lx4/l;->c:I

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

# virtual methods
.method public final run()V
    .locals 1

    iget v0, p0, Lx4/l;->c:I

    iget-object p0, p0, Lx4/l;->d:Landroid/view/View;

    packed-switch v0, :pswitch_data_0

    invoke-static {p0}, Lwex/ui/core/feature/MyPageProfileFeature;->a(Landroid/view/View;)V

    return-void

    :pswitch_0
    invoke-static {p0}, Lwex/ui/core/feature/ChatTopBarTransparent;->d(Landroid/view/View;)V

    return-void

    :pswitch_1
    invoke-static {p0}, Lwex/ui/core/feature/ChatTopBarTransparent;->b(Landroid/view/View;)V

    return-void

    :pswitch_2
    invoke-static {p0}, Lwex/ui/core/feature/CalendarStyleHuangli;->a(Landroid/view/View;)V

    return-void

    :pswitch_3
    invoke-static {p0}, Lwex/ui/core/feature/BubbleApply;->apply(Landroid/view/View;)Z

    return-void

    :pswitch_4
    sget-object v0, Lwex/ui/core/feature/BottomBarComposeFeature;->INSTANCE:Lwex/ui/core/feature/BottomBarComposeFeature;

    invoke-static {v0, p0}, Lwex/ui/core/feature/BottomBarComposeFeature;->access$apply(Lwex/ui/core/feature/BottomBarComposeFeature;Landroid/view/View;)V

    return-void

    :pswitch_data_0
    .packed-switch 0x0
        :pswitch_4
        :pswitch_3
        :pswitch_2
        :pswitch_1
        :pswitch_0
    .end packed-switch
.end method
