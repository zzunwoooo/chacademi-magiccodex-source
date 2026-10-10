package kr.chacademi.chatlayout;

import com.ebicep.chatplus.config.AnchorPoint;
import com.ebicep.chatplus.config.Config;
import com.ebicep.chatplus.config.ConfigKt;
import com.ebicep.chatplus.features.chattabs.ChatTab;
import com.ebicep.chatplus.features.chatwindows.ChatWindow;
import com.ebicep.chatplus.features.chatwindows.ChatWindowsManager;
import com.ebicep.chatplus.features.chatwindows.TabSettings;
import com.ebicep.chatplus.hud.ChatManager;
import com.ebicep.chatplus.hud.ChatRenderer;
import java.io.IOException;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dynamic native windows; the same ChatTab owns its messages, filters and counters throughout a move. */
public final class ChatLayout {
    private static final Logger LOG=LoggerFactory.getLogger("chacademi-chat-layout");
    private static final Set<String> BADGE_TABS=Set.of("기숙사","파티","귓말");
    private static final int IVORY=0xFFEDE8D8, ICON=0xE6C6AA70, BUTTON=0x8A0D2131, HOVER=0xB31E5960;
    private static LayoutStateStore store;
    private static LayoutState state;
    private static final IdentityHashMap<ChatWindow,WindowState> states=new IdentityHashMap<>();
    private static final IdentityHashMap<ChatWindow,IdleFade> idle=new IdentityHashMap<>();
    private static final Set<ChatWindow> clipped=Collections.newSetFromMap(new IdentityHashMap<>());
    public static double backgroundOpacity(ChatWindow window){
        WindowState saved=states.get(window);
        return saved!=null&&saved.backgroundOpacity!=null?saved.backgroundOpacity:state==null?0.5:state.backgroundOpacity;
    }
    public static boolean managesWindow(ChatWindow window){return panels.containsKey(window);}
    public static float windowOpacity(ChatWindow window){
        long now=net.minecraft.Util.getMillis(),stamp=0;
        for(ChatTab tab:window.getTabSettings().getTabs())stamp=Math.max(stamp,tab.getLastMessageTime());
        IdleFade fade=idle.get(window);
        if(fade==null){fade=new IdleFade(now,stamp);idle.put(window,fade);}
        return fade.update(now,stamp,InteractionMode.interactionAllowed(Minecraft.getInstance().screen));
    }
    private static final IdentityHashMap<ChatWindow,LayoutMath.Panel> panels=new IdentityHashMap<>();
    private static int viewportWidth,viewportHeight;
    private static boolean renderingHeaderTabs;
    private static ChatWindow gestureWindow,tabDragWindow;
    private static DragSession drag;
    private static ResizeSession resize;
    private static ChatTab tabDrag;
    private static double tabStartX,tabStartY,tabDragCenter,lastMouseX,lastMouseY;
    private static boolean tabMoved,consumedPress;
    private static final PendingViewRestore<ChatTab> pendingViews=new PendingViewRestore<>();
    private static final Set<ChatWindow> geometryUpdating=Collections.newSetFromMap(new IdentityHashMap<>());
    private ChatLayout(){}

    private static void loadState(){
        if(state==null){
            store=new LayoutStateStore(FabricLoader.getInstance().getConfigDir().resolve("chacademi-chat-layout.json"));
            state=store.load();
        }
    }

    private static List<String> names(ChatWindow window){
        var result=new ArrayList<String>();
        for(ChatTab tab:window.getTabSettings().getTabs())result.add(tab.getName());
        return result;
    }

    private static boolean systemOnly(ChatWindow window){
        var names=new HashSet<>(names(window));
        return names.equals(Set.of("시스템"))||names.equals(Set.of("시스템","전투"));
    }

