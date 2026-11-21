"""Reference-led, flat-shaded hardware. Blender --background --python THIS_FILE.

Reuses the binary exporter/capture setup, not the old generic console geometry.
Units are normalized display units, not manufacturer engineering dimensions.
"""
from pathlib import Path
import sys

PREVIOUS = Path(__file__).with_name('build_lowpoly_hardware.py')
exec(compile(PREVIOUS.read_text(encoding='utf-8').split('OUT.mkdir(')[0].split('# FAITHFUL_CLI_REDIRECT')[0], str(PREVIOUS), 'exec'))
import bmesh
from mathutils import Matrix

SOURCE = ROOT / 'art-source/faithful-hardware'
ATLAS = ROOT / 'lemuroid-app/src/main/assets/textures/hardware_atlas_v3.png'
image = bpy.data.images.load(str(ATLAS)); image.pack()
materials = {}
bpy.context.preferences.filepaths.save_version = 0

def material(name, color=(1,1,1), tile=0):
    m=mat(name,color,tile,0)
    linear=tuple(v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4 for v in color)
    for node in m.node_tree.nodes:
        if node.type=='MIX_RGB':node.inputs[2].default_value=(*linear,1)
    m.node_tree.nodes.get('Principled BSDF').inputs['Roughness'].default_value=1
    return m

WHITE=material('white'); GRAY=material('gray',(.70,.70,.73)); LIGHT=material('light',(.84,.84,.85))
BLACK=material('black',(.065,.07,.085)); CHAR=material('charcoal',(.16,.17,.19))
RED=material('red',(.72,.035,.06)); PURPLE=material('purple',(.30,.22,.53))
MAGENTA=material('magenta',(.48,.055,.20)); SILVER=material('silver',(.68,.70,.72))
BEIGE=material('beige',(.83,.81,.72)); SCREEN=material('olive LCD',tile=15)
COLOR_SCREEN=material('colour LCD',(.12,.20,.26))
GLASS_REFLECTION=material('pixel glass reflection',(.20,.31,.37)); COVER=material('cover',tile=-1)
WOOD=material('walnut',tile=3); SIDE=material('arcade red artwork',tile=4)
DECK=material('arcade orange deck',tile=5); MARQUEE=material('ARCADE marquee',tile=6)
COIN=material('coin mechanism',tile=7)

def box(name,p,size,m=GRAY,bevel=0):
    # Only intentional single planar corner cuts; never a rounded bevel modifier.
    bpy.ops.mesh.primitive_cube_add(size=1, location=xyz(p))
    o=bpy.context.object;o.name=name;o.dimensions=(size[0],size[2],size[1])
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    return adopt(o,m)

def mesh(name, points, faces, m):
    data=bpy.data.meshes.new(name);data.from_pydata([xyz(p) for p in points],[],faces);data.update()
    bm=bmesh.new();bm.from_mesh(data)
    # Outward-volume recalculation is only meaningful on closed solids. On an
    # isolated decal or annulus it may reverse the intended visible face, which
    # Blender's two-sided preview hides but Android GL_BACK culling exposes.
    if all(edge.is_manifold for edge in bm.edges):
        bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces))
    bm.to_mesh(data);bm.free()
    o=bpy.data.objects.new(name,data);current.objects.link(o);data.materials.append(m)
    return o

