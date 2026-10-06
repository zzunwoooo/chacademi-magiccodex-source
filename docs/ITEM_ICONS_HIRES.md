# 고해상도 아이템 아이콘 (Fabric UI)

기준일 2026-10-06. 대상 모듈 `magic-codex-fabric`, MC 1.21.4.

## 문제

32px를 넘는 아이템 텍스처(예: 128x128 픽셀아트)를 바닐라 GUI는 16 GUI 단위(화면 32~64px)로 줄일 때 nearest 샘플링으로 텍셀을 건너뛴다. 1px 외곽선이 끊기고 그라데이션이 지글거린다.

## 동작

- `ItemIconMixin`이 `DrawContext.drawItem(LivingEntity, World, ItemStack, int, int, int, int)` HEAD에서 `ItemIconTextures.draw`를 호출한다. 인벤토리·핫바·툴팁·magiccodex 화면 등 GUI에서 그리는 모든 아이템이 이 경로를 거친다. 손에 든 아이템, 바닥에 떨어진 아이템, 아이템 액자는 바닐라 그대로다.
- 적용 조건 (하나라도 아니면 바닐라):
  - `assets/<ns>/items/<id>.json`이 `minecraft:model` 타입이고 `tints`가 없음
  - 모델 parent 체인이 `minecraft:item/generated`(또는 `item/handheld`, `builtin/generated`)로 끝남, `elements`·`display.gui` 없음
  - `layer0`만 있음, `.png.mcmeta`(애니메이션) 없음
  - 텍스처 한 변이 33~1024px
  - 인챈트 반짝임(glint)이 없음
- 처음 보이는 아이템 모델은 그 프레임만 바닐라로 그리고, 별도 스레드(`magiccodex-item-icons`)에서 JSON/PNG를 읽어 밉맵을 만든다. 업로드는 클라이언트 틱마다 최대 4개.
- 리소스 리로드, 접속 해제, 종료 시 `UiResources.reset()`에서 함께 비운다.

## 그리는 방식 (3차, 현재)

- **32px 이하 텍스처**(바닐라 16px 아이템 등): 모드가 손대지 않는다. 바닐라 픽셀 렌더 그대로다.
- **32px 초과 텍스처**(차카데미 128px 아이콘): 픽셀 격자로 줄이지 않고 그림 그대로 부드럽게 그린다. magiccodex UI 이미지(`HudTextureCache`)와 같은 방식이다.
  - 별도 GPU 텍스처를 쓰고, 색은 알파를 미리 곱해 둔다(premultiplied).
  - 밉맵 전체를 만들고, 축소는 `LINEAR_MIPMAP_LINEAR`, 확대는 `LINEAR`로 그린다.
  - 셰이더 `item_icon`은 알파 0.02 미만만 버리고 나머지는 블렌딩한다.

이전 시도 기록:
- 1차는 외곽선 보존 밉맵, 2차는 화면 픽셀 외곽선 셰이더였다.
- 둘 다 128px 아이콘을 "픽셀"로 보이게 하려던 것이라 3차에서 제거했다.
- 128px 텍스처의 1px 선은 칸 크기(약 48px)에서 0.4픽셀이라, 픽셀로 유지하려 할수록 뭉개지거나 딱딱해졌다.

비용: 아이콘 1개당 GPU 메모리 약 90KB. 밉맵은 GPU가 만든다. 매 프레임은 아이템당 사각형 1개.

## 검증 상태

- 3차 렌더 방식은 같은 처리(프리멀티플라이드 + generateMipmap + 트릴리니어)를 WebGL2에서 마력코어로 32/48/64/85px 렌더해 확인. 3차에서 `OutlineMipmaps`와 그 테스트는 삭제됐다.
- 1.21.4 yarn 매핑 대조: `drawItem` 7인자 오버로드, `drawTexture` 13인자, `NativeImage.setColorArgb`, `AbstractTexture.setFilter(ZZ)`, `ResourceFactory.getResource → Optional` 확인.
- 1차는 호스팅 빌드 후 실게임에서 동작(외곽선은 뭉개짐) 확인. **3차 변경은 Gradle 컴파일·실게임 확인 전.** 호스팅에서:
  `.\magic-codex-fabric\gradlew.bat -p magic-codex-fabric test remapJar`
- 실게임에서 볼 것: 인벤토리·핫바에서 128px 아이콘이 픽셀 격자 없이 매끈하게 보이는지, 바닐라 아이템은 그대로 픽셀인지, GUI 배율 2/3/4, 슬롯 하이라이트·개수 숫자가 아이콘 위에 오는지, 펫 화면 확대 아이템이 선명한지, 로그 `High-res item icons active`.
- 믹스인은 `require = 0`: 매핑이 달라 주입이 안 되면 크래시 없이 바닐라로 남는다(로그 문구가 안 나오면 이 경우).
