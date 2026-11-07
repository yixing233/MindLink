.class public final synthetic Lx4/c1;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Landroid/view/View$OnClickListener;


# instance fields
.field public final synthetic a:I

.field public synthetic b:Lwex/ui/core/feature/MusicCardFeature$MusicItem;


# direct methods
.method public synthetic constructor <init>(I)V
    .locals 0

    iput p1, p0, Lx4/c1;->a:I

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final onClick(Landroid/view/View;)V
    .locals 1

    iget v0, p0, Lx4/c1;->a:I

    iget-object p0, p0, Lx4/c1;->b:Lwex/ui/core/feature/MusicCardFeature$MusicItem;

    packed-switch v0, :pswitch_data_0

    invoke-static {p0, p1}, Lwex/ui/core/feature/MusicPanels;->n(Lwex/ui/core/feature/MusicCardFeature$MusicItem;Landroid/view/View;)V

    return-void

    :pswitch_0
    invoke-static {p0, p1}, Lwex/ui/core/feature/MusicPanels;->d(Lwex/ui/core/feature/MusicCardFeature$MusicItem;Landroid/view/View;)V

    return-void

    nop

    :pswitch_data_0
    .packed-switch 0x0
        :pswitch_0
    .end packed-switch
.end method