def prism(name, outline, a, b, m, plane='xz'):
    # outline is a genuine hardware silhouette, extruded across the missing axis.
    def p(q,v):
        return (q[0],v,q[1]) if plane=='xz' else ((q[0],q[1],v) if plane=='xy' else (v,q[0],q[1]))
    n=len(outline);points=[p(q,v) for v in [a,b] for q in outline]
    faces=[tuple(range(n-1,-1,-1)),tuple(range(n,n*2))]
    faces += [(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
    return mesh(name,points,faces,m)

def cutbox(name,p,size,m,cut=.025):
    x,y,z=p;w,h,d=size;w/=2;d/=2;c=min(cut,w*.25,d*.25)
    outline=[(x-w+c,z-d),(x+w-c,z-d),(x+w,z-d+c),(x+w,z+d-c),
             (x+w-c,z+d),(x-w+c,z+d),(x-w,z+d-c),(x-w,z-d+c)]
    return prism(name,outline,y-h/2,y+h/2,m)

def plate(name,p,w,h,m,axis='z'):
    x,y,z=p
    if axis in ('z','back'):pts=[(x-w/2,y-h/2,z),(x+w/2,y-h/2,z),(x+w/2,y+h/2,z),(x-w/2,y+h/2,z)]
    elif axis=='y':pts=[(x-w/2,y,z+h/2),(x+w/2,y,z+h/2),(x+w/2,y,z-h/2),(x-w/2,y,z-h/2)]
    else:pts=[(x,y-h/2,z+w/2),(x,y-h/2,z-w/2),(x,y+h/2,z-w/2),(x,y+h/2,z+w/2)]
    obj=mesh(name,pts,[(3,2,1,0) if axis=='back' else (0,1,2,3)],m)
    if m==COLOR_SCREEN and axis=='z':
        # Small flat stair-step reflections, matching the pixel glass in the references.
        outline=[(0,0),(.11,0),(.11,.09),(.20,.09),(.20,.18),(.29,.18),(.29,.27),(.38,.27),(.38,.38),(.27,.38),(.27,.29),(.18,.29),(.18,.20),(.09,.20),(.09,.11),(0,.11)]
        mesh('Single pixel glass glint',[(x+w*(u+.005),y+h*v,z+.0002) for u,v in outline],[tuple(range(len(outline)))],GLASS_REFLECTION)
    return obj

def label(tile,p,w,h,axis='y'):
    return plate('Authentic faceplate marking',p,w,h,material('decal '+str(tile),tile=tile),axis)

def vents(p,count,spacing,length,axis='y',vertical=False):
    x,y,z=p
    for i in range(count):
        shift=(i-(count-1)/2)*spacing
        if axis=='y':plate('Vent groove',(x+shift,y,z),spacing*.45,length,BLACK,'y')
        else:plate('Speaker / ventilation slit',(x+shift,y,z),spacing*.42,length,BLACK)

def controls(x,y,z,scale=1):
    box('D-pad horizontal',(x,y,z),(.15*scale,.052*scale,.024),BLACK)
    box('D-pad vertical',(x,y,z+.001),(.052*scale,.15*scale,.026),BLACK)

def button(x,y,z,r=.035,m=BLACK,axis='z'):
    return cyl('Physical button',(x,y,z),r,.018,m,axis,8)

def shell_front(name,w,h,d,m,cut=.025):
    # Cut only corners present on the original; long straight edges stay straight.
    x=w/2;c=cut
    return prism(name,[(-x+c,0),(x-c,0),(x,c),(x,h-c),(x-c,h),(-x+c,h),(-x,h-c),(-x,c)],-d/2,d/2,m,'xy')

def start_scene(key):
    global current
    current=bpy.data.collections.new(key);s=bpy.data.scenes.new(key);s.collection.children.link(current);bpy.context.window.scene=s
    return s

def cartridge_slot(x,y,z,w=.55):
    box('Recessed cartridge slot',(x,y,z),(w,.014,.065),BLACK)
    box('Slot inner shutter',(x,y+.003,z),(w*.88,.014,.026),CHAR)

def ports(y,z,xs,width=.22):
    for x in xs:
        box('Controller connector',(x,y,z),(width,.065,.018),BLACK)
        box('Connector lower lip',(x,y-.026,z+.011),(width*.82,.008,.010),SILVER)

def home_loft(name, outline, rings, m, crown=None):
    # Hand-traced physical outline, joined by planar rings. Ring height may vary
    # per outline point, so a sculpted console is one continuous shell, not boxes.
    n=len(outline);points=[]
    for sx,sz,height in rings:
        for i,(x,z) in enumerate(outline):
            y=height[i] if isinstance(height,(list,tuple)) else height
            points.append((x*sx,y,z*sz))
    faces=[tuple(range(n-1,-1,-1))]
    for k in range(len(rings)-1):
        a=k*n;b=a+n
        faces.extend((a+i,a+(i+1)%n,b+(i+1)%n,b+i) for i in range(n))
    a=(len(rings)-1)*n
    if crown is None:faces.append(tuple(range(a,a+n)))
    else:
        c=len(points);points.append(crown)
        faces.extend((a+i,a+(i+1)%n,c) for i in range(n))
    return mesh(name,points,faces,m)


def console(key):
    scene=start_scene(key)
    if key=='nes':
        # NES-001: taller two-tone front loader, door print is on its FRONT face.
        prism('NES tapered lower case',[(-.59,.0),(.59,.0),(.64,.17),(-.64,.17)],-.505,.505,CHAR,'xy')
        box('NES straight upper case',(0,.305,0),(1.28,.27,1.01),LIGHT)
        plate('Door top rear seam',(-.13,.442,.15),.89,.006,CHAR,'y')
        box('Closed front loading door',(-.13,.313,.511),(.89,.192,.012),LIGHT)
        for x in [-.581,.321]:plate('Door side seam',(x,.313,.519),.004,.197,CHAR)
        box('Door lower seam',(-.13,.215,.52),(.90,.005,.004),CHAR)
        label(8,(-.17,.337,.522),.54,.10,'z')
        box('Right charcoal stripe',(.43,.444,0),(.18,.008,1.01),CHAR)
        for i in range(18):plate('Pale top vent rib',(.43,.45,-.41+i*.034),.18,.012,LIGHT,'y')
        box('Front right black band',(.43,.225,.515),(.18,.44,.012),CHAR)
        for x in [-.36,-.21]:box('POWER RESET key',(x,.09,.519),(.115,.059,.020),GRAY)
        plate('Power status light',(-.46,.09,.532),.018,.018,RED)
        for x in [.378,.481]:
            cutbox('Vertical seven pin socket',(x,.087,.526),(.078,.12,.015),BLACK,.006)
        box('Finger lift',(-.13,.215,.527),(.11,.012,.015),LIGHT)
    elif key=='snes':
        # SNS-001 North American shell: a broad rear deck and two long front
        # shoulders with the narrow EJECT lever between them, not three boxes.
        cutbox('SNS-001 lower shell',(0,.055,0),(1.25,.11,1.06),GRAY,.025)
        profile=[(.09,.525),(.19,.525),(.22,.18),(.305,-.10),(.305,-.49),(.27,-.53),(.09,-.53)]
        prism('SNS-001 continuous stepped upper case',profile,-.61,.61,LIGHT,'yz')
        for x in [-.275,.275]:
            prism('SNS-001 long front rail',[(.105,.532),(.205,.532),(.23,.11),(.23,-.015),(.105,-.015)],x-.17,x+.17,LIGHT,'yz')
        # Central cartridge housing stays flush with the shoulders at the back.
        for x in [-.412,.412]:plate('Cartridge housing side seam',(x,.307,-.245),.008,.46,CHAR,'y')
        cartridge_slot(0,.315,-.235,.77)
        plate('Cartridge shutter seam',(0,.327,-.235),.73,.006,CHAR,'y')
        label(9,(-.13,.309,-.07),.45,.095)
        # The two sliders are wide and sit over the rails. EJECT is narrow/long.
        for x in [-.275,.275]:
            cutbox('Purple POWER RESET slider',(x,.244,.105),(.325,.040,.105),PURPLE,.016)
            box('Raised slider grip',(x,.269,.10),(.21,.017,.033),PURPLE)
        box('Central long EJECT lever',(0,.207,.305),(.205,.020,.295),GRAY)
        plate('EJECT lever seam',(0,.221,.46),.19,.007,CHAR,'y')
        for x in [-.275,.275]:
            cutbox('Controller port recess',(x,.065,.539),(.30,.052,.016),CHAR,.012)
            plate('Controller contact slot',(x,.065,.549),.235,.018,BLACK)
        for x in [-.55,.55]:vents((x,.310,-.35),3,.015,.18)
        plate('Power indicator',(-.465,.212,.23),.018,.025,RED,'y')
    elif key=='n64':
        # NUS-001: curved perimeter kept as 16 explicit planes, continuous sloped
        # shoulders and cartridge ridge. No separate tall rectangular rear block.
        outline=[(-.46,-.475),(.46,-.475),(.55,-.44),(.59,-.34),(.60,-.12),(.64,.23),(.63,.36),(.56,.46),(.43,.48),(.30,.43),(-.30,.43),(-.43,.48),(-.56,.46),(-.63,.36),(-.64,.23),(-.60,-.12),(-.59,-.34),(-.55,-.44)]
        home_loft('NUS-001 lower winged shell',outline,[(.97,.98,.0),(1,1,.115)],BLACK)
        heights=[.27-.10*max(0,z+.12) for x,z in outline]
        home_loft('NUS-001 sculpted upper shell',outline,[(1,1,.119),(.97,.98,.164),(.91,.94,heights)],CHAR,(0,.335,-.22))
        # Project each grille vertex onto the actual low-poly dome. Raising the
        # physical crown must not bury the grille in the changed surface.
        def n64_surface_y(x,z):
            apex=(0,-.22,.335)
            rim=[(xx*.91,zz*.94,yy) for (xx,zz),yy in zip(outline,heights)]
            for j,a in enumerate(rim):
                b=rim[(j+1)%len(rim)];c=apex
                det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(det)<1e-8:continue
                u=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(z-c[1]))/det
                v=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(z-c[1]))/det
                w=1-u-v
                if min(u,v,w)>=-1e-6:return u*a[2]+v*b[2]+w*c[2]+.003
            return .27
        # Forward grille follows the cartridge ridge's real segmented slope.
        for i in range(17):
            x=(i-8)*.032;front=.10-.10*(abs(x)/.29)**2
            mesh('N64 recessed cooling slit',[(xx,n64_surface_y(xx,zz),zz) for xx,zz in [(x-.006,-.075),(x+.006,-.075),(x+.006,front),(x-.006,front)]],[(3,2,1,0)],BLACK)
        cutbox('Cartridge slot surround',(0,.337,-.235),(.70,.020,.105),BLACK,.025)
        box('Two grey cartridge shutters',(0,.349,-.235),(.64,.012,.067),GRAY)
        plate('Cartridge shutter dividing seam',(0,.356,-.235),.63,.004,BLACK,'y')
        cutbox('Memory expansion door seam',(0,.274,.235),(.365,.012,.205),BLACK,.035)
        cutbox('Memory expansion door',(0,.284,.232),(.347,.012,.184),CHAR,.032)
        box('Memory door finger tab',(0,.294,.139),(.073,.009,.022),BLACK)
        cutbox('POWER recessed track',(-.39,.248,.227),(.105,.015,.19),BLACK,.025)
        cutbox('POWER slider',(-.39,.262,.245),(.088,.024,.108),CHAR,.022)
        cutbox('RESET button recess',(.39,.248,.227),(.105,.015,.19),BLACK,.025)
        cutbox('RESET button',(.39,.261,.254),(.086,.021,.12),CHAR,.025)
        for x in [-.43,-.145,.145,.43]:
            z=.477 if abs(x)>.3 else .438
            cyl('Four grey controller socket rims',(x,.107,z),.053,.019,GRAY,'z',8)
            mesh('Controller socket openings',[(x+.035*math.cos(i*math.tau/8),.107+.035*math.sin(i*math.tau/8),z+.012) for i in range(8)],[tuple(range(8))],BLACK)
        # Front logo belongs on the near-vertical centre badge, not flat on top.
        label(11,(0,.165,.451),.17,.082,'z')
        plate('Red power LED',(-.25,.15,.451),.015,.014,RED)
    elif key in ('md','segacd'):
        # User-selected Mega Drive I western casing, shared by md/genesis aliases.
        # Right-offset circular deck, controls left, gold 16-BIT, front-right branding.
        base=.19 if key=='segacd' else 0
        if base:
            cutbox('Sega CD model 1 front-loader base',(0,.095,.015),(1.35,.19,1.08),BLACK,.018)
            box('Sega CD drawer face',(-.115,.105,.563),(.84,.098,.022),CHAR)
            plate('Front tray seam',(-.115,.105,.577),.77,.048,BLACK)
            label(14,(-.115,.109,.581),.25,.035,'z')
            for x,m in [(.40,material('CD ready green',(.10,.65,.20))),(.49,RED)]:plate('CD status LED',(x,.115,.568),.022,.014,m)
        outline=[(-.57,-.49),(.54,-.49),(.64,-.40),(.66,-.27),(.66,.37),(.59,.49),(-.59,.49),(-.66,.40),(-.66,-.36)]
        home_loft('Mega Drive I lower chassis',outline,[(.99,.98,base),(1,1,base+.065),(1,1,base+.105)],BLACK)
        heights=[base+(.25 if z<-.20 else .19) for x,z in outline]
        home_loft('Mega Drive I sloping upper case',outline,[(1,1,base+.11),(.96,.97,heights)],BLACK,(.12,base+.238,-.12))
        cyl('Offset round Mega Drive deck',(.13,base+.241,-.10),.413,.034,CHAR,'y',24)
        cyl('Deck black inset',(.13,base+.263,-.10),.385,.012,BLACK,'y',24)
        # Lettering is deliberately separate from the cartridge shutter.
        label(1,(.32,base+.202,.43),.34,.065)
        label(10,(.13,base+.272,.12),.215,.09)
        cartridge_slot(.13,base+.278,-.24,.60)
        plate('Silver power indicator strip',(.13,base+.274,.235),.235,.040,LIGHT,'y')
        plate('Red power indicator',(.13,base+.276,.235),.014,.014,RED,'y')
        # Long vent bank is rear-left, not on the right side of the circle.
        for i in range(14):plate('Genesis left cooling louvre',(-.51,base+.254,-.40+i*.025),.18,.010,CHAR,'y')
        box('Left control panel',(-.435,base+.200,.307),(.29,.015,.32),CHAR)
        box('Vertical headphone volume recess',(-.515,base+.213,.30),(.052,.012,.238),BLACK)
        box('Volume slider grip',(-.515,base+.227,.337),(.047,.021,.040),GRAY)
        box('POWER on-off recess',(-.384,base+.215,.218),(.119,.012,.053),BLACK)
        box('POWER slider',(-.405,base+.229,.218),(.044,.020,.046),CHAR)
        box('RESET button recess',(-.384,base+.215,.389),(.119,.012,.050),BLACK)
        box('White RESET button',(-.384,base+.229,.389),(.088,.018,.033),LIGHT)
        cyl('Headphone socket',(-.51,base+.051,.502),.026,.016,SILVER,'z',8)
        cyl('Headphone socket hole',(-.51,base+.051,.513),.017,.008,BLACK,'z',8)
        # Real front DE-9 sockets: trapezoidal metal opening and two rows of pins.
        for x in [.255,.455]:
            for width,height,z,m in [(.086,.033,.505,GRAY),(.075,.024,.507,BLACK)]:
                mesh('Mega Drive DE-9 socket',[(x-width,base+.055+height,z),(x+width,base+.055+height,z),(x+width*.82,base+.055-height,z),(x-width*.82,base+.055-height,z)],[(3,2,1,0)],m)
            for count,y in [(5,.067),(4,.044)]:
                for i in range(count):plate('DE-9 visible pin',(x+(i-(count-1)/2)*.024,base+y,.509),.007,.008,SILVER)
    elif key=='sms':
        # Master System I western black/red case; the media/control panel occupies
        # the right half, while the left is a long uninterrupted wedge.
        prism('Master System I tapered wedge',[(0,.45),(.105,.45),(.205,.34),(.26,-.27),(.20,-.45),(0,-.45)],-.71,.71,BLACK,'yz')
        mesh('Right red control panel',[(.03,.157,.40),(.61,.157,.40),(.61,.212,.34),(.03,.212,.34),(.61,.249,-.07),(.03,.249,-.07)],[(0,1,2,3),(3,2,4,5)],RED)
        cartridge_slot(.32,.266,-.27,.56)
        box('Right panel black key strip',(.32,.256,-.12),(.58,.018,.10),BLACK)
        for x in [.12,.52]:box('RESET PAUSE control',(x,.275,-.12),(.115,.022,.058),CHAR)
        # The thin red front edge is distinct from the large red top diagram.
        mesh('Thin red front seam',[(-.70,.112,.444),(.70,.112,.444),(.70,.118,.438),(-.70,.118,.438)],[(0,1,2,3)],RED)
        label(14,(-.33,.253,-.11),.39,.13)
        ports(.046,.459,[-.03,.18],.15)
        box('Card input opening',(.49,.060,.459),(.29,.052,.018),BLACK)
        box('Front POWER push button',(-.57,.032,.472),(.15,.036,.025),CHAR)
        plate('Red panel power triangle',(.23,.233,.14),.10,.063,BLACK,'y')
    elif key=='psx':
        cutbox('PlayStation SCPH-1001 lower chassis',(0,.075,0),(1.27,.15,.885),GRAY,.014)
        cutbox('PlayStation upper chassis',(0,.207,0),(1.27,.114,.885),LIGHT,.014)
        cyl('Circular lid seam',(0,.267,0),.403,.009,GRAY,'y',32)
        cyl('Large circular CD lid',(0,.274,0),.397,.009,LIGHT,'y',32)
        label(12,(0,.280,.145),.20,.15)
        for x,r in [(-.465,.092),(.465,.092)]:button(x,.271,.30,r,GRAY,'y')
        button(-.50,.271,.10,.042,GRAY,'y')
        for x in [-.303,.303]:
            plate('Long side panel seam',(x,.266,-.37),.006,.145,GRAY,'y')
            plate('Front side panel seam',(x,.266,.37),.006,.145,GRAY,'y')
        plate('Power LED',(-.545,.275,.22),.015,.025,material('power green',(.2,.62,.25)),'y')
        for x in [-.27,.27]:
            box('Memory card slot',(x,.185,.451),(.32,.031,.018),CHAR)
            box('Controller socket',(x,.075,.452),(.31,.068,.022),CHAR)
        for x in [-.54,.54]:vents((x,.266,-.22),5,.022,.25)
    elif key=='atari2600':
        # CX-2600 1977 heavy sixer: horizontal ribs on the sloping FRONT deck,
        # six switches behind them, and walnut across the front fascia.
        prism('CX2600 heavy sixer continuous wedge',[(0,.43),(.12,.43),(.22,.12),(.27,-.14),(.245,-.43),(0,-.43)],-.73,.73,BLACK,'yz')
        plate('Full-width walnut fascia',(0,.067,.438),1.43,.112,WOOD)
        for i in range(17):
            z=-.045+i*.025
            y=(.27-(z+.14)*.05/.26 if z<=.12 else .22-(z-.12)*.10/.31)+.008
            box('Transverse front deck rib',(0,y,z),(1.32,.017,.011),CHAR)
        # Thin yellow-orange outline around the original rear switch plate.
        orange=material('VCS ochre trim',(.72,.41,.12))
        for z in [-.32,-.15]:plate('Switch panel ochre edge',(0,.280,z),1.32,.007,orange,'y')
        for x in [-.665,.665]:plate('Switch panel ochre end',(x,.280,-.235),.006,.17,orange,'y')
        cartridge_slot(0,.283,-.235,.36)
        for x in [-.55,-.42,-.29,.29,.42,.55]:
            box('Chrome switch base',(x,.283,-.235),(.065,.014,.075),SILVER)
            box('Six tall chrome toggle',(x,.312,-.255),(.025,.059,.025),SILVER)
        label(28,(.48,.134,.328),.13,.065)
    elif key=='atari7800':
        # CX7800: shallow trapezoidal body and FOUR front keys. The old asset's
        # two-button layout and top-mounted ports did not match the hardware.
        outline=[(-.52,-.47),(.52,-.47),(.62,-.34),(.65,.40),(.59,.46),(-.59,.46),(-.65,.40),(-.62,-.34)]
        home_loft('CX7800 tapered lower case',outline,[(.97,.98,0),(1,1,.07)],BLACK)
        heights=[.245 if z<0 else .14 for x,z in outline]
        home_loft('CX7800 shallow sloping shell',outline,[(1,1,.075),(.96,.97,heights)],BLACK,(0,.25,-.18))
        cartridge_slot(0,.264,-.20,.43)
        for side in [-1,1]:
            for i in range(7):plate('Rear vent slot',(side*(.29+i*.036),.246,-.30),.014,.17,CHAR,'y')
        mesh('Brushed silver diagonal faceplate',[(-.60,.217,.10),(.60,.217,.10),(.59,.252,-.12),(-.59,.252,-.12)],[(0,1,2,3)],SILVER)
        label(29,(-.29,.240,-.035),.36,.055)
        rainbow=[RED,material('7800 orange',(.87,.38,.03)),material('7800 yellow',(.81,.70,.08)),material('7800 green',(.17,.49,.18))]
        for i,m in enumerate(rainbow):
            x=-.43875+i*.2925
            plate('Thin 7800 rainbow band',(x,.239,-.02),.2925,.006,m,'y')
        for x in [-.47,-.29,.29,.47]:box('POWER PAUSE SELECT RESET key',(x,.153,.345),(.137,.024,.069),CHAR)
        ports(.046,.468,[-.32,.32],.17)
    elif key=='pce':
        # PI-TG001: central HuCard loading well open to the front, two white side
        # shoulders, green POWER on the left and one round PAD socket on right.
        cutbox('PI-TG001 white lower shell',(0,.055,0),(.79,.11,.77),BEIGE,.014)
        cutbox('PI-TG001 upper housing',(0,.135,-.10),(.79,.10,.57),WHITE,.018)
        for x in [-.285,.285]:
            box('HuCard loading well side shoulder',(x,.150,.19),(.20,.095,.37),WHITE)
            for z in [.06,.10,.14,.18]:plate('Side shoulder vent',(x,.201,z),.185,.012,GRAY,'y')
        notch=[(-.18,.025),(.18,.025),(.18,.375),(.078,.375)]
        notch += [(.078*math.cos(i*math.pi/8),.375-.078*math.sin(i*math.pi/8)) for i in range(1,9)]
        notch += [(-.18,.375)]
        prism('HuCard bed with concave thumb access',notch,.106,.128,LIGHT)
        for x in [-.125,-.075,-.025,.025,.075,.125]:plate('HuCard tray guide',(x,.131,.14),.008,.23,WHITE,'y')
        box('HuCard connector mouth',(0,.158,-.005),(.36,.037,.025),BLACK)
        green=material('PC Engine green POWER',(.16,.42,.23))
        box('POWER switch recess',(-.285,.077,.397),(.14,.041,.018),BLACK)
        box('Green POWER slider',(-.315,.076,.410),(.060,.033,.025),green)
        cyl('PAD connector outer ring',(.285,.065,.398),.043,.020,SILVER,'z',8)
        cyl('PAD connector black DIN opening',(.285,.065,.411),.033,.009,BLACK,'z',8)
        label(27,(0,.189,-.18),.31,.12)
    elif key in ('gb','gbc'):
        # DMG-01 (90 x 148 mm) and CGB-001 (78 x 133 mm): different case outlines.
        dmg=key=='gb';body=BEIGE if dmg else material('GBC teal',(.025,.48,.43))
        w,h,d=(.572,.940,.195) if dmg else (.551,.940,.192)
        if dmg: outline=[(-.286,.018),(-.286,.922),(-.268,.940),(.268,.940),(.286,.922),(.286,.105),(.265,.054),(.220,.016),(.180,0),(-.268,0)]
        else: outline=[(-.245,0),(.165,0),(.235,.025),(.275,.080),(.275,.885),(.253,.928),(.205,.940),(-.205,.940),(-.253,.928),(-.275,.885),(-.275,.035)]
        prism('DMG-01 case' if dmg else 'CGB-001 case',outline,-d/2,d/2,body,'xy')
        z=d/2
        # DMG has the characteristic asymmetric lower-right display surround.
        surround=[(-.240,.49),(.182,.49),(.238,.535),(.238,.877),(-.240,.877)] if dmg else [(-.235,.489),(.195,.489),(.238,.525),(.238,.865),(.210,.89),(-.210,.89),(-.238,.865)]
        prism('DMG grey lens' if dmg else 'CGB black lens',surround,z+.002,z+.013,GRAY if dmg else BLACK,'xy')
        plate('10 to 9 LCD',(0,.701,z+.016),.320,.288,SCREEN if dmg else COLOR_SCREEN)
        controls(-.157,.327,z+.022,.88)
        for x,y in [(.112,.307),(.210,.351)]:button(x,y,z+.024,.041,MAGENTA if dmg else BLACK)
        for x,y in [(-.063,.188),(.040,.158)]:
            o=box('SELECT START slanted rubber key',(x,y,z+.02),(.077,.022,.018),CHAR);o.rotation_euler.y=-.35
        if dmg:
            for i in range(6):
                o=plate('DMG six diagonal speaker slits',(.102+i*.023,.076+i*.009,z+.004),.008,.057,CHAR);o.rotation_euler.y=-.30
        else:
            for row in range(4):
                for col in range(5):plate('CGB perforated speaker',(.095+col*.025,.062+row*.023,z+.004),.009,.010,BLACK)
        label(13 if dmg else 24,(0,.469,z+.016),.32,.050,'z')
        plate('Battery indicator',(-.208,.730,z+.017),.014,.012,RED)
        box('Rear battery cover',(0,.27,-d/2-.004),(.40,.36,.008),body)
    elif key=='gba':
        # AGB-001 launch indigo. Front outline traced against Nintendo's launch
        # photograph: game.watch.impress.co.jp/docs/20010116/nin00.jpg.
        # The top centre is high, shoulders descend, and the BOTTOM bows down.
        body=material('GBA indigo',(.34,.25,.59));keys=material('GBA original grey controls',(.72,.72,.74))
        outline=[(-.285,.646),(.279,.646),(.327,.625),(.365,.600),(.447,.571),(.529,.546),(.556,.510),(.577,.191),(.569,.143),(.527,.096),(.344,.048),(.258,.023),(.069,.004),(-.069,.004),(-.260,.023),(-.348,.048),(-.531,.096),(-.573,.145),(-.581,.191),(-.573,.464),(-.556,.535),(-.518,.558),(-.434,.577),(-.371,.600),(-.333,.627)]
        # Three explicit faceted rings taper back and front; no smooth bevel.
        # Real thickness 24.5 / 144.5 * normalized width 1.16 = .197.
        n=len(outline);points=[]
        for scale,z in [(.968,-.098),(1.0,.025),(.985,.094)]:
            points += [(x*scale,.325+(y-.325)*scale,z) for x,y in outline]
        faces=[tuple(range(n-1,-1,-1)),tuple(range(2*n,3*n))]
        for r in range(2):
            for i in range(n):faces.append((r*n+i,r*n+(i+1)%n,(r+1)*n+(i+1)%n,(r+1)*n+i))
        mesh('AGB-001 traced front and tapered shell',points,faces,body)
        z=.094
        # The real lens has curved top and lower edges, not a square black slab.
        lens=[(-.281,.552),(-.215,.562),(0,.568),(.215,.562),(.281,.552),(.298,.528),(.309,.176),(.300,.117),(.269,.089),(.178,.071),(0,.064),(-.178,.071),(-.269,.089),(-.300,.117),(-.309,.176),(-.298,.528)]
        prism('AGB curved lens outline',lens,z+.001,z+.006,BLACK,'xy')
        plate('AGB 240 by 160 active LCD',(0,.347,z+.008),.514,.342667,COLOR_SCREEN)
        # Narrow wordmark uses the existing clean monochrome label atlas.
        label(2,(0,.115,z+.009),.305,.027,'z')
        # The original D-pad and A/B are silver-grey, not black or gold.
        cross=[(-.070,-.023),(-.023,-.023),(-.023,-.070),(.023,-.070),(.023,-.023),(.070,-.023),(.070,.023),(.023,.023),(.023,.070),(-.023,.070),(-.023,.023),(-.070,.023)]
        prism('AGB one-piece grey D-pad',[(x-.430,y+.364) for x,y in cross],z+.004,z+.021,keys,'xy')
        for x,y in [(.381,.346),(.487,.379)]:button(x,y,z+.017,.038,keys)
        # START over SELECT, both close to the lens at lower LEFT.
        for y in [.209,.146]:button(-.348,y,z+.012,.017,keys)
        # Five long nearly horizontal slots slant upwards to the right.
        for i in range(5):
            y=.126+i*.023
            mesh('AGB diagonal speaker opening',[(.342,y,z+.001),(.469,y+.033,z+.001),(.469,y+.044,z+.001),(.342,y+.011,z+.001)],[(0,1,2,3)],BLACK)
        # L/R follow the falling shoulder contour behind the purple front lip.
        # Their front-most face stops behind the shell so they cannot float.
        for side in [-1,1]:
            xy=[(side*.326,.627),(side*.455,.596),(side*.545,.564),(side*.552,.535),(side*.454,.561),(side*.355,.598)]
            prism('AGB integrated grey shoulder trigger',xy,-.075,.004,keys,'xy')
        plate('AGB green POWER indicator',(.383,.526,z+.001),.017,.017,material('power green',(.32,.60,.12)))
        box('AGB rear battery door',(0,.270,-.101),(.510,.335,.008),body)
    elif key=='gg':
        # Sega Game Gear 2110: 210 x 113 mm; the old asset was too tall/narrow.
        w,h,z=1.20,.646,.115
        outline=[(-.485,0),(.485,0),(.550,.025),(.590,.078),(.600,.445),(.570,.555),(.505,.626),(.440,.646),(-.440,.646),(-.505,.626),(-.570,.555),(-.600,.445),(-.590,.078),(-.550,.025)]
        prism('Game Gear 2110 original case',outline,-z,z,CHAR,'xy')
        shell_front('Game Gear recessed black lens',.555,.465,.013,BLACK,.028).location+=Vector(xyz((0,.104,z+.005)))
        plate('Game Gear 4 to 3 LCD',(0,.347,z+.017),.440,.330,COLOR_SCREEN)
        label(16,(0,.544,z+.018),.350,.046,'z')
        cyl('Game Gear circular direction rocker',(-.447,.350,z+.018),.090,.015,BLACK,'z',12)
        controls(-.447,.350,z+.035,.87)
        blue=material('GG blue keys',(.08,.36,.60))
        for x,y in [(.400,.267),(.494,.338)]:button(x,y,z+.028,.048,blue)
        button(.455,.525,z+.024,.032,blue)
        for i in range(7):plate('Game Gear left speaker slot',(-.500+i*.020,.105,z+.006),.008,.106,BLACK)
        plate('Game Gear power indicator',(-.347,.505,z+.02),.014,.014,RED)
        box('Game Gear battery compartment',(0,.260,-z-.004),(.89,.38,.008),CHAR)
    elif key=='lynx':
        # Lynx II (1991), not an olive generic rectangle. Four mirrored action keys.
        body=material('Lynx II graphite',(.32,.33,.34));z=.112
        outline=[(-.485,0),(.485,0),(.558,.030),(.610,.095),(.630,.195),(.612,.560),(.570,.667),(.500,.720),(-.500,.720),(-.570,.667),(-.612,.560),(-.630,.195),(-.610,.095),(-.558,.030)]
        prism('Atari Lynx II grip silhouette',outline,-z,z,body,'xy')
        shell_front('Lynx II deep black lens',.642,.579,.016,BLACK,.020).location+=Vector(xyz((0,.080,z+.005)))
        plate('Lynx 160 to 102 LCD',(0,.368,z+.019),.536,.342,COLOR_SCREEN)
        # Tile 17 has a green background from the old Lynx I interpretation; do not paste it onto a grey Lynx II.
        controls(-.474,.427,z+.027,1.07)
        for x,y in [(.438,.483),(.534,.483),(.438,.366),(.534,.366)]:button(x,y,z+.027,.036,BLACK)
        for y in [.176,.235,.294]:box('Lynx option restart flip key',(.391,y,z+.022),(.036,.025,.013),BLACK)
        for y in [.210,.270]:box('Lynx power backlight key',(-.470,y,z+.022),(.053,.027,.013),BLACK)
        for i in range(6):plate('Lynx II right speaker',(.468+i*.014,.152,z+.006),.006,.110,BLACK)
        box('Lynx II battery compartment',(0,.290,-z-.004),(.80,.40,.008),body)
    elif key in ('ngp','ngc'):
        # Both SNK portables have a click stick, never a D-pad plus an extra stick.
        color=key=='ngc';body=material('NGPC platinum blue',(.24,.38,.55)) if color else SILVER
        w,h,z=(.97,.615,.105) if color else (.97,.594,.095)
        outline=[(-.414,0),(.414,0),(.465,.032),(.485,.090),(.485,h-.080),(.454,h-.026),(.395,h),(-.395,h),(-.454,h-.026),(-.485,h-.080),(-.485,.090),(-.465,.032)]
        prism('SNK Neo Geo Pocket Color case' if color else 'SNK Neo Geo Pocket case',outline,-z,z,body,'xy')
        shell_front('SNK square display lens',.448,.462,.015,BLACK,.012).location+=Vector(xyz((0,.081,z+.004)))
        plate('SNK 160 to 152 LCD',(0,.333,z+.018),.360,.342,COLOR_SCREEN if color else SCREEN)
        label(19 if color else 18,(0,.107,z+.019),.280,.030,'z')
        cyl('SNK click-stick recessed ring',(-.356,.335,z+.020),.085,.014,BLACK,'z',12)
        cyl('SNK octagonal click-stick',(-.356,.335,z+.041),.060,.027,BLACK,'z',8)
        for x,y in [(.310,.291),(.405,.350)]:button(x,y,z+.026,.041,BLACK)
        for x in [-.359,.359]:box('SNK POWER OPTION key',(x,.523,z+.016),(.067,.025,.012),BLACK)
        for i in range(6):plate('SNK lower left speaker',(-.410+i*.020,.122,z+.006),.008,.065,BLACK)
        box('SNK rear battery compartment',(0,.268,-z-.004),(.535,.36,.008),body)
    elif key in ('ws','wsc'):
        # Original SW-001 and WonderSwan Color: two separate four-button diamonds.
        color=key=='wsc';body=material('WonderSwan pearl blue',(.28,.39,.57)) if color else SILVER
        z=.073 if color else .065
        outline=[(-.417,0),(.358,0),(.424,.022),(.470,.067),(.485,.135),(.485,.500),(.460,.555),(.406,.585),(-.406,.585),(-.460,.555),(-.485,.500),(-.485,.070),(-.460,.025)]
        prism('WonderSwan Color outline' if color else 'WonderSwan SW-001 outline',outline,-z,z,body,'xy')
        shell_front('WonderSwan inset screen lens',.548,.463,.014,LIGHT if color else CHAR,.012).location+=Vector(xyz((.008,.075,z+.003)))
        plate('WonderSwan 224 to 144 LCD',(.008,.333,z+.017),.444,.2854,COLOR_SCREEN if color else SCREEN)
        label(21 if color else 20,(.008,.146 if color else .506,z+.018),.280,.030,'z')
        for cy in [.400,.180]:
            for dx,dy in [(0,.049),(.049,0),(0,-.049),(-.049,0)]:box('WonderSwan independent direction key',(-.365+dx,cy+dy,z+.023),(.038,.038,.015),LIGHT)
        for x,y in [(.338,.178),(.407,.244)]:button(x,y,z+.022,.032,LIGHT)
        for x in [-.075,.080]:box('WonderSwan SOUND START key',(x,.052,z+.020),(.057,.019,.012),BLACK)
        if color:box('WonderSwan Color orange POWER key',(.212,.052,z+.020),(.056,.023,.012),RED)
        for i in range(5):plate('WonderSwan right speaker',(.338+i*.020,.423,z+.005),.008,.075,BLACK)
        box('WonderSwan single AA back grip',(-.18,.24,-z-.025),(.43,.39,.05),body)
    elif key=='psp':
        # PSP-1000: 170 x 74 mm. Shallow faceted capsule, 480:272 active display.
        z=.087
        outline=[(-.510,0),(.510,0),(.576,.026),(.615,.083),(.635,.180),(.635,.360),(.615,.458),(.576,.515),(.510,.540),(-.510,.540),(-.576,.515),(-.615,.458),(-.635,.360),(-.635,.180),(-.615,.083),(-.576,.026)]
        prism('PSP-1000 capsule silhouette',outline,-z,z,BLACK,'xy')
        plate('PSP widescreen LCD',(0,.300,z+.007),.756,.4284,COLOR_SCREEN)
        controls(-.486,.346,z+.024,.94)
        button(-.481,.126,z+.022,.047,CHAR)
        for dx,dy in [(0,.069),(.069,0),(0,-.069),(-.069,0)]:button(.486+dx,.346+dy,z+.021,.028,CHAR)
        for x in [-.343,-.237,-.177,-.100,.240,.330]:box('PSP lower system key',(x,.043,z+.013),(.041,.015,.009),CHAR)
        label(22,(0,.044,z+.017),.145,.032,'z')
        for x in [-.590,.590]:
            for y in [.218,.247,.276]:plate('PSP speaker aperture',(x,y,z+.004),.010,.009,CHAR)
        for x in [-.477,.477]:box('PSP clear L R trigger',(x,.525,-.015),(.230,.029,.115),SILVER)
        cyl('PSP rear UMD ring',(0,.270,-z-.005),.216,.010,SILVER,'z',24)
    elif key in ('nds','3ds'):
        ds=key=='nds';body=LIGHT if ds else material('3DS aqua blue',(.11,.32,.43))
        w=1.10 if ds else 1.05;depth=.635 if ds else .650
        cutbox('NTR-001 lower case' if ds else 'CTR-001 lower case',(0,.067,.030),(w,.134,depth),body,.020)
        # Both DS LCDs are 4:3; CTR top is 5:3 and lower remains 4:3.
        sw=.445 if ds else .470;sh=sw*.75
        plate('Lower touch bezel',(0,.137,.030),sw+.073,sh+.060,BLACK,'y')
        plate('Lower touch LCD',(0,.139,.030),sw,sh,COLOR_SCREEN,'y')
        dy=.070 if ds else .155
        box('Lower D-pad horizontal',(-w*.352,.151,dy),(.140,.015,.044),BLACK)
        box('Lower D-pad vertical',(-w*.352,.152,dy),(.044,.015,.140),BLACK)
        if not ds:button(-w*.352,.156,-.095,.057,LIGHT,'y')
        for dx,dz in [(0,-.064),(.064,0),(0,.064),(-.064,0)]:button(w*.352+dx,.152,.015+dz,.025,BLACK,'y')
        if ds:
            for z in [-.171,-.122]:box('DS START SELECT',(.405,.151,z),(.065,.012,.024),BLACK)
            box('DS POWER key',(-.405,.151,-.156),(.075,.012,.025),BLACK)
            # Original DS has a large GBA cartridge opening on the front edge.
            box('DS Slot-2 GBA mouth',(0,.065,.352),(.59,.044,.014),BLACK)
        else:
            for x in [-.134,0,.134]:box('3DS SELECT HOME START',(x,.149,.272),(.110,.010,.026),BLACK)
            box('3DS POWER key',(.391,.149,.260),(.074,.012,.032),BLACK)
        cyl('Clamshell hinge',(0,.149,-.253),.035,w*.90,body,'x',8)
        old=set(scene.objects)
        shell_front('DS original upper lid' if ds else '3DS upper lid',w,.590,.060,body,.018).location+=Vector(xyz((0,.17,-.260)))
        plate('Upper display lens',(0,.478,-.224),.610 if ds else .920,.440,BLACK)
        plate('Upper LCD',(0,.478,-.221),sw if ds else .695,sh if ds else .417,COLOR_SCREEN)
        for x in [-w*.424,w*.424]:
            for row in range(3):
                for col in range(2):plate('Upper stereo speaker hole',(x+(col-.5)*.018,.463+(row-1)*.026,-.220),.009,.014,BLACK)
        if not ds:
            button(0,.716,-.219,.014,BLACK)
            box('3DS depth slider',(.486,.585,-.219),(.014,.063,.009),LIGHT)
        bpy.context.view_layer.update()
        pivot=Vector(xyz((0,.170,-.260)))
        for o in set(scene.objects)-old:o.matrix_world=Matrix.Translation(pivot)@Matrix.Rotation(math.radians(-14),4,'X')@Matrix.Translation(-pivot)@o.matrix_world
        cyl('Headphone jack',(.160 if ds else 0,.040,.356),.018,.014,BLACK,'z',8)
    elif key=='dos':
        box('IBM 5150 desktop',(0,.155,0),(1.12,.31,.92),BEIGE)
        for x in [.02,.37]:
            box('Side by side full height 5.25 drive',(x,.165,.468),(.32,.218,.016),CHAR)
            plate('Disk insertion slot',(x,.17,.480),.27,.014,BLACK)
            box('Full height drive latch',(x,.16,.489),(.047,.079,.015),GRAY)
        label(30,(-.44,.231,.477),.10,.064,'z')
        vents((-.37,.105,.477),11,.027,.085,'z')
        # CRT side taper: screen stays rectangular 4:3, tube deep at the rear.
        prism('5153 CRT tapered hood',[(.32,.30),(.96,.30),(.92,-.36),(.39,-.36)],-.43,.43,BEIGE,'yz')
        box('CRT front frame',(0,.64,.319),(.86,.64,.052),BEIGE)
        box('CRT recessed surround',(-.04,.66,.350),(.68,.50,.015),GRAY)
        plate('Four by three screen',(-.04,.66,.362),.60,.45,COLOR_SCREEN)
        for y in [.48,.56,.64]:button(.365,y,.36,.024,GRAY)
        label(30,(.32,.84,.352),.10,.044,'z')
        prism('Model F keyboard wedge',[(0,1.04),(.065,1.04),(.11,.60),(0,.60)],-.56,.56,BEIGE,'yz')
        # Separate left function block, central typewriter block and right numeric block.
        for row in range(5):
            for col in range(17):
                if row==4 and 3<=col<=11:continue
                z=.65+row*.070;y=.114-row*.007
                plate('Model F keycap',(-.51+col*.063,y,z),.052,.055,LIGHT if col>1 else GRAY,'y')
        plate('Long space bar',(-.055,.085,.93),.49,.055,LIGHT,'y')
    elif key=='arcade':
        side=[(0,.35),(.69,.35),(.81,.54),(.93,.54),(1.02,.28),(1.51,.05),(1.61,.22),(1.85,.22),(1.85,-.39),(0,-.39)]
        prism('Left angular red side panel',side,-.46,-.415,SIDE,'yz')
        prism('Right angular red side panel',side,.415,.46,SIDE,'yz')
        box('Black cabinet base',(0,.34,-.005),(.83,.68,.68),BLACK)
        box('Rear upright',(0,1.25,-.35),(.83,1.16,.08),BLACK)
        box('Marquee top rail',(0,1.845,-.04),(.84,.035,.54),BLACK)
        box('Marquee lower rail',(0,1.625,.17),(.84,.032,.10),BLACK)
        plate('ARCADE illuminated marquee',(0,1.735,.233),.82,.20,MARQUEE)
        plate('Coin door',(0,.345,.344),.34,.40,COIN)
        # Sloped control panel and recessed sloped monitor are real planar geometry.
        mesh('Orange control deck',[(-.415,.80,.53),(.415,.80,.53),(.415,.94,.27),(-.415,.94,.27)],[(0,1,2,3)],DECK)
        box('Orange control fascia',(0,.784,.536),(.83,.07,.025),material('orange',(.96,.43,.015)))
        mesh('Recessed monitor frame',[(-.39,1.0,.235),(.39,1.0,.235),(.39,1.53,.01),(-.39,1.53,.01)],[(0,1,2,3)],BLACK)
        mesh('CRT screen',[(-.31,1.06,.213),(.31,1.06,.213),(.31,1.46,.042),(-.31,1.46,.042)],[(0,1,2,3)],COLOR_SCREEN)
        cyl('Joystick stem',(-.23,.955,.40),.018,.15,BLACK,'y',8)
        cyl('Joystick square-cut ball',(-.23,1.025,.40),.050,.055,RED,'y',8)
        for x,z,m in [(.06,.39,RED),(.16,.39,RED),(.26,.39,RED),(.11,.31,material('blue',(.05,.38,.65))),(.21,.31,material('blue',(.05,.38,.65)))]:button(x,.93,z,.030,m,'y')
    # Every console rests on the shelf, including traced handheld silhouettes
    # whose natural outline has a slightly positive minimum height.
    bpy.context.view_layer.update()
    bottom=min((o.matrix_world@v.co).z for o in scene.objects if o.type=='MESH' for v in o.data.vertices)
    for o in scene.objects:
        if o.type=='MESH':o.location.z-=bottom
    bpy.context.view_layer.update()
    return scene

