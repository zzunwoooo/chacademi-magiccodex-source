# 도서관·교실 책 기획 (메모 #105, Claude 2026-10-26 — 오너 최종 검수 대기)

- 파일: `deployment/operating-config/MagicCodexBridge/books.yml` (운영 overlay, 소스 기본값 `magic-codex-paper/src/main/resources/books.yml` 도 같은 내용).
- 적용: 서버 `plugins/MagicCodexBridge/books.yml` 교체 → `/서버관리 새로고침 책`. 블록 연결은 서버에서 책장을 바라보고 `/컨텐츠관리 책 연결 <책ID>` (아래 장소별 목록).
- 종류: **[마법]** 닫으면 마법 발견(플레이어당 1번) · **[마법·대기]** 그 마법이 아직 MagicDiscovery manual-conditions.yml 에 없어 commands 를 주석으로 꺼 둠(조건 등록 후 주석 풀기) · **[헛걸음]** 마법 없는 역사서·잡서·이론서·일기·민담 등.
- 선정 기준: 수업으로 확정 습득하는 마법과 겹치지 않게, 발견 조건이 애매하거나 지식형(생활·탐구·이론·역사) 마법. 스토리 스포일러 없음. 헛걸음 책은 같은 책장에 섞어 두고 일부는 지능 요구도 걸어 "겨우 읽었는데 마법 없음".
- 필요 지능 단계: 0 · 20 · 40 · 60 · 80 · 100 (전투 스펙 대충 ≈26 · 적당 ≈100 · 한계 ≈122 기준).

## 요약: 총 67권 — 헛걸음 37 · 마법 13 · 마법·대기 17

| 장소 | 권수 |
|---|---|
| 3층 도서관 (library) | 23 |
| 도서관 2층 (library_upper) | 16 |
| 강의실 1 (classroom_1) — 바람 교실 | 4 |
| 강의실 2 (classroom_2) — 장치·대지 실습실 | 3 |
| 강의실 3 (classroom_3) — 화염 실습실 | 3 |
| 연금술 공방 (alchemy_workshop) | 3 |
| 대강당 (great_hall) | 3 |
| 기숙사 입구 (dorm_entrances) · 2층 기숙사 홀 (dorm_hall) | 4 |
| 지하 강화소 (forge_basement) | 2 |
| 카페테리아 (cafeteria) · 병원 (hospital) | 3 |
| 낚시 용품점 · 아티팩트 연구소 · 1층 로비 | 3 |

## 책 목록 (장소 순)

