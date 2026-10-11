# 화염 피해 귀속 확인
현재 SpellRuntime은 Mythic castSkill 변수 damage/magic_power/magic_haste를 전달한다. MagicCodexBridge의 시전 연결은 PortableVFX 결과가 있으면 먼저 사용한다. 조사한 PortableVFX/Bridge에는 실제 피해를 실행하는 .damage() 호출이나 spell ID가 연결된 피해 이벤트가 없다. SeasonalSpellMechanics의 자체 element 피해 경로는 있으나 현행299 전체 전투 경로로 사용된다는 근거가 없다.

공식 API: https://www.mythiccraft.io/javadocs/mythic/io/lumine/mythic/bukkit/events/MythicDamageEvent.html
DamageMetadata: https://www.mythiccraft.io/javadocs/mythic/io/lumine/mythic/api/skills/damage/DamageMetadata.html
공식 문서는5.4 스냅샷으로, 운영5.9의 완전 동일성을 주장하지 않는다. 이 API 존재만으로 카탈로그의 spell/element 귀속이 생기는 것은 아니다. 유료 JAR은 사용자 승인대로 컴파일 참조만 하고 추출/역공학하지 않았다.
