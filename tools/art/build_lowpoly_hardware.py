"""Original low-poly silhouettes; Blender 5.2 background exporter, no third-party meshes.

blender --background --python tools/art/build_lowpoly_hardware.py
Engine coordinates: right-handed X right, Y up, +Z front; bottom at Y=0.
L3D2: LE uint32 magic/count; per part RGBA+shininess/specular/emissive+vertex count;
triangle vertices float32 x y z nx ny nz u v. Media cover material is LAST, UV 0..1.
"""
import bpy, math, struct, json, hashlib
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'lemuroid-app/src/main/assets/models3d'
SOURCE = ROOT / 'art-source/lowpoly-hardware'
ATLAS = ROOT / 'lemuroid-app/src/main/assets/textures/pixel_atlas_v2.png'
KEYS = 'nes snes n64 md segacd sms gb gbc gba nds 3ds psp gg atari2600 atari7800 pce lynx ngp ngc ws wsc dos psx arcade'.split()
bpy.ops.wm.read_factory_settings(use_empty=True)
materials = {}
current = None
image = bpy.data.images.load(str(ATLAS)) if ATLAS.exists() else None
if image:image.pack()

def mat(name, color, tile=3, emissive=0):
    key = (name, tuple(color), tile)
    if key in materials: return materials[key]
    m=bpy.data.materials.new(name);m.diffuse_color=(*color,1);m['tile']=tile;m['emissive']=emissive
    m.use_nodes=True;n=m.node_tree.nodes;bs=n.get('Principled BSDF');bs.inputs['Base Color'].default_value=(*color,1);bs.inputs['Roughness'].default_value=.72
    if image and name!='cover':
        t=n.new('ShaderNodeTexImage');t.image=image;t.interpolation='Closest'
        mix=n.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=1;mix.inputs[2].default_value=(*color,1)
        m.node_tree.links.new(t.outputs['Color'],mix.inputs[1]);m.node_tree.links.new(mix.outputs[0],bs.inputs['Base Color'])
    materials[key]=m;return m

BODY=mat('shell',(.72,.72,.72));DARK=mat('dark',(.11,.12,.14));ACCENT=mat('accent',(.45,.32,.62));SCREEN=mat('screen',(1,1,1),4,.15);COVER=mat('cover',(1,1,1),-1)
def recolor(color): return mat('shell',color)
def accent(color):return mat('accent',color)
def xyz(p):return (p[0],-p[2],p[1])
def adopt(o,m):
    for c in list(o.users_collection):c.objects.unlink(o)
    current.objects.link(o);o.data.materials.append(m);return o
def box(name,p,size,m=BODY,bevel=0):
    bpy.ops.mesh.primitive_cube_add(size=1, location=xyz(p));o=bpy.context.object;o.name=name;o.dimensions=xyz((size[0],size[1],-size[2]))
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    if bevel:
        mod=o.modifiers.new('Single segment bevel','BEVEL');mod.width=bevel;mod.segments=1
        bpy.context.view_layer.objects.active=o;bpy.ops.object.modifier_apply(modifier=mod.name)
    return adopt(o,m)
def cyl(name,p,r,depth,m=DARK,axis='y',verts=12):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=depth, location=xyz(p));o=bpy.context.object;o.name=name
    if axis=='z':o.rotation_euler[0]=math.pi/2
    elif axis=='x':o.rotation_euler[1]=math.pi/2
    bpy.ops.object.transform_apply(location=False,rotation=True,scale=True);return adopt(o,m)
def pad(x,y,z,s=.09):
    box('D-pad horizontal',(x,y,z),(s*2.5,s*.8,.025),DARK)
    box('D-pad vertical',(x,y,z+.002),(s*.8,s*2.5,.029),DARK)
def face_buttons(x,y,z,count=2,spacing=.13,m=ACCENT):
    for i in range(count):cyl('Action button',(x+(i-(count-1)/2)*spacing,y+(i%2)*.06,z),.044,.025,m,'z',8)
