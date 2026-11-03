# 신규 150종 발견 조건 카운터 명세 (서버 세션 구현용, Claude 2026-11-03)

오너: "조건을 실제로 만들건 없고 서버 세션에다가 말해서 제작할거야 / 궁극들이랑 몇개는 스크롤 사용 / 던전 보상 같은걸로".
오너 2026-11-03: "발견 조건 겹치는거 빼고 / 스크롤로 된건 우리 스크롤 시스템 할로윈에 했던거" → 운영 중 조건과 겹치던 19종 교체, 주문서 → 할로윈 마법 스크롤.

오너 2026-11-03 2차: "너무 바닐라 위주 / 커스텀 시스템과 연동 / 스크롤은 각 원소별 궁극 2개 중 1개만" → 원소마다 5종(30종)을 큐디치·연금술·마법봉 강화·의뢰·기숙사·펫·NPC 호감도·토네이도·작물 각성·고유 몬스터·칭호·파티·스토리 조건으로 교체, 스크롤은 원소별 궁극 1종(6종)만.

오너 2026-11-03 3차: "큐디치·기숙사·계절·연속 의뢰 빼고 / 현재 유저 진행 단계에 맞춰 설계" → 150종 전부 데이터 기준으로 다시 (야생 282명 통계·로그 301명; 1주 안에 채우는 유저 비율 초급≈35% 중급≈25% 고급≈10% 비전≈4% 궁극≈1~2%). 시스템 연동: 연금술 새 조합법·몬스터 전리품 상자·낚시 상자·청크 점유·마법 힌트권·마법 스크롤·야생 우두머리 종류·마법봉 강화.
정의는 `magic-discovery-paper/src/main/resources/discoveries.yml` 끝 구간 (tools/nova150/discovery.py 에서 생성). 아래 키를 `MagicDiscovery.signal(uuid, key, amount)` 로 올려 주면 됩니다.
`state.*` 키는 `MagicDiscovery.state()` 의 허용 목록에 추가 필요 (`state.popularity`). 이동 거리 키는 블록 단위(정수 누적).

- 주문서: scroll.<마법 id>: 할로윈 때 만든 마법 스크롤(item:magic_scroll, /마법스크롤 <마법> [닉네임] [수량])을 우클릭하면 MagicScroll 이 이미 이 신호를 보냄 → 새로 만들 것 없음. 던전 보상 등으로 스크롤만 나눠 주면 됨.

