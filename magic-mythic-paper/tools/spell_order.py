"""마법 번호·순서 (2026-10-25 오너: 실제로 쓰는 마법만, 원소별로 묶어 1번부터, 원소 안에서는 가중치 1~10 순, 10 = 가장 궁극).
원소 순서 = 도감 탭 순서 (바람·화염·물·대지·빛·어둠). 같은 가중치 안에서는 옛 번호 순.
가중치는 마나 비용 기준 (≤10:1, ≤25:2, ≤40:3, ≤60:4, ≤90:5, ≤150:6, ≤250:7, ≤500:8, <1500:9, ≥1500:10; 장착 패시브 3) + 수동 보정.
모든 생성기(arcana/mob/gimmick/nova_spells.py)는 도감 파일 이름·order·카탈로그 number 를 이 표에서 가져온다.
"""
# id: (번호, 원소, 가중치, 옛 번호)
ORDER = {
    'feather_fall': (1, 'wind', 1, 1),  # feather fall
    'wind_push': (2, 'wind', 1, 4),  # 윈드 푸쉬
    'wind_message': (3, 'wind', 1, 9),  # 바람의 전언
    'gale_dash': (4, 'wind', 1, 12),  # 바람 걸음
    'wind_grooming': (5, 'wind', 1, 14),  # 바람 털빗
    'trajectory_acceleration': (6, 'wind', 1, 17),  # 미라지 슈터
    'dust_duster': (7, 'wind', 1, 18),  # 클린
    'wind_needle': (8, 'wind', 1, 329),  # 바람 송곳
    'mini_twister': (9, 'wind', 1, 330),  # 작은 회오리
    'season_sense': (10, 'wind', 1, 381),  # 계절 예보
    'light_leap': (11, 'wind', 2, 5),  # 상승기류
    'far_touch': (12, 'wind', 2, 7),  # 손길 너머
    'moon_wind': (13, 'wind', 2, 317),  # 반달 바람날
    'wind_basket': (14, 'wind', 3, 2),  # 바람바구니
    'harvest_wind': (15, 'wind', 3, 3),  # 수확의 바람
    'vacuum_cleave': (16, 'wind', 3, 11),  # 윈드 커터
    'feather_rustle': (17, 'wind', 3, 20),  # 깃털 털기
    'cross_breeze': (18, 'wind', 3, 21),  # 바람 화살
    'returning_blade': (19, 'wind', 3, 22),  # 되돌이 칼날
    'landing_airbrake': (20, 'wind', 3, 203),  # 빗자루 차원이동
    'gale_wings': (21, 'wind', 3, 318),  # 돌풍 날갯짓
    'feather_storm': (22, 'wind', 3, 319),  # 깃털 폭풍
    'roar_leap': (23, 'wind', 3, 321),  # 포효 도약
    'gale_combo': (24, 'wind', 3, 345),  # 질풍 연격
    'resonant_arrow': (25, 'wind', 4, 30),  # 미스틱 슈터
    'binding_pull': (26, 'wind', 4, 315),  # 끌어오기
    'tailwind': (27, 'wind', 4, 380),  # 순풍
    'flight': (28, 'wind', 5, 6),  # 비행
    'returning_wind': (29, 'wind', 5, 13),  # 되받는 바람
    'leaf_cleanup': (30, 'wind', 5, 19),  # 잎새 청소
    'pressure_press': (31, 'wind', 5, 23),  # 윈드 프레스
    'wind_decoy': (32, 'wind', 5, 24),  # 바람 미끼
    'pressure_rupture': (33, 'wind', 5, 31),  # 토네이도 스핀
    'vacuum_burst': (34, 'wind', 5, 344),  # 진공 폭발
    'air_cannon': (35, 'wind', 5, 354),  # 공기 포탄
    'air_step': (36, 'wind', 6, 25),  # 허공 디딤
    'air_stacking': (37, 'wind', 6, 27),  # 허공 쌓기
    'air_choke': (38, 'wind', 6, 346),  # 숨 막힘
    'storm_eye': (39, 'wind', 6, 347),  # 폭풍의 눈
    'storm_command': (40, 'wind', 7, 10),  # 폭풍의 지휘
    'lightning_needle': (41, 'wind', 7, 26),  # 라이트닝 쇼크
    'arrested_orbit': (42, 'wind', 7, 28),  # 퍼붓는 화살
    'weightless_zone': (43, 'wind', 8, 15),  # 중력지대
    'storm_crown': (44, 'wind', 8, 32),  # 삼중 탄환
    'sky_dive': (45, 'wind', 8, 362),  # 천공 강하
    'thundercloud': (46, 'wind', 8, 363),  # 뇌운
    'crossfire': (47, 'wind', 10, 29),  # 크로스 애로우
    'heaven_cyclone': (48, 'wind', 10, 370),  # 하늘의 눈
    'ember_bolt': (49, 'fire', 1, 34),  # 파이어 볼
    'rekindle': (50, 'fire', 1, 35),  # 다시 타오르는 불
    'spark_flick': (51, 'fire', 1, 325),  # 불씨 튕기기
    'ember_puff': (52, 'fire', 1, 326),  # 작은 불숨
    'hearth_touch': (53, 'fire', 2, 33),  # 손끝의 화로
    'palm_fireworks': (54, 'fire', 2, 44),  # 손바닥 폭죽
    'signal_flare': (55, 'fire', 2, 376),  # 봉화 신호
    'ember_trap': (56, 'fire', 3, 37),  # 잿불 덫
    'glass_firing': (57, 'fire', 3, 49),  # 광석 재련
    'sponge_drying': (58, 'fire', 3, 51),  # 스펀지 말리기
    'glaze_coating': (59, 'fire', 3, 55),  # 화염의 정복자
    'lava_suture': (60, 'fire', 3, 60),  # 용암 봉합
    'thawing_heat': (61, 'fire', 3, 239),  # 해빙의 열기
    'steam_ignition': (62, 'fire', 3, 240),  # 증기 점화
    'fox_orbit': (63, 'fire', 3, 300),  # 여우불 공전
    'curving_plumes': (64, 'fire', 3, 301),  # 곡선 불깃털
    'flame_wheel': (65, 'fire', 3, 336),  # 화염 원반
    'flame_forging': (66, 'fire', 4, 36),  # 불꽃 단조
    'living_furnace': (67, 'fire', 4, 40),  # 살아 있는 화로
    'wick_trimming': (68, 'fire', 4, 48),  # 심지 줄이기
    'charcoal_conversion': (69, 'fire', 4, 50),  # 발화
    'kiln_breath': (70, 'fire', 4, 54),  # 가마 숨결
    'eruption_line': (71, 'fire', 4, 337),  # 분화선
    'flame_javelin': (72, 'fire', 4, 338),  # 화염 투창
    'alchemy_preheat': (73, 'fire', 4, 374),  # 연금로 예열
    'flame_lash': (74, 'fire', 5, 42),  # 화염 채찍
    'boiling_armor': (75, 'fire', 5, 53),  # 끓는 갑주
    'cinder_skipring': (76, 'fire', 5, 212),  # 불꽃 줄넘기
    'heat_haze': (77, 'fire', 5, 339),  # 아지랑이
    'broom_afterburner': (78, 'fire', 5, 375),  # 불꽃 추진
    'fire_curtain': (79, 'fire', 6, 38),  # 플레어 월
    'firebird_hunt': (80, 'fire', 6, 59),  # 불새
    'flame_leap': (81, 'fire', 7, 46),  # 블레이즈 킥
    'incandescent_break': (82, 'fire', 7, 62),  # 파이어 스트라이크
    'blue_bonfire': (83, 'fire', 7, 63),  # 푸른 화톳불
    'crimson_spiral': (84, 'fire', 7, 356),  # 홍염 나선
    'scorch_lock': (85, 'fire', 7, 358),  # 작열의 표적
    'phoenix_oath': (86, 'fire', 8, 39),  # 불사조의 서약
    'ash_brand': (87, 'fire', 8, 45),  # 재의 낙인
    'crimson_heart': (88, 'fire', 8, 47),  # 홍련의 심장
    'explosive_wedge': (89, 'fire', 8, 56),  # 폭발 쐐기
    'heat_recovery': (90, 'fire', 8, 58),  # 열량 보존
    'flame_sword_dance': (91, 'fire', 8, 61),  # 화염 검무
    'volcanic_barrage': (92, 'fire', 8, 357),  # 화산탄 세례
    'falling_star': (93, 'fire', 9, 41),  # 메테오 스트라이크
    'crimson_breath': (94, 'fire', 10, 64),  # 드래곤 브레스
    'flame_emperor': (95, 'fire', 10, 368),  # 염제 강림
    'water_bolt': (96, 'water', 1, 67),  # 물방울 탄환
    'quench': (97, 'water', 1, 68),  # 불길 씻기
    'cauldron_fill': (98, 'water', 1, 75),  # 가마솥 채우기
    'dye_rinse': (99, 'water', 1, 76),  # 염색 씻기
    'wash_letters': (100, 'water', 1, 82),  # 글씨 씻기
    'dew_gathering': (101, 'water', 1, 84),  # 이슬 모으기
    'fish_trap': (102, 'water', 1, 324),  # 아공간 수조
    'tide_slash': (103, 'water', 1, 327),  # 물살 베기
    'ice_shard': (104, 'water', 1, 328),  # 얼음 조각
    'water_breath': (105, 'water', 2, 65),  # 물결 숨결
    'tide_stride': (106, 'water', 2, 66),  # 물살 타기
    'fish_lure': (107, 'water', 2, 77),  # 물고기 유인
    'moist_farmland': (108, 'water', 2, 81),  # 촉촉한 밭
    'water_level': (109, 'water', 2, 241),  # 수위 조절
    'water_glimpse': (110, 'water', 2, 377),  # 물밑 엿보기
    'spring_unseal': (111, 'water', 3, 72),  # 수맥 개방
    'calm_ripple': (112, 'water', 3, 378),  # 잔잔한 물결
    'frost_rupture': (113, 'water', 4, 78),  # 서리 파열
    'crystal_orbit': (114, 'water', 4, 302),  # 결정 공전탄
    'frost_path': (115, 'water', 4, 340),  # 서리 길
    'hydro_burst': (116, 'water', 4, 341),  # 수류 탄환
    'treasure_scent': (117, 'water', 4, 379),  # 보물 냄새
    'purifying_wave': (118, 'water', 5, 69),  # 정화의 물결
    'ice_bridge': (119, 'water', 5, 70),  # 얼음 다리
    'bubble_aria': (120, 'water', 5, 86),  # 물거품 아리아
    'potion_mist': (121, 'water', 5, 90),  # 약액 분무
    'crystal_coat': (122, 'water', 5, 342),  # 결정 갑옷
    'ice_bloom': (123, 'water', 5, 343),  # 얼음 꽃
    'pressure_lance': (124, 'water', 6, 79),  # 수압창
    'quiet_mooring': (125, 'water', 6, 85),  # 노아의 방주
    'pressure_hammer': (126, 'water', 6, 87),  # 수압해머
    'waterwheel_shield': (127, 'water', 6, 91),  # 물레 방패
    'concrete_curing': (128, 'water', 6, 92),  # 콘크리트 양생
    'ripple_guard': (129, 'water', 6, 93),  # 파문방벽
    'bouncing_droplet': (130, 'water', 6, 95),  # 도약하는 물방울
    'droplet_relay': (131, 'water', 6, 213),  # 물방울 인계
    'folding_tideplane': (132, 'water', 6, 214),  # 접히는 수면
    'water_elemental': (133, 'water', 7, 73),  # 운디네
    'eternal_ice_prison': (134, 'water', 7, 74),  # 만년빙옥
    'life_distribution': (135, 'water', 7, 80),  # 생명의 분배
    'healing_rain': (136, 'water', 7, 83),  # 치유의 비
    'waterspout': (137, 'water', 7, 88),  # 용오름
    'tidal_wave': (138, 'water', 7, 94),  # 해일
    'ebb_mark': (139, 'water', 7, 360),  # 썰물
    'current_sorting': (140, 'water', 8, 89),  # 사금 추출
    'abyssal_pressure': (141, 'water', 8, 96),  # 심해의 압력
    'freeze_field': (142, 'water', 8, 359),  # 빙결 지대
    'ice_serpent': (143, 'water', 8, 361),  # 얼음 뱀
    'leviathan_rise': (144, 'water', 10, 369),  # 레비아탄
    'pebble_toss': (145, 'earth', 1, 99),  # 조약돌 던지기
    'weathering_touch': (146, 'earth', 1, 112),  # 풍화의 손길
    'grass_sprouting': (147, 'earth', 1, 115),  # 잔디 돋우기
    'thorn_manners': (148, 'earth', 1, 211),  # 가시의 예절
    'conduit_turn': (149, 'earth', 1, 249),  # 도관 회전
    'sand_flick': (150, 'earth', 1, 334),  # 모래 한 줌
    'verdant_touch': (151, 'earth', 2, 97),  # 풍요의 손길
    'miners_rhythm': (152, 'earth', 2, 100),  # 광부의 박자
    'seed_row': (153, 'earth', 2, 107),  # 씨앗 줄심기
    'bee_calm': (154, 'earth', 2, 108),  # 벌떼 달래기
    'moss_coating': (155, 'earth', 2, 114),  # 이끼 입히기
    'bark_peeling': (156, 'earth', 2, 117),  # 껍질 벗기기
    'compost_return': (157, 'earth', 2, 118),  # 퇴비 환원
    'pollen_matchmaking': (158, 'earth', 2, 120),  # 꽃가루 중매
    'dormant_seed': (159, 'earth', 2, 122),  # 동면의 씨앗
    'ore_whisper': (160, 'earth', 3, 98),  # 땅의 속삭임
    'vein_fracture': (161, 'earth', 3, 110),  # 광맥 파쇄
    'flint_sorting': (162, 'earth', 3, 116),  # 돌 고르기
    'stone_weight': (163, 'earth', 3, 242),  # 무게추
    'vine_awaken': (164, 'earth', 3, 243),  # 덩굴 깨우기
    'venom_spit': (165, 'earth', 3, 303),  # 독 뱉기
    'smoke_veil': (166, 'earth', 3, 304),  # 연막 은신
    'fairy_dust': (167, 'earth', 3, 316),  # 요정 가루
    'hunting_pounce': (168, 'earth', 3, 320),  # 사냥 도약
    'tail_spikes': (169, 'earth', 3, 323),  # 꼬리 가시
    'crop_snare': (170, 'earth', 3, 387),  # 작물 붙잡기
    'stone_wall': (171, 'earth', 4, 102),  # 석벽
    'thorn_armor': (172, 'earth', 4, 109),  # 가시 갑옷
    'earth_embrace': (173, 'earth', 4, 129),  # 대지의 품
    'gravity_slam': (174, 'earth', 4, 322),  # 중력 강타
    'earth_anchor': (175, 'earth', 5, 111),  # 대지의 닻
    'counterweight': (176, 'earth', 5, 123),  # 반격의 무게추
    'little_giant': (177, 'earth', 5, 126),  # 작은 거인
    'spring_ground': (178, 'earth', 5, 127),  # 용수철 지반
    'boulder_charge': (179, 'earth', 5, 353),  # 바위 돌진
    'earth_shaping': (180, 'earth', 6, 101),  # 대지 성형
    'iron_carapace': (181, 'earth', 6, 113),  # 철의 외피
    'material_exchange': (182, 'earth', 6, 124),  # 자재 치환
    'remembering_field': (183, 'earth', 6, 125),  # 기억하는 밭
    'earth_elemental': (184, 'earth', 7, 104),  # 그놈
    'earthquake': (185, 'earth', 7, 105),  # 지진
    'mountain_back': (186, 'earth', 7, 128),  # 산맥의 등
    'climate_control': (187, 'earth', 8, 106),  # 기후 통제
    'thorn_seeds': (188, 'earth', 8, 119),  # 가시 씨앗
    'forest_throne': (189, 'earth', 8, 130),  # 숲의 왕좌
    'primordial_stone': (190, 'earth', 8, 131),  # 원초의 돌
    'fault_rift': (191, 'earth', 8, 367),  # 단층 붕괴
    'titan_stride': (192, 'earth', 10, 373),  # 타이탄의 발걸음
    'guiding_star': (193, 'light', 1, 132),  # 길잡이 별
    'companion_return': (194, 'light', 1, 145),  # 동행 귀환
    'luminous_copy': (195, 'light', 1, 151),  # 빛의 필사
    'starlight_survey': (196, 'light', 1, 155),  # 별빛 측량
    'prism_shot': (197, 'light', 1, 333),  # 프리즘 탄
    'sunlance_drop': (198, 'light', 1, 335),  # 햇살 꽂기
    'minor_heal': (199, 'light', 2, 133),  # 작은 치유
    'sun_drying': (200, 'light', 2, 150),  # 햇살 말림
    'mirror_tune': (201, 'light', 2, 245),  # 반사 조율
    'prism_tint': (202, 'light', 2, 246),  # 프리즘 물들이기
    'seal_talismans': (203, 'light', 2, 307),  # 봉인 부적
    'chain_arrow': (204, 'light', 2, 309),  # 사슬 화살
    'comforting_light': (205, 'light', 3, 142),  # 위로의 빛
    'firefly_mark': (206, 'light', 3, 148),  # 반딧불 표식
    'guiding_fletching': (207, 'light', 3, 152),  # 유도 깃
    'domain_stomp': (208, 'light', 3, 305),  # 영역 선포
    'shield_slam': (209, 'light', 3, 308),  # 방패 내려찍기
    'insight_flash': (210, 'light', 3, 384),  # 영감의 섬광
    'blessed_ribbon': (211, 'light', 3, 386),  # 축복의 리본
    'spatial_leap': (212, 'light', 4, 135),  # 공간 도약
    'exorcism_flash': (213, 'light', 4, 143),  # 퇴마의 섬광
    'merciful_arrow': (214, 'light', 4, 158),  # 자비의 화살
    'light_beam': (215, 'light', 4, 244),  # 빛줄기
    'seal_circle': (216, 'light', 4, 306),  # 봉인진
    'halo_throw': (217, 'light', 4, 351),  # 광륜 투척
    'harvest_bell': (218, 'light', 4, 385),  # 수확제의 종
    'true_sight': (219, 'light', 5, 137),  # 진실의 눈
    'luminous_handover': (220, 'light', 5, 156),  # 빛의 교대
    'flash_blink': (221, 'light', 5, 352),  # 섬광 도약
    'protective_ward': (222, 'light', 6, 138),  # 보호 결계
    'rainbow_collar': (223, 'light', 6, 149),  # 교화
    'sharing_table': (224, 'light', 6, 153),  # 나눔 식탁
    'summons_letter': (225, 'light', 6, 154),  # 소환장
    'mana_deposit': (226, 'light', 6, 159),  # 마력 예금
    'guardian_oath': (227, 'light', 6, 160),  # 수호자의 서약
    'constellation_blessing': (228, 'light', 7, 139),  # 성좌의 가호
    'arcane_imprint': (229, 'light', 7, 140),  # 마력 각인
    'flawless_moment': (230, 'light', 7, 146),  # 무결의 순간
    'dawn_ritual': (231, 'light', 8, 141),  # 여명의 의식
    'arcane_mending': (232, 'light', 8, 144),  # 마력 봉합
    'mana_conduit': (233, 'light', 8, 157),  # 마력 전선
    'constellation_emissary': (234, 'light', 8, 164),  # 성좌의 대리인
    'prism_cage': (235, 'light', 8, 365),  # 빛의 감옥
    'solar_judgment': (236, 'light', 9, 147),  # 태양의 심판
    'starlight_reprise': (237, 'light', 9, 163),  # 별빛 재현
    'aurora_veil': (238, 'light', 10, 372),  # 오로라
    'night_sight': (239, 'dark', 1, 165),  # 밤눈
    'shadow_bind': (240, 'dark', 1, 166),  # 그림자 매듭
    'shadow_fang': (241, 'dark', 1, 331),  # 그림자 송곳니
    'dusk_orb': (242, 'dark', 1, 332),  # 어둠 구슬
    'pack_sort': (243, 'dark', 1, 382),  # 배낭 정리
    'echo_reading': (244, 'dark', 2, 169),  # 잔향 읽기
    'statue_wake': (245, 'dark', 2, 247),  # 석상 깨우기
    'soul_scythe': (246, 'dark', 2, 311),  # 영혼 낫
    'faint_veil': (247, 'dark', 3, 167),  # 옅은 은신
    'sealing_thread': (248, 'dark', 3, 170),  # 봉인의 실
    'life_siphon': (249, 'dark', 3, 174),  # 생명 갈취
    'hungry_shadow': (250, 'dark', 3, 177),  # 굶주린 그림자
    'ink_fingertip': (251, 'dark', 3, 183),  # 먹물 손끝
    'inverted_offering': (252, 'dark', 3, 193),  # 뒤집힌 공물
    'rewind': (253, 'dark', 3, 248),  # 되감기
    'dimension_mark': (254, 'dark', 3, 250),  # 차원 표식
    'mana_drain': (255, 'dark', 3, 310),  # 마나 흡수
    'oblivion_orbs': (256, 'dark', 3, 312),  # 망각의 탄환
    'bone_grasp': (257, 'dark', 3, 313),  # 뼈의 손아귀
    'shadow_leap': (258, 'dark', 3, 314),  # 그림자 도약
    'heart_read': (259, 'dark', 3, 383),  # 마음 읽기
    'misfortune_whisper': (260, 'dark', 4, 176),  # 나약함의 저주
    'loosen_grasp': (261, 'dark', 4, 186),  # 손아귀 풀기
    'hushed_soles': (262, 'dark', 4, 210),  # 고요한 밑창
    'shadow_step': (263, 'dark', 4, 350),  # 그림자 걸음
    'shadow_double': (264, 'dark', 5, 168),  # 그림자 분신
    'curse_transfer': (265, 'dark', 5, 178),  # 저주의 전가
    'black_harvest': (266, 'dark', 5, 182),  # 검은 수확
    'healing_seal': (267, 'dark', 5, 188),  # 회복 봉인
    'wound_accounting': (268, 'dark', 5, 192),  # 상처 회계
    'shadow_mire': (269, 'dark', 5, 348),  # 그림자 늪
    'dread_dream': (270, 'dark', 5, 349),  # 악몽
    'hex_doll': (271, 'dark', 5, 355),  # 저주 인형
    'shadow_exchange': (272, 'dark', 6, 172),  # 그림자 교환
    'drowsiness': (273, 'dark', 6, 175),  # 졸음
    'discord': (274, 'dark', 6, 179),  # 마인드컨트롤
    'pain_deferral': (275, 'dark', 6, 181),  # 고통 유예
    'night_lullaby': (276, 'dark', 6, 185),  # 밤의 자장가
    'shadow_scales': (277, 'dark', 6, 189),  # 어둠의 저울
    'ghost_tool': (278, 'dark', 6, 194),  # 유령 도구
    'bell_of_fear': (279, 'dark', 6, 195),  # 공포의 종
    'night_chain': (280, 'dark', 7, 180),  # 밤의 사슬
    'shadow_nail': (281, 'dark', 7, 184),  # 그림자 못
    'warning_thread': (282, 'dark', 7, 190),  # 경고의 실
    'fate_link': (283, 'dark', 7, 364),  # 운명의 실
    'forbidden_contract': (284, 'dark', 8, 173),  # 금서의 계약
    'void_maw': (285, 'dark', 8, 198),  # 공허의 입
    'wraith_march': (286, 'dark', 8, 366),  # 망령의 행진
    'eclipse_eye': (287, 'dark', 10, 196),  # 월식의 눈
    'abyss_gate': (288, 'dark', 10, 371),  # 심연의 문
}
# 버린 마법 (도감에서 뺌, 서버 설정은 꺼진 채로 둠)
DROPPED = ['aerial_haul', 'descent_command', 'cinder_detonation', 'flame_footprints', 'fireproof_film', 'water_mirror', 'vine_ladder', 'automatic_supply', 'light_mark', 'luminous_archive', 'experience_exchange', 'white_night_sanctuary']
def number(i, default=None): return ORDER[i][0] if i in ORDER else default
# 버린 마법의 이름·원소·옛 번호 (생성기가 미몹 스킬 주석·원소를 계속 쓸 수 있게)
DROPPED_INFO = {
    'aerial_haul': ('부유', 'wind', 8),
    'descent_command': ('낙하 명령', 'wind', 16),
    'cinder_detonation': ('스타 퓨리', 'fire', 43),
    'flame_footprints': ('불꽃 걸음', 'fire', 52),
    'fireproof_film': ('내연성', 'fire', 57),
    'water_mirror': ('수면의 거울', 'water', 71),
    'vine_ladder': ('덩굴 사다리', 'earth', 103),
    'automatic_supply': ('자동 보급', 'earth', 121),
    'light_mark': ('빛의 낙인', 'light', 134),
    'luminous_archive': ('빛의 서고', 'light', 136),
    'experience_exchange': ('경험의 환전', 'light', 161),
    'white_night_sanctuary': ('백야의 성역', 'light', 162),
}
