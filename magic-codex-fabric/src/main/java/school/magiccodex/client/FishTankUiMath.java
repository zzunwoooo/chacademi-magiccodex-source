package school.magiccodex.client;
/** The approved tank camera equations, separated from Minecraft for scale/transition tests. */
final class FishTankUiMath {
 record Magnification(float zoom,float presentation){}
 static Magnification magnification(float shown){float zoom=Math.clamp(380/shown,1.3f,2.2f);return new Magnification(zoom,Math.clamp(380/(shown*zoom),1f,2.4f));}
 static boolean fresh(float progress,String selected){return progress<=.001f||selected==null;}
 static float ease(float t){t=Math.clamp(t,0,1);return t*t*t*(t*(t*6-15)+10);}
 static float progress(float value,boolean selected,float dt){float target=selected?1:0;float rate=selected?1/.85f:1/.7f;return target>value?Math.min(target,value+dt*rate):Math.max(target,value-dt*rate);}
 static float blend(float dt,float rate){return 1-(float)Math.exp(-dt*rate);}
 static float local(float world,float camera,float zoom,float center){return (world-camera)*zoom+center;}
 static float world(double local,float camera,float zoom,float center){return (float)((local-center)/zoom+camera);}
}
