from PIL import Image
import sys
out=sys.argv[1]
OL=(38,40,46,255); D=(86,90,99,255); M=(146,151,160,255); L=(196,200,209,255); H=(233,235,241,255)
def draw(rows):
    im=Image.new("RGBA",(16,16),(0,0,0,0)); pal={'o':OL,'d':D,'m':M,'l':L,'h':H}
    for y,r in enumerate(rows):
        for x,c in enumerate(r):
            if c in pal: im.putpixel((x,y),pal[c])
    return im
icons={
"helmet":["................","....oooooooo....","...ollhlllllo...","..ommhlmmmmmmo..","..ommhlmmmmmmo..","..ollllllllllo..","..ommooommmmmo..","..omo.hl.omomo..","..omo.hl.omomo..","..oo..hl..oooo..","......ll........","................","................","................","................","................"],
"chestplate":["................","..ooo......ooo..","..olho....ohlo..","..ommooooommmo..","..omdmmhlmmdmo..","..oooommmmoooo..","...omdmlhmdmo...","...omdmllmdmo...","...omdmmmmdmo...","...ollllllllo...","...omhhmmhhmo...","...oddddddddo...","...oo.oooo.oo...","................","................","................"],
"leggings":["................","...oooooooooo...","...ollllllllo...","...ommmmmmmmo...","...omddmmddmo...","...omd.oo.dmo...","...omd.oo.dmo...","...ommo..ommo...","...ommo..ommo...","...olho..olho...","...ommo..ommo...","...omdo..omdo...","...oooo..oooo...","................","................","................"],
"boots":["................","................","................","...ooo....ooo...","...omo....omo...","...omo....omo...","...omo....omo...","...odo....odo...","..oolmo..oolmo..","..olhmooooolhmo.","..omdddo.omdddo.","..oooooo.oooooo.","................","................","................","................"]}
for k,v in icons.items(): draw(v).save(f"{out}/dwarven_{k}.png")
