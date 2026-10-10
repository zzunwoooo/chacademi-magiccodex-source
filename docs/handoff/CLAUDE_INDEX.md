# Claude 작업분 목록 (GPT/Codex 인계용)

Claude 는 GPT 작업 브랜치에 직접 커밋하지 않고, **`claude/` 로 시작하는 별도 브랜치**에만 올립니다.
각 브랜치에는 `docs/handoff/CLAUDE_<주제>_<날짜>.md` 인계 문서가 한 개씩 있습니다.

## GPT/Codex 가 받는 순서

사용자가 "깃에서 클로드 작업분 확인하고 주의사항 보고 다음 패치 진행해" 라고 하면:

1. `git fetch origin` 후 `git branch -r --list 'origin/claude/*'` 로 Claude 브랜치를 찾는다.
2. 아래 표에서 **상태가 `받기 전`** 인 브랜치마다 인계 문서를 읽는다 (`git show origin/<브랜치>:docs/handoff/<문서>`).
   문서의 "주의사항"과 "확인할 것"을 먼저 본다.
3. 지금 작업 브랜치에 병합한다 (`git merge --no-ff origin/<브랜치>`). 충돌이 나면 인계 문서의 "변경 범위" 표를 기준으로 Claude 쪽 의도를 살려 해결한다.
4. AGENTS.md 대로 호스팅에서 빌드·테스트한다.
5. 이 표의 상태를 `받음 (<커밋>, <날짜>)` 으로 바꿔 커밋한다. 받은 뒤의 Claude 브랜치는 지우지 않는다 (기록용).
6. 그다음 사용자의 다음 패치를 진행한다.

## 브랜치

| 브랜치 | 기준 | 인계 문서 | 내용 | 상태 |
| --- | --- | --- | --- | --- |
| `claude/first-nickname-20261010` | `codex/hires-item-icons-20261006` @ `9a6263a` | `CLAUDE_FIRST_NICKNAME_20261010.md` | `/최초닉네임설정` (닫을 수 없는 창, 재접속 시 다시 열기, 저장 후 `storydialogue {player} ch1-2`) | 받음 (1a42349, 2026-10-10; 호스팅 검증 완료, 배포 전) |
| `claude/chacademy-story-20261010` | `claude/first-nickname-20261010` (위 브랜치 포함) | `CLAUDE_CHACADEMY_STORY_20261010.md` | `chacademy-story/` 스토리 컷신·대화 모드+플러그인+편집기. 한글 닉네임, 내 일러스트, 호감도, 닫기 금지, 이어서 보기 | 받음 (3779d57, 2026-10-10; 검증·기존 한국어 글꼴 개인 배포 완료) |
| `claude/audit-fixes-20261010` | `codex/hires-item-icons-20261006` @ `310512b` | `CLAUDE_AUDIT_FIXES_20261010.md` | 스토리 대화창을 MagicCodex 기존 대화 UI로 통합 + 전체 코드 점검 수정 (상태 저장 추방, VFX 한도, 닉네임 중복 금지, 친구 신청제, 리롤 소모 규칙, 스토리 서버 검증 등). **빌드·실행 안 함 — 문서 0장·4장 먼저** | 받기 전 |

## Claude 가 지키는 규칙

- GPT 브랜치(`codex/*` 등)에 직접 push 하지 않는다.
- 이 저장소를 빌드·서버 설치하지 않는다 (AGENTS.md: 호스팅 우선). 빌드 여부는 인계 문서에 적는다.
- 새 브랜치를 만들 때마다 이 표에 한 줄 추가한다.

## Codex audit checkpoint

Audit integration build verified; deployment pending DB/graph blockers. See [CODEX_AUDIT_VERIFICATION_20261010.md](CODEX_AUDIT_VERIFICATION_20261010.md). The audit row stays pending until deployment prerequisites are resolved.
