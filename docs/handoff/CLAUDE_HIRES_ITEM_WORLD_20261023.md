# 고해상도 아이템: 손·3인칭·바닥·액자도 매끈하게 (2026-10-23, 미빌드)

- 브랜치 `claude/hires-item-world-20261023` (← `claude/npc-buttons-20261023` @ b97de93). **MagicCodex 클라이언트 모드만** 바뀜. 서버·프로토콜 변경 없음.
- 증상(오너 스크린샷): 128px 아이콘을 손에 들면 바닐라 nearest 확대로 픽셀 계단이 보이고, 3인칭·바닥에서는 `item/generated` 의 픽셀별 돌출 옆면이 테두리에 털처럼 붙음.

## 바뀐 것

- `ItemModelManagerMixin`: `ItemModelManager.update(...)` 두 오버로드 RETURN 에서 렌더 상태에 고해상도 모델 id 를 붙임 (`HiResItemState` 덕 인터페이스). GUI 모드·반짝임(마법 부여)·조건 밖 아이템은 null = 바닐라.
- `ItemRenderStateMixin`: `ItemRenderState.render` HEAD 에서 id 가 있으면 `ItemIconTextures.renderWorld` 로 그리고 바닐라 취소. `clear` 에서 id 초기화.
- `ItemIconTextures.renderWorld`: 모델 자체 display 변환(`getTransformation().apply(leftHand, …)`) → 아이템 평면(z=8/16)에 앞·뒤 사각형 2장(간격 1/1000 블록). 레이어 `RenderLayer.getItemEntityTranslucentCull(월드용 텍스처)` = 바닐라 아이템과 같은 조명·오버레이·외곽선.
- 월드용 텍스처를 아이콘마다 하나 더 올림: 알파 미리곱 없이(바닐라 엔티티 셰이더용), 투명 텍셀 RGB 는 가장 가까운 보이는 색으로 채움(`bleed`) → 확대·밉맵 때 검은 테두리 없음.
- `CodexTextureFilterMixin`: `magiccodex:runtime_item_icon/*` 는 바닐라 레이어에서도 밉맵 사용(멀리 있는 바닥 아이템 반짝임 방지). 블러는 기존 규칙(magiccodex 네임스페이스 = 부드럽게) 그대로.
- 정사각형이 아닌 텍스처(예: 티켓 256x172)는 GUI·월드 모두 비율 유지, 16x16 칸 가운데 정렬 (이전에는 GUI 에서 늘어남).
- 바닐라 16px 아이템, 32px 이하 텍스처, 조건 밖 모델(엘리먼트·틴트·다층·애니메이션)은 그대로.

## 확인 못 함 / 볼 것

- **Gradle 컴파일 전** (작성 환경에서 Maven 차단). API 이름은 yarn 1.21.4 매핑과 대조: `ItemModelManager.update` 6·7인자, `ItemRenderState.render/clear/getTransformation/leftHand/layerCount`, `Transformation.apply(ZLMatrixStack)`, `RenderLayer.getItemEntityTranslucentCull`, `VertexConsumer.vertex(Matrix4f,FFF)/color(I)/texture/overlay(I)/light(I)/normal(Entry,FFF)`.
- `@Shadow public abstract Transformation getTransformation()` 접근자가 바닐라와 다르면 믹스인 오류 → 고칠 것. 주입은 전부 `require = 0`.
- 호스팅: `.\magic-codex-fabric\gradlew.bat -p magic-codex-fabric test remapJar` (새 테스트 `ItemIconBleedTest`, `CodexTextureFilterTest.runtimeItemIconsAlwaysUseTheirMipChain`).
- 실게임: 1인칭 손(오른손·왼손), F5 3인칭, 바닥에 버린 아이템(가까이·멀리), 아이템 액자, 머리에 쓴 아이템, 마법 부여된 아이템(바닐라로 남아야 함), 인벤토리 티켓 비율.
- 리소스팩 아이콘을 256px 로 바꿔도 코드 변경 없음 (`MAX_SIZE` 1024).
