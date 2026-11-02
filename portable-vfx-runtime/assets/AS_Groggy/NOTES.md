# 재의 그림자 (1인 던전 보스) VFX 그룹 B — 변환·서버 연동 메모

모든 팩은 PortableVFX 클라이언트 호환 범위 안에서만 만들었다 (client_compat check OK). 빠진 기능·대체 없음. 쓰는 기능: mesh 파티클, bloom, stopTail(onStop), link path, flipbook.
**빨간 예고는 VFX 에 없다** — 모드 예고(chatelegraph / Telegraphs)를 쓴다. 서버 id = `claude:<팩 소문자>/<시스템 소문자>` (CONTRACT.md).
좌표: static +Z = 서버가 넘긴 yaw 정면, 원점 = 지정 위치 바닥. 보스 모델 키 약 7.2블록(머리 뒤 촛불 고리 높이 약 6.2, 가슴 등불 약 3.6).

| 팩 | 시스템 (role) | 서버 쪽 |
|---|---|---|
| AS_Desperate | scream (static, areaRadius 기준 10) | 발악: 0.5초 예고 뒤 보스 발밑에 재생, 두 번째는 yaw +22.5° 로 다시 |
| AS_Ward | shield (attached 2.2초, 반경 약 1.6 고정) | 빛의 수호: 장치에 follow 2.2초 |
| AS_Barrage | start (static r 4) · aura (attached 루프) · shard (projectile) · pop (static r 0.6) | 탄막 시작에 start + aura(끝에 stop). 탄마다 shard 를 2틱마다 이동, 맞거나 사라질 때 pop |
| AS_Groggy | fall (static r 4) · stun (attached 루프) | 그로기 시작에 fall + stun, 10초 뒤 stop |
| AS_Absorb | beam (link 1.0초, 장치→보스 가슴) · burst (static r 6) · enrage (attached 루프) | 변신: 장치마다 beam(1초 간격) → burst → 광폭화 동안 enrage (55초마다 다시 붙임) |
| AS_Device | light (attached 루프) · tether (link 루프) · break (static r 2.5) · respawn (static r 2) | 장치 켜짐: light + tether(보스 가슴까지). 파괴: light·tether stop + break. 재점화: respawn 후 light·tether |
| AS_Fate | intro · death (static r 6, 3초) · vanish · appear (static r 3) | 등장 / 처치 / 50% 귀환 (옛 자리 vanish → 중앙 appear) |

루프(aura·stun·enrage·light·tether)는 loop=true(주기 2초)로 만들어 길이에 상관없이 서버가 stop 할 때까지 이어진다 (계약서의 27·11·60초 길이 대신). stop 하면 오래 남는 층은 onStop 0.25초 꼬리로 바로 사라진다.
link 시스템(beam·tether)은 PR_SoulChain chain 과 같은 방식(path progress 0 → 1 = 장치 → 보스)이라 서버는 startCast(linkLength) + updateCast 로 끝점을 맞추면 된다.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