def disc(name,r,inner,z,m,back=False):
    n=32;points=[]
    for radius in [inner,r]:
        points += [(math.cos(i*math.tau/n)*radius,math.sin(i*math.tau/n)*radius,z) for i in range(n)]
    faces=[(i,i+n,(i+1)%n+n,(i+1)%n) for i in range(n)]
    return mesh(name,points,[tuple(reversed(f)) for f in faces] if back else faces,m)

def media_contacts(w,y,z,count=16,height=.10,back=False):
    """Exposed edge fingers, reserved for cartridge/card meshes."""
    gold=material('cartridge contact gold',(.70,.52,.20))
    for i in range(count):
        plate('Edge contact %02d'%i,((i-(count-1)/2)*w/count,y,z),w/count*.72,height,gold,'back' if back else 'z')

def media(key):
    if key=='arcade':
        s=console('arcade')
        s.name='media_arcade'
        for o in s.objects:
            if o.type=='MESH' and o.name.startswith('CRT screen'):o.data.materials[0]=COVER
        return s
    scene=start_scene('media_'+key)
    # Reference variants: Nintendo US shells, Sega first-party shells, Atari
    # standard shells. A system's media is not a generic squarish cartridge.
    # Ratios follow the photographed physical object; normalized display size.
    w,h,d=.92,.95,.15;body=BLACK
    if key in ('psx','segacd'):
        # 120 mm disc, 15 mm hole, 1.2 mm thickness. PS1 data face is black.
        disc('Printed disc label',.69,.15,.007,COVER)
        disc('Unprinted disc rim',.70,.69,.006,SILVER)
        disc('Clear clamping annulus',.15,.0875,.007,LIGHT)
        disc('Disc data face',.70,.0875,-.007,BLACK if key=='psx' else SILVER,back=True)
        for radius in [.70,.0875]:
            pts=[(math.cos(i*math.tau/32)*radius,math.sin(i*math.tau/32)*radius,z) for z in [-.007,.007] for i in range(32)]
            edge_faces=[(i,(i+1)%32,(i+1)%32+32,i+32) for i in range(32)]
            mesh('Optical disc physical edge',pts,[tuple(reversed(f)) for f in edge_faces] if radius<.1 else edge_faces,SILVER)
        return scene
    if key=='psp':
        # UMD's round disc occupies almost the entire white D-shaped caddy.
        # The old rectangular label covering half a small hub was incorrect.
        outline=[(-.60,-.40),(-.55,-.50),(-.35,-.57),(0,-.61),(.35,-.57),(.55,-.50),(.60,-.40),(.59,.15),(.55,.32),(.43,.47),(.25,.56),(0,.60),(-.25,.56),(-.43,.47),(-.55,.32),(-.59,.15)]
        prism('UMD white protective caddy',outline,-.045,.045,WHITE,'xy')
        disc('UMD circular printed game label',.535,.13,.052,COVER)
        disc('UMD clear inner hub',.13,.065,.054,LIGHT)
        cyl('UMD metal magnetic clamp',(0,0,.056),.065,.010,SILVER,'z',16)
        for x in [-.45,.45]:
            plate('UMD locating indentation',(x,-.335,.047),.06,.045,GRAY)
        plate('UMD rear laser aperture',(.18,-.23,-.047),.37,.23,SILVER,'back')
        return scene
    if key=='nes':
        w,h,d=1.0,1.11,.14;body=GRAY
        outline=[(-w/2,-h/2),(w/2,-h/2),(w/2,h/2),(-w/2,h/2)]
    elif key=='snes':
        w,h,d=1.16,.75,.16;body=GRAY
        outline=[(-.58,-.375),(.58,-.375),(.58,.345),(.40,.345),(.40,.375),(-.40,.375),(-.40,.345),(-.58,.345)]
    elif key=='n64':
        w,h,d=1.16,.766,.185;body=GRAY
        outline=[(-.58,-.383),(.58,-.383),(.58,.255),(.46,.300),(.34,.339),(.17,.370),(0,.383),(-.17,.370),(-.34,.339),(-.46,.300),(-.58,.255)]
    elif key=='gb':
        w,h,d=.73,.84,.115;body=GRAY
        # DMG power-switch key at top right; CGB-exclusive shell has none.
        outline=[(-.365,-.42),(.365,-.42),(.365,.31),(.29,.31),(.29,.42),(-.365,.42)]
    elif key=='gbc':
        w,h,d=.73,.84,.115;body=CHAR
        outline=[(-.365,-.42),(.365,-.42),(.365,.34),(.33,.40),(.20,.42),(-.20,.42),(-.33,.40),(-.365,.34)]
    elif key=='gba':
        w,h,d=.96,.56,.144;body=CHAR
        outline=[(-.48,-.28),(.48,-.28),(.48,.19),(.45,.19),(.45,.28),(-.45,.28),(-.45,.19),(-.48,.19)]
    elif key in ('nds','3ds'):
        w,h,d=.66,.70,.076;body=CHAR if key=='nds' else WHITE
        outline=[(-.33,-.35),(.33,-.35),(.33,.27),(.39,.27),(.39,.35),(-.33,.35)] if key=='3ds' else [(-.33,-.35),(.33,-.35),(.33,.29),(.27,.35),(-.33,.35)]
    elif key=='pce':
        w,h,d=.72,1.13,.010;body=WHITE
        outline=[(-.36,-.55),(-.34,-.565),(.34,-.565),(.36,-.55),(.36,.55),(.34,.565),(-.34,.565),(-.36,.55)]
    elif key=='dos':
        w,h,d=.99,1.04,.037;body=BLACK
        outline=[(-.495,-.52),(.495,-.52),(.495,.40),(.38,.52),(-.495,.52)]
    elif key in ('ngp','ngc'):
        w,h,d=.86,.64,.10;body=CHAR
        outline=[(-.43,-.32),(.43,-.32),(.43,.27),(.39,.32),(-.39,.32),(-.43,.27)]
    elif key in ('ws','wsc'):
        w,h,d=1.0,.63,.08;body=CHAR
        outline=[(-.50,-.315),(.50,-.315),(.50,.29),(.475,.315),(-.475,.315),(-.50,.29)]
    elif key=='lynx':
        w,h,d=.80,.88,.035;body=CHAR
        outline=[(-.40,-.42),(-.38,-.44),(.38,-.44),(.40,-.42),(.40,.44),(-.40,.44)]
    elif key=='md':
        w,h,d=1.02,.70,.17
        outline=[(-.51,-.35),(.51,-.35),(.51,.31),(.47,.35),(-.47,.35),(-.51,.31)]
    elif key=='sms':
        w,h,d=1.10,.73,.16
        outline=[(-.55,-.365),(.55,-.365),(.55,.365),(-.55,.365)]
    elif key=='gg':
        w,h,d=.90,.96,.14
        outline=[(-.45,-.48),(.45,-.48),(.45,.37),(.38,.37),(.38,.48),(-.38,.48),(-.38,.37),(-.45,.37)]
    elif key in ('atari2600','atari7800'):
        w,h,d=.82,1.0,.20;body=CHAR
        outline=[(-.41,-.50),(.41,-.50),(.41,.47),(.38,.50),(-.38,.50),(-.41,.47)]
    else:raise ValueError('No photographed media specification for '+key)
    prism(key+' cartridge original silhouette',outline,-d/2,d/2,body,'xy')
    if key=='nes':
        for i in range(7):plate('NES vertical grip channel',(-.435+i*.022,.13,d/2+.003),.008,.77,CHAR)
        plate('NES recessed label bed',(.045,.15,d/2+.003),.60,.77,CHAR)
        plate('NES front label',(.045,.15,d/2+.007),.57,.74,COVER)
        plate('NES lower horizontal mould seam',(0,-.40,d/2+.003),.96,.009,CHAR)
        box('Recessed NES connector opening',(0,-.536,0),(.79,.035,.11),CHAR)
    elif key=='snes':
        # SNS-006 US shell: ribbed wings and recessed lower central panel.
        plate('SNES upper label',(0,.18,d/2+.006),.75,.34,COVER)
        for side in [-1,1]:
            plate('SNES wing seam',(side*.405,-.01,d/2+.003),.008,.70,CHAR)
            for i in range(5):plate('SNES horizontal wing groove',(side*.493,-.26+i*.12,d/2+.004),.16,.008,CHAR)
            cyl('SNES front screw',(side*.49,-.318,d/2+.008),.018,.005,CHAR,'z',8)
        plate('SNES lower recessed panel',(0,-.23,d/2+.002),.75,.25,LIGHT)
        for x in [-.14,.14]:plate('SNES latch mould line',(x,-.19,d/2+.005),.006,.18,GRAY)
    elif key=='n64':
        # NUS-006: wide curved wings, distinctly tall central label.
        plate('N64 central label',(0,-.005,d/2+.006),.56,.63,COVER)
        for side in [-1,1]:
            plate('N64 vertical wing seam',(side*.35,-.055,d/2+.003),.008,.65,CHAR)
            plate('N64 lower grip step',(side*.54,-.235,d/2+.003),.055,.010,LIGHT)
    elif key in ('gb','gbc'):
        plate('GB recessed label bed',(0,-.045,d/2+.002),.56,.55,CHAR)
        plate('GB printed label',(0,-.045,d/2+.006),.52,.51,COVER)
        for side in [-1,1]:
            for i in range(4):plate('GB horizontal grip ridge',(side*.32,.015+i*.07,d/2+.004),.035,.008,LIGHT if key=='gbc' else CHAR)
        plate('GB top moulded title bar',(0,.315,d/2+.004),.48,.035,LIGHT if key=='gbc' else CHAR)
        box('GB bottom connector recess',(0,-.405,0),(.57,.026,.075),CHAR)
    elif key=='gba':
        plate('GBA printed label',(0,-.035,d/2+.006),.69,.35,COVER)
        plate('GBA top embossed title rail',(0,.205,d/2+.004),.72,.018,BLACK)
        for side in [-1,1]:
            plate('GBA recessed side grip',(side*.427,-.015,d/2+.003),.015,.30,BLACK)
        box('GBA connector opening',(0,-.265,0),(.76,.025,.09),BLACK)
        media_contacts(.70,-.235,-d/2-.003,16,.06,True)
    elif key in ('nds','3ds'):
        plate('DS recessed label surround',(0,.01,d/2+.003),.56,.59,GRAY if key=='3ds' else BLACK)
        plate('DS printed label',(0,.01,d/2+.006),.52,.55,COVER)
        # NTR/CTR cards have seventeen rear fingers, not nine SD-card contacts.
        media_contacts(.52,-.245,-d/2-.004,17,.13,True)
        for x in [-.29,.29]:plate('DS side guide',(x,-.04,d/2+.004),.010,.45,GRAY if key=='3ds' else BLACK)
    elif key=='pce':
        # HuCard's gold fingers are on its front TOP, unlike DS/GBA.
        plate('HuCard printed face',(0,-.10,d/2+.004),.65,.83,COVER)
        plate('HuCard contact substrate',(0,.445,d/2+.002),.64,.20,CHAR)
        media_contacts(.61,.45,d/2+.004,38,.16)
    elif key in ('ws','wsc'):
        plate('WonderSwan upper label',(0,.065,d/2+.006),.92,.39,COVER)
        plate('WonderSwan exposed PCB',(0,-.235,d/2+.003),.91,.13,material('card PCB',(.10,.25,.20)))
        media_contacts(.88,-.245,d/2+.006,48,.095)
        plate('WonderSwan moulded top rail',(0,.287,d/2+.003),.55,.017,BLACK)
    elif key=='lynx':
        plate('Lynx printed label',(0,-.015,d/2+.004),.75,.66,COVER)
        plate('Lynx PCB connector strip',(0,-.38,d/2+.002),.74,.10,material('card PCB',(.10,.25,.20)))
        media_contacts(.72,-.38,d/2+.005,34,.08)
        box('Lynx raised extraction lip',(0,.405,.018),(.78,.07,.06),CHAR)
    elif key in ('ngp','ngc'):
        plate('NGP printed label',(0,.035,d/2+.006),.74,.40,COVER)
        for i in range(6):plate('NGP top extraction groove',((i-2.5)*.13,.28,d/2+.004),.075,.009,BLACK)
        plate('NGP lower moulded branding rail',(0,-.24,d/2+.003),.67,.025,BLACK)
        box('NGP connector recess',(0,-.30,0),(.69,.024,.065),BLACK)
    elif key=='md':
        plate('Mega Drive printed label',(0,.06,d/2+.006),.74,.46,COVER)
        for side in [-1,1]:
            for i in range(5):plate('Mega Drive horizontal grip flute',(side*.455,-.12+i*.065,d/2+.004),.06,.009,CHAR)
        box('Mega Drive connector recess',(0,-.336,0),(.78,.025,.12),CHAR)
    elif key=='sms':
        # Standard Sega Master System labels occupy the narrow top strip.
        plate('Master System upper strip label',(0,.235,d/2+.006),1.0,.21,COVER)
        for i in range(4):plate('Master System full width moulded groove',(0,.075-i*.045,d/2+.003),1.03,.008,CHAR)
        box('Master System connector recess',(0,-.35,0),(.83,.025,.11),CHAR)
    elif key=='gg':
        plate('Game Gear printed label',(0,-.015,d/2+.006),.73,.66,COVER)
        plate('Game Gear upper grip',(0,.42,d/2+.004),.65,.016,CHAR)
        box('Game Gear connector recess',(0,-.46,0),(.70,.027,.09),CHAR)
    elif key in ('atari2600','atari7800'):
        plate('Atari front printed label',(0,.045,d/2+.006),.71,.75,COVER)
        # Standard Atari-family cartridge, taller than wide, thick end cap.
        box('Atari insertion end',(0,-.455,0),(.78,.09,d+.015),BLACK)
        plate('Atari top label fold',(0,.502,0),.71,d*.75,CHAR,'y')
        for i in range(3):plate('Atari extraction rib',(0,.39+i*.027,-d/2-.003),.73,.008,BLACK,'back')
    elif key=='dos':
        plate('Metal shutter',(-.06,.31,d/2+.005),.53,.36,SILVER)
        plate('Shutter opening',(.07,.33,d/2+.009),.09,.22,BLACK)
        plate('Floppy label',(0,-.20,d/2+.006),.76,.47,COVER)
        for x in [-.40,.40]:plate('Floppy write protect / density window',(x,-.40,d/2+.007),.055,.065,CHAR)
        cyl('Floppy rear metal spindle',(0,-.03,-d/2-.004),.125,.008,SILVER,'z',16)
        plate('Floppy rear spindle slot',(.04,-.035,-d/2-.010),.045,.07,BLACK,'back')
    return scene

