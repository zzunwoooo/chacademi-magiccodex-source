"""Source of truth for CHA_* MythicMobs skills and the matching MagicCodexBridge spell-runtime.yml routes.
Run: python3 tools/spells.py  (from magic-mythic-paper/) -> mythicmobs/Skills/chacademi_spells.yml, server-config/spell-runtime.yml
Damage numbers are placeholders until the spell balance review (owner decision: balance not final)."""
import os, glob, yaml
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE)
REPO = os.path.dirname(ROOT)

# (id, skill, target, range, base, ratio, utility, [mythic lines], note)
U = lambda i, sk=None, rng=24: (i, sk or 'CHA_' + i, 'SELF', rng, 0, 0, True, ['chanote{n=1} @self'], 'MagicCodexBridge SpellUtility가 실제 처리 (기존 유틸)')
S = [
 # ---------------- wind ----------------
 ('feather_fall','CHA_feather_step','SELF',12,0,0,False,['chaselfpotion{type=SLOW_FALLING;duration=100;level=0} @self'],'5초 천천히 낙하'),
 U('wind_basket'), U('harvest_wind'),
 ('wind_push','CHA_wind_push','ENTITY',10,0,0,False,['chahit{knock=1.6;up=0.35}'],'정면 몬스터 1체 약 4블록 밀기'),
 ('light_leap','CHA_light_leap','SELF',12,0,0,False,['chaselfvelocity{m=set;x=0;y=1.2;z=0} @self','delay 14','chaselfpotion{type=SLOW_FALLING;duration=40;level=0} @self'],'위로 약 7블록, 착지 피해 완화'),
 ('flight','CHA_flight','SELF',12,0,0,False,['chaworld{type=flight;ticks=200} @self'],'빗자루 비행 (ChacademyMotion). 빗자루 없으면 ticks 동안 연습용 빗자루, 모드 없으면 ticks 동안 바닐라 비행'),
 U('far_touch'),
 ('storm_command','CHA_storm_command','AIM',10,6,0.45,False,['delay 30','chazone{r=4;ticks=40;period=10;mult=0.5;suck=0.15}'],'영창 1.5초 후 반경 4블록 4회 타격'),
 ('vacuum_cleave','CHA_vacuum_cleave','AIM',24,4,0.35,False,['chabolt{speed=2.2;range=24;radius=0.6;pierce=99;breaks=CHAIN}'],'관통 칼날, 사슬 블록 파괴'),
 U('gale_dash'),
 ('returning_wind','CHA_returning_wind','SELF',12,0,0,False,['chaguard{type=reflect;ticks=10;r=4} @self'],'0.5초 투사체 반사'),
 U('wind_grooming'),
 ('weightless_zone','CHA_weightless_zone','AIM',15,0,0,False,['chazone{r=5;ticks=60;period=5;mult=0;suck=0.45}'],'반경 5블록 3초 끌어당김'),
 U('dust_duster'), U('leaf_cleanup'), U('feather_rustle'),
 ('cross_breeze','CHA_cross_breeze','AIM',30,7,0.55,False,['delay 40','chabolt{speed=2.5;range=30;radius=0.7;pierce=1;aoe=2;aoemult=0.5}'],'2초 충전 후 강한 화살'),
 ('returning_blade','CHA_returning_blade','AIM',18,4,0.3,False,['chabolt{speed=1.1;range=14;radius=1.2;pierce=99;returns=true}'],'갔다가 돌아오며 각 1회씩 타격'),
 ('pressure_press','CHA_pressure_press','AIR_ENTITY',15,0,0,False,['chahit{slam=2.2;status=gravity:40}'],'공중 대상을 내리꽂음 + 2초 중력 (낙하 피해는 바닐라)'),
 ('wind_decoy','CHA_wind_decoy','AIM',10,0,0,False,['chaworld{type=decoy;r=10;ticks=100}'],'반경 10블록 일반 몬스터 유인 5초'),
 ('air_step','CHA_air_step','SELF',12,0,0,False,['chaworld{type=platform;ticks=400} @self'],'발밑 3x3 임시 발판 20초 (보이는 건 VFX)'),
 ('lightning_needle','CHA_lightning_needle','AIM',20,6,0.5,False,['chahit{r=1.5;max=3}'],'지정 위치 낙뢰 피해'),
 ('air_stacking','CHA_air_stacking','AIM',10,0,0,False,['chaworld{type=line;max=6}'],'손에 든 블록 최대 6칸 일렬 설치'),
 ('arrested_orbit','CHA_arrested_orbit','AIM',25,3,0.2,False,
    ['chabolt{speed=1.8;range=25;radius=0.5;homing=true;spread=-8}','delay 4','chabolt{speed=1.8;range=25;radius=0.5;homing=true;spread=8}','delay 4',
     'chabolt{speed=1.8;range=25;radius=0.5;homing=true;spread=-4}','delay 4','chabolt{speed=1.8;range=25;radius=0.5;homing=true;spread=4}','delay 4',
     'chabolt{speed=1.8;range=25;radius=0.5;homing=true}'],'화살 5발 순차 발사'),
 ('crossfire','CHA_crossfire','ENTITY',15,8,0.6,False,['delay 10','chahit{execute=0.2}'],'일반 몬스터 처형, 플레이어·보스는 체력 20% 미만일 때만'),
 ('pressure_rupture','CHA_pressure_rupture','SELF',12,4,0.3,False,['chahit{center=caster;r=3;knock=1.2;up=0.3} @self'],'주변 3블록 밀치며 피해'),
 ('storm_crown','CHA_storm_crown','AIM',15,3,0.25,False,
    ['chabolt{speed=2;range=15;radius=0.5;homing=true;combo=storm_crown;combocount=3;combobuff=SPEED:60:2}','delay 6',
     'chabolt{speed=2;range=15;radius=0.5;homing=true;combo=storm_crown;combocount=3;combobuff=SPEED:60:2}','delay 6',
     'chabolt{speed=2;range=15;radius=0.5;homing=true;combo=storm_crown;combocount=3;combobuff=SPEED:60:2}'],'3발 모두 같은 대상 적중 시 3초 신속 III'),
 # ---------------- fire ----------------
 U('hearth_touch'),
 ('ember_bolt','CHA_ember_bolt','AIM',24,2,0.25,False,['chabolt{speed=1.6;range=24;radius=0.5;ignite=40}'],'약한 불씨 탄'),
 ('rekindle','CHA_rekindle','SELF',12,0,0,False,['chagimmick{type=ignite;range=12;mana=10} @self'],'원소 화로(기믹) 점화, 없으면 모닥불·초 (2026-10-20 Bridge 유틸에서 이동)'),
 ('ember_trap','CHA_ember_trap','AIM',15,6,0.4,False,['chazone{r=1.5;ticks=1200;trap=true;trapr=3;key=ember_trap;limit=2;ignite=60}'],'60초 덫, 최대 2개, 밟으면 폭발'),
 ('fire_curtain','CHA_fire_curtain','AIM',12,2,0.12,False,['chazone{wall=6;r=1.5;ticks=100;period=10;ignite=40}'],'폭 6블록 화염벽 5초'),
 ('phoenix_oath','CHA_phoenix_oath','SELF',12,0,0,False,['chaguard{type=lethal;ticks=6000;heal=6} @self'],'300초 동안 치명상 1회 무효'),
 U('living_furnace'),
 ('falling_star','CHA_falling_star','AIM',20,14,0.9,False,['delay 25','chahit{r=4;max=12;knock=0.8;up=0.4;ignite=60}'],'운석 낙하 반경 4블록'),
 ('flame_lash','CHA_flame_lash','ENTITY',8,6,0.5,False,['chahit{pull=1.4;ignite=40}'],'끌어당기며 피해'),
 ('cinder_detonation','CHA_cinder_detonation','BURNING_ENTITY',18,8,0.6,False,['chahit{r=3;max=6;ignite=60}'],'(기존 라우트 유지) 불타는 대상 폭발'),
 ('palm_fireworks','CHA_palm_fireworks','SELF',12,0,0,False,['chanote{n=1} @self'],'장식 마법, 게임 효과 없음'),
 ('ash_brand','CHA_ash_brand','ENTITY',15,5,0.4,False,['chamark{type=ash_brand;ticks=60;r=3;cooldown=200}'],'3초 뒤 폭발, 맞은 적에게 전이'),
 ('flame_leap','CHA_flame_leap','SELF',12,5,0.4,False,['chahit{center=caster;r=2.5;knock=0.8;ignite=40} @self'],'회전 발차기 근접 범위'),
 ('crimson_heart','CHA_crimson_heart','SELF',12,0,0,False,['chahit{center=caster;r=5;max=12;mult=0;hpcost=0.5;hpscale=2.5;knock=1} @self'],'현재 체력 절반 소모, 소모량 비례 폭발'),
 ('wick_trimming','CHA_wick_trimming','SELF',12,0,0,False,['chagimmick{type=extinguish;range=12;mana=50} @self'],'원소 화로(기믹) 끄기, 없으면 불 블록 끄기 (2026-10-20 Bridge 유틸에서 이동)'),
 ('charcoal_conversion','CHA_charcoal_conversion','ENTITY',20,0,0,False,['chahit{mult=0;ignite=200}'],'10초 발화'),
 U('sponge_drying'), U('boiling_armor'),
 ('kiln_breath','CHA_kiln_breath','SELF',12,0,0,False,['chaworld{type=kiln;r=6;ticks=400} @self'],'주변 화로 20초 가공 2배속'),
 ('explosive_wedge','CHA_explosive_wedge','ENTITY',15,7,0.5,False,['chamark{type=wedge;ticks=600;r=2.5}'],'근접 피격 시 폭발 (시간 경과로는 안 터짐)'),
 ('heat_recovery','CHA_heat_recovery','SELF',12,0,0,False,['chaworld{type=heat;r=10} @self'],'주변 불·용암 개수만큼 회복'),
 ('firebird_hunt','CHA_firebird_hunt','AIM',10,6,0.45,False,['chabolt{speed=1.2;range=12;radius=1.6;pierce=99;ignite=60}'],'넓게 관통하는 불새'),
 U('lava_suture'),
 ('flame_sword_dance','CHA_flame_sword_dance','SELF',12,4,0.35,False,['chamark{type=swords;count=3;ticks=600} @self'],'근접 공격마다 검 1자루 소모 추가 피해'),
 ('incandescent_break','CHA_incandescent_break','AIM',15,7,0.5,False,['chabolt{speed=4;range=15;radius=0.6;pierce=99;shield=100}'],'짧은 레이저, 방패 5초 무력화'),
 ('crimson_breath','CHA_crimson_breath','AIM',15,12,0.8,False,
    ['delay 30']+sum([['chahit{center=caster;cone=50;r=9;mult=0.4;ignite=40} @self','delay 6'] for _ in range(5)],[])[:-1],'1.5초 영창 후 부채꼴 브레스 5회'),
 # ---------------- water ----------------
 ('water_breath','CHA_water_breath','SELF',12,0,0,False,['chaselfpotion{type=WATER_BREATHING;duration=600;level=0} @self'],'30초 수중 호흡'),
 ('tide_stride','CHA_tide_stride','SELF',12,0,0,False,['chaselfpotion{type=DOLPHINS_GRACE;duration=300;level=0} @self'],'15초 수영 속도'),
 ('water_bolt','CHA_water_bolt','AIM',15,2,0.25,False,['chabolt{speed=1.6;range=15;radius=0.5;knock=0.45}'],'약 1블록 밀치는 물 탄환'),
 ('quench','CHA_quench','SELF',12,0,0,False,['chaally{type=quench} @self'],'자신의 불 끄기'),
 ('purifying_wave','CHA_purifying_wave','SELF',12,0,0,False,['chaally{type=cleanse;r=5} @self'],'자신·아군 독/나약함/구속 해제'),
 ('ice_bridge','CHA_ice_bridge','SELF',12,0,0,False,['chaworld{type=frostwalk;ticks=300;r=2} @self'],'15초 차가운 걸음'),
 ('spring_unseal','CHA_spring_unseal','AIM',20,0,0,False,['chaworld{type=water}'],'바라보는 위치에 물 설치'),
 ('water_elemental','CHA_water_elemental','SELF',12,3,0.25,False,['chaworld{type=companion;ticks=600;period=30;range=10;knock=0.3} @self'],'30초 동안 1.5초마다 근처 적 공격'),
 ('eternal_ice_prison','CHA_eternal_ice_prison','ENTITY',15,0,0,False,['chamark{type=prison;ticks=100}'],'일반 적 5초 봉쇄, 보스는 감속'),
 ('cauldron_fill','CHA_cauldron_fill','AIM',15,0,0,False,['chaworld{type=cauldron}'],'가마솥 물 한 단계'),
 ('dye_rinse','CHA_dye_rinse','SELF',12,0,0,False,['chaworld{type=dye} @self'],'손에 든 가죽 장비 염색 제거'),
 ('fish_lure','CHA_fish_lure','SELF',12,0,0,False,['chaworld{type=lure;min=20;max=60} @self'],'던진 낚싯바늘 입질 시간 단축'),
 ('frost_rupture','CHA_frost_rupture','AIM',15,6,0.45,False,['delay 20','chahit{r=2.5;max=8;up=0.5;effect=SLOWNESS:40:1}'],'1초 뒤 얼음 가시 범위'),
 ('pressure_lance','CHA_pressure_lance','AIM',15,6,0.45,False,['chabolt{speed=2.5;range=15;radius=0.6;pierce=3;falloff=0.7}'],'최대 3체 관통, 뒤일수록 약함'),
 ('life_distribution','CHA_life_distribution','SELF',6,0,0,False,['chaally{type=share;r=6;max=3} @self'],'자신+아군 최대 3명 체력 비율 분배'),
 ('moist_farmland','CHA_moist_farmland','AIM',15,0,0,False,['chaworld{type=wetfarm;r=2;max=9}'],'경작지 최대 9칸 적시기'),
 ('wash_letters','CHA_wash_letters','AIM',15,0,0,False,['chaworld{type=sign}'],'표지판 한 면 글씨 지우기'),
 ('healing_rain','CHA_healing_rain','AIM',15,0,0,False,['chazone{r=5;ticks=100;period=20;mult=0;heal=2}'],'5초 동안 아군 1초마다 하트 1칸 회복'),
 ('dew_gathering','CHA_dew_gathering','SELF',12,0,0,False,['chaworld{type=dew;max=3} @self'],'빈 병 최대 3개 물병으로'),
 ('quiet_mooring','CHA_quiet_mooring','SELF',12,0,0,False,['chaworld{type=boat} @self'],'물 위에서 보트 생성'),
 ('bubble_aria','CHA_bubble_aria','AIM',15,5,0.4,False,['chabolt{speed=1.3;range=15;radius=0.6;mult=0;aoe=4;aoemult=1}'],'명중 지점 반경 4블록 광역'),
 ('pressure_hammer','CHA_pressure_hammer','AIM',15,9,0.6,False,['delay 15','chahit{r=3;max=10;status=stun:20}'],'반경 3블록 강타 + 1초 기절'),
 ('waterspout','CHA_waterspout','AIM',15,6,0.45,False,['chazone{r=2.5;ticks=60;period=10;mult=0.35;lift=0.55}'],'3초 용오름, 띄우며 지속 피해'),
 ('current_sorting','CHA_current_sorting','SELF',12,0,0,False,['chaworld{type=sapgeum;min=1;max=3;item=GOLD_NUGGET} @self'],'물 속에서 사금(금 조각) 1~3개'),
 ('potion_mist','CHA_potion_mist','SELF',12,0,0,False,['chaally{type=potion;r=5;ticks=200} @self'],'손의 물약 소비, 반경 5블록 동료 10초 적용'),
 ('waterwheel_shield','CHA_waterwheel_shield','SELF',12,0,0,False,['chaguard{type=deflect;ticks=200;reduce=0.5;push=1.2} @self'],'다음 근접 피해 절반 + 공격자 밀침 (10초)'),
 ('concrete_curing','CHA_concrete_curing','AIM',15,0,0,False,['chaworld{type=concrete;max=16}'],'연결된 콘크리트 가루 최대 16개 굳히기'),
 ('ripple_guard','CHA_ripple_guard','SELF',12,0,0,False,['chaguard{type=block;ticks=40} @self'],'2초 정면 투사체 소멸'),
 ('tidal_wave','CHA_tidal_wave','AIM',12,6,0.45,False,['chabolt{speed=0.9;range=12;radius=2.2;pierce=99;knock=1.3;up=0.3;from=caster}'],'전방 해일, 3블록 밀침'),
 ('bouncing_droplet','CHA_bouncing_droplet','AIM',15,3,0.3,False,['chabolt{speed=1.4;range=15;radius=0.5;bounce=3;bouncer=6}'],'서로 다른 적 최대 4명 튕김'),
 ('abyssal_pressure','CHA_abyssal_pressure','AIM',15,5,0.35,False,['chazone{r=2.5;ticks=120;period=20;mult=0.3;effect=SLOWNESS:30:2;status=physical_weakness:30}'],'6초 수압 지대, 둔화·나약함·지속 피해'),
 # ---------------- earth ----------------
 ('verdant_touch','CHA_verdant_touch','SELF',12,0,0,False,['chaworld{type=grow;r=5;max=8} @self'],'주변 밀·당근·감자 최대 8개 한 단계 성장'),
 ('ore_whisper','CHA_ore_whisper','SELF',12,0,0,False,['chaworld{type=ore;r=10;max=3;ticks=100} @self'],'광석 최대 3곳 5초 발광 표시 (시전자만 보임)'),
 ('pebble_toss','CHA_pebble_toss','AIM',15,2,0.2,False,['chabolt{speed=1.6;range=15;radius=0.5}'],'약한 조약돌 탄'),
 ('miners_rhythm','CHA_miners_rhythm','SELF',12,0,0,False,['chaselfpotion{type=HASTE;duration=300;level=0} @self'],'15초 성급함'),
 # ---------------- 2026-10-14: 미구현 71종 (이전 7 + 101~167 신규 64) ----------------
 # 피해·범위 등 "미정" 표시된 값은 모두 임시값 (handoff 미정 목록 참고)
 ('flame_forging','CHA_flame_forging','SELF',12,0,0,False,['chautil{type=forge} @self'],'손에 든 재료를 단조표대로 변환 (재료표 미정: config flame-forging.recipes)'),
 ('blue_bonfire','CHA_blue_bonfire','SELF',12,0,0,False,['chazone{center=caster;r=10;ticks=1200;period=20;mult=0;manaregen=1.1} @self'],'60초 화톳불, 반경 10블록 마나 회복 +10% (같은 마법끼리 미중첩)'),
 # earth
 ('earth_shaping','CHA_earth_shaping','AIM',7,4,0.3,False,['chahit{center=caster;cone=60;r=7;up=0.45;knock=0.2;hitevent=external:hitBurst} @self'],'정면 7블록 부채꼴 충격파, 약하게 띄움 (피해·각도 임시)'),
 ('stone_wall','CHA_stone_wall','AIM',8,0,0,False,['chazone{wall=4;r=0.6;height=3;ticks=200;period=20;mult=0;solid=true}'],'10초 돌벽: 적 이동·투사체 차단, 실제 블록 없음 (크기 4x3 = VFX 기준, 미정)'),
 ('vine_ladder','CHA_vine_ladder','SELF',5,0,0,False,['chasummon{type=ladder;h=8;ticks=600;range=5} @self'],'바라보는 벽 최대 8블록 30초 사다리, 실제 덩굴 없음 (VFX 없음)'),
 ('earth_elemental','CHA_earth_elemental','SELF',12,5,0.4,False,['chasummon{type=golem;ticks=1200;range=8;reach=2.2;period=36;windup=12;hitr=1.5;knock=0.6} @self'],'60초 흙 골렘(가상) 동행·근접 공격 (공격력 임시)'),
 ('earthquake','CHA_earthquake','SELF',12,6,0.45,False,['chazone{center=caster;r=6;ticks=60;period=20;mult=0.5;effect=SLOWNESS:20:0} @self'],'주변 6블록 지면 흔들기 (지속 3초·피해 임시)'),
 ('climate_control','CHA_climate_control','SELF',12,0,0,False,['chafarm{type=weather} @self'],'맑음<->비 전환, 이후 자연 변화'),
 ('seed_row','CHA_seed_row','SELF',12,0,0,False,['chafarm{type=seedrow;max=6} @self'],'소지 씨앗으로 전방 6칸 농지 파종'),
 ('bee_calm','CHA_bee_calm','SELF',12,0,0,False,['chafarm{type=bees;r=8} @self'],'주변 화난 벌 진정 (반경 8 임시)'),
 ('thorn_armor','CHA_thorn_armor','SELF',12,2,0.15,False,['chaguard{type=thorns;ticks=160} @self'],'8초 근접 공격자 반격 피해 (피해 임시)'),
 ('vein_fracture','CHA_vein_fracture','AIM',6,0,0,False,['chautil{type=vein;max=8}'],'연결된 같은 광석 최대 8개 채굴, 도구 규칙·내구도, 맨손 드롭 없음'),
 ('earth_anchor','CHA_earth_anchor','SELF',12,0,0,False,['chaguard{type=anchor;ticks=200} @self'],'10초 공격 넉백 무시 (폭발 제외), 이동 가능'),
 ('weathering_touch','CHA_weathering_touch','AIM',6,0,0,False,['chautil{type=age}'],'석재를 이끼·금 간 변종으로'),
 ('iron_carapace','CHA_iron_carapace','SELF',12,0,0,False,['chaselfpotion{type=RESISTANCE;duration=400;level=0} @self'],'20초 저항 (레벨 미정: I 임시)'),
 ('moss_coating','CHA_moss_coating','SELF',12,0,0,False,['chautil{type=deepdark;depth=64} @self'],'발아래 Deep Dark 탐지, 결과는 VFX로만 (범위·마나·쿨 미정)'),
 ('grass_sprouting','CHA_grass_sprouting','AIM',8,0,0,False,['chafarm{type=grass;size=2}'],'조준한 2x2 흙만 잔디로'),
 ('flint_sorting','CHA_flint_sorting','AIM',15,2,0.2,False,['chabolt{speed=1.8;range=15;radius=0.4;count=3;countmax=5;interval=3}'],'돌 파편 3~5발 순차 발사 (사거리·피해 임시)'),
 ('bark_peeling','CHA_bark_peeling','AIM',6,0,0,False,['chafarm{type=strip;max=6}'],'연결된 원목 최대 6개 껍질 벗기기'),
 ('compost_return','CHA_compost_return','SELF',12,0,0,False,['chafarm{type=compost} @self'],'손의 식물 재료 -> 뼛가루 (바닐라 퇴비통 기대값 임시, 재료·비율 미정)'),
 ('thorn_seeds','CHA_thorn_seeds','AIM',15,5,0.35,False,[f'chabolt{{speed=2;range=15;radius=0.5;spread={a}}}' for a in (-20,-10,0,10,20)],'선인장 가시 5발 부채꼴 동시 발사 (사거리·피해 임시)'),
 ('pollen_matchmaking','CHA_pollen_matchmaking','AIM',10,0,0,False,['chafarm{type=breed;r=8}'],'번식 가능한 동물 한 쌍 번식 (먹이 불필요, 대기시간 준수)'),
 ('dormant_seed','CHA_dormant_seed','SELF',12,0,0,False,['chafarm{type=harvest;len=5} @self'],'전방 5칸 다 자란 작물만 수확, 재파종 없음'),
 ('counterweight','CHA_counterweight','SELF',12,0,0,False,['chaguard{type=counter;ticks=20;extra=40} @self'],'1초 안에 방패로 막으면 공격자 2초 행동 정지'),
 ('material_exchange','CHA_material_exchange','ENTITY',15,0,0,False,['chamark{type=root;ticks=40}'],'대상 이동만 2초 정지, 보스 면역'),
 ('remembering_field','CHA_remembering_field','SELF',12,0,0,False,['chafarm{type=ripen;len=5} @self'],'전방 5칸 작물 즉시 성숙'),
 ('little_giant','CHA_little_giant','SELF',12,0,0,False,['chabuff{type=giant;ticks=300;scale=0.5;reach=1;damage=0.3} @self'],'15초 몸집·근접 거리·근접 피해 증가 (증가량 임시)'),
 ('spring_ground','CHA_spring_ground','AIM',10,0,0,False,['chasummon{type=spring;uses=3;ticks=600;up=1.1;forward=0.5}'],'임시 도약판 (3회·30초·높이 임시)'),
 ('mountain_back','CHA_mountain_back','AIM',16,8,0.5,False,['delay 10','chabolt{speed=0.5;range=16;radius=1.4;pierce=99;rehit=8;knock=0.9;up=0.2;from=caster;mult=0.4;missevent=external:wallBreak}'],'굴러가는 바위가 밀며 여러 번 타격 (크기·사거리·피해 임시)'),
 ('earth_embrace','CHA_earth_embrace','SELF',12,0,0,False,['chafarm{type=revive;len=5} @self'],'전방 5칸, 완전히 마른 농지 위 작물 되살리기 (더위 건조 시스템 없음, 해석 확인 필요)'),
 ('forest_throne','CHA_forest_throne','SELF',12,6,0.4,False,['chabuff{type=root;ticks=160} @self','chazone{center=caster;r=6;ticks=160;period=10;max=2;pick=random;mult=0.35;knock=0.8;up=0.3} @self'],'8초 제자리, 주변 6블록 뿌리 순차 공격·넉백'),
 ('primordial_stone','CHA_primordial_stone','SELF',22,20,1.0,False,['chautil{type=core;ticks=6000;speed=0.7;range=22;radius=0.8;aoe=2.5;aoemult=0.5;mana=500} @self'],'돌핵 생성 -> 다시 사용 시 발사 (Bridge 쿨타임 API 없으면 자동 발사, 확률·피해 미정)'),
 # light
 ('guiding_star','CHA_guiding_star','SELF',12,0,0,False,['chautil{type=star;ticks=200} @self'],'웅크려 시전 = 위치 저장, 일반 시전 = 10초 방향 표시'),
 ('minor_heal','CHA_minor_heal','SELF',12,0,0,False,['chaally{type=heal;amount=2} @self'],'자신 체력 하트 1칸 회복'),
 ('light_mark','CHA_light_mark','ENTITY',20,2,0.25,False,['chabolt{speed=1.2;range=20;radius=0.5;homing=true}'],'조준한 적에게 유도 빛 탄 (피해·사거리 임시, VFX 없음)'),
 ('true_sight','CHA_true_sight','SELF',12,0,0,False,['chautil{type=truesight;r=16;ticks=300} @self'],'15초 근처 투명한 적 윤곽 + 숨은 단서 이벤트 (범위 임시)'),
 ('protective_ward','CHA_protective_ward','SELF',12,0,0,False,['chaguard{type=ward;ticks=200;r=2.5;per=2;reduce=10;extra=200} @self'],'10초 투사체 흡수 -> 보호막 (전환량·상한 임시)'),
 ('constellation_blessing','CHA_constellation_blessing','SELF',12,0,0,False,['chaally{type=buff;r=6;effect=SPEED:600:0,RESISTANCE:600:0} @self'],'자신+6블록 동료 30초 신속·저항 (레벨 임시)'),
 ('arcane_imprint','CHA_arcane_imprint','SELF',12,0,0,False,['chautil{type=mine;ticks=400} @self'],'곡괭이 20초 3x3 채굴'),
 ('dawn_ritual','CHA_dawn_ritual','SELF',12,0,0,False,['chaally{type=dawn;r=8;ticks=400} @self'],'8블록 해로운 효과 해제 + 20초 재적용 방지'),
 ('comforting_light','CHA_comforting_light','SELF',15,0,0,False,['chaally{type=comfort;range=15;amount=10;ticks=100} @self'],'바라보는 동료 임시 하트 5칸 5초, 미중첩'),
 ('exorcism_flash','CHA_exorcism_flash','SELF',12,8,0.5,False,['chahit{center=caster;r=8;max=16;undead=true} @self'],'주변 언데드만 범위 피해 (반경·피해 임시)'),
 ('arcane_mending','CHA_arcane_mending','SELF',12,0,0,False,['chautil{type=mend} @self'],'손에 든 일반 도구 완전 수리'),
 ('companion_return','CHA_companion_return','SELF',12,0,0,False,['chautil{type=return} @self'],'시전자 혼자 마을 귀환 지점으로 (config return-point)'),
 ('flawless_moment','CHA_flawless_moment','SELF',12,0,0,False,['chaguard{type=invuln;ticks=60} @self'],'3초 무적, 이동·공격 가능'),
 ('solar_judgment','CHA_solar_judgment','AIM',30,10,0.6,False,['chachannel{ticks=240;move=0.3;then=CHA_solar_judgment_fire}'],'12초 영창(이동·피격 시 실패) 후 3초 레이저 (피해·사거리 임시)'),
 ('firefly_mark','CHA_firefly_mark','SELF',12,0,0,False,['chasummon{type=light;ticks=1200;level=15} @self'],'60초 따라다니는 실제 빛'),
 ('sun_drying','CHA_sun_drying','SELF',12,0,0,False,['chafarm{type=craft;in=ROTTEN_FLESH:4;out=LEATHER:1} @self'],'썩은 살점 4 -> 가죽 1'),
 ('luminous_copy','CHA_luminous_copy','AIM',12,0,0,False,['chautil{type=text;range=12;ticks=100;wait=600}'],'채팅으로 입력한 글씨를 조준 위치에 5초 표시 (입력창은 클라이언트 제안)'),
 ('guiding_fletching','CHA_guiding_fletching','SELF',12,0,0,False,['chaguard{type=homing;ticks=100} @self'],'5초 동안 쏘는 화살이 조준한 적에게 유도, 벽 우회 없음'),
 ('sharing_table','CHA_sharing_table','SELF',12,0,0,False,['chaally{type=feed;r=8} @self'],'자신 제외 근처 아군 허기 전부 회복 (반경 임시)'),
 ('summons_letter','CHA_summons_letter','SELF',12,0,0,False,['chasummon{type=pets;ticks=600} @self'],'길들인 늑대·고양이 새로 소환, 30초 뒤 사망'),
 ('starlight_survey','CHA_starlight_survey','SELF',48,0,0,False,['chautil{type=survey;range=48} @self'],'바라보는 블록 좌표·거리 표시'),
 ('luminous_handover','CHA_luminous_handover','SELF',12,0,0,False,['chautil{type=taunt;r=10;ticks=100} @self'],'주변 몬스터 도발 (보스 포함, 범위·지속 임시)'),
 ('mana_conduit','CHA_mana_conduit','SELF',12,0,0,False,['chasummon{type=conduit;count=5;r=6;ticks=400;amount=50;ratio=0.5} @self'],'마나석 5개, 획득 시 마나 50+계수x마력 (계수·반경·유지 임시)'),
 ('merciful_arrow','CHA_merciful_arrow','AIM',25,4,0.3,False,['chabolt{speed=3;range=25;radius=0.6;ally=true;pierce=1;hitevent=external:heal}'],'매우 빠른 치유 화살, 맞은 아군 회복 (치유량 임시)'),
 ('mana_deposit','CHA_mana_deposit','SELF',12,0,0,False,['chautil{type=potion} @self'],'마나 포션(item:mana_potion) 1개 생성, 사용 시 마나 50'),
 ('guardian_oath','CHA_guardian_oath','SELF',10,0,0,False,['chaally{type=oath;range=10;r=10;ratio=0.3;ticks=200} @self'],'바라보는 동료 피해 일부 대신 받기 (거리·지속·비율 임시)'),
 ('experience_exchange','CHA_experience_exchange','SELF',12,0,0,False,['chautil{type=exp;ratio=0.5} @self'],'현재 마나 전부 -> 경험치 (비율 임시)'),
 ('starlight_reprise','CHA_starlight_reprise','SELF',12,0,0,False,['chautil{type=reprise} @self'],'다른 장착 마법 1개 쿨타임 초기화 (Bridge 쿨타임 API + 슬롯 보고 필요)'),
 ('constellation_emissary','CHA_constellation_emissary','SELF',12,0,0,False,['chasummon{type=emissary;ticks=600;r=8;amount=6;period=40} @self'],'30초 별 사역마: 체력 낮은 동료 하트 3칸, 2초 간격'),
 ('night_sight','CHA_night_sight','SELF',12,0,0,False,['chaselfpotion{type=NIGHT_VISION;duration=1200;level=0} @self'],'60초 야간 투시'),
 ('shadow_bind','CHA_shadow_bind','ENTITY',10,0,0,False,['chahit{mult=0;effect=SLOWNESS:60:1}'],'가까운 몬스터 3초 둔화 (사거리·둔화 임시)'),
 ('faint_veil','CHA_faint_veil','SELF',12,0,0,False,['chaselfpotion{type=INVISIBILITY;duration=160;level=0} @self'],'8초 투명 (갑옷·손 아이템은 보임)'),
]
# 한 마법이 쓰는 추가 스킬 (Bridge 라우트 없음)
# 300~323: mob skills learned by killing that mob (tools/mob_spells.py is their source of truth)
import sys as _sys; _sys.path.insert(0, HERE)
import mob_spells as _mob_spells
S += _mob_spells.routes()
# 168~214 최종 기획 신규 (tools/arcana_spells.py is their source of truth)
import arcana_spells as _arcana
S += _arcana.routes()
# 239~250 던전 기믹 마법 (tools/gimmick_spells.py is their source of truth)
import gimmick_spells as _gimmick
S += _gimmick.routes()

