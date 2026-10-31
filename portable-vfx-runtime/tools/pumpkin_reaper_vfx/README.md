# 하베스트(호박 리퍼) 보스 VFX 소스 — 팩 16개 `PR_*`

완성 팩은 `portable-vfx-runtime/assets/PR_*/` (vfx.json + textures + NOTES.md). 이 폴더는 다시 만들 때 쓰는 원본.
**VFX 에는 빨간 예고가 없다** — 원형·직선·부채꼴 예고는 모드 `chatelegraph` 가 그림 (사장님 2026-10-31).
VFX 에 남은 빨간 표시는 모드로 못 그리는 것만: 나눠 맞기 아이콘(PR_CandyRain shareMark), 사슬 5초 카운트다운(PR_SoulChain bind), 등불 빛 경계선(PR_LanternLight), 저주 장판(PR_GhostDash curse).

| 파일 | 내용 |
|---|---|
| `lib.js` | 공통 스펙 라이브러리 (메시: 부채꼴·띠·링·룬띠·다각성·호, FX 블록, 마법진, 머티리얼). 각 팩 스펙 앞에 붙여서 export |
| `packs/<팩>.js` | 팩별 스펙 (systems + demo). 시스템 id 와 서버 연동은 `packs/_NOTES.md` |
| `gen_pumpkin.py` + `postsize.py` | 공용 텍스처 생성 → 예산 맞춰 줄이기 |
| `build.sh` / `shots.sh` | 묶기(make_bundle → client_compat fix/check → validate → preview → zip) / 캡처 시트 |

다시 만들기 (Anime VFX Toolkit 필요 — 사장님 아티팩트 "Anime VFX Toolkit" 의 toolkit_bundle.txt 를 `~/vfx/` 에 풀기):
```
python3 gen_pumpkin.py tex && python3 postsize.py tex
./build.sh HarvestSlash        # → fin/PR_HarvestSlash/ (+ .zip). 16개: HarvestSlash CrescentSpin BoomerangScythe PumpkinBomb SoulChain GroundHands GhostDash TrickOrTreat CandyRain ReaperLeap ReturnGuard Harvested LyingPumpkins ThreeBells LastLantern LanternLight
```
서버 코드(`magic-mythic-paper/.../ReaperBoss.java`, Skills yml)는 `claude:pr_<팩 소문자>/<시스템 소문자>` 로 부른다.
16개 모두 client_compat check OK, 팩당 RGBA 0.6~3.7 MiB (합 약 29 MiB).
