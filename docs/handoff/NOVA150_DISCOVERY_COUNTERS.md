# 신규 150종 발견 조건 카운터 명세 (서버 세션 구현용, Claude 2026-11-03)

오너: "조건을 실제로 만들건 없고 서버 세션에다가 말해서 제작할거야 / 궁극들이랑 몇개는 스크롤 사용 / 던전 보상 같은걸로".
오너 2026-11-03: "발견 조건 겹치는거 빼고 / 스크롤로 된건 우리 스크롤 시스템 할로윈에 했던거" → 운영 중 조건과 겹치던 19종 교체, 주문서 → 할로윈 마법 스크롤.

오너 2026-11-03 2차: "너무 바닐라 위주 / 커스텀 시스템과 연동 / 스크롤은 각 원소별 궁극 2개 중 1개만" → 원소마다 5종(30종)을 큐디치·연금술·마법봉 강화·의뢰·기숙사·펫·NPC 호감도·토네이도·작물 각성·고유 몬스터·칭호·파티·스토리 조건으로 교체, 스크롤은 원소별 궁극 1종(6종)만.
정의는 `magic-discovery-paper/src/main/resources/discoveries.yml` 끝 구간 (tools/nova150/discovery.py 에서 생성). 아래 키를 `MagicDiscovery.signal(uuid, key, amount)` 로 올려 주면 됩니다.
`state.*` 키는 `MagicDiscovery.state()` 의 허용 목록에 추가 필요 (`state.popularity`). 이동 거리 키는 블록 단위(정수 누적).

- 주문서: scroll.<마법 id>: 할로윈 때 만든 마법 스크롤(item:magic_scroll, /마법스크롤 <마법> [닉네임] [수량])을 우클릭하면 MagicScroll 이 이미 이 신호를 보냄 → 새로 만들 것 없음. 던전 보상 등으로 스크롤만 나눠 주면 됨.

