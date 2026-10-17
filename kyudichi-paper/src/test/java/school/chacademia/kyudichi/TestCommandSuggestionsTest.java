package school.chacademia.kyudichi;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestCommandSuggestionsTest {
    @Test void startOffersAllSupportedOptions() {
        assertEquals(List.of("준비:0", "시간:3", "스니치:0", "무적", "블러저:끔"), TestCommandSuggestions.complete(new String[]{"테스트", "arena", ""}, List.of()));
    }
    @Test void laterArgumentsStillOfferRemainingOptions() {
        assertEquals(List.of("스니치:0", "무적", "블러저:끔"), TestCommandSuggestions.complete(new String[]{"테스트", "arena", "준비:0", "시간:3", ""}, List.of()));
    }
    @Test void enteredCustomValueSuppressesSameOptionKey() {
        assertEquals(List.of(), TestCommandSuggestions.complete(new String[]{"테스트", "arena", "준비:20", "준"}, List.of()));
    }
    @Test void optionPrefixFiltersAndFlagsAreNotRepeated() {
        assertEquals(List.of("블러저:끔"), TestCommandSuggestions.complete(new String[]{"테스트", "arena", "무적", "블"}, List.of()));
        assertFalse(TestCommandSuggestions.complete(new String[]{"테스트", "arena", "무적", ""}, List.of()).contains("무적"));
    }
    @Test void joinCompletesArenaThenTeamAndStops() {
        assertEquals(List.of("green"), TestCommandSuggestions.complete(new String[]{"테스트", "참가", "gr"}, List.of("green", "blue")));
        assertEquals(List.of("B"), TestCommandSuggestions.complete(new String[]{"테스트", "참가", "green", "B"}, List.of("green")));
        assertEquals(List.of(), TestCommandSuggestions.complete(new String[]{"테스트", "참가", "green", "B", ""}, List.of("green")));
    }
    @Test void endCompletesArenaWithoutStartOptions() {
        assertEquals(List.of("green"), TestCommandSuggestions.complete(new String[]{"테스트", "종료", "g"}, List.of("green")));
        assertEquals(List.of(), TestCommandSuggestions.complete(new String[]{"테스트", "종료", "green", ""}, List.of("green")));
    }
}