| 장소 | 종류 | 책ID | 제목 | 연결 마법 | 필요 지능 | 위치 | 쪽 |
|---|---|---|---|---|---|---|---|
| 3층 도서관 (library) | 헛걸음 | library_guide | 대도서관 이용 안내 |  | 0 | 입구 안내 데스크 옆 책장 | 2 |
| 3층 도서관 (library) | 헛걸음 | ashen_night_chronicle | 잿빛 밤 연대기 |  | 0 | 끝 책장 (빛 고급 수업에서 이솔데가 펼쳐 보라고 한 책) | 3 |
| 3층 도서관 (library) | 헛걸음 | school_rules_commentary | 차카데미아 교칙 해설서 |  | 0 | 1층 서가 · 학칙 칸 (어둠 고급·응용 수업에서 그레고르가 읽어 오라고 한 책) | 2 |
| 3층 도서관 (library) | 헛걸음 | founding_history | 차카데미아 설립사 |  | 0 | 1층 서가 · 역사 칸 | 2 |
| 3층 도서관 (library) | 헛걸음 | archmage_life | 대마법사 전기 (요약본) |  | 20 | 1층 서가 · 역사 칸 (공식 역사만) | 2 |
| 3층 도서관 (library) | 헛걸음 | four_houses | 네 기숙사 이야기 |  | 0 | 1층 서가 · 기숙사 칸 | 2 |
| 3층 도서관 (library) | 헛걸음 | six_elements | 여섯 원소 개론 |  | 20 | 안쪽 서가 · 원소 이론 칸 | 2 |
| 3층 도서관 (library) | 헛걸음 | mana_circulation | 마나 순환 이론 개론 |  | 40 | 안쪽 서가 · 원소 이론 칸 (지능 걸린 이론서 — 마법 없음) | 2 |
| 3층 도서관 (library) | 헛걸음 | spell_naming | 마법 이름 짓는 법 |  | 20 | 안쪽 서가 · 원소 이론 칸 | 2 |
| 3층 도서관 (library) | 헛걸음 | discarded_list | 폐기 도서 목록 (사본) |  | 60 | 안쪽 서가 맨 아래 칸 (지능 60 — 겨우 읽어도 목록뿐) | 2 |
| 3층 도서관 (library) | 헛걸음 | structure_vol7 | 고등 마법 구조론 제7권 |  | 100 | 안쪽 서가 가장 높은 칸 (지능 100 — 제7권만 있음) | 2 |
| 3층 도서관 (library) | 마법 | mechanics_understanding | 기계에 대한 이해 | far_touch | 20 | far_touch 손길 너머 · 안쪽 서가 · 원소 이론 칸 옆 '기계' 칸 | 2 |
| 3층 도서관 (library) | 마법 | flight_records | 비행 연구 기록 | flight | 20 | flight 비행 · 안쪽 서가 · 바람 칸 (빗자루 사진이 붙은 두꺼운 기록철) | 2 |
| 3층 도서관 (library) | 마법 | chemistry_basics | 화학 기초 | cauldron_fill | 0 | cauldron_fill 가마솥 채우기 · 안쪽 서가 · 연금 칸 | 2 |
| 3층 도서관 (library) | 마법 | scribe_eraser | 서기관의 지우개 | wash_letters | 0 | wash_letters 글씨 씻기 · 입구 안내 데스크 아래 칸 (서기·필사 칸) | 2 |
| 3층 도서관 (library) | 마법 | energy_treatise | 에너지에 관하여 | heat_recovery | 80 | heat_recovery 열량 보존 · 안쪽 서가 · 화염 칸 | 2 |
| 3층 도서관 (library) | 마법 | three_firebirds | 불사조, 주작, 가루다 | firebird_hunt | 60 | firebird_hunt 불새 · 안쪽 서가 · 신화 칸 | 2 |
| 3층 도서관 (library) | 마법 | flame_colors | 불꽃의 색과 마력 | blue_bonfire | 60 | blue_bonfire 푸른 화톳불 · 안쪽 서가 · 화염 칸 | 2 |
| 3층 도서관 (library) | 마법 | spirit_contract_water | 정령계약서 - 수 | water_elemental | 80 | water_elemental 운디네 · 안쪽 서가 · 정령학 칸 (자물쇠 달린 서류철) | 2 |
| 3층 도서관 (library) | 마법 | spirit_contract_earth | 정령계약서 - 지 | earth_elemental | 80 | earth_elemental 그놈 · 안쪽 서가 · 정령학 칸 (수 계약서 바로 옆) | 2 |
| 3층 도서관 (library) | 마법 | immovable_forest_king | 움직이지 않는 숲의 왕 | forest_throne | 100 | forest_throne 숲의 왕좌 · 안쪽 서가 · 정령학 칸 맨 위 (두꺼운 가죽 장정) | 2 |
| 3층 도서관 (library) | 마법 | body_art_training | 체술 강화 | flame_leap | 60 | flame_leap 블레이즈 킥 · 안쪽 서가 · 화염 칸 맨 아래, 다른 책들 뒤에 숨겨진 얇은 책 (책장 뒷면 쪽에 연결 권장) | 2 |
| 3층 도서관 (library) | 마법·대기 | hungry_void | 텅 빈 것의 식욕 | void_maw 공허의 입 (대기) | 100 | 안쪽 서가 가장 깊은 칸, 표지가 없는 검은 책 (숨겨진 책) | 2 |
| 도서관 2층 (library_upper) | 헛걸음 | star_atlas | 별자리 도감 |  | 20 | 천문 칸 | 2 |
| 도서관 2층 (library_upper) | 헛걸음 | old_newsletter | 학교 소식지 묶음 (90년 전) |  | 0 | 신문·기록 칸 | 2 |
| 도서관 2층 (library_upper) | 헛걸음 | dragon_tales | 용에 관한 민담 |  | 40 | 신화·민담 칸 (용 이야기 — 마법 없음) | 2 |
| 도서관 2층 (library_upper) | 헛걸음 | alchemy_history | 연금술의 역사 |  | 40 | 연금 역사 칸 | 2 |
| 도서관 2층 (library_upper) | 헛걸음 | circle_geometry | 마법진 기하학 |  | 60 | 마법진 칸 (지능 60 — 도형만 잔뜩) | 2 |
| 도서관 2층 (library_upper) | 헛걸음 | third_year_diary | 어느 3학년의 일기 |  | 0 | 일기 칸 | 2 |
| 도서관 2층 (library_upper) | 헛걸음 | invisible_cloak_notes | 실패한 연구 노트 — 투명 망토 |  | 60 | 연구 노트 칸 (지능 60 — 실패한 연구) | 2 |
| 도서관 2층 (library_upper) | 헛걸음 | forest_bestiary | 숲 생물 관찰기 |  | 20 | 생물 칸 | 2 |
| 도서관 2층 (library_upper) | 마법·대기 | season_almanac | 계절 관측 일지 | season_sense 계절 예보 (대기) | 0 | 천문 칸 (별자리 도감 옆) | 2 |
| 도서관 2층 (library_upper) | 마법·대기 | mirrors_and_light | 거울과 빛의 길 | mirror_tune 반사 조율 (대기) | 20 | 광학 칸 | 2 |
| 도서관 2층 (library_upper) | 마법·대기 | prism_studies | 프리즘 연구 | prism_tint 프리즘 물들이기 (대기) | 20 | 광학 칸 (거울과 빛의 길 옆) | 2 |
| 도서관 2층 (library_upper) | 마법·대기 | straight_light | 곧은 빛에 관한 소론 | light_beam 빛줄기 (대기) | 40 | 광학 칸 맨 위 | 2 |
| 도서관 2층 (library_upper) | 마법·대기 | turning_back | 되돌리는 장치에 관하여 | rewind 되감기 (대기) | 40 | 시간 칸 | 2 |
| 도서관 2층 (library_upper) | 마법·대기 | shadow_twin | 그림자는 두 번 선다 | shadow_double 그림자 분신 (대기) | 60 | 그림자 칸 | 2 |
| 도서관 2층 (library_upper) | 마법·대기 | loosened_grip | 손아귀를 푸는 법 | loosen_grasp 손아귀 풀기 (대기) | 60 | 그림자 칸 | 2 |
| 도서관 2층 (library_upper) | 마법·대기 | warning_thread_manual | 경계를 긋는 실 | warning_thread 경고의 실 (대기) | 80 | 그림자 칸 맨 위 | 2 |
| 강의실 1 (classroom_1) — 바람 교실 | 헛걸음 | wind_textbook | 바람학 교과서 제1권 |  | 20 | 교탁 옆 책장 | 2 |
| 강의실 1 (classroom_1) — 바람 교실 | 헛걸음 | ungraded_homework | 채점 안 된 숙제 더미 |  | 0 | 뒤쪽 사물함 위 | 2 |
| 강의실 1 (classroom_1) — 바람 교실 | 마법 | falling_mechanics | 떨어지는 것들의 역학 | pressure_press | 40 | pressure_press 윈드 프레스 · 교탁 뒤 책장 (발견 조건 변경 제안: 앵무새 공격 → 이 책) | 2 |
| 강의실 1 (classroom_1) — 바람 교실 | 마법 | whirlwind_axis | 회오리의 축 | pressure_rupture | 60 | pressure_rupture 토네이도 스핀 · 교탁 뒤 책장 (발견 조건 변경 제안: 제자리 10바퀴 → 이 책) | 2 |
| 강의실 2 (classroom_2) — 장치·대지 실습실 | 헛걸음 | lecture_prep_notes | 어느 교수의 수업 준비 노트 |  | 40 | 교탁 서랍 옆 책장 (지능 40) | 2 |
| 강의실 2 (classroom_2) — 장치·대지 실습실 | 마법·대기 | conduit_design | 마력 도관 설계 | conduit_turn 도관 회전 (대기) | 20 | 실습 교재 칸 | 2 |
| 강의실 2 (classroom_2) — 장치·대지 실습실 | 마법·대기 | weight_and_balance | 무게와 균형 | stone_weight 무게추 (대기) | 20 | 실습 교재 칸 (마력 도관 설계 옆) | 2 |
| 강의실 3 (classroom_3) — 화염 실습실 | 헛걸음 | fire_safety_rules | 화염 실습실 안전 수칙 |  | 0 | 문 옆 벽 책장 | 2 |
| 강의실 3 (classroom_3) — 화염 실습실 | 마법·대기 | steam_primer | 증기 기관 입문 | steam_ignition 증기 점화 (대기) | 40 | 실습 교재 칸 | 2 |
| 강의실 3 (classroom_3) — 화염 실습실 | 마법·대기 | thawing_notes | 얼음을 녹이는 열 | thawing_heat 해빙의 열기 (대기) | 40 | 실습 교재 칸 (증기 기관 입문 옆) | 2 |
| 연금술 공방 (alchemy_workshop) | 헛걸음 | herb_guide | 약초 도감 |  | 0 | 약초 선반 옆 | 2 |
| 연금술 공방 (alchemy_workshop) | 헛걸음 | potion_accidents | 폭발한 물약 기록 |  | 20 | 작업대 아래 (지능 20) | 2 |
| 연금술 공방 (alchemy_workshop) | 마법·대기 | alchemist_insight | 연금술사의 번뜩임 | insight_flash 영감의 섬광 (대기) | 40 | 작업대 위 노트 꽂이 | 2 |
| 대강당 (great_hall) | 헛걸음 | ceremony_program | 입학식 식순 |  | 0 | 단상 옆 책장 | 2 |
| 대강당 (great_hall) | 헛걸음 | alumni_roll | 역대 졸업생 명부 |  | 0 | 뒤쪽 벽 책장 | 2 |
| 대강당 (great_hall) | 마법·대기 | statue_keeper_log | 석상 관리 일지 | statue_wake 석상 깨우기 (대기) | 20 | 단상 뒤 책장 (발견 조건 변경 제안: 밤에 갑옷 거치대 앞 30초 → 이 책) | 2 |
| 기숙사 입구 (dorm_entrances) · 2층 기숙사 홀 (dorm_hall) | 헛걸음 | dorm_guide | 기숙사 생활 안내 |  | 0 | 기숙사 입구 게시판 옆 책장 | 2 |
| 기숙사 입구 (dorm_entrances) · 2층 기숙사 홀 (dorm_hall) | 마법·대기 | locksmith_secret | 자물쇠 장인의 비밀 | sealing_thread 봉인의 실 (대기) | 40 | 기숙사 입구 사감 책상 옆 (발견 조건 변경 제안: 내 상자 100번 열고 닫기 → 이 책) | 2 |
| 기숙사 입구 (dorm_entrances) · 2층 기숙사 홀 (dorm_hall) | 헛걸음 | unsent_letter | 전하지 못한 편지 |  | 0 | 2층 기숙사 홀 소파 옆 책장 | 2 |
| 기숙사 입구 (dorm_entrances) · 2층 기숙사 홀 (dorm_hall) | 헛걸음 | dorm_ghost_stories | 기숙사 괴담 모음 |  | 0 | 2층 기숙사 홀 벽난로 옆 책장 | 2 |
| 지하 강화소 (forge_basement) | 헛걸음 | otto_ledger | 오토의 장부 |  | 0 | 계산대 뒤 선반 | 2 |
| 지하 강화소 (forge_basement) | 마법·대기 | arcane_imprint_manual | 도구에 새기는 마력 | arcane_imprint 마력 각인 (대기) | 80 | 모루 옆 공구 선반 위 (발견 조건 변경 제안: 모루로 이름 10번 → 이 책) | 2 |
| 카페테리아 (cafeteria) · 병원 (hospital) | 헛걸음 | dorothea_recipes | 도로테아의 요리 노트 |  | 0 | 카페테리아 배식대 옆 선반 | 2 |
| 카페테리아 (cafeteria) · 병원 (hospital) | 헛걸음 | lost_library_book | 누군가 두고 간 도서관 책 |  | 0 | 카페테리아 창가 자리 의자 위 (도서관에서 누가 들고 나온 책) | 2 |
| 카페테리아 (cafeteria) · 병원 (hospital) | 헛걸음 | first_aid_guide | 응급 처치 안내서 |  | 0 | 병원 대기 의자 옆 책장 | 2 |
| 낚시 용품점 · 아티팩트 연구소 · 1층 로비 | 헛걸음 | sea_fishing_primer | 바다 낚시 입문 |  | 0 | 낚시 용품점 카운터 옆 선반 | 2 |
| 낚시 용품점 · 아티팩트 연구소 · 1층 로비 | 헛걸음 | artifact_inventory | 연구소 소장품 목록 |  | 40 | 아티팩트 연구소 진열장 옆 서류철 (지능 40) | 2 |
| 낚시 용품점 · 아티팩트 연구소 · 1층 로비 | 헛걸음 | visitor_log | 방문자 명부 |  | 0 | 1층 로비 안내 책상 위 | 2 |