    private static boolean refresh(){
        if(!Config.INSTANCE.getLoaded()||!Config.INSTANCE.getValues().getEnabled()){
            panels.clear();return false;
        }
        // The addon owns movement handles and hover chrome. Older installs can still enable native overlays.
        var config=Config.INSTANCE.getValues();
        if(config.getMovableChatEnabled()||config.getHoverHighlightEnabled()){
            config.setMovableChatEnabled(false);
            config.setHoverHighlightEnabled(false);
            ConfigKt.setQueueUpdateConfig(true);
        }
        loadState();
        var screen=Minecraft.getInstance().getWindow();
        viewportWidth=screen.getGuiScaledWidth();viewportHeight=screen.getGuiScaledHeight();
        if(viewportWidth<LayoutMath.MIN_WIDTH+4||viewportHeight<90){panels.clear();return false;}
        var windows=Config.INSTANCE.getValues().getChatWindows();
        var liveTabs=Collections.newSetFromMap(new IdentityHashMap<ChatTab,Boolean>());
        for(ChatWindow window:windows)liveTabs.addAll(window.getTabSettings().getTabs());
        pendingViews.retainKeys(liveTabs);
        states.keySet().removeIf(window->!windows.contains(window));
        idle.keySet().removeIf(window->!windows.contains(window));
        var claimed=new HashSet<String>();
        for(var entry:states.entrySet())claimed.add(entry.getValue().id);
        boolean dirty=false;
        for(int index=0;index<windows.size();index++){
            ChatWindow window=windows.get(index);
            PrivateConversations.filters(window);
            String signature=LayoutState.signature(names(window));
            WindowState saved=states.get(window);
            if(saved==null){
                saved=WindowMatcher.find(state.windows,signature,claimed,
                        (double)window.getRenderer().getX()/viewportWidth,
                        (double)(window.getRenderer().getY()-LayoutMath.HEADER)/viewportHeight);
                if(saved==null){
                    saved=new WindowState();
                    saved.systemDefault=systemOnly(window);
                    if(saved.systemDefault){saved.y=-1;}
                    else if(index>0){saved.x+=Math.min(index,8)*0.025;saved.y+=Math.min(index,8)*0.035;}
                }
                states.put(window,saved);claimed.add(saved.id);dirty=true;
            }
            if(!signature.equals(saved.signature)){saved.signature=signature;dirty=true;}
            if(window.getTabSettings().getHideTabs()){
                window.getTabSettings().setHideTabs(false);ConfigKt.setQueueUpdateConfig(true);
            }
            if(!window.getTabSettings().getShowTabsWhenChatNotOpen()){
                window.getTabSettings().setShowTabsWhenChatNotOpen(true);ConfigKt.setQueueUpdateConfig(true);
            }
        }
        var active=new ArrayList<WindowState>();
        for(ChatWindow window:windows)active.add(states.get(window));
        if(state.windows.size()!=active.size()||!new HashSet<>(state.windows).equals(new HashSet<>(active)))dirty=true;
        state.windows=active;
        panels.clear();
        // Resolve ordinary windows first, then attach only an uninitialized legacy system pane.
        for(ChatWindow window:windows){
            WindowState saved=states.get(window);
            if(saved.y==-1&&saved.systemDefault)continue;
            panels.put(window,calculate(window,saved));
        }
        for(ChatWindow window:windows){
            WindowState saved=states.get(window);
            if(saved.y==-1&&saved.systemDefault){
                LayoutMath.Panel first=null;
                for(ChatWindow candidate:windows){
                    if(candidate!=window&&!states.get(candidate).systemDefault&&panels.containsKey(candidate)){
                        first=panels.get(candidate);break;
                    }
                }
                if(first!=null)saved.anchor((double)first.frame().x()/viewportWidth,(double)first.frame().bottom()/viewportHeight);
                else saved.anchor(0.0125,0.18);
                panels.put(window,calculate(window,saved));dirty=true;
            }
            int inset=ContentInsets.nativePadding(window.getRenderer().getUpdatedScale());
            int rightInset=AppearanceControls.rightPadding(window.getRenderer().getUpdatedScale());
            boolean horizontalPadding=window.getPadding().getLeft()!=inset||window.getPadding().getRight()!=rightInset;
            boolean bottomPadding=window.getPadding().getBottom()!=ContentInsets.BOTTOM;
            if(horizontalPadding){
                for(ChatTab tab:window.getTabSettings().getTabs()){
                    if(!pendingViews.contains(tab))pendingViews.capture(tab,tab.getChatScrollbarPos(),tab.getNewMessageSinceScroll());
                }
                window.getPadding().setLeft(inset);window.getPadding().setRight(rightInset);
                for(ChatTab tab:window.getTabSettings().getTabs())tab.queueRefreshDisplayedMessages(true);
                ConfigKt.setQueueUpdateConfig(true);
            }
            if(bottomPadding){window.getPadding().setBottom(ContentInsets.BOTTOM);ConfigKt.setQueueUpdateConfig(true);}
            applyGeometry(window,panels.get(window),horizontalPadding||bottomPadding);
            updateTabCoordinates(window);
        }
        if(dirty)save();
        return !panels.isEmpty();
    }

    private static LayoutMath.Panel calculate(ChatWindow window,WindowState saved){
        int twoLines=(int)Math.ceil(2.0*window.getRenderer().getUpdatedLineHeight()*window.getRenderer().getUpdatedScale())+ContentInsets.BOTTOM;
        int naturalWidth=2+ContentInsets.TAB_BACKGROUND+HeaderLayout.CONTROLS;
        for(ChatTab tab:window.getTabSettings().getTabs())naturalWidth+=Minecraft.getInstance().font.width(tab.getName())+2*ChatTab.PADDING+1;
        LayoutMath.Panel panel=HeaderLayout.apply(LayoutMath.window(viewportWidth,viewportHeight,saved,twoLines,naturalWidth),naturalWidth,viewportWidth);
        if(Minecraft.getInstance().screen instanceof ChatScreen&&window==ChatManager.INSTANCE.getSelectedWindow()
                &&!window.getGeneralSettings().getDisabled())panel=InputLayout.attach(panel);
        return panel;
    }

    public static void beforeRender(GuiGraphics graphics){
        PrivateConversations.tick();
        if(!InteractionMode.interactionAllowed(Minecraft.getInstance().screen)&&(drag!=null||resize!=null||tabDrag!=null))finishGesture();
        refresh();
    }

    public static boolean beforeWindowRender(ChatWindow window,GuiGraphics graphics,int mx,int my){
        var panel=panels.get(window);
        if(panel==null)return false;
        if(panel.mode()!=UiState.Mode.CLOSED){
            var frame=panel.frame();
            float opacity=windowOpacity(window);
            if(opacity>0)graphics.fill(frame.x(),panel.header().bottom(),frame.right(),frame.bottom(),color(backgroundOpacity(window)*opacity,0x0D2131));
            var header=panel.header();
            graphics.fill(header.x(),header.top(),header.right(),header.bottom(),color(backgroundOpacity(window),0x0D2131));
        }
        if(panel.nativeBodyVisible()&&windowOpacity(window)>0){
            clipped.add(window);
            var clip=ContentInsets.messageClipWithControls(panel.body());
            graphics.enableScissor(clip.x(),clip.top(),clip.right(),clip.bottom());
            return false;
        }
        drawHeaderTabs(graphics,window,panel);
        drawChrome(graphics,window,panel,mx,my);
        return true;
    }

    public static void afterWindowRender(ChatWindow window,GuiGraphics graphics,int mx,int my){
        var panel=panels.get(window);
        if(panel!=null&&clipped.remove(window)){
            graphics.disableScissor();drawHeaderTabs(graphics,window,panel);drawChrome(graphics,window,panel,mx,my);
        }
    }

