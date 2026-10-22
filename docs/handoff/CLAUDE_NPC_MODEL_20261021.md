# Claude 2026-10-21 — Citizens NPC 3D 외형 (GLB 스키닝 렌더러 + ChacaNPC 연동) — 미빌드

- 브랜치: `claude/npc-model-20261021`
- 기준: `codex/followups-integrated-20261020-task12` @ `0cd1454` (최신 통합본). 과거 브랜치 덮어쓰기 없음, 변경분만.
- 이번 범위: 개발까지. **운영 서버 배포·재시작·런처 공개는 하지 않음** (별도 승인 후).

## 1. 바뀐 모듈

| 모듈 | 내용 | 기존 파일 수정 |
|---|---|---|
| `client-mods/chacademi-npc-model/` (새 Fabric 모드) | GLB 읽기·검증, CPU 스키닝, 애니메이션 상태, 모델 캐시·BC7, 렌더·이름표 mixin | 없음 (새 모듈) |
| `chaca-npc-model-protocol/` (새, 순수 Java) | 패킷 계약 `chacademi:npc_model` / `chacademi:npc_model_c` v1 | 없음 |
| `chaca-npc-paper/` (ChacaNPC) | `appearance/` 패키지, `cmd/AppearanceCommands.java` | `ChacaNpcPlugin`(+15줄 연결), `DialogueService`(대화 중 NPC 목록·인사 호출), `NpcManager`(스토리 대화 열림 → 인사 호출 2줄), `build.gradle`(소스 폴더·버전 `0.3.0-npcmodel.1`) |
| `tools/npc-model/` | `normalize_npc_model.py` (FBX→배포용 GLB), `preview_npc_model.py` (게임 없이 자세 렌더) | 없음 |
| `docs/handoff/patches/story-marker-npc-model-height.patch` | 스토리 머리 위 징표 높이를 3D 모델 꼭대기로 (`claude/story-steps-20261020` @ 7e6ac03 에 `git apply --check` 통과) | 적용 안 함 (스토리 브랜치가 아직 통합 전) |

MagicCodex(Bridge·Fabric)·PortableVFX·스토리 모드는 **건드리지 않음**. 클릭 우선순위(선물 → 스토리 → AI)·Bridge claim 그대로.

## 2. 버전·호환

- 클라이언트: MC 1.21.4, loader 0.16.10, fabric-api 0.119.4+1.21.4, 모장 매핑 (chacademy-motion 과 동일 조합, loom 1.10-SNAPSHOT).
- 서버: Paper 1.21.4, Citizens 컴파일 기준 `2.0.37-SNAPSHOT`(기존 값 그대로 — 실제 school 설치 버전은 여전히 미확인).
- 프로토콜 v1. 모드만 있고 서버가 구버전이면 HELLO_ACK 가 안 와서 아무것도 안 바뀜. 서버만 새 버전이면 채널을 안 듣는 플레이어에게 안 보냄. 버전이 다르면 서버 콘솔 경고 1회 + 원래 외형.
- 새 의존 없음. ChacaNPC 는 Citizens·Paper API만 추가 사용(`PlayerTrackEntityEvent`, `Entity#getTrackedBy`, `NPCDespawnEvent`, `NPCRemoveEvent`).

## 3. 서버 부하 (설계 + 측정 방법)

- 매 틱 작업 없음. 위치·회전 패킷 없음(바닐라 엔티티 동기화 그대로). 뼈대·물리·렌더 계산 없음.
- 보내는 때: 추적 시작(다음 틱, 그 플레이어에게만) · 외형 명령 · AI 대화 시작/끝(1초 주기 비교, 바뀐 NPC만) · 인사 · 새로고침. 메시지 크기 약 25~45바이트.
- `PlayerTrackEntityEvent` 는 모든 엔티티에 불려서 플레이어 엔티티가 아니면 바로 반환, 모드 미사용자도 바로 반환.
- 측정: `/NPC관리 외형 통계` → 모드 사용자 수, 보낸 패킷 수·분당·바이트, 추적 검사 횟수·평균 µs. **실서버에서 아직 측정하지 않음.**