def handheld(key,color,width=.85,height=.56,screenw=.42,screenh=.30,vertical=False):
    b=recolor(color);box('Rounded handheld shell',(0,height/2,.0),(width,height,.15),b,.045)
    sy=height*.61 if vertical else height*.55
    box('Screen bezel',(0,sy,.083),(screenw+.08,screenh+.075,.025),DARK,.012)
    box('LCD',(0,sy,.100),(screenw,screenh,.012),SCREEN)
    if vertical:
        pad(-width*.26,height*.20,.095,.052);face_buttons(width*.22,height*.18,.095,2,.09)
        box('Select Start',(0,height*.08,.095),(.16,.018,.016),DARK)
    else:
        pad(-width*.38,height*.43,.095,.050);face_buttons(width*.35,height*.37,.095,2,.08)
        box('Select Start',(0,height*.12,.095),(.15,.018,.016),DARK)
    return b
def clamshell(key,color):
    b=recolor(color);wide=1.02 if key=='3ds' else .95
    box('Lower clamshell',(0,.085,.10),(wide,.17,.62),b,.035)
    box('Lower touch screen',(0,.181,.11),(.40,.012,.28),SCREEN)
    cyl('Hinge',(0,.18,-.20),.055,wide*.85,DARK,'x',12)
    # Upright, visibly tilted upper lid; shape differs between DS and 3DS.
    lid=box('Upper lid',(0,.52,-.20),(wide,.62,.075),b,.025);lid.rotation_euler.x=math.radians(-12)
    box('Upper bezel',(0,.54,-.137),(.78 if key=='3ds' else .65,.40,.018),DARK)
    box('Upper screen',(0,.54,-.121),(.65 if key=='3ds' else .54,.31,.014),SCREEN)
    # Controls on the top of the lower half.
    box('Control cross',(wide*-.37,.183,.10),(.13,.012,.045),DARK);box('Control cross 2',(wide*-.37,.185,.10),(.045,.014,.13),DARK)
    for x,z in [(.35,.06),(.40,.13),(.30,.13),(.35,.20)]:cyl('Action',(x,.186,z),.026,.016,DARK,'y',8)

