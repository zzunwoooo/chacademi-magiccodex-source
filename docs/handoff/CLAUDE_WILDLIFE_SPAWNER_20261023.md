# 야생 몹 자체 스폰 루프 (Claude, 2026-10-23)

브랜치 `claude/wildlife-spawner-20261023` (기준: `codex/story-nameplates-bundle-20261022-task7`) · 모듈 `creature-spawns-paper` · 버전 `0.5.0-spawner-20261023`

## 변경
- 새 `WildSpawner.java`: MythicMobs RandomSpawns 대신 플러그인이 직접 첫 스폰. 미몹 몹(`CA_Wild_<id>`/`_shiny`)을 `MythicMob.spawn(..., SpawnReason.NATURAL)`로 소환 → 기존 `spawn()` 이벤트 재검사·자연 표시·`group()`·`cleanup()`을 그대로 탐.
- `Habitat`: `rarity`(1/2/3/boss), `weight`(선택, 0이면 등급 가중치) 추가. 운영 habitats.yml에 rarity가 없으면 JAR 안 기본 habitats.yml 값 사용.
- `habitats.yml`: 138종에 `rarity` 줄 추가 (1성 33 / 2성 45 / 3성 59 / boss 1 = glacialcore, 예전 spawn-plan.json 기준).
- `config.yml`: `chunk-cap` 6→4, `spawner` 블록 추가(enabled, period-seconds 5, attempts-per-player 3, max-attempts-per-tick 40, rarity-weights 4.5/1.8/0.4/0.06). 블록이 없어도 같은 기본값으로 동작.
- `WildlifePlugin`: 루프 시작/정지/리로드 연결, 루프용 헬퍼(timeAndSeasonOk·chunkFull 등), 상태·진단에 루프 통계.
- `deployment/`: RandomSpawns 패치 스크립트 2개 삭제(더 이상 해당 없음), README를 새 배포 절차로 교체.

## 빌드 시 주의 (Claude 환경에서 Paper/Mythic 저장소 접근 불가 → 컴파일 못 함, 구문만 확인)
- 새로 쓴 API: `World.getHighestBlockYAt(int,int,HeightMap)`, `HeightMap.MOTION_BLOCKING_NO_LEAVES`, `Block.getLightFromSky()`, `YamlConfiguration.loadConfiguration(Reader)`, `JavaPlugin.getResource`, `ActiveMob.getEntity().getBukkitEntity()`. 컴파일 오류가 나면 이 부분부터.
- 기존 테스트 `BiomeCompatibilityTest`는 `Habitat.parse(id,section,warnings)` 3인자 오버로드를 그대로 씀(유지함).
- `HabitatCondition`/`MainThreadSpawnGate`(cahabitat)는 남겨 둠. RandomSpawns 파일이 실수로 남아 있으면 상태 명령에 "(구) 미몹 랜덤스폰 조건 호출" 줄이 뜸.

## 배포
`creature-spawns-paper/deployment/README.txt` 참고. 야생 서버만. 핵심: 새 JAR, RandomSpawns `chacademia_wildlife.yml` 제거, 운영 config.yml `chunk-cap` 4로.

## 2차 (부하 최적화)
- `WildlifePlugin.check()` 순서 변경: 보호 구역 → 청크 개체 수 → 플레이어 거리·동종·지역 개체 수(캐시)를 **블록 검사보다 먼저**. 주변이 꽉 찬 평상시엔 블록을 읽기 전에 실패. 결과 문자열은 같고, 실패 이유가 겹칠 때 어떤 이유로 집계되는지만 바뀜.
- 자체 스폰 루프는 시도 앞부분에서 청크를 이미 세므로 `checkChunkCounted()`로 청크 재계산을 건너뜀(한 시도에 한 번만 셈). 스폰 이벤트 재검사·무리 추가는 기존처럼 청크를 셈.

## 3차 (확률 확인 명령)
- `/월드관리 야생몹 확률 [닉네임]`: 서 있는 자리(바이옴, 물속/지상) 기준으로 스폰 시도가 걸렸을 때 각 종이 뽑힐 % (등급 가중치 비율), 각 종이 이 자리에서 지형 조건상 가능한지, 지금 안 나오는 종과 이유(높이/시간·계절·날씨), 청크·지역 개체 수를 보여 줌. 읽기 전용.
- `Habitat`에 `name` 필드 추가(habitats.yml `name`, 없으면 id).

## 4차 (빈 바이옴 + 크리에이티브)
- 서식지가 하나도 없던 Terralith 바이옴 34개(yellowstone, highlands, shrubland, yosemite_*, painted_mountains, cave/ice_caves 등, Terralith 2.5 바이옴 목록 기준) + 지상 몹이 없던 desert_oasis를 `biome-compatibility.yml`의 새 `terralith-biomes` 섹션으로 비슷한 바이옴 서식지에 연결. 원래 서식지는 그대로 정확히 일치, 연결은 명시적 목록만(와일드카드 없음). 이제 Terralith 바이옴 99개 모두 지상(동굴 포함) 몹이 1종 이상.
- 운영 서버의 `plugins/ChAcademiaWildlife/biome-compatibility.yml`은 파일이 있으면 자동 갱신 안 됨 → **레포 최신 파일로 교체 필수**. 없으면 콘솔에 경고.
- 스폰 대상 플레이어: 생존·모험 → 관전 제외 전부(크리에이티브 포함). 루프 대상과 check()의 플레이어 거리 조건 둘 다. (살아나는 작물은 그대로 생존·모험만)
- 테스트 `terralithBiomesWithoutOwnHabitatReuseSimilarHabitats` 추가.