    public static boolean shouldRenderTabs(TabSettings settings){
        return !managesTabs(settings)||renderingHeaderTabs;
    }
    private static void drawHeaderTabs(GuiGraphics graphics,ChatWindow window,LayoutMath.Panel panel){
        if(panel.mode()==UiState.Mode.CLOSED)return;
        var area=HeaderLayout.tabs(panel);
        graphics.enableScissor(area.x(),area.top(),area.right(),area.bottom());
        renderingHeaderTabs=true;
        try{window.getTabSettings().renderTabs(graphics);}
        finally{renderingHeaderTabs=false;graphics.disableScissor();}
    }
    /** Own fill precedes native transparent tab backgrounds and native text. */
    public static void beforeTabsRender(TabSettings settings,GuiGraphics graphics){
        ChatWindow window=settings.getChatWindow();
        var panel=panels.get(window);
        if(panel==null||panel.mode()==UiState.Mode.CLOSED)return;
        updateTabCoordinates(window);
        ChatTab selected=settings.getSelectedTab();
        int x=Math.max(panel.body().x(),selected.getXStart());
        int right=Math.min(panel.body().right(),selected.getXEnd());
        if(right>x&&selected.getYStart()>=panel.header().top()){
            graphics.fill(x,selected.getYStart(),right,selected.getYStart()+ChatTab.TAB_HEIGHT,0xB31E5960);
        }
    }

    private static void updateTabCoordinates(ChatWindow window){
        var settings=window.getTabSettings();var tabs=settings.getTabs();
        int first=Math.max(0,Math.min(settings.getStartRenderTabIndex(),Math.max(0,tabs.size()-1)));
        int x=window.getRenderer().getInternalX()+tabXOffset(settings),y=window.getRenderer().getInternalY()+1+tabYOffset(settings);
        for(int index=first;index<tabs.size();index++){
            ChatTab tab=tabs.get(index);
            tab.setWidth(Minecraft.getInstance().font.width(tab.getName())+2*ChatTab.PADDING);
            tab.setXStart(x);tab.setYStart(y);
            x+=tab.getWidth()+1;
        }
    }

    /** Restore the selected native pane once when opening chat, retaining the native edit box. */
    public static void beginInput(){
        if(!refresh())return;
        ensureInputOwner(true);
        refresh();
    }

    private static void ensureInputOwner(boolean restoreSelected){
        if(!(Minecraft.getInstance().screen instanceof ChatScreen)||state==null)return;
        ChatWindow selected=ChatManager.INSTANCE.getSelectedWindow();
        WindowState saved=states.get(selected);
        if(saved!=null&&!selected.getGeneralSettings().getDisabled()){
            if(restoreSelected){
                boolean changed=saved.mode==UiState.Mode.CLOSED||saved.mode==UiState.Mode.MINIMIZED;
                if(saved.mode==UiState.Mode.CLOSED)saved.open();
                if(saved.mode==UiState.Mode.MINIMIZED)saved.minimize();
                if(changed){refresh();save();}
            }
            if(saved.mode==UiState.Mode.NORMAL||saved.mode==UiState.Mode.MAXIMIZED)return;
        }
        var windows=Config.INSTANCE.getValues().getChatWindows();
        for(int index=windows.size()-1;index>=0;index--){
            ChatWindow candidate=windows.get(index);var panel=panels.get(candidate);
            if(panel!=null&&panel.nativeBodyVisible()&&!candidate.getGeneralSettings().getDisabled()){
                ChatWindowsManager.INSTANCE.selectWindow(candidate);return;
            }
        }
        Minecraft.getInstance().setScreen(null);
    }

    /** Current GUI bounds; callers relocate the existing EditBox rather than replace it. */
    public static LayoutMath.Rect inputBounds(){
        if(!(Minecraft.getInstance().screen instanceof ChatScreen)||!refresh())return null;
        return currentInputBounds();
    }
    private static LayoutMath.Rect currentInputBounds(){
        if(!(Minecraft.getInstance().screen instanceof ChatScreen)||!Config.INSTANCE.getLoaded())return null;
        ChatWindow selected=ChatManager.INSTANCE.getSelectedWindow();
        var panel=panels.get(selected);
        return panel==null||selected.getGeneralSettings().getDisabled()?null:InputLayout.field(panel);
    }
    public static boolean hasManagedInput(){return currentInputBounds()!=null;}
    public static int tabXOffset(TabSettings settings){
        return panels.containsKey(settings.getChatWindow())?ContentInsets.TAB_BACKGROUND:0;
    }
    public static int tabYOffset(TabSettings settings){
        var panel=panels.get(settings.getChatWindow());
        return panel==null?0:panel.header().top()-settings.getChatWindow().getRenderer().getInternalY();
    }
    public static float inputDepth(){
        return InputLayout.overlayDepth(Config.INSTANCE.getValues().getChatWindows().size());
    }
    public static boolean managesTabs(TabSettings settings){return panels.containsKey(settings.getChatWindow());}
    public static ChatTab clickedManagedTab(TabSettings settings,double mx,double my){
        ChatWindow window=settings.getChatWindow();
        var panel=panels.get(window);
        if(panel==null||panel.mode()==UiState.Mode.CLOSED||window.getGeneralSettings().getDisabled())return null;
        updateTabCoordinates(window);
        var tabs=settings.getTabs();
        var bounds=new ArrayList<LayoutMath.Rect>();
        for(ChatTab tab:tabs)bounds.add(new LayoutMath.Rect(tab.getXStart(),tab.getYStart(),tab.getWidth(),ChatTab.TAB_HEIGHT));
        int index=TabHitTest.find(bounds,settings.getStartRenderTabIndex(),HeaderLayout.tabs(panel),mx,my);
        return index<0?null:tabs.get(index);
    }
    public static boolean paintInput(GuiGraphics graphics){
        var field=currentInputBounds();
        if(field==null)return false;
        var box=InputLayout.background(field);
        graphics.fill(box.x(),box.top(),box.right(),box.bottom(),0xB30D2131);
        border(graphics,box,color(state.borderOpacity,0xC6AA70));
        return true;
    }

