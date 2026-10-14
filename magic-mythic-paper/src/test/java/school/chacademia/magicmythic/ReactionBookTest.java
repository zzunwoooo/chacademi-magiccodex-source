package school.chacademia.magicmythic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import school.magiccodex.protocol.ElementProtocol;
import school.magiccodex.protocol.ElementProtocol.Element;
import school.magiccodex.protocol.ElementProtocol.Reaction;

class ReactionBookTest {
    final UUID mob = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();

    @Test void firstHitMarks() {
        var book = new ReactionBook(60, 60);
        assertEquals(new ReactionBook.Marked(Element.FIRE, 60), book.hit(mob, a, Element.FIRE, 0));
        assertEquals(Element.FIRE, book.element(mob, 59));
        assertNull(book.element(mob, 60));
    }

    @Test void otherElementInsideWindowReactsAndConsumes() {
        var book = new ReactionBook(60, 60);
        book.hit(mob, a, Element.FIRE, 0);
        var out = book.hit(mob, b, Element.WATER, 30);
        assertEquals(new ReactionBook.Reacted(Reaction.STEAM, a), out);
        assertNull(book.element(mob, 31));
    }

    @Test void expiredMarkDoesNotReact() {
        var book = new ReactionBook(60, 60);
        book.hit(mob, a, Element.FIRE, 0);
        assertInstanceOf(ReactionBook.Marked.class, book.hit(mob, a, Element.WATER, 60));
    }

    @Test void sameElementRefreshes() {
        var book = new ReactionBook(60, 60);
        book.hit(mob, a, Element.FIRE, 0);
        book.hit(mob, a, Element.FIRE, 50);
        assertEquals(Element.FIRE, book.element(mob, 100));
    }

    @Test void cooldownBlocksImmediateSecondReaction() {
        var book = new ReactionBook(60, 60);
        book.hit(mob, a, Element.FIRE, 0);
        assertInstanceOf(ReactionBook.Reacted.class, book.hit(mob, a, Element.WATER, 10));
        book.hit(mob, a, Element.LIGHT, 20);                       // during cooldown: just marks
        assertInstanceOf(ReactionBook.Marked.class, book.hit(mob, a, Element.DARK, 30));
        assertEquals(Element.DARK, book.element(mob, 31));
        assertInstanceOf(ReactionBook.Reacted.class, book.hit(mob, a, Element.LIGHT, 71)); // cooldown over (10+60)
    }

    @Test void sweepDropsStale() {
        var book = new ReactionBook(60, 60);
        book.hit(mob, a, Element.FIRE, 0);
        book.hit(UUID.randomUUID(), a, Element.EARTH, 100);
        assertEquals(1, book.sweep(100));
        assertEquals(1, book.size());
    }

    @Test void everyPairHasExactlyOneReaction() {
        int pairs = 0;
        for (Element x : Element.values()) for (Element y : Element.values()) {
            if (x == y) { assertNull(Reaction.of(x, y)); continue; }
            assertNotNull(Reaction.of(x, y));
            assertEquals(Reaction.of(x, y), Reaction.of(y, x));
            pairs++;
        }
        assertEquals(30, pairs);
        assertEquals(15, Reaction.values().length);
    }

    @Test void elementIdsMatchCatalogueCategories() {
        for (String id : new String[]{"fire", "water", "wind", "earth", "light", "dark"}) assertNotNull(Element.of(id));
        assertNull(Element.of(""));
        assertNull(Element.of("arcane"));
    }

    @Test void protocolRoundTrip() {
        var frames = new ElementProtocol.Frame[]{
            new ElementProtocol.Mark(1234, Element.DARK, 60), new ElementProtocol.Clear(-5), new ElementProtocol.Pop(42, Reaction.ECLIPSE)};
        for (var f : frames) {
            byte[] raw = ElementProtocol.encode(f);
            assertTrue(raw.length <= ElementProtocol.MAX_BYTES);
            assertEquals(f, ElementProtocol.decode(raw));
        }
        assertThrows(IllegalArgumentException.class, () -> ElementProtocol.decode(new byte[]{2, 0, 0, 0, 0, 0}));
        assertThrows(IllegalArgumentException.class, () -> ElementProtocol.decode(new byte[]{1, 0, 0, 0, 0, 1, 9, 0, 60}));
    }
}
