package wex.ui.core.feature;

/** Only the account contract is doubled; all status behavior is the production class. */
final class SelfProfile {
    static String account = "wxid_A";
    static String confirmed = "";
    static boolean databaseReady;
    static String wxid() { return account; }
    static boolean statusAccountReady(String expected) {
        return databaseReady && expected != null && !expected.isEmpty()
                && expected.equals(account) && expected.equals(confirmed);
    }
    static void ready(String value) { account = value; confirmed = value; databaseReady = true; }
}
