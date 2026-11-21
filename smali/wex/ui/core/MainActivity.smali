.class public final Lwex/ui/core/MainActivity;
.super Landroid/app/Activity;
.source "MainActivity"

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V
    return-void
.end method

.method public onCreate(Landroid/os/Bundle;)V
    .locals 2
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V
    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
    move-result-object v0
    const/4 v1, 0x0
    invoke-virtual {v0, v1}, Landroid/view/Window;->setDecorFitsSystemWindows(Z)V
    invoke-virtual {v0, v1}, Landroid/view/Window;->setStatusBarColor(I)V
    invoke-virtual {v0, v1}, Landroid/view/Window;->setNavigationBarColor(I)V
    invoke-virtual {v0, v1}, Landroid/view/Window;->setNavigationBarContrastEnforced(Z)V
    invoke-static {p0, p1}, Lwex/ui/core/feature/AboutPageBuilder;->build(Landroid/app/Activity;Landroid/os/Bundle;)Landroid/view/View;
    move-result-object v0
    invoke-virtual {p0, v0}, Landroid/app/Activity;->setContentView(Landroid/view/View;)V
    return-void
.end method

.method protected onSaveInstanceState(Landroid/os/Bundle;)V
    .locals 0
    invoke-static {p0, p1}, Lwex/ui/core/feature/AboutPageBuilder;->save(Landroid/app/Activity;Landroid/os/Bundle;)V
    invoke-super {p0, p1}, Landroid/app/Activity;->onSaveInstanceState(Landroid/os/Bundle;)V
    return-void
.end method

.method protected onResume()V
    .locals 1
    invoke-super {p0}, Landroid/app/Activity;->onResume()V
    const/4 v0, 0x1
    invoke-static {p0, v0}, Lwex/ui/core/feature/AboutPageBuilder;->lifecycle(Landroid/app/Activity;Z)V
    return-void
.end method

.method protected onPause()V
    .locals 1
    const/4 v0, 0x0
    invoke-static {p0, v0}, Lwex/ui/core/feature/AboutPageBuilder;->lifecycle(Landroid/app/Activity;Z)V
    invoke-super {p0}, Landroid/app/Activity;->onPause()V
    return-void
.end method

.method protected onDestroy()V
    .locals 0
    invoke-static {p0}, Lwex/ui/core/feature/AboutPageBuilder;->dispose(Landroid/app/Activity;)V
    invoke-super {p0}, Landroid/app/Activity;->onDestroy()V
    return-void
.end method

.method public onBackPressed()V
    .locals 1
    invoke-static {p0}, Lwex/ui/core/feature/AboutPageBuilder;->back(Landroid/app/Activity;)Z
    move-result v0
    if-eqz v0, :desktop_back_default
    return-void
    :desktop_back_default
    invoke-super {p0}, Landroid/app/Activity;->onBackPressed()V
    return-void
.end method
