package school.magiccodex.paper;

/** World full-time clock. Sleep advances seasons; time moving backwards preserves progress. */
final class SeasonClock {
    long anchor,last;
    int start;
    boolean automatic;
    final long length;
    SeasonClock(long anchor,int start,boolean automatic,long length,long now){
        if(start<0||start>3||length<24000)throw new IllegalArgumentException("Invalid season clock");
        this.anchor=anchor;this.start=start;this.automatic=automatic;this.length=length;this.last=now;
    }
    void observe(long now){if(now<last)anchor-=last-now;last=now;}
    int season(){return automatic?(int)((start+Math.max(0,last-anchor)/length)%4):start;}
    int day(){return automatic?(int)((Math.max(0,last-anchor)%length)/24000)+1:1;}
    void set(int season,long now,boolean automatic){this.start=season;this.anchor=now;this.last=now;this.automatic=automatic;}
}