    public static Boolean managedHitArea(ChatWindow window,double mx,double my){
        var panel=panels.get(window);
        return panel==null?null:!window.getGeneralSettings().getDisabled()
                &&panel.mode()!=UiState.Mode.CLOSED&&panel.frame().contains(mx,my);
    }

    private static void applyGeometry(ChatWindow window,LayoutMath.Panel panel,boolean forced){
        ChatRenderer renderer=window.getRenderer();
        int height=Math.max(LayoutMath.MIN_HEIGHT,panel.body().height());
        int bottom=panel.body().bottom(),top=bottom-height;
        boolean changed=forced||window.getGeneralSettings().getAnchorPoint()!=AnchorPoint.TOP_LEFT
                ||window.getTabSettings().getPosition()!=TabSettings.Position.BOTTOM
                ||renderer.getX()!=panel.body().x()||renderer.getY()!=top
                ||renderer.getWidth()!=panel.body().width()||renderer.getHeight()!=height;
        boolean cold=!PendingViewRestore.cacheReady(renderer.getLineHeight(),renderer.getScale());
        if(!changed&&!cold)return;
        geometryUpdating.add(window);
        try{
            window.getGeneralSettings().setAnchorPoint(AnchorPoint.TOP_LEFT);
            window.getTabSettings().setPosition(TabSettings.Position.BOTTOM);
            renderer.setHeight(height);
            // Native setWidth invokes rescale callbacks before it returns. Seed its cached denominator first.
            if(cold)renderer.updateCachedDimension();
            renderer.setWidth(panel.body().width());
            renderer.setX(panel.body().x());renderer.setY(bottom);renderer.updateCachedDimension();
        }finally{
            geometryUpdating.remove(window);
        }
        for(ChatTab tab:window.getTabSettings().getTabs())
            pendingViews.onGeometryReady(tab,viewReady(tab),snapshot->restoreView(tab,snapshot));
    }

    private static ChatWindow frontWindow(double mx,double my){
        var windows=Config.INSTANCE.getValues().getChatWindows();
        for(int index=windows.size()-1;index>=0;index--){
            ChatWindow window=windows.get(index);
            var panel=panels.get(window);
            if(panel!=null&&!window.getGeneralSettings().getDisabled()&&panel.frame().contains(mx,my))return window;
        }
        return null;
    }

    private static ChatWindow dropWindow(double mx,double my){
        ChatWindow window=frontWindow(mx,my);
        return window!=null&&panels.get(window).mode()!=UiState.Mode.CLOSED?window:null;
    }

    public static boolean handleClick(double mx,double my,int button){
        if(button!=0||!InteractionMode.interactionAllowed(Minecraft.getInstance().screen)||!refresh())return false;
        ChatWindow window=frontWindow(mx,my);
        if(window==null)return false;
        if(clickPanel(window,panels.get(window),mx,my)){
            if(tabDrag==null&&Config.INSTANCE.getValues().getChatWindows().contains(window))ChatWindowsManager.INSTANCE.selectWindow(window);
            consumedPress=true;
            ensureInputOwner(false);
            refresh();
            return true;
        }
        if(InteractionMode.isMagicCursor(Minecraft.getInstance().screen)&&panels.get(window).nativeBodyVisible()){
            ChatWindowsManager.INSTANCE.selectWindow(window);
            consumedPress=true;return true;
        }
        return false;
    }

