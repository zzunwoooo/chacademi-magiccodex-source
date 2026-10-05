package school.magiccodex.client;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import net.minecraft.client.sound.PositionedSoundInstance;

import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import school.magiccodex.client.CodexData.Spell;
import school.magiccodex.client.CodexHitboxes.Rect;

/**
 * Editing is local and transactional: changes take effect only after Save.
 * Keyboard-first: click or press a key to pick its spell (max 6), right-click a key or slot to clear,
 * and drag (or click two) of the six bottom slots to reorder the HUD bar.
 */
public final class KeySettingsScreen extends Screen {
    private static final Identifier BACKGROUND=id("textures/gui/key_settings_base.png");
    private static final Identifier BODY=id("label"), TITLE=id("title");
    private static final int IVORY=0xFFF0E3C8, GOLD=0xFFD0B98C, MUTED=0xFFA4ADB5, TEAL=0xFF8AD7C9;
    private final Screen parent;
    private final Supplier<List<Spell>> catalog;
    private final boolean catalogBacked;
    private final Path path;
    private SpellKeySettings saved, draft;
    private CodexTypography type;
    /** selected = slot picked for click-to-swap reordering; pickerKey = key whose spell is being chosen. */
    private int selected=-1, pickerKey=-1, page;
    private int dragSlot=-1; private double pressX,pressY; private boolean dragMoved, swallowChar;
    private String query="", notice="";
    private boolean confirmDiscard;
    private double mx,my;
    private long lastSound;
    private long savedPopupAt = -1;
    private HudTextureCache images;
    private int resourceGeneration=-1;
    private final UiResources.Entrance entrance=new UiResources.Entrance();