| 키 | 셀 것 | 쓰는 마법 | 목표 |
|---|---|---|---|
| `break.dandelion` | 민들레 블록을 부숨 (누적) | 301 민들레 홀씨탄 | 64 |
| `place.banner` | 현수막 설치 | 302 바람개비 던지기 | 16 |
| `craft.paper` | 종이 제작 개수 | 303 종이학 | 128 |
| `hit.sweep_multi` | 검 휩쓸기 공격 한 번에 몬스터 2마리 이상 피해 (횟수) | 304 회전 베기 | 50 |
| `use.goat_horn` | 염소 뿔피리 사용 | 305 휘파람 칼날 | 10 |
| `throw.wind_charge` | 돌풍구 던지기 | 306 돌풍 장타 | 50 |
| `obtain.phantom_membrane` | 팬텀 막을 인벤토리에 처음 얻은 개수 (줍기·제작 포함, 누적) | 307 공기 풍선 | 8 |
| `craft.lead` | 끈(목줄) 제작 개수 | 308 연줄 | 8 |
| `kill.breeze` | 브리즈 처치 | 309 풍차 날개 | 30 |
| `quidditch.bludger_hit` | 큐디치(kyudichi) 경기 중 블러저 쳐내기 성공 (누적) | 310 역풍로 | 20 |
| `wild.awakened_crop_kill` | 야생 작물 각성 몬스터(ChAcademiaWildlife awakening) 처치 | 311 홀씨 지뢰밭 | 30 |
| `open.vault` | 시련의 금고(일반·불길한) 열기 | 312 회오리 발차기 | 5 |
| `use.stonecutter` | 석재 절단기에서 꺼낸 결과물 개수 | 313 바람 톱 | 256 |
| `shear.cobweb` | 가위로 거미줄 캐기 | 314 칼바람 장막 | 32 |
| `hatch.chicken_egg` | 던진 달걀에서 병아리 태어남 | 315 천 마리 종이학 | 30 |
| `fly.elytra` | 겉날개 활공 이동 블록 | 316 바람 낙하산 | 10000 |
| `equip.saddle` | 서로 다른 바닐라 동물(말·돼지·스트라이더·낙타·당나귀·노새 등)에게 안장 장착 (같은 개체 중복 X) | 317 회전목마 돌풍 | 10 |
| `kill.arrow_from_above` | 화살로 몬스터 처치, 쏜 사람이 대상보다 10블록 넘게 높음 | 318 칼날비 | 30 |
| `ride.minecart` | 광산 수레를 타고 이동한 블록 | 319 진공 회랑 | 2000 |
| `raid.won` | 습격 승리 (영웅 효과 받기) | 320 풍경 결계 | 5 |
| `tornado.survive` | 토네이도 이벤트 동안 토네이도 반경 안에 들어갔다가 이벤트가 끝날 때 살아 있음 (이벤트당 1회) | 321 무풍지대 | 3 |
| `obtain.elytra` | 겉날개 얻기 (엔드 함선 액자에서 꺼내기) | 322 거대 바람개비 | 3 |
| `quidditch.win` | 큐디치 경기 승리 (승리 팀 전원) | 323 연날리기 축제 | 10 |
| `quidditch.snitch_catch` | 큐디치 경기 중 스니치 잡기 | 324 천공의 방아 | 3 |
| `harvest.honeycomb_smoked` | 아래에 모닥불이 있는 벌집·벌통에서 가위로 벌집 조각 수확 | 326 불티 집게 | 10 |
| `cauldron.lava_drip` | 뾰족한 점적석 아래 가마솥이 용암으로 가득 참 (CauldronLevelChangeEvent NATURAL_FILL, 16블록 안 가장 가까운 플레이어) | 327 쇳물 방울 | 3 |
| `place.lantern` | 랜턴(영혼 랜턴 포함) 설치 | 328 등롱 불씨 | 32 |
| `light.nether_portal` | 불로 네더 차원문을 열기 (PortalCreateEvent reason FIRE, 불 붙인 플레이어) | 329 성냥 긋기 | 3 |
| `alchemy.success` | 연금술 조합 성공 | 330 숯불 투척 | 10 |
| `fill.lava_bucket` | 양동이로 용암 뜨기 | 331 불꽃 손톱 | 10 |
| `craft.fire_charge` | 화염구 제작 개수 | 332 풀무질 | 32 |
| `wand.enhance_success` | 마법봉 강화 성공 | 333 단조 망치 | 5 |
| `craft.iron_armor` | 철 갑옷 부위 제작 | 334 쇳물 갑주 | 20 |
| `place.chain` | 사슬 설치 | 335 등롱 사슬 | 64 |
| `kill.blaze` | 블레이즈 처치 | 336 불꽃 팽이 | 50 |
| `harvest.torchflower` | 다 자란 횃불꽃 수확 | 337 화염 꽃잎 | 10 |
| `ride.strider` | 스트라이더 타고 용암 위 이동 블록 | 338 용암 거품 | 500 |
| `mine.ancient_debris` | 고대 잔해 캐기 | 339 주물 거푸집 | 8 |
| `kill.ghast` | 가스트 처치 | 340 풍등 폭격 | 20 |
| `kill.magma_cube` | 마그마 큐브 처치 | 341 불실 그물 | 100 |
| `barter.piglin` | 피글린 물물교환 (금 주괴 건넨 횟수) | 342 화로 돔 | 64 |
| `drink.fire_resistance` | 화염 저항 물약 마시기 | 343 쇳물 해일 | 10 |
| `grow.nether_fungus` | 뼛가루로 진홍·뒤틀린 균류를 거대 균류로 키움 | 344 불꽃 연꽃 | 30 |
| `smithing.netherite` | 네더라이트 업그레이드 | 345 쇳물 손 | 4 |
| `alchemy.recipe_found` | 처음 성공한 서로 다른 연금술 조합법 수 | 346 태양 흑점 | 10 |
| `alchemy.mana_core` | 연금술로 마력코어 제작 | 347 도가니 붓기 | 5 |
| `obtain.totem` | 불사의 토템 얻기 | 348 불나비 떼 | 5 |
| `wand.enhance_perfect` | 마법봉 하나의 강화 기회(maximum-attempts, 지금 10번)를 실패 없이 모두 성공 | 349 대장장이 신의 모루 | 1 |
| `weather.rain_minutes` | 비 오는 바이옴에서 하늘이 보이는 곳에 있던 분 | 351 빗방울 연사 | 30 |
| `pet.assist_kill` | 펫을 소환해 데리고 있는 동안 플레이어가 몬스터 처치 | 352 물보라 발차기 | 30 |
| `craft.clock` | 시계 제작 | 353 물시계 방울 | 4 |
| `harvest.kelp` | 켈프 캐기 | 354 물 리본 | 256 |
| `mine.ice_silk` | 섬세한 손길로 얼음류 캐기 | 355 서리 결정 | 64 |
| `hatch.turtle` | 근처(16블록)에서 거북 알 부화 | 356 안개 숨 | 5 |
| `visit.ice_spikes_seconds` | 얼음 가시 바이옴에 있던 초 | 357 고드름 투척 | 600 |
| `housing.guest` | 내 기숙사 방에 들어온 서로 다른 학생 수 | 358 물장구 | 5 |
| `place.pointed_dripstone` | 뾰족한 점적석 설치 | 359 고드름 처마 | 64 |
| `freeze.powder_snow` | 가루눈에 빠져 완전히 얼어붙음 (얼음 피해 시작) | 360 결빙 파동 | 3 |
| `obtain.trident` | 삼지창 얻기 | 361 소용돌이 원반 | 1 |
| `kill.glow_squid` | 발광 오징어 처치 | 362 해파리 떼 | 30 |
| `boat.ice` | 얼음류 위 보트 이동 블록 | 363 얼음 미끄럼 | 1000 |
| `channeling.lightning` | 집전 삼지창으로 번개 소환 | 364 폭우 | 5 |
| `build.snow_golem` | 눈 블록·조각된 호박으로 눈 골렘 만들기 | 365 서리 정원 | 5 |
| `kill.elder_guardian` | 엘더 가디언 처치 | 366 물시계 감옥 | 3 |
| `treasure.buried` | 묻힌 보물 상자 열기 | 367 안개 바다 | 5 |
| `obtain.blue_ice` | 파란 얼음 얻기 | 368 빙산 낙하 | 128 |
| `kill.guardian` | 가디언 처치 | 369 이안류 | 100 |
| `activate.conduit` | 전달체 활성화 (근처에 있을 때) | 370 빙하 결정탑 | 1 |
| `affinity.npc30` | 호감도 30 이상이 된 학교 NPC 수 | 371 결빙 화음 | 1 |
| `breed.axolotl_blue` | 파란 아홀로틀 새끼 태어남 (부모를 먹인 플레이어) | 372 거대 물방울 | 1 |
| `quidditch.save` | 큐디치 경기 중 골 선방 (config scoring.save-goal) | 373 심해 촉수 | 8 |
| `quest.daily_done` | 의뢰 완료 (누적) | 374 천상의 물시계 | 100 |
| `mine.sand` | 모래·붉은 모래 캐기 | 376 모래 채찍 | 512 |
| `obtain.flint` | 자갈에서 부싯돌 떨어짐 | 377 모래 알갱이 | 64 |
| `brush.suspicious` | 솔로 수상한 모래·자갈 털기 완료 | 378 화석 송곳니 | 20 |
| `obtain.clay_ball` | 점토 덩이 얻기 (줍기, 누적) | 379 점토 덩이 | 128 |
| `link.lodestone` | 자석석에 나침반 연결 | 380 자철 조각 | 3 |
| `kill.ravager` | 파괴수 처치 | 381 흙먼지 발구르기 | 5 |
| `kill.ca_wild` | 야생 차카데미 고유 몬스터(CA_Wild_*) 처치 | 382 보석 파편 | 100 |
| `loot.desert_pyramid` | 사막 피라미드 상자 처음 열기 | 383 모래시계 구덩이 | 3 |
| `ride.camel` | 낙타 타고 이동 블록 | 384 모래 분수 | 2000 |
| `place.lightning_rod` | 피뢰침 설치 | 385 자석 손 | 16 |
| `obtain.armadillo_scute` | 아르마딜로 인갑 얻기 | 386 모래 두더지 | 32 |
| `make.mud` | 물병으로 흙을 진흙으로 | 387 진흙 폭탄 | 128 |
| `housing.furniture` | 내 기숙사 방에 놓은 가구 수 (현재 값) | 388 바위 팽이 | 50 |
| `kill.silverfish` | 좀벌레 처치 | 389 유사 파도 | 100 |
| `hatch.sniffer` | 근처(16블록)에서 스니퍼 알 부화 | 390 화석 뱀뼈 | 1 |
| `mine.obsidian` | 흑요석(우는 흑요석 포함) 캐기 | 391 석화의 손 | 128 |
| `cure.zombie_villager` | 좀비 주민 치료 완료 | 392 모래관 | 3 |
| `craft.iron_block` | 철 블록 제작 | 393 쇳가루 폭풍 | 64 |
| `damage.falling_block_suffocate` | 떨어진 모래·자갈 블록 안에서 질식 피해 | 394 모래 폭포 | 10 |
| `find.geode` | 자수정 정동 발견 (싹트는 자수정 블록 처음 바라본 서로 다른 정동) | 395 정동 개화 | 10 |
| `lightning.rod_struck` | 자기가 설치한 피뢰침에 번개 맞음 | 396 쌍극 감옥 | 10 |
| `tornado.flung` | 토네이도에 띄워진 뒤 죽지 않고 착지 (이벤트 중) | 397 모래폭풍 | 5 |
| `title.owned` | 가진 칭호 수 (현재 값) | 398 사막화 | 15 |
| `quest.daily_streak` | 의뢰를 하루 1번 이상 완료한 연속 일수 (0시 초기화 기준) | 399 대사막의 모래시계 | 14 |
| `craft.firework_star` | 불꽃 별(폭죽 탄약) 제작 개수 | 401 별똥 바늘 | 32 |
| `craft.spyglass` | 망원경 제작 개수 | 402 초점 렌즈 | 3 |
| `npc.talk` | AI NPC(ChacaNPC)와 대화한 횟수 | 403 촛불 성가 | 30 |
| `use.loom` | 베틀 무늬 결과물 꺼내기 | 404 빛 바늘땀 | 10 |
| `harvest.glow_berries` | 발광 열매 따기 | 405 거울 조각 | 64 |
| `allay.collect` | 알레이가 가져다준 아이템 개수 | 406 별빛 뛰기 | 64 |
| `mine.glowstone` | 발광석 캐기 | 407 빛 부채 | 64 |
| `place.painting` | 그림 걸기 | 408 거울판 | 16 |
| `enchant.level30` | 30레벨 마법 부여 | 409 삼각 별자리 | 10 |
| `place.daylight_detector` | 햇빛 감지기 설치 | 410 해시계 바늘 | 16 |
| `story.chapter1` | 스토리 1장 완료 | 411 남십자성 | 1 |
| `craft.stained_glass_colors` | 만든 색유리 색 종류 수 (최대 16) | 412 색유리 파편 | 16 |
| `place.ender_eye_frame` | 엔드 차원문 틀에 엔더의 눈 끼우기 | 413 별 잇기 | 12 |
| `beacon.tier4` | 4단계 피라미드 신호기 활성화 | 414 등대 | 1 |
| `kill.shulker` | 셜커 처치 | 415 굴절 창 | 30 |
| `craft.shulker_box` | 셜커 상자 제작 | 416 북두칠성 | 7 |
| `kill.evoker` | 소환사 처치 | 417 거울 미궁 | 5 |
| `craft.tipped_arrow` | 물약 화살 제작 | 418 오리온의 활 | 64 |
| `state.popularity` | 현재 인기도 (스탯 화면 값, 상태값) | 419 카시오페이아 | 300 |
| `affinity.npc20` | 호감도 20 이상이 된 학교 NPC 수 | 420 성운 직조 | 5 |
| `use.end_gateway` | 엔드 관문 순간이동 | 421 거울 세계 | 10 |
| `party.quest_done` | 파티에 들어가 있는 동안 의뢰 완료 | 422 천체 망원경 | 30 |
| `obtain.nether_star` | 네더의 별 얻기 | 423 은하수 | 3 |
| `pet.collected` | 가진 서로 다른 펫 종류 수 (MCPets·차카데미 펫 모두) | 424 황도 12궁 | 12 |
| `till.night` | 밤에 괭이로 흙 갈기 | 426 초승달 낫 | 256 |
| `kill.bat` | 박쥐 처치 | 427 박쥐 떼 | 30 |
| `alchemy.fail` | 연금술 조합 실패 | 428 꿈 조각 | 10 |
| `obtain.string` | 실 얻기 | 429 꼭두각시 당김 | 128 |
| `kill.crossbow` | 쇠뇌로 쏜 화살·폭죽으로 몬스터 처치 | 430 달빛 화살 | 50 |
| `eat.chorus_fruit` | 후렴과 먹기 | 431 달 조각 | 20 |
| `obtain.spider_eye` | 거미 눈 얻기 | 432 검은 나방 | 64 |
| `moon.phases` | 밤에 접속해 본 서로 다른 달 모양 수 (최대 8) | 433 달빛 표식 | 8 |
| `armor_stand.equip` | 서로 다른 갑옷 거치대에 장비 입히기 | 434 그림자극 | 10 |
| `drink.invisibility` | 투명화 물약 마시기 | 435 박쥐 망토 | 10 |
| `break.sword` | 내구도가 다해 부러진 검 | 436 반달 방패 | 5 |
| `wand.enhance_fail` | 마법봉 강화 실패 | 437 상현·하현 칼 | 10 |
| `kill.slime` | 슬라임 처치 (마그마 큐브 제외) | 438 달의 인력 | 50 |
| `tame.wolf` | 늑대 길들이기 | 439 달의 울음 | 10 |
| `loot.ancient_city` | 고대 도시 상자 처음 열기 | 440 꿈의 미로 | 10 |
| `break.spawner` | 몬스터 생성기 부수기 | 441 박쥐 군주 | 3 |
| `kill.charged_creeper` | 충전된 크리퍼 처치 | 442 꼭두각시 무대 | 3 |
| `obtain.mob_head` | 몬스터 머리(좀비·스켈레톤·크리퍼·위더 스켈레톤·피글린 등) 얻기 | 443 달 떨구기 | 5 |
| `obtain.dragon_breath` | 드래곤의 숨결 병 얻기 | 444 달빛 기둥 | 32 |
| `kill.wither` | 위더 처치 | 445 월광 회전륜 | 1 |
| `kill.ca_wild_night` | 밤(13000~23000틱)에 야생 차카데미 고유 몬스터(CA_Wild_*) 처치 | 446 막 내림 | 300 |
| `effect.darkness` | 어둠 효과(워든·스컬크 비명기) 받고 끝날 때 살아 있음 | 447 그믐 장막 | 10 |
| `housing.visit_other` | 들어가 본 서로 다른 남의 기숙사 방 수 | 448 그림자 도플갱어 | 10 |
| `wild.awakened_crop_fullmoon` | 보름달(달 모양 0) 밤에 작물 각성 몬스터 처치 | 449 만월 강림 | 100 |

## 마법 스크롤 6종 (할로윈 때 만든 스크롤 그대로 — 서버에 새로 만들 것 없음, 던전 보상 등으로 나눠 주기만)

| # | id | 스크롤 | 키 |
|---|---|---|---|
| 325 | `sky_sever` | 「창공 절단」 마법 스크롤 (`/마법스크롤 sky_sever`) | `scroll.sky_sever` |
| 350 | `furnace_gate` | 「용광로의 문」 마법 스크롤 (`/마법스크롤 furnace_gate`) | `scroll.furnace_gate` |
| 375 | `glacial_epoch` | 「영겁의 빙하」 마법 스크롤 (`/마법스크롤 glacial_epoch`) | `scroll.glacial_epoch` |
| 400 | `core_magnetosphere` | 「지핵 자기장」 마법 스크롤 (`/마법스크롤 core_magnetosphere`) | `scroll.core_magnetosphere` |
| 425 | `supernova` | 「초신성」 마법 스크롤 (`/마법스크롤 supernova`) | `scroll.supernova` |
| 450 | `moonlit_masquerade` | 「월하 가면무도회」 마법 스크롤 (`/마법스크롤 moonlit_masquerade`) | `scroll.moonlit_masquerade` |