    private static final HudScroll wheel=new HudScroll();
    public static boolean handleScroll(double mx,double my,double amount){
        if(!InteractionMode.isMagicCursor(Minecraft.getInstance().screen)||!refresh()){wheel.reset();return false;}
        var window=frontWindow(mx,my);
        if(window==null||!panels.get(window).nativeBodyVisible()){wheel.reset();return false;}
        int lines=wheel.lines(window.getTabSettings().getSelectedTab(),amount,net.minecraft.client.gui.screens.Screen.hasShiftDown());
        if(lines!=0)window.getTabSettings().getSelectedTab().scrollChat(lines);
        return true;
    }
    public static ChatTab privateTab(PrivatePeer peer,String name,boolean create){
        if(!refresh())return null;
        var windows=Config.INSTANCE.getValues().getChatWindows();
        for(var window:windows)for(var tab:window.getTabSettings().getTabs()){
            if(peer.equals(PrivateConversations.peer(tab))){
                if(create){
                    tab.getCurrentSettings().setName(name);
                    var saved=states.get(window);
                    if(saved.mode==UiState.Mode.CLOSED)saved.open();
                    refresh();ConfigKt.setQueueUpdateConfig(true);
                }
                return tab;
            }
        }
        if(!create||windows.isEmpty())return null;
        var source=ChatManager.INSTANCE.getSelectedWindow();
        var window=source.clone();window.updateWindowReference();
        window.getRenderer().setScale(source.getRenderer().getScale());
        window.getRenderer().updateCachedDimension();
        var settings=new com.ebicep.chatplus.features.chattabs.ServerChatTabSettings();
        settings.setName(name);settings.setPattern("(?!)");settings.updateRegex();
        settings.setAutoPrefix(peer.marker());settings.setCommandsOverrideAutoPrefix(false);
        settings.setAlwaysAdd(false);settings.setSkipOthers(false);
        var tab=new ChatTab(window,settings);tab.setCurrentSettings(settings);settings.setChatTab(tab);
        window.getTabSettings().getTabs().clear();window.getTabSettings().getTabs().add(tab);
        window.getTabSettings().setSelectedTabIndex(0);window.getTabSettings().setStartRenderTabIndex(0);
        window.updateWindowReference();window.getTabSettings().resetSortedChatTabs(false);
        var saved=new WindowState();saved.x=Math.min(0.65,0.04+windows.size()*0.025);
        saved.y=Math.min(0.65,0.25+windows.size()*0.035);saved.width=0.32;saved.height=0.18;
        saved.backgroundOpacity=states.get(source).backgroundOpacity;
        states.put(window,saved);
        // Keep the currently typed recipient selected while a new sender appears.
        windows.add(Math.max(0,windows.size()-1),window);
        ChatManager.INSTANCE.resetGlobalSortedTabs();ConfigKt.setQueueUpdateConfig(true);
        refresh();save();return tab;
    }
    private static boolean closePrivate(ChatWindow window){
        var tab=window.getTabSettings().getSelectedTab();
        if(PrivateConversations.peer(tab)==null)return false;
        PrivateConversations.closed(tab);
        var tabs=window.getTabSettings().getTabs();tabs.remove(tab);
        if(tabs.isEmpty()){
            Config.INSTANCE.getValues().getChatWindows().remove(window);states.remove(window);panels.remove(window);
        }else{
            window.getTabSettings().setSelectedTabIndex(Math.min(window.getTabSettings().getSelectedTabIndex(),tabs.size()-1));
            window.getTabSettings().setStartRenderTabIndex(0);window.updateWindowReference();
            window.getTabSettings().resetSortedChatTabs(false);
        }
        ChatManager.INSTANCE.resetGlobalSortedTabs();ConfigKt.setQueueUpdateConfig(true);
        refresh();ensureInputOwner(false);save();return true;
    }
    private static boolean clickPanel(ChatWindow window,LayoutMath.Panel panel,double mx,double my){
        WindowState saved=states.get(window);
        if(panel.mode()==UiState.Mode.CLOSED){saved.open();refresh();save();return true;}
        for(int index=0;index<4;index++){
            if(panel.control(index).contains(mx,my)){
                switch(index){
                    case 0->saved.toggleLock();case 1->saved.maximize();
                    case 2->saved.minimize();case 3->{if(!closePrivate(window))saved.close();}default->throw new IllegalStateException();
                }
                refresh();save();return true;
            }
        }
        if(panel.nativeBodyVisible()){
            for(int index=0;index<4;index++){
                if(!AppearanceControls.button(panel.body(),index).contains(mx,my))continue;
                int direction=index%2==0?1:-1;
                if(index<2){
                    saved.backgroundOpacity=AppearanceControls.opacity(backgroundOpacity(window),direction);
                }else{
                    ChatRenderer renderer=window.getRenderer();
                    float scale=AppearanceControls.scale(renderer.getUpdatedScale(),direction);
                    if(scale!=renderer.getUpdatedScale()){
                        for(ChatTab tab:window.getTabSettings().getTabs())
                            if(!pendingViews.contains(tab))pendingViews.capture(tab,tab.getChatScrollbarPos(),tab.getNewMessageSinceScroll());
                        window.getGeneralSettings().setScale(scale);
                        geometryUpdating.add(window);
                        try{renderer.updateCachedDimension();}
                        finally{geometryUpdating.remove(window);}
                        for(ChatTab tab:window.getTabSettings().getTabs())tab.queueRefreshDisplayedMessages(true);
                        ConfigKt.setQueueUpdateConfig(true);
                    }
                }
                refresh();save();return true;
            }
        }
        if(panel.nativeBodyVisible()&&panel.resizeHandle().contains(mx,my)){
            if(saved.locked)return true;
            gestureWindow=window;
            saved.anchor((double)panel.frame().x()/viewportWidth,(double)panel.frame().top()/viewportHeight);
            if(saved.mode==UiState.Mode.MAXIMIZED)saved.maximize();
            saved.resize((double)panel.body().width()/viewportWidth,(double)panel.body().height()/viewportHeight);
            resize=ResizeSession.begin(panel.body().width(),panel.body().height(),mx,my,viewportWidth,viewportHeight);
            refresh();return true;
        }
        if(panel.tabs()>0&&HeaderLayout.tabs(panel).contains(mx,my)){
            ChatTab clicked=window.getTabSettings().getClickedTab(mx,my);
            if(clicked!=null){
                tabDragWindow=window;tabDrag=clicked;
                tabStartX=mx/viewportWidth;tabStartY=my/viewportHeight;
                tabDragCenter=(clicked.getXStart()+clicked.getWidth()/2.0)/viewportWidth;
                lastMouseX=mx;lastMouseY=my;tabMoved=false;return true;
            }
        }
        if(!panel.header().contains(mx,my))return false;
        if(saved.locked)return true;
        double x=(double)panel.frame().x()/viewportWidth,y=(double)panel.frame().top()/viewportHeight;
        if(saved.mode==UiState.Mode.MAXIMIZED)saved.maximize();
        saved.anchor(x,y);gestureWindow=window;refresh();
        drag=DragSession.begin(x,y,mx,my,viewportWidth,viewportHeight);
        return true;
    }

    public static boolean handleDrag(double mx,double my,int button){
        if(button!=0||!InteractionMode.interactionAllowed(Minecraft.getInstance().screen)||!refresh())return false;
        if(tabDrag!=null){
            lastMouseX=mx;lastMouseY=my;
            double dx=(mx/viewportWidth-tabStartX)*viewportWidth,dy=(my/viewportHeight-tabStartY)*viewportHeight;
            if(dx*dx+dy*dy>9)tabMoved=true;
            ChatWindow target=dropWindow(mx,my);
            if(tabMoved&&target==tabDragWindow&&HeaderLayout.tabs(panels.get(target)).contains(mx,my))reorderDraggedTab(mx);
            return true;
        }
        if(resize!=null){
            var size=resize.move(mx,my,viewportWidth,viewportHeight);
            states.get(gestureWindow).resize(size.width(),size.height());refresh();return true;
        }
        if(drag==null)return false;
        var anchor=drag.move(mx,my,viewportWidth,viewportHeight);
        states.get(gestureWindow).anchor(anchor.x(),anchor.y());refresh();return true;
    }

