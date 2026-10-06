package school.magiccodex.paper;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import school.magiccodex.protocol.ManaProtocol;
import school.magiccodex.protocol.SchoolProtocol;

/** Administrator-only facade; every mutation delegates to the subsystem that owns its state. */
final class AdminCommandBridge implements CommandExecutor,TabCompleter {
    private static final List<String> USER_ACTIONS=List.of("스탯","마법","기숙사","호감도");
    private final MagicCodexBridge plugin;
    private final ManaBridge mana;
    private final SchoolBridge school;
    private final EnhancementBridge enhancement;
    private final AppraisalBridge appraisal;
    private final TitleBridge titles;
    private final NpcSocialService social;
    AdminCommandBridge(MagicCodexBridge plugin,ManaBridge mana,SchoolBridge school,EnhancementBridge enhancement,AppraisalBridge appraisal,TitleBridge titles,NpcSocialService social){
        this.plugin=plugin;this.mana=mana;this.school=school;this.enhancement=enhancement;this.appraisal=appraisal;this.titles=titles;this.social=social;
        for(String name:List.of("유저관리","점수관리","아이템관리","칭호추가","칭호삭제")){
            PluginCommand command=Objects.requireNonNull(plugin.getCommand(name));command.setExecutor(this);command.setTabCompleter(this);
        }
    }
    private static void allowed(CommandSender sender,String permission){if(!sender.hasPermission(permission))throw new IllegalArgumentException("권한이 없습니다: "+permission);}
    private Player player(String name){Player player=plugin.names().resolve(name);if(player==null)throw new IllegalArgumentException("접속 중인 유저를 찾을 수 없거나 닉네임이 중복됩니다. 영문 계정명을 사용해 주세요.");return player;}
    private static String joined(String[] args,int start,int end){return String.join(" ",Arrays.copyOfRange(args,start,end));}
    private int userAction(String[] args){for(int i=1;i<args.length;i++)if(USER_ACTIONS.contains(args[i])&&plugin.names().resolve(joined(args,0,i))!=null)return i;for(int i=args.length-1;i>=1;i--)if(plugin.names().resolve(joined(args,0,i))!=null)return i;return 1;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{
            switch(command.getName()){
                case "유저관리"->user(sender,args);
                case "점수관리"->score(sender,args);
                case "아이템관리"->item(sender,args);
                case "칭호추가","칭호삭제"->title(sender,args,command.getName().equals("칭호삭제"));
                default->throw new IllegalArgumentException("등록되지 않은 관리 명령입니다.");
            }
        }catch(NumberFormatException e){sender.sendMessage("수치를 올바른 숫자로 입력해 주세요. 정수 항목에는 소수를 사용할 수 없습니다.");}
        catch(IllegalArgumentException e){sender.sendMessage(e.getMessage()==null?"입력값을 확인해 주세요.":e.getMessage());}
        return true;
    }
    private void user(CommandSender sender,String[] input){
        int index=userAction(input);
        if(input.length<=index)throw new IllegalArgumentException("/유저관리 <닉네임·영문계정> 스탯|마법|기숙사|호감도 ...");
        String action=input[index];allowed(sender,permission(action));Player target=player(joined(input,0,index));
        String[] args=Arrays.copyOfRange(input,index+1,input.length);
        switch(action){
            case "스탯"->{
                if(args.length!=3||!AdminCommandRules.STATS.contains(args[0]))throw new IllegalArgumentException("/유저관리 <유저> 스탯 현재마나|마나|마나회복|마법가속 설정|추가|감소 <수치>");
                if(!plugin.playerStateReady(target))throw new IllegalArgumentException("캐릭터 정보를 불러오는 중입니다.");
                plugin.refreshEquipment(target);UUID id=target.getUniqueId();ManaAccount account=mana.mana.account(id);
                double before=switch(args[0]){case "현재마나"->account.snapshot().current();case "마나"->account.baseMaximum;case "마나회복"->account.baseRegen;default->account.baseHaste;};
                double next=AdminCommandRules.changed(before,args[1],AdminCommandRules.nonnegative(args[2],1_000_000),1_000_000);
                switch(args[0]){case "현재마나"->mana.mana.setCurrent(id,next);case "마나"->mana.mana.setBaseMaximum(id,next);case "마나회복"->mana.mana.setBaseRegeneration(id,next);default->mana.mana.setBaseHaste(id,next);}
                mana.mana.save(target);plugin.savePlayerState(target);var snapshot=mana.mana.account(id).snapshot();
                if(target.getListeningPluginChannels().contains(ManaProtocol.RESPONSE))target.sendPluginMessage(plugin,ManaProtocol.RESPONSE,ManaProtocol.encode(new ManaProtocol.Response(0,ManaProtocol.SNAPSHOT,0,snapshot)));
                double actual=args[0].equals("현재마나")?snapshot.current():next;
                sender.sendMessage(plugin.names().name(target)+" "+args[0]+" = "+AdminCommandRules.number(actual)+(args[0].equals("현재마나")?"":" (장비·버프 제외 기본값)"));
            }
            case "마법"->{
                if(args.length<2||!List.of("습득","미습득").contains(args[0]))throw new IllegalArgumentException("/유저관리 <유저> 마법 습득|미습득 <등록된 마법명·ID>");
                Map<String,String> catalog=DiscoveryLink.registeredSpells();String id=AdminCommandRules.resolve(catalog,joined(args,1,args.length));
                if(id==null)throw new IllegalArgumentException("등록된 마법이 없거나 이름이 중복됩니다. 마법 ID를 선택해 주세요. 발견 플러그인 활성 상태도 확인해 주세요.");
                String name=plugin.names().name(target);
                DiscoveryLink.adminSetLearned(target.getUniqueId(),id,args[0].equals("습득"),error->sender.sendMessage(error==null?name+" · "+catalog.get(id)+" "+args[0]+" 처리 완료":error));
            }
            case "기숙사"->{
                if(args.length!=1)throw new IllegalArgumentException("/유저관리 <유저> 기숙사 <기숙사명>");
                int house=SchoolBridge.parseHouse(args[0]);if(house<0)throw new IllegalArgumentException("등록된 기숙사를 선택해 주세요.");
                String name=plugin.names().name(target);school.adminHouse(target,house,error->sender.sendMessage(error==null?name+"의 기숙사 = "+SchoolProtocol.HOUSES.get(house):error));
            }
            case "호감도"->{
                if(social==null)throw new IllegalArgumentException("NPC 호감도 기능이 준비되지 않았습니다.");
                int op=args.length-1;if(op>=0&&!args[op].equals("조회"))op--;
                if(op<1||!AdminCommandRules.QUERIES.contains(args[op]))throw new IllegalArgumentException("/유저관리 <유저> 호감도 <등록 NPC명·ID> 조회 또는 설정|추가|감소 <정수>");
                String operation=args[op];if(operation.equals("조회")&&op!=args.length-1||!operation.equals("조회")&&op!=args.length-2)throw new IllegalArgumentException("호감도 명령 인수를 확인해 주세요.");
                int value=operation.equals("조회")?0:Integer.parseInt(args[args.length-1]);if(value<0)throw new IllegalArgumentException("호감도 수치는 0 이상의 정수입니다.");
                String npc=AdminCommandRules.resolve(social.registeredNpcs(),joined(args,0,op));if(npc==null)throw new IllegalArgumentException("등록된 NPC가 없거나 이름이 중복됩니다. NPC ID를 선택해 주세요.");
                String name=plugin.names().name(target);
                social.adminChange(target.getUniqueId(),npc,operation,value).whenComplete((row,error)->{
                    if(error!=null){sender.sendMessage("호감도를 처리하지 못했습니다. 입력값과 서버 로그를 확인해 주세요.");return;}
                    sender.sendMessage(name+" · "+npc+" 호감도 "+row.score()+" / "+AffinityStore.gate(row.heart())+" · 하트 "+row.heart());
                });
            }
            default->throw new IllegalArgumentException("스탯, 마법, 기숙사, 호감도 중 선택해 주세요.");
        }
    }
    private static String permission(String branch){return switch(branch){case "스탯"->"magiccodex.mana.admin";case "마법"->"magicdiscovery.admin";case "기숙사"->"magiccodex.school.admin";case "호감도"->"magiccodex.dialogue.admin";default->throw new IllegalArgumentException("스탯, 마법, 기숙사, 호감도 중 선택해 주세요.");};}
    private void score(CommandSender sender,String[] args){
        allowed(sender,"magiccodex.school.admin");
        if(args.length<2||args.length>3||!AdminCommandRules.QUERIES.contains(args[1])||args[1].equals("조회")&&args.length!=2||!args[1].equals("조회")&&args.length!=3)throw new IllegalArgumentException("/점수관리 <기숙사> 조회 또는 추가|감소|설정 <점수>");
        int house=SchoolBridge.parseHouse(args[0]);long value=args.length==3?Long.parseLong(args[2]):0;
        school.adminScore(house,args[1],value,sender::sendMessage);
    }
    private void item(CommandSender sender,String[] args){
        if(args.length==0)throw new IllegalArgumentException("/아이템관리 마법봉 <마력> <추가마나> <마법가속> 또는 마력코어 <코어수> <성공률%> <별잡기보너스%> <상승배율> <성향>");
        allowed(sender,args[0].equals("마법봉")?"magiccodex.enhance.admin":"magiccodex.core.admin");
        if(!(sender instanceof Player target))throw new IllegalArgumentException("아이템은 명령을 실행한 유저에게 지급됩니다. 게임 안에서 실행해 주세요.");
        if(!plugin.playerStateReady(target))throw new IllegalArgumentException("캐릭터 정보를 불러오는 중입니다.");
        if(target.getInventory().firstEmpty()<0)throw new IllegalArgumentException("인벤토리에 빈칸이 필요합니다.");
        ItemStack item;
        switch(args[0]){
            case "마법봉"->{
                if(args.length!=4)throw new IllegalArgumentException("/아이템관리 마법봉 <마력:0~100000> <추가마나:0~100000> <마법가속:0~100000>");
                double power=AdminCommandRules.nonnegative(args[1],100000),mana=AdminCommandRules.nonnegative(args[2],100000),haste=AdminCommandRules.nonnegative(args[3],100000);
                item=new ItemStack(Material.BLAZE_ROD);var meta=item.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("마법봉").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));item.setItemMeta(meta);enhancement.register(item,power,mana,haste);
            }
            case "마력코어"->{
                if(args.length!=6)throw new IllegalArgumentException("/아이템관리 마력코어 <코어수:1~10> <성공률%:0~100> <별잡기보너스%:0~100> <상승배율:0초과~10000> <마력|마나|마법가속>");
                item=appraisal.adminCore(Integer.parseInt(args[1]),AdminCommandRules.nonnegative(args[2],100),AdminCommandRules.nonnegative(args[3],100),AdminCommandRules.nonnegative(args[4],10000),AdminCommandRules.affinity(args[5]));
            }
            default->throw new IllegalArgumentException("마법봉 또는 마력코어를 선택해 주세요.");
        }
        target.getInventory().addItem(item);target.saveData();plugin.savePlayerState(target);sender.sendMessage(args[0]+" 1개를 지급했습니다.");
    }
    private void title(CommandSender sender,String[] args,boolean remove){
        allowed(sender,"magiccodex.titles.admin");
        if(args.length<2)throw new IllegalArgumentException("/"+(remove?"칭호삭제":"칭호추가")+" <닉네임·영문계정> <등록 칭호명·ID>");
        int split=1;for(int i=1;i<args.length;i++)if(plugin.names().resolve(joined(args,0,i))!=null)split=i;
        Player target=player(joined(args,0,split));Map<String,String> catalog=titles.registeredTitles(remove);String id=AdminCommandRules.resolve(catalog,joined(args,split,args.length));
        if(id==null)throw new IllegalArgumentException("등록된 "+(remove?"":"활성 ")+"칭호가 없거나 이름이 중복됩니다. 칭호 ID를 선택해 주세요.");
        titles.grant(target.getUniqueId(),id,remove,ok->sender.sendMessage(ok?"칭호 "+(remove?"삭제":"추가")+" 완료: "+catalog.get(id):"칭호를 저장하지 못했습니다."));
    }
    private List<String> players(){var choices=new ArrayList<String>();for(Player p:Bukkit.getOnlinePlayers()){choices.add(p.getName());String name=plugin.names().name(p);if(plugin.names().resolve(name)==p)choices.add(name);}return choices;}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){
        if(args==null||args.length==0)return List.of();
        try{return complete(sender,command.getName(),args);}catch(IllegalArgumentException e){return List.of();}
    }
    private List<String> complete(CommandSender sender,String command,String[] args){
        String part=args[args.length-1];Collection<String> choices=List.of();
        switch(command){
            case "유저관리"->{
                List<String> branches=USER_ACTIONS.stream().filter(a->sender.hasPermission(permission(a))).toList();if(branches.isEmpty())return List.of();
                if(args.length==1)choices=players();
                else{
                    int branch=userAction(args);if(plugin.names().resolve(joined(args,0,branch))==null)choices=AdminCommandRules.tailChoices(players(),args,0);
                    else if(args.length==branch+1)choices=branches;
                    else if(branch<args.length&&branches.contains(args[branch])){
                        int position=args.length-branch-1;String action=args[branch];
                        choices=switch(action){
                            case "스탯"->AdminCommandRules.statOptions(position);
                            case "기숙사"->position==1?SchoolProtocol.HOUSES:List.of();
                            case "마법"->position==1?List.of("습득","미습득"):position>=2?AdminCommandRules.tailChoices(AdminCommandRules.catalogChoices(DiscoveryLink.registeredSpells()),args,branch+2):List.of();
                            case "호감도"->{
                                if(social==null)yield List.of();
                                int operation=-1;for(int i=branch+2;i<args.length-1;i++)if(AdminCommandRules.QUERIES.contains(args[i]))operation=i;
                                if(operation>=0)yield args.length==operation+2&&!args[operation].equals("조회")?List.of("<호감도:0이상_정수>","0","1","5","20","100"):List.of();
                                String npc=joined(args,branch+1,args.length-1);
                                yield position>=2&&AdminCommandRules.resolve(social.registeredNpcs(),npc)!=null?AdminCommandRules.QUERIES:AdminCommandRules.tailChoices(AdminCommandRules.catalogChoices(social.registeredNpcs()),args,branch+1);
                            }
                            default->List.of();
                        };
                    }
                }
            }
            case "점수관리"->{allowed(sender,"magiccodex.school.admin");choices=args.length==1?SchoolProtocol.HOUSES:args.length==2?AdminCommandRules.QUERIES:args.length==3&&!args[1].equals("조회")?List.of("<점수:정수>","0","1","10","100"):List.of();}
            case "아이템관리"->{
                if(args.length==1){var options=new ArrayList<String>();if(sender instanceof Player&&sender.hasPermission("magiccodex.enhance.admin"))options.add("마법봉");if(sender instanceof Player&&sender.hasPermission("magiccodex.core.admin"))options.add("마력코어");choices=options;}
                else if(sender instanceof Player&&args[0].equals("마법봉")&&sender.hasPermission("magiccodex.enhance.admin"))choices=AdminCommandRules.itemOptions("마법봉",args.length);
                else if(sender instanceof Player&&args[0].equals("마력코어")&&sender.hasPermission("magiccodex.core.admin"))choices=AdminCommandRules.itemOptions("마력코어",args.length);
            }
            case "칭호추가","칭호삭제"->{
                allowed(sender,"magiccodex.titles.admin");int split=0;for(int i=1;i<args.length;i++)if(plugin.names().resolve(joined(args,0,i))!=null)split=i;
                choices=split==0?AdminCommandRules.tailChoices(players(),args,0):AdminCommandRules.tailChoices(AdminCommandRules.catalogChoices(titles.registeredTitles(command.equals("칭호삭제"))),args,split);
            }
            default->{return List.of();}
        }
        return AdminCommandRules.filter(choices,part);
    }
}
