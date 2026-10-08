from PIL import Image
import sys, os
OUT = sys.argv[1] if len(sys.argv) > 1 else "."
OL=(14,15,18,255); D=(38,40,46,255); M=(66,69,77,255); L=(122,127,138,255); H=(176,181,192,255)
TR=(214,212,204,255)
T=(0,0,0,0)

def rect(im,x,y,w,h,c):
    for i in range(w):
        for j in range(h): im.putpixel((x+i,y+j),c)
def frame(im,x,y,w,h,c):
    for i in range(w): im.putpixel((x+i,y),c); im.putpixel((x+i,y+h-1),c)
    for j in range(h): im.putpixel((x,y+j),c); im.putpixel((x+w-1,y+j),c)
def plate(im,x,y,w,h,base=M):
    rect(im,x,y,w,h,base); frame(im,x,y,w,h,OL)
    for i in range(1,w-1): im.putpixel((x+i,y+1),L)

l1=Image.new("RGBA",(64,32),T); l2=Image.new("RGBA",(64,32),T)

# ---- HELMET (front 8,8 / right 0,8 / left 16,8 / back 24,8 / top 8,0)
for hx in (0,16,24): plate(l1,hx,8,8,8,M)
plate(l1,8,8,8,8,M)
for hx in (0,8,16,24):
    rect(l1,hx,8,8,2,L); frame(l1,hx,8,8,2,OL)                # brow band
    for rx in (hx+1,hx+6): l1.putpixel((rx,9),H)             # rivets
# top with central crest ridge
plate(l1,8,0,8,8,M); rect(l1,11,0,2,8,L); rect(l1,11,0,1,8,H); rect(l1,16,0,8,8,D)
# front face: eye slits show skin, nasal guard, cheek guards, rust beard
rect(l1,9,11,2,2,T); rect(l1,13,11,2,2,T)
rect(l1,11,10,2,6,L); rect(l1,11,10,1,6,H); rect(l1,10,10,1,3,OL); rect(l1,13,10,1,3,OL)
rect(l1,8,13,1,3,L); rect(l1,15,13,1,3,L)                   # cheek guards
rect(l1,9,13,2,3,T); rect(l1,13,13,2,3,T)                   # open face



# side: ear flap and beard edge
for sx in (0,16):
    rect(l1,sx+1,11,5,4,D); frame(l1,sx+1,11,5,4,OL); rect(l1,sx+2,12,3,2,M)
    rect(l1,sx+(5 if sx==0 else 1),13,2,3,D)
# back: neck guard
rect(l1,24,13,8,3,D); frame(l1,24,13,8,3,OL)

# ---- CHESTPLATE (front 20,20 / right 16,20 / left 28,20 / back 32,20)
plate(l1,20,20,8,12,M)
rect(l1,20,20,8,1,L)
# engraved center plate
rect(l1,22,21,4,7,L); frame(l1,22,21,4,7,OL); rect(l1,23,22,2,5,M)
l1.putpixel((23,24),H); l1.putpixel((24,24),H); l1.putpixel((23,23),D); l1.putpixel((24,25),D)
rect(l1,20,22,2,6,D); rect(l1,26,22,2,6,D)                   # side plates
for y in (23,25): l1.putpixel((21,y),L); l1.putpixel((26,y),L)
# braided belt + buckle
rect(l1,20,28,8,2,D); frame(l1,20,28,8,2,OL)
for x in (20,22,24,26): l1.putpixel((x,28),L); l1.putpixel((x+1,29),L)
rect(l1,23,28,2,2,H); l1.putpixel((24,29),M)
# tassets
rect(l1,20,30,8,2,D); frame(l1,20,30,8,2,OL)
for x in (21,23,25): l1.putpixel((x,31),OL)
plate(l1,16,20,4,12,D); plate(l1,28,20,4,12,D); plate(l1,32,20,8,12,D)
rect(l1,20,16,8,4,D)

# ---- ARMS with large pauldron
for ax in (40,44,48,52):
    rect(l1,ax,20,4,12,D); frame(l1,ax,20,4,12,OL)
    rect(l1,ax,20,4,5,M); frame(l1,ax,20,4,5,OL); rect(l1,ax+1,21,2,1,H)   # pauldron
    rect(l1,ax,24,4,1,L)
    rect(l1,ax,28,4,3,M); frame(l1,ax,28,4,3,OL)                          # vambrace
    l1.putpixel((ax+1,29),L)
rect(l1,44,16,4,4,L); rect(l1,48,16,4,4,D)

# ---- BOOTS (layer1 legs rows 28-31)
for lx in (0,4,8,12):
    rect(l1,lx,28,4,4,D); frame(l1,lx,28,4,4,OL)
    rect(l1,lx,28,4,1,M)
    l1.putpixel((lx+1,30),TR); l1.putpixel((lx+2,31),TR); l1.putpixel((lx+2,30),TR) if lx%8==0 else None
rect(l1,4,16,4,4,D)

# ---- LEGGINGS (layer2): legs + belt strip on body
for bx,bw in [(20,8),(16,4),(28,4),(32,8)]:
    rect(l2,bx,29,bw,3,D); frame(l2,bx,29,bw,3,OL)
for lx in (0,4,8,12):
    rect(l2,lx,20,4,8,D); frame(l2,lx,20,4,8,OL)
    rect(l2,lx,20,4,1,M)
    rect(l2,lx,23,4,3,M); frame(l2,lx,23,4,3,OL)      # kneecap
    l2.putpixel((lx+1,24),L)
rect(l2,4,16,4,4,D)

os.makedirs(OUT,exist_ok=True)
l1.save(os.path.join(OUT,"dwarven_layer_1.png")); l2.save(os.path.join(OUT,"dwarven_layer_2.png"))

# ---- preview: front figure + helmet side on a skin base ----
SK=(190,140,108,255)
fig=Image.new("RGBA",(16,32),T)
for (x,y,w,h) in [(4,0,8,8),(4,8,8,12),(0,8,4,12),(12,8,4,12),(4,20,4,12),(8,20,4,12)]:
    rect(fig,x,y,w,h,SK)
def paste(src,box,pos,flip=False):
    c=src.crop(box)
    if flip: c=c.transpose(Image.FLIP_LEFT_RIGHT)
    fig.alpha_composite(c,pos)
paste(l1,(4,20,8,32),(4,20)); paste(l1,(4,20,8,32),(8,20))
paste(l2,(4,20,8,32),(4,20)); paste(l2,(4,20,8,32),(8,20))
paste(l1,(20,20,28,32),(4,8)); paste(l2,(20,20,28,32),(4,8))
paste(l1,(44,20,48,32),(0,8)); paste(l1,(44,20,48,32),(12,8),True)
paste(l1,(8,8,16,16),(4,0))
side=Image.new("RGBA",(8,8),SK); side.alpha_composite(l1.crop((16,8,24,16)))
S=16
big=fig.resize((16*S,32*S),Image.NEAREST); sb=side.resize((8*S,8*S),Image.NEAREST)
sheet=Image.new("RGBA",(16*S+40+8*S+40,32*S+40),(40,44,56,255))
sheet.alpha_composite(big,(20,20)); sheet.alpha_composite(sb,(16*S+60,20))
sheet.save(os.path.join(OUT,"preview.png"))



