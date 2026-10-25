# proposal model
L=['대충','적당','한계']
I={'대충':26,'적당':100,'한계':122}
HP={'대충':25,'적당':39,'한계':45}
DR={'대충':15*1.25/100,'적당':(20+10)*1.25/100,'한계':0.45}
ART={'대충':1.0,'적당':1.08,'한계':1.12}
MANA={'대충':(350,9),'적당':(700,12),'한계':(900,15)}
EHP={k:HP[k]/(1-DR[k]) for k in L}
B=40
ref=(B+I['적당'])*ART['적당']
tgt={'초급':.065,'중급':.11,'고급':.22,'궁극(쉬움)':.45,'궁극(연계)':1.30}
r={t:tgt[t]*EHP['적당']/ref for t in tgt}
for t in r: print(t,'ratio',round(r[t],3),'base',round(B*r[t],2))
D=lambda t,a:r[t]*(B+I[a])*ART[a]
print('EHP',{k:round(v,1) for k,v in EHP.items()})
for t in tgt:
  print(t,{a:round(D(t,a),1) for a in L})
need=473+486+273
print('\n공격→방어: 초급 몇발 | 혼합 몇발 | 고급 1발 %HP | 연계궁극 %HP | 쉬운궁극 %HP | 예상시간(s)')
for a in L:
  M,R=MANA[a];f=min(1,(M+60*R)/need)
  for d in L:
    e=EHP[d]
    mix=(18.9*D('초급',a)+6.5*D('중급',a)+1.09*D('고급',a))/(18.9+6.5+1.09)
    dpm=.5*f*(18.9*D('초급',a)+6.5*D('중급',a)+1.09*D('고급',a))
    print(a,'→',d, round(e/D('초급',a),1), round(e/mix,1), f"{D('고급',a)/e*100:.0f}%", f"{D('궁극(연계)',a)/e*100:.0f}%", f"{D('궁극(쉬움)',a)/e*100:.0f}%", round(e/dpm*60), 'f',round(f,2))
