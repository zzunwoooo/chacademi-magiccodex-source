package school.magiccodex.client;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import school.magiccodex.protocol.WalletProtocol;
import static org.junit.jupiter.api.Assertions.*;

class TopMenuTest {
    @Test void collapsedAndPartiallyRevealedIconsCannotBeClicked(){
        var state=new TopMenuState();
        assertEquals(-1,state.hit(178,18,198));
        assertEquals(-2,state.hit(223,18,198));
        state.toggle(1000);state.update(1120);
        assertEquals(.5f,state.progress(),.0001f);
        assertEquals(-2,state.hit(state.iconCenter(198,1),18,198));
        state.update(1240);
        for(int i=0;i<TopMenuState.LABELS.length;i++)assertEquals(i,state.hit(state.iconCenter(198,i),18,198));
        assertEquals(-2,state.hit(200,40,198));
    }
    @Test void reversingSlideDoesNotJumpAndClosesCompletely(){
        var state=new TopMenuState();state.toggle(1000);state.update(1090);
        float before=state.progress();state.toggle(1090);
        assertEquals(before,state.progress());
        state.update(1330);assertEquals(0,state.progress());assertFalse(state.expanded());
        assertEquals(198,state.width(198));
    }
    @Test void wideBalanceDoesNotShiftMenuHitTargetsAwayFromIcons(){
        var state=new TopMenuState();state.toggle(0);state.update(240);
        for(float moneyWidth:new float[]{0,45,87,138,500}){
            float base=TopMenuState.baseWidth(moneyWidth);
            // The season area must never trigger the shifted menu or expansion arrow.
            assertEquals(-2,state.hit(TopMenuState.SEASON_CENTER,18,base));
            assertTrue(TopMenuState.seasonHovered(TopMenuState.SEASON_CENTER,18));
            assertFalse(TopMenuState.seasonHovered(53,18));
            for(int i=0;i<TopMenuState.LABELS.length;i++)assertEquals(i,state.hit(state.iconCenter(base,i),18,base));
            assertEquals(-1,state.hit(state.arrowCenter(base),18,base));
            assertEquals(base+TopMenuState.LABELS.length*TopMenuState.STEP,state.width(base));
            assertTrue(state.arrowCenter(base)-state.iconCenter(base,TopMenuState.LABELS.length-1)>29);
        }
        assertEquals("캐시샵",TopMenuState.command(0));assertEquals("우편함",TopMenuState.command(1));
        assertEquals("",TopMenuState.command(2));assertEquals("친구",TopMenuState.command(3));
        assertEquals("스텟창",TopMenuState.command(4));assertEquals("펫도감",TopMenuState.command(5));
        assertEquals("학교기증",TopMenuState.command(6));
    }
    @Test void walletProtocolIsFixedSizeAndRejectsInvalidValues(){
        var snapshot=new WalletProtocol.Snapshot(true,12450.25);
        assertEquals(snapshot,WalletProtocol.decode(WalletProtocol.encode(snapshot)));
        assertTrue(WalletProtocol.validRequest(WalletProtocol.request()));
        assertFalse(WalletProtocol.validRequest(new byte[5]));
        assertFalse(WalletProtocol.validRequest(new byte[4]));
        assertThrows(IllegalArgumentException.class,()->WalletProtocol.decode(new byte[13]));
        byte[] packet=WalletProtocol.encode(snapshot);packet[4]=7;
        assertThrows(IllegalArgumentException.class,()->WalletProtocol.decode(packet));
        assertThrows(IllegalArgumentException.class,()->WalletProtocol.decode(Arrays.copyOf(packet,14)));
        assertThrows(IllegalArgumentException.class,()->new WalletProtocol.Snapshot(true,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->new WalletProtocol.Snapshot(true,Double.POSITIVE_INFINITY));
        assertEquals(new WalletProtocol.Snapshot(true,-20),WalletProtocol.decode(WalletProtocol.encode(new WalletProtocol.Snapshot(true,-20))));
    }
}
