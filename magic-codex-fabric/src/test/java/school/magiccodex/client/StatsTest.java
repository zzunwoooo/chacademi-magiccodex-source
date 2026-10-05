package school.magiccodex.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatsTest {
    @Test void socialActionUsesExplicitHookAndNeverPretendsToSucceed(){
        var id=java.util.UUID.randomUUID();StatsSocialActions.reset();
        assertTrue(StatsSocialActions.activate(StatsSocialActions.Action.FRIEND,id).contains("아직 연결"));
        assertTrue(StatsSocialActions.activate(StatsSocialActions.Action.POPULARITY,id).contains("아직 연결"));
        try{
            StatsSocialActions.connect((action,target)->{assertEquals(id,target);return action.name();});
            assertEquals("POPULARITY",StatsSocialActions.activate(StatsSocialActions.Action.POPULARITY,id));
        }finally{StatsSocialActions.reset();}
    }
    @Test void unavailableValuesNeverBecomeZeroOrNan(){
        assertEquals("—",StatsValues.number(null));
        assertEquals("—",StatsValues.number(Double.NaN));
        assertEquals("—",StatsValues.number(Double.POSITIVE_INFINITY));
        assertEquals("100",StatsValues.number(100.0));
        assertEquals("5.3",StatsValues.number(5.25));
        assertEquals("-3",StatsValues.number(-3.0));
        assertEquals(0,StatsValues.fraction(null,100.0));
        assertEquals(0,StatsValues.fraction(50.0,0.0));
        assertEquals(0,StatsValues.fraction(Double.NaN,100.0));
        assertEquals(1,StatsValues.fraction(120.0,100.0));
        assertEquals(.5f,StatsValues.fraction(50.0,100.0));
    }
    @Test void layoutKeepsCloseTargetInsideImageAcrossWindowShapes(){
        for(int[] size:new int[][]{{320,180},{640,360},{854,480},{1920,1080},{800,1000},{3440,1440}}){
            var l=StatsLayout.fit(size[0],size[1]);
            assertTrue(l.x()>0&&l.y()>0);
            assertTrue(l.x()+1448*l.scale()<size[0]);
            assertTrue(l.y()+1086*l.scale()<size[1]);
            double x=l.x()+StatsLayout.CLOSE.centerX()*l.scale();
            double y=l.y()+StatsLayout.CLOSE.centerY()*l.scale();
            assertTrue(StatsLayout.CLOSE.contains(l.localX(x),l.localY(y)));
            for(var r:java.util.List.of(StatsLayout.FRIEND,StatsLayout.POPULARITY)){
                assertTrue(r.contains(l.localX(l.x()+r.centerX()*l.scale()),l.localY(l.y()+r.centerY()*l.scale())));
                assertTrue(r.y()+r.height()<754);
                assertTrue(StatsLayout.socialContains(r,r.centerX(),r.centerY()));
                assertFalse(StatsLayout.socialContains(r,r.x(),r.y()));
            }
            assertFalse(StatsLayout.FRIEND.contains(StatsLayout.POPULARITY.centerX(),StatsLayout.POPULARITY.centerY()));
        }
    }
    @Test void exactClassCountBoundedMotionAndFrontBackTransitions(){
        var s=new StatsStars();boolean front=false,back=false;
        for(int rank=1;rank<=9;rank++)for(int step=0;step<600;step++){
            s.update(rank,step*.2);assertEquals(rank,s.count());
            for(int i=0;i<s.count();i++){
                assertTrue(Math.abs(s.x(i))<=1);assertTrue(Math.abs(s.y(i))<=1);
                assertTrue(Math.abs(s.depth(i))<=1);
                front|=s.foreground(i);back|=!s.foreground(i);
            }
        }
        assertTrue(front&&back);
        s.update(5,9);float x=s.x(0),y=s.y(0);
        s.update(5,9.001);assertTrue(Math.abs(x-s.x(0))<.002&&Math.abs(y-s.y(0))<.002);
    }
}