    public KeySettingsScreen(Screen parent) {
        this(parent,CodexCatalog::spells,true,FabricLoader.getInstance().getConfigDir().resolve("magiccodex/key-settings.yml"));
    }
    /** Development fixture uses the same event/render paths and a separate temporary settings file. */
    public KeySettingsScreen(Screen parent,Supplier<List<Spell>> catalog,boolean catalogBacked,Path path) {
        super(Text.literal("마법 키 설정")); this.parent=parent; this.catalog=catalog; this.catalogBacked=catalogBacked; this.path=path;
        try { saved=SpellKeySettings.load(path); }
        catch(Exception error) { saved=new SpellKeySettings(); notice="설정 읽기 실패 · 기존 파일은 저장 전까지 유지됩니다.";
            org.slf4j.LoggerFactory.getLogger("magiccodex").warn("키 설정 읽기 실패",error); }
        draft=saved.copy();
    }
    public boolean isCatalogBacked() { return catalogBacked; }
    public List<SpellKeySettings.Binding> bindings() { return draft.slots(); }
    public boolean pickerOpen() { return pickerKey>=0; }
    public int pickerKey() { return pickerKey; }
    private boolean waiting() { return catalogBacked && !PermissionClient.catalogConfirmed(); }
    private static Identifier id(String value) { return Identifier.of("magiccodex",value); }
    @Override protected void init() { type=UiResources.text();images=UiResources.images();resourceGeneration=UiResources.generation(); }
    private Spell spell(String id) { return catalog.get().stream().filter(s->s.id().equals(id)).findFirst().orElse(null); }
    private List<Spell> choices() {
        String needle=query.toLowerCase(Locale.ROOT);
        return catalog.get().stream().filter(s->s.permissionKnown() && s.discovered())
                .filter(s->s.name().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta) {
        if(type==null||images==null||resourceGeneration!=UiResources.generation())init();
        swallowChar=false; // Key and char events of one press are delivered before the next frame.
        type.beginFrame(); images.beginFrame(); var layout=CodexLayout.fit(width,height); mx=layout.localX(mouseX); my=layout.localY(mouseY);
        ctx.fill(0,0,width,height,0x55050B13);
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(layout.x(),layout.y(),0); ctx.getMatrices().scale(layout.scale(),layout.scale(),1);
            drawBackground(ctx);
            text(ctx,"마법 키 설정",836,102,38,IVORY,TITLE);
            text(ctx,"키보드의 키를 클릭하거나 직접 눌러 마법을 등록하세요 · 최대 6개",836,140,19,GOLD,BODY);
            for(var key:KeySettingsLayout.KEYS) drawKey(ctx,key);
            for(int i=0;i<6;i++) drawSlot(ctx,i);
            text(ctx,waiting()?"서버에서 배운 마법을 확인하고 있습니다…":!notice.isEmpty()?notice:
                    selected>=0?"순서를 바꿀 다른 칸을 클릭하세요 · 같은 칸을 다시 누르면 취소":
                    "우클릭으로 등록 해제 · 아래 칸을 끌거나 두 칸을 차례로 클릭해 순서 변경",836,789,17,GOLD,BODY);
            label(ctx,"WASD 이동   I 도감   P 스테이터스   Z 마법 모드",171,857,16,MUTED);
            text(ctx,"등록 " + draft.slots().stream().filter(b->!b.empty()).count()+" / 6",1116,857,18,GOLD,BODY);
            button(ctx,KeySettingsLayout.SAVE,draft.sameAs(saved)?"저장":"저장 *",true);
            button(ctx,KeySettingsLayout.RESET,"초기화",false);
            if(hover(KeySettingsLayout.CLOSE) && !pickerOpen() && !confirmDiscard) overlay(ctx,KeySettingsLayout.CLOSE,0x334CB2A0,0x99C9AD7A,5);
            // Typography renders at z+1. Lift the entire modal above the base text/icons.
            if(pickerOpen() || confirmDiscard) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(0,0,20);
                if(pickerOpen()) drawPicker(ctx);
                if(confirmDiscard) drawDiscard(ctx);
                ctx.getMatrices().pop();
            }
            if(!pickerOpen() && !confirmDiscard) drawSavedPopup(ctx);
            if(dragging()) drawDragGhost(ctx);
        } finally { ctx.getMatrices().pop(); images.endFrame(); }
        entrance.draw(ctx,width,height);
    }
    /** Reuse the approved PNG frame at a smaller size without leaving its old large slots behind. */
    private void drawBackground(DrawContext ctx) {
        slice(ctx,new Rect(0,0,1672,600),new Rect(0,0,1672,600));
        slice(ctx,new Rect(0,600,140,208),new Rect(0,600,140,208));
        slice(ctx,new Rect(1535,600,137,208),new Rect(1535,600,137,208));
        slice(ctx,new Rect(140,260,50,150),new Rect(140,600,1395,208));
        slice(ctx,new Rect(0,808,1672,133),new Rect(0,808,1672,133));
        for(int i=0;i<6;i++) {
            Rect r=KeySettingsLayout.slot(i);
            slice(ctx,new Rect(309,616,126,120),new Rect(r.x()-2,r.y()-2,r.width()+4,r.height()+4));
            Rect k=KeySettingsLayout.slotKey(i);
            overlay(ctx,k,0x77314353,0xAA8D9BA5,5);
        }
    }
    private void slice(DrawContext ctx,Rect source,Rect target) {
        images.drawTexture(ctx,BACKGROUND,target.x(),target.y(),source.x(),source.y(),
                target.width(),target.height(),source.width(),source.height(),1672,941,0xE8FFFFFF);
    }
    private void drawSavedPopup(DrawContext ctx) {
        if(savedPopupAt<0) return;
        long elapsed=Util.getMeasuringTimeMs()-savedPopupAt;
        if(elapsed>=2400) { savedPopupAt=-1; return; }
        float alpha=Math.min(1f,Math.min(elapsed/180f,(2400-elapsed)/400f));
        if(alpha<=0) return;
        // Independent floating toast: fades in, holds, then fades away; never takes input focus.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0,Math.round((1-alpha)*-6),40);
        Rect box=new Rect(629,555,414,56);
        overlay(ctx,new Rect(box.x()+3,box.y()+4,box.width(),box.height()),fade(0x55000000,alpha),0,9);
        overlay(ctx,box,fade(0xF214302F,alpha),fade(0xFF8FC9B6,alpha),9);
        text(ctx,"✓",661,583,24,fade(TEAL,alpha),BODY);
        text(ctx,"키 설정을 저장했습니다",851,583,22,fade(IVORY,alpha),BODY);
        ctx.getMatrices().pop();
    }
    private static int fade(int color,float alpha) { return (Math.round((color>>>24)*alpha)<<24)|(color&0xFFFFFF); }
    private void drawKey(DrawContext ctx,KeySettingsLayout.Key key) {
        Rect r=key.box(); boolean allowed=KeySettingsLayout.allowed(key.code());
        boolean blocked=KeySettingsLayout.reservedShortcut(key.code());
        int slot=-1;
        for(int i=0;i<6;i++) if(!draft.get(i).empty() && draft.get(i).keyCode()==key.code()) slot=i;
        boolean hot=hover(r) && !pickerOpen() && !confirmDiscard;
        if(!allowed) overlay(ctx,r,blocked?0xA06A303B:0x294C5661,blocked?0xFFE38A90:0x446B7781,6);
        if(slot>=0) {
            overlay(ctx,r,slot==selected?0x44499084:0x22277569,slot==selected?0xFF8AD7C9:0x9974A59B,6);
            Spell spell=spell(draft.get(slot).spellId());
            if(spell!=null) art(ctx,spell,r.centerX(),r.centerY()-4,44);
            text(ctx,key.label(),r.x()+r.width()-15,r.y()+r.height()-13,16,IVORY,BODY);
        } else {
            text(ctx,key.label(),r.centerX(),r.centerY()-(blocked?7:0),key.label().length()>5?17:21,blocked?0xFFFFC3C3:allowed?0xFFD5DADF:0xFF858E99,BODY);
            if(blocked) text(ctx,"지정 불가",r.centerX(),r.y()+r.height()-13,12,0xFFF0A4A8,BODY);
        }
        if(hot) overlay(ctx,r,blocked?0x337F3540:allowed?0x224FC5AF:0x22717B89,blocked?0xFFFFA4A8:allowed?0xFFA2DACE:0x998E949A,6);
    }
    private boolean dragging() { return dragSlot>=0 && dragMoved; }
    private int slotAt() {
        for(int i=0;i<6;i++) if(hover(KeySettingsLayout.slot(i)) || hover(KeySettingsLayout.slotKey(i))) return i;
        return -1;
    }
    private void drawSlot(DrawContext ctx,int i) {
        Rect r=KeySettingsLayout.slot(i); var binding=draft.get(i); Spell spell=spell(binding.spellId());
        boolean idle=!pickerOpen() && !confirmDiscard;
        boolean source=dragging() && dragSlot==i, target=dragging() && dragSlot!=i && slotAt()==i;
        if(i==selected) overlay(ctx,r,0x15339988,TEAL,9);
        if(target) overlay(ctx,r,0x334FC5AF,TEAL,9);
        else if(hover(r) && idle && !dragging()) overlay(ctx,r,0x225AAB9C,0xFFD8C398,9);
        if(!binding.empty() && spell!=null) {
            art(ctx,spell,r.centerX(),r.centerY(),70);
            if(!spell.permissionKnown() || !spell.discovered()) {
                overlay(ctx,r,0xAA07121C,0x7777868A,7); text(ctx,"사용 불가",r.centerX(),r.centerY(),16,MUTED,BODY);
            }
        } else if(!binding.empty()) text(ctx,"?",r.centerX(),r.centerY(),28,0x99CBB88D,BODY);
        if(source) overlay(ctx,r,0xB007121C,0x7777868A,7);
        Rect key=KeySettingsLayout.slotKey(i);
        String keyName=binding.empty()?Integer.toString(i+1):KeySettingsLayout.allowed(binding.keyCode())?KeySettingsLayout.name(binding.keyCode()):"키 없음";
        text(ctx,keyName,key.centerX(),key.centerY(),keyName.length()>5?15:18,binding.empty()?0x88A4ADB5:GOLD,BODY);
        if((hover(r) || hover(key)) && idle && !dragging() && !binding.empty()) {
            ctx.getMatrices().push(); ctx.getMatrices().translate(0,0,10);
            overlay(ctx,new Rect(Math.round(r.centerX()-122),575,244,37),0xF0112230,0xAA998A6E,6);
            text(ctx,shorten(spell==null?"없는 마법":spell.name(),218,18),r.centerX(),593,18,IVORY,BODY);
            ctx.getMatrices().pop();
        }
    }
    private void drawDragGhost(DrawContext ctx) {
        Spell spell=spell(draft.get(dragSlot).spellId()); if(spell==null) return;
        ctx.getMatrices().push(); ctx.getMatrices().translate(0,0,30);
        art(ctx,spell,(float)mx,(float)my,64);
        ctx.getMatrices().pop();
    }
    private void drawPicker(DrawContext ctx) {
        ctx.fill(124,48,1549,899,0xC507111B);
        overlay(ctx,new Rect(354,162,964,635),0xF20D1C2C,GOLD,12);
        text(ctx,"["+KeySettingsLayout.name(pickerKey)+"] 키에 등록할 마법",836,201,28,IVORY,TITLE);
        text(ctx,waiting()?"권한 확인 중…":"검색: " + (query.isEmpty()?"이름을 입력하세요":query),836,236,18,MUTED,BODY);
        button(ctx,KeySettingsLayout.PICKER_CLOSE,"×",false);
        var all=choices(); page=Math.min(page,Math.max(0,(all.size()-1)/9));
        for(int i=0;i<9;i++) {
            int index=page*9+i; if(index>=all.size()) break;
            Spell s=all.get(index); Rect r=KeySettingsLayout.choice(i);
            String state=choiceState(s);
            boolean blocked=state.equals(FULL), current=state.equals(CURRENT);
            overlay(ctx,r,hover(r)&&!blocked?0x5545706C:0x66213043,blocked?0xFF505D68:current?0xFFD0B98C:hover(r)?TEAL:0x996B7B80,7);
            art(ctx,s,r.x()+53,r.centerY(),76);
            label(ctx,shorten(s.name(),165,21),r.x()+102,r.centerY()-12,21,blocked?MUTED:IVORY);
            label(ctx,state.isEmpty()?s.category().label:state,r.x()+102,r.centerY()+20,16,current?GOLD:MUTED);
        }
        if(all.isEmpty()) text(ctx,waiting()?"잠시만 기다려 주세요":"등록할 수 있는 배운 마법이 없습니다",836,469,23,MUTED,BODY);
        button(ctx,KeySettingsLayout.PREVIOUS,"이전",false); button(ctx,KeySettingsLayout.NEXT,"다음",false);
        text(ctx,(page+1)+" / "+Math.max(1,(all.size()+8)/9)+"    ·    ESC 취소",836,762,18,GOLD,BODY);
    }
    private static final String CURRENT="현재 이 키", FULL="6개 모두 등록됨";
    /** Picker subtitle: empty = free to register, otherwise why/what happens on click. */
    private String choiceState(Spell s) {
        int at=draft.slotOfSpell(s.id());
        if(at>=0 && draft.get(at).keyCode()==pickerKey) return CURRENT;
        if(at>=0) return "등록됨 · "+KeySettingsLayout.name(draft.get(at).keyCode())+" → 이 키로 이동";
        if(draft.slotOfKey(pickerKey)<0 && draft.count()>=SpellKeySettings.LIMIT) return FULL;
        return "";
    }
    private void drawDiscard(DrawContext ctx) {
        ctx.fill(124,48,1549,899,0xD007111B);
        overlay(ctx,new Rect(550,350,572,254),0xFF101F2D,GOLD,10);
        text(ctx,"저장하지 않은 변경이 있습니다",836,416,26,IVORY,TITLE);
        text(ctx,"변경을 취소하고 도감으로 돌아갈까요?",836,460,20,MUTED,BODY);
        button(ctx,KeySettingsLayout.CONTINUE,"계속 설정",false); button(ctx,KeySettingsLayout.DISCARD,"변경 취소",false);
    }
    private void button(DrawContext ctx,Rect r,String label,boolean primary) {
        boolean hot=hover(r);
        overlay(ctx,r,hot?0x554C9E91:primary?0x162C8F86:0x10283B4D,hot?TEAL:0x778F9EA6,6);
        text(ctx,label,r.centerX(),r.centerY(),20,IVORY,BODY);
    }
    /** Rounded/chamfered translucent overlay stays within the artwork instead of a square hover box. */
    private static void overlay(DrawContext ctx,Rect r,int fill,int edge,int corner) {
        for(int row=0;row<r.height();row++) {
            int inset=Math.max(0,corner-Math.min(row,r.height()-1-row));
            int left=r.x()+inset,right=r.x()+r.width()-inset;
            ctx.fill(left,r.y()+row,right,r.y()+row+1,fill);
            if(row==0 || row==r.height()-1) ctx.fill(left,r.y()+row,right,r.y()+row+1,edge);
            else { ctx.fill(left,r.y()+row,left+1,r.y()+row+1,edge); ctx.fill(right-1,r.y()+row,right,r.y()+row+1,edge); }
        }
    }
    private boolean hover(Rect r) { return r.contains(mx,my); }
    private void text(DrawContext ctx,String value,float x,float y,float size,int color,Identifier font) { type.draw(ctx,value,x,y,size,color,font,true); }
    private void label(DrawContext ctx,String value,float x,float y,float size,int color) { type.draw(ctx,value,x,y,size,color,BODY,false); }
    private String shorten(String s,float w,float size) {
        if(type.width(s,size,BODY)<=w) return s;
        while(!s.isEmpty() && type.width(s+"…",size,BODY)>w) s=s.substring(0,s.offsetByCodePoints(s.length(),-1));
        return s+"…";
    }
    private void art(DrawContext ctx,Spell spell,float x,float y,int size) {
        if(!images.drawCentered(ctx,spell.icon(),x,y,size,0xFFFFFFFF)) text(ctx,"✦",x,y,size/2f,GOLD,TITLE);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        var layout=CodexLayout.fit(width,height); mx=layout.localX(x); my=layout.localY(y);
        if(button!=0 && button!=1) return false;
        if(confirmDiscard) {
            if(button==0 && hover(KeySettingsLayout.CONTINUE)) { confirmDiscard=false; sound(false); }
            else if(button==0 && hover(KeySettingsLayout.DISCARD)) { sound(false); leave(); }
            return true;
        }
        if(pickerOpen()) {
            if(button!=0) return true;
            if(hover(KeySettingsLayout.PICKER_CLOSE)) { pickerKey=-1; sound(false); return true; }
            if(hover(KeySettingsLayout.PREVIOUS)) { page=Math.max(0,page-1); sound(false); return true; }
            if(hover(KeySettingsLayout.NEXT)) { page=Math.min(Math.max(0,(choices().size()-1)/9),page+1); sound(false); return true; }
            if(waiting()) return true;
            for(int i=0;i<9;i++) if(hover(KeySettingsLayout.choice(i))) {
                int index=page*9+i; var choices=choices(); if(index>=choices.size()) return true;
                choose(choices.get(index)); return true;
            }
            return true;
        }
        if(button==0 && hover(KeySettingsLayout.CLOSE)) { close(); return true; }
        if(button==0 && hover(KeySettingsLayout.SAVE)) { save(); return true; }
        if(button==0 && hover(KeySettingsLayout.RESET)) { draft.clear(); selected=-1; notice=""; sound(false); return true; }
        int slot=slotAt();
        if(slot>=0) {
            if(button==1) {
                if(!draft.get(slot).empty()) { draft.clear(slot); notice=""; sound(false); }
                selected=-1; return true;
            }
            // Order changes on release so a press can become either a drag or a click.
            dragSlot=slot; pressX=mx; pressY=my; dragMoved=false;
            return true;
        }
        for(var key:KeySettingsLayout.KEYS) if(hover(key.box())) {
            if(button==1) {
                if(draft.clearKey(key.code())>=0) { notice=""; sound(false); }
                if(selected>=0 && draft.get(selected).empty()) selected=-1;
            } else openPicker(key.code());
            return true;
        }
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(dragSlot<0) return super.mouseDragged(x,y,button,dx,dy);
        var layout=CodexLayout.fit(width,height); mx=layout.localX(x); my=layout.localY(y);
        if(!dragMoved && !draft.get(dragSlot).empty() && Math.hypot(mx-pressX,my-pressY)>6) { dragMoved=true; selected=-1; }
        return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if(button!=0 || dragSlot<0) return super.mouseReleased(x,y,button);
        var layout=CodexLayout.fit(width,height); mx=layout.localX(x); my=layout.localY(y);
        int from=dragSlot, to=slotAt(); boolean moved=dragMoved;
        dragSlot=-1; dragMoved=false;
        if(pickerOpen() || confirmDiscard) return true;
        if(moved) { if(to>=0 && to!=from) { draft.swap(from,to); notice=""; } sound(false); return true; }
        if(to!=from) return true;
        if(selected<0) {
            if(draft.get(from).empty()) { notice="키보드의 키를 클릭하거나 눌러 마법을 등록하세요."; sound(true); }
            else { selected=from; notice=""; sound(false); }
        } else if(selected==from) { selected=-1; sound(false); }
        else { draft.swap(selected,from); selected=-1; notice=""; sound(false); }
        return true;
    }
    /** Opens the spell picker for a keyboard key; reserved keys explain why they are refused. */
    private boolean openPicker(int code) {
        if(!KeySettingsLayout.KEYS.stream().anyMatch(k->k.code()==code)) return false;
        if(!KeySettingsLayout.allowed(code)) {
            notice=KeySettingsLayout.reservedShortcut(code)?"WASD · I · O · P · Z 키는 지정할 수 없습니다.":"이 키는 지정할 수 없습니다.";
            sound(true); return false;
        }
        if(waiting()) { notice="서버에서 배운 마법을 확인하고 있습니다…"; sound(true); return false; }
        selected=-1; pickerKey=code; page=0; query=""; notice=""; sound(false);
        return true;
    }
    private void choose(Spell s) {
        if(!s.permissionKnown() || !s.discovered()) { notice="배운 마법만 키를 지정할 수 있습니다."; sound(true); return; }
        if(choiceState(s).equals(CURRENT)) { pickerKey=-1; sound(false); return; }
        try { draft.assign(pickerKey,s.id()); pickerKey=-1; notice=""; sound(false); }
        catch(IllegalArgumentException error) { notice=error.getMessage(); pickerKey=-1; sound(true); }
    }
    private void save() {
        try { draft.save(path); saved=draft.copy(); if(catalogBacked) CastingClient.saved(saved);
            notice=""; savedPopupAt=Util.getMeasuringTimeMs(); sound(false); }
        catch(Exception error) { notice=error.getMessage(); sound(true); org.slf4j.LoggerFactory.getLogger("magiccodex").warn("키 설정 저장 실패",error); }
    }
    private void sound(boolean error) {
        long now=Util.getMeasuringTimeMs(); if(now-lastSound<70) return; lastSound=now;
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(),error?0.7f:1.15f,error?0.18f:0.25f));
    }
    @Override public boolean keyPressed(int code,int scan,int modifiers) {
        if(code==GLFW.GLFW_KEY_ESCAPE) {
            if(confirmDiscard) confirmDiscard=false;
            else if(pickerOpen()) pickerKey=-1;
            else if(selected>=0) selected=-1;
            else close();
            return true;
        }
        if(confirmDiscard || dragSlot>=0) return true;
        if(pickerOpen()) { if(code==GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) { query=query.substring(0,query.offsetByCodePoints(query.length(),-1)); page=0; } return true; }
        // The same press also produces a typed character; keep it out of the new picker's search box.
        if(openPicker(code)) swallowChar=true;
        return true;
    }
    @Override public boolean charTyped(char chr,int modifiers) {
        if(swallowChar) { swallowChar=false; return true; }
        if(pickerOpen() && !confirmDiscard && !Character.isISOControl(chr) && query.length()<32) { query+=chr; page=0; }
        return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(pickerOpen() && !confirmDiscard && vertical!=0) { page=Math.clamp(page+(vertical<0?1:-1),0,Math.max(0,(choices().size()-1)/9)); sound(false); }
        return true;
    }
    @Override public void close() { sound(false); if(!draft.sameAs(saved)) confirmDiscard=true; else leave(); }
    private void leave() { client.setScreen(parent); }
    public void invalidateImageCache() { images=null;type=null; }
    public void verifyImageRenderer() { if(images==null || images.size()==0) throw new IllegalStateException("No key setting images"); images.verifyGpuState(); }
    @Override public void removed() { type=null;invalidateImageCache(); }
    @Override public boolean shouldPause() { return false; }
}
