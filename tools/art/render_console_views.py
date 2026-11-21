"""Read-only Blender source inspection: front and three-quarter views per console.
Blender -b --python tools/art/render_console_views.py -- [optional console keys]
No photographs or game covers are baked into these previews.
"""
import bpy,sys,json
from pathlib import Path
from mathutils import Vector
root=Path(__file__).resolve().parents[2]
bpy.ops.wm.open_mainfile(filepath=str(root/'art-source/faithful-hardware/hardware-library.blend'))
manifest=json.loads((root/'art-source/faithful-hardware/manifest.json').read_text(encoding='utf-8'))
keys=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [r['key'] for r in manifest['assets'] if not r['key'].startswith('media_')]
out=root/'art-source/faithful-hardware/inspection';out.mkdir(exist_ok=True)
for key in keys:
    scene=bpy.data.scenes[key];bpy.context.window.scene=scene
    points=[o.matrix_world@v.co for o in scene.objects if o.type=='MESH' for v in o.data.vertices]
    low=Vector([min(p[i] for p in points) for i in range(3)])
    high=Vector([max(p[i] for p in points) for i in range(3)])
    center=(low+high)*.5;span=max(high-low)
    world=bpy.data.worlds.new(key+' inspection');world.use_nodes=True
    world.node_tree.nodes['Background'].inputs[0].default_value=(.12,.14,.18,1)
    world.node_tree.nodes['Background'].inputs[1].default_value=.7;scene.world=world
    for delta,power in [((-2,-3,4),180),((2,-2,2),100)]:
        bpy.ops.object.light_add(type='AREA',location=center+Vector(delta));o=bpy.context.object
        o.data.energy=power;o.data.size=4;o.rotation_euler=(center-o.location).to_track_quat('-Z','Y').to_euler()
    bpy.ops.object.camera_add();cam=bpy.context.object;cam.data.type='ORTHO';scene.camera=cam
    scene.render.engine='CYCLES';scene.cycles.samples=12;scene.cycles.use_denoising=True
    scene.render.resolution_x=768;scene.render.resolution_y=768;scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGB'
    scene.view_settings.view_transform='Standard'
    for view,delta in [('front',(0,-4,0)),('three-quarter',(2,-4,2.5))]:
        cam.location=center+Vector(delta);cam.rotation_euler=(center-cam.location).to_track_quat('-Z','Y').to_euler()
        cam.data.ortho_scale=span*(1.18 if view=='front' else 1.5)
        scene.render.filepath=str(out/(key+'-'+view+'.png'))
        bpy.ops.render.render(write_still=True)
        print('INSPECTED',key,view,flush=True)