EXTRA = {
 'CHA_solar_judgment_fire': ('solar_judgment', ['chazone{r=4;ticks=60;period=5;mult=0.15}'], '태양의 심판 영창 완료 후 3초 레이저 (chachannel then=)'),
}
EXTRA.update(_arcana.EXTRA)
SKIP = {
 'wind_message':'Bridge가 직접 처리하는 귓속말 마법',
 'rainbow_collar':'교화(HolyTaming): TamingBridge가 별도 처리',
 'luminous_archive':'잠긴 상자 시스템 자체가 아직 없음 (실패 확률·내용물 처리·획득 조건 미정)',
}
# 슬롯 장착형 패시브: 미몹 라우트 없이 애드온 Passives 가 장착 상태(SpellSlots)로 처리. 시전 키는 VFX만 재생.
PASSIVE = {
 'trajectory_acceleration':'장착 중 내가 피해를 준 대상에게 유도 바람 화살 (발동당 마나 10)',
 'resonant_arrow':'장착 중 직접 공격 적중 시 추가 바람 화살 (발동당 마나 50), 미라지와 동시 발동',
 'glass_firing':'장착 중 광석 드롭 자동 재련 (광석당 마나 3)',
 'flame_footprints':'장착 중 매초 마나 10, 발자국이 잠시 뒤 폭발 (서버 mana-spells enabled:false 유지)',
 'glaze_coating':'장착 중 화염 지속 피해(FIRE_TICK) 면역',
 'automatic_supply':'장착 중 손의 건축 블록이 떨어지면 인벤토리에서 보충',
}