    private static void reorderDraggedTab(double mx){
        TabSettings settings=tabDragWindow.getTabSettings();List<ChatTab> tabs=settings.getTabs();
        int from=TabTransfer.identityIndex(tabs,tabDrag);
        if(from<0)return;
        int first=Math.max(0,Math.min(settings.getStartRenderTabIndex(),tabs.size()-1));
        var widths=new ArrayList<Integer>();for(ChatTab tab:tabs)widths.add(Math.max(1,tab.getWidth()));
        double center=(tabDragCenter+mx/viewportWidth-tabStartX)*viewportWidth;
        int to=CategorySlots.target(widths,first,tabDragWindow.getRenderer().getInternalX()+tabXOffset(settings),from,center);
        if(from==to)return;
        ChatTab selected=settings.getSelectedTab();
        var unread=unreadSnapshot();
        replaceTabs(tabDragWindow,CategoryOrder.move(tabs,from,to),selected);
        updateTabCoordinates(tabDragWindow);
        restoreUnread(unread);ChatManager.INSTANCE.resetGlobalSortedTabs();ConfigKt.setQueueUpdateConfig(true);
    }

    public static boolean handleRelease(double mouseX,double mouseY,int button){
        if(button!=0||(drag==null&&resize==null&&tabDrag==null&&!consumedPress))return false;
        boolean ready=refresh();
        if(tabDrag!=null&&ready&&Config.INSTANCE.getValues().getChatWindows().contains(tabDragWindow)){
            lastMouseX=mouseX;lastMouseY=mouseY;
            if(!tabMoved){
                ChatWindowsManager.INSTANCE.selectWindow(tabDragWindow);
                // A newly selected pane gains the input row; refresh before computing the tab click point.
                refresh();
                tabDragWindow.getTabSettings().handleClickedTab((tabDrag.getXStart()+tabDrag.getXEnd())/2.0,
                        tabDrag.getYStart()+ChatTab.TAB_HEIGHT/2.0);
                tabDrag.setUnreadCount(0);
                ensureInputOwner(true);
            }else{
                ChatWindow target=dropWindow(lastMouseX,lastMouseY);
                if(target!=tabDragWindow&&lastMouseX>=0&&lastMouseX<viewportWidth
                        &&lastMouseY>=0&&lastMouseY<viewportHeight-2){
                    transferDraggedTab(target,lastMouseX,lastMouseY);
                }
            }
        }
        finishGesture();consumedPress=false;return true;
    }

    private static int insertionIndex(ChatWindow window,double mx,double my){
        var panel=panels.get(window);var settings=window.getTabSettings();var tabs=settings.getTabs();
        if(!HeaderLayout.tabs(panel).contains(mx,my))return tabs.size();
        int first=Math.max(0,Math.min(settings.getStartRenderTabIndex(),Math.max(0,tabs.size()-1)));
        double x=window.getRenderer().getInternalX()+tabXOffset(settings);
        for(int index=first;index<tabs.size();index++){
            int width=Math.max(1,tabs.get(index).getWidth());
            if(mx<x+width/2.0)return index;
            x+=width+1;
        }
        return tabs.size();
    }

    private static LayoutMath.Panel normalPanel(ChatWindow window){
        WindowState saved=states.get(window),normal=new WindowState();
        normal.x=saved.x;normal.y=saved.y;normal.width=saved.width;normal.height=saved.height;
        normal.systemDefault=saved.systemDefault;
        return calculate(window,normal);
    }
    private static void freezeDefaultSize(ChatWindow window){
        WindowState saved=states.get(window);
        var normal=normalPanel(window);
        if(saved.width==null)saved.width=(double)normal.body().width()/viewportWidth;
        if(saved.height==null)saved.height=(double)normal.body().height()/viewportHeight;
    }

    private static void transferDraggedTab(ChatWindow destination,double mx,double my){
        ChatWindow source=tabDragWindow;
        var sourceTabs=source.getTabSettings().getTabs();
        if(TabTransfer.identityIndex(sourceTabs,tabDrag)<0)return;
        var unread=unreadSnapshot();
        var sourceNormal=normalPanel(source);
        freezeDefaultSize(source);
        if(destination!=null)freezeDefaultSize(destination);
        ChatTab sourceSelection=source.getTabSettings().getSelectedTab();
        ChatTab destinationSelection=destination==null?null:destination.getTabSettings().getSelectedTab();
        boolean created=destination==null;
        WindowState newState=null;
        if(created){
            destination=source.clone(); // Only style/settings are cloned; the real tab is transferred below.
            destination.updateWindowReference();
            destination.getRenderer().setScale(source.getRenderer().getScale());
            // Bootstrap with the native temporary tab still present: width clamping may synchronously rescale it.
            destination.getRenderer().updateCachedDimension();
            destination.getTabSettings().getTabs().clear();
            newState=new WindowState();newState.systemDefault=tabDrag.getName().equals("시스템");
            newState.anchor(mx/viewportWidth,my/viewportHeight);
            if(!newState.systemDefault)newState.height=0.16;
            newState.width=(double)sourceNormal.body().width()/viewportWidth;
        }
        var destinationTabs=destination.getTabSettings().getTabs();
        int insertion=created?0:insertionIndex(destination,mx,my);
        var plan=TabTransfer.plan(sourceTabs,destinationTabs,tabDrag,sourceSelection,destinationSelection,insertion);
        pendingViews.capture(tabDrag,tabDrag.getChatScrollbarPos(),tabDrag.getNewMessageSinceScroll());
        var windows=Config.INSTANCE.getValues().getChatWindows();
        if(plan.source().isEmpty()){
            windows.remove(source);sourceTabs.clear();states.remove(source);panels.remove(source);
        }else{
            ChatTab selected=plan.source().get(plan.sourceSelection());
            replaceTabs(source,plan.source(),selected);
        }
        if(created){windows.add(destination);states.put(destination,newState);}
        replaceTabs(destination,plan.destination(),plan.destination().get(plan.destinationSelection()));
        // Front the drop destination without switch events: a drag must never auto-send or clear unread counts.
        windows.remove(destination);windows.add(destination);
        for(ChatWindow window:windows){
            window.updateWindowReference();window.getTabSettings().resetSortedChatTabs(false);
        }
        ChatManager.INSTANCE.resetGlobalSortedTabs();restoreUnread(unread);
        ConfigKt.setQueueUpdateConfig(true);
        tabDrag.queueRefreshDisplayedMessages(true);
        refresh();ensureInputOwner(false);
        refresh();
        restoreUnread(unread);
    }