## 블록 연결 명령 (장소별로 그 책장을 바라보고)


### 3층 도서관 (library)

- 입구 안내 데스크 옆 책장 → `/컨텐츠관리 책 연결 library_guide`  (대도서관 이용 안내)
- 끝 책장 (빛 고급 수업에서 이솔데가 펼쳐 보라고 한 책) → `/컨텐츠관리 책 연결 ashen_night_chronicle`  (잿빛 밤 연대기)
- 1층 서가 · 학칙 칸 (어둠 고급·응용 수업에서 그레고르가 읽어 오라고 한 책) → `/컨텐츠관리 책 연결 school_rules_commentary`  (차카데미아 교칙 해설서)
- 1층 서가 · 역사 칸 → `/컨텐츠관리 책 연결 founding_history`  (차카데미아 설립사)
- 1층 서가 · 역사 칸 (공식 역사만) → `/컨텐츠관리 책 연결 archmage_life`  (대마법사 전기 (요약본))
- 1층 서가 · 기숙사 칸 → `/컨텐츠관리 책 연결 four_houses`  (네 기숙사 이야기)
- 안쪽 서가 · 원소 이론 칸 → `/컨텐츠관리 책 연결 six_elements`  (여섯 원소 개론)
- 안쪽 서가 · 원소 이론 칸 (지능 걸린 이론서 — 마법 없음) → `/컨텐츠관리 책 연결 mana_circulation`  (마나 순환 이론 개론)
- 안쪽 서가 · 원소 이론 칸 → `/컨텐츠관리 책 연결 spell_naming`  (마법 이름 짓는 법)
- 안쪽 서가 맨 아래 칸 (지능 60 — 겨우 읽어도 목록뿐) → `/컨텐츠관리 책 연결 discarded_list`  (폐기 도서 목록 (사본))
- 안쪽 서가 가장 높은 칸 (지능 100 — 제7권만 있음) → `/컨텐츠관리 책 연결 structure_vol7`  (고등 마법 구조론 제7권)
- far_touch 손길 너머 · 안쪽 서가 · 원소 이론 칸 옆 '기계' 칸 → `/컨텐츠관리 책 연결 mechanics_understanding`  (기계에 대한 이해)
- flight 비행 · 안쪽 서가 · 바람 칸 (빗자루 사진이 붙은 두꺼운 기록철) → `/컨텐츠관리 책 연결 flight_records`  (비행 연구 기록)
- cauldron_fill 가마솥 채우기 · 안쪽 서가 · 연금 칸 → `/컨텐츠관리 책 연결 chemistry_basics`  (화학 기초)
- wash_letters 글씨 씻기 · 입구 안내 데스크 아래 칸 (서기·필사 칸) → `/컨텐츠관리 책 연결 scribe_eraser`  (서기관의 지우개)
- heat_recovery 열량 보존 · 안쪽 서가 · 화염 칸 → `/컨텐츠관리 책 연결 energy_treatise`  (에너지에 관하여)
- firebird_hunt 불새 · 안쪽 서가 · 신화 칸 → `/컨텐츠관리 책 연결 three_firebirds`  (불사조, 주작, 가루다)
- blue_bonfire 푸른 화톳불 · 안쪽 서가 · 화염 칸 → `/컨텐츠관리 책 연결 flame_colors`  (불꽃의 색과 마력)
- water_elemental 운디네 · 안쪽 서가 · 정령학 칸 (자물쇠 달린 서류철) → `/컨텐츠관리 책 연결 spirit_contract_water`  (정령계약서 - 수)
- earth_elemental 그놈 · 안쪽 서가 · 정령학 칸 (수 계약서 바로 옆) → `/컨텐츠관리 책 연결 spirit_contract_earth`  (정령계약서 - 지)
- forest_throne 숲의 왕좌 · 안쪽 서가 · 정령학 칸 맨 위 (두꺼운 가죽 장정) → `/컨텐츠관리 책 연결 immovable_forest_king`  (움직이지 않는 숲의 왕)
- flame_leap 블레이즈 킥 · 안쪽 서가 · 화염 칸 맨 아래, 다른 책들 뒤에 숨겨진 얇은 책 (책장 뒷면 쪽에 연결 권장) → `/컨텐츠관리 책 연결 body_art_training`  (체술 강화)
- void_maw 공허의 입 · 안쪽 서가 가장 깊은 칸, 표지가 없는 검은 책 (숨겨진 책) → `/컨텐츠관리 책 연결 hungry_void`  (텅 빈 것의 식욕)

