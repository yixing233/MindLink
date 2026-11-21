package wex.ui.core.feature;

/** Bounded, content-free runtime diagnostics for manual device feedback. */
public final class FeatureDiagnostics {
    private FeatureDiagnostics() { }

    public static String summary() {
        StringBuilder out=new StringBuilder();
        out.append("MindLink diagnostics\n");
        out.append("database=").append(HostDatabaseBridge.status()).append('\n');
        out.append("profile=").append(SelfProfile.summary()).append('\n');
        TextStatusBridge.Snapshot status=TextStatusBridge.snapshot();
        out.append("textStatus=").append(status.kind).append(" accountPresent=").append(status.account!=null&&!status.account.isEmpty()).append('\n');
        out.append("conversation=").append(ConversationListBridge.status()).append('\n');
        out.append("headerEnabled=").append(FloatingChatHeaderFeature.enabled()).append('\n');
        out.append("footerEnabled=").append(FloatingChatFooterFeature.enabled()).append(" panelAbove=").append(FloatingChatFooterFeature.panelAbove()).append('\n');
        out.append("toolbarEnabled=").append(ChatToolbarFeature.isEnabled()).append('\n');
        out.append("bubbleEnabled=").append(BubbleSettings.enabled()).append(" mode=").append(BubbleSettings.mode()).append('\n');
        return out.toString();
    }
}
