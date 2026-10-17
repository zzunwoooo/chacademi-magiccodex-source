import numpy as np, wave, subprocess, os, sys
SR=44100
rng=np.random.default_rng(7)
def t(d): return np.arange(int(SR*d))/SR
def env(n,a,r,sustain=1.0):
    e=np.ones(n)*sustain; ai=max(1,int(SR*a)); ri=max(1,int(SR*r))
    e[:ai]=np.linspace(0,sustain,ai); e[-ri:]*=np.linspace(1,0,ri); return e
def bp(x,lo,hi):
    X=np.fft.rfft(x); f=np.fft.rfftfreq(len(x),1/SR); X[(f<lo)|(f>hi)]=0; return np.fft.irfft(X,len(x))
def lp(x,hi): return bp(x,0,hi)
def norm(x,peak=0.85): m=np.max(np.abs(x)); return x if m==0 else x/m*peak
def save(name,x):
    x=norm(x); pcm=(x*32767).astype(np.int16)
    w=f'{name}.wav'
    with wave.open(w,'wb') as f: f.setnchannels(1);f.setsampwidth(2);f.setframerate(SR);f.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',w,'-c:a','libvorbis','-q:a','5',f'out/{name}.ogg'],check=True)
os.makedirs('out',exist_ok=True)

def whistle(d,f0=2950,rise=0.0):
    tt=t(d); n=len(tt)
    trill=35+5*np.sin(2*np.pi*0.7*tt)            # the pea rattling
    ph=np.cumsum(2*np.pi*(f0*(1+rise*tt/d)+90*np.sin(2*np.pi*np.cumsum(trill)/SR))/SR)
    am=0.72+0.28*np.sin(2*np.pi*np.cumsum(trill)/SR)
    tone=np.sin(ph)+0.25*np.sin(2*ph)+0.08*np.sin(3*ph)
    breath=bp(rng.standard_normal(n),2200,6000)*0.12
    return (tone*am+breath)*env(n,0.012,0.05)
def silence(d): return np.zeros(int(SR*d))

save('whistle_short', whistle(0.45))
save('whistle_long', whistle(1.25,rise=0.02))
save('whistle_end', np.concatenate([whistle(0.32),silence(0.12),whistle(0.32),silence(0.12),whistle(1.3,rise=0.02)]))

def saw(f,tt):
    x=np.zeros(len(tt))
    for k in range(1,14): x+=np.sin(2*np.pi*f*k*tt)/k
    return x
def horn(freqs,d):
    tt=t(d); x=np.zeros(len(tt))
    for f in freqs:
        vib=1+0.004*np.sin(2*np.pi*5.5*tt)
        x+=saw(f*vib,tt)
    # brassy filter opening
    x=lp(x,2400)
    return x*env(len(tt),0.06,0.25)
def crowd(d):
    tt=t(d); n=len(tt); x=np.zeros(n)
    for i in range(40):
        f=rng.uniform(350,1400); bw=rng.uniform(150,400)
        v=bp(rng.standard_normal(n),f-bw/2,f+bw/2)
        mod=0.5+0.5*np.sin(2*np.pi*rng.uniform(0.3,1.8)*tt+rng.uniform(0,6.28))
        x+=v*mod
    x+=bp(rng.standard_normal(n),1500,5000)*0.5
    swell=np.minimum(1,tt/0.35)*np.clip(1-(tt-1.6)/(d-1.6),0,1)**1.3
    return x*swell
d=2.8; a=crowd(d); hn=np.concatenate([horn([261.6,329.6,392.0],0.32),horn([349.2,440,523.3],0.28),horn([392,493.9,587.3,784],1.1)])
mix=norm(a,0.35); hn=norm(hn,0.9); mix[:len(hn)]+=hn
save('goal', mix)

def chime(f,d,decay=3.5):
    tt=t(d); return (np.sin(2*np.pi*f*tt)+0.4*np.sin(2*np.pi*2.76*f*tt)+0.2*np.sin(2*np.pi*5.4*f*tt))*np.exp(-decay*tt)
x=np.zeros(int(SR*1.6))
for i,f in enumerate([1318.5,1568,1975.5,2637,3136]):
    c=chime(f,1.2); s=int(SR*0.07*i); x[s:s+len(c)]+=c
x+=bp(rng.standard_normal(len(x)),3000,9000)*0.08*np.exp(-2*t(1.6))
# wing flutter
tt=t(1.6); x+=bp(rng.standard_normal(len(tt)),600,2500)*0.25*(0.5+0.5*np.sign(np.sin(2*np.pi*28*tt)))*np.exp(-1.5*tt)
save('snitch_appear', x)

tt=t(0.55); f=110*np.exp(-6*tt)+38
thump=np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-7*tt)
crack=lp(rng.standard_normal(len(tt)),3000)*np.exp(-40*tt)
save('bludger_hit', thump+0.6*crack)

tt=t(0.35); knock=(np.sin(2*np.pi*620*tt)+0.6*np.sin(2*np.pi*1250*tt))*np.exp(-22*tt)
snap=bp(rng.standard_normal(len(tt)),1200,8000)*np.exp(-60*tt)
save('deflect', knock+1.2*snap)

def whoosh(d,lo,hi):
    tt=t(d); n=len(tt); x=np.zeros(n); noise=rng.standard_normal(n)
    seg=2048
    out=np.zeros(n)
    for s in range(0,n,seg//2):
        frac=s/n; c=lo+(hi-lo)*np.sin(np.pi*frac)
        chunk=noise[s:s+seg]
        if len(chunk)<64: break
        w=np.hanning(len(chunk)); out[s:s+len(chunk)]+=bp(chunk,c*0.6,c*1.6)*w
    return out*np.sin(np.pi*tt/d)**1.5
save('throw', whoosh(0.4,500,2400))
tt=t(0.22); save('catch', (np.sin(2*np.pi*180*tt)*np.exp(-25*tt)+lp(rng.standard_normal(len(tt)),1800)*np.exp(-45*tt)*0.7))
