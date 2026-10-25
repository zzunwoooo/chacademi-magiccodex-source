# NPC 활동 장소 (오너 2026-10-26, 메모 #84)

타운맵을 돌며 정한 34곳. 각 자리 **가운데**에 서서 아래 명령을 그대로 입력하면 ChacaNPC `places.yml` 에 좌표가 저장된다 (반경 = 도착한 NPC가 돌아다니는 범위, 칸).
벽·계단 때문에 막힐 만한 길만 `/NPC관리 경로 추가 <영문이름>` 으로 길목을 순서대로 찍는다 (예: 로비→기숙사 홀, 1층→3층 계단).

| # | 장소 | 영문이름 | 반경 | 명령 |
|---|---|---|---|---|
| 1 | 항구 부두 | `harbor_pier` | 8 | `/NPC관리 장소 harbor_pier 8 항구 부두` |
| 2 | 낚시 용품점 | `fishing_shop` | 4 | `/NPC관리 장소 fishing_shop 4 낚시 용품점` |
| 3 | 물고기 판매점 | `fish_market` | 4 | `/NPC관리 장소 fish_market 4 물고기 판매점` |
| 4 | 메인 광장 | `main_plaza` | 14 | `/NPC관리 장소 main_plaza 14 메인 광장` |
| 5 | 피피 잡화점 | `pipi_shop` | 4 | `/NPC관리 장소 pipi_shop 4 피피 잡화점` |
| 6 | 빗자루 판매점 | `broom_shop` | 4 | `/NPC관리 장소 broom_shop 4 빗자루 판매점` |
| 7 | 빗자루 판매점 뒤 논밭 | `farm_field` | 8 | `/NPC관리 장소 farm_field 8 빗자루 판매점 뒤 논밭` |
| 8 | 퀘스트 게시판 | `quest_board` | 4 | `/NPC관리 장소 quest_board 4 퀘스트 게시판` |
| 9 | 뒷마을 가는 문 | `back_village_gate` | 5 | `/NPC관리 장소 back_village_gate 5 뒷마을 가는 문` |
| 10 | 가구점 | `furniture_shop` | 4 | `/NPC관리 장소 furniture_shop 4 가구점` |
| 11 | 결투장 건물 | `pvp_hall` | 6 | `/NPC관리 장소 pvp_hall 6 결투장 건물` |
| 12 | 아티팩트 연구소 | `artifact_lab` | 5 | `/NPC관리 장소 artifact_lab 5 아티팩트 연구소` |
| 13 | 마법학교 오르는 계단 | `school_stairs` | 5 | `/NPC관리 장소 school_stairs 5 마법학교 오르는 계단` |
| 14 | 작은 분수 광장 | `fountain_plaza` | 7 | `/NPC관리 장소 fountain_plaza 7 작은 분수 광장` |
| 15 | 학교 앞 쉼터 | `school_rest` | 8 | `/NPC관리 장소 school_rest 8 학교 앞 쉼터` |
| 16 | 마법학교 1층 로비 | `school_lobby` | 8 | `/NPC관리 장소 school_lobby 8 마법학교 1층 로비` |
| 17 | 대강당 | `great_hall` | 12 | `/NPC관리 장소 great_hall 12 대강당` |
| 18 | 지하 강화소 | `forge_basement` | 5 | `/NPC관리 장소 forge_basement 5 지하 강화소` |
| 19 | 지하 감옥 | `dungeon_cell` | 5 | `/NPC관리 장소 dungeon_cell 5 지하 감옥` |
| 20 | 2층 기숙사 홀 | `dorm_hall` | 8 | `/NPC관리 장소 dorm_hall 8 2층 기숙사 홀` |
| 21 | 기숙사 입구 | `dorm_entrances` | 5 | `/NPC관리 장소 dorm_entrances 5 기숙사 입구` |
| 22 | 움직이는 벽화 계단 | `painting_stairs` | 4 | `/NPC관리 장소 painting_stairs 4 움직이는 벽화 계단` |
| 23 | 3층 도서관 | `library` | 8 | `/NPC관리 장소 library 8 3층 도서관` |
| 24 | 3층 메인 계단 홀 | `main_stairs` | 6 | `/NPC관리 장소 main_stairs 6 3층 메인 계단 홀` |
| 25 | 나선형 계단 | `spiral_stairs` | 3 | `/NPC관리 장소 spiral_stairs 3 나선형 계단` |
| 26 | 강의실 1 | `classroom_1` | 6 | `/NPC관리 장소 classroom_1 6 강의실 1` |
| 27 | 강의실 2 | `classroom_2` | 6 | `/NPC관리 장소 classroom_2 6 강의실 2` |
| 28 | 강의실 3 | `classroom_3` | 6 | `/NPC관리 장소 classroom_3 6 강의실 3` |
| 29 | 도서관 2층 | `library_upper` | 6 | `/NPC관리 장소 library_upper 6 도서관 2층` |
| 30 | 카페테리아 | `cafeteria` | 8 | `/NPC관리 장소 cafeteria 8 카페테리아` |
| 31 | 병원 | `hospital` | 6 | `/NPC관리 장소 hospital 6 병원` |
| 32 | 가정집 1 | `house_1` | 4 | `/NPC관리 장소 house_1 4 가정집 1` |
| 33 | 상가 1 | `shop_1` | 4 | `/NPC관리 장소 shop_1 4 상가 1` |
| 34 | 연금술 공방 | `alchemy_workshop` | 5 | `/NPC관리 장소 alchemy_workshop 5 연금술 공방` |

다 등록하면 서버 `plugins/ChacaNPC/places.yml` 을 Claude 에게 전달 → NPC 85명 일과표(schedule) 일괄 작성. 스토리 퀘스트 NPC(Citizens 고정)는 일과표에서 제외.
