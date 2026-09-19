"""P3 authored poses. World elevation and hazard timing remain server-authoritative."""
import copy

from generate_yang_jian_animations import CARRY_ANGLE

import math

def append_p3(pose,finish,attack_pose):
    def frame(t,p,y=0):return (t,p,[0,y,0])
    calm=pose(chest=[-2,0,0],head=[0,0,0],left_arm=[12,8,-5],left_forearm=[-20,0,CARRY_ANGLE],left_hand=[-30,0,0])
    focus=pose(hips=[2,0,0],waist=[2,0,0],chest=[3,0,0],head=[-5,0,0],
        left_arm=[138.5,-47,28.5],left_forearm=[-69,25,-8],left_hand=[-22,0,-13],
        right_arm=[13,-7,-8],right_forearm=[-14,0,10],left_thigh=[12,0,-6],right_thigh=[-12,0,6])
    proclaim=pose(chest=[-5,0,0],head=[-8,0,0],left_arm=[114,12,42],left_forearm=[-22,0,-23],
        right_arm=[62,-16,-35],right_forearm=[-20,0,16],left_hand=[-50,0,-16],right_hand=[-30,0,0])
    cast=pose(chest=[4,-12,0],head=[-4,12,0],left_arm=[100,6,24],left_forearm=[-14,0,-18],left_hand=[-78,0,0],
        right_arm=[21,-9,-12],right_forearm=[-12,0,10])
    flight=pose(chest=[-3,0,0],head=[5,0,0],left_arm=[89,0,39],left_forearm=[-25,0,-20],
        right_arm=[64,-13,-26],right_forearm=[-31,0,15],right_hand=[-72,0,0],
        left_thigh=[28,0,-9],right_thigh=[17,0,10],left_shin=[-44,0,0],right_shin=[-37,0,0])
    beam_focus=copy.deepcopy(focus)
    beam_focus['left_arm']=[109,-15,30]
    beam_focus['left_forearm']=[-69,6,-24]
    def flying(p):
        p=copy.deepcopy(p)
        for bone in ('left_thigh','right_thigh','left_shin','right_shin'):p[bone]=flight[bone][:]
        return p
    idle=[]
    for tick in range(0,81,4):
        p=copy.deepcopy(calm);p['chest'][0]+=math.sin(tick*math.pi/40)*.7
        idle.append(frame(tick,p,math.sin(tick*math.pi/40)*.2))
    finish('p3_idle',idle,80,True)
    finish('third_eye_open',[frame(0,pose()),frame(12,pose(chest=[14,0,0],head=[12,0,0]),-.8),
        frame(25,focus),frame(40,focus),frame(53,proclaim,.3),frame(62,proclaim,.3),frame(70,calm)],70,
        metadata={'hit_ticks':[70],'active_ticks':0,'eye_open_ticks':[20,55]})
    for name,w,a,r in [('eye_beam',10,8,14),('sweep_beam',24,112,22),('tracking_beam',22,50,22)]:
        frames=[frame(0,calm),frame(w*.55,beam_focus,-.25),frame(w,beam_focus,-.25)]
        for tick in range(w+4,w+a+1,4):
            p=copy.deepcopy(beam_focus)
            if name=='sweep_beam':
                # The body performs three complete passes.  The clone starts
                # halfway through each pass, so the body resets its head arc
                # only when it teleports for the next body pass.
                local=min(1.0,((tick-w)%32)/32)
                if tick>=w+96:local=1.0
                p['head'][1]=-65+130*local
            p['left_hand'][2]+=math.sin(tick*.3)*2
            frames.append(frame(tick,p,-.25))
        frames += [frame(w+a,beam_focus,-.25),frame(w+a+r,calm)]
        finish(name,frames,w+a+r,metadata={'hit_ticks':[w],'active_ticks':a})
    frames=[frame(0,calm),frame(15,flight),frame(19,flying(proclaim))]
    for t in (26,56,86):frames += [frame(t-4,flying(proclaim)),frame(t,flying(cast)),frame(t+8,flight)]
    frames += [frame(117,flight),frame(138,calm),frame(150,calm)]
    frames[-2]=frame(138,calm);frames[-1]=frame(140,calm)
    finish('myriad_swords',frames,140,metadata={'cast_ticks':[26,56,86],'impact_ticks':[48,78,108]})
    for name,times,total in [('sword_rain',[20,36,52,68,84,100,116,132],172),('red_thunder',[14,28,42,56],90)]:
        frames=[frame(0,calm)]
        for i,t in enumerate(times):
            load=copy.deepcopy(proclaim);hit=copy.deepcopy(cast)
            load['chest'][1]=(-1 if i%2 else 1)*12;hit['head'][1]=-load['chest'][1]
            frames += [frame(max(1,t-7),load),frame(t,hit,-.25),frame(t+5,calm)]
        frames.append(frame(total,calm));finish(name,frames,total,metadata={'cast_ticks':times})
    for name,arc in [('divine_sweep',170),('divine_spin',360)]:
        # Clockwise, like every other rotating attack: the mirrored model authors it as increasing
        # yaw, and the chain it drags is drawn in world space the opposite way.
        ready=attack_pose('horizontal',True);ready['root']=[0,-arc*.5,0]
        frames=[frame(0,calm),frame(12,ready,-.6),frame(20,ready,-.6)]
        for i in range(1,11):
            p=attack_pose('horizontal');p['root']=[0,-arc*.5+arc*i/10,0]
            frames.append(frame(20+i,p,-.6))
        end=copy.deepcopy(calm);end['root']=[0,360 if arc==360 else 0,0]
        frames.append(frame(50,end));finish(name,frames,50,metadata={'hit_ticks':[20],'active_ticks':10})
    load=flying(attack_pose('overhead',True));impact=attack_pose('overhead')
    finish('aerial_combo',[frame(0,calm),frame(16,flight),frame(20,flying(proclaim)),frame(24,flying(cast)),
        frame(41,flight),frame(54,flying(beam_focus)),frame(60,flying(beam_focus)),frame(99,flying(beam_focus)),
        frame(108,load),frame(122,load),frame(130,impact,-1),frame(138,proclaim),frame(140,cast),
        frame(153,calm),frame(180,calm)],180,metadata={'cast_ticks':[24,30,36,60,104,140],
        'impact_ticks':[42,44,46,76,130,162]})
    judgement_cast=flying(cast)
    frames=[frame(0,calm),frame(20,flight),frame(35,flying(proclaim)),frame(43,flying(proclaim))]
    # Both ultimate sword segments are dense bursts now, so the boss repeats the cast gesture for
    # each of the three close waves instead of holding one pose across a single wide spread.
    for index,t in enumerate((50,56,62)):
        wave=copy.deepcopy(judgement_cast);wave['chest'][1]=(-1 if index%2 else 1)*10
        wave['head'][1]=-wave['chest'][1]
        frames.append(frame(t,wave))
    frames += [frame(77,flight),frame(88,load),frame(108,load),frame(116,impact,-1),
        frame(124,flight),frame(130,flying(beam_focus)),frame(184,flying(beam_focus)),frame(214,flight),
        frame(224,flying(proclaim)),frame(228,flying(cast)),frame(254,flying(proclaim))]
    for index,t in enumerate((264,270,276)):
        wave=copy.deepcopy(judgement_cast);wave['chest'][1]=(-1 if index%2 else 1)*10
        wave['head'][1]=-wave['chest'][1]
        frames.append(frame(t,wave))
    frames += [frame(282,flight),frame(304,calm),frame(328,calm)]
    finish('divine_judgement',frames,328,
        metadata={'cast_ticks':[50,56,62,88,130,184,228,264,270,276],
        'impact_ticks':[76,78,80,116,154,204,252,288,290,292],'landing_tick':304})
    kneel=pose(hips=[14,0,0],waist=[9,0,0],chest=[14,0,0],head=[18,0,0],
        left_thigh=[46,0,-8],right_thigh=[-46,0,7],left_shin=[-66,0,0],right_shin=[-58,0,0],
        left_arm=[24,0,19],right_arm=[29,0,-14],right_forearm=[-12,0,9])
    finish('p3_death',[frame(0,calm),frame(6,pose(chest=[-14,0,0],head=[-12,0,0]),-.6),
        frame(15,kneel,-7),frame(20,kneel,-7)],20)