PASSIVE.update(_arcana.passive_notes())
SKIP.update(_arcana.SKIP)

def catalog():
    D = {}
    for f in glob.glob(os.path.join(REPO,'magic-codex-fabric/src/main/resources/assets/magiccodex/default-spells/*.yml')):
        d = yaml.safe_load(open(f, encoding='utf-8')); D[d['id']] = d
    return D

def main():
    D = catalog()
    B = yaml.safe_load(open(os.path.join(REPO,'portable-vfx-runtime/paper/src/main/resources/spell-bindings.yml'), encoding='utf-8'))['bindings']
    ids = [s[0] for s in S]
    assert len(ids) == len(set(ids)), 'duplicate id'
    missing = [k for k in B if k not in ids and k not in SKIP and k not in PASSIVE]
    assert not missing, missing
    out = ['# 차카데미 마법 미몹 스킬 (자동 생성: magic-mythic-paper/tools/spells.py) - 이펙트는 PortableVFX 담당, 여기는 게임 효과만',
           '# 피해량 <skill.var.damage> 는 MagicCodexBridge spell-runtime.yml 의 base-damage + power-ratio x 마력 으로 전달됩니다.',
           '# cha* 메커닉은 ChAcademiaMagicMythic 플러그인이 제공합니다.', '']
    rt = ['# 서버 마력 스냅샷: 피해 = base-damage + power-ratio * 마력. (자동 생성: magic-mythic-paper/tools/spells.py)',
          '# 피해 수치는 밸런스 검토 전 임시값입니다.', 'spells:']
    for (i, sk, tgt, rng, base, ratio, util, lines, note) in S:
        d = D.get(i, {}); name = d.get('name', i)
        cat = d.get('category', '')
        # every cha* mechanic learns the spell element (MagicImpactEvent -> dungeon gimmicks)
        lines = [l.replace('}', f';element={cat}}}', 1) if l.startswith('cha') and '{' in l and cat else l for l in lines]
        # every cha* mechanic learns its spell id (VFX external:* events go to that spell's running cast)
        lines = [l.replace('}', f';spell={i}}}', 1) if l.startswith('cha') and '{' in l else l for l in lines]
        assert sk.startswith('CHA_') and sk[4:].replace('_','').isalnum() and sk[4:] == sk[4:].lower(), sk
        assert tgt in ('SELF','AIM','ENTITY','AIR_ENTITY','BURNING_ENTITY','BLOCK') and 1 <= rng <= 48
        out.append(f'# {d.get("order", "")} {name} - {note}')
        out.append(f'{sk}:')
        out.append('  Cooldown: 0')
        out.append('  Skills:')
        out += [f'  - {l}' for l in lines]
        out.append('')
        rt += [f'  {i}:', f'    name: "{name}"', f'    skill: "{sk}"', f'    target: "{tgt}"', f'    range: {rng}',
               f'    utility: {"true" if util else "false"}', f'    base-damage: {base}', f'    power-ratio: {ratio}']
    for sk, (owner, lines, note) in EXTRA.items():
        cat = D.get(owner, {}).get('category', '')
        lines = [l.replace('}', f';element={cat};spell={owner}}}', 1) if l.startswith(('chahit{', 'chabolt{', 'chazone{')) else l for l in lines]
        out += [f'# (추가 스킬) {note}', f'{sk}:', '  Cooldown: 0', '  Skills:'] + [f'  - {l}' for l in lines] + ['']
    os.makedirs(os.path.join(ROOT,'mythicmobs/Skills'), exist_ok=True); os.makedirs(os.path.join(ROOT,'server-config'), exist_ok=True)
    open(os.path.join(ROOT,'mythicmobs/Skills/chacademi_spells.yml'),'w',encoding='utf-8').write('\n'.join(out))
    open(os.path.join(ROOT,'server-config/spell-runtime.yml'),'w',encoding='utf-8').write('\n'.join(rt)+'\n')
    # summary table for docs
    rows = ['| # | id | 이름 | 대상 | 처리 | 내용 |','|---|---|---|---|---|---|']
    order = {k: v.get('order', 0) for k, v in D.items()}
    allids = sorted(set(ids) | set(SKIP) | set(PASSIVE), key=lambda k: order.get(k, 999))
    spec = {s[0]: s for s in S}
    for k in allids:
        nm = D.get(k, {}).get('name', k)
        if k in SKIP: rows.append(f'| {order.get(k,"")} | {k} | {nm} | - | 보류 | {SKIP[k]} |')
        elif k in PASSIVE: rows.append(f'| {order.get(k,"")} | {k} | {nm} | 장착 | 애드온 패시브 | {PASSIVE[k]} |')
        else:
            s = spec[k]; how = 'Bridge 유틸' if s[6] else ('미몹+애드온' if any(l.startswith('cha') and not l.startswith('chanote') for l in s[7]) else '미몹 기본')
            rows.append(f'| {order.get(k,"")} | {k} | {nm} | {s[2]} | {how} | {s[8]} |')
    open(os.path.join(ROOT,'SPELLS.md'),'w',encoding='utf-8').write('# 마법별 미몹 구현표 (자동 생성)\n\n' + '\n'.join(rows) + '\n')
    print(len(S), 'routes,', len(PASSIVE), 'passives,', len(SKIP), 'skipped')