def uv_for(v,n,m,bounds):
    lo,hi=bounds
    # Planar mapping in original local axes. Sloped faces retain a rectangular decal.
    axes=sorted(range(3),key=lambda i:abs(n[i]))[:2]
    if abs(n[2])>.5:axes=[0,1]
    elif abs(n[1])>.5:axes=[0,2]
    u=(v[axes[0]]-lo[axes[0]])/max(.0001,hi[axes[0]]-lo[axes[0]])
    vv=(v[axes[1]]-lo[axes[1]])/max(.0001,hi[axes[1]]-lo[axes[1]])
    if abs(n[2])>.5:vv=1-vv
    if m.name.startswith('cover'):return (u,vv)
    tile=int(m['tile'])
    # First generated 4x4 sheet on the left, second 4x4 sheet on the right.
    col=tile%4+(4 if tile>=16 else 0);row=(tile%16)//4
    # Flat colours sample the clean interior of the white plastic tile, avoiding borders.
    if tile==0:return (.06,.12)
    # Wide wordmarks occupy a narrow strip in their source square. Use its actual
    # bounds so the lettering does not collapse into a hairline on a small plate.
    crops={1:(.03,.35,.97,.70),2:(.025,.35,.975,.57),8:(.025,.30,.975,.70),9:(.025,.30,.975,.72),10:(.04,.30,.96,.71)}
    if tile in crops:
        left,top,right,bottom=crops[tile]
        return ((col+left+(right-left)*u)/8,(row+top+(bottom-top)*vv)/4)
    return ((col+.025+.95*u)/8,(row+.025+.95*vv)/4)

