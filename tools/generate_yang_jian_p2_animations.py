"""P2 animation authoring, appended by generate_yang_jian_animations.py.

The P1 generator owns the resource write; importing this module never overwrites
files. Full clips use the exact YangJianSkill windup/active/recovery timeline.
"""
import copy
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

    def broad(loaded=False,side=1):
        p=attack_pose('horizontal',loaded,side=side)
        p['left_arm']=[75,side*12,36]
        p['left_forearm']=[-44,0,22]
        p['right_hand']=[-110 if loaded else -135,side*8,side*13]
        return p

    def sequence(name,windups,active,gap,recovery,kinds):
        frames=[];starts=[];hits=[];tick=0
        for i,(w,kind) in enumerate(zip(windups,kinds)):
            starts.append(tick);hits.append(tick+w)
            if kind=='overhead':load,hit=axe(True),axe()
            elif kind=='upward':load,hit=attack_pose('upward',True),attack_pose('upward')
            else:load,hit=broad(True,1 if i%2==0 else -1),broad(False,1 if i%2==0 else -1)
            # The delayed final strike holds its loaded pose, avoiding an evenly paced combo.
            held=tick+w*.48 if name=='delayed_combo' and i==2 else tick+w*.7
            frames += [frame(tick,pose()),frame(held,load,-.7),frame(tick+w-1.5,load,-.7),
                       frame(tick+w,hit,-.6),frame(tick+w+active-.2,hit,-.4)]
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
            if weapon=='axe':
                p['right_arm']=[11,-8,5];p['right_forearm']=[-12,0,0];p['left_arm']=[13,0,-5]
            elif weapon=='sword':
                p['right_arm']=[16,-11,5];p['right_forearm']=[-7,0,0];p['right_hand']=[-23,0,-8]
            else:
                p['right_arm']=[24,-9,5];p['right_forearm']=[-20,0,0];p['right_hand']=[-5,0,-4]
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
        right_arm=[139,-8,-14],right_forearm=[-35,0,15],right_hand=[-103,0,0],
        left_arm=[106,14,37],left_forearm=[-29,0,-21],left_hand=[-45,-12,13],
        left_thigh=[37,0,-8],right_thigh=[26,0,8],left_shin=[-66,0,0],right_shin=[-55,0,0])
    loaded=axe(True)
    loaded['left_thigh']=[36,0,-10];loaded['right_thigh']=[27,0,9]
    loaded['left_shin']=[-65,0,0];loaded['right_shin']=[-56,0,0]
    loaded['left_foot']=[-13,0,0];loaded['right_foot']=[-10,0,0]
    # The summoned axe is heavy, so it is carried two-handed: the left hand grips the shaft just
    # above the right hand for the whole spin and the dive, and only the slam releases it. Both arms
    # were solved against the rig (see tools/check_yang_jian_transition_grip.py) with the grip kept
    # in front of the chest; the handle then leans up and outward with the head above the shoulder.
    TWO_HANDED_GRIP={
        32:{'left_arm':[68.77,-51.78,41.06],'left_forearm':[-50.82,0.0,-100.69],'left_hand':[4.19,-47.45,-52.61],
            'right_arm':[72.5,43.92,-77.97],'right_forearm':[-75.87,-0.01,13.71],'right_hand':[-24.17,13.21,-5.84]},
        40:{'left_arm':[71.73,-48.48,42.83],'left_forearm':[-52.14,0.0,-102.83],'left_hand':[13.99,-42.06,-58.85],
            'right_arm':[73.84,45.67,-74.84],'right_forearm':[-77.36,-0.01,13.63],'right_hand':[-22.17,11.03,-7.57]},
        46:{'left_arm':[75.08,-43.68,44.45],'left_forearm':[-54.93,-6.4,-95.49],'left_hand':[13.87,-37.45,-59.28],
            'right_arm':[76.34,46.06,-70.93],'right_forearm':[-77.83,-3.22,13.18],'right_hand':[-21.95,10.18,-6.7]},
        52:{'left_arm':[78.02,-39.33,45.85],'left_forearm':[-58.36,-11.55,-88.18],'left_hand':[13.35,-33.14,-60.21],
            'right_arm':[78.71,46.34,-67.15],'right_forearm':[-78.28,-6.08,12.61],'right_hand':[-21.64,9.32,-6.1]},
        58:{'left_arm':[80.52,-35.44,47.19],'left_forearm':[-62.03,-15.57,-81.09],'left_hand':[12.42,-29.19,-61.38],
            'right_arm':[80.93,46.57,-63.51],'right_forearm':[-78.61,-8.65,12.11],'right_hand':[-21.33,8.45,-5.73]},
        64:{'left_arm':[82.58,-32.01,48.54],'left_forearm':[-65.63,-18.64,-74.4],'left_hand':[11.2,-25.64,-62.62],
            'right_arm':[82.98,46.81,-60.03],'right_forearm':[-78.79,-10.96,11.76],'right_hand':[-21.07,7.58,-5.54]},
        68:{'left_arm':[84.37,-28.96,49.78],'left_forearm':[-69.17,-20.9,-68.09],'left_hand':[9.82,-22.47,-63.94],
            'right_arm':[84.92,47.01,-56.71],'right_forearm':[-78.95,-13.01,11.41],'right_hand':[-20.77,6.72,-5.51]},
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
        right_arm=[52,-8,-12],right_forearm=[-14,0,13],right_hand=[-60.85,0,0],
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
    sheathe=pose(hips=[4,-16,0],waist=[7,-15,0],chest=[9,-19,0],head=[-5,26,0],
        right_arm=[46,-35,57],right_forearm=[-27,-16,35],right_hand=[-96,0,-61],
        left_arm=[5,-23,9],left_forearm=[-9,0,4],left_thigh=[18,0,-5],right_thigh=[-17,0,5])
    cut=broad(False);cut['right_hand']=[-142,24,14]
    finish('draw_slash',[frame(0,pose()),frame(8,sheathe,-.55),frame(16.5,sheathe,-.55),frame(18,cut,-.3),
        frame(20,cut,-.3),frame(40,pose())],40,metadata={'hit_ticks':[18],'windup_ticks':[18],'active_ticks':2})

    for name,windup,active,recovery,arc in [('whip_sweep',22,8,18,200),('whip_spin',28,12,24,360)]:
        ready=pose(hips=[5,0,0],waist=[5,0,0],chest=[7,0,0],head=[-9,0,0],
            right_arm=[60,-15,-28],right_forearm=[-18,0,21],right_hand=[-100,0,-14],
            left_arm=[35,0,33],left_forearm=[-38,0,-18],left_thigh=[15,0,-7],right_thigh=[-15,0,7])
        load=copy.deepcopy(ready);load['root']=[0,-arc/2,0]
        frames=[frame(0,pose()),frame(windup*.55,load,-.4),frame(windup,load,-.4)]
        for i in range(1,active+1):
            p=copy.deepcopy(ready)
            # Increasing authored yaw is the clockwise look every rotating attack shares.
            p['root']=[0,-arc/2+arc*i/active,0]
            p['left_thigh']=[math.sin(i/active*math.pi*2)*16,0,-7]
            p['right_thigh']=[-p['left_thigh'][0],0,7]
            p['chest'][2]=math.sin(i/active*math.pi)*-5
            frames.append(frame(windup+i,p,-.4))
        end=pose();end['root']=[0,360 if arc==360 else 0,0]
        frames.append(frame(windup+active+recovery,end))
        finish(name,frames,windup+active+recovery,metadata={'hit_ticks':[windup],'windup_ticks':[windup],
            'active_ticks':active,'arc_degrees':arc})

    ready=pose(hips=[5,0,0],waist=[4,-12,0],chest=[7,-18,0],head=[-9,15,0],
        right_arm=[60,-10,-10],right_forearm=[-18,0,9],right_hand=[-116,0,0],
        left_arm=[65,20,25],left_forearm=[-25,0,-14],left_thigh=[24,0,-5],right_thigh=[-25,0,5])
    thrust=pose(hips=[13,0,0],waist=[8,16,0],chest=[15,18,0],head=[-17,-22,0],
        right_arm=[72,8,-4],right_forearm=[-8,0,2],right_hand=[-118,0,0],
        left_arm=[35,-10,28],left_forearm=[-34,0,-17],left_thigh=[40,0,-5],right_thigh=[-39,0,5],
        left_shin=[-21,0,0],right_shin=[29,0,0],right_foot=[-10,0,0])
    finish('lightning_thrust',[frame(0,pose()),frame(11,ready,-.7),frame(16.5,ready,-.7),
        frame(18,thrust,-1),frame(24,thrust,-1),frame(44,pose())],44,
        metadata={'hit_ticks':[18],'windup_ticks':[18],'active_ticks':6})
    vanish=pose(chest=[12,-18,0],head=[-8,20,0],right_arm=[30,-22,-13],right_forearm=[-31,0,15],
        right_hand=[-105,0,0],left_arm=[42,20,30],left_thigh=[35,0,-7],right_thigh=[-29,0,7])
    finish('invisible_dash',[frame(0,pose()),frame(8,vanish,-.8),frame(17,vanish,-.8),frame(22,ready,-.6),
        frame(28.5,ready,-.6),frame(30,thrust,-1),frame(36,thrust,-1),frame(58,pose())],58,
        metadata={'hit_ticks':[30],'windup_ticks':[30],'active_ticks':6,'invisible_ticks':[8,18],'reveal_warning_ticks':12})
