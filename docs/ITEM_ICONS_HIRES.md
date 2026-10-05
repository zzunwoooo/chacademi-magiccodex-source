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

## 외곽선 보존 밉맵 (`OutlineMipmaps`)

원본 PNG는 바꾸지 않는다. 레벨 0은 원본, 그 아래 레벨마다:

1. 원본에서 4방향 중 투명과 닿은 불투명 픽셀을 외곽선으로 표시한다.
2. 축소 텍셀의 덮임 비율이 40% 이상이면 실루엣에 포함한다(알파는 0/255만).
3. 실루엣 안쪽 텍셀은 외곽선을 뺀 내부 색만 평균한다. 그라데이션은 평균되고 외곽선 색이 섞여 탁해지지 않는다.
4. 실루엣 가장자리 텍셀은 그 주변(한 블록 여유) 원본 외곽선 색의 평균으로 다시 칠한다. 어느 레벨에서도 1텍셀 불투명 외곽선이 남고, 부위별 외곽선 색조도 유지된다.

GPU 설정: 축소 `LINEAR_MIPMAP_LINEAR`, 확대 `NEAREST`(확대 미리보기에서 픽셀아트가 번지지 않게), `LOD_BIAS -0.5`(GUI 배율 3, 48px에서 덜 흐리게). 프리멀티플라이드 알파, 알파 0.1 미만은 discard(`item_icon` 셰이더)로 바닐라 GUI 아이템과 같은 깊이 정렬을 유지한다.

비용: 128px 아이콘 1개의 밉맵 계산 약 10ms(워커 스레드, 1회), GPU 메모리 약 90KB. 매 프레임 비용은 아이템당 텍스처 사각형 1개.

## 검증 상태

- `OutlineMipmapsTest` 7개: 이 작업 환경에서 JUnit 대체 러너로 실행해 통과.
- 1.21.4 yarn 매핑 대조: `drawItem` 7인자 오버로드, `drawTexture` 13인자, `NativeImage.setColorArgb`, `AbstractTexture.setFilter(ZZ)`, `ResourceFactory.getResource → Optional` 확인.
- **Gradle 컴파일·전체 테스트·실게임 확인은 아직 안 함.** 이 작업 환경에서 Maven/Fabric 저장소 접근이 막혀 있었다. 호스팅에서:
  `.\magic-codex-fabric\gradlew.bat -p magic-codex-fabric test remapJar`
- 실게임에서 볼 것: 인벤토리·핫바에서 128px 아이콘 외곽선이 끊기지 않는지, GUI 배율 2/3/4, 슬롯 하이라이트·개수 숫자가 아이콘 위에 오는지, 펫 화면 확대 아이템이 선명한지, 로그 `High-res item icons active`.
- 믹스인은 `require = 0`: 매핑이 달라 주입이 안 되면 크래시 없이 바닐라로 남는다(로그 문구가 안 나오면 이 경우).
