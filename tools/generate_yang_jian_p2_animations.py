"""P2 animation authoring, appended by generate_yang_jian_animations.py.

The P1 generator owns the resource write; importing this module never overwrites
files. Full clips use the exact YangJianSkill windup/active/recovery timeline.
"""
import copy

from generate_yang_jian_animations import CARRY_ANGLE, thrust_poses

import math


def append_p2(pose, finish, attack_pose):
    def frame(t,p,y=0):return (t,p,[0,y,0])

    def axe(loaded=False):
        return pose(hips=[-3 if loaded else 13,0,0],waist=[-7 if loaded else 14,0,0],
            chest=[-12 if loaded else 20,0,0],neck=[7 if loaded else -11,0,0],head=[5 if loaded else -10,0,0],
            right_arm=[130 if loaded else 55,-8,-12],right_forearm=[-40 if loaded else -17,0,13],
            right_hand=[-105 if loaded else -125,0,0],left_arm=[102 if loaded else 70,20,45],
            left_forearm=[-55 if loaded else -35,-15,28],left_hand=[-22,0,-14],
            left_thigh=[-13 if loaded else 30,0,-6],right_thigh=[15 if loaded else -21,0,6],
            left_shin=[13 if loaded else -23,0,0],right_shin=[-12 if loaded else 22,0,0])

    def broad(loaded=False,side=1,snap=False,follow=False):
        p=attack_pose('horizontal',loaded,follow=follow,side=side,snap=snap)
        p['left_arm']=[75 if not follow else 56,side*12,34]
        p['left_forearm']=[-42,0,22]
        p['right_hand']=[-104 if loaded else -120 if follow else -138,side*(16 if follow else 12),side*14]
        return p

    def sequence(name,windups,active,gap,recovery,kinds):
        frames=[];starts=[];hits=[];tick=0
        for i,(w,kind) in enumerate(zip(windups,kinds)):
            starts.append(tick);hits.append(tick+w)
            side = 1 if i%2==0 else -1
            if kind=='overhead':
                load,snapped,hit,follow=axe(True),attack_pose('overhead',snap=True),axe(),attack_pose('overhead',follow=True)
            elif kind=='upward':
                load,snapped,hit,follow=attack_pose('upward',True),attack_pose('upward',snap=True),attack_pose('upward'),attack_pose('upward',follow=True)
            else:
                load,snapped,hit,follow=broad(True,side),broad(False,side,snap=True),broad(False,side),broad(False,side,follow=True)
            # The delayed final strike holds its loaded pose, avoiding an evenly paced combo.
            held=tick+w*.46 if name=='delayed_combo' and i==2 else tick+w*.64
            frames += [frame(tick,pose(),-.15),frame(held,load,-.82),frame(tick+w-1.4,snapped,-.88),
                       frame(tick+w,hit,-.72),frame(tick+w+active-.2,follow,-.42)]
            tick+=w+active+gap
        duration=tick-gap+recovery
        frames.append(frame(duration,pose()))
        finish(name,frames,duration,metadata={'step_start_ticks':starts,'hit_ticks':hits,'windup_ticks':windups,'active_ticks':active,'gap_ticks':gap})

    # Phase two retains a restrained martial stance, with visibly different carries.
    for weapon in ('axe','sword','whip'):
        frames=[]
        for tick in range(0,81,4):
            wave=math.sin(tick*math.pi/40)
            p=pose(chest=[wave*.7,0,0],head=[-wave*.3,0,0])
            # A carried weapon clears the legs by flexing the elbow forward, never by splaying the
            # forearm outward, so every carry keeps the elbow's sideways channel at zero.
            if weapon=='axe':
                p['right_arm']=[11,-8,8];p['right_forearm']=[-20,0,-CARRY_ANGLE];p['left_arm']=[13,0,-5]
            elif weapon=='sword':
                p['right_arm']=[16,-11,8];p['right_forearm']=[-16,0,-CARRY_ANGLE];p['right_hand']=[-23,0,0]
            else:
                p['right_arm']=[24,-9,8];p['right_forearm']=[-26,0,-CARRY_ANGLE];p['right_hand']=[-5,0,0]
            frames.append(frame(tick,p,wave*.18))
        finish('p2_idle_'+weapon,frames,80,True)

    # The server supplies the eight-block ascent/descent. These root offsets are
    # only sub-pixel weight shifts, so the body and summoned axe stay registered
    # to the authoritative impact. Hair, plates and cloth inherit delayed motion.
    launch=pose(hips=[10,0,0],waist=[7,0,0],chest=[9,0,0],head=[-12,0,0],
        right_arm=[26,-8,-14],right_forearm=[-22,0,14],left_arm=[32,5,24],left_forearm=[-30,0,-19],
        left_thigh=[31,0,-7],right_thigh=[23,0,7],left_shin=[-50,0,0],right_shin=[-43,0,0])
    rise=pose(hips=[-3,0,0],waist=[-4,0,0],chest=[-8,0,0],head=[-12,0,0],
        right_arm=[85,-12,-17],right_forearm=[-39,0,20],right_hand=[-67,0,0],
        left_arm=[65,12,39],left_forearm=[-23,0,-17],left_hand=[-35,0,12],
        left_thigh=[36,0,-9],right_thigh=[20,0,8],left_shin=[-62,0,0],right_shin=[-51,0,0])
    call=pose(hips=[-4,0,0],waist=[-6,0,0],chest=[-10,0,0],neck=[5,0,0],head=[-12,0,0],
        right_arm=[139,-8,-14],right_forearm=[-35,0,-CARRY_ANGLE],right_hand=[-103,0,0],
        left_arm=[106,14,37],left_forearm=[-29,0,CARRY_ANGLE],left_hand=[-45,-12,13],
        left_thigh=[37,0,-8],right_thigh=[26,0,8],left_shin=[-66,0,0],right_shin=[-55,0,0])
    loaded=axe(True)
    loaded['left_thigh']=[36,0,-10];loaded['right_thigh']=[27,0,9]
    loaded['left_shin']=[-65,0,0];loaded['right_shin']=[-56,0,0]
    loaded['left_foot']=[-13,0,0];loaded['right_foot']=[-10,0,0]
    # The summoned axe is heavy, so it is carried two-handed: the left hand grips the shaft just
    # above the right hand for the whole spin and the dive, and only the slam releases it. Both arms
    # were solved against the rig (see tools/check_yang_jian_transition_grip.py) with the grip kept
    # in front of the chest; the handle then leans up and outward with the head above the shoulder.
    # The elbow stays a hinge (sideways channel zero) and only flexes and pronates.
    TWO_HANDED_GRIP={

    32:{"left_arm":[94.82, -15.48, 28.52],"left_forearm":[-101.64, 2.44, 18.0],"left_hand":[-52.94, -24.07, -23.74],

        "right_arm":[80.42, 33.15, -75.07],"right_forearm":[-92.88, -0.02, -18.0],"right_hand":[-12.19, 13.02, -7.13]},

    40:{"left_arm":[94.82, -15.47, 28.52],"left_forearm":[-101.63, 2.43, 18.0],"left_hand":[-52.94, -24.07, -23.75],

        "right_arm":[80.42, 33.15, -75.07],"right_forearm":[-92.86, -0.02, -18.0],"right_hand":[-12.2, 13.02, -7.14]},

    46:{"left_arm":[94.82, -15.47, 28.52],"left_forearm":[-101.63, 2.43, 18.0],"left_hand":[-52.93, -24.07, -23.76],

        "right_arm":[80.42, 33.15, -75.07],"right_forearm":[-92.86, -0.02, -18.0],"right_hand":[-12.2, 13.02, -7.14]},

    52:{"left_arm":[94.82, -15.48, 28.52],"left_forearm":[-101.64, 2.42, 18.0],"left_hand":[-52.93, -24.05, -23.76],

        "right_arm":[80.42, 33.15, -75.06],"right_forearm":[-92.86, -0.02, -18.0],"right_hand":[-12.2, 13.02, -7.14]},

    58:{"left_arm":[94.82, -15.51, 28.52],"left_forearm":[-101.65, 2.41, 18.0],"left_hand":[-52.92, -24.02, -23.76],

        "right_arm":[80.42, 33.16, -75.06],"right_forearm":[-92.86, -0.02, -18.0],"right_hand":[-12.19, 13.02, -7.14]},

    64:{"left_arm":[94.82, -15.54, 28.52],"left_forearm":[-101.66, 2.38, 18.0],"left_hand":[-52.9, -23.97, -23.75],

        "right_arm":[80.43, 33.16, -75.04],"right_forearm":[-92.85, -0.04, -18.0],"right_hand":[-12.19, 13.02, -7.14]},

    68:{"left_arm":[94.81, -15.58, 28.52],"left_forearm":[-101.68, 2.35, 18.0],"left_hand":[-52.88, -23.9, -23.74],

        "right_arm":[80.44, 33.16, -75.01],"right_forearm":[-92.84, -0.06, -18.0],"right_hand":[-12.19, 13.01, -7.14]},

}

    def hold(carried,tick):
        for name,values in TWO_HANDED_GRIP[tick].items():
            carried[name]=list(values)
        return carried

    frames=[frame(0,pose()),frame(6,launch,-1.0),frame(15,rise),frame(24,call),frame(32,hold(copy.deepcopy(call),32))]
    # One complete turn in a single direction. The model is mirrored, so the clockwise look is
    # authored as increasing yaw, which also keeps it in step with the whip and the combo spins.
    for tick,yaw in ((40,0),(46,72),(52,144),(58,216),(64,288)):
        p=copy.deepcopy(loaded)
        p['root']=[0,yaw,0]
        # The torso, bent knees and ankles counterbalance the weight of the enlarged axe; both arms
        # come from the solved two-handed hold so the hands stay locked to the moving shaft.
        arc=math.sin((tick-40)/24*math.pi)
        p['waist'][2]=arc*5;p['chest'][2]=-arc*7
        p['left_thigh'][0]+=arc*7;p['right_thigh'][0]-=arc*9
        p['left_shin'][0]-=arc*8;p['right_shin'][0]+=arc*6
        frames.append(frame(tick,hold(p,tick)))
    dive=copy.deepcopy(loaded);dive['root']=[0,324,0]
    dive['hips'][0]=-3;dive['chest'][0]=4
    hold(dive,68)
    impact=pose(hips=[18,0,0],waist=[19,0,0],chest=[31,0,0],neck=[-12,0,0],head=[-18,0,0],
        right_arm=[52,-8,-12],right_forearm=[-14,0,13],right_hand=[-66.0,0,0],
        left_arm=[66,20,43],left_forearm=[-31,-15,25],left_hand=[-22,0,-14],
        left_thigh=[47,0,-10],right_thigh=[-27,0,10],left_shin=[-48,0,0],right_shin=[32,0,0],
        left_foot=[9,0,0],right_foot=[-15,0,0])
    impact['root']=[0,360,0]
    recoil=copy.deepcopy(impact)
    recoil['hips'][0]=-13;recoil['chest'][0]=-21
    recoil['right_forearm'][0]=-22;recoil['left_forearm'][0]=-40
    recoil['right_hand']=[-55.65,0,0]
    settle=axe();settle['right_arm']=[37,-8,-12];settle['right_hand']=[-51.75,0,0]
    recovery=pose(hips=[3,0,0],waist=[2,0,0],chest=[4,0,0],head=[-4,0,0],
        right_arm=[19,-8,-8],right_forearm=[-17,0,9],right_hand=[-59.9,0,0],left_arm=[17,0,10],
        left_thigh=[12,0,-5],right_thigh=[-9,0,5],left_shin=[-10,0,0],right_shin=[8,0,0])
    # 360 faces exactly where 0 does, so holding that value keeps the finished turn intact
    # instead of splining a spurious extra revolution on the way into the P2 idle.
    for part in (recoil,settle,recovery):
        part['root']=[0,360,0]
    ending=pose();ending['root']=[0,360,0]
    # Adjacent hold keys arrest Catmull-Rom's post-impact angular overshoot; with
    # a threefold shaft even a small overshoot would drive the axe under ground.
    frames += [frame(68,dive),frame(72,impact,-1.2),frame(72.1,impact,-1.2),frame(75.9,impact,-1.2),
        frame(76,impact,-1.2),frame(83,recoil,-.6),frame(96,settle,-.4),frame(112,recovery),
        frame(126,recovery),frame(132,recovery),frame(144,ending)]
    finish('transition',frames,144,metadata={'ascent_ticks':[0,24],'summon_ticks':[24,40],
        'sweep_ticks':[40,64],'sweep_arc_degrees':360,'hit_ticks':[72],
        'early_shockwave_ticks':[84,95],'shockwave_ticks':[96,126],
        'recovery_end_tick':144,'server_owns_flight':True})
    summon=pose(chest=[-4,0,0],head=[-9,0,0],right_arm=[112,-10,-15],right_forearm=[-42,0,18],
        right_hand=[-77,0,0],left_arm=[70,10,29],left_forearm=[-15,0,-14],left_hand=[-25,0,0])
    finish('axe_summon',[frame(0,pose()),frame(16,summon),frame(24,summon,.3),frame(28,axe(True),-.3),frame(37,pose())],37)
    airborne=axe(True);airborne['left_thigh']=[38,0,-8];airborne['right_thigh']=[30,0,8]
    airborne['left_shin']=[-62,0,0];airborne['right_shin']=[-57,0,0]
    finish('axe_slam',[frame(0,pose()),frame(7,axe(True),-1.4),frame(13,airborne),frame(23,airborne),
        frame(26.5,axe(True)),frame(28,axe(),-1.1),frame(34,axe(),-1.1),frame(53,pose())],53,
        metadata={'hit_ticks':[28],'windup_ticks':[28],'active_ticks':3,'jump_begin_tick':8,'jump_end_tick':27})
    sequence('axe_combo',[10,8,11,24],4,3,22,['horizontal','horizontal','upward','overhead'])
    sequence('delayed_combo',[7,6,24],3,3,22,['horizontal','horizontal','horizontal'])
    cast=pose(chest=[-3,12,0],head=[1,-13,0],left_arm=[88,-10,18],left_forearm=[-5,0,-12],
        left_hand=[-55,0,8],right_arm=[24,0,-10],right_forearm=[-17,0,8])
    release=pose(chest=[5,-7,0],head=[-3,8,0],left_arm=[91,8,6],left_forearm=[-2,0,-4],left_hand=[-82,0,0])
    finish('flying_swords',[frame(0,pose()),frame(13,cast,.1),frame(23,cast,.1),frame(24,release),frame(30,release),frame(43,pose())],43,
        metadata={'hit_ticks':[24],'windup_ticks':[24],'active_ticks':1})
    sheathe=pose(hips=[8,-22,-2],waist=[9,-18,-2],chest=[11,-22,-3],neck=[-4,16,1],head=[-7,28,1],
        right_arm=[44,-36,56],right_forearm=[-30,-16,35],right_hand=[-94,-10,-52],
        left_arm=[12,-20,12],left_forearm=[-18,0,6],left_hand=[-14,10,-8],
        left_thigh=[26,0,-7],right_thigh=[-22,0,7],left_shin=[-34,0,0],right_shin=[-26,0,0])
    draw_snap=broad(False,1,snap=True)
    cut=broad(False,1);cut['right_hand']=[-142,24,14];cut['hips'][1]=26;cut['chest'][1]=28
    cut_follow=broad(False,1,follow=True);cut_follow['right_hand']=[-124,20,10]
    finish('draw_slash',[frame(0,pose()),frame(8,sheathe,-.88),frame(15.6,sheathe,-.95),
        frame(16.9,draw_snap,-.85),frame(18,cut,-.55),frame(21,cut_follow,-.38),frame(40,pose())],40,
        metadata={'hit_ticks':[18],'windup_ticks':[18],'active_ticks':2})

    for name,windup,active,recovery,arc in [('whip_sweep',22,8,18,200),('whip_spin',28,12,24,360)]:
        ready=pose(hips=[5,0,0],waist=[5,0,0],chest=[7,0,0],head=[-9,0,0],
            right_arm=[60,-15,-28],right_forearm=[-18,0,21],right_hand=[-100,0,-14],
            left_arm=[35,0,33],left_forearm=[-38,0,-18],left_thigh=[15,0,-7],right_thigh=[-15,0,7])
        # The lash is carried by the arm as well as the body. The whip hand is drawn in for the
        # wind-up, opens out through the middle of the arc and cracks the wrist at the apex, while
        # the empty hand counterswings. Without this the sweep read as a stiff body rotation.
        load=copy.deepcopy(ready)
        load['root']=[0,-arc/2,0]
        load['right_arm']=[72,-19,-40];load['right_forearm']=[-30,0,21];load['right_hand']=[-126,0,-14]
        load['left_arm']=[30,0,42];load['left_forearm']=[-46,0,-18]
        load['hips'][1]=-6;load['waist'][1]=-7;load['chest'][1]=-10
        frames=[frame(0,pose()),frame(windup*.55,load,-.4),frame(windup,load,-.4)]
        for i in range(1,active+1):
            s=i/active
            p=copy.deepcopy(ready)
            # Increasing authored yaw is the clockwise look every rotating attack shares.
            p['root']=[0,-arc/2+arc*i/active,0]
            # 0 -> 1 -> 0 across the active ticks: out of the wind-up, through the apex, back in.
            lash=math.sin(s*math.pi)
            p['right_arm']=[60-26*lash,-15+10*lash,-28+46*lash]
            p['right_forearm']=[-18+17*lash,0,21]
            p['right_hand']=[-100-36*lash,0,-14]
            p['left_arm']=[35+9*lash,0,33+15*lash]
            p['left_forearm']=[-38+11*lash,0,-18]
            p['left_thigh']=[math.sin(i/active*math.pi*2)*16,0,-7]
            p['right_thigh']=[-p['left_thigh'][0],0,7]
            p['hips'][1]=4+5*math.sin(math.pi*s)
            p['waist'][1]=3-6*math.cos(math.pi*s)
            p['chest'][1]=8-11*math.cos(math.pi*s)
            p['chest'][2]=math.sin(i/active*math.pi)*-5
            frames.append(frame(windup+i,p,-.4))
        end=pose();end['root']=[0,360 if arc==360 else 0,0]
        frames.append(frame(windup+active+recovery,end))
        finish(name,frames,windup+active+recovery,metadata={'hit_ticks':[windup],'windup_ticks':[windup],
            'active_ticks':active,'arc_degrees':arc})

    ready, thrust, thrust_apex, thrust_brake = thrust_poses()
    finish('lightning_thrust',[frame(0,pose()),frame(11,ready,-1.15),frame(16.5,ready,-1.25),
        (18.2,thrust,[0,-.95,-.85]),(21.2,thrust_apex,[0,-1.05,-.95]),(24,thrust,[0,-.88,-.65]),
        (28,thrust_brake,[0,-1.10,-.20]),frame(44,pose())],44,
        metadata={'hit_ticks':[18],'windup_ticks':[18],'active_ticks':6})
    vanish=pose(hips=[12,-14,-3],waist=[9,-14,-2],chest=[14,-20,-3],head=[-10,22,1],
        right_arm=[32,-22,-13],right_forearm=[-34,0,15],right_hand=[-105,0,0],
        left_arm=[48,20,30],left_forearm=[-28,0,-16],left_thigh=[38,0,-7],right_thigh=[-30,0,7],
        left_shin=[-42,0,0],right_shin=[-26,0,0])
    finish('invisible_dash',[frame(0,pose()),frame(8,vanish,-1.05),frame(17,vanish,-1.05),frame(22,ready,-1.10),
        frame(28.5,ready,-1.25),(30.2,thrust,[0,-.95,-.85]),(33.2,thrust_apex,[0,-1.05,-.95]),
        (36,thrust,[0,-.88,-.65]),(40,thrust_brake,[0,-1.10,-.20]),frame(58,pose())],58,
        metadata={'hit_ticks':[30],'windup_ticks':[30],'active_ticks':6,'invisible_ticks':[8,18],'reveal_warning_ticks':12})

    # P2 Variation Skill 1: AXE_DRAW_MIXUP (60 ticks: step0 axe cleave hit=14..16, swap=19, step1 sword draw hit=37..39, end=60)
    axe_load = broad(True, 1)
    axe_snap = broad(False, 1, snap=True)
    axe_hit = broad(False, 1)
    axe_follow = broad(False, 1, follow=True)
    sword_cut = broad(False, -1); sword_cut['right_hand'] = [-142, -22, -14]; sword_cut['hips'][1] = -26; sword_cut['chest'][1] = -28
    sword_follow = broad(False, -1, follow=True); sword_follow['right_hand'] = [-122, -18, -10]
    finish('axe_draw_mixup', [
        frame(0, pose()), frame(9.5, axe_load, -.85), frame(12.6, axe_snap, -.92),
        frame(14, axe_hit, -.72), frame(17.5, axe_follow, -.45),
        # Weapon swaps to sword at tick 19 -> drop straight into coiled iai sheathe crouch!
        frame(23, sheathe, -.90), frame(34.5, sheathe, -1.02),
        frame(35.8, broad(False, -1, snap=True), -.88), frame(37, sword_cut, -.58),
        frame(40.5, sword_follow, -.38), frame(60, pose()),
    ], 60, metadata={'step_start_ticks': [0, 21], 'hit_ticks': [14, 37], 'windup_ticks': [14, 16], 'active_ticks': 3, 'gap_ticks': 4})

    # P2 Variation Skill 2: WHIP_LANCE_REEL (68 ticks: step0 190° whip lash hit=18..23, swap=26, step1 lightning thrust hit=42..47, end=68)
    whip_arc = 190
    whip_ready = pose(hips=[5, 0, 0], waist=[5, 0, 0], chest=[7, 0, 0], head=[-9, 0, 0],
        right_arm=[60, -15, -28], right_forearm=[-18, 0, 21], right_hand=[-100, 0, -14],
        left_arm=[35, 0, 33], left_forearm=[-38, 0, -18], left_thigh=[15, 0, -7], right_thigh=[-15, 0, 7])
    whip_load = copy.deepcopy(whip_ready)
    whip_load['root'] = [0, -whip_arc / 2, 0]
    whip_load['right_arm'] = [72, -19, -40]; whip_load['right_forearm'] = [-30, 0, 21]; whip_load['right_hand'] = [-126, 0, -14]
    whip_load['left_arm'] = [30, 0, 42]; whip_load['left_forearm'] = [-46, 0, -18]
    whip_load['hips'][1] = -8; whip_load['waist'][1] = -8; whip_load['chest'][1] = -12
    reel_frames = [frame(0, pose()), frame(10, whip_load, -.45), frame(18, whip_load, -.45)]
    for i in range(1, 7):
        s = i / 6.0
        p = copy.deepcopy(whip_ready)
        p['root'] = [0, -whip_arc / 2 + whip_arc * s, 0]
        lash = math.sin(s * math.pi)
        p['right_arm'] = [60 - 26 * lash, -15 + 10 * lash, -28 + 46 * lash]
        p['right_forearm'] = [-18 + 17 * lash, 0, 21]
        p['right_hand'] = [-100 - 36 * lash, 0, -14]
        p['left_arm'] = [35 + 10 * lash, 0, 33 + 15 * lash]
        p['left_forearm'] = [-38 + 11 * lash, 0, -18]
        p['hips'][1] = 4 + 6 * math.sin(math.pi * s)
        p['waist'][1] = 3 - 6 * math.cos(math.pi * s)
        p['chest'][1] = 8 - 11 * math.cos(math.pi * s)
        p['chest'][2] = math.sin(s * math.pi) * -5
        reel_frames.append(frame(18 + i, p, -.45))
    # Retract whip into spear bow-stance chamber (root unwinds cleanly back to 0) then unleash lightning thrust!
    reel_frames += [
        frame(28, ready, -1.05), frame(40.5, ready, -1.25),
        (42.2, thrust, [0, -.95, -.85]), (45.2, thrust_apex, [0, -1.05, -.95]),
        (48, thrust, [0, -.88, -.65]), (52, thrust_brake, [0, -1.10, -.20]),
        frame(68, pose()),
    ]
    finish('whip_lance_reel', reel_frames, 68, metadata={'step_start_ticks': [0, 28], 'hit_ticks': [18, 42], 'windup_ticks': [18, 14], 'active_ticks': 6, 'gap_ticks': 4})
