package school.magiccodex.client;
import java.io.ByteArrayInputStream;import java.util.*;
import net.minecraft.client.gui.DrawContext;import net.minecraft.item.ItemStack;import net.minecraft.nbt.*;
import school.magiccodex.protocol.ShopAdminProtocol;import school.magiccodex.protocol.ShopAdminProtocol.*;
/** Operator-only catalogue editor. Existing typography/widgets, no replacement of the player shop. */
public final class ShopAdminScreen extends SocialScreen {
 private Response data;private Input search,name,buy,sell;private boolean buyAllowed=true,sellAllowed=false,creating=true;
 private String selected="";private int scroll;private final Deque<Integer> previous=new ArrayDeque<>();
 private Request confirmation;private Map<String,ItemStack> icons=Map.of();
 ShopAdminScreen(){super("상점 물품 관리");}
 @Override SocialLayout.Fit fit(){float s=Math.min(width*.94f/1340,height*.92f/900);return new SocialLayout.Fit((width-1340*s)/2,(height-900*s)/2,s);}
 @Override protected void init(){super.init();search=new Input("상품 검색",80,search==null?"":search.text());name=new Input("상품명",120,name==null?"":name.text());buy=new Input("구매 가격",32,buy==null?"0":buy.text());sell=new Input("판매 가격",32,sell==null?"0":sell.text());}
 void receive(Response r){data=r;busy=false;confirmation=null;notice(r.message());var decoded=new HashMap<String,ItemStack>();for(var p:r.products())decoded.put(p.id(),decode(p.preview()));icons=Map.copyOf(decoded);scroll=0;
  if(!creating&&r.products().stream().anyMatch(p->p.id().equals(selected)))select(selected);else{creating=true;selected="";}
 }
 void failed(String m){busy=false;confirmation=null;notice(m);}
 private ItemStack decode(byte[] b){if(b.length==0||client.world==null)return ItemStack.EMPTY;try{return ItemStack.fromNbt(client.world.getRegistryManager(),NbtIo.readCompressed(new ByteArrayInputStream(b),NbtSizeTracker.of(2097152))).orElse(ItemStack.EMPTY);}catch(Exception e){return ItemStack.EMPTY;}}
 private void select(String id){selected=id;creating=false;confirmation=null;var p=data.products().stream().filter(x->x.id().equals(id)).findFirst().orElse(null);if(p!=null){name.widget.setText(p.name());buyAllowed=!p.buy().isEmpty();sellAllowed=!p.sell().isEmpty();buy.widget.setText(buyAllowed?p.buy():"0");sell.widget.setText(sellAllowed?p.sell():"0");}}
 private void newProduct(){creating=true;selected="";confirmation=null;var item=client.player.getMainHandStack();name.widget.setText(item.isEmpty()?"":ItemTooltipRenderer.localName(item,item.getName()).getString());buy.widget.setText("0");sell.widget.setText("0");buyAllowed=true;sellAllowed=false;notice("현재 주 손 아이템 원본을 1개 단위 상품으로 등록합니다. 아이템은 소비하지 않습니다.");}
 private void list(String shop,int offset){if(data==null||busy)return;confirmation=null;busy=ShopAdminClient.request(this,new Request(ShopAdminProtocol.LIST,1,data.session(),shop,data.revision(),"","","off","off",search.text(),offset));if(!busy)notice("서버 연결 또는 이전 요청을 확인하세요.");}
 private void prepare(int action){if(data==null||busy||data.revision()<1)return;if(action==ShopAdminProtocol.DELETE&&selected.isEmpty())return;
  try{String b=buyAllowed?buy.text():"off",s=sellAllowed?sell.text():"off";if(action!=ShopAdminProtocol.DELETE){ShopAdminProtocol.price(b);ShopAdminProtocol.price(s);if(name.text().isBlank())throw new IllegalArgumentException("상품 이름을 입력하세요.");if(action==ShopAdminProtocol.ADD&&client.player.getMainHandStack().isEmpty())throw new IllegalArgumentException("주 손에 등록할 아이템을 들어 주세요.");}
   String confirmedName=action==ShopAdminProtocol.DELETE?data.products().stream().filter(p->p.id().equals(selected)).findFirst().orElseThrow().name():name.text();
   confirmation=new Request(action,1,data.session(),data.shop(),data.revision(),selected,confirmedName,b,s,search.text(),data.offset());search.focus(false);name.focus(false);buy.focus(false);sell.focus(false);
  }catch(IllegalArgumentException e){notice(e.getMessage());}
 }
 private void confirm(){if(confirmation==null||busy)return;busy=ShopAdminClient.request(this,confirmation);if(busy)confirmation=null;else notice("서버 연결 또는 이전 요청을 확인하세요.");}
 private void cycle(int step){if(data==null||data.shops().isEmpty())return;int at=0;for(int i=0;i<data.shops().size();i++)if(data.shops().get(i).id().equals(data.shop()))at=i;var s=data.shops().get(Math.floorMod(at+step,data.shops().size()));creating=true;selected="";previous.clear();search.widget.setText("");list(s.id(),0);}
 private void fieldBox(DrawContext c,Input input,int y,String hint){box(c,820,y,450,48,input.focus());input.draw(c,826,y+2,438,44,hint);}
 @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){var f=fit();double mx=f.x(mouseX),my=f.y(mouseY);ItemStack hover=ItemStack.EMPTY;start(c);try{
  asset(c,"friends_panel",0,0,1340,900,0,0,1340,1174,0xF0DEE5EF);label(c,"상점 물품 관리",52,62,34,WHITE,true);button(c,"닫기",1190,36,100,46,in(mx,my,1190,36,100,46));label(c,"운영자 · 상품 원본 유지 · 저장 전 확인",52,99,18,MUTED,false);
  button(c,"‹",50,124,54,48,in(mx,my,50,124,54,48));button(c,"›",706,124,54,48,in(mx,my,706,124,54,48));
  String title=data==null?"상점 불러오는 중":data.shops().stream().filter(s->s.id().equals(data.shop())).map(s->s.title()+" · "+s.id()).findFirst().orElse(data.shop().isEmpty()?"등록된 상점 없음":data.shop());fitted(c,title,120,148,26,570,GOLD,true);
  button(c,"새로고침",820,124,200,48,!busy&&in(mx,my,820,124,200,48));button(c,"손 아이템 등록",1034,124,236,48,!busy&&in(mx,my,1034,124,236,48));
  box(c,50,188,550,48,search.focus());search.draw(c,56,190,538,44,"상품 이름 / UUID 검색");button(c,"검색",614,188,146,48,!busy&&in(mx,my,614,188,146,48));
  if(data!=null){for(int row=0;row<8&&row+scroll<data.products().size();row++){var p=data.products().get(row+scroll);int y=252+row*60;boolean h=in(mx,my,50,y,710,55);box(c,50,y,710,55,p.id().equals(selected)||h);var item=icons.getOrDefault(p.id(),ItemStack.EMPTY);if(!item.isEmpty()){c.getMatrices().push();c.getMatrices().translate(60,y+5,0);c.getMatrices().scale(2.7f,2.7f,1);c.drawItem(item,0,0);c.getMatrices().pop();if(h)hover=item;}fitted(c,p.name(),116,y+19,22,610,WHITE,true);fitted(c,"구매 "+(p.buy().isEmpty()?"차단":p.buy())+" · 판매 "+(p.sell().isEmpty()?"차단":p.sell()),116,y+43,18,610,MUTED,false);}
   if(data.products().isEmpty())center(c,"상품이 없습니다. 검색 또는 손 아이템 등록",400,448,21,MUTED);
  }
  button(c,"이전 목록",50,748,180,46,!busy&&!previous.isEmpty()&&in(mx,my,50,748,180,46));button(c,"다음 목록",580,748,180,46,!busy&&data!=null&&data.nextOffset()>=0&&in(mx,my,580,748,180,46));center(c,data==null?"":(data.offset()+1)+"번부터 · 화면 내 스크롤",405,770,18,MUTED);
  label(c,creating?"새 상품 등록":"선택 상품 수정",820,211,27,WHITE,true);label(c,creating?"저장 시 서버의 주 손 아이템을 복사":"아이템 원본 유지 · 이름과 가격 수정",820,249,18,MUTED,false);
  label(c,"상품 이름",820,292,20,GOLD,false);fieldBox(c,name,310,"상품 이름");
  label(c,"구매 가격 · 유저가 지불",820,395,20,GOLD,false);button(c,buyAllowed?"구매 허용":"구매 차단",1094,375,176,42,buyAllowed);fieldBox(c,buy,431,"0 이상 · 소수점 6자리");
  label(c,"판매 가격 · 유저가 받음",820,522,20,GOLD,false);button(c,sellAllowed?"판매 허용":"판매 차단",1094,502,176,42,sellAllowed);fieldBox(c,sell,558,"0 이상 · 소수점 6자리");
  label(c,"무료 거래는 0 · 차단은 허용 버튼으로 설정",820,635,18,MUTED,false);button(c,busy?"처리 중…":creating?"등록 확인":"수정 확인",820,678,450,55,!busy&&in(mx,my,820,678,450,55));button(c,"상품 삭제…",820,748,450,46,!busy&&!creating&&in(mx,my,820,748,450,46));
  toast(c,50,821,1220);
  if(confirmation!=null){HudMesh.quad(c,0,0,1340,900,0xCF07131E,0xCF07131E);box(c,310,280,720,340,false);center(c,confirmation.action()==ShopAdminProtocol.DELETE?"이 상품을 삭제할까요?":"이 설정을 저장할까요?",670,329,29,WHITE);fitted(c,confirmation.name(),355,388,26,630,GOLD,true);label(c,confirmation.action()==ShopAdminProtocol.DELETE?"목록에서 제거합니다. 이전 거래 기록은 유지됩니다.":"구매 "+confirmation.buy()+" · 판매 "+confirmation.sell(),355,442,20,MUTED,false);label(c,confirmation.action()==ShopAdminProtocol.ADD?"서버의 현재 주 손 아이템을 원본 그대로 등록합니다.":"다른 관리자의 변경이 있으면 저장되지 않습니다.",355,480,18,MUTED,false);button(c,"취소",355,535,250,52,in(mx,my,355,535,250,52));button(c,"확인",735,535,250,52,in(mx,my,735,535,250,52));}
 }finally{end(c);}if(confirmation==null&&!hover.isEmpty()&&entrance.ready())c.drawItemTooltip(textRenderer,hover,mouseX,mouseY);
 }
 @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return super.mouseClicked(x,y,button);var f=fit();double mx=f.x(x),my=f.y(y);
  if(confirmation!=null){if(in(mx,my,355,535,250,52))confirmation=null;else if(in(mx,my,735,535,250,52))confirm();return true;}
  if(in(mx,my,1190,36,100,46)){close();return true;}if(busy)return true;
  if(in(mx,my,50,124,54,48))cycle(-1);else if(in(mx,my,706,124,54,48))cycle(1);
  else if(in(mx,my,820,124,200,48)){previous.clear();list(data==null?"":data.shop(),0);}
  else if(in(mx,my,1034,124,236,48))newProduct();
  else if(in(mx,my,614,188,146,48)){previous.clear();list(data==null?"":data.shop(),0);}
  else if(in(mx,my,50,748,180,46)&&!previous.isEmpty())list(data.shop(),previous.pop());
  else if(in(mx,my,580,748,180,46)&&data!=null&&data.nextOffset()>=0){previous.push(data.offset());list(data.shop(),data.nextOffset());}
  else if(in(mx,my,1094,375,176,42))buyAllowed=!buyAllowed;
  else if(in(mx,my,1094,502,176,42))sellAllowed=!sellAllowed;
  else if(in(mx,my,820,678,450,55))prepare(creating?ShopAdminProtocol.ADD:ShopAdminProtocol.EDIT);
  else if(in(mx,my,820,748,450,46)&&!creating)prepare(ShopAdminProtocol.DELETE);
  else {search.click(mx,my);name.click(mx,my);buy.click(mx,my);sell.click(mx,my);if(data!=null)for(int row=0;row<8&&row+scroll<data.products().size();row++)if(in(mx,my,50,252+row*60,710,55)){select(data.products().get(row+scroll).id());break;}}
  return true;
 }
 @Override public boolean mouseScrolled(double x,double y,double h,double v){if(confirmation==null&&!busy&&data!=null)scroll=Math.clamp(scroll-(int)Math.signum(v),0,Math.max(0,data.products().size()-8));return true;}
 @Override public boolean keyPressed(int key,int scan,int mods){if(confirmation!=null){if(key==256)confirmation=null;return true;}if(busy)return key==256?super.keyPressed(key,scan,mods):true;if(search.focus()&&(key==257||key==335)){previous.clear();list(data==null?"":data.shop(),0);return true;}for(var i:List.of(search,name,buy,sell))if(i.focus()&&i.key(key,scan,mods))return true;return super.keyPressed(key,scan,mods);}
 @Override public boolean charTyped(char ch,int mods){if(busy||confirmation!=null)return true;for(var i:List.of(search,name,buy,sell))if(i.focus()&&i.typed(ch,mods))return true;return super.charTyped(ch,mods);}
 @Override public void close(){if(data!=null&&!busy)ShopAdminClient.request(this,new Request(ShopAdminProtocol.CLOSE,1,data.session(),data.shop(),0,"","","off","off","",0));ShopAdminClient.detach(this);client.setScreen(null);}
}