| 키 | 셀 것 | 쓰는 마법 | 목표 |
|---|---|---|---|
| `break.flower` | 꽃 블록(민들레·양귀비·튤립 등 1칸·2칸 꽃) 부수기 | 301 민들레 홀씨탄 | 12 |
| `obtain.wool` | 양털(색 무관) 줍기 | 302 바람개비 던지기 | 16 |
| `obtain.feather` | 깃털 줍기 | 303 종이학 | 6 |
| `open.chest` | 상자(덫 상자 포함) 열기 | 304 회전 베기 | 570 |
| `pickup.arrow` | 바닥에 꽂히거나 떨어진 화살 줍기 | 305 휘파람 칼날 | 12 |
| `kill.creeper` | 크리퍼 처치 | 306 돌풍 장타 | 4 |
| `climb.blocks` | 사다리·덩굴·비계 등을 타고 오른 블록 | 307 공기 풍선 | 60 |
| `obtain.string` | 실 줍기 | 308 연줄 | 20 |
| `mine.log` | 원목(나무 종류 무관) 캐기 | 309 풍차 날개 | 350 |
| `boat.distance` | 보트를 타고 이동한 블록 | 310 역풍로 | 4500 |
| `obtain.gunpowder` | 화약 줍기 | 311 홀씨 지뢰밭 | 8 |
| `kill.polar_bear` | 북극곰 처치 | 312 회오리 발차기 | 5 |
| `mine.tuff` | 응회암 캐기 | 313 바람 톱 | 100 |
| `mine.cobweb` | 거미줄 캐기(도구 무관) | 314 칼바람 장막 | 25 |
| `break.leaves` | 나뭇잎 블록 부수기 | 315 천 마리 종이학 | 450 |
| `fall.distance` | 떨어진 높이 합계(블록) | 316 바람 낙하산 | 14000 |
| `kill.livestock` | 닭·돼지·소·양 처치 | 317 회전목마 돌풍 | 170 |
| `shoot.bow` | 활 쏘기(시위를 당겨 놓은 횟수) | 318 칼날비 | 60 |
| `open.chest` | 상자(덫 상자 포함) 열기 | 319 진공 회랑 | 2400 |
| `kill.illager` | 약탈자·변명자·소환사·파괴수·마녀 처치 | 320 풍경 결계 | 25 |
| `kill.boss_kinds` | 처치한 서로 다른 야생 우두머리(kill.mob.* 고유 몹) 종류 수 | 321 무풍지대 | 3 |
| `obtain.ghast_tear` | 가스트 눈물 줍기 | 322 거대 바람개비 | 4 |
| `obtain.rabbit` | 토끼 가죽·토끼 발 줍기 | 323 연날리기 축제 | 9 |
| `open.monster_loot` | 몬스터 전리품 상자(monster_loot_ticket) 열기 | 324 천공의 방아 | 33 |
| `obtain.raw_gold` | 금 원석 줍기 | 326 불티 집게 | 14 |
| `obtain.raw_copper` | 구리 원석 줍기 | 327 쇳물 방울 | 32 |
| `craft.torch` | 횃불 제작 개수 | 328 등롱 불씨 | 32 |
| `obtain.flint` | 부싯돌 줍기 | 329 성냥 긋기 | 4 |
| `mine.blackstone` | 흑암 캐기 | 330 숯불 투척 | 64 |
| `obtain.leather` | 가죽 줍기 | 331 불꽃 손톱 | 10 |
| `obtain.redstone` | 레드스톤 가루 줍기 | 332 풀무질 | 50 |
| `craft.diamond_tool` | 다이아몬드 곡괭이·도끼·삽·괭이·검 제작 | 333 단조 망치 | 9 |
| `craft.diamond_armor` | 다이아몬드 투구·흉갑·각반·부츠 제작 | 334 쇳물 갑주 | 8 |
| `place.lantern` | 랜턴(영혼 랜턴 포함) 설치 | 335 등롱 사슬 | 8 |
| `craft.gold_ingot` | 금 주괴 제작(굽기·조합) | 336 불꽃 팽이 | 10 |
| `mine.blackstone` | 흑암 캐기 | 337 화염 꽃잎 | 260 |
| `place.lava` | 용암 양동이 붓기 | 338 용암 거품 | 4 |
| `craft.iron_block` | 철 블록 제작 | 339 주물 거푸집 | 8 |
| `craft.lantern` | 랜턴(영혼 랜턴 포함) 제작 | 340 풍등 폭격 | 100 |
| `kill.nether_mob` | 피글린·좀비 피글린·가스트·블레이즈·마그마 큐브·호글린·피글린 야수 처치 | 341 불실 그물 | 34 |
| `obtain.quartz` | 네더 석영 줍기 | 342 화로 돔 | 1700 |
| `obtain.gold_nugget` | 금 조각 줍기 | 343 쇳물 해일 | 220 |
| `obtain.blaze_rod` | 블레이즈 막대 줍기 | 344 불꽃 연꽃 | 4 |
| `mine.ancient_debris` | 고대 잔해 캐기 | 345 쇳물 손 | 100 |
| `alchemy.recipe_found` | 처음 성공한 서로 다른 연금술 조합법 수 (ALCHEMY CRAFT … new) | 346 태양 흑점 | 4 |
| `craft.coal_block` | 석탄 블록 제작 | 347 도가니 붓기 | 13 |
| `mine.glowstone` | 발광석 캐기 | 348 불나비 떼 | 40 |
| `wand.enhance_perfect` | 마법봉 하나의 강화 기회(maximum-attempts)를 실패 없이 모두 성공 (강화 기록이 로그에 없어 비율 미확인) | 349 대장장이 신의 모루 | 1 |
| `kill.cod` | 대구 처치(검·마법 등으로) | 351 빗방울 연사 | 3 |
| `kill.drowned` | 드라운드 처치 | 352 물보라 발차기 | 2 |
| `mine.clay` | 점토 블록 캐기 | 353 물시계 방울 | 4 |
| `walk.underwater` | 물속에서 걸어 이동한 블록 | 354 물 리본 | 1200 |
| `mine.ice` | 얼음·꽁꽁 언 얼음·파란 얼음 캐기 | 355 서리 결정 | 8 |
| `obtain.ink` | 먹물 주머니·발광 먹물 주머니 줍기 | 356 안개 숨 | 2 |
| `mine.sugarcane` | 사탕수수 캐기 | 357 고드름 투척 | 2 |
| `kill.squid` | 오징어·발광 오징어 처치 | 358 물장구 | 2 |
| `obtain.amethyst` | 자수정 조각 줍기 | 359 고드름 처마 | 25 |
| `eat.melon` | 수박 조각 먹기 | 360 결빙 파동 | 4 |
| `mine.kelp` | 켈프 캐기 | 361 소용돌이 원반 | 4 |
| `open.fishing_box` | 낚시 상자(fishing_ticket) 열기 | 362 해파리 떼 | 2 |
| `open.barrel` | 통 열기 | 363 얼음 미끄럼 | 28 |
| `kill.guardian` | 가디언·엘더 가디언 처치 | 364 폭우 | 3 |
| `mine.snow` | 눈·눈 블록 캐기 | 365 서리 정원 | 20 |
| `obtain.prismarine` | 프리즈머린 조각·프리즈머린 수정 줍기 | 366 물시계 감옥 | 16 |
| `kill.drowned` | 드라운드 처치 | 367 안개 바다 | 12 |
| `mine.ice` | 얼음·꽁꽁 언 얼음·파란 얼음 캐기 | 368 빙산 낙하 | 120 |
| `kill.squid` | 오징어·발광 오징어 처치 | 369 이안류 | 8 |
| `mine.ice` | 얼음·꽁꽁 언 얼음·파란 얼음 캐기 | 370 빙하 결정탑 | 320 |
| `mine.snow` | 눈·눈 블록 캐기 | 371 결빙 화음 | 190 |
| `kill.guardian` | 가디언·엘더 가디언 처치 | 372 거대 물방울 | 10 |
| `obtain.ink` | 먹물 주머니·발광 먹물 주머니 줍기 | 373 심해 촉수 | 33 |
| `obtain.prismarine` | 프리즈머린 조각·프리즈머린 수정 줍기 | 374 천상의 물시계 | 170 |
| `mine.sand` | 모래·붉은 모래 캐기 | 376 모래 채찍 | 20 |
| `mine.gravel` | 자갈 캐기 | 377 모래 알갱이 | 28 |
| `obtain.bone` | 뼈 줍기 | 378 화석 송곳니 | 14 |
| `obtain.clay_ball` | 점토 덩이 줍기 | 379 점토 덩이 | 8 |
| `mine.copper_ore` | 구리 광석(심층암 포함) 캐기 | 380 자철 조각 | 10 |
| `mine.gold_ore` | 금 광석(심층암 포함) 캐기 | 381 흙먼지 발구르기 | 12 |
| `mine.lapis_ore` | 청금석 광석(심층암 포함) 캐기 | 382 보석 파편 | 8 |
| `mine.sand` | 모래·붉은 모래 캐기 | 383 모래시계 구덩이 | 90 |
| `mine.redstone_ore` | 레드스톤 광석(심층암 포함) 캐기 | 384 모래 분수 | 28 |
| `craft.copper_ingot` | 구리 주괴 제작(굽기·조합) | 385 자석 손 | 4 |
| `mine.moss` | 이끼 블록 캐기 | 386 모래 두더지 | 13 |
| `craft.bone_meal` | 뼛가루 제작 | 387 진흙 폭탄 | 24 |
| `craft.chest` | 상자 제작 | 388 바위 팽이 | 18 |
| `mine.sand` | 모래·붉은 모래 캐기 | 389 유사 파도 | 700 |
| `obtain.bone` | 뼈 줍기 | 390 화석 뱀뼈 | 78 |
| `mine.obsidian` | 흑요석(우는 흑요석 포함) 캐기 | 391 석화의 손 | 8 |
| `obtain.emerald` | 에메랄드 줍기 | 392 모래관 | 30 |
| `mine.lapis_ore` | 청금석 광석(심층암 포함) 캐기 | 393 쇳가루 폭풍 | 50 |
| `mine.gravel` | 자갈 캐기 | 394 모래 폭포 | 200 |
| `mine.amethyst_cluster` | 다 자란 자수정 군집 캐기 | 395 정동 개화 | 22 |
| `mine.emerald_ore` | 에메랄드 광석(심층암 포함) 캐기 | 396 쌍극 감옥 | 14 |
| `land.claim` | 청크 점유 횟수 (MagicCodexBridge 청크 점유) | 397 모래폭풍 | 7 |
| `mine.sculk` | 스컬크 계열 블록 캐기 | 398 사막화 | 15000 |
| `mine.sand` | 모래·붉은 모래 캐기 | 399 대사막의 모래시계 | 8000 |
| `eat.apple` | 사과 먹기 | 401 별똥 바늘 | 60 |
| `use.cartography` | 지도 제작대 열기 | 402 초점 렌즈 | 2 |
| `eat.bread` | 빵 먹기 | 403 촛불 성가 | 6 |
| `obtain.glow_berries` | 발광 열매 줍기 | 404 빛 바늘땀 | 2 |
| `use.hint_ticket` | 마법 힌트권 사용 (MagicCodexBridge 마법 힌트권 사용) | 405 거울 조각 | 3 |
| `open.monster_loot` | 몬스터 전리품 상자(monster_loot_ticket) 열기 | 406 별빛 뛰기 | 10 |
| `obtain.rotten_flesh` | 썩은 살점 줍기 | 407 빛 부채 | 18 |
| `enchant.item` | 마법 부여대로 마법 부여 | 408 거울판 | 3 |
| `use.scroll` | 마법 스크롤 사용 (아무 마법) | 409 삼각 별자리 | 1 |
| `talk.villager` | 주민 거래 화면 열기 | 410 해시계 바늘 | 2 |
| `obtain.emerald` | 에메랄드 줍기 | 411 남십자성 | 2 |
| `craft.glass` | 유리 제작(모래 굽기) | 412 색유리 파편 | 30 |
| `craft.shulker_box` | 셜커 상자 제작 | 413 별 잇기 | 2 |
| `craft.torch` | 횃불 제작 개수 | 414 등대 | 250 |
| `kill.shulker` | 셜커 처치 | 415 굴절 창 | 16 |
| `craft.shulker_box` | 셜커 상자 제작 | 416 북두칠성 | 10 |
| `kill.wither_skeleton` | 위더 스켈레톤 처치 | 417 거울 미궁 | 4 |
| `pickup.arrow` | 바닥에 꽂히거나 떨어진 화살 줍기 | 418 오리온의 활 | 94 |
| `obtain.lapis` | 청금석 줍기 | 419 카시오페이아 | 430 |
| `use.scroll` | 마법 스크롤 사용 (아무 마법) | 420 성운 직조 | 12 |
| `open.shulker` | 셜커 상자 열기 | 421 거울 세계 | 600 |
| `enchant.item` | 마법 부여대로 마법 부여 | 422 천체 망원경 | 60 |
| `obtain.glow_berries` | 발광 열매 줍기 | 423 은하수 | 128 |
| `use.hint_ticket` | 마법 힌트권 사용 (MagicCodexBridge 마법 힌트권 사용) | 424 황도 12궁 | 13 |
| `kill.wolf` | 늑대 처치 | 426 초승달 낫 | 2 |
| `obtain.gunpowder` | 화약 줍기 | 427 박쥐 떼 | 4 |
| `death.count` | 사망 횟수 | 428 꿈 조각 | 16 |
| `obtain.string` | 실 줍기 | 429 꼭두각시 당김 | 10 |
| `kill.spider` | 거미·동굴 거미 처치 | 430 달빛 화살 | 8 |
| `open.shulker` | 셜커 상자 열기 | 431 달 조각 | 13 |
| `obtain.spider_eye` | 거미 눈 줍기 | 432 검은 나방 | 2 |
| `kill.creeper` | 크리퍼 처치 | 433 달빛 표식 | 8 |
| `open.shulker` | 셜커 상자 열기 | 434 그림자극 | 48 |
| `obtain.ender_pearl` | 엔더 진주 줍기 | 435 박쥐 망토 | 2 |
| `craft.shield` | 방패 제작 | 436 반달 방패 | 2 |
| `use.grindstone` | 숫돌 열기 | 437 상현·하현 칼 | 2 |
| `kill.spider` | 거미·동굴 거미 처치 | 438 달의 인력 | 14 |
| `kill.wolf` | 늑대 처치 | 439 달의 울음 | 13 |
| `open.barrel` | 통 열기 | 440 꿈의 미로 | 76 |
| `break.spawner` | 몬스터 생성기 부수기 | 441 박쥐 군주 | 3 |
| `kill.creeper` | 크리퍼 처치 | 442 꼭두각시 무대 | 38 |
| `death.count` | 사망 횟수 | 443 달 떨구기 | 53 |
| `obtain.ender_pearl` | 엔더 진주 줍기 | 444 달빛 기둥 | 130 |
| `kill.wither_skeleton` | 위더 스켈레톤 처치 | 445 월광 회전륜 | 9 |
| `kill.stray` | 스트레이 처치 | 446 막 내림 | 20 |
| `kill.bat` | 박쥐 처치 | 447 그믐 장막 | 6 |
| `obtain.gunpowder` | 화약 줍기 | 448 그림자 도플갱어 | 72 |
| `kill.boss_kinds` | 처치한 서로 다른 야생 우두머리(kill.mob.* 고유 몹) 종류 수 | 449 만월 강림 | 6 |

## 마법 스크롤 6종 (할로윈 때 만든 스크롤 그대로 — 서버에 새로 만들 것 없음, 던전 보상 등으로 나눠 주기만)

| # | id | 스크롤 | 키 |
|---|---|---|---|
| 325 | `sky_sever` | 「창공 절단」 마법 스크롤 (`/마법스크롤 sky_sever`) | `scroll.sky_sever` |
| 350 | `furnace_gate` | 「용광로의 문」 마법 스크롤 (`/마법스크롤 furnace_gate`) | `scroll.furnace_gate` |
| 375 | `glacial_epoch` | 「영겁의 빙하」 마법 스크롤 (`/마법스크롤 glacial_epoch`) | `scroll.glacial_epoch` |
| 400 | `core_magnetosphere` | 「지핵 자기장」 마법 스크롤 (`/마법스크롤 core_magnetosphere`) | `scroll.core_magnetosphere` |
| 425 | `supernova` | 「초신성」 마법 스크롤 (`/마법스크롤 supernova`) | `scroll.supernova` |
| 450 | `moonlit_masquerade` | 「월하 가면무도회」 마법 스크롤 (`/마법스크롤 moonlit_masquerade`) | `scroll.moonlit_masquerade` |
