package school.magiccodex.client;
import java.io.ByteArrayInputStream;import java.time.*;import java.time.format.DateTimeFormatter;import java.util.*;
import net.minecraft.client.gui.DrawContext;import net.minecraft.client.gui.screen.Screen;import net.minecraft.item.ItemStack;import net.minecraft.nbt.*;import net.minecraft.util.Identifier;
import school.magiccodex.protocol.MailboxProtocol;import school.magiccodex.protocol.MailboxProtocol.*;
/** Original approved 1672x940 panel with separate runtime text, slots and controls. */
public final class MailboxScreen extends SocialScreen {
 private static final Identifier PANEL=Identifier.of("magiccodex","textures/gui/mailbox/mailbox_panel_background.png");
 private final Screen parent;private Response data;private Input search;private boolean requested,confirmDelete;private int scroll,bodyScroll;private List<ItemStack> icons=List.of();
 MailboxScreen(Screen parent){super("우편함");this.parent=parent;}
 @Override SocialLayout.Fit fit(){float s=Math.min(width*.92f/1672,height*.90f/940);return new SocialLayout.Fit((width-1672*s)/2,(height-940*s)/2,s);}
 @Override protected void init(){super.init();search=new Input("우편 검색",80,search==null?"":search.text());if(!requested){requested=true;send(MailboxProtocol.OPEN,"",0);}}
 void receive(Response r){data=r;busy=false;notice(r.message());icons=r.attachments().stream().map(a->decode(a.preview())).toList();scroll=Math.clamp(scroll,0,Math.max(0,visible().size()-9));}
 void failed(String message){busy=false;notice(message);}
 private ItemStack decode(byte[] bytes){if(client.world==null||bytes.length==0)return ItemStack.EMPTY;try{return ItemStack.fromNbt(client.world.getRegistryManager(),NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtSizeTracker.of(262144))).orElse(ItemStack.EMPTY);}catch(Exception e){return ItemStack.EMPTY;}}
 private List<Entry> visible(){if(data==null)return List.of();String q=search==null?"":search.text().toLowerCase(Locale.ROOT);return data.entries().stream().filter(e->e.title().toLowerCase(Locale.ROOT).contains(q)).toList();}
 private void send(int action,String id,int page){if(busy)return;busy=MailboxClient.request(this,action,data==null?0:data.session(),id,page);}
 private String selected(){return data==null?"":data.selected();}private int page(){return data==null?0:data.page();}
 @Override public void render(DrawContext c,int x,int y,float delta){var f=fit();double mx=f.x(x),my=f.y(y);start(c);try{
  if(client.getResourceManager().getResource(PANEL).isPresent())images.drawTexture(c,PANEL,0,0,0,0,1672,940,1672,940,1672,940,0xFFFFFFFF);else asset(c,"friends_panel",0,0,1672,940,0,0,1340,1174,0xFFFFFFFF);
  label(c,"우편함",171,78,40,GOLD,true);FriendsScreen.cross(c,1598,72,in(mx,my,1576,50,44,44)?CYAN:WHITE);
  box(c,74,168,653,51,search.focus());search.draw(c,80,170,641,47,"이 페이지의 우편 검색");
  var entries=visible();for(int row=0;row<9&&row+scroll<entries.size();row++){var e=entries.get(row+scroll);int ry=236+row*65;boolean active=e.id().equals(selected());box(c,74,ry,653,59,active||in(mx,my,74,ry,653,59));fitted(c,e.title(),91,ry+20,26,617,active?CYAN:WHITE,true);String date=DateTimeFormatter.ofPattern("MM.dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(e.created()));label(c,date+" · "+(e.remaining()==0?"수령 완료":"남은 첨부 "+e.remaining()),91,ry+45,18,MUTED,false);}
  if(entries.isEmpty())center(c,data==null?"우편을 불러오는 중…":"우편이 없습니다",400,491,26,MUTED);
  button(c,"이전",74,850,130,46,page()>0&&in(mx,my,74,850,130,46));center(c,(page()+1)+" 페이지",401,874,22,MUTED);button(c,"다음",597,850,130,46,data!=null&&data.more()&&in(mx,my,597,850,130,46));
  var chosen=data==null?null:data.entries().stream().filter(e->e.id().equals(selected())).findFirst().orElse(null);fitted(c,chosen==null?"우편을 선택해 주세요":chosen.title(),815,190,32,773,GOLD,true);
  List<String> lines=wrapped(data==null?"":data.body(),773);bodyScroll=Math.clamp(bodyScroll,0,Math.max(0,lines.size()-11));for(int i=0;i<11&&i+bodyScroll<lines.size();i++)label(c,lines.get(i+bodyScroll),815,244+i*28,24,WHITE,false);
  if(lines.size()>11)label(c,"본문에서 스크롤하여 더 보기",815,550,17,MUTED,false);
  label(c,"첨부 아이템",815,592,25,GOLD,true);if(data!=null)for(int i=0;i<data.attachments().size();i++){var a=data.attachments().get(i);int ax=815+(i%5)*156,ay=614+(i/5)*91;box(c,ax,ay,141,81,false);var item=icons.get(i);if(!item.isEmpty()){c.getMatrices().push();c.getMatrices().translate(ax+15,ay+12,0);c.getMatrices().scale(2.2f,2.2f,1);c.drawItem(item,0,0);c.getMatrices().pop();}label(c,a.claimed()?"수령완료":"×"+a.amount(),ax+15,ay+65,18,a.claimed()?MUTED:GOLD,false);if(in(mx,my,ax,ay,141,81))fitted(c,a.name(),815,804,18,773,WHITE,false);}
  boolean can=data!=null&&data.attachments().stream().anyMatch(a->!a.claimed());button(c,busy?"처리 중…":"선택 우편 받기",815,819,773,48,!busy&&can&&in(mx,my,815,819,773,48));
  button(c,"새로고침",532,71,195,43,!busy&&in(mx,my,532,71,195,43));button(c,"모두 받기",815,879,303,43,!busy&&in(mx,my,815,879,303,43));button(c,"수령한 우편 모두 삭제",1135,879,453,43,!busy&&in(mx,my,1135,879,453,43));
  if(confirmDelete){chamfer(c,30,30,1612,880,20,0xCA091624);box(c,476,325,720,250,false);center(c,"수령한 우편을 모두 삭제할까요?",836,378,31,WHITE);center(c,"미수령 첨부가 있는 우편은 남아 있습니다.",836,430,24,MUTED);button(c,"취소",510,499,283,49,in(mx,my,510,499,283,49));button(c,"삭제",877,499,283,49,in(mx,my,877,499,283,49));}
  toast(c,815,543,773);
 }finally{end(c);}}
 private List<String>wrapped(String text,int max){var lines=new ArrayList<String>();for(String paragraph:text.split("\n",-1)){StringBuilder line=new StringBuilder();for(int cp:paragraph.codePoints().toArray()){String next=new String(Character.toChars(cp));if(type.width(line+next,24,BODY)>max&&line.length()>0){lines.add(line.toString());line.setLength(0);}line.append(next);}lines.add(line.toString());}return lines;}
 @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return super.mouseClicked(x,y,button);var f=fit();double mx=f.x(x),my=f.y(y);if(confirmDelete){if(in(mx,my,510,499,283,49))confirmDelete=false;else if(in(mx,my,877,499,283,49)){confirmDelete=false;send(MailboxProtocol.DELETE_CLAIMED,"",page());}return true;}if(in(mx,my,1576,50,44,44)){close();return true;}if(busy)return true;var entries=visible();for(int row=0;row<9&&row+scroll<entries.size();row++)if(in(mx,my,74,236+row*65,653,59)){bodyScroll=0;send(MailboxProtocol.DETAIL,entries.get(row+scroll).id(),page());return true;}if(in(mx,my,74,850,130,46)&&page()>0){scroll=0;send(MailboxProtocol.OPEN,"",page()-1);}else if(in(mx,my,597,850,130,46)&&data!=null&&data.more()){scroll=0;send(MailboxProtocol.OPEN,"",page()+1);}else if(in(mx,my,815,819,773,48))send(MailboxProtocol.CLAIM,selected(),page());else if(in(mx,my,532,71,195,43))send(MailboxProtocol.OPEN,selected(),page());else if(in(mx,my,815,879,303,43))send(MailboxProtocol.CLAIM_ALL,"",page());else if(in(mx,my,1135,879,453,43))confirmDelete=true;else search.click(mx,my);return true;}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){var f=fit();if(f.x(x)<773)scroll=Math.clamp(scroll-(int)Math.signum(vertical),0,Math.max(0,visible().size()-9));else bodyScroll=Math.max(0,bodyScroll-(int)Math.signum(vertical));return true;}
 @Override public boolean keyPressed(int key,int scan,int mods){if(search.key(key,scan,mods))return true;return super.keyPressed(key,scan,mods);}
 @Override public boolean charTyped(char c,int mods){return search.typed(c,mods)||super.charTyped(c,mods);}
 @Override public void close(){MailboxClient.detach(this);if(!busy&&data!=null)MailboxClient.request(this,MailboxProtocol.CLOSE,data.session(),"",page());client.setScreen(parent);}
}