## 4. 클라이언트 성능 (측정값은 클라우드 샌드박스 CPU 1스레드, 게임 밖 — 실기 수치 아님)

- 루카(삼각형 15,977·관절 65) 자세+스키닝 1회 ≈ 0.39ms. NPC 10명 전부 매 프레임 갱신 시 3.85ms, 20명 7.85ms.
- 실제로는 거리별 갱신(30/15/8/3Hz) + 프레임 예산 3ms 로 제한. 화면 밖은 계산 안 함.
- **미측정**: 정점 전송(NPC 1명 프레임당 약 6.4만 정점)을 포함한 프레임 시간, 셰이더(Iris) 사용/미사용, NPC 1·10·20명 실기, GPU 메모리. → 실기 체크리스트 7절.
- 텍스처: 2048×2 → BC7 약 11MB/모델 (RGBA8 이면 약 43MB). 첫 로드 때 BC7 인코딩(2048 한 장 약 2~3초, 작업 스레드)·`.cache` 저장, 이후 즉시.

## 5. 배포 순서 (승인 후)

1. 런처 콘텐츠: `<게임폴더>/chacademi/npc-models/` 에 `models.json` + `luka.glb` + `ria.glb` (GLB는 공개 저장소에 없음 — 오너 PC `Desktop\차카데미npc_claude\3D모델_npc-models\` 에 전달). models.json 의 sha256 과 파일이 같아야 함.
2. 런처 모드 목록에 `chacademi-npc-model-0.1.0-npcmodel.1.jar` 추가 (fabric-api 필요, 다른 모드 의존 없음).
3. school 서버: `chaca-npc-0.3.0-npcmodel.1.jar` 교체, `plugins/ChacaNPC/npc-models.yml` 넣기 (예시 `client-mods/chacademi-npc-model/dist/server-config/npc-models.yml`: `luka`→luka, `st_lia`→ria). 서버 재시작.
4. 확인: `/NPC관리 외형 정보` (NPC 옆), `/NPC관리 외형 통계`, 클라이언트 로그 `[npc-models] 모델 'luka' 준비됨`.

순서가 바뀌어도 안전: 어느 한쪽이 없으면 원래 외형.

## 6. 롤백

- 외형만 끄기: `/NPC관리 외형 끄기`(NPC 하나) 또는 npc-models.yml 의 characters 를 비우고 `/NPC관리 외형 새로고침` → 모드 사용자 화면이 즉시 원래 외형.
- 서버 되돌리기: `chaca-npc-0.2.0-commands.1.jar` 로 교체. npc-models.yml 은 남아 있어도 무시됨. DB·Citizens 데이터는 이 기능이 쓰지 않음.
- 클라이언트 되돌리기: 런처에서 모드 jar 제거 (npc-models 폴더는 남아도 무해).

## 7. 검증

실행함:
- 순수 Java 테스트 14개 통과 (대체 러너): GLB 읽기·Blender 접두어 제거, 관절 회전 스키닝, slerp, 손상·잘림·필수 확장·외부 URI·순환·크기 상한 거부, 확장 진단, 실제 루카·리아 로드(키·발바닥·walk·관절 65), 걷기 히스테리시스, 인사 1회→대화→대기, talk 없을 때 대기 대체, models.json 경로 이탈·ID 형식·해시 누락 거부, JSON, 프로토콜 왕복·버전 불일치·남는 바이트·잘못된 모델 ID.
- Java 스키닝 결과 = Python 기준 구현 (루카·리아 walk 프레임 전체 정점 최대 오차 3.8e-7).
- 정리 도구로 루카·리아 변환 후 앞·옆 자세 렌더 확인 (idle·walk·wait), 실제 models.json 의 해시·클립 이름 일치 확인.
- 스토리 징표 패치 `git apply --check` 통과.

실행 못 함 (호스팅에서 필요):
- **Fabric 모드·ChacaNPC Gradle 빌드** (이 환경에서 Fabric/Mojang/Paper/Citizens 저장소 접근 차단). mixin 대상 서명(1.21.4 `PlayerRenderer#extractRenderState`, `LivingEntityRenderer#render`)과 렌더 상태 필드 이름은 첫 빌드·첫 실행에서 확인 필요 — `defaultRequire: 1` 이라 틀리면 시작 때 바로 크래시로 드러남.
- 실기 전부: 루카·리아 표시, 클릭·이동 유지, 원래 외형 fallback(모드 없음·파일 지움·해시 바꿈·models.json 깨짐), 재접속·청크 재진입·서버 이동·NPC 재생성, 이름표 높이, 목·팔·다리 변형과 머리카락·소매·치마 관통(엔진/자산 구분 기록), 셰이더팩, 성능 수치.

## 8. 자산 관찰 (엔진 문제 아님)

- 리아 첫 파일(`리아.glb`, Tripo 원본): 관절 노드 변환 0개, 뼈대가 메쉬와 90° 회전, 정점 80~86% Hips 고정 → 애니메이션 불가였음. 이후 받은 `ria_wait_walk_v5.fbx` 는 정상(웨이트 고르게 분포, 손 관절 위치 일치).
- 루카 FBX: FBX2glTF 변환 시 텍스처 연결이 끊겨 1px 자리표시 그림이 들어감 → 정리 도구가 `.fbm` 그림을 메쉬 노드 이름으로 다시 붙임.
- 루카 walk 는 원래 루트가 전진(초당 1.23블록) → 제자리로 바꾸고 속도는 재생 속도 계산에 씀. 리아 walk 는 원래 제자리(README 권장 0.636 m/s @1.14m → 1.7블록 키 기준 0.945).
- 리아는 talk·greet 클립 없음 → 대화 중에도 wait. 루카는 talk = wait (오너 확인), greet 없음.

## 9. 미완료·후속

- 인사(greet) 클립: 두 모델 모두 없음. 클립이 오면 models.json `greet` 에 이름만 넣으면 됨 (같은 골격 별도 파일은 `clipFiles`).
- LOD 메쉬 없음 → 모든 거리 원본 메쉬. 저폴리 GLB 를 받으면 `lods` 에 추가.
- 스토리 징표 높이: 스토리 브랜치 통합 때 위 패치 적용.
- Citizens 홀로그램 이름표를 쓰는 설정이면 그 높이는 별도 조정 필요.
- 얼굴 모프·립싱크·천 물리·GPU 스키닝: 범위 밖.

## 11. 독립 검토 반영 (2커밋째)

코드 읽기 검토(컴파일 없이)에서 나온 것 중 고친 것:
- 컴파일 오류: `AppearanceCommands` 의 같은 서명 `scale(...)` 두 개 → 파서를 `parseScale` 로.
- HELLO/HELLO_ACK 무한 왕복: 클라는 새 세션일 때만 HELLO, 서버는 처음 연결될 때만 HELLO_ACK.
- 밉맵이 안 쓰이던 문제: `GL_TEXTURE_MIN_LOD/MAX_LOD` 설정 (HudArt 와 같은 처리).
- BC7 미지원 GPU(macOS 등): 렌더 스레드에서 먼저 지원 여부를 확인해 작업 스레드가 압축을 건너뜀 (메인 스레드에서 푸는 멈춤 없음). 캐시 파일 이름에 인코더 버전.
- 모델이 너무 빨리 내려가 다시 읽던 문제: 마지막 NPC가 사라진 시각부터 2분 세도록.
- 그리다 예외: 게임을 멈추지 않고 그 모델을 실패로 두고 원래 외형으로 (포즈 스택은 finally 로 복구).
- 숨긴 몸에서 화살·벌침·머리 칸 해골이 떠 보이던 것, 원래 투명한 NPC가 모델로 보이던 것.
- GPU 업로드 실패 때 텍스처 누수, 관리자 '동작 대화' 테스트가 1초 만에 풀리던 것.
- 삼각형 그리기 QUADS(정점 4개) → TRIANGLES(3개).

남은 메모: 플러그인 핫리로드(PlugMan) 뒤에는 재접속해야 연결됨(정식 재시작은 문제없음), 반투명 재질은 Fabulous 그래픽에서 섞임이 다를 수 있음, 큰 텍스처 GPU 업로드는 한 프레임에 몰림(첫 로드 때만), NPC 수·거리 상한은 아직 없음.

## 10. GPT 빌드 때 주의

- 새 Fabric 프로젝트는 단독 Gradle 프로젝트(`client-mods/chacademi-npc-model`, wrapper 포함). 루트 settings.gradle 에 넣지 않음 (chacademy-motion 과 같은 방식).
- `chaca-npc-paper` 는 `../chaca-npc-model-protocol/src` 를 소스 폴더로 포함 (클라이언트 모드도 `../../chaca-npc-model-protocol/src`).
- 테스트: `chaca-npc-paper:test` 에 프로토콜 테스트 3개 추가, 모드 `test` 에 CoreTest 11개 (실제 GLB 가 `dist/npc-models/` 에 없으면 그 1개는 그냥 통과).
- `Bc7Codec.java` 는 PortableVFX `ClaudeTextureCodec` 복사본(패키지·이름만 변경).

## 12. 2026-10-22 추가 — 모델 비율 조정 (모드 0.1.1-npcmodel.2, 클라이언트만)

- 사람 비율 모델이 마크 캐릭터 옆에서 작아 보이는 문제 → 키를 크게 올리는 대신 다리·몸통·팔 길이를 줄이고 키를 조금만 올림.
- `core/Proportion.java`: 읽을 때 바인드 자세 기준으로 뼈 길이를 바꿈 (정점은 뼈 길이 방향으로만, 노드 위치·inverse bind 같이 수정). 동작은 회전만 쓰므로 그대로. 변환 도구 `--legs/--torso/--arms` 와 같은 계산 (루카로 비교: 차이 2e-7).
- models.json 새 키: `proportion {legs, torso, arms}` (0.5~1.5), `height` (블록, 0 = scale). 시작값 루카 1.95 · 리아 1.9 · 선장 2.1 · 엘레나 2.5(모자 포함), 비율 모두 0.82/0.95/0.92.
- 클라이언트 명령 `/npc모델 정보|비율|키|저장|되돌리기` (내 화면만, 서버 영향 없음). `저장`은 내 models.json 만 바꿈 → 확정값은 런처 배포본 models.json 에 반영.
- 테스트 16개 통과 (비율 조정·models.json 저장/보존 추가). 빌드: `client-mods/chacademi-npc-model` 만 다시 빌드. 서버 ChacaNPC 변경 없음.
- 주의: 클립 파일(clipFiles)에 다리·몸통 뼈의 위치(translation) 채널이 있으면 줄인 길이를 덮어씀 (지금 모델들은 골반만 있음).

## 13. 2026-10-22 — 피피 + 다른 모델 동작 빌려 쓰기 (모드 0.1.1-npcmodel.2 에 포함)

- 피피(`pipi`, 블렌더 GLB): 클립 greet_02·walk 만 있고 대기 없음 → `clipFiles` 로 teacher_ellna.glb 의 idle 사용 (`"idle": "teacher_ellna:idle"`), 인사 = greet_02.
- 클립 가져오기: 회전 채널만 쓰고 위치·크기 채널은 버림. 뼈대 맨 위(Hips)는 파일마다 부모 좌표가 달라 기본 자세 대비 회전량으로 옮김 (안 하면 눕는 문제 확인).
- 가져온 클립은 항상 `<파일>:<클립>` 이름, 자기 클립과 안 겹치면 원래 이름으로도 (자기 클립 우선 — 예전엔 클립 파일이 덮었음).
- 비율 조정이 위치 채널을 가진 클립(블렌더 내보내기)도 처리: 바뀐 뼈 길이만큼 채널 값을 옮김 (안 하면 다리 길이가 원래대로 돌아감). 변환 도구도 동일.
- 테스트 17개 통과.
