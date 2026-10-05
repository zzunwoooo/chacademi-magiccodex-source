package school.magiccodex.client;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import school.magiccodex.protocol.DialogueAdminProtocol;

/** Plain administrator forms; all authoritative validation and permissions remain on Paper. */
public final class DialogueAdminScreen extends Screen {
    private DialogueAdminProtocol.Response data;
    private final Map<String,String> draft=new HashMap<>();
    private String id="",revision="",message="",popup="";private List<String> options=List.of(),optionLabels=List.of();
    private int optionPage,tab,scroll,listPage,goal,commandIndex,itemIndex,formX,formW,bodyTop,bodyBottom,totalHeight;
    private boolean editing,dirty,deleteConfirm,discardConfirm;private long waiting;
    private final List<Label> labels=new ArrayList<>();
    private record Label(String text,int x,int y){}
    private record Row(String label,String key,int max,boolean multi,List<String> choices,List<String> names){}
    public DialogueAdminScreen(DialogueAdminProtocol.Response data){super(Text.literal("대화 관리"));this.data=data;receiveData(data);}
    private void receiveData(DialogueAdminProtocol.Response r){data=r;message=r.message();waiting=0;if(!r.id().isEmpty()){node="start";id=r.id();revision=r.revision();draft.clear();draft.putAll(r.fields());editing=true;dirty=false;deleteConfirm=discardConfirm=false;}}
    void previewOpened(){waiting=0;message="미리보기에서는 진행 상태와 보상을 변경하지 않습니다.";}
    void receive(DialogueAdminProtocol.Response r){receiveData(r);if(r.message().equals("대화를 삭제했습니다.")){editing=false;draft.clear();dirty=false;}if(client!=null)init();}
    @Override public void renderBackground(DrawContext c,int x,int y,float delta){}
    @Override public boolean shouldPause(){return false;}
    @Override public void tick(){if(waiting>0&&Util.getMeasuringTimeMs()>waiting){waiting=0;message="응답을 확인하지 못했습니다. 목록을 새로고침해 주세요.";init();}}
    private void request(int action,String target){if(waiting>0)return;try{if(DialogueAdminClient.send(action,target,revision,action==2||action==4?draft:Map.of())){waiting=Util.getMeasuringTimeMs()+10000;message="처리 중…";}else message="서버 응답을 기다려 주세요.";}catch(IllegalArgumentException e){message=e.getMessage();}}
    private ButtonWidget button(String text,int x,int y,int w,Runnable action){var b=ButtonWidget.builder(Text.literal(text),v->action.run()).dimensions(x,y,w,20).build();addDrawableChild(b);return b;}
    @Override protected void init(){
        clearChildren();labels.clear();int left=12,listW=Math.min(190,Math.max(116,width/4));formX=left+listW+14;formW=width-formX-43;bodyTop=70;bodyBottom=height-56;
        button("+ 새 대화",left,37,listW,()->{if(canDiscard())create();});
        int visible=Math.max(1,(height-132)/30),maxPage=Math.max(0,(data.entries().size()-1)/visible);listPage=Math.clamp(listPage,0,maxPage);
        for(int i=listPage*visible;i<Math.min(data.entries().size(),(listPage+1)*visible);i++){var q=data.entries().get(i);int y=70+(i-listPage*visible)*30;button(q.rank()+" · "+clip(q.title(),listW-12),left,y,listW,()->{if(canDiscard()){scroll=0;request(1,q.id());}}).setTooltip(Tooltip.of(Text.literal(q.id()+" · "+q.publication())));}
        button("‹",left,height-57,26,()->{listPage--;init();});button("›",left+listW-26,height-57,26,()->{listPage++;init();});labels.add(new Label((listPage+1)+" / "+(maxPage+1),left+39,height-50));
        button("새로고침",left,height-31,listW,()->{if(canDiscard())request(0,"");});button("닫기",width-54,10,42,this::close);if(editing)button("+ 장면",width-222,10,76,this::addNode);if(editing)button("미리보기",width-140,10,80,()->request(4,id));
        if(!editing){labels.add(new Label("대화를 선택하거나 새 대화를 추가하세요.",formX,83));return;}
        String[] names={"대화 정보","장면 · 대사","선택지 · 분기","조건 · 동작"};int tw=(formW-9)/4;for(int i=0;i<4;i++){final int n=i;button((i==tab?"• ":"")+names[i],formX+i*(tw+3),37,tw,()->{tab=n;scroll=0;popup="";init();});}
        var rows=rows();totalHeight=0;for(var r:rows)totalHeight+=rowHeight(r);scroll=Math.clamp(scroll,0,Math.max(0,totalHeight-(bodyBottom-bodyTop)));
        int y=bodyTop-scroll;for(var r:rows){int h=rowHeight(r);if(y>=bodyTop&&y+h<=bodyBottom){labels.add(new Label(r.label,formX,y));int yy=y+12;
                if(!r.choices.isEmpty()){String value=value(r.key),name=value;int at=r.choices.indexOf(value);if(at>=0&&at<r.names.size())name=r.names.get(at);button(name+"  ▾",formX,yy,formW,()->{popup=r.key;optionPage=0;options=r.choices;optionLabels=r.names;init();});}
                else if(r.multi){var f=new EditBoxWidget(textRenderer,formX,yy,formW,h-17,Text.literal(""),Text.literal(r.label));f.setMaxLength(r.max);f.setText(value(r.key));f.setChangeListener(v->put(r.key,v));addDrawableChild(f);}
                else {var f=new TextFieldWidget(textRenderer,formX,yy,formW,18,Text.literal(r.label));f.setMaxLength(r.max);f.setText(value(r.key));f.setChangedListener(v->put(r.key,v));if(r.key.equals("id")&&!revision.isEmpty())f.setEditable(false);addDrawableChild(f);}
            }y+=h;}
        button("저장 · 반영",formX,height-31,Math.min(105,formW/2-3),()->{if(waiting==0)request(2,id);});button(deleteConfirm?"정말 삭제":"삭제",formX+Math.min(105,formW/2-3)+6,height-31,Math.min(70,formW/3),()->{if(waiting!=0)return;if(deleteConfirm)request(3,id);else{deleteConfirm=true;init();}}).active=!revision.isEmpty();
        if(totalHeight>bodyBottom-bodyTop){button("↑",width-38,bodyTop-1,23,()->{scroll=Math.max(0,scroll-36);init();});button("↓",width-38,bodyBottom-20,23,()->{scroll+=36;init();});}
        if(!popup.isEmpty()){clearChildren();button("선택 취소",formX,37,formW,()->{popup="";init();});int visibleOptions=Math.max(1,(height-150)/26),maxOptionPage=Math.max(0,(options.size()-1)/visibleOptions);optionPage=Math.clamp(optionPage,0,maxOptionPage);for(int i=optionPage*visibleOptions;i<Math.min(options.size(),(optionPage+1)*visibleOptions);i++){String v=options.get(i),label=i<optionLabels.size()?optionLabels.get(i):v;button(label,formX,72+(i-optionPage*visibleOptions)*26,formW,()->{put(popup,v);popup="";init();});}button("‹ 이전",formX,height-58,80,()->{optionPage--;init();});button("다음 ›",formX+90,height-58,80,()->{optionPage++;init();});}

    }
    private int rowHeight(Row r){return r.multi?Math.max(52,Math.min(112,bodyBottom-bodyTop)):36;}
    private Row row(String label,String key,int max){return new Row(label,key,max,false,List.of(),List.of());}
    private Row multi(String label,String key,int max){return new Row(label,key,max,true,List.of(),List.of());}
    private Row choice(String label,String key,List<String> v,List<String> names){return new Row(label,key,0,false,v,names);}
    private void addNode(){var ns=new ArrayList<>(nodes());if(ns.size()>=32){message="장면은 최대 32개입니다. 새 대화 파일로 나눠 주세요.";return;}int index=1;while(ns.contains("scene_"+index))index++;node="scene_"+index;ns.add(node);draft.put("nodes",String.join("\n",ns));draft.put("node."+node+".speaker","NPC 이름");draft.put("node."+node+".portrait","elena-neutral");draft.put("node."+node+".text","대사를 입력하세요.");draft.put("node."+node+".choices","0");dirty=true;tab=1;scroll=112;init();}
    private String node="start";
    private List<String> nodes(){return draft.getOrDefault("nodes","start").lines().map(String::trim).filter(v->!v.isEmpty()).distinct().toList();}
    private List<Row> rows(){var r=new ArrayList<Row>();var ns=nodes();if(!ns.contains(node)&&!ns.isEmpty())node=ns.getFirst();String p="node."+node+".",cp=p+"choice."+goal+".";
        switch(tab){
            case 0->{r.add(row("대화 ID (저장 후 고정)","id",48));r.add(row("퀘스트 / 대화 제목","title",100));r.add(choice("공개 여부","enabled",List.of("true","false"),List.of("공개","비공개")));r.add(row("시작 장면 ID","start",48));r.add(multi("NPC 연결 · 한 줄씩: citizens:12 또는 tag:codex_elena","npcs",1500));r.add(row("대화 권한 (빈칸: 모두)","permission",100));r.add(row("연결할 메인 퀘스트 ID","story-id",48));r.add(row("메인 퀘스트 이름","story-title",100));r.add(multi("이야기 단계 표시 · 한 줄씩: 단계ID=설명","story-stages",3000));}
            case 1->{r.add(multi("장면 ID 목록 · 한 줄씩 추가 (최대 32개)","nodes",1600));r.add(choice("편집할 장면","@node",ns,ns));r.add(row("NPC 이름","node."+node+".speaker",64));r.add(row("일러스트 ID · 예: elena-neutral / 빈칸: 숨김",p+"portrait",120));r.add(multi("대사 · 줄바꿈 가능, 길면 자동으로 페이지 분리",p+"text",1600));}
            case 2->{r.add(choice("편집할 장면","@node",ns,ns));r.add(choice("사용할 선택지 개수",p+"choices",List.of("0","1","2","3","4","5","6"),List.of()));r.add(choice("편집할 선택지","@goal",List.of("0","1","2","3","4","5"),List.of("선택지 1","선택지 2","선택지 3","선택지 4","선택지 5","선택지 6")));r.add(row("선택지 문구",cp+"text",140));r.add(row("이동할 장면 ID (빈칸: 대화 종료)",cp+"next",48));}
            case 3->{r.add(choice("편집할 장면","@node",ns,ns));r.add(multi("장면 진입 조건 · 예: story first_clue find_arden",p+"conditions",1500));r.add(choice("편집할 선택지","@goal",List.of("0","1","2","3","4","5"),List.of("선택지 1","선택지 2","선택지 3","선택지 4","선택지 5","선택지 6")));r.add(multi("선택지 조건 · flag / story / quest / permission / custom",cp+"conditions",1500));r.add(multi("선택 동작 · 한 줄씩: story ID 단계 / quest ID / command 명령어",cp+"actions",2500));}
        }return r;
    }
    private String value(String k){if(k.equals("id"))return id;if(k.equals("@goal"))return ""+goal;if(k.equals("@node"))return node;return draft.getOrDefault(k,k.endsWith(".choices")?"0":"");}
    private void put(String k,String v){if(k.equals("id")){id=v;dirty=true;}else if(k.equals("@goal")){goal=Integer.parseInt(v);}else if(k.equals("@node")){node=v;}else{draft.put(k,v);dirty=true;}discardConfirm=false;}
    private boolean canDiscard(){if(!dirty)return true;if(discardConfirm){dirty=false;discardConfirm=false;return true;}discardConfirm=true;message="저장하지 않은 내용이 있습니다. 한 번 더 누르면 버립니다.";return false;}
    private void create(){id="";revision="";draft.clear();draft.putAll(Map.of("title","새 이야기","start","start","enabled","false","nodes","start","node.start.speaker","NPC 이름","node.start.portrait","elena-neutral","node.start.text","안녕하세요. 아카데미에 오신 것을 환영해요.","node.start.choices","1","node.start.choice.0.text","대화를 마친다."));node="start";editing=true;dirty=true;tab=scroll=goal=0;deleteConfirm=false;message="장면과 선택지를 작성하세요. 미리보기는 진행 상태를 바꾸지 않습니다.";init();}
    private String clip(String s,int w){return textRenderer.trimToWidth(s,Math.max(12,w));}
    @Override public void close(){if(canDiscard())super.close();}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(super.mouseScrolled(x,y,h,v))return true;if(x>=formX&&y>=bodyTop&&y<bodyBottom){scroll+=(int)-Math.signum(v)*36;init();return true;}return false;}
    @Override public void render(DrawContext c,int x,int y,float delta){c.fill(0,0,width,height,0xEC0C1722);c.fill(formX-6,33,width-8,height-40,0xFF182A39);c.drawText(textRenderer,"차카데미 · 대화 관리",14,16,0xFFEAD2A0,false);if(popup.isEmpty())for(var l:labels)c.drawText(textRenderer,clip(l.text,l.x>=formX?formW:180),l.x,l.y,0xFFE2E9EE,false);super.render(c,x,y,delta);if(!message.isEmpty())c.drawText(textRenderer,clip(message,width-26),13,height-43,0xFFEDCF8D,false);}
}
