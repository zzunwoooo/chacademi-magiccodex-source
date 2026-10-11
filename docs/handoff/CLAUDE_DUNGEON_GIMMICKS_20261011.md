# Claude 인계: 던전 기믹 모델 (2026-10-11)

- 브랜치: `claude/dungeon-gimmick-models-20261011`
- 기준: `claude/magic-mythic-20261011` @ `d4c0859` (위 브랜치 포함. 인계 표 충돌을 피하려고 이어서 만들었다)
- 코드 변경 없음. `dungeon-gimmicks/` 폴더에 모델 자산만 추가했다. 빌드 영향 없음.

## 내용

- 기믹 모델 43종, 6속성 변형 포함 63개 `.bbmodel` (`dungeon-gimmicks/models/<id>/`)
- 목록 `index.json`, 히트박스 `hitboxes.json`, 3D 도감 `viewer.html`, 생성 스크립트 `tools/`
- 자세한 분류·애니메이션 규칙·배치 주의는 `dungeon-gimmicks/README.md`

## 주의사항

1. Blockbench에서 직접 열어 확인하지 못했다. 자체 렌더러와 도감 페이지로만 확인했다.
2. 아직 서버에 넣을 것은 없다. ModelEngine 등록과 MythicMobs 기믹 몹, 마법→기믹 신호는 다음 작업(`magic-mythic-paper` 확장)에서 한다.
3. 모델은 이로치 버전이 없다 (기믹이라 불필요). 대신 원소 비석·보물상자·원소 조각·조각 보관함은 속성별 텍스처 변형이 있다 (모양·애니메이션 동일).

## 확인할 것

- ModelEngine에 넣었을 때 `activate`(hold) → `active`(loop) 전환이 자연스러운지
- 크기 0으로 숨긴 부분(불꽃·포탈)이 ModelEngine에서도 안 보이는지
