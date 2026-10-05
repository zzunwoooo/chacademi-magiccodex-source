package school.magiccodex.client;
import java.io.ByteArrayInputStream;import java.math.*;import java.util.*;
import net.minecraft.client.gui.DrawContext;import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;import net.minecraft.nbt.*;import net.minecraft.sound.SoundEvents;import net.minecraft.util.*;
import school.magiccodex.protocol.ShopProtocol;import school.magiccodex.protocol.ShopProtocol.*;

public final class ShopScreen extends SocialScreen {
 private Response data;private Input search,quantity;private String selected="",sentSearch="";private boolean selling;private int scroll;private long searchAt;private Map<String,ItemStack> icons=Map.of();
 private static final Identifier PANEL=Identifier.of("magiccodex","textures/gui/shop/shop-panel.png");
 private static final int X=790,W=781,ROW_Y=221,ROW_H=65,ROWS=7;
 ShopScreen(){super("상점");}
 @Override SocialLayout.Fit fit(){float s=.85f*Math.min(width*.92f/1672,height*.90f/941);return new SocialLayout.Fit((width-1672*s)/2,(height-941*s)/2,s);}
 @Override protected void init(){super.init();search=new Input("상품 검색",80,search==null?"":search.text());quantity=new Input("수량",9,quantity==null?"1":quantity.text());quantity.widget.setTextPredicate(v->v.matches("[0-9]{0,9}"));}
 void receive(Response r){data=r;busy=false;notice(r.message());var map=new HashMap<String,ItemStack>();for(var p:r.products())map.put(p.id(),decode(p.preview()));icons=Map.copyOf(map);if(visible().stream().noneMatch(p->p.id().equals(selected)))selected=visible().isEmpty()?"":visible().getFirst().id();scroll=Math.clamp(scroll,0,Math.max(0,visible().size()-ROWS));}
 void failed(String m){busy=false;notice(m);}
 void tradeSucceeded(){client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,1.25f,.30f));}
 private ItemStack decode(byte[] b){if(b.length==0||client.world==null)return ItemStack.EMPTY;try{return ItemStack.fromNbt(client.world.getRegistryManager(),NbtIo.readCompressed(new ByteArrayInputStream(b),NbtSizeTracker.of(2097152))).orElse(ItemStack.EMPTY);}catch(Exception e){return ItemStack.EMPTY;}}
 private String name(Product p){var stack=icons.getOrDefault(p.id(),ItemStack.EMPTY);return stack.isEmpty()?p.name():ItemTooltipRenderer.localName(stack,stack.getName()).getString();}
 private List<Product> visible(){if(data==null)return List.of();String q=search==null?"":search.text().toLowerCase(Locale.ROOT);return data.products().stream().filter(p->!(selling?p.sell():p.buy()).isEmpty()&&(name(p).toLowerCase(Locale.ROOT).contains(q)||p.name().toLowerCase(Locale.ROOT).contains(q))).toList();}
 private Product selected(){return data==null?null:data.products().stream().filter(p->p.id().equals(selected)).findFirst().orElse(null);}
 private int number(){try{return ShopProtocol.quantity(quantity.text());}catch(Exception e){return 0;}}
 private BigDecimal total(){var p=selected();if(p==null||number()==0)return null;try{return new BigDecimal(selling?p.sell():p.buy()).multiply(BigDecimal.valueOf(number()));}catch(Exception e){return null;}}
 private int possible(){var p=selected();if(p==null)return 0;var icon=icons.getOrDefault(p.id(),ItemStack.EMPTY);int max=Math.min(576,(icon.isEmpty()?64:icon.getMaxCount())*9);if(selling)return Math.min(max,p.owned());try{var price=new BigDecimal(p.buy());if(price.signum()==0)return max;if(data.balance().isEmpty())return 0;return Math.min(max,new BigDecimal(data.balance()).divide(price,0,RoundingMode.DOWN).min(BigDecimal.valueOf(max)).intValue());}catch(Exception e){return 0;}}
 private void request(int action){if(data==null||busy)return;var p=selected();if((action==ShopProtocol.BUY||action==ShopProtocol.SELL)&&(p==null||number()==0||number()>possible())){notice("가능 수량 안에서 숫자를 입력해 주세요.");return;}busy=ShopClient.request(this,new Request(action,1,data.session(),data.shop(),data.revision(),p==null?"":p.id(),quantity.text(),UUID.randomUUID().toString(),search.text()));if(!busy)notice("상점 서버 연결 또는 처리 상태를 확인해 주세요.");}
 @Override public void tick(){super.tick();if(search!=null&&!search.text().equals(sentSearch)&&!busy&&data!=null){long now=Util.getMeasuringTimeMs();if(searchAt==0)searchAt=now+450;if(now>=searchAt){sentSearch=search.text();searchAt=0;scroll=0;selected=visible().isEmpty()?"":visible().getFirst().id();if(sentSearch.isEmpty()||visible().isEmpty())request(ShopProtocol.OPEN);}}}
 @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){var f=fit();double mx=f.x(mouseX),my=f.y(mouseY);ItemStack hovered=ItemStack.EMPTY;start(c);try{
  if(client.getResourceManager().getResource(PANEL).isPresent())images.drawTexture(c,PANEL,0,0,0,0,1672,941,1672,941,1672,941,0xDBDEE5EF);else asset(c,"friends_panel",0,0,1672,941,0,0,1340,1174,0xDBDEE5EF);
  label(c,data==null?"상점":data.title(),70,66,39,WHITE,true);FriendsScreen.cross(c,1598,64,in(mx,my,1576,42,44,44)?CYAN:WHITE);
  String portrait=data==null?"elena-neutral":data.portrait();var npc=Identifier.of("magiccodex","textures/gui/dialogue/"+portrait+".png");
  c.enableScissor(65,125,708,875);try{if(client.getResourceManager().getResource(npc).isPresent())images.drawTexture(c,npc,40,205,0,0,630,945,1024,1536,1024,1536,0xFFFFFFFF);}finally{c.disableScissor();}
  images.drawTexture(c,Identifier.of("magiccodex","textures/hud/top-menu.png"),1193,50,451,436,30,30,199,194,1774,887,0xFFFFFFFF);fitted(c,(data==null||data.balance().isEmpty()?"—":data.balance())+" G",1234,66,30,310,GOLD,true);
  button(c,"구매",X,159,150,46,!selling||in(mx,my,X,159,150,46));button(c,"판매",954,159,150,46,selling||in(mx,my,954,159,150,46));box(c,1122,159,449,46,search.focus());search.draw(c,1128,161,437,42,"상품 검색");
  var list=visible();for(int i=0;i<ROWS&&i+scroll<list.size();i++){var p=list.get(i+scroll);int ry=ROW_Y+i*ROW_H;boolean hover=in(mx,my,X,ry,W,59);box(c,X,ry,W,59,p.id().equals(selected)||hover);var icon=icons.getOrDefault(p.id(),ItemStack.EMPTY);
   if(!icon.isEmpty()){c.getMatrices().push();c.getMatrices().translate(802,ry+4,0);c.getMatrices().scale(3.15f,3.15f,1);c.drawItem(icon,0,0);c.getMatrices().pop();if(hover)hovered=icon;}
   fitted(c,name(p),865,ry+(selling?22:30),28,447,WHITE,true);if(selling)label(c,"보유 "+p.owned(),865,ry+45,17,MUTED,false);fitted(c,(selling?p.sell():p.buy())+" G",1330,ry+31,25,220,GOLD,false);
  }
  if(list.isEmpty())center(c,"검색 결과가 없습니다",1180,418,25,MUTED);
  var chosen=selected();fitted(c,chosen==null?"상품을 선택해 주세요":name(chosen),X,748,29,510,WHITE,true);
  label(c,(selling?"판매":"구매")+" 가능 수량 "+possible(),X,786,18,MUTED,false);
  center(c,"수량",1002,790,18,MUTED);button(c,"−",852,815,58,56,!busy&&number()>1&&in(mx,my,852,815,58,56));box(c,922,815,160,56,quantity.focus());float qw=Math.min(148,type.width(quantity.text().isEmpty()?"1":quantity.text(),23,BODY)+24);quantity.draw(c,928+(148-qw)/2,819,qw,48,"수량");button(c,"+",1094,815,58,56,!busy&&number()<possible()&&in(mx,my,1094,815,58,56));
  BigDecimal sum=total();label(c,"합계",1290,743,18,MUTED,false);fitted(c,sum==null?"—":sum.stripTrailingZeros().toPlainString()+" G",1290,782,40,281,WHITE,true);
  button(c,busy?"처리 중…":selling?"판매하기":"구매하기",1285,815,286,56,!busy&&number()>0&&number()<=possible()&&in(mx,my,1285,815,286,56));toast(c,X,674,W);
 }finally{end(c);}
 // Native item-tooltip entrypoint uses the existing rich MagicCodex renderer and Shift+wheel scrolling.
 if(!hovered.isEmpty()&&entrance.ready())c.drawItemTooltip(textRenderer,hovered,mouseX,mouseY);
 }
 @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return super.mouseClicked(x,y,button);var f=fit();double mx=f.x(x),my=f.y(y);if(in(mx,my,1576,42,44,44)){sound(1.1f,.16f);close();return true;}if(busy)return true;
  if(in(mx,my,X,159,150,46)||in(mx,my,954,159,150,46)){selling=in(mx,my,954,159,150,46);scroll=0;selected=visible().isEmpty()?"":visible().getFirst().id();quantity.widget.setText("1");sound(1.1f,.16f);return true;}
  var list=visible();for(int i=0;i<ROWS&&i+scroll<list.size();i++)if(in(mx,my,X,ROW_Y+i*ROW_H,W,59)){selected=list.get(i+scroll).id();quantity.widget.setText("1");sound(1.1f,.14f);return true;}
  if(in(mx,my,852,815,58,56)){if(number()>1){quantity.widget.setText(Integer.toString(number()-1));sound(1.1f,.14f);}}else if(in(mx,my,1094,815,58,56)){if(number()<possible()){quantity.widget.setText(Integer.toString(Math.max(1,number()+1)));sound(1.1f,.14f);}}else if(in(mx,my,1285,815,286,56)){if(number()>0&&number()<=possible()){sound(1.1f,.16f);request(selling?ShopProtocol.SELL:ShopProtocol.BUY);}}else{search.click(mx,my);if(in(mx,my,922,815,160,56))quantity.click(Math.clamp(mx,quantity.x,quantity.x+quantity.w-.1),Math.clamp(my,quantity.y,quantity.y+quantity.h-.1));else quantity.focus(false);}return true;
 }
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.clamp(scroll-(int)Math.signum(vertical),0,Math.max(0,visible().size()-ROWS));return true;}
 @Override public boolean keyPressed(int key,int scan,int mods){if(search.focus()&&search.key(key,scan,mods)||quantity.focus()&&quantity.key(key,scan,mods))return true;return super.keyPressed(key,scan,mods);}
 @Override public boolean charTyped(char c,int mods){return search.focus()&&search.typed(c,mods)||quantity.focus()&&quantity.typed(c,mods)||super.charTyped(c,mods);}
 @Override public void close(){if(data!=null&&!busy)request(ShopProtocol.CLOSE);ShopClient.detach(this);client.setScreen(null);}
}