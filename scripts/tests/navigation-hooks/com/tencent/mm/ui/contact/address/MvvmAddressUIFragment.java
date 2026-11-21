package com.tencent.mm.ui.contact.address;

/** ABI and numeric id pinned by the isolated 8.0.76 host DEX metadata. */
public final class MvvmAddressUIFragment {
    public com.tencent.mm.view.recyclerview.WxRecyclerView p;
    private final android.view.View root;
    public MvvmAddressUIFragment(android.view.View root, com.tencent.mm.view.recyclerview.WxRecyclerView list) {
        this.root = root; p = list;
    }
    public android.view.View getView() { return root; }
}
