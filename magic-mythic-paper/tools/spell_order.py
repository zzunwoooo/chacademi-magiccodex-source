"""마법 번호·순서 (2026-10-25 오너, 23:30 생활·편의 12종 추가로 300개: 실제로 쓰는 마법만, 원소별로 묶어 1번부터, 원소 안에서는 가중치 1~10 순, 10 = 가장 궁극).
원소 순서 = 도감 탭 순서 (바람·화염·물·대지·빛·어둠). 같은 가중치 안에서는 옛 번호 순.
가중치는 마나 비용 기준 (≤10:1, ≤25:2, ≤40:3, ≤60:4, ≤90:5, ≤150:6, ≤250:7, ≤500:8, <1500:9, ≥1500:10; 장착 패시브 3) + 수동 보정.
모든 생성기(arcana/mob/gimmick/nova_spells.py)는 도감 파일 이름·order·카탈로그 number 를 이 표에서 가져온다.
"""
# id: (번호, 원소, 가중치, 옛 번호)
ORDER = {
    'feather_fall': (1, 'wind', 1, 1),  # 깃털 낙하
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
    'light_step': (14, 'wind', 2, 388),  # 사뿐한 발걸음
    'wind_basket': (15, 'wind', 3, 2),  # 바람바구니
    'harvest_wind': (16, 'wind', 3, 3),  # 수확의 바람
    'vacuum_cleave': (17, 'wind', 3, 11),  # 윈드 커터
    'feather_rustle': (18, 'wind', 3, 20),  # 깃털 털기
    'cross_breeze': (19, 'wind', 3, 21),  # 바람 화살
    'returning_blade': (20, 'wind', 3, 22),  # 되돌이 칼날
    'landing_airbrake': (21, 'wind', 3, 203),  # 빗자루 차원이동
    'gale_wings': (22, 'wind', 3, 318),  # 돌풍 날갯짓
    'feather_storm': (23, 'wind', 3, 319),  # 깃털 폭풍
    'roar_leap': (24, 'wind', 3, 321),  # 포효 도약
    'gale_combo': (25, 'wind', 3, 345),  # 질풍 연격
    'herding_breeze': (26, 'wind', 3, 389),  # 몰이 바람
    'resonant_arrow': (27, 'wind', 4, 30),  # 미스틱 슈터
    'binding_pull': (28, 'wind', 4, 315),  # 끌어오기
    'tailwind': (29, 'wind', 4, 380),  # 순풍
    'flight': (30, 'wind', 5, 6),  # 비행
    'returning_wind': (31, 'wind', 5, 13),  # 되받는 바람
    'leaf_cleanup': (32, 'wind', 5, 19),  # 잎새 청소
    'pressure_press': (33, 'wind', 5, 23),  # 윈드 프레스
    'wind_decoy': (34, 'wind', 5, 24),  # 바람 미끼
    'pressure_rupture': (35, 'wind', 5, 31),  # 토네이도 스핀
    'vacuum_burst': (36, 'wind', 5, 344),  # 진공 폭발
    'air_cannon': (37, 'wind', 5, 354),  # 공기 포탄
    'air_step': (38, 'wind', 6, 25),  # 허공 디딤
    'air_stacking': (39, 'wind', 6, 27),  # 허공 쌓기
    'air_choke': (40, 'wind', 6, 346),  # 숨 막힘
    'storm_eye': (41, 'wind', 6, 347),  # 폭풍의 눈
    'storm_command': (42, 'wind', 7, 10),  # 폭풍의 지휘
    'lightning_needle': (43, 'wind', 7, 26),  # 라이트닝 쇼크
    'arrested_orbit': (44, 'wind', 7, 28),  # 퍼붓는 화살
    'weightless_zone': (45, 'wind', 8, 15),  # 중력지대
    'storm_crown': (46, 'wind', 8, 32),  # 삼중 탄환
    'sky_dive': (47, 'wind', 8, 362),  # 천공 강하
    'thundercloud': (48, 'wind', 8, 363),  # 뇌운
    'crossfire': (49, 'wind', 10, 29),  # 크로스 애로우
    'heaven_cyclone': (50, 'wind', 10, 370),  # 하늘의 눈
    'ember_bolt': (51, 'fire', 1, 34),  # 파이어 볼
    'rekindle': (52, 'fire', 1, 35),  # 다시 타오르는 불
    'spark_flick': (53, 'fire', 1, 325),  # 불씨 튕기기
    'ember_puff': (54, 'fire', 1, 326),  # 작은 불숨
    'hearth_touch': (55, 'fire', 2, 33),  # 손끝의 화로
    'palm_fireworks': (56, 'fire', 2, 44),  # 손바닥 폭죽
    'signal_flare': (57, 'fire', 2, 376),  # 봉화 신호
    'ember_trap': (58, 'fire', 3, 37),  # 잿불 덫
    'glass_firing': (59, 'fire', 3, 49),  # 광석 재련
    'sponge_drying': (60, 'fire', 3, 51),  # 스펀지 말리기
    'glaze_coating': (61, 'fire', 3, 55),  # 화염의 정복자
    'lava_suture': (62, 'fire', 3, 60),  # 용암 봉합
    'thawing_heat': (63, 'fire', 3, 239),  # 해빙의 열기
    'steam_ignition': (64, 'fire', 3, 240),  # 증기 점화
    'fox_orbit': (65, 'fire', 3, 300),  # 여우불 공전
    'curving_plumes': (66, 'fire', 3, 301),  # 곡선 불깃털
    'flame_wheel': (67, 'fire', 3, 336),  # 화염 원반
    'campfire_rest': (68, 'fire', 3, 390),  # 모닥불 쉼터
    'flame_forging': (69, 'fire', 4, 36),  # 불꽃 단조
    'living_furnace': (70, 'fire', 4, 40),  # 살아 있는 화로
    'wick_trimming': (71, 'fire', 4, 48),  # 심지 줄이기
    'charcoal_conversion': (72, 'fire', 4, 50),  # 발화
    'kiln_breath': (73, 'fire', 4, 54),  # 가마 숨결
    'eruption_line': (74, 'fire', 4, 337),  # 분화선
    'flame_javelin': (75, 'fire', 4, 338),  # 화염 투창
    'alchemy_preheat': (76, 'fire', 4, 374),  # 연금로 예열
    'ward_candle': (77, 'fire', 4, 391),  # 결계 향초
    'flame_lash': (78, 'fire', 5, 42),  # 화염 채찍
    'boiling_armor': (79, 'fire', 5, 53),  # 끓는 갑주
    'cinder_skipring': (80, 'fire', 5, 212),  # 불꽃 줄넘기
    'heat_haze': (81, 'fire', 5, 339),  # 아지랑이
    'broom_afterburner': (82, 'fire', 5, 375),  # 불꽃 추진
    'fire_curtain': (83, 'fire', 6, 38),  # 플레어 월
    'firebird_hunt': (84, 'fire', 6, 59),  # 불새
    'flame_leap': (85, 'fire', 7, 46),  # 블레이즈 킥
    'incandescent_break': (86, 'fire', 7, 62),  # 파이어 스트라이크
    'blue_bonfire': (87, 'fire', 7, 63),  # 푸른 화톳불
    'crimson_spiral': (88, 'fire', 7, 356),  # 홍염 나선
    'scorch_lock': (89, 'fire', 7, 358),  # 작열의 표적
    'phoenix_oath': (90, 'fire', 8, 39),  # 불사조의 서약
    'ash_brand': (91, 'fire', 8, 45),  # 재의 낙인
    'crimson_heart': (92, 'fire', 8, 47),  # 홍련의 심장
    'explosive_wedge': (93, 'fire', 8, 56),  # 폭발 쐐기
    'heat_recovery': (94, 'fire', 8, 58),  # 열량 보존
    'flame_sword_dance': (95, 'fire', 8, 61),  # 화염 검무
    'volcanic_barrage': (96, 'fire', 8, 357),  # 화산탄 세례
    'falling_star': (97, 'fire', 9, 41),  # 메테오 스트라이크
    'crimson_breath': (98, 'fire', 10, 64),  # 드래곤 브레스
    'flame_emperor': (99, 'fire', 10, 368),  # 염제 강림
    'water_bolt': (100, 'water', 1, 67),  # 물방울 탄환
    'quench': (101, 'water', 1, 68),  # 불길 씻기
    'cauldron_fill': (102, 'water', 1, 75),  # 가마솥 채우기
    'dye_rinse': (103, 'water', 1, 76),  # 염색 씻기
    'wash_letters': (104, 'water', 1, 82),  # 글씨 씻기
    'dew_gathering': (105, 'water', 1, 84),  # 이슬 모으기
    'fish_trap': (106, 'water', 1, 324),  # 아공간 수조
    'tide_slash': (107, 'water', 1, 327),  # 물살 베기
    'ice_shard': (108, 'water', 1, 328),  # 얼음 조각
    'scale_appraisal': (109, 'water', 1, 393),  # 비늘 감정
    'water_breath': (110, 'water', 2, 65),  # 물결 숨결
    'tide_stride': (111, 'water', 2, 66),  # 물살 타기
    'fish_lure': (112, 'water', 2, 77),  # 물고기 유인
    'moist_farmland': (113, 'water', 2, 81),  # 촉촉한 밭
    'water_level': (114, 'water', 2, 241),  # 수위 조절
    'water_glimpse': (115, 'water', 2, 377),  # 물밑 엿보기
    'bottle_fill': (116, 'water', 2, 392),  # 물병 가득
    'spring_unseal': (117, 'water', 3, 72),  # 수맥 개방
    'calm_ripple': (118, 'water', 3, 378),  # 잔잔한 물결
    'frost_rupture': (119, 'water', 4, 78),  # 서리 파열
    'crystal_orbit': (120, 'water', 4, 302),  # 결정 공전탄
    'frost_path': (121, 'water', 4, 340),  # 서리 길
    'hydro_burst': (122, 'water', 4, 341),  # 수류 탄환
    'treasure_scent': (123, 'water', 4, 379),  # 보물 냄새
    'purifying_wave': (124, 'water', 5, 69),  # 정화의 물결
    'ice_bridge': (125, 'water', 5, 70),  # 얼음 다리
    'bubble_aria': (126, 'water', 5, 86),  # 물거품 아리아
    'potion_mist': (127, 'water', 5, 90),  # 약액 분무
    'crystal_coat': (128, 'water', 5, 342),  # 결정 갑옷
    'ice_bloom': (129, 'water', 5, 343),  # 얼음 꽃
    'pressure_lance': (130, 'water', 6, 79),  # 수압창
    'quiet_mooring': (131, 'water', 6, 85),  # 노아의 방주
    'pressure_hammer': (132, 'water', 6, 87),  # 수압해머
    'waterwheel_shield': (133, 'water', 6, 91),  # 물레 방패
    'concrete_curing': (134, 'water', 6, 92),  # 콘크리트 양생
    'ripple_guard': (135, 'water', 6, 93),  # 파문방벽
    'bouncing_droplet': (136, 'water', 6, 95),  # 도약하는 물방울
    'droplet_relay': (137, 'water', 6, 213),  # 물방울 인계
    'folding_tideplane': (138, 'water', 6, 214),  # 접히는 수면
    'water_elemental': (139, 'water', 7, 73),  # 운디네
    'eternal_ice_prison': (140, 'water', 7, 74),  # 만년빙옥
    'life_distribution': (141, 'water', 7, 80),  # 생명의 분배
    'healing_rain': (142, 'water', 7, 83),  # 치유의 비
    'waterspout': (143, 'water', 7, 88),  # 용오름
    'tidal_wave': (144, 'water', 7, 94),  # 해일
    'ebb_mark': (145, 'water', 7, 360),  # 썰물
    'current_sorting': (146, 'water', 8, 89),  # 사금 추출
    'abyssal_pressure': (147, 'water', 8, 96),  # 심해의 압력
    'freeze_field': (148, 'water', 8, 359),  # 빙결 지대
    'ice_serpent': (149, 'water', 8, 361),  # 얼음 뱀
    'leviathan_rise': (150, 'water', 10, 369),  # 레비아탄
    'pebble_toss': (151, 'earth', 1, 99),  # 조약돌 던지기
    'weathering_touch': (152, 'earth', 1, 112),  # 풍화의 손길
    'grass_sprouting': (153, 'earth', 1, 115),  # 잔디 돋우기
    'thorn_manners': (154, 'earth', 1, 211),  # 가시의 예절
    'conduit_turn': (155, 'earth', 1, 249),  # 도관 회전
    'sand_flick': (156, 'earth', 1, 334),  # 모래 한 줌
    'verdant_touch': (157, 'earth', 2, 97),  # 풍요의 손길
    'miners_rhythm': (158, 'earth', 2, 100),  # 광부의 박자
    'seed_row': (159, 'earth', 2, 107),  # 씨앗 줄심기
    'bee_calm': (160, 'earth', 2, 108),  # 벌떼 달래기
    'moss_coating': (161, 'earth', 2, 114),  # 이끼 입히기
    'bark_peeling': (162, 'earth', 2, 117),  # 껍질 벗기기
    'compost_return': (163, 'earth', 2, 118),  # 퇴비 환원
    'pollen_matchmaking': (164, 'earth', 2, 120),  # 꽃가루 중매
    'dormant_seed': (165, 'earth', 2, 122),  # 동면의 씨앗
    'crop_survey': (166, 'earth', 2, 394),  # 작물 살피기
    'ore_whisper': (167, 'earth', 3, 98),  # 땅의 속삭임
    'vein_fracture': (168, 'earth', 3, 110),  # 광맥 파쇄
    'flint_sorting': (169, 'earth', 3, 116),  # 돌 고르기
    'stone_weight': (170, 'earth', 3, 242),  # 무게추
    'vine_awaken': (171, 'earth', 3, 243),  # 덩굴 깨우기
    'venom_spit': (172, 'earth', 3, 303),  # 독 뱉기
    'smoke_veil': (173, 'earth', 3, 304),  # 연막 은신
    'fairy_dust': (174, 'earth', 3, 316),  # 요정 가루
    'hunting_pounce': (175, 'earth', 3, 320),  # 사냥 도약
    'tail_spikes': (176, 'earth', 3, 323),  # 꼬리 가시
    'crop_snare': (177, 'earth', 3, 387),  # 작물 붙잡기
    'replant_touch': (178, 'earth', 3, 395),  # 되심기 손길
    'stone_wall': (179, 'earth', 4, 102),  # 석벽
    'thorn_armor': (180, 'earth', 4, 109),  # 가시 갑옷
    'earth_embrace': (181, 'earth', 4, 129),  # 대지의 품
    'gravity_slam': (182, 'earth', 4, 322),  # 중력 강타
    'earth_anchor': (183, 'earth', 5, 111),  # 대지의 닻
    'counterweight': (184, 'earth', 5, 123),  # 반격의 무게추
    'little_giant': (185, 'earth', 5, 126),  # 작은 거인
    'spring_ground': (186, 'earth', 5, 127),  # 용수철 지반
    'boulder_charge': (187, 'earth', 5, 353),  # 바위 돌진
    'earth_shaping': (188, 'earth', 6, 101),  # 대지 성형
    'iron_carapace': (189, 'earth', 6, 113),  # 철의 외피
    'material_exchange': (190, 'earth', 6, 124),  # 자재 치환
    'remembering_field': (191, 'earth', 6, 125),  # 기억하는 밭
    'earth_elemental': (192, 'earth', 7, 104),  # 그놈
    'earthquake': (193, 'earth', 7, 105),  # 지진
    'mountain_back': (194, 'earth', 7, 128),  # 산맥의 등
    'climate_control': (195, 'earth', 8, 106),  # 기후 통제
    'thorn_seeds': (196, 'earth', 8, 119),  # 가시 씨앗
    'forest_throne': (197, 'earth', 8, 130),  # 숲의 왕좌
    'primordial_stone': (198, 'earth', 8, 131),  # 원초의 돌
    'fault_rift': (199, 'earth', 8, 367),  # 단층 붕괴
    'titan_stride': (200, 'earth', 10, 373),  # 타이탄의 발걸음
    'guiding_star': (201, 'light', 1, 132),  # 길잡이 별
    'companion_return': (202, 'light', 1, 145),  # 동행 귀환
    'luminous_copy': (203, 'light', 1, 151),  # 빛의 필사
    'starlight_survey': (204, 'light', 1, 155),  # 별빛 측량
    'prism_shot': (205, 'light', 1, 333),  # 프리즘 탄
    'sunlance_drop': (206, 'light', 1, 335),  # 햇살 꽂기
    'minor_heal': (207, 'light', 2, 133),  # 작은 치유
    'sun_drying': (208, 'light', 2, 150),  # 햇살 말림
    'mirror_tune': (209, 'light', 2, 245),  # 반사 조율
    'prism_tint': (210, 'light', 2, 246),  # 프리즘 물들이기
    'seal_talismans': (211, 'light', 2, 307),  # 봉인 부적
    'chain_arrow': (212, 'light', 2, 309),  # 사슬 화살
    'memory_lantern': (213, 'light', 2, 397),  # 기억의 등불
    'comforting_light': (214, 'light', 3, 142),  # 위로의 빛
    'firefly_mark': (215, 'light', 3, 148),  # 반딧불 표식
    'guiding_fletching': (216, 'light', 3, 152),  # 유도 깃
    'domain_stomp': (217, 'light', 3, 305),  # 영역 선포
    'shield_slam': (218, 'light', 3, 308),  # 방패 내려찍기
    'insight_flash': (219, 'light', 3, 384),  # 영감의 섬광
    'blessed_ribbon': (220, 'light', 3, 386),  # 축복의 리본
    'enlighten_hint': (221, 'light', 3, 396),  # 깨달음의 빛
    'spatial_leap': (222, 'light', 4, 135),  # 공간 도약
    'exorcism_flash': (223, 'light', 4, 143),  # 퇴마의 섬광
    'merciful_arrow': (224, 'light', 4, 158),  # 자비의 화살
    'light_beam': (225, 'light', 4, 244),  # 빛줄기
    'seal_circle': (226, 'light', 4, 306),  # 봉인진
    'halo_throw': (227, 'light', 4, 351),  # 광륜 투척
    'harvest_bell': (228, 'light', 4, 385),  # 수확제의 종
    'true_sight': (229, 'light', 5, 137),  # 진실의 눈
    'luminous_handover': (230, 'light', 5, 156),  # 빛의 교대
    'flash_blink': (231, 'light', 5, 352),  # 섬광 도약
    'protective_ward': (232, 'light', 6, 138),  # 보호 결계
    'rainbow_collar': (233, 'light', 6, 149),  # 교화
    'sharing_table': (234, 'light', 6, 153),  # 나눔 식탁
    'summons_letter': (235, 'light', 6, 154),  # 소환장
    'mana_deposit': (236, 'light', 6, 159),  # 마력 예금
    'guardian_oath': (237, 'light', 6, 160),  # 수호자의 서약
    'constellation_blessing': (238, 'light', 7, 139),  # 성좌의 가호
    'arcane_imprint': (239, 'light', 7, 140),  # 마력 각인
    'flawless_moment': (240, 'light', 7, 146),  # 무결의 순간
    'dawn_ritual': (241, 'light', 8, 141),  # 여명의 의식
    'arcane_mending': (242, 'light', 8, 144),  # 마력 봉합
    'mana_conduit': (243, 'light', 8, 157),  # 마력 전선
    'constellation_emissary': (244, 'light', 8, 164),  # 성좌의 대리인
    'prism_cage': (245, 'light', 8, 365),  # 빛의 감옥
    'solar_judgment': (246, 'light', 9, 147),  # 태양의 심판
    'starlight_reprise': (247, 'light', 9, 163),  # 별빛 재현
    'aurora_veil': (248, 'light', 10, 372),  # 오로라
    'night_sight': (249, 'dark', 1, 165),  # 밤눈
    'shadow_bind': (250, 'dark', 1, 166),  # 그림자 매듭
    'shadow_fang': (251, 'dark', 1, 331),  # 그림자 송곳니
    'dusk_orb': (252, 'dark', 1, 332),  # 어둠 구슬
    'pack_sort': (253, 'dark', 1, 382),  # 배낭 정리
    'echo_reading': (254, 'dark', 2, 169),  # 잔향 읽기
    'statue_wake': (255, 'dark', 2, 247),  # 석상 깨우기
    'soul_scythe': (256, 'dark', 2, 311),  # 영혼 낫
    'shadow_vault': (257, 'dark', 2, 398),  # 그림자 금고
    'faint_veil': (258, 'dark', 3, 167),  # 옅은 은신
    'sealing_thread': (259, 'dark', 3, 170),  # 봉인의 실
    'life_siphon': (260, 'dark', 3, 174),  # 생명 갈취
    'hungry_shadow': (261, 'dark', 3, 177),  # 굶주린 그림자
    'ink_fingertip': (262, 'dark', 3, 183),  # 먹물 손끝
    'inverted_offering': (263, 'dark', 3, 193),  # 뒤집힌 공물
    'rewind': (264, 'dark', 3, 248),  # 되감기
    'dimension_mark': (265, 'dark', 3, 250),  # 차원 표식
    'mana_drain': (266, 'dark', 3, 310),  # 마나 흡수
    'oblivion_orbs': (267, 'dark', 3, 312),  # 망각의 탄환
    'bone_grasp': (268, 'dark', 3, 313),  # 뼈의 손아귀
    'shadow_leap': (269, 'dark', 3, 314),  # 그림자 도약
    'heart_read': (270, 'dark', 3, 383),  # 마음 읽기
    'sweet_sleep': (271, 'dark', 3, 399),  # 단잠의 주문
    'misfortune_whisper': (272, 'dark', 4, 176),  # 나약함의 저주
    'loosen_grasp': (273, 'dark', 4, 186),  # 손아귀 풀기
    'hushed_soles': (274, 'dark', 4, 210),  # 고요한 밑창
    'shadow_step': (275, 'dark', 4, 350),  # 그림자 걸음
    'shadow_double': (276, 'dark', 5, 168),  # 그림자 분신
    'curse_transfer': (277, 'dark', 5, 178),  # 저주의 전가
    'black_harvest': (278, 'dark', 5, 182),  # 검은 수확
    'healing_seal': (279, 'dark', 5, 188),  # 회복 봉인
    'wound_accounting': (280, 'dark', 5, 192),  # 상처 회계
    'shadow_mire': (281, 'dark', 5, 348),  # 그림자 늪
    'dread_dream': (282, 'dark', 5, 349),  # 악몽
    'hex_doll': (283, 'dark', 5, 355),  # 저주 인형
    'shadow_exchange': (284, 'dark', 6, 172),  # 그림자 교환
    'drowsiness': (285, 'dark', 6, 175),  # 졸음
    'discord': (286, 'dark', 6, 179),  # 마인드컨트롤
    'pain_deferral': (287, 'dark', 6, 181),  # 고통 유예
    'night_lullaby': (288, 'dark', 6, 185),  # 밤의 자장가
    'shadow_scales': (289, 'dark', 6, 189),  # 어둠의 저울
    'ghost_tool': (290, 'dark', 6, 194),  # 유령 도구
    'bell_of_fear': (291, 'dark', 6, 195),  # 공포의 종
    'night_chain': (292, 'dark', 7, 180),  # 밤의 사슬
    'shadow_nail': (293, 'dark', 7, 184),  # 그림자 못
    'warning_thread': (294, 'dark', 7, 190),  # 경고의 실
    'fate_link': (295, 'dark', 7, 364),  # 운명의 실
    'forbidden_contract': (296, 'dark', 8, 173),  # 금서의 계약
    'void_maw': (297, 'dark', 8, 198),  # 공허의 입
    'wraith_march': (298, 'dark', 8, 366),  # 망령의 행진
    'eclipse_eye': (299, 'dark', 10, 196),  # 월식의 눈
    'abyss_gate': (300, 'dark', 10, 371),  # 심연의 문
    # ---- 301~450 신규 150종 (2026-10-31, tools/nova150_spells.py; 번호는 원소·등급 순으로 뒤에 붙임, 전체 재정렬은 나중에) ----
    'dandelion_volley': (301, 'wind', 2, 301),  # 민들레 홀씨탄 (초급, DandelionVolley)
    'pinwheel_toss': (302, 'wind', 2, 302),  # 바람개비 던지기 (초급, PinwheelToss)
    'paper_crane': (303, 'wind', 2, 303),  # 종이학 (초급, PaperCrane)
    'whirl_cut': (304, 'wind', 2, 304),  # 회전 베기 (초급, WhirlCut)
    'whistle_blade': (305, 'wind', 2, 305),  # 휘파람 칼날 (초급, WhistleBlade)
    'gust_palm': (306, 'wind', 2, 306),  # 돌풍 장타 (초급, GustPalm)
    'air_balloon': (307, 'wind', 2, 307),  # 공기 풍선 (초급, AirBalloon)
    'kite_tether': (308, 'wind', 4, 308),  # 연줄 (중급, KiteTether)
    'windmill_blades': (309, 'wind', 4, 309),  # 풍차 날개 (중급, WindmillBlades)
    'headwind_lane': (310, 'wind', 4, 310),  # 역풍로 (중급, HeadwindLane)
    'seed_mines': (311, 'wind', 4, 311),  # 홀씨 지뢰밭 (중급, SeedMines)
    'cyclone_kick': (312, 'wind', 4, 312),  # 회오리 발차기 (중급, CycloneKick)
    'air_saw': (313, 'wind', 4, 313),  # 바람 톱 (중급, AirSaw)
    'shear_curtain': (314, 'wind', 6, 314),  # 칼바람 장막 (고급, ShearCurtain)
    'thousand_cranes': (315, 'wind', 6, 315),  # 천 마리 종이학 (고급, ThousandCranes)
    'gust_canopy': (316, 'wind', 6, 316),  # 바람 낙하산 (고급, GustCanopy)
    'carousel_gale': (317, 'wind', 6, 317),  # 회전목마 돌풍 (고급, CarouselGale)
    'sky_blade_rain': (318, 'wind', 6, 318),  # 칼날비 (고급, SkyBladeRain)
    'vacuum_corridor': (319, 'wind', 6, 319),  # 진공 회랑 (고급, VacuumCorridor)
    'wind_chime_ward': (320, 'wind', 8, 320),  # 풍경 결계 (비전, WindChimeWard)
    'dead_calm': (321, 'wind', 8, 321),  # 무풍지대 (비전, DeadCalm)
    'great_pinwheel': (322, 'wind', 8, 322),  # 거대 바람개비 (비전, GreatPinwheel)
    'kite_festival': (323, 'wind', 8, 323),  # 연날리기 축제 (비전, KiteFestival)
    'sky_mill': (324, 'wind', 10, 324),  # 천공의 방아 (궁극, SkyMill)
    'sky_sever': (325, 'wind', 10, 325),  # 창공 절단 (궁극, SkySever)
    'ember_tongs': (326, 'fire', 2, 326),  # 불티 집게 (초급, EmberTongs)
    'molten_drip': (327, 'fire', 2, 327),  # 쇳물 방울 (초급, MoltenDrip)
    'lantern_wisp': (328, 'fire', 2, 328),  # 등롱 불씨 (초급, LanternWisp)
    'match_strike': (329, 'fire', 2, 329),  # 성냥 긋기 (초급, MatchStrike)
    'coal_lob': (330, 'fire', 2, 330),  # 숯불 투척 (초급, CoalLob)
    'flame_claw': (331, 'fire', 2, 331),  # 불꽃 손톱 (초급, FlameClaw)
    'bellows_blast': (332, 'fire', 2, 332),  # 풀무질 (초급, BellowsBlast)
    'forge_hammer': (333, 'fire', 4, 333),  # 단조 망치 (중급, ForgeHammer)
    'molten_plating': (334, 'fire', 4, 334),  # 쇳물 갑주 (중급, MoltenPlating)
    'lantern_chain': (335, 'fire', 4, 335),  # 등롱 사슬 (중급, LanternChain)
    'flame_top': (336, 'fire', 4, 336),  # 불꽃 팽이 (중급, FlameTop)
    'flame_petals': (337, 'fire', 4, 337),  # 화염 꽃잎 (중급, FlamePetals)
    'lava_bubbles': (338, 'fire', 4, 338),  # 용암 거품 (중급, LavaBubbles)
    'casting_mold': (339, 'fire', 6, 339),  # 주물 거푸집 (고급, CastingMold)
    'sky_lantern_raid': (340, 'fire', 6, 340),  # 풍등 폭격 (고급, SkyLanternRaid)
    'flame_loom': (341, 'fire', 6, 341),  # 불실 그물 (고급, FlameLoom)
    'furnace_dome': (342, 'fire', 6, 342),  # 화로 돔 (고급, FurnaceDome)
    'molten_wave': (343, 'fire', 6, 343),  # 쇳물 해일 (고급, MoltenWave)
    'fire_lotus': (344, 'fire', 6, 344),  # 불꽃 연꽃 (고급, FireLotus)
    'molten_grasp': (345, 'fire', 8, 345),  # 쇳물 손 (비전, MoltenGrasp)
    'sunspot': (346, 'fire', 8, 346),  # 태양 흑점 (비전, Sunspot)
    'crucible': (347, 'fire', 8, 347),  # 도가니 붓기 (비전, Crucible)
    'ember_butterflies': (348, 'fire', 8, 348),  # 불나비 떼 (비전, EmberButterflies)
    'forge_god_anvil': (349, 'fire', 10, 349),  # 대장장이 신의 모루 (궁극, ForgeGodAnvil)
    'furnace_gate': (350, 'fire', 10, 350),  # 용광로의 문 (궁극, FurnaceGate)
    'rain_patter': (351, 'water', 2, 351),  # 빗방울 연사 (초급, RainPatter)
    'spray_kick': (352, 'water', 2, 352),  # 물보라 발차기 (초급, SprayKick)
    'clepsydra_drop': (353, 'water', 2, 353),  # 물시계 방울 (초급, ClepsydraDrop)
    'water_ribbon': (354, 'water', 2, 354),  # 물 리본 (초급, WaterRibbon)
    'rime_crystal': (355, 'water', 2, 355),  # 서리 결정 (초급, RimeCrystal)
    'mist_breath': (356, 'water', 2, 356),  # 안개 숨 (초급, MistBreath)
    'icicle_fling': (357, 'water', 2, 357),  # 고드름 투척 (초급, IcicleFling)
    'water_drum': (358, 'water', 4, 358),  # 물장구 (중급, WaterDrum)
    'icicle_eaves': (359, 'water', 4, 359),  # 고드름 처마 (중급, IcicleEaves)
    'freeze_pulse': (360, 'water', 4, 360),  # 결빙 파동 (중급, FreezePulse)
    'whirlpool_disc': (361, 'water', 4, 361),  # 소용돌이 원반 (중급, WhirlpoolDisc)
    'jelly_swarm': (362, 'water', 4, 362),  # 해파리 떼 (중급, JellySwarm)
    'ice_slide': (363, 'water', 4, 363),  # 얼음 미끄럼 (중급, IceSlide)
    'rain_barrage': (364, 'water', 6, 364),  # 폭우 (고급, RainBarrage)
    'rime_garden': (365, 'water', 6, 365),  # 서리 정원 (고급, RimeGarden)
    'clepsydra_cage': (366, 'water', 6, 366),  # 물시계 감옥 (고급, ClepsydraCage)
    'sea_of_fog': (367, 'water', 6, 367),  # 안개 바다 (고급, SeaOfFog)
    'iceberg_drop': (368, 'water', 6, 368),  # 빙산 낙하 (고급, IcebergDrop)
    'rip_current': (369, 'water', 6, 369),  # 이안류 (고급, RipCurrent)
    'glacier_spire': (370, 'water', 8, 370),  # 빙하 결정탑 (비전, GlacierSpire)
    'frozen_chord': (371, 'water', 8, 371),  # 결빙 화음 (비전, FrozenChord)
    'giant_droplet': (372, 'water', 8, 372),  # 거대 물방울 (비전, GiantDroplet)
    'deep_tentacles': (373, 'water', 8, 373),  # 심해 촉수 (비전, DeepTentacles)
    'celestial_clepsydra': (374, 'water', 10, 374),  # 천상의 물시계 (궁극, CelestialClepsydra)
    'glacial_epoch': (375, 'water', 10, 375),  # 영겁의 빙하 (궁극, GlacialEpoch)
    'sand_lash': (376, 'earth', 2, 376),  # 모래 채찍 (초급, SandLash)
    'sand_pellet': (377, 'earth', 2, 377),  # 모래 알갱이 (초급, SandPellet)
    'fossil_fang': (378, 'earth', 2, 378),  # 화석 송곳니 (초급, FossilFang)
    'clay_glob': (379, 'earth', 2, 379),  # 점토 덩이 (초급, ClayGlob)
    'lodestone_shard': (380, 'earth', 2, 380),  # 자철 조각 (초급, LodestoneShard)
    'sand_stomp': (381, 'earth', 2, 381),  # 흙먼지 발구르기 (초급, SandStomp)
    'gem_splinter': (382, 'earth', 2, 382),  # 보석 파편 (초급, GemSplinter)
    'hourglass_pit': (383, 'earth', 4, 383),  # 모래시계 구덩이 (중급, HourglassPit)
    'sand_spout': (384, 'earth', 4, 384),  # 모래 분수 (중급, SandSpout)
    'magnet_palm': (385, 'earth', 4, 385),  # 자석 손 (중급, MagnetPalm)
    'sand_burrow': (386, 'earth', 4, 386),  # 모래 두더지 (중급, SandBurrow)
    'mud_bomb': (387, 'earth', 4, 387),  # 진흙 폭탄 (중급, MudBomb)
    'stone_top': (388, 'earth', 4, 388),  # 바위 팽이 (중급, StoneTop)
    'quicksand_tide': (389, 'earth', 6, 389),  # 유사 파도 (고급, QuicksandTide)
    'fossil_wyrm': (390, 'earth', 6, 390),  # 화석 뱀뼈 (고급, FossilWyrm)
    'petrify_touch': (391, 'earth', 6, 391),  # 석화의 손 (고급, PetrifyTouch)
    'sand_coffin': (392, 'earth', 6, 392),  # 모래관 (고급, SandCoffin)
    'iron_dust_storm': (393, 'earth', 6, 393),  # 쇳가루 폭풍 (고급, IronDustStorm)
    'sand_fall': (394, 'earth', 6, 394),  # 모래 폭포 (고급, SandFall)
    'geode_bloom': (395, 'earth', 8, 395),  # 정동 개화 (비전, GeodeBloom)
    'dipole_prison': (396, 'earth', 8, 396),  # 쌍극 감옥 (비전, DipolePrison)
    'sandstorm': (397, 'earth', 8, 397),  # 모래폭풍 (비전, Sandstorm)
    'desertify': (398, 'earth', 8, 398),  # 사막화 (비전, Desertify)
    'great_hourglass': (399, 'earth', 10, 399),  # 대사막의 모래시계 (궁극, GreatHourglass)
    'core_magnetosphere': (400, 'earth', 10, 400),  # 지핵 자기장 (궁극, CoreMagnetosphere)
    'star_needle': (401, 'light', 2, 401),  # 별똥 바늘 (초급, StarNeedle)
    'focus_lens': (402, 'light', 2, 402),  # 초점 렌즈 (초급, FocusLens)
    'candle_hymn': (403, 'light', 2, 403),  # 촛불 성가 (초급, CandleHymn)
    'light_stitch': (404, 'light', 2, 404),  # 빛 바늘땀 (초급, LightStitch)
    'mirror_shard': (405, 'light', 2, 405),  # 거울 조각 (초급, MirrorShard)
    'star_hop': (406, 'light', 2, 406),  # 별빛 뛰기 (초급, StarHop)
    'light_fan': (407, 'light', 2, 407),  # 빛 부채 (초급, LightFan)
    'mirror_pane': (408, 'light', 4, 408),  # 거울판 (중급, MirrorPane)
    'triad_constellation': (409, 'light', 4, 409),  # 삼각 별자리 (중급, TriadConstellation)
    'sundial_sweep': (410, 'light', 4, 410),  # 해시계 바늘 (중급, SundialSweep)
    'southern_cross': (411, 'light', 4, 411),  # 남십자성 (중급, SouthernCross)
    'stained_shards': (412, 'light', 4, 412),  # 색유리 파편 (중급, StainedShards)
    'star_link': (413, 'light', 4, 413),  # 별 잇기 (중급, StarLink)
    'lighthouse': (414, 'light', 6, 414),  # 등대 (고급, Lighthouse)
    'refraction_spear': (415, 'light', 6, 415),  # 굴절 창 (고급, RefractionSpear)
    'big_dipper': (416, 'light', 6, 416),  # 북두칠성 (고급, BigDipper)
    'mirror_maze': (417, 'light', 6, 417),  # 거울 미궁 (고급, MirrorMaze)
    'orion_bow': (418, 'light', 6, 418),  # 오리온의 활 (고급, OrionBow)
    'cassiopeia': (419, 'light', 6, 419),  # 카시오페이아 (고급, Cassiopeia)
    'nebula_weave': (420, 'light', 8, 420),  # 성운 직조 (비전, NebulaWeave)
    'mirror_realm': (421, 'light', 8, 421),  # 거울 세계 (비전, MirrorRealm)
    'celestial_scope': (422, 'light', 8, 422),  # 천체 망원경 (비전, CelestialScope)
    'milky_way': (423, 'light', 8, 423),  # 은하수 (비전, MilkyWay)
    'zodiac_wheel': (424, 'light', 10, 424),  # 황도 12궁 (궁극, ZodiacWheel)
    'supernova': (425, 'light', 10, 425),  # 초신성 (궁극, Supernova)
    'crescent_sickle': (426, 'dark', 2, 426),  # 초승달 낫 (초급, CrescentSickle)
    'bat_flurry': (427, 'dark', 2, 427),  # 박쥐 떼 (초급, BatFlurry)
    'dream_splinter': (428, 'dark', 2, 428),  # 꿈 조각 (초급, DreamSplinter)
    'marionette_tug': (429, 'dark', 2, 429),  # 꼭두각시 당김 (초급, MarionetteTug)
    'moonbeam_dart': (430, 'dark', 2, 430),  # 달빛 화살 (초급, MoonbeamDart)
    'moon_shard': (431, 'dark', 2, 431),  # 달 조각 (초급, MoonShard)
    'black_moth': (432, 'dark', 2, 432),  # 검은 나방 (초급, BlackMoth)
    'lunar_mark': (433, 'dark', 4, 433),  # 달빛 표식 (중급, LunarMark)
    'shadow_play': (434, 'dark', 4, 434),  # 그림자극 (중급, ShadowPlay)
    'bat_cloak': (435, 'dark', 4, 435),  # 박쥐 망토 (중급, BatCloak)
    'half_moon_guard': (436, 'dark', 4, 436),  # 반달 방패 (중급, HalfMoonGuard)
    'quarter_blades': (437, 'dark', 4, 437),  # 상현·하현 칼 (중급, QuarterBlades)
    'moon_pull': (438, 'dark', 4, 438),  # 달의 인력 (중급, MoonPull)
    'moon_howl': (439, 'dark', 6, 439),  # 달의 울음 (고급, MoonHowl)
    'dream_labyrinth': (440, 'dark', 6, 440),  # 꿈의 미로 (고급, DreamLabyrinth)
    'bat_sovereign': (441, 'dark', 6, 441),  # 박쥐 군주 (고급, BatSovereign)
    'puppet_stage': (442, 'dark', 6, 442),  # 꼭두각시 무대 (고급, PuppetStage)
    'moon_drop': (443, 'dark', 6, 443),  # 달 떨구기 (고급, MoonDrop)
    'moon_pillar': (444, 'dark', 6, 444),  # 달빛 기둥 (고급, MoonPillar)
    'moon_wheel': (445, 'dark', 8, 445),  # 월광 회전륜 (비전, MoonWheel)
    'curtain_fall': (446, 'dark', 8, 446),  # 막 내림 (비전, CurtainFall)
    'new_moon_veil': (447, 'dark', 8, 447),  # 그믐 장막 (비전, NewMoonVeil)
    'shadow_doppel': (448, 'dark', 8, 448),  # 그림자 도플갱어 (비전, ShadowDoppel)
    'full_moon_descent': (449, 'dark', 10, 449),  # 만월 강림 (궁극, FullMoonDescent)
    'moonlit_masquerade': (450, 'dark', 10, 450),  # 월하 가면무도회 (궁극, MoonlitMasquerade)
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
