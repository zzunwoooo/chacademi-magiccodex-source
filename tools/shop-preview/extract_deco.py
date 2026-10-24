import cv2, numpy as np
from PIL import Image
ref=cv2.imread('ref/reference.png').astype(np.float32)  # BGR
H,W=ref.shape[:2]
B,G,R=ref[...,0],ref[...,1],ref[...,2]
gold=((R-B)>40)&(R>100)&(G>70)
def rect(m,x0,y0,x1,y1): m[y0:y1,x0:x1]=1
panel=np.zeros((H,W),np.uint8); front=np.zeros((H,W),np.uint8)
# panel layer (drawn before cards / NPC)
rect(panel,478,15,1660,27)       # top border line
rect(panel,1647,15,1653,800)     # right border (upper part)
rect(panel,486,15,492,330)       # left border line (the rest is under the illustration)
rect(panel,478,10,520,45)        # top-left corner ornament
rect(panel,503,48,564,104)       # title star
rect(panel,1600,8,1665,50)       # top-right corner ornament
rect(panel,900,74,1330,96)       # dotted ornament line under the title row
rect(panel,500,119,1640,128)     # header line
# front layer (drawn after the illustration and the footer fill)
rect(front,413,797,1645,802)     # footer top line
rect(front,1588,776,1642,824)    # footer top-right star
rect(front,1647,790,1653,925)    # right border (footer part)
rect(front,413,912,1645,918)     # footer bottom line
rect(front,1600,892,1672,941)    # bottom-right corner
rect(front,1626,880,1645,912)    # bottom-right inner corner lines
rect(front,414,795,420,920)      # footer left end
rect(front,412,795,424,805)      # its top corner
rect(front,412,908,424,920)      # its bottom corner
rect(front,8,0,38,40)            # top star of the thin left line
rect(front,19,40,26,300)         # thin left line (upper)
rect(front,19,740,26,905)        # thin left line (lower)
rect(front,0,900,48,941)         # bottom star of the thin left line
rect(front,48,914,370,924)       # bottom thin line
rect(front,40,840,104,910)       # star by the name
rect(front,80,890,272,899)       # underline
rect(front,232,872,262,902)      # small star on the underline
for name,m in [('panel',panel),('front',front)]:
    sel=(gold&(m>0)).astype(np.uint8)
    dil=cv2.dilate(sel,np.ones((5,5),np.uint8))
    bg=cv2.inpaint(ref.astype(np.uint8),dil*255,7,cv2.INPAINT_TELEA).astype(np.float32)
    gref=np.array([118,205,247],np.float32)   # BGR of the line core
    d=ref-bg; gd=gref-bg
    a=np.clip((d*gd).sum(2)/np.maximum((gd*gd).sum(2),1),0,1)
    a[dil==0]=0
    a[a<0.06]=0
    col=np.clip(bg+d/np.maximum(a,0.06)[...,None],0,255)
    out=np.zeros((H,W,4),np.uint8)
    out[...,0]=col[...,2];out[...,1]=col[...,1];out[...,2]=col[...,0];out[...,3]=(a*255).astype(np.uint8)
    out[a==0]=0
    if name=='front':
        # straight runs copied from one clean sample so the illustration's gold trims never leak into them
        def hrow(y0,y1,x0,x1,xs):
            out[y0:y1,x0:x1]=out[y0:y1,xs:xs+1]
        def vcol(x0,x1,y0,y1,ys):
            out[y0:y1,x0:x1]=out[ys:ys+1,x0:x1]
        hrow(797,802,424,1585,1000)
        hrow(912,918,424,1620,1000)
        vcol(414,420,806,908,850)
        out[780:797,424:1585]=0
        out[806:908,400:414]=0
        out[806:908,420:440]=0
    Image.fromarray(out,'RGBA').save(f'shop_deco_{name}.png',optimize=True)
    print(name,(a>0).sum())
