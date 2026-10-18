# 마법별 미몹 구현표 (자동 생성)

| # | id | 이름 | 대상 | 처리 | 내용 |
|---|---|---|---|---|---|
| 1 | feather_fall | feather fall | SELF | 미몹+애드온 | 5초 천천히 낙하 |
| 2 | wind_basket | 바람바구니 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 3 | harvest_wind | 수확의 바람 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 4 | wind_push | 윈드 푸쉬 | ENTITY | 미몹+애드온 | 정면 몬스터 1체 약 4블록 밀기 |
| 5 | light_leap | 상승기류 | SELF | 미몹+애드온 | 위로 약 7블록, 착지 피해 완화 |
| 6 | flight | 비행 | SELF | 미몹+애드온 | 빗자루 비행 (ChacademyMotion). 빗자루 없으면 ticks 동안 연습용 빗자루, 모드 없으면 ticks 동안 바닐라 비행 |
| 7 | far_touch | 손길 너머 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 9 | wind_message | 바람의 전언 | - | 보류 | Bridge가 직접 처리하는 귓속말 마법 |
| 10 | storm_command | 폭풍의 지휘 | AIM | 미몹+애드온 | 영창 1.5초 후 반경 4블록 4회 타격 |
| 11 | vacuum_cleave | 윈드 커터 | AIM | 미몹+애드온 | 관통 칼날, 사슬 블록 파괴 |
| 12 | gale_dash | 바람 걸음 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 13 | returning_wind | 되받는 바람 | SELF | 미몹+애드온 | 0.5초 투사체 반사 |
| 14 | wind_grooming | 바람 털빗 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 15 | weightless_zone | 중력지대 | AIM | 미몹+애드온 | 반경 5블록 3초 끌어당김 |
| 17 | trajectory_acceleration | 미라지 슈터 | 장착 | 애드온 패시브 | 장착 중 내가 피해를 준 대상에게 유도 바람 화살 (발동당 마나 10) |
| 18 | dust_duster | 클린 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 19 | leaf_cleanup | 잎새 청소 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 20 | feather_rustle | 깃털 털기 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 21 | cross_breeze | 바람 화살 | AIM | 미몹+애드온 | 2초 충전 후 강한 화살 |
| 22 | returning_blade | 되돌이 칼날 | AIM | 미몹+애드온 | 갔다가 돌아오며 각 1회씩 타격 |
| 23 | pressure_press | 윈드 프레스 | AIR_ENTITY | 미몹+애드온 | 공중 대상을 내리꽂음 (낙하 피해는 바닐라) |
| 24 | wind_decoy | 바람 미끼 | AIM | 미몹+애드온 | 반경 10블록 일반 몬스터 유인 5초 |
| 25 | air_step | 허공 디딤 | SELF | 미몹+애드온 | 발밑 3x3 임시 발판 20초 (보이는 건 VFX) |
| 26 | lightning_needle | 라이트닝 쇼크 | AIM | 미몹+애드온 | 지정 위치 낙뢰 피해 |
| 27 | air_stacking | 허공 쌓기 | AIM | 미몹+애드온 | 손에 든 블록 최대 6칸 일렬 설치 |
| 28 | arrested_orbit | 퍼붓는 화살 | AIM | 미몹+애드온 | 화살 5발 순차 발사 |
| 29 | crossfire | 크로스 애로우 | ENTITY | 미몹+애드온 | 일반 몬스터 처형, 플레이어·보스는 체력 20% 미만일 때만 |
| 30 | resonant_arrow | 미스틱 슈터 | 장착 | 애드온 패시브 | 장착 중 직접 공격 적중 시 추가 바람 화살 (발동당 마나 50), 미라지와 동시 발동 |
| 31 | pressure_rupture | 토네이도 스핀 | SELF | 미몹+애드온 | 주변 3블록 밀치며 피해 |
| 32 | storm_crown | 삼중 탄환 | AIM | 미몹+애드온 | 3발 모두 같은 대상 적중 시 3초 신속 III |
| 33 | hearth_touch | 손끝의 화로 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 34 | ember_bolt | 파이어 볼 | AIM | 미몹+애드온 | 약한 불씨 탄 |
| 35 | rekindle | 다시 타오르는 불 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 36 | flame_forging | 불꽃 단조 | SELF | 미몹+애드온 | 손에 든 재료를 단조표대로 변환 (재료표 미정: config flame-forging.recipes) |
| 37 | ember_trap | 잿불 덫 | AIM | 미몹+애드온 | 60초 덫, 최대 2개, 밟으면 폭발 |
| 38 | fire_curtain | 플레어 월 | AIM | 미몹+애드온 | 폭 6블록 화염벽 5초 |
| 39 | phoenix_oath | 불사조의 서약 | SELF | 미몹+애드온 | 300초 동안 치명상 1회 무효 |
| 40 | living_furnace | 살아 있는 화로 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 41 | falling_star | 메테오 스트라이크 | AIM | 미몹+애드온 | 운석 낙하 반경 4블록 |
| 42 | flame_lash | 화염 채찍 | ENTITY | 미몹+애드온 | 끌어당기며 피해 |
| 43 | cinder_detonation | 스타 퓨리 | BURNING_ENTITY | 미몹+애드온 | (기존 라우트 유지) 불타는 대상 폭발 |
| 44 | palm_fireworks | 손바닥 폭죽 | SELF | 미몹 기본 | 장식 마법, 게임 효과 없음 |
| 45 | ash_brand | 재의 낙인 | ENTITY | 미몹+애드온 | 3초 뒤 폭발, 맞은 적에게 전이 |
| 46 | flame_leap | 블레이즈 킥 | SELF | 미몹+애드온 | 회전 발차기 근접 범위 |
| 47 | crimson_heart | 홍련의 심장 | SELF | 미몹+애드온 | 현재 체력 절반 소모, 소모량 비례 폭발 |
| 48 | wick_trimming | 심지 줄이기 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 49 | glass_firing | 광석 재련 | 장착 | 애드온 패시브 | 장착 중 광석 드롭 자동 재련 (광석당 마나 3) |
| 50 | charcoal_conversion | 발화 | ENTITY | 미몹+애드온 | 10초 발화 |
| 51 | sponge_drying | 스펀지 말리기 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 52 | flame_footprints | 불꽃 걸음 | 장착 | 애드온 패시브 | 장착 중 매초 마나 10, 발자국이 잠시 뒤 폭발 (서버 mana-spells enabled:false 유지) |
| 53 | boiling_armor | 끓는 갑주 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 54 | kiln_breath | 가마 숨결 | SELF | 미몹+애드온 | 주변 화로 20초 가공 2배속 |
| 55 | glaze_coating | 화염의 정복자 | 장착 | 애드온 패시브 | 장착 중 화염 지속 피해(FIRE_TICK) 면역 |
| 56 | explosive_wedge | 폭발 쐐기 | ENTITY | 미몹+애드온 | 근접 피격 시 폭발 (시간 경과로는 안 터짐) |
| 58 | heat_recovery | 열량 보존 | SELF | 미몹+애드온 | 주변 불·용암 개수만큼 회복 |
| 59 | firebird_hunt | 불새 | AIM | 미몹+애드온 | 넓게 관통하는 불새 |
| 60 | lava_suture | 용암 봉합 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 61 | flame_sword_dance | 화염 검무 | SELF | 미몹+애드온 | 근접 공격마다 검 1자루 소모 추가 피해 |
| 62 | incandescent_break | 파이어 스트라이크 | AIM | 미몹+애드온 | 짧은 레이저, 방패 5초 무력화 |
| 63 | blue_bonfire | 푸른 화톳불 | SELF | 미몹+애드온 | 60초 화톳불, 반경 10블록 마나 회복 +10% (같은 마법끼리 미중첩) |
| 64 | crimson_breath | 드래곤 브레스 | AIM | 미몹+애드온 | 1.5초 영창 후 부채꼴 브레스 5회 |
| 65 | water_breath | 물결 숨결 | SELF | 미몹+애드온 | 30초 수중 호흡 |
| 66 | tide_stride | 물살 타기 | SELF | 미몹+애드온 | 15초 수영 속도 |
| 67 | water_bolt | 물방울 탄환 | AIM | 미몹+애드온 | 약 1블록 밀치는 물 탄환 |
| 68 | quench | 불길 씻기 | SELF | 미몹+애드온 | 자신의 불 끄기 |
| 69 | purifying_wave | 정화의 물결 | SELF | 미몹+애드온 | 자신·아군 독/나약함/구속 해제 |
| 70 | ice_bridge | 얼음 다리 | SELF | 미몹+애드온 | 15초 차가운 걸음 |
| 72 | spring_unseal | 수맥 개방 | AIM | 미몹+애드온 | 바라보는 위치에 물 설치 |
| 73 | water_elemental | 운디네 | SELF | 미몹+애드온 | 30초 동안 1.5초마다 근처 적 공격 |
| 74 | eternal_ice_prison | 만년빙옥 | ENTITY | 미몹+애드온 | 일반 적 5초 봉쇄, 보스는 감속 |
| 75 | cauldron_fill | 가마솥 채우기 | AIM | 미몹+애드온 | 가마솥 물 한 단계 |
| 76 | dye_rinse | 염색 씻기 | SELF | 미몹+애드온 | 손에 든 가죽 장비 염색 제거 |
| 77 | fish_lure | 물고기 유인 | SELF | 미몹+애드온 | 던진 낚싯바늘 입질 시간 단축 |
| 78 | frost_rupture | 서리 파열 | AIM | 미몹+애드온 | 1초 뒤 얼음 가시 범위 |
| 79 | pressure_lance | 수압창 | AIM | 미몹+애드온 | 최대 3체 관통, 뒤일수록 약함 |
| 80 | life_distribution | 생명의 분배 | SELF | 미몹+애드온 | 자신+아군 최대 3명 체력 비율 분배 |
| 81 | moist_farmland | 촉촉한 밭 | AIM | 미몹+애드온 | 경작지 최대 9칸 적시기 |
| 82 | wash_letters | 글씨 씻기 | AIM | 미몹+애드온 | 표지판 한 면 글씨 지우기 |
| 83 | healing_rain | 치유의 비 | AIM | 미몹+애드온 | 5초 동안 아군 1초마다 하트 1칸 회복 |
| 84 | dew_gathering | 이슬 모으기 | SELF | 미몹+애드온 | 빈 병 최대 3개 물병으로 |
| 85 | quiet_mooring | 노아의 방주 | SELF | 미몹+애드온 | 물 위에서 보트 생성 |
| 86 | bubble_aria | 물거품 아리아 | AIM | 미몹+애드온 | 명중 지점 반경 4블록 광역 |
| 87 | pressure_hammer | 수압해머 | AIM | 미몹+애드온 | 반경 3블록 강타 + 1초 기절 |
| 88 | waterspout | 용오름 | AIM | 미몹+애드온 | 3초 용오름, 띄우며 지속 피해 |
| 89 | current_sorting | 사금 추출 | SELF | 미몹+애드온 | 물 속에서 사금(금 조각) 1~3개 |
| 90 | potion_mist | 약액 분무 | SELF | 미몹+애드온 | 손의 물약 소비, 반경 5블록 동료 10초 적용 |
| 91 | waterwheel_shield | 물레 방패 | SELF | 미몹+애드온 | 다음 근접 피해 절반 + 공격자 밀침 (10초) |
| 92 | concrete_curing | 콘크리트 양생 | AIM | 미몹+애드온 | 연결된 콘크리트 가루 최대 16개 굳히기 |
| 93 | ripple_guard | 파문방벽 | SELF | 미몹+애드온 | 2초 정면 투사체 소멸 |
| 94 | tidal_wave | 해일 | AIM | 미몹+애드온 | 전방 해일, 3블록 밀침 |
| 95 | bouncing_droplet | 도약하는 물방울 | AIM | 미몹+애드온 | 서로 다른 적 최대 4명 튕김 |
| 96 | abyssal_pressure | 심해의 압력 | AIM | 미몹+애드온 | 6초 수압 지대, 감속·지속 피해 |
| 97 | verdant_touch | 풍요의 손길 | SELF | 미몹+애드온 | 주변 밀·당근·감자 최대 8개 한 단계 성장 |
| 98 | ore_whisper | 땅의 속삭임 | SELF | 미몹+애드온 | 광석 최대 3곳 5초 발광 표시 (시전자만 보임) |
| 99 | pebble_toss | 조약돌 던지기 | AIM | 미몹+애드온 | 약한 조약돌 탄 |
| 100 | miners_rhythm | 광부의 박자 | SELF | 미몹+애드온 | 15초 성급함 |
| 101 | earth_shaping | 대지 성형 | AIM | 미몹+애드온 | 정면 7블록 부채꼴 충격파, 약하게 띄움 (피해·각도 임시) |
| 102 | stone_wall | 석벽 | AIM | 미몹+애드온 | 10초 돌벽: 적 이동·투사체 차단, 실제 블록 없음 (크기 4x3 = VFX 기준, 미정) |
| 103 | vine_ladder | 덩굴 사다리 | SELF | 미몹+애드온 | 바라보는 벽 최대 8블록 30초 사다리, 실제 덩굴 없음 (VFX 없음) |
| 104 | earth_elemental | 그놈 | SELF | 미몹+애드온 | 60초 흙 골렘(가상) 동행·근접 공격 (공격력 임시) |
| 105 | earthquake | 지진 | SELF | 미몹+애드온 | 주변 6블록 지면 흔들기 (지속 3초·피해 임시) |
| 106 | climate_control | 기후 통제 | SELF | 미몹+애드온 | 맑음<->비 전환, 이후 자연 변화 |
| 107 | seed_row | 씨앗 줄심기 | SELF | 미몹+애드온 | 소지 씨앗으로 전방 6칸 농지 파종 |
| 108 | bee_calm | 벌떼 달래기 | SELF | 미몹+애드온 | 주변 화난 벌 진정 (반경 8 임시) |
| 109 | thorn_armor | 가시 갑옷 | SELF | 미몹+애드온 | 8초 근접 공격자 반격 피해 (피해 임시) |
| 110 | vein_fracture | 광맥 파쇄 | AIM | 미몹+애드온 | 연결된 같은 광석 최대 8개 채굴, 도구 규칙·내구도, 맨손 드롭 없음 |
| 111 | earth_anchor | 대지의 닻 | SELF | 미몹+애드온 | 10초 공격 넉백 무시 (폭발 제외), 이동 가능 |
| 112 | weathering_touch | 풍화의 손길 | AIM | 미몹+애드온 | 석재를 이끼·금 간 변종으로 |
| 113 | iron_carapace | 철의 외피 | SELF | 미몹+애드온 | 20초 저항 (레벨 미정: I 임시) |
| 114 | moss_coating | 이끼 입히기 | SELF | 미몹+애드온 | 발아래 Deep Dark 탐지, 결과는 VFX로만 (범위·마나·쿨 미정) |
| 115 | grass_sprouting | 잔디 돋우기 | AIM | 미몹+애드온 | 조준한 2x2 흙만 잔디로 |
| 116 | flint_sorting | 돌 고르기 | AIM | 미몹+애드온 | 돌 파편 3~5발 순차 발사 (사거리·피해 임시) |
| 117 | bark_peeling | 껍질 벗기기 | AIM | 미몹+애드온 | 연결된 원목 최대 6개 껍질 벗기기 |
| 118 | compost_return | 퇴비 환원 | SELF | 미몹+애드온 | 손의 식물 재료 -> 뼛가루 (바닐라 퇴비통 기대값 임시, 재료·비율 미정) |
| 119 | thorn_seeds | 가시 씨앗 | AIM | 미몹+애드온 | 선인장 가시 5발 부채꼴 동시 발사 (사거리·피해 임시) |
| 120 | pollen_matchmaking | 꽃가루 중매 | AIM | 미몹+애드온 | 번식 가능한 동물 한 쌍 번식 (먹이 불필요, 대기시간 준수) |
| 121 | automatic_supply | 자동 보급 | 장착 | 애드온 패시브 | 장착 중 손의 건축 블록이 떨어지면 인벤토리에서 보충 |
| 122 | dormant_seed | 동면의 씨앗 | SELF | 미몹+애드온 | 전방 5칸 다 자란 작물만 수확, 재파종 없음 |
| 123 | counterweight | 반격의 무게추 | SELF | 미몹+애드온 | 1초 안에 방패로 막으면 공격자 2초 행동 정지 |
| 124 | material_exchange | 자재 치환 | ENTITY | 미몹+애드온 | 대상 이동만 2초 정지, 보스 면역 |
| 125 | remembering_field | 기억하는 밭 | SELF | 미몹+애드온 | 전방 5칸 작물 즉시 성숙 |
| 126 | little_giant | 작은 거인 | SELF | 미몹+애드온 | 15초 몸집·근접 거리·근접 피해 증가 (증가량 임시) |
| 127 | spring_ground | 용수철 지반 | AIM | 미몹+애드온 | 임시 도약판 (3회·30초·높이 임시) |
| 128 | mountain_back | 산맥의 등 | AIM | 미몹+애드온 | 굴러가는 바위가 밀며 여러 번 타격 (크기·사거리·피해 임시) |
| 129 | earth_embrace | 대지의 품 | SELF | 미몹+애드온 | 전방 5칸, 완전히 마른 농지 위 작물 되살리기 (더위 건조 시스템 없음, 해석 확인 필요) |
| 130 | forest_throne | 숲의 왕좌 | SELF | 미몹+애드온 | 8초 제자리, 주변 6블록 뿌리 순차 공격·넉백 |
| 131 | primordial_stone | 원초의 돌 | SELF | 미몹+애드온 | 돌핵 생성 -> 다시 사용 시 발사 (Bridge 쿨타임 API 없으면 자동 발사, 확률·피해 미정) |
| 132 | guiding_star | 길잡이 별 | SELF | 미몹+애드온 | 웅크려 시전 = 위치 저장, 일반 시전 = 10초 방향 표시 |
| 133 | minor_heal | 작은 치유 | SELF | 미몹+애드온 | 자신 체력 하트 1칸 회복 |
| 134 | light_mark | 빛의 낙인 | ENTITY | 미몹+애드온 | 조준한 적에게 유도 빛 탄 (피해·사거리 임시, VFX 없음) |
| 136 | luminous_archive | 빛의 서고 | - | 보류 | 잠긴 상자 시스템 자체가 아직 없음 (실패 확률·내용물 처리·획득 조건 미정) |
| 137 | true_sight | 진실의 눈 | SELF | 미몹+애드온 | 15초 근처 투명한 적 윤곽 + 숨은 단서 이벤트 (범위 임시) |
| 138 | protective_ward | 보호 결계 | SELF | 미몹+애드온 | 10초 투사체 흡수 -> 보호막 (전환량·상한 임시) |
| 139 | constellation_blessing | 성좌의 가호 | SELF | 미몹+애드온 | 자신+6블록 동료 30초 신속·저항 (레벨 임시) |
| 140 | arcane_imprint | 마력 각인 | SELF | 미몹+애드온 | 곡괭이 20초 3x3 채굴 |
| 141 | dawn_ritual | 여명의 의식 | SELF | 미몹+애드온 | 8블록 해로운 효과 해제 + 20초 재적용 방지 |
| 142 | comforting_light | 위로의 빛 | SELF | 미몹+애드온 | 바라보는 동료 임시 하트 5칸 5초, 미중첩 |
| 143 | exorcism_flash | 퇴마의 섬광 | SELF | 미몹+애드온 | 주변 언데드만 범위 피해 (반경·피해 임시) |
| 144 | arcane_mending | 마력 봉합 | SELF | 미몹+애드온 | 손에 든 일반 도구 완전 수리 |
| 145 | companion_return | 동행 귀환 | SELF | 미몹+애드온 | 시전자 혼자 마을 귀환 지점으로 (config return-point) |
| 146 | flawless_moment | 무결의 순간 | SELF | 미몹+애드온 | 3초 무적, 이동·공격 가능 |
| 147 | solar_judgment | 태양의 심판 | AIM | 미몹+애드온 | 12초 영창(이동·피격 시 실패) 후 3초 레이저 (피해·사거리 임시) |
| 148 | firefly_mark | 반딧불 표식 | SELF | 미몹+애드온 | 60초 따라다니는 실제 빛 |
| 149 | rainbow_collar | 교화 | - | 보류 | 교화(HolyTaming): TamingBridge가 별도 처리 |
| 150 | sun_drying | 햇살 말림 | SELF | 미몹+애드온 | 썩은 살점 4 -> 가죽 1 |
| 151 | luminous_copy | 빛의 필사 | AIM | 미몹+애드온 | 채팅으로 입력한 글씨를 조준 위치에 5초 표시 (입력창은 클라이언트 제안) |
| 152 | guiding_fletching | 유도 깃 | SELF | 미몹+애드온 | 5초 동안 쏘는 화살이 조준한 적에게 유도, 벽 우회 없음 |
| 153 | sharing_table | 나눔 식탁 | SELF | 미몹+애드온 | 자신 제외 근처 아군 허기 전부 회복 (반경 임시) |
| 154 | summons_letter | 소환장 | SELF | 미몹+애드온 | 길들인 늑대·고양이 새로 소환, 30초 뒤 사망 |
| 155 | starlight_survey | 별빛 측량 | SELF | 미몹+애드온 | 바라보는 블록 좌표·거리 표시 |
| 156 | luminous_handover | 빛의 교대 | SELF | 미몹+애드온 | 주변 몬스터 도발 (보스 포함, 범위·지속 임시) |
| 157 | mana_conduit | 마력 전선 | SELF | 미몹+애드온 | 마나석 5개, 획득 시 마나 50+계수x마력 (계수·반경·유지 임시) |
| 158 | merciful_arrow | 자비의 화살 | AIM | 미몹+애드온 | 매우 빠른 치유 화살, 맞은 아군 회복 (치유량 임시) |
| 159 | mana_deposit | 마력 예금 | SELF | 미몹+애드온 | 마나 포션(item:mana_potion) 1개 생성, 사용 시 마나 50 |
| 160 | guardian_oath | 수호자의 서약 | SELF | 미몹+애드온 | 바라보는 동료 피해 일부 대신 받기 (거리·지속·비율 임시) |
| 161 | experience_exchange | 경험의 환전 | SELF | 미몹+애드온 | 현재 마나 전부 -> 경험치 (비율 임시) |
| 163 | starlight_reprise | 별빛 재현 | SELF | 미몹+애드온 | 다른 장착 마법 1개 쿨타임 초기화 (Bridge 쿨타임 API + 슬롯 보고 필요) |
| 164 | constellation_emissary | 성좌의 대리인 | SELF | 미몹+애드온 | 30초 별 사역마: 체력 낮은 동료 하트 3칸, 2초 간격 |
| 165 | night_sight | 밤눈 | SELF | 미몹+애드온 | 60초 야간 투시 |
| 166 | shadow_bind | 그림자 매듭 | ENTITY | 미몹+애드온 | 가까운 몬스터 3초 둔화 (사거리·둔화 임시) |
| 167 | faint_veil | 옅은 은신 | SELF | 미몹+애드온 | 8초 투명 (갑옷·손 아이템은 보임) |
