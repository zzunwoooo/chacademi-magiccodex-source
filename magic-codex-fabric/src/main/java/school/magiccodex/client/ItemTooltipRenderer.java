package school.magiccodex.client;

import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.*;
import net.minecraft.util.*;

/** Client-only item tooltips: preserve vanilla rich information, lift only the first lore line. */
public final class ItemTooltipRenderer {
    private static final Identifier PANEL=Identifier.of("magiccodex","textures/gui/tooltip/tooltip-panel-v2.png");
    private static final Identifier ORBIT=Identifier.of("magiccodex","textures/gui/tooltip/item-orbit.png");
    private static ItemStack cached=ItemStack.EMPTY;
    private static List<Text> lines=List.of();
    private static List<OrderedText> title=List.of(),type=List.of(),body=List.of();
    private static int cachedWidth=-1,epoch=-1,offset,maxScroll;
    private static long lastRender;
    private static Screen owner;
    private static final Map<String,String> korean=new HashMap<>();
    private static boolean koreanLoaded;
    private ItemTooltipRenderer(){}
    static boolean renderedOn(Screen screen){return owner==screen&&Util.getMeasuringTimeMs()-lastRender<500;}
    static void reset(){korean.clear();koreanLoaded=false;cached=ItemStack.EMPTY;owner=null;lines=List.of();title=List.of();type=List.of();body=List.of();offset=maxScroll=0;lastRender=0;epoch=-1;}
    public static boolean render(DrawContext c,TextRenderer font,ItemStack stack,int mouseX,int mouseY){
        return render(c,font,stack,mouseX,mouseY,Screen.getTooltipFromItem(MinecraftClient.getInstance(),stack));
    }
    private static final float TOOLTIP_SCALE=.85f;
    public static boolean render(DrawContext c,TextRenderer font,ItemStack stack,int mouseX,int mouseY,List<Text> info){
        c.getMatrices().push();
        try{
            c.getMatrices().scale(TOOLTIP_SCALE,TOOLTIP_SCALE,1);
            return renderScaled(c,font,stack,Math.round(mouseX/TOOLTIP_SCALE),Math.round(mouseY/TOOLTIP_SCALE),info);
        }finally{c.getMatrices().pop();}
    }
    private static boolean renderScaled(DrawContext c,TextRenderer font,ItemStack stack,int mouseX,int mouseY,List<Text> info){
        var client=MinecraftClient.getInstance();
        if(stack.isEmpty()||client.currentScreen==null)return false;
        // Bundle/container visual payloads keep their vanilla interactive preview.
        if(stack.getTooltipData().isPresent())return false;
        if(info.isEmpty())return false; // Respect hide-tooltip components.
        int sw=(int)(c.getScaledWindowWidth()/TOOLTIP_SCALE),sh=(int)(c.getScaledWindowHeight()/TOOLTIP_SCALE);
        if(sw<150||sh<130)return false;
        int w=Math.min(info.size()==1?124:148,sw-12),textWidth=w-20;
        long now=Util.getMeasuringTimeMs();
        boolean changed=owner!=client.currentScreen||!ItemStack.areItemsAndComponentsEqual(stack,cached)||now-lastRender>250;
        if(changed||cachedWidth!=w||epoch!=UiResources.generation()||!lines.equals(info)){
            cached=stack.copy();owner=client.currentScreen;cachedWidth=w;epoch=UiResources.generation();lines=List.copyOf(info);
            if(changed)offset=0;
            title=font.wrapLines(styled(localName(stack,info.getFirst()),"tooltip_name"),textWidth);
            var rest=new ArrayList<>(info.subList(1,info.size()));
            var lore=stack.get(DataComponentTypes.LORE);Text itemType=null;
            if(lore!=null&&!lore.styledLines().isEmpty()){
                Text first=lore.styledLines().getFirst();
                // Match the full styled lore sequence, avoiding identical enchantment/attribute text.
                int index=Collections.indexOfSubList(rest,lore.styledLines());
                if(index>=0){
                    itemType=lore.lines().getFirst();
                    for(int i=0;i<lore.lines().size();i++){
                        Text raw=lore.lines().get(i);rest.set(index+i,raw.copy().setStyle(raw.getStyle().withItalic(raw.getStyle().isItalic())));
                    }
                    rest.remove(index);
                }
            }
            if(WandTooltip.matches(stack))rest.removeIf(line->line.getString().startsWith("[강화] 사용 횟수 ")||line.getString().startsWith("[강화] 마력 "));
            type=itemType==null||itemType.getString().isBlank()?List.of():font.wrapLines(styled(itemType,"tooltip_type"),textWidth);
            var wrapped=new ArrayList<OrderedText>();
            for(Text line:rest){if(line.getString().isEmpty())wrapped.add(OrderedText.EMPTY);else wrapped.addAll(font.wrapLines(styled(line,"tooltip"),textWidth));}
            body=List.copyOf(wrapped);
        }
        lastRender=now;
        if(ManaCoreTooltip.matches(stack)){
            int content=ManaCoreTooltip.height(title,type,body)+58;
            int h=Math.min(sh-12,content),view=h-12;
            maxScroll=Math.max(0,content-h);offset=Math.clamp(offset,0,maxScroll);
            int x=mouseX+12;if(x+w>sw-6)x=mouseX-w-12;x=Math.clamp(x,6,sw-w-6);
            int y=Math.clamp(mouseY-h/2,6,sh-h-6);
            var images=UiResources.images();images.beginFrame();c.getMatrices().push();
            try{
                c.getMatrices().translate(0,0,600);panel(c,images,x,y,w,h);
                c.enableScissor(x+3,y+4,x+w-3,y+h-4);
                try{itemPreview(c,images,stack,x,y-offset,w,now);ManaCoreTooltip.render(c,font,x,y-offset+48,w,title,type,body,now,stack);c.draw();}
                finally{c.disableScissor();}
                if(maxScroll>0){c.fill(x+w-5,y+6,x+w-4,y+h-6,0x403A667F);int thumb=Math.max(8,view*view/content);int py=y+6+(view-thumb)*offset/maxScroll;c.fill(x+w-5,py,x+w-4,py+thumb,0xFFA8CFD5);}
            }finally{c.getMatrices().pop();images.endFrame();}
            return true;
        }
        boolean wand=WandTooltip.matches(stack);
        int preview=wand?76:60,header=preview+title.size()*10+(type.isEmpty()?0:type.size()*10)+12+(wand?55:0);
        // For exceptionally long names all text joins the scrollable region rather than escaping the window.
        boolean compact=header>sh-60;
        List<OrderedText> displayed=body;
        if(compact){var all=new ArrayList<OrderedText>(title);all.addAll(type);all.addAll(body);displayed=all;header=preview+12;}
        int contentHeight=displayed.size()*11;
        int h=Math.min(Math.min(sh-12,240),header+(displayed.isEmpty()?0:17+contentHeight)+(wand&&displayed.isEmpty()?2:10));
        int available=Math.max(0,h-header-25);
        if(contentHeight>available)available=Math.max(0,available-12);
        maxScroll=Math.max(0,contentHeight-available);offset=Math.clamp(offset,0,maxScroll);
        int x=mouseX+12;if(x+w>sw-6)x=mouseX-w-12;x=Math.clamp(x,6,sw-w-6);
        int y=Math.clamp(mouseY-h/2,6,sh-h-6);
        var images=UiResources.images();images.beginFrame();
        c.getMatrices().push();
        try{
            c.getMatrices().translate(0,0,600);
            // Flat fallback guarantees readability while the async PNG slices warm up.
            panel(c,images,x,y,w,h);
            if(images.missedThisFrame()){
                c.fill(x+14,y+5,x+w-14,y+h-5,0xD8091A2B);
                c.fill(x+3,y+15,x+10,y+h-15,0xD8091A2B);
                c.fill(x+w-14,y+15,x+w-3,y+h-15,0xD8091A2B);
            }
            itemPreview(c,images,stack,x,y,w,now);
            if(wand)WandTooltip.remaining(c,font,stack,x,y+56,w);
            int ty=y+preview;
            if(!compact){
                for(var line:title){c.drawText(font,line,x+10,ty,0xFFEBD19F,false);ty+=10;}
                if(!type.isEmpty()){for(var line:type){c.drawText(font,line,x+10,ty,0xFF9DA5B2,false);ty+=10;}}
            }
            if(wand&&!compact){WandTooltip.stats(c,font,stack,x,ty+9,w);divider(c,x+10,y+header-9,w-20);}
            if(!displayed.isEmpty()){
                int section=y+header;
                if(!wand||compact)divider(c,x+10,section-9,w-20);
                Text label=styled(Text.literal("아이템 설명"),"tooltip_section");
                c.drawText(font,label,x+10,section,0xFFEBD19F,false);
                int top=section+13;
                c.enableScissor(x+8,top-4,x+w-8,top+available);
                try{for(int i=Math.max(0,offset/11);i<displayed.size()&&i*11-offset<available;i++)c.drawText(font,displayed.get(i),x+10,top+i*11-offset,0xFFB7BDC8,false);}
                finally{c.disableScissor();}
                if(maxScroll>0){
                    c.fill(x+w-7,top,x+w-6,top+available,0x403A667F);
                    int thumb=Math.max(8,available*available/Math.max(1,contentHeight));
                    int pos=top+(available-thumb)*offset/Math.max(1,maxScroll);
                    c.fill(x+w-7,pos,x+w-6,pos+thumb,0xFFA8CFD5);
                    c.drawText(font,styled(Text.literal("Shift + 휠 · 설명 스크롤"),"tooltip"),x+10,y+h-15,0xFF9DB4C6,false);
                }
            }
            c.draw();
        }finally{c.getMatrices().pop();images.endFrame();}
        return true;
    }
    static Text localName(ItemStack stack,Text fallback){
        if(stack.contains(DataComponentTypes.CUSTOM_NAME)||!Objects.equals(stack.get(DataComponentTypes.ITEM_NAME),stack.getItem().getDefaultStack().get(DataComponentTypes.ITEM_NAME)))return fallback;
        if(!koreanLoaded){
            koreanLoaded=true;
            var resource=MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of("minecraft","lang/ko_kr.json"));
            if(resource.isPresent())try(var reader=resource.get().getReader()){
                com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e->korean.put(e.getKey(),e.getValue().getAsString()));
            }catch(Exception ignored){}
        }
        String value=korean.get(stack.getItem().getTranslationKey());
        return value==null?fallback:Text.literal(value).setStyle(fallback.getStyle());
    }
    private static void itemPreview(DrawContext c,HudTextureCache images,ItemStack stack,int x,int y,int w,long now){
            galaxy(c,x+10,y+8,w-20,45,now);
            images.drawTexture(c,ORBIT,x+w/2-43,y+23,80,190,86,25,1610,485,1774,887,0x40FFFFFF);
            c.draw();
            c.getMatrices().push();
            c.getMatrices().translate(x+w/2f,y+31,0);
            // A restrained oscillating sprite turn avoids exposing an empty back face.
            float angle=(float)Math.sin(now/1800.0)*.16f;
            c.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(angle));
            c.getMatrices().scale(2.2f*(.86f+.14f*(float)Math.cos(now/2100.0)),2.2f,1);
            c.drawItem(stack,-8,-8);c.draw();c.getMatrices().pop();
    }
    private static void galaxy(DrawContext c,int x,int y,int w,int h,long now){
        double t=now/5500.0;
        // Soft overlapping dust, moving at different speeds; no texture uploads per frame.
        for(int layer=0;layer<3;layer++)for(int i=0;i<38;i++){
            float u=i/37f;
            float px=x+u*w;
            float py=y+h*.5f+(float)(Math.sin(u*5.5+t+layer*.65)*7+Math.sin(u*11-t*.6)*2);
            int a=(int)(Math.sin(u*Math.PI)*(layer==0?5:3));
            HudMesh.disk(c,px,py,9-layer*2,(a<<24)|(layer==1?0x938ACF:0x65ABC5));
        }
        for(int i=0;i<15;i++){
            float u=(float)((i*.61803398875+t*.025)%1);
            float px=x+u*w,py=y+8+(float)((Math.sin(i*2.37+t*(.2+i*.012))*.5+.5)*(h-16));
            int a=(int)((55+45*Math.sin(t*1.7+i))*Math.sin(u*Math.PI));
            HudMesh.star(c,px,py,i%5==0?1.1f:.55f,(a<<24)|0xC7E6F1);
        }
    }
    private static Text styled(Text text,String requested){
        var result=Text.empty();
        text.visit((style,value)->{
            var face=style.getFont().equals(Style.DEFAULT_FONT_ID)?Identifier.of("magiccodex",requested):style.getFont();
            int color=requested.equals("tooltip_name")||requested.equals("tooltip_section")?0xEBD19F:requested.equals("tooltip_type")?0x9DA5B2:0xB7BDC8;
            result.append(Text.literal(value).setStyle(style.withFont(face).withBold(false).withItalic(false).withColor(color)));
            return Optional.empty();
        },Style.EMPTY);
        return result;
    }
    private static void divider(DrawContext c,int x,int y,int width){
        int middle=x+width/2;
        HudMesh.line(c,x,y,middle-5,y,.5f,0xA0969081);
        HudMesh.line(c,middle+5,y,x+width,y,.5f,0xA0969081);
        HudMesh.star(c,middle,y,3,0xFFADA18C);
    }
    private static void panel(DrawContext c,HudTextureCache cache,int x,int y,int w,int h){
        c.fill(x+4,y+5,x+w-4,y+h-5,0x800B1421);
        cache.drawTexture(c,PANEL,x,y,90,43,w,10,710,50,887,1774,0xFFFFFFFF);
        cache.drawTexture(c,PANEL,x,y+10,90,800,w,h-20,710,24,887,1774,0xFFFFFFFF);
        cache.drawTexture(c,PANEL,x,y+h-10,90,1684,w,10,710,48,887,1774,0xFFFFFFFF);
    }
    static void warm(HudTextureCache c){
        ManaCoreTooltip.warm(c);
        c.prepareRegion(PANEL,90,43,710,50);c.prepareRegion(PANEL,90,800,710,24);c.prepareRegion(PANEL,90,1684,710,48);c.prepareRegion(ORBIT,80,190,1610,485);
    }
    public static boolean scroll(long window,double delta){
        var c=MinecraftClient.getInstance();
        if(window!=c.getWindow().getHandle()||c.currentScreen!=owner||c.currentScreen==null||Util.getMeasuringTimeMs()-lastRender>150||maxScroll==0||!Screen.hasShiftDown()||delta==0)return false;
        offset=Math.clamp(offset-(int)Math.copySign(24,delta),0,maxScroll);return true;
    }
}