# ---- client aiming preview (MagicCodex client, display only; the server re-checks range/sight) ----
def _params(line):
    body = line[line.index('{')+1:line.index('}')]
    out = {}
    for kv in body.split(';'):
        if '=' in kv:
            k, v = kv.split('=', 1); out[k.strip()] = v.strip()
    return out

def aim_shape(spec):
    """(id, skill, target, range, ...) -> dict for assets/magiccodex/spell-targeting.json, or None (instant cast)."""
    i, sk, tgt, rng, base, ratio, util, lines, note = spec
    if tgt == 'SELF': return None
    if tgt in ('ENTITY', 'AIR_ENTITY', 'BURNING_ENTITY'):
        d = {'mode': 'unit', 'range': rng}
        if tgt == 'AIR_ENTITY': d['filter'] = 'air'
        if tgt == 'BURNING_ENTITY': d['filter'] = 'burning'
        first = next((l for l in lines if l.startswith(('chahit{', 'chamark{'))), None)
        if first and 'r' in _params(first): d['radius'] = float(_params(first)['r'])
        return d
    if tgt == 'BLOCK': return {'mode': 'point', 'range': rng, 'radius': 0.6}
    first = next((l for l in lines if l.startswith(('chahit{', 'chabolt{', 'chazone{', 'chaworld{', 'chafarm{', 'chautil{', 'chasummon{', 'chachannel{', 'chatelegraph{', 'chamobshot{'))), None)
    if first is None: return {'mode': 'point', 'range': rng, 'radius': 0.6}
    if first.startswith('chamobshot{'):
        q = _params(first)
        return {'mode': 'line', 'range': rng, 'length': float(q.get('range', rng)), 'width': round(max(0.2, 2 * float(q.get('radius', 0.7))), 2), 'origin': 'caster'}
    if first.startswith('chatelegraph{'):
        q = _params(first)
        if q.get('at') == 'target': return {'mode': 'area', 'range': rng, 'radius': float(q.get('r', 3))}
        if q.get('shape') == 'cone': return {'mode': 'cone', 'range': rng, 'length': float(q.get('r', 3)), 'angle': float(q.get('angle', 60))}
        if q.get('shape') == 'line': return {'mode': 'line', 'range': rng, 'length': float(q.get('length', 8)), 'width': float(q.get('width', 2)), 'origin': 'caster'}
        return None
    p = _params(first)
    if p.get('center') == 'caster':
        if 'cone' in p: return {'mode': 'cone', 'range': rng, 'length': float(p.get('r', rng)), 'angle': float(p['cone'])}
        return None
    if first.startswith('chabolt{'):
        return {'mode': 'line', 'range': rng, 'length': float(p.get('range', rng)), 'width': round(max(0.2, 2 * float(p.get('radius', 0.6))), 2), 'origin': p.get('from', 'eye')}
    if 'wall' in p:
        return {'mode': 'wall', 'range': rng, 'width': float(p['wall']), 'depth': round(2 * float(p.get('r', 0.5)), 2)}
    if 'r' in p:
        return {'mode': 'area', 'range': rng, 'radius': float(p['r'])}
    return {'mode': 'point', 'range': rng, 'radius': 0.6}

def write_targeting():
    import json
    out = {}
    for spec in S:
        d = aim_shape(spec)
        if d is not None: out[spec[0]] = d
    path = os.path.join(REPO, 'magic-codex-fabric/src/main/resources/assets/magiccodex/spell-targeting.json')
    doc = {'_comment': 'auto-generated by magic-mythic-paper/tools/spells.py (python3 tools/spells.py targeting). Client preview only; server spell-runtime.yml range is authoritative.',
           'version': 1, 'spells': out}
    open(path, 'w', encoding='utf-8').write(json.dumps(doc, ensure_ascii=False, indent=1, sort_keys=True) + '\n')
    print(len(out), 'aim shapes ->', path)

if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['targeting']: write_targeting()
    else: main(); write_targeting()
