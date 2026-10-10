# Chat / Wind whisper integration (2026-10-10)

Baseline: codex/hires-item-icons-20261006, aa4f63b.
Minecraft 1.21.4 / Fabric; retain ChatPlus 2.8.1.

Artifacts:
- client-mods/chacademi-chat-layout/build/libs/chacademi-chat-layout-1.3.9.jar
- magic-codex-fabric/build/libs/magic-codex-ui-0.39.0-catalog.alpha.2-chat.1+mc1.21.4.jar
- magic-codex-paper/build/libs/magic-codex-bridge-0.30.0-catalog.alpha.1-chat.1.jar

Replace the corresponding older JAR rather than installing duplicate versions. The client and server MagicCodex changes are both required. Files were built, not installed into the running network or a player's launcher.

Alt scrolling is routed only inside a visible chat pane. Private input is bound to server/account and recipient UUID; commands in that input remain literal private text. Closing a private tab cancels pending local replies. Incoming messages do not steal the active typing target. Message history is not persisted by this addon; tab identity and layout follow ChatPlus configuration persistence.

Wind Message keeps permission, availability, mana and protocol validation. It has no spell cooldown. Normal network rate limits remain. A delivered pair may reply during that session, and disconnect revokes the pair. Display format is envelope glyph + sender + colon + message, using a default-font text child so the icon font does not affect the message.

The 192x132 transparent envelope is rendered through the existing high-resolution asynchronous texture pipeline, with a bitmap fallback during loading. The new item model is a texture lookup asset, not a gameplay item.

Verification on hosting: addon check/build, Fabric check/build, Build-Server.ps1 -Verify. The server build uses local ignored dependency directories from the hosting workspace. Added LuckPerms test runtime dependency required by the branch's targeting tests. No Minecraft client was launched; verify Alt scroll, drag/merge/X/reopen, two-player replies, insufficient mana, reconnect, and icon appearance in game before rollout.