### 도서관 2층 (library_upper)

- 천문 칸 → `/컨텐츠관리 책 연결 star_atlas`  (별자리 도감)
- 신문·기록 칸 → `/컨텐츠관리 책 연결 old_newsletter`  (학교 소식지 묶음 (90년 전))
- 신화·민담 칸 (용 이야기 — 마법 없음) → `/컨텐츠관리 책 연결 dragon_tales`  (용에 관한 민담)
- 연금 역사 칸 → `/컨텐츠관리 책 연결 alchemy_history`  (연금술의 역사)
- 마법진 칸 (지능 60 — 도형만 잔뜩) → `/컨텐츠관리 책 연결 circle_geometry`  (마법진 기하학)
- 일기 칸 → `/컨텐츠관리 책 연결 third_year_diary`  (어느 3학년의 일기)
- 연구 노트 칸 (지능 60 — 실패한 연구) → `/컨텐츠관리 책 연결 invisible_cloak_notes`  (실패한 연구 노트 — 투명 망토)
- 생물 칸 → `/컨텐츠관리 책 연결 forest_bestiary`  (숲 생물 관찰기)
- season_sense 계절 예보 · 천문 칸 (별자리 도감 옆) → `/컨텐츠관리 책 연결 season_almanac`  (계절 관측 일지)
- mirror_tune 반사 조율 · 광학 칸 → `/컨텐츠관리 책 연결 mirrors_and_light`  (거울과 빛의 길)
- prism_tint 프리즘 물들이기 · 광학 칸 (거울과 빛의 길 옆) → `/컨텐츠관리 책 연결 prism_studies`  (프리즘 연구)
- light_beam 빛줄기 · 광학 칸 맨 위 → `/컨텐츠관리 책 연결 straight_light`  (곧은 빛에 관한 소론)
- rewind 되감기 · 시간 칸 → `/컨텐츠관리 책 연결 turning_back`  (되돌리는 장치에 관하여)
- shadow_double 그림자 분신 · 그림자 칸 → `/컨텐츠관리 책 연결 shadow_twin`  (그림자는 두 번 선다)
- loosen_grasp 손아귀 풀기 · 그림자 칸 → `/컨텐츠관리 책 연결 loosened_grip`  (손아귀를 푸는 법)
- warning_thread 경고의 실 · 그림자 칸 맨 위 → `/컨텐츠관리 책 연결 warning_thread_manual`  (경계를 긋는 실)

