"""Build a seamless 256px caustic lookup texture; no runtime noise generation."""
import math, random
from pathlib import Path
from PIL import Image
rng=random.Random(2147)
size=256;cells=5
sites={(x,y):(x+.5+rng.uniform(-.37,.37),y+.5+rng.uniform(-.37,.37)) for y in range(cells) for x in range(cells)}
image=Image.new('RGB',(size,size));pixels=image.load()
for y in range(size):
 for x in range(size):
  # Smooth periodic domain warping rounds the otherwise straight Voronoi edges.
  u=x/size*cells+.20*math.sin(y/size*math.tau*2)+.09*math.cos(x/size*math.tau)
  v=y/size*cells+.19*math.sin(x/size*math.tau*2)+.08*math.cos(y/size*math.tau)
  ix=math.floor(u);iy=math.floor(v);dist=[]
  for oy in range(-1,2):
   for ox in range(-1,2):
    sx=ix+ox;sy=iy+oy;px,py=sites[sx%cells,sy%cells]
    px+=math.floor(sx/cells)*cells;py+=math.floor(sy/cells)*cells
    dist.append(math.hypot(u-px,v-py))
  dist.sort();edge=dist[1]-dist[0]
  value=round(255*(.82*math.exp(-(edge/.045)**2)+.18*math.exp(-(edge/.13)**2)))
  pixels[x,y]=(value,value,value)
path=Path(__file__).resolve().parents[1]/'assets/caustics-tile.png';image.save(path)
print(path)