def export(scene,key,media=False):
    groups={};allverts=[]
    for o in scene.objects:
        if o.type!='MESH':continue
        me=o.data;me.calc_loop_triangles();m=me.materials[0]
        if o.name.startswith('N64 recessed cooling slit'):
            assert all(p.normal.z>0 for p in me.polygons),'N64 grille must face upward'
        if o.name.startswith('Mega Drive DE-9 socket'):
            assert all(p.normal.y<0 for p in me.polygons),'DE-9 socket must face forward'
        # Group by shader inputs, not material name: different atlas decals share a draw call.
        ident=(m.name.startswith('cover'),tuple(m.diffuse_color))
        if ident not in groups:groups[ident]=[]
        eng=[(p.co.x,p.co.z,-p.co.y) for p in me.vertices]
        lo=[min(p[i] for p in eng) for i in range(3)];hi=[max(p[i] for p in eng) for i in range(3)]
        for old in list(me.uv_layers):me.uv_layers.remove(old)
        layer=me.uv_layers.new(name='Hardware UV');layer.active_render=True
        for polygon in me.polygons:
            nn=polygon.normal;n=(nn.x,nn.z,-nn.y)
            for loop in polygon.loop_indices:
                u,v=uv_for(eng[me.loops[loop].vertex_index],n,m,(lo,hi));layer.data[loop].uv=(u,1-v)
        for tri in me.loop_triangles:
            nn=tri.normal;local_n=(nn.x,nn.z,-nn.y)
            nn=o.matrix_world.to_3x3()@nn;nn.normalize();normal=(nn.x,nn.z,-nn.y)
            for idx in tri.vertices:
                p=o.matrix_world@me.vertices[idx].co;v=(p.x,p.z,-p.y)
                groups[ident].append((*v,*normal,*uv_for(eng[idx],local_n,m,(lo,hi))));allverts.append(v)
    ordered=sorted(groups,key=lambda ident:(ident[0],ident[1]))
    data=bytearray(struct.pack('<II',0x3244334c,len(ordered)))
    for ident in ordered:
        vs=groups[ident];data+=struct.pack('<7fI',*ident[1],8,.025,0,len(vs))
        for v in vs:data+=struct.pack('<8f',*v)
    tris=len(allverts)//3;limit=800 if 'arcade' in key else 600
    assert tris<=limit,(key,tris,limit)
    assert len(ordered)<=8,(key,len(ordered))
    path=OUT/(key+'.l3d');path.write_bytes(data)
    return dict(key=key,triangles=tris,parts=len(ordered),bytes=len(data),sha256=hashlib.sha256(data).hexdigest(),bounds=[[min(v[i] for v in allverts) for i in range(3)],[max(v[i] for v in allverts) for i in range(3)]],cover_part=len(ordered)-1 if media else None)

# Keep the reproducible binary export and four labelled Blender contact sheets.
tail=PREVIOUS.read_text(encoding='utf-8').split('OUT.mkdir(',1)[1]
tail='OUT.mkdir('+tail
tail=tail.replace("generator='tools/art/build_lowpoly_hardware.py'","generator='tools/art/build_faithful_hardware.py'")
tail=tail.replace('assets=rows','atlas_grid=[8,4],assets=rows')
tail=tail.replace('location=(6,-10,12)', 'location=(10,-14,10)').replace('1600,8','650,8').replace('900,6','350,6')
if '--no-sheets' in sys.argv:
    tail=tail.replace('bpy.ops.render.render(write_still=True)','None')
exec(compile(tail,str(PREVIOUS),'exec'))