### 강의실 1 (classroom_1) — 바람 교실

- 교탁 옆 책장 → `/컨텐츠관리 책 연결 wind_textbook`  (바람학 교과서 제1권)
- 뒤쪽 사물함 위 → `/컨텐츠관리 책 연결 ungraded_homework`  (채점 안 된 숙제 더미)
- pressure_press 윈드 프레스 · 교탁 뒤 책장 (발견 조건 변경 제안: 앵무새 공격 → 이 책) → `/컨텐츠관리 책 연결 falling_mechanics`  (떨어지는 것들의 역학)
- pressure_rupture 토네이도 스핀 · 교탁 뒤 책장 (발견 조건 변경 제안: 제자리 10바퀴 → 이 책) → `/컨텐츠관리 책 연결 whirlwind_axis`  (회오리의 축)

### 강의실 2 (classroom_2) — 장치·대지 실습실

- 교탁 서랍 옆 책장 (지능 40) → `/컨텐츠관리 책 연결 lecture_prep_notes`  (어느 교수의 수업 준비 노트)
- conduit_turn 도관 회전 · 실습 교재 칸 → `/컨텐츠관리 책 연결 conduit_design`  (마력 도관 설계)
- stone_weight 무게추 · 실습 교재 칸 (마력 도관 설계 옆) → `/컨텐츠관리 책 연결 weight_and_balance`  (무게와 균형)

