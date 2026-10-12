# 마법별 미몹 구현표 (자동 생성)

| # | id | 이름 | 대상 | 처리 | 내용 |
|---|---|---|---|---|---|
| 1 | feather_fall | feather fall | SELF | 미몹+애드온 | 5초 천천히 낙하 |
| 2 | wind_basket | 바람바구니 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 3 | harvest_wind | 수확의 바람 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 4 | wind_push | 윈드 푸쉬 | ENTITY | 미몹+애드온 | 정면 몬스터 1체 약 4블록 밀기 |
| 5 | light_leap | 상승기류 | SELF | 미몹+애드온 | 위로 약 7블록, 착지 피해 완화 |
| 6 | flight | 비행 | SELF | 미몹+애드온 | 빗자루 비행(ChacademyMotion): 빗자루가 있으면 무제한, 없으면 연습용 빗자루 10초. 모드가 없으면 예전 10초 비행 |
| 7 | far_touch | 손길 너머 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 9 | wind_message | 바람의 전언 | - | 보류 | Bridge가 직접 처리하는 귓속말 마법 |
| 10 | storm_command | 폭풍의 지휘 | AIM | 미몹+애드온 | 영창 1.5초 후 반경 4블록 4회 타격 |
| 11 | vacuum_cleave | 윈드 커터 | AIM | 미몹+애드온 | 관통 칼날, 사슬 블록 파괴 |
| 12 | gale_dash | 바람 걸음 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 13 | returning_wind | 되받는 바람 | SELF | 미몹+애드온 | 0.5초 투사체 반사 |
| 14 | wind_grooming | 바람 털빗 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 15 | weightless_zone | 중력지대 | AIM | 미몹+애드온 | 반경 5블록 3초 끌어당김 |
| 17 | trajectory_acceleration | 미라지 슈터 | - | 보류 | 슬롯 장착형 상시 효과: Bridge 장착 상태 연동 필요 |
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
| 30 | resonant_arrow | 미스틱 슈터 | - | 보류 | 슬롯 장착형 상시 효과: Bridge 장착 상태 연동 필요 |
| 31 | pressure_rupture | 토네이도 스핀 | SELF | 미몹+애드온 | 주변 3블록 밀치며 피해 |
| 32 | storm_crown | 삼중 탄환 | AIM | 미몹+애드온 | 3발 모두 같은 대상 적중 시 3초 신속 III |
| 33 | hearth_touch | 손끝의 화로 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 34 | ember_bolt | 파이어 볼 | AIM | 미몹+애드온 | 약한 불씨 탄 |
| 35 | rekindle | 다시 타오르는 불 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 36 | flame_forging | 불꽃 단조 | - | 보류 | 특수 단조 재료표가 아직 없음 |
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
| 49 | glass_firing | 광석 재련 | - | 보류 | 슬롯 장착형 상시 효과(광석 자동 재련): Bridge 장착 상태 연동 필요 |
| 50 | charcoal_conversion | 발화 | ENTITY | 미몹+애드온 | 10초 발화 |
| 51 | sponge_drying | 스펀지 말리기 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 52 | flame_footprints | 불꽃 걸음 | - | 보류 | 슬롯 장착형 상시 효과: Bridge 장착 상태 연동 필요 |
| 53 | boiling_armor | 끓는 갑주 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 54 | kiln_breath | 가마 숨결 | SELF | 미몹+애드온 | 주변 화로 20초 가공 2배속 |
| 55 | glaze_coating | 화염의 정복자 | - | 보류 | 슬롯 장착형 상시 효과(화염 면역): Bridge 장착 상태 연동 필요 |
| 56 | explosive_wedge | 폭발 쐐기 | ENTITY | 미몹+애드온 | 근접 피격 시 폭발 (시간 경과로는 안 터짐) |
| 58 | heat_recovery | 열량 보존 | SELF | 미몹+애드온 | 주변 불·용암 개수만큼 회복 |
| 59 | firebird_hunt | 불새 | AIM | 미몹+애드온 | 넓게 관통하는 불새 |
| 60 | lava_suture | 용암 봉합 | SELF | Bridge 유틸 | MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸) |
| 61 | flame_sword_dance | 화염 검무 | SELF | 미몹+애드온 | 근접 공격마다 검 1자루 소모 추가 피해 |
| 62 | incandescent_break | 파이어 스트라이크 | AIM | 미몹+애드온 | 짧은 레이저, 방패 5초 무력화 |
| 63 | blue_bonfire | 푸른 화톳불 | - | 보류 | 마나 회복량 증가는 Bridge 마나 API 연동 필요 |
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
| 87 | pressure_hammer | 수압해머 | AIM | 미몹+애드온 | 반경 3블록 강타 + 짧은 경직 |
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