    private static void replaceTabs(ChatWindow window,List<ChatTab> replacement,ChatTab selected){
        var settings=window.getTabSettings();var tabs=settings.getTabs();
        tabs.clear();tabs.addAll(replacement);
        int index=TabTransfer.identityIndex(tabs,selected);
        settings.setSelectedTabIndex(Math.max(0,index));
        settings.setStartRenderTabIndex(Math.min(settings.getStartRenderTabIndex(),Math.max(0,tabs.size()-1)));
        window.updateWindowReference();settings.resetSortedChatTabs(false);
    }

    private static IdentityHashMap<ChatTab,Integer> unreadSnapshot(){
        var unread=new IdentityHashMap<ChatTab,Integer>();
        for(ChatWindow window:Config.INSTANCE.getValues().getChatWindows())
            for(ChatTab tab:window.getTabSettings().getTabs())unread.put(tab,tab.getUnreadCount());
        return unread;
    }
    private static void restoreUnread(IdentityHashMap<ChatTab,Integer> unread){
        unread.forEach(ChatTab::setUnreadCount);
    }

    /** Native reflow keeps the original messages; retain only a moved tab's viewport through its refresh. */
    public static void afterTabRescale(ChatTab tab){
        if(!pendingViews.contains(tab))return;
        pendingViews.onRescale(tab,viewReady(tab),snapshot->restoreView(tab,snapshot));
    }
    public static void afterTabRefresh(ChatTab tab){
        if(!pendingViews.contains(tab))return;
        pendingViews.onRefresh(tab,tab.getRefreshing(),viewReady(tab),snapshot->restoreView(tab,snapshot));
    }
    private static boolean viewReady(ChatTab tab){
        ChatWindow window=tab.getChatWindow();ChatRenderer renderer=window.getRenderer();
        return !geometryUpdating.contains(window)
                &&PendingViewRestore.cacheReady(renderer.getLineHeight(),renderer.getScale());
    }
    private static void restoreView(ChatTab tab,PendingViewRestore.Snapshot snapshot){
        tab.setScrollPos(snapshot.scroll());tab.setNewMessageSinceScroll(snapshot.newMessages());
    }

    public static void handleRemoved(){finishGesture();consumedPress=false;}
    private static void finishGesture(){
        boolean changed=drag!=null||resize!=null;
        if(drag!=null&&panels.containsKey(gestureWindow)){
            var panel=panels.get(gestureWindow);
            states.get(gestureWindow).anchor((double)panel.frame().x()/viewportWidth,(double)panel.frame().top()/viewportHeight);
        }
        drag=null;resize=null;gestureWindow=null;tabDrag=null;tabDragWindow=null;tabMoved=false;
        if(changed&&state!=null)save();
    }
    private static void save(){
        try{store.save(state);}catch(IOException failed){LOG.warn("Could not save chat layout state");}
    }

    private static void drawChrome(GuiGraphics graphics,ChatWindow window,LayoutMath.Panel panel,int mx,int my){
        var font=Minecraft.getInstance().font;
        boolean interactive=InteractionMode.interactionAllowed(Minecraft.getInstance().screen)&&frontWindow(mx,my)==window;
        float alpha=1;
        float bodyAlpha=windowOpacity(window);
        int edge=color(state.borderOpacity*alpha,0xC6AA70);
        if(panel.mode()==UiState.Mode.CLOSED){
            var frame=panel.frame();
            graphics.fill(frame.x(),frame.top(),frame.right(),frame.bottom(),interactive?HOVER:BUTTON);
            border(graphics,frame,edge);graphics.drawString(font,"열기",frame.x()+4,frame.top()+2,IVORY,false);return;
        }
        if(bodyAlpha>0)border(graphics,panel.frame(),IdleFade.color(edge,bodyAlpha));
        var header=panel.header();
        border(graphics,header,edge);
        // The header deliberately has no caption; its blank region remains a drag surface.
        String tooltip=null;
        WindowState saved=states.get(window);
        for(int index=0;index<4;index++){
            var button=panel.control(index);
            boolean hovered=interactive&&button.contains(mx,my);
            graphics.fill(button.x(),button.top(),button.right(),button.bottom(),IdleFade.color(hovered?HOVER:BUTTON,alpha));
            icon(graphics,button,index,saved.locked,alpha);
            if(hovered)tooltip=switch(index){
                case 0->saved.locked?"이동·크기 잠금 해제":"이동·크기 잠금";
                case 1->saved.locked?"잠금 중":panel.mode()==UiState.Mode.MAXIMIZED?"복원":"최대화";
                case 2->panel.mode()==UiState.Mode.MINIMIZED?"본문 복원":"최소화";
                default->"닫기";
            };
        }
        if(panel.nativeBodyVisible()&&!saved.locked&&bodyAlpha>0){
            var frame=panel.frame();int tint=IdleFade.color(interactive&&panel.resizeHandle().contains(mx,my)?ICON:edge,bodyAlpha);
            graphics.fill(frame.right()-5,frame.bottom()-2,frame.right()-1,frame.bottom()-1,tint);
            graphics.fill(frame.right()-2,frame.bottom()-5,frame.right()-1,frame.bottom()-1,tint);
        }
        if(tabMoved&&tabDrag!=null&&dropWindow(lastMouseX,lastMouseY)==window&&window!=tabDragWindow){
            var frame=panel.frame();
            border(graphics,new LayoutMath.Rect(frame.x()+2,frame.top()+2,frame.width()-4,frame.height()-4),0xDD4F9E9E);
        }
        if(panel.nativeBodyVisible()&&(bodyAlpha>0||interactive)){
            for(int index=0;index<4;index++){
                var button=AppearanceControls.button(panel.body(),index);
                boolean hovered=interactive&&button.contains(mx,my);
                graphics.fill(button.x(),button.top(),button.right(),button.bottom(),hovered?HOVER:BUTTON);
                int x=button.x()+2,y=button.top()+button.height()/2;
                graphics.fill(x,y,x+5,y+1,ICON);
                if(index%2==0)graphics.fill(x+2,y-2,x+3,y+3,ICON);
                if(hovered)tooltip=index<2
                    ?(index==0?"배경 더 진하게":"배경 더 투명하게")+" ("+Math.round(backgroundOpacity(window)*100)+"%)"
                    :(index==2?"글씨 크게":"글씨 작게")+" ("+Math.round(window.getRenderer().getUpdatedScale()*100)+"%)";
            }
        }
        drawUnreadBadges(graphics,window,panel);
        if(tooltip!=null&&tabDrag==null){
            int width=font.width(tooltip)+6,x=Math.max(2,Math.min(mx+6,viewportWidth-width-2));
            int y=Math.max(2,Math.min(my+12,viewportHeight-14));
            graphics.pose().pushPose();graphics.pose().translate(0,0,400);
            graphics.fill(x,y,x+width,y+12,0xE60D2131);
            graphics.drawString(font,tooltip,x+3,y+2,IVORY,false);
            graphics.pose().popPose();
        }
    }

