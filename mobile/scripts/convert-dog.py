"""Run with Blender: --background --python convert-dog.py -- <download-dir> <output.glb>."""

import argparse
import json
import sys
from pathlib import Path
from tempfile import TemporaryDirectory

import bpy
from mathutils import Matrix

CLIPS = {
    'Idle_1': 'Idle', 'Idle_2': 'Idle2', 'Idle_3': 'Idle3', 'Idle_4': 'Idle4',
    'Idle_5_start': 'Idle5Start', 'Idle_5_loop': 'Idle5Loop', 'Idle_5_end': 'Idle5End',
    'Idle_6': 'Idle6', 'Idle_7': 'Idle7',
    'Walk_F_IP': 'Walk', 'Walk_B_IP': 'WalkBack', 'Walk_L_IP': 'WalkLeft',
    'Walk_R_IP': 'WalkRight', 'Walk_BL_IP': 'WalkBackLeft', 'Walk_BR_IP': 'WalkBackRight',
    'Trot_F_IP': 'Trot', 'Trot_L_IP': 'TrotLeft', 'Trot_R_IP': 'TrotRight',
    'Run_F_IP': 'Gallop', 'Run_L_IP': 'GallopLeft', 'Run_R_IP': 'GallopRight',
    'RunFast_F_IP': 'RunFast', 'RunFast_L_IP': 'RunFastLeft', 'RunFast_R_IP': 'RunFastRight',
    'Turn_L_IP': 'TurnLeft', 'Turn_R_IP': 'TurnRight',
    'Turn_L180_IP': 'TurnLeft180', 'Turn_R180_IP': 'TurnRight180',
    'Bark': 'Bark',
    'Sitting_start': 'SitStart', 'Sitting_loop_1': 'SitIdle',
    'Sitting_loop_2': 'SitIdle2', 'Sitting_end': 'SitEnd',
    'Lie_start': 'LieStart', 'Lie_loop_1': 'LieIdle', 'Lie_loop_2': 'LieIdle2', 'Lie_end': 'LieEnd',
    'Lie_belly_start': 'BellyLieStart', 'Lie_belly_loop_1': 'BellyLieIdle',
    'Lie_belly_loop_2': 'BellyLieIdle2', 'Lie_belly_end': 'BellyLieEnd',
    'Jump_Place_IP': 'JumpPlace', 'Jump_F_IP': 'JumpForward', 'Jump_Run_IP': 'JumpRun',
    'JumpStart_Place': 'JumpStartPlace', 'JumpStart_Up': 'JumpStartUp',
    'JumpStart_F_IP': 'JumpStartForward', 'JumpStart_Down_IP': 'JumpStartDown',
    'JumpAir_Up': 'JumpAirUp', 'JumpAir_Up_F': 'JumpAirUpForward',
    'JumpAir_high': 'JumpAirHigh', 'JumpAir_high_F': 'JumpAirHighForward',
    'JumpAir_Horiz': 'JumpAirHorizontal', 'JumpAir_low': 'JumpAirLow',
    'JumpAir_low_F': 'JumpAirLowForward', 'JumpLand': 'JumpLand',
    'JumpLand_F_IP': 'JumpLandForward', 'JumpLand_Place': 'JumpLandPlace',
    'JumpUp_End_IP': 'JumpUpEnd',
    'EatDrink_start': 'EatDrinkStart', 'Eat_loop': 'Eat', 'Eat_tear': 'EatTear',
    'Drink_loop': 'Drink', 'EatDrink_end': 'EatDrinkEnd',
    'Digging_start': 'DigStart', 'Digging_loop': 'DigLoop', 'Digging_end': 'DigEnd',
    'Scratching': 'ScratchEar', 'Pissing': 'Pee', 'Defecate': 'Poop',
    'Crouch_Idle_start': 'CrouchStart', 'Crouch_Idle_loop_1': 'CrouchIdle',
    'Crouch_Idle_loop_2': 'CrouchIdle2', 'Crouch_Idle_end': 'CrouchEnd',
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:])
    source = args.source.resolve()
    output = args.output.resolve()

    bpy.ops.wm.open_mainfile(
        filepath=str(source / 'Cartoon_ShibaInu_Blend/Cartoon_ShibaInu_anim_IP.blend'),
        load_ui=False,
    )
    rig = next(obj for obj in bpy.context.scene.objects if obj.type == 'ARMATURE')
    rig.animation_data.action = None
    for track in list(rig.animation_data.nla_tracks):
        rig.animation_data.nla_tracks.remove(track)
    for bone in rig.pose.bones:
        bone.matrix_basis = Matrix.Identity(4)
    for obj in list(bpy.context.scene.objects):
        if obj != rig:
            bpy.data.objects.remove(obj, do_unlink=True)

    actions = {}
    for original, runtime in CLIPS.items():
        action = bpy.data.actions[original]
        action.name = runtime
        action.use_fake_user = True
        actions[runtime] = action
    for action in list(bpy.data.actions):
        if action not in actions.values():
            bpy.data.actions.remove(action, do_unlink=True)

    original_objects = set(bpy.data.objects)
    bpy.ops.import_scene.fbx(filepath=str(source / 'Cartoon_ShibaInu_FBX/Cartoon_ShibaInu_LowPoly.fbx'))
    imported = set(bpy.data.objects) - original_objects
    mesh = next(obj for obj in imported if obj.type == 'MESH')
    assert set(mesh.vertex_groups.keys()) <= set(rig.data.bones.keys())
    # Bake FBX's object axis conversion into bind-space vertices, then use the
    # original Blender animation rig. Their bone positions and weight names match.
    mesh.data.transform(mesh.matrix_world.copy())
    mesh.parent = rig
    mesh.matrix_parent_inverse = Matrix.Identity(4)
    mesh.matrix_basis = Matrix.Identity(4)
    for modifier in mesh.modifiers:
        if modifier.type == 'ARMATURE':
            modifier.object = rig
    for obj in imported:
        if obj != mesh:
            bpy.data.objects.remove(obj, do_unlink=True)
    mesh.name = 'ShibaInuMobile'

    texture_dir = source / 'Cartoon_ShibaInu_Texture/texture'
    temporary_textures = TemporaryDirectory(prefix='usee-dog-')
    material = bpy.data.materials.new('ShibaInuMobilePBR')
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    shader = nodes.get('Principled BSDF')
    shader.inputs['Metallic'].default_value = 0
    for filename, input_name, normal in [
        ('Cartoon_ShibaInu_Albedo1.png', 'Base Color', False),
        ('Cartoon_ShibaInu_Roughness.png', 'Roughness', False),
        ('Cartoon_ShibaInu_Normal.png', 'Normal', True),
    ]:
        image = bpy.data.images.load(str(texture_dir / filename), check_existing=False)
        if input_name != 'Base Color':
            image.colorspace_settings.name = 'Non-Color'
        image.scale(1024, 1024)
        # Save resized pixels first: packing an untouched file can reuse its
        # original 2048px bytes even after scaling the image datablock.
        image.filepath_raw = str(Path(temporary_textures.name) / filename)
        image.file_format = 'PNG'
        image.save()
        image.pack()
        texture = nodes.new('ShaderNodeTexImage')
        texture.image = image
        if normal:
            normal_node = nodes.new('ShaderNodeNormalMap')
            links.new(texture.outputs['Color'], normal_node.inputs['Color'])
            links.new(normal_node.outputs['Normal'], shader.inputs['Normal'])
        else:
            links.new(texture.outputs['Color'], shader.inputs[input_name])
    mesh.data.materials.clear()
    mesh.data.materials.append(material)
    for polygon in mesh.data.polygons:
        polygon.material_index = 0
        polygon.use_smooth = True

    rig.animation_data.action = actions['Idle']
    bpy.context.scene.frame_set(0)
    bpy.context.view_layer.update()
    bpy.ops.object.select_all(action='DESELECT')
    mesh.select_set(True)
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    output.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=str(output), export_format='GLB', use_selection=True,
        export_animations=True, export_animation_mode='ACTIONS',
        export_anim_single_armature=True, export_frame_range=False,
        export_force_sampling=True, export_yup=True,
        export_cameras=False, export_lights=False,
    )
    print('MODEL_EXPORT', json.dumps(dict(
        output=str(output), triangles=sum(len(p.vertices) - 2 for p in mesh.data.polygons),
        bones=len(rig.data.bones), animations=list(actions), bytes=output.stat().st_size,
    )))
    output.with_suffix('.animations.json').write_text(json.dumps({
        name: {'source': original, 'seconds': float((actions[name].frame_range[1] - actions[name].frame_range[0]) / bpy.context.scene.render.fps)}
        for original, name in CLIPS.items()
    }, indent=2), encoding='utf-8')
    temporary_textures.cleanup()


if __name__ == '__main__':
    main()
