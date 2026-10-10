# Chacademi Chat Layout 1.3.9

Fabric client addon for Minecraft 1.21.4 and ChatPlus 2.8.1 (Java 21). Compile dependency only; no Fabric API or MagicCodex hard dependency.

Open chat using the current chat key (Enter/T). The original Minecraft edit box moves into the selected pane, below its messages, with tabs sharing the top header with the window controls. A 20 GUI-unit input row is added while that pane is selected: messages, including a default two-line System pane, retain their body height. Native typing, text selection, history and command completion remain available. A minimized or closed selected pane is restored when chat opens. Closing/minimizing it while typing switches input to another open pane; if none remain, chat input closes.

With MagicCodex's school.magiccodex.client.HudCursorScreen active (Alt held), the same controls, resizing and tab drag gestures work without opening chat. Clicking a pane body selects the pane used by the next chat input. Outside clicks follow MagicCodex's original handling. Releasing Alt cleans up unfinished gestures and pending cursor movement.

Drag a tab within its strip to reorder, onto another open frame to merge, or onto empty HUD space to detach. A last remaining tab moves its existing pane. Tab objects and message history are retained. Moving tabs does not send chat or reset unread counts. Active native search/bookmark display follows ChatPlus rewrap behavior; stored bookmarks remain untouched.

Each pane has lock, maximize/restore, minimize and close buttons. Drag blank header space to move, and the lower-right grip to resize. Lock blocks pane movement/resize/maximize, while tab movements remain available. Closed panes retain incoming history and show a small 열기 button. Private category badges use native unread counts.

Layout state: config/chacademi-chat-layout.json, schemaVersion 2. Each pane has an id, tab signature, normalized x/y, optional normalized width/height, lock and mode. The previous two-pane state is migrated. backgroundOpacity defaults to 0.50 and borderOpacity to 0.85; edit these between 0 and 1 with the game closed. Delete only this addon state file with the game closed to reset placement. It stores no messages or credentials.

Replace an older chacademi-chat-layout JAR; do not keep duplicate versions. Keep the separately installed ChatPlus 2.8.1 mod. The canonical ChatPlus preset uses transparent native backgrounds, tabs visible in HUD, disabled native movable-chat handles, and disabled round notification badges; this addon paints its own theme and rectangular badges. Optional native bottom-bar icons and the input-length counter are disabled in the matching preset.

Build on hosting only:
JAVA_HOME=C:\Program Files\Java\jdk-21.0.10
GRADLE_USER_HOME=C:\Chacademi\tools\gradle-cache
C:\Chacademi\build-workspace\gradlew.bat -p C:\Chacademi\build-workspace\client-mods\chacademi-chat-layout check build --no-daemon --console=plain

Position/body size follows normalized GUI viewport dimensions. ChatPlus's minimum 130 GUI-unit width and native font/tab/button sizes remain in force. The rendered GUI, IME and MagicCodex runtime integration still need an in-game check; no game has been launched for this build.

ChatPlus upstream: https://github.com/ebicep/ChatPlus (GPL-3.0). See LICENSE and NOTICE.

For a JAR-only update with an older preset, also set sendNoteTextBarElementEnabled, bookmarkTextBarElementEnabled, findMessageTextBarElementEnabled and screenshotChatTextBarElementEnabled to false, plus inputBoxSettings.showInputBoxInputLength=false. These optional widgets have separate native bottom-screen positions; they are not the relocated edit box.

1.3.1 fixes the cold-renderer crash when a tab is detached with the Alt cursor. New renderer caches are initialized before width callbacks, and moved-tab scroll restoration waits until geometry is committed and native line-height/scale caches are ready. Pending state is consumed only after a completed refresh and successful restoration.

1.3.2 lets the current chat/command key open native chat while the MagicCodex Alt cursor is held. The opener is deferred to the next keyboard tick so T or slash is not inserted twice. Messages, tab captions and input text now share an approximately five-GUI-unit inner margin; native padding handles wrapping and message links. Saved pane position/size and category routing remain unchanged.

1.3.3 renders the original input and its background together above all native pane depths, adds vertical glyph padding, and hits displayed tab rectangles instead of the native pre-input Y gate. Native movement and hover overlays are disabled automatically while the addon manages the windows, including JAR-only upgrades. Window selection refreshes geometry before activating a tab. Game-render verification remains pending.

1.3.4 adds an eight-second per-window idle hold and 1.2-second smooth fade. Tabs and unread badges remain; chat/Alt interaction or new messages restore the body. Borderless input gains six GUI units between its text origin and outline and is clipped inside the outline. Optional MagicCodex 0.39.0 HUD callback compatibility keeps status/top-menu UI visible in ChatScreen while retaining inventory/F1 behavior. No MagicCodex JAR replacement is needed.

1.3.5 moves native tabs into a persistent 16-GUI-unit header beside the controls. Rendering and hit tests share the header tab area; the native body pass suppresses tabs and a separate clipped header pass renders them once. Body fading does not affect the header. Minimum width reserves room for controls; minimized panes retain only the header. 113 tests pass; no in-game execution.

1.3.7 adds native bottom padding (5 GUI units) and clips messages inside the body border, excluding the input extension. Default two-line system height includes the padding. 115 tests pass; no game launched.

1.3.7: Command completion and usage/error hints are anchored to the actual selected pane input. Popups choose available space above/below the input and follow detached pane movement; visible rows fit the viewport. Host check/build passed: 120 tests in 20 suites. In-game verification remains manual.

1.3.8: Each open pane has right-side +/- appearance buttons. The top pair adjusts background opacity by 5 percentage points; the bottom pair adjusts message text scale by 5 points (50%-200%). Hover shows a Korean tooltip and current value. Buttons work in ChatScreen and MagicCodex Alt cursor mode. Pane opacity is stored independently in addon layout state (absent values inherit global opacity); text scale uses the native ChatPlus per-window setting and save queue. Message wrapping reserves the rail, and native per-line fills are disabled for managed panes so they do not compound opacity. Input/category captions retain their current size. Legacy state remains supported. Host build/check: 125 tests, 21 suites, no failures. In-game verification remains manual.

## 1.3.9: Wind whisper conversations
Alt cursor wheel scrolls the hovered pane, including older history. Trackpad fractions are accumulated per pane; Shift scrolls one line per wheel unit.

Requires the matching MagicCodex client and server chat.1 builds for private conversations. Incoming Wind Message creates a sender-named private pane, identified by authenticated UUID and scoped to the server/account. X removes the selected private conversation; a later message recreates it. Tabs can still merge/detach. Input in that tab is always sent through Wind Message to that UUID, including text starting with slash. Failures never fall back to public chat. Only one reply request is in flight at a time.

Replies consume mana without a spell cooldown. Existing spell access and server validation still apply. A successfully delivered conversation allows the receiver to reply during that server session. Disconnect clears this permission. The smooth envelope glyph uses MagicCodex's high-resolution texture renderer. Whole-chat excludes envelope-prefixed messages; Whisper includes them.

Build and automated tests passed on hosting. Alt scrolling, private-pane lifecycle, IME and the new glyph still require an in-game integration check. See docs/CHAT-WHISPER-INTEGRATION.md in the repository root.