def console(key):
    global current
    current=bpy.data.collections.new(key);scene=bpy.data.scenes.new(key);scene.collection.children.link(current)
    bpy.context.window.scene=scene
    if key in ('gb','gbc'):
        handheld(key,(.72,.73,.61) if key=='gb' else (.30,.27,.63),.55,.88,.34,.28,True)
        for i in range(4):box('Speaker slit',(.12+i*.025,.07,.091),(.009,.06,.006),DARK)
    elif key=='gba':handheld(key,(.32,.30,.65),1.02,.56,.50,.32)
    elif key=='psp':
        handheld(key,(.16,.17,.19),1.23,.52,.72,.39);cyl('Analog nub',(-.48,.10,.096),.04,.018,DARK,'z',10)
    elif key=='gg':handheld(key,(.16,.18,.21),1.02,.65,.48,.35)
    elif key=='lynx':handheld(key,(.26,.27,.28),1.17,.57,.55,.32)
    elif key in ('ngp','ngc'):
        handheld(key,(.60,.61,.57) if key=='ngp' else (.24,.43,.62),.91,.57,.42,.32)
        cyl('Click stick',(-.34,.24,.117),.075,.026,DARK,'z',10)
    elif key in ('ws','wsc'):
        b=handheld(key,(.64,.65,.60) if key=='ws' else (.31,.42,.55),.91,.55,.43,.29)
        # Second directional cluster identifies WonderSwan and vertical play.
        pad(-.34,.43,.115,.027)
    elif key in ('nds','3ds'):clamshell(key,(.78,.80,.81) if key=='nds' else (.24,.29,.43))
    elif key=='nes':
        b=recolor((.70,.70,.67));box('NES rectangular body',(0,.16,0),(1.28,.32,.92),b,.025)
        box('Cartridge door',(0,.30,.14),(1.18,.065,.48),b)
        box('Front black band',(0,.10,.475),(1.17,.09,.025),DARK)
        box('Dark lid stripe',(.38,.344,-.13),(.18,.015,.57),DARK)
        for x in [-.43,-.26]:box('Power reset',(x,.16,.49),(.10,.045,.025),DARK)
    elif key=='snes':
        b=recolor((.71,.72,.70));box('SNES stepped base',(0,.11,0),(1.26,.22,.85),b,.045)
        box('Raised cartridge deck',(0,.245,-.05),(.94,.17,.62),b,.035)
        box('Cartridge slot',(0,.338,-.12),(.67,.012,.075),DARK)
        for x in [-.40,.40]:box('Sliding control',(x,.234,.22),(.16,.04,.18),ACCENT,.012)
        box('Front controller sockets',(0,.10,.435),(.66,.07,.018),DARK)
    elif key in ('md','segacd'):
        b=recolor((.13,.14,.16));box('Sculpted main case',(-.05,.13,0),(1.26,.26,.90),b,.08)
        cyl('Round cartridge deck',(-.02,.27,-.04),.38,.065,DARK,'y',20)
        box('Cartridge slot',(-.02,.310,-.07),(.58,.012,.07),DARK)
        box('Controller ports',(-.12,.12,.46),(.67,.065,.025),DARK)
        box('Red power',(.40,.28,.15),(.12,.025,.06),accent((.65,.10,.13)))
        if key=='segacd':
            box('Sega CD extension',(.18,.055,.02),(1.62,.11,1.05),b,.035)
            box('CD tray',(.66,.12,.07),(.35,.06,.71),DARK,.04)
    elif key=='sms':
        b=recolor((.12,.14,.17));box('Sloped wedge case',(0,.13,0),(1.31,.26,.76),b,.035)
        box('Red inset front',(0,.12,.39),(1.15,.10,.025),accent((.67,.12,.14)))
        box('Cartridge slot',(0,.274,-.11),(.64,.012,.075),DARK)
        box('Card slot',(.25,.075,.414),(.35,.018,.01),DARK)
    elif key=='n64':
        b=recolor((.22,.23,.25));box('Curved wide base',(0,.12,0),(1.26,.24,.93),b,.08)
        box('Raised rear arch',(0,.265,-.12),(.78,.20,.61),b,.05)
        box('Cartridge slot',(0,.375,-.15),(.63,.012,.07),DARK)
        for x in [-.45,-.15,.15,.45]:cyl('Four controller ports',(x,.13,.477),.055,.025,DARK,'z',10)
        box('Expansion cover',(0,.258,.24),(.48,.018,.25),DARK)
    elif key=='psx':
        b=recolor((.66,.67,.65));box('PlayStation case',(0,.12,0),(1.25,.24,.91),b,.04)
        cyl('Circular disc lid',(0,.257,-.04),.36,.032,b,'y',24)
        for x in [-.49,.49]:cyl('Round controls',(x,.255,.21),.075,.025,DARK,'y',12)
        for x in [-.26,.26]:box('Controller socket',(x,.085,.47),(.28,.075,.025),DARK)
    elif key in ('atari2600','atari7800'):
        b=recolor((.14,.15,.16));box('Atari trapezoid body',(0,.12,0),(1.35,.24,.83),b,.045)
        wood=accent((.58,.33,.17)) if key=='atari2600' else accent((.70,.71,.72))
        box('Front panel',(0,.115,.43),(1.25,.15,.025),wood)
        box('Cartridge slot',(0,.259,-.10),(.44,.015,.075),DARK)
        for x in [-.48,-.30,.30,.48]:box('Switch',(x,.28,-.12),(.035,.075,.045),BODY)
        for i in range(5):box('Top ribs',(0,.251,.04+i*.035),(1.15,.012,.012),DARK)
    elif key=='pce':
        b=recolor((.85,.83,.76));box('Tiny square console',(0,.105,0),(.77,.21,.72),b,.03)
        box('HuCard slot',(0,.095,.373),(.56,.025,.015),DARK)
        box('HuCard deck',(0,.222,.05),(.56,.018,.43),b)
        box('Power switch',(-.26,.23,.19),(.10,.03,.08),accent((.62,.12,.16)))
    elif key=='dos':
        b=recolor((.73,.70,.59));box('Desktop PC',(0,.11,0),(1.10,.22,.80),b,.025)
        box('Floppy drives',(.27,.13,.41),(.40,.08,.02),DARK)
        box('CRT deep rear',(0,.57,-.10),(.72,.68,.60),b,.045)
        box('CRT bezel',(0,.58,.22),(.74,.58,.05),DARK,.035)
        box('CRT display',(0,.58,.253),(.62,.43,.012),SCREEN)
        box('Keyboard',(0,.035,.68),(1.04,.07,.30),b,.015)
        for i in range(3):box('Key rows',(0,.078,.59+i*.065),(.85,.018,.035),DARK)
    elif key=='arcade':
        b=recolor((.17,.19,.24));box('Cabinet plinth',(0,.35,0),(.84,.70,.66),b,.035)
        box('Upper cabinet',(0,1.15,-.12),(.84,.98,.50),b,.035)
        box('Marquee',(0,1.64,.18),(.79,.20,.045),mat('accent',(1,.70,.30),7,.2))
        box('Screen bezel',(0,1.16,.17),(.75,.67,.04),DARK)
        box('Arcade screen',(0,1.16,.196),(.62,.49,.012),SCREEN)
        box('Control deck',(0,.78,.29),(.92,.10,.45),b,.02)
        cyl('Joystick shaft',(-.22,.89,.31),.024,.15,DARK,'y',8)
        cyl('Joystick ball',(-.22,.98,.31),.065,.075,ACCENT,'y',8)
        for x in [.07,.23,.36]:cyl('Button',(x,.845,.32),.037,.025,ACCENT,'y',8)
        box('Coin door',(0,.34,.345),(.30,.30,.025),DARK)
    return scene

