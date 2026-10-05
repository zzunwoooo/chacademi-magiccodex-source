package school.magiccodex.client;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import school.magiccodex.protocol.MailboxProtocol;
import school.magiccodex.protocol.MailboxProtocol.*;
/** Function-first existing-style inbox. Production background can be supplied independently. */
public final class MailboxScreen extends SocialScreen {
 private final Screen parent;private Response data;private Input search;private boolean requested,confirmDelete;private int scroll,bodyScroll;private List<ItemStack> icons=List.of();
 MailboxScreen(Screen parent){super("우편함");this.parent=parent;}
 @Override SocialLayout.Fit fit(){float s=Math.min(width*.90f/1200,height*.88f/760);return new SocialLayout.Fit((width-1200*s)/2,(height-760*s)/2,s);}
 @Override protected void init(){super.init();search=new Input("우편 검색",80,search==null?"":search.text());if(!requested){requested=true;send(MailboxProtocol.OPEN,"",0);}}
 void receive(Response r){data=r;busy=false;notice(r.message());icons=r.attachments().stream().map(a->decode(a.preview())).toList();scroll=Math.clamp(scroll,0,Math.max(0,visible().size()-7));}
 void failed(String message){busy=false;notice(message);}
 private ItemStack decode(byte[] bytes){if(client.world==null||bytes.length==0)return ItemStack.EMPTY;try{return ItemStack.fromNbt(client.world.getRegistryManager(),NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtSizeTracker.of(262144))).orElse(ItemStack.EMPTY);}catch(Exception e){return ItemStack.EMPTY;}}
 private List<Entry> visible(){if(data==null)return List.of();String q=search==null?"":search.text().toLowerCase(Locale.ROOT);return data.entries().stream().filter(e->e.title().toLowerCase(Locale.ROOT).contains(q)).toList();}
 private void send(int action,String id,int page){if(busy)return;busy=MailboxClient.request(this,action,data==null?0:data.session(),id,page);}
 private String selected(){return data==null?"":data.selected();}private int page(){return data==null?0:data.page();}
 @Override public void render(DrawContext c,int x,int y,float delta){var f=fit();double mx=f.x(x),my=f.y(y);start(c);try{
  // Reuse existing panel/slot/button assets, preserving opacity without tint filters.
  asset(c,"friends_panel",0,0,1200,760,0,0,1340,1174,0xFFFFFFFF);
  label(c,"우편함",64,69,36,GOLD,true);FriendsScreen.cross(c,1138,65,in(mx,my,1116,43,44,44)?CYAN:WHITE);
  box(c,54,113,493,49,search.focus());search.draw(c,60,115,481,45,"이 페이지의 우편 검색");
  var entries=visible();for(int row=0;row<7&&row+scroll<entries.size();row++){var e=entries.get(row+scroll);int ry=182+row*63;boolean active=e.id().equals(selected());box(c,54,ry,493,57,active||in(mx,my,54,ry,493,57));fitted(c,e.title(),69,ry+19,23,459,active?CYAN:WHITE,true);String date=DateTimeFormatter.ofPattern("MM.dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(e.created()));label(c,date+" · "+(e.remaining()==0?"수령 완료":"남은 첨부 "+e.remaining()),69,ry+41,17,MUTED,false);}
  if(entries.isEmpty())center(c,data==null?"우편을 불러오는 중…":"우편이 없습니다",299,363,24,MUTED);
  button(c,"이전",54,638,92,43,page()>0&&in(mx,my,54,638,92,43));center(c,(page()+1)+" 페이지",300,660,20,MUTED);button(c,"다음",455,638,92,43,data!=null&&data.more()&&in(mx,my,455,638,92,43));
  box(c,572,113,574,518,false);var chosen=data==null?null:data.entries().stream().filter(e->e.id().equals(selected())).findFirst().orElse(null);fitted(c,chosen==null?"우편을 선택해 주세요":chosen.title(),598,149,29,520,GOLD,true);
  List<String> lines=wrapped(data==null?"":data.body(),515);bodyScroll=Math.clamp(bodyScroll,0,Math.max(0,lines.size()-8));for(int i=0;i<8&&i+bodyScroll<lines.size();i++)label(c,lines.get(i+bodyScroll),598,195+i*26,22,WHITE,false);
  if(lines.size()>8)label(c,"본문에서 스크롤하여 더 보기",598,403,16,MUTED,false);
  label(c,"첨부 아이템",598,441,22,GOLD,true);if(data!=null)for(int i=0;i<data.attachments().size();i++){var a=data.attachments().get(i);int ax=598+(i%5)*104,ay=462+(i/5)*75;box(c,ax,ay,92,68,false);var item=icons.get(i);if(!item.isEmpty()){c.getMatrices().push();c.getMatrices().translate(ax+8,ay+9,0);c.getMatrices().scale(1.7f,1.7f,1);c.drawItem(item,0,0);c.getMatrices().pop();}label(c,a.claimed()?"수령완료":"×"+a.amount(),ax+8,ay+54,16,a.claimed()?MUTED:GOLD,false);if(in(mx,my,ax,ay,92,68))fitted(c,a.name(),598,614,18,518,WHITE,false);}
  boolean can=data!=null&&data.attachments().stream().anyMatch(a->!a.claimed());button(c,busy?"처리 중…":"선택 우편 받기",572,638,574,43,!busy&&can&&in(mx,my,572,638,574,43));
  button(c,"새로고침",54,698,160,42,!busy&&in(mx,my,54,698,160,42));button(c,"모두 받기",231,698,231,42,!busy&&in(mx,my,231,698,231,42));button(c,"수령한 우편 모두 삭제",479,698,383,42,!busy&&in(mx,my,479,698,383,42));
  if(confirmDelete){chamfer(c,30,30,1140,690,20,0xCA091624);box(c,315,268,570,210,false);center(c,"수령한 우편을 모두 삭제할까요?",600,316,27,WHITE);center(c,"미수령 첨부가 있는 우편은 남아 있습니다.",600,361,21,MUTED);button(c,"취소",344,407,222,44,in(mx,my,344,407,222,44));button(c,"삭제",634,407,222,44,in(mx,my,634,407,222,44));}
  toast(c,575,685,570);
 }finally{end(c);}}
 private List<String>wrapped(String text,int max){var lines=new ArrayList<String>();for(String paragraph:text.split("\n",-1)){StringBuilder line=new StringBuilder();for(int cp:paragraph.codePoints().toArray()){String next=new String(Character.toChars(cp));if(type.width(line+next,22,BODY)>max&&line.length()>0){lines.add(line.toString());line.setLength(0);}line.append(next);}lines.add(line.toString());}return lines;}
 @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return super.mouseClicked(x,y,button);var f=fit();double mx=f.x(x),my=f.y(y);if(confirmDelete){if(in(mx,my,344,407,222,44))confirmDelete=false;else if(in(mx,my,634,407,222,44)){confirmDelete=false;send(MailboxProtocol.DELETE_CLAIMED,"",page());}return true;}if(in(mx,my,1116,43,44,44)){close();return true;}if(busy)return true;var entries=visible();for(int row=0;row<7&&row+scroll<entries.size();row++)if(in(mx,my,54,182+row*63,493,57)){bodyScroll=0;send(MailboxProtocol.DETAIL,entries.get(row+scroll).id(),page());return true;}if(in(mx,my,54,638,92,43)&&page()>0){scroll=0;send(MailboxProtocol.OPEN,"",page()-1);}else if(in(mx,my,455,638,92,43)&&data!=null&&data.more()){scroll=0;send(MailboxProtocol.OPEN,"",page()+1);}else if(in(mx,my,572,638,574,43))send(MailboxProtocol.CLAIM,selected(),page());else if(in(mx,my,54,698,160,42))send(MailboxProtocol.OPEN,selected(),page());else if(in(mx,my,231,698,231,42))send(MailboxProtocol.CLAIM_ALL,"",page());else if(in(mx,my,479,698,383,42))confirmDelete=true;else search.click(mx,my);return true;}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){var f=fit();if(f.x(x)<560)scroll=Math.clamp(scroll-(int)Math.signum(vertical),0,Math.max(0,visible().size()-7));else bodyScroll=Math.max(0,bodyScroll-(int)Math.signum(vertical));return true;}
 @Override public boolean keyPressed(int key,int scan,int mods){if(search.key(key,scan,mods))return true;return super.keyPressed(key,scan,mods);}
 @Override public boolean charTyped(char c,int mods){return search.typed(c,mods)||super.charTyped(c,mods);}
 @Override public void close(){MailboxClient.detach(this);if(!busy&&data!=null)MailboxClient.request(this,MailboxProtocol.CLOSE,data.session(),"",page());client.setScreen(parent);}
}