### 강의실 3 (classroom_3) — 화염 실습실

- 문 옆 벽 책장 → `/컨텐츠관리 책 연결 fire_safety_rules`  (화염 실습실 안전 수칙)
- steam_ignition 증기 점화 · 실습 교재 칸 → `/컨텐츠관리 책 연결 steam_primer`  (증기 기관 입문)
- thawing_heat 해빙의 열기 · 실습 교재 칸 (증기 기관 입문 옆) → `/컨텐츠관리 책 연결 thawing_notes`  (얼음을 녹이는 열)

### 연금술 공방 (alchemy_workshop)

- 약초 선반 옆 → `/컨텐츠관리 책 연결 herb_guide`  (약초 도감)
- 작업대 아래 (지능 20) → `/컨텐츠관리 책 연결 potion_accidents`  (폭발한 물약 기록)
- insight_flash 영감의 섬광 · 작업대 위 노트 꽂이 → `/컨텐츠관리 책 연결 alchemist_insight`  (연금술사의 번뜩임)

### 대강당 (great_hall)

- 단상 옆 책장 → `/컨텐츠관리 책 연결 ceremony_program`  (입학식 식순)
- 뒤쪽 벽 책장 → `/컨텐츠관리 책 연결 alumni_roll`  (역대 졸업생 명부)
- statue_wake 석상 깨우기 · 단상 뒤 책장 (발견 조건 변경 제안: 밤에 갑옷 거치대 앞 30초 → 이 책) → `/컨텐츠관리 책 연결 statue_keeper_log`  (석상 관리 일지)

