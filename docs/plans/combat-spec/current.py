L=['대충','적당','한계']
I={'대충':27,'적당':52,'한계':144}
HP={'대충':25,'적당':28,'한계':40}
ARM={'대충':(15,0,0),'적당':(20,8,12),'한계':(20,12,16)}  # armor,toughness,EPF
T={'초급':(3.3,.27),'중급':(5.0,.38),'고급':(7.4,.52),'궁극':(13.2,.87)}
def red(d,a,t,epf):
  ar=min(20,max(a/5,a-4*d/(t+8)))/25; d2=d*(1-ar); return d2*(1-min(20,epf)/25)
for t,(b,r) in T.items():
  print(t,{a:round(b+r*I[a],1) for a in L})
for a in L:
  for dd in L:
    row=[]
    for t,(b,r) in T.items():
      raw=b+r*I[a]; got=red(raw,*ARM[dd]); row.append(f"{t} {got:.1f}({HP[dd]/got:.1f}발)")
    print(a,'→',dd,' | '.join(row), f"감소율(초급) {1-red(T['초급'][0]+T['초급'][1]*I[a],*ARM[dd])/(T['초급'][0]+T['초급'][1]*I[a]):.0%}")
# no armor
for a in L: print(a,'맨몸 초급 발수 vs 20HP', round(20/(3.3+.27*I[a]),1))
