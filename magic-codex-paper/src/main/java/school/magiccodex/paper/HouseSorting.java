package school.magiccodex.paper;

import java.util.*;

/** Pre-assignment population ceiling; declaration order is the final deterministic tie break. */
final class HouseSorting {
    static final List<String> IDS=List.of("arkeon","lumina","bestiaz","noxer");
    static final int MAX_TOLERANCE=1_000_000;
    static void tolerance(int value){if(value<0||value>MAX_TOLERANCE)throw new IllegalArgumentException("허용치는 0~1000000 정수입니다.");}
    static int[] scores(int t,int d){
        if(t<0||t>3||d<0||d>3)throw new IllegalArgumentException("T,D는 각각 0~3입니다.");
        return new int[]{t+d,t+3-d,3-t+d,6-t-d};
    }
    static int preferred(int t,int d){int[] s=scores(t,d);int best=0;for(int i=1;i<4;i++)if(s[i]>s[best])best=i;return best;}
    static int choose(int t,int d,long[] counts,int tolerance){
        tolerance(tolerance);if(counts.length!=4||Arrays.stream(counts).anyMatch(n->n<0))throw new IllegalArgumentException("House counts");
        int[] scores=scores(t,d);long min=Arrays.stream(counts).min().orElseThrow();
        return java.util.stream.IntStream.range(0,4).boxed()
            .sorted(Comparator.<Integer>comparingInt(i->scores[i]).reversed().thenComparingLong(i->counts[i]).thenComparingInt(i->i))
            .filter(i->counts[i]-min<=tolerance).findFirst().orElseThrow();
    }
}
