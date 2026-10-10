# 폰트

KoreanCNM light/medium/bold의 라이선스는 아직 확인되지 않았습니다. TTF/WOFF 파일을 공개 Git에 추가하거나 자동 다운로드하지 않습니다.

공개 기본 빌드는 세 subtitle font를 minecraft:default로 연결하므로 누락 TTF를 요구하지 않습니다.

사용 허가를 별도로 확인한 로컬 빌드에서는 아래 경로에 이미 보유한 파일을 배치할 수 있습니다. processResources가 실제로 존재하는 TTF만 공급자로 추가하고 기본 글꼴 폴백도 유지합니다.

- mod/src/main/resources/assets/chaca_story/font/cnm_l.ttf, cnm_m.ttf, cnm_b.ttf
- editor/fonts/cnm_l.woff, cnm_m.woff, cnm_b.woff (편집기 선택사항)

2026-10-10 확인: 기존 개인 Story JAR에 세 TTF가 있고 알려진 로컬 원본 경로도 존재했습니다. 존재 여부만 확인했으며 복사/공개하지 않았습니다. 이번 final-ready JAR에는 폰트가 없으며 기존 개인 JAR와 글꼴 모양이 달라질 수 있습니다.