### 기숙사 입구 (dorm_entrances) · 2층 기숙사 홀 (dorm_hall)

- 기숙사 입구 게시판 옆 책장 → `/컨텐츠관리 책 연결 dorm_guide`  (기숙사 생활 안내)
- sealing_thread 봉인의 실 · 기숙사 입구 사감 책상 옆 (발견 조건 변경 제안: 내 상자 100번 열고 닫기 → 이 책) → `/컨텐츠관리 책 연결 locksmith_secret`  (자물쇠 장인의 비밀)
- 2층 기숙사 홀 소파 옆 책장 → `/컨텐츠관리 책 연결 unsent_letter`  (전하지 못한 편지)
- 2층 기숙사 홀 벽난로 옆 책장 → `/컨텐츠관리 책 연결 dorm_ghost_stories`  (기숙사 괴담 모음)

### 지하 강화소 (forge_basement)

- 계산대 뒤 선반 → `/컨텐츠관리 책 연결 otto_ledger`  (오토의 장부)
- arcane_imprint 마력 각인 · 모루 옆 공구 선반 위 (발견 조건 변경 제안: 모루로 이름 10번 → 이 책) → `/컨텐츠관리 책 연결 arcane_imprint_manual`  (도구에 새기는 마력)

### 카페테리아 (cafeteria) · 병원 (hospital)

- 카페테리아 배식대 옆 선반 → `/컨텐츠관리 책 연결 dorothea_recipes`  (도로테아의 요리 노트)
- 카페테리아 창가 자리 의자 위 (도서관에서 누가 들고 나온 책) → `/컨텐츠관리 책 연결 lost_library_book`  (누군가 두고 간 도서관 책)
- 병원 대기 의자 옆 책장 → `/컨텐츠관리 책 연결 first_aid_guide`  (응급 처치 안내서)

### 낚시 용품점 · 아티팩트 연구소 · 1층 로비

- 낚시 용품점 카운터 옆 선반 → `/컨텐츠관리 책 연결 sea_fishing_primer`  (바다 낚시 입문)
- 아티팩트 연구소 진열장 옆 서류철 (지능 40) → `/컨텐츠관리 책 연결 artifact_inventory`  (연구소 소장품 목록)
- 1층 로비 안내 책상 위 → `/컨텐츠관리 책 연결 visitor_log`  (방문자 명부)

## 대기 책 켜기

[마법·대기] 책은 books.yml 에서 그 책의 commands 주석(#)을 풀고, MagicDiscovery manual-conditions.yml 에 그 마법의 수동 조건을 등록한 뒤 `/서버관리 새로고침 책`. 발견 조건 파일 변경은 오너가 나중에 일괄 재기획할 때 함께 (오너 원칙).
