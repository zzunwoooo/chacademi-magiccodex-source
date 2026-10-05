package school.magiccodex.client;

/** Layout and animation share one coordinate system, including all hit targets. */
public final class TopMenuState {
    public static final String[] LABELS={"우편함","마법 도감","펫 도감","캐시샵","친구","퀘스트","기숙사 점수","스테이터스","장비","칭호"};
    private static final String[] COMMANDS={"우편함","","펫도감","캐시샵","친구","퀘스트","학교기증","스텟창","장비","칭호"};
    public static final float STEP=38,HEIGHT=36;
    public static final float SEASON_SPACE=28,SEASON_CENTER=28;
    public static final float TEMPERATURE_SPACE=56;
    private HudSeason season=HudSeason.SPRING;
    private boolean expanded;
    private float from,progress;
    private long started;
    public float progress(){return progress;}
    public boolean expanded(){return expanded;}
    public HudSeason season(){return season;}
    public void season(HudSeason value){season=java.util.Objects.requireNonNull(value);}
    public static float baseWidth(float moneyWidth){return 153+SEASON_SPACE+TEMPERATURE_SPACE+Math.clamp(moneyWidth,45,138);}
    public static boolean seasonHovered(float x,float y){return Math.abs(x-SEASON_CENTER)<=12 && y>=5 && y<=31;}
    public void update(long now){
        float t=Math.clamp((now-started)/240f,0,1);
        float eased=t*t*(3-2*t);
        progress=from+((expanded?1:0)-from)*eased;
    }
    public void toggle(long now){update(now);from=progress;expanded=!expanded;started=now;}
    public float width(float base){return base+STEP*LABELS.length*progress;}
    public float iconCenter(float base,int index){return base-13+STEP*index;}
    public float arrowCenter(float base){return width(base)-20;}
    /** -2 outside, -1 arrow, 0..8 menu. Hidden/transitioning items cannot activate. */
    public int hit(float x,float y,float base){
        if(y<5 || y>31)return -2;
        if(Math.abs(x-arrowCenter(base))<=13)return -1;
        if(progress<.999f || !expanded)return -2;
        for(int i=0;i<LABELS.length;i++)if(Math.abs(x-iconCenter(base,i))<=16)return i;
        return -2;
    }
    public static String command(int index){return COMMANDS[index];}
}
