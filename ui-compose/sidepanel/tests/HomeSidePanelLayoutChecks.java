package wex.ui.core.feature;

import java.util.ArrayList;
import java.util.List;

/** Real production validation/copy/codec; fixtures replace JSON plumbing only, never its rules. */
public final class HomeSidePanelLayoutChecks {
    private static int assertions;
    private static void verify(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        assertions++;
    }
    private static HomeSidePanelLayout.Card quote(String categories, Integer min, Integer max) {
        HomeSidePanelLayout.Card card = HomeSidePanelLayout.card(HomeSidePanelLayout.CardType.HITOKOTO, false);
        card.id = "original-quote";
        card.hitokotoCategories = categories;
        card.hitokotoMinLength = min;
        card.hitokotoMaxLength = max;
        card.showHitokotoSource = false;
        return card;
    }
    private static List<HomeSidePanelLayout.Card> layout(HomeSidePanelLayout.Card quote) {
        List<HomeSidePanelLayout.Card> cards = new ArrayList<>();
        HomeSidePanelLayout.Card date = HomeSidePanelLayout.card(HomeSidePanelLayout.CardType.DATE_TIME, true);
        date.id = "original-date";
        cards.add(date);
        cards.add(quote);
        return cards;
    }
    private static void invalid(String categories, Integer min, Integer max, String reason) throws Exception {
        HomeSidePanelLayout.Card card = quote(categories, min, max);
        List<HomeSidePanelLayout.Card> cards = layout(card);
        try {
            HomeSidePanelLayout.validate(cards);
            throw new AssertionError("Invalid settings accepted");
        } catch (IllegalArgumentException error) {
            verify(error.getMessage().contains(reason), "Clear original-rule validation error");
        }
        try {
            HomeSidePanelLayout.encode(cards);
            throw new AssertionError("Invalid layout encoded for save");
        } catch (IllegalArgumentException error) {
            verify(error.getMessage().contains("原配置未改变"), "Failed save promises no config change");
        }
        List<HomeSidePanelLayout.Card> copied = HomeSidePanelLayout.copy(cards);
        verify(copied.size() == 2 && copied.get(0).id.equals("original-date") && copied.get(1).id.equals("original-quote"), "Old invalid layout remains copyable without defaults");
        verify(copied.get(1) != card, "Copy is not live editor model");
        verify(java.util.Objects.equals(copied.get(1).hitokotoCategories, categories == null ? "" : categories), "Original category not rewritten");
        verify(java.util.Objects.equals(copied.get(1).hitokotoMinLength, min), "Original min retained");
        verify(java.util.Objects.equals(copied.get(1).hitokotoMaxLength, max), "Original max retained");
        verify(!copied.get(1).showHitokotoSource && copied.get(0).showLunarCalendar, "Other settings preserved");
        verify(java.util.Objects.equals(card.hitokotoCategories, categories) && java.util.Objects.equals(card.hitokotoMinLength, min), "Validation does not mutate input");
    }
    public static void main(String[] args) throws Exception {
        invalid("z", null, null, "不支持");
        invalid("a", -1, 8, "不能为负数");
        invalid("a", 0, -1, "不能为负数");
        invalid("a,b", 9, 2, "最大长度");
        invalid(", ， ", null, null, "至少选择");
        for (String categories : new String[]{null, "", "a", "a,b", "a，b c", "a,a,l"}) {
            List<HomeSidePanelLayout.Card> cards = layout(quote(categories, 0, 8));
            HomeSidePanelLayout.validate(cards);
            List<HomeSidePanelLayout.Card> decoded = HomeSidePanelLayout.decode(HomeSidePanelLayout.encode(cards));
            verify(decoded.size() == 2 && decoded.get(1).id.equals("original-quote"), "Valid saved IDs preserved");
        }
        HomeSidePanelLayout.Card nonQuote = HomeSidePanelLayout.card(HomeSidePanelLayout.CardType.DATE_TIME, false);
        nonQuote.hitokotoCategories = "z";
        HomeSidePanelLayout.validate(java.util.Collections.singletonList(nonQuote));
        verify(true, "Only active one-line card fields validated");
        System.out.println("PASS: dedicated HomeSidePanelLayout checks (" + assertions + " assertions, real Java production; JSON platform fixture, no rendering)");
    }
}
