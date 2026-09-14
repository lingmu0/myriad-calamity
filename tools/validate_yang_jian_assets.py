"""Check approved geometry, UVs, rig channels and combat/animation timing without launching Minecraft."""
from pathlib import Path
import hashlib
import json
import math
import re
import struct

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/myriad_calamity'


def main():
    original=(ROOT/'modeling/yang_jian_v3.bbmodel').read_bytes()
    manifest=json.loads((ROOT/'modeling/yang_jian-runtime-manifest.json').read_text('utf8'))
    assert manifest['source_sha256']==hashlib.sha256(original).hexdigest(), 'Model changed after export'
    model=json.loads(original)
    mesh=json.loads((ASSETS/'mesh/yang_jian.json').read_text('utf8'))
    png=(ASSETS/'textures/entity/yang_jian.png').read_bytes()
    assert png[:8]==b'\x89PNG\r\n\x1a\n'
    assert list(struct.unpack('>II',png[16:24]))==mesh['texture_size']==[2048,2048]
    names=set();faces=0

    def walk(nodes):
        nonlocal faces
        for b in nodes:
            assert b['name'] not in names
            names.add(b['name'])
            assert all(math.isfinite(v) for v in b['pivot']+b['rotation'])
            for f in b['faces']:
                faces+=1
                assert len(f['v'])==4 and abs(sum(v*v for v in f['n'])-1)<1e-4
                for v in f['v']:
                    assert len(v)==5 and all(math.isfinite(x) for x in v)
                    assert 0<=v[3]<=1 and 0<=v[4]<=1
            walk(b['children'])
    walk(mesh['bones'])
    assert names=={g['name'] for g in model['groups']}
    assert faces==manifest['rendered_faces']==len(model['elements'])*6
    runtime=json.loads((ASSETS/'animations/yang_jian.json').read_text('utf8'))['clips']
    authored=json.loads((ROOT/'modeling/yang_jian-animation-source.json').read_text('utf8'))['clips']
    required={'idle','walk','combo','four_combo','six_combo','throw','throw_followup','recall','thrust_windup','thrust',
              'summon_hound','coordinated','guard','counter_windup','counter','phase_clear','death',
              'transition','axe_summon','axe_slam','axe_combo','flying_swords','draw_slash','whip_sweep','whip_spin',
              'lightning_thrust','invisible_dash','delayed_combo','p2_idle_axe','p2_idle_sword','p2_idle_whip',
              'p3_idle','third_eye_open','eye_beam','sweep_beam','tracking_beam','myriad_swords','sword_rain',
              'red_thunder','divine_sweep','divine_spin','aerial_combo','divine_judgement','p3_death',
              'step_approach','step_approach_axe','step_approach_sword','step_approach_whip'}
    assert required<=runtime.keys()
    for name,clip in runtime.items():
        assert clip['length']>0 and names==clip['bones'].keys(), (name,'Every articulated bone must have a pose')
        for bone,channels in clip['bones'].items():
            for channel,keys in channels.items():
                assert channel in {'rotation','position','scale'}
                assert all(len(k)==4 and all(math.isfinite(v) for v in k) for k in keys)
                assert all(a[0]<b[0] for a,b in zip(keys,keys[1:])),(name,bone,channel)
                assert keys[0][0]==0 and abs(keys[-1][0]-clip['length'])<1e-6
                if clip['loop']:
                    assert max(abs(a-b) for a,b in zip(keys[0][1:],keys[-1][1:]))<1e-4,(name,bone,'loop seam')
    for suffix in ('','_axe','_sword','_whip'):
        name='step_approach'+suffix
        clip=runtime[name]
        assert not clip['loop'] and clip['length']==.5,(name,'Advancing step must finish in ten ticks')
        assert authored[name]['timing']=={'prepare_ticks':[0,0],'travel_ticks':[0,8],'settle_ticks':[8,10],'offhand_throw_tick':0}
        assert all(k[1]==0 and k[3]==0 for k in clip['bones']['root']['position']), 'Server owns horizontal step motion'
        flick={round(k['time']*20,1):k['values'] for k in authored[name]['bones']['left_hand']['rotation']}
        assert flick[0][0]==75 and flick[2][0]==91,'Offhand wrist must release on the first step tick'
        for bone in ('hips','waist','chest','left_thigh','right_thigh','left_shin','right_shin','left_foot','right_foot'):
            keys=clip['bones'][bone]['rotation']
            assert max(k[1] for k in keys)-min(k[1] for k in keys)>1,(name,bone,'Must articulate the whole step')
    transition=authored['transition']
    transition_java=(ROOT/'src/main/java/net/xuwu/myriadcalamity/entity/YangJianTransition.java').read_text('utf8')
    transition_ticks={name:int(re.search(rf'\b{name}\s*=\s*(\d+)',transition_java)[1]) for name in
        ('ASCEND_END','SUMMON_START','SUMMON_END','SWEEP_START','SWEEP_END','IMPACT','WAVE_START','WAVE_END','DURATION')}
    assert transition['length']*20==transition_ticks['DURATION']==144,'Transition duration must match the server'
    assert transition['timing']=={'ascent_ticks':[0,transition_ticks['ASCEND_END']],
        'summon_ticks':[transition_ticks['SUMMON_START'],transition_ticks['SUMMON_END']],
        'sweep_ticks':[transition_ticks['SWEEP_START'],transition_ticks['SWEEP_END']],
        'sweep_arc_degrees':180,'hit_ticks':[transition_ticks['IMPACT']],
        'shockwave_ticks':[transition_ticks['WAVE_START'],transition_ticks['WAVE_END']],
        'recovery_end_tick':transition_ticks['DURATION'],'server_owns_flight':True}
    transition_root=runtime['transition']['bones']['root']
    assert all(abs(k[2])<2 and k[1]==0 and k[3]==0 for k in transition_root['position']), 'Do not animate the server flight twice'
    yaw={round(k['time']*20):k['values'][1] for k in transition['bones']['root']['rotation']}
    assert yaw[40]==-90 and yaw[64]==90 and yaw[72]==0,'Axe must sweep a half-circle before the forward slam'
    for bone in ('hips','waist','chest','right_arm','right_forearm','right_hand','left_thigh','right_shin','hair_back_03','rear_sash_tip'):
        keys=runtime['transition']['bones'][bone]['rotation']
        assert any(max(k[axis] for k in keys)-min(k[axis] for k in keys)>2 for axis in (1,2,3)),(bone,'Transition joint is static')
    java=(ROOT/'src/main/java/net/xuwu/myriadcalamity/entity/YangJianSkill.java').read_text('utf8')
    for name in ['COMBO','FOUR_COMBO','SIX_COMBO','AXE_COMBO','DELAYED_COMBO']:
        pattern=rf'{name}\(\d+,\s*\d+,\s*new int\[\]\{{([\d,]+)\}},\s*(\d+),\s*(\d+),\s*(\d+)'
        match=re.search(pattern,java)
        assert match,name
        windups=list(map(int,match[1].split(',')));active,gap,recovery=map(int,match.group(2,3,4))
        starts=[];hits=[];tick=0
        for w in windups:starts.append(tick);hits.append(tick+w);tick+=w+active+gap
        meta=authored[name.lower()]['timing']
        assert meta['step_start_ticks']==starts and meta['hit_ticks']==hits,(name,'server timing changed')
        assert abs(runtime[name.lower()]['length']*20-(tick-gap+recovery))<1e-5
    for name in ['AXE_SUMMON','AXE_SLAM','FLYING_SWORDS','DRAW_SLASH','WHIP_SWEEP','WHIP_SPIN','LIGHTNING_THRUST','INVISIBLE_DASH']:
        pattern=rf'{name}\(\d+,\s*\d+,\s*new int\[\]\{{([\d,]+)\}},\s*(\d+),\s*(\d+),\s*(\d+)'
        match=re.search(pattern,java);assert match,name
        w=int(match[1]);active,gap,recovery=map(int,match.group(2,3,4))
        clip=runtime[name.lower()]
        assert abs(clip['length']*20-(w+active+recovery))<1e-5,(name,'duration')
        meta=authored[name.lower()]['timing']
        if meta:assert meta['hit_ticks']==[w] and meta['active_ticks']==active,(name,'hit')
    weapons=json.loads((ASSETS/'mesh/yang_jian_weapons.json').read_text('utf8'))
    for name in ['THIRD_EYE_OPEN','EYE_BEAM','SWEEP_BEAM','TRACKING_BEAM','MYRIAD_SWORDS','SWORD_RAIN',
                 'RED_THUNDER','DIVINE_SWEEP','AERIAL_COMBO','DIVINE_JUDGEMENT']:
        match=re.search(rf'{name}\(\d+,\s*\d+,\s*new int\[\]\{{(\d+)\}},\s*(\d+),\s*(\d+),\s*(\d+)',java)
        assert match,name
        w,active,gap,recovery=map(int,match.groups())
        assert abs(runtime[name.lower()]['length']*20-(w+active+recovery))<1e-5,(name,'P3 clip duration')
        meta=authored[name.lower()]['timing']
        if 'hit_ticks' in meta:assert meta['hit_ticks']==[w] and meta['active_ticks']==active,(name,'P3 hit frame')
    assert runtime['divine_spin']['length']==runtime['divine_sweep']['length']
    assert authored['myriad_swords']['timing']['impact_ticks']==[48,78,108]
    assert authored['aerial_combo']['timing']['impact_ticks']==[42,76,130,162]
    assert authored['divine_judgement']['timing']['impact_ticks']==[76,116,154,204,252,288]
    assert weapons['texture']=='textures/entity/yang_jian.png' and weapons['texture_size']==[2048,2048]
    assert set(weapons['weapons'])=={'axe','sword','whip'}
    for name,weapon in weapons['weapons'].items():
        assert weapon['cubes']>=20 and len(weapon['faces'])==weapon['cubes']*6
        for face in weapon['faces']:
            assert len(face['v'])==4 and abs(sum(v*v for v in face['n'])-1)<1e-4
            for v in face['v']:
                assert len(v)==5 and all(math.isfinite(x) for x in v)
                assert 0<=v[3]<=1 and 0<=v[4]<=1
    p2_path=ROOT/'modeling/yang_jian_p2.bbmodel'
    if p2_path.exists():
        p2=json.loads(p2_path.read_text('utf8'))
        approved={e['uuid']:e for e in model['elements']}
        p2_elements={e['uuid']:e for e in p2['elements']}
        assert all(p2_elements[key]==element for key,element in approved.items()),'Approved body changed in P2 project'
        assert len(p2['elements'])==len(model['elements'])+sum(w['cubes'] for w in weapons['weapons'].values())
        p2_groups={g['uuid'] for g in p2['groups']}
        for animation in p2['animations']:assert set(animation['animators'])<=p2_groups
    p3=json.loads((ROOT/'modeling/yang_jian_p3.bbmodel').read_text('utf8'))
    p3_elements={e['uuid']:e for e in p3['elements']}
    assert all(p3_elements[e['uuid']]==e for e in model['elements']),'Approved body changed in P3 project'
    assert len(p3['elements'])==len(model['elements'])+sum(w['cubes'] for w in weapons['weapons'].values())
    assert len(p3['animations'])==len(runtime),'All P3 animations must remain editable in Blockbench'
    p3_groups={g['uuid'] for g in p3['groups']}
    for animation in p3['animations']:assert set(animation['animators'])<=p3_groups
    for project in (p2,p3):
        transition_anim=next(a for a in project['animations'] if a['name']=='animation.transition')
        assert transition_anim['length']==7.2
        axe_animator=next(a for a in transition_anim['animators'].values() if a['name']=='weapon_axe')
        scales=[(k['time'],float(k['data_points'][0]['x'])) for k in axe_animator['keyframes']]
        assert scales==[(0,0),(1.2,0),(2.0,3),(6.3,3),(7.2,1)],'Editable giant axe must match runtime scale timing'
    assert len(png)>1000
    from check_yang_jian_transition_contact import validate as validate_transition_contact
    validate_transition_contact()
    print(f'Yang Jian: approved model hash verified; {len(names)} bones, {faces} faces, {len(runtime)} clips; UVs, loop seams and server hit frames valid.')


if __name__=='__main__':main()