def uv_for(v,n,m,bounds):
    axes=sorted(range(3), key=lambda i:abs(n[i]))[:2]
    # Front-facing screens/labels always map horizontal X and vertical Y.
    if abs(n[2])>.5: axes=[0,1]
    lo,hi=bounds;u=(v[axes[0]]-lo[axes[0]])/max(.001,hi[axes[0]]-lo[axes[0]]);vv=(v[axes[1]]-lo[axes[1]])/max(.001,hi[axes[1]]-lo[axes[1]])
    if abs(n[2])>.5:vv=1-vv # Android bitmap V=0 is the top of the label/screen.
    if m.name.startswith('cover'):return (.04+.92*u,.04+.92*vv)
    tile=int(m['tile']);return ((tile%4+.03+.94*u)/4,(tile//4+.03+.94*vv)/2)

def export(scene,key,media=False):
    groups={}; allverts=[]
    for o in scene.objects:
        if o.type!='MESH':continue
        mesh=o.data;mesh.calc_loop_triangles();m=mesh.materials[0]
        eng=[(p.co.x,p.co.z,-p.co.y) for p in mesh.vertices]
        lo=[min(p[i] for p in eng) for i in range(3)];hi=[max(p[i] for p in eng) for i in range(3)]
        for old in list(mesh.uv_layers):mesh.uv_layers.remove(old)
        layer=mesh.uv_layers.new(name='Atlas UV');layer.active_render=True
        for polygon in mesh.polygons:
            nn=polygon.normal;n=(nn.x,nn.z,-nn.y)
            for loop in polygon.loop_indices:
                u,v=uv_for(eng[mesh.loops[loop].vertex_index],n,m,(lo,hi))
                layer.data[loop].uv=(u,1-v) # Blender images are bottom-up; Android atlas is top-down.
        for tri in mesh.loop_triangles:
            nb=o.matrix_world.to_3x3()@tri.normal;n=(nb.x,nb.z,-nb.y)
            for idx in tri.vertices:
                p=o.matrix_world@mesh.vertices[idx].co;v=(p.x,p.z,-p.y)
                local=eng[idx];uv=uv_for(local,n,m,(lo,hi));groups.setdefault(m,[]).append((*v,*n,*uv));allverts.append(v)
    ordered=sorted(groups,key=lambda m:(m.name.startswith('cover'),m.name))
    data=bytearray(struct.pack('<II',0x3244334c,len(ordered)))
    for m in ordered:
        vs=groups[m];data+=struct.pack('<7fI',*m.diffuse_color,28,.18,float(m['emissive']),len(vs))
        for v in vs:data+=struct.pack('<8f',*v)
    tris=len(allverts)//3;limit=1200 if key=='arcade' else 600
    assert tris<=limit,(key,tris,limit)
    path=OUT/(key+'.l3d');path.write_bytes(data)
    return dict(key=key,triangles=tris,parts=len(ordered),bytes=len(data),sha256=hashlib.sha256(data).hexdigest(),bounds=[[min(v[i] for v in allverts) for i in range(3)],[max(v[i] for v in allverts) for i in range(3)]],cover_part=len(ordered)-1 if media else None)

def media(key):
    global current
    if key=='arcade':
        scene=console('arcade')
        for o in scene.objects:
            if o.type=='MESH':
                if o.data.materials[0]==SCREEN:o.data.materials[0]=COVER
                o.location.z-=.84
        return scene
    current=bpy.data.collections.new('media_'+key);scene=bpy.data.scenes.new('media_'+key);scene.collection.children.link(current);bpy.context.window.scene=scene
    b=recolor((.48,.49,.52));w,h,d=.90,1.15,.14
    if key in ('nds','3ds'):w,h,d=.62,.68,.065;b=recolor((.14,.15,.17) if key=='nds' else (.78,.79,.78))
    elif key=='gba':w,h,d=.98,.65,.13;b=recolor((.21,.22,.28))
    elif key in ('gb','gbc'):w,h,d=.78,.90,.15;b=recolor((.67,.68,.62))
    elif key=='nes':w,h,d=.90,1.45,.16
    elif key=='snes':w,h,d=1.15,.85,.18;b=recolor((.64,.65,.67))
    elif key=='n64':w,h,d=1.12,.80,.19;b=recolor((.28,.29,.32))
    elif key=='pce':w,h,d=.80,1.18,.035;b=recolor((.86,.85,.78))
    elif key=='dos':w,h,d=1.04,1.08,.10;b=recolor((.17,.18,.22))
    elif key in ('psx','segacd'):
        cyl('Optical disc',(0,0,0),.70,.035,recolor((.73,.77,.80)),'z',32)
        cyl('Disc label',(0,0,.026),.54,.008,COVER,'z',32)
        cyl('Centre hole',(0,0,.038),.095,.012,DARK,'z',16);return scene
    elif key=='psp':
        cyl('UMD round shell',(0,0,0),.60,.10,recolor((.83,.84,.81)),'z',24)
        box('UMD label',(0,.05,.058),(.82,.55,.012),COVER)
        cyl('UMD exposed disc',(0,-.22,.064),.20,.012,DARK,'z',16);return scene
    elif key in ('ws','wsc','ngp','ngc','lynx'):w,h,d=.80,.80,.10
    elif key in ('md','sms','gg','atari2600','atari7800'):w,h,d=1.0,.90,.17;b=recolor((.15,.16,.19))
    box('Media shell',(0,0,0),(w,h,d),b,.025 if d>.06 else 0)
    if key=='n64':box('Raised curved top',(0,h*.39,0),(w*.75,.18,d),b,.04)
    if key=='snes':
        for x in [-.47,.47]:box('Side grip',(x,0,.095),(.06,h*.70,.025),DARK)
    if key in ('nds','3ds','pce'):box('Gold contacts',(0,-h*.40,d*.55),(w*.65,h*.13,.012),accent((.69,.53,.22)))
    if key=='3ds':box('Card tab',(w*.45,h*.40,0),(.14,.12,d),b)
    if key=='dos':
        box('Metal floppy shutter',(0,h*.32,d*.55),(.55,.35,.018),BODY)
        box('Floppy label',(0,-h*.14,d*.57),(w*.73,h*.45,.012),COVER)
    else:box('Game label',(0,h*.06,d*.57),(w*.72,h*.62,.012),COVER)
    return scene

# FAITHFUL_CLI_REDIRECT
# Historical entry point now builds the current reference-led assets.
if __name__ == '__main__':
    import runpy
    runpy.run_path(str(ROOT / 'tools/art/build_faithful_hardware.py'), run_name='__main__')
    raise SystemExit(0)

OUT.mkdir(parents=True,exist_ok=True);SOURCE.mkdir(parents=True,exist_ok=True)
rows=[];scenes=[]
for key in KEYS:
    scene=console(key);rows.append(export(scene,key));scenes.append(scene)
media_scenes=[]
for key in KEYS:
    scene=media(key);rows.append(export(scene,'media_'+key,True))
    media_scenes.append(scene)

# One editable presentation scene with shared geometry, plus individual asset scenes.
sheet=bpy.data.scenes.new('Contact sheet');bpy.context.window.scene=sheet
for i,scene in enumerate(scenes):
    gx=(i%6)*2.1;gy=(i//6)*2.2
    for obj in scene.objects:
        if obj.type!='MESH':continue
        cp=obj.copy();cp.data=obj.data;sheet.collection.objects.link(cp);cp.location+=Vector((gx,-gy,0))
    curve=bpy.data.curves.new('Label','FONT');curve.body=scene.name.upper();curve.size=.16;obj=bpy.data.objects.new(scene.name+'_label',curve);sheet.collection.objects.link(obj);obj.location=(gx-.58,-gy-.76,.02)
    curve.materials.append(mat('caption',(.83,.85,.89)))
bpy.ops.object.camera_add(location=(11,-15,16));camera=bpy.context.object;camera.rotation_euler=(Vector((5.2,-3.4,.1))-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.type='ORTHO';camera.data.ortho_scale=17.2;sheet.camera=camera
sheet.render.engine='CYCLES';sheet.cycles.samples=8;sheet.cycles.use_denoising=True
world=bpy.data.worlds.new('Studio');world.use_nodes=True;world.node_tree.nodes['Background'].inputs[0].default_value=(.10,.12,.17,1);world.node_tree.nodes['Background'].inputs[1].default_value=.65;sheet.world=world
for loc,power,size in [((3,-4,10),1800,8),((10,-1,6),900,6)]:
    bpy.ops.object.light_add(type='AREA',location=loc);o=bpy.context.object;o.data.energy=power;o.data.shape='DISK';o.data.size=size
sheet.render.resolution_x=1600;sheet.render.resolution_y=1000;sheet.render.resolution_percentage=100
sheet.view_settings.view_transform='Standard';sheet.render.filepath=str(SOURCE/'console-contact-sheet.png')
(SOURCE/'manifest.json').write_text(json.dumps(dict(generator='tools/art/build_lowpoly_hardware.py',atlas=str(ATLAS.relative_to(ROOT)),assets=rows),indent=2)+'\n',encoding='utf-8')
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE/'hardware-library.blend'))
bpy.ops.render.render(write_still=True)
source_scenes=scenes+media_scenes
for page in range(4):
    prefix='console' if page<2 else 'media';number=page%2+1
    presentation=bpy.data.scenes.new(prefix+' sheet '+str(number));bpy.context.window.scene=presentation
    for i,scene in enumerate(source_scenes[page*12:(page+1)*12]):
        gx=(i%4)*2.2;gy=(i//4)*2.3
        for obj in scene.objects:
            if obj.type!='MESH':continue
            cp=obj.copy();cp.data=obj.data;presentation.collection.objects.link(cp);cp.location+=Vector((gx,-gy,0))
        curve=bpy.data.curves.new('Readable name','FONT');curve.body=scene.name.upper();curve.size=.23;curve.align_x='CENTER'
        obj=bpy.data.objects.new(scene.name+' name',curve);presentation.collection.objects.link(obj);obj.location=(gx,-gy-.68,.025)
        label=mat('bright caption',(.96,.96,1));label.node_tree.nodes.get('Principled BSDF').inputs['Emission Color'].default_value=(.96,.96,1,1);label.node_tree.nodes.get('Principled BSDF').inputs['Emission Strength'].default_value=.5;curve.materials.append(label)
    bpy.ops.object.camera_add(location=(6,-10,12));cam=bpy.context.object;cam.rotation_euler=(Vector((3.3,-2.2,.2))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.type='ORTHO';cam.data.ortho_scale=10.8;presentation.camera=cam
    presentation.world=world;presentation.render.engine='CYCLES';presentation.cycles.samples=12;presentation.cycles.use_denoising=True
    for loc,power,size in [((2,-2,8),1600,8),((8,-2,6),900,6)]:
        bpy.ops.object.light_add(type='AREA',location=loc);o=bpy.context.object;o.data.energy=power;o.data.size=size
    presentation.render.resolution_x=1600;presentation.render.resolution_y=1100;presentation.render.resolution_percentage=100;presentation.view_settings.view_transform='Standard'
    presentation.render.filepath=str(SOURCE/(prefix+'-sheet-'+str(number)+'.png'));bpy.ops.render.render(write_still=True)
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE/'hardware-library.blend'))
print('EXPORTED',len(rows),'L3D2 assets; total triangles',sum(r['triangles'] for r in rows))