    public static void afterRender(GuiGraphics graphics,int mx,int my){
        if(tabDrag==null||!tabMoved||!InteractionMode.interactionAllowed(Minecraft.getInstance().screen))return;
        var font=Minecraft.getInstance().font;
        String label=tabDrag.getName();
        int width=font.width(label)+10;
        int x=Math.max(2,Math.min(mx+7,viewportWidth-width-2)),y=Math.max(2,Math.min(my+8,viewportHeight-16));
        graphics.pose().pushPose();graphics.pose().translate(0,0,400);
        graphics.fill(x,y,x+width,y+14,0xDD0D2131);
        border(graphics,new LayoutMath.Rect(x,y,width,14),color(state.borderOpacity,0xC6AA70));
        graphics.drawString(font,label,x+5,y+3,IVORY,false);
        graphics.pose().popPose();
    }

    private static void drawUnreadBadges(GuiGraphics graphics,ChatWindow window,LayoutMath.Panel panel){
        var font=Minecraft.getInstance().font;var settings=window.getTabSettings();var tabs=settings.getTabs();
        int first=Math.max(0,settings.getStartRenderTabIndex());
        for(int index=first;index<tabs.size();index++){
            ChatTab tab=tabs.get(index);
            if((!BADGE_TABS.contains(tab.getName())&&PrivateConversations.peer(tab)==null)||tab.getUnreadCount()<=0||tab.getWidth()<=0
                    ||tab.getXStart()>=HeaderLayout.tabs(panel).right())continue;
            String text=tab.getUnreadCount()>999?"999+":Integer.toString(tab.getUnreadCount());
            float scale=0.65f;int width=Math.max(9,(int)Math.ceil(font.width(text)*scale)+4);
            int x=Math.max(panel.body().x(),Math.min(tab.getXEnd()-width+2,HeaderLayout.tabs(panel).right()-width));
            int y=Math.max(0,tab.getYStart()-6);
            graphics.fill(x,y,x+width,y+9,0xF2ED1C24);
            graphics.pose().pushPose();
            graphics.pose().translate(x+(width-font.width(text)*scale)/2.0f,y+1.5f,0);
            graphics.pose().scale(scale,scale,1);graphics.drawString(font,text,0,0,0xFFFFFFFF,false);
            graphics.pose().popPose();
        }
    }
    private static int color(double opacity,int rgb){return((int)Math.round(Math.max(0,Math.min(1,opacity))*255)<<24)|rgb;}
    private static void icon(GuiGraphics graphics,LayoutMath.Rect button,int kind,boolean locked,float alpha){
        int tint=IdleFade.color(ICON,alpha);
        int x=button.x()+2,y=button.top()+(button.height()-5)/2;
        if(kind==0){
            border(graphics,new LayoutMath.Rect(x,y+2,5,4),tint);
            graphics.fill(x+1,y,x+2,y+3,tint);graphics.fill(x+1,y,x+4,y+1,tint);
            if(locked)graphics.fill(x+3,y,x+4,y+3,tint);else graphics.fill(x+4,y,x+5,y+2,tint);
        }else if(kind==1)border(graphics,new LayoutMath.Rect(x,y,5,5),tint);
        else if(kind==2)graphics.fill(x,y+4,x+5,y+5,tint);
        else for(int i=0;i<5;i++){
            graphics.fill(x+i,y+i,x+i+1,y+i+1,tint);
            graphics.fill(x+4-i,y+i,x+5-i,y+i+1,tint);
        }
    }
    private static void border(GuiGraphics graphics,LayoutMath.Rect rect,int color){
        graphics.fill(rect.x(),rect.top(),rect.right(),rect.top()+1,color);
        graphics.fill(rect.x(),rect.bottom()-1,rect.right(),rect.bottom(),color);
        graphics.fill(rect.x(),rect.top()+1,rect.x()+1,rect.bottom()-1,color);
        graphics.fill(rect.right()-1,rect.top()+1,rect.right(),rect.bottom()-1,color);
    }
}
