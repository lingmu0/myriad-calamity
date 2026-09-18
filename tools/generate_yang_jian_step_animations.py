"""A short advancing martial step; world displacement belongs to the server.

The feet start driving on tick 0 and push/gather through tick 8, then settle by
tick 10. The stride is long and the torso leans into it, so the advance reads as a
committed lunge. A left-hand blade flick releases on that same first tick; the right
hand keeps each weapon's existing carry throughout the advancing step.
"""
import copy


def append_steps(pose, finish):
    carries = {
        '': {},
        '_axe': {'right_arm': [11, -8, -8], 'right_forearm': [-12, 0, 9], 'left_arm': [13, 0, 8]},
        '_sword': {'right_arm': [16, -11, -9], 'right_forearm': [-7, 0, 9], 'right_hand': [-23, 0, -8]},
        '_whip': {'right_arm': [24, -9, -10], 'right_forearm': [-20, 0, 8], 'right_hand': [-5, 0, -4]},
    }
    # Front foot reaches once while the rear foot drives and gathers underneath;
    # there is no alternating walk cycle and no cosmetic horizontal root motion.
    # The stride is deliberately long and the torso leans into it, so the advance
    # reads as a committed lunge rather than a shuffle.
    stages = [
        # Start already in the first drive pose; there is no planted two-tick
        # windup before the server begins moving the entity.
        (0, -1.15, dict(hips=[9, -4, 0], waist=[6, 4, 0], chest=[9, -5, 0], neck=[-3, 1, 0], head=[-9, 5, 0],
            left_thigh=[30, 0, -6], right_thigh=[20, 0, 6], left_shin=[-38, 0, 0],
            right_shin=[-36, 0, 0], left_foot=[10, 0, 0], right_foot=[13, 0, 0])),
        (2, -.30, dict(hips=[13, -6, 0], waist=[8, 5, 0], chest=[13, -6, 0], neck=[-5, 2, 0], head=[-13, 6, 0],
            left_thigh=[54, 0, -7], right_thigh=[-46, 0, 7], left_shin=[-42, 0, 0],
            right_shin=[-16, 0, 0], left_foot=[-14, 0, 0], right_foot=[34, 0, 0])),
        (3.2, .15, dict(hips=[12, -5, 0], waist=[7, -4, 0], chest=[12, -11, -3], neck=[-4, 1, 0], head=[-12, 9, 0],
            left_thigh=[48, 0, -7], right_thigh=[-34, 0, 7], left_shin=[-34, 0, 0],
            right_shin=[-30, 0, 0], left_foot=[-16, 0, 0], right_foot=[30, 0, 0])),
        (4.4, .25, dict(hips=[11, 3, 0], waist=[7, 7, 0], chest=[13, 14, 2], neck=[-4, -3, 0], head=[-13, -11, 0],
            left_thigh=[42, 0, -6], right_thigh=[-24, 0, 6], left_shin=[-28, 0, 0],
            right_shin=[-40, 0, 0], left_foot=[-17, 0, 0], right_foot=[27, 0, 0])),
        (5, .25, dict(hips=[11, 4, 0], waist=[7, 8, 0], chest=[11, 16, 2], neck=[-4, -4, 0], head=[-11, -13, 0],
            left_thigh=[38, 0, -6], right_thigh=[-16, 0, 6], left_shin=[-24, 0, 0],
            right_shin=[-46, 0, 0], left_foot=[-17, 0, 0], right_foot=[24, 0, 0])),
        (5.6, -.70, dict(hips=[7, 2, 0], waist=[4, -2, 0], chest=[6, 2, 0], head=[-8, -3, 0],
            left_thigh=[24, 0, -5], right_thigh=[14, 0, 5], left_shin=[-32, 0, 0],
            right_shin=[-26, 0, 0], left_foot=[12, 0, 0], right_foot=[14, 0, 0])),
        (7.8, -.35, dict(hips=[3, 1, 0], waist=[2, -1, 0], chest=[2, 1, 0], head=[-4, -1, 0],
            left_thigh=[12, 0, -4], right_thigh=[7, 0, 4], left_shin=[-17, 0, 0],
            right_shin=[-13, 0, 0], left_foot=[6, 0, 0], right_foot=[7, 0, 0])),
        (8.8, 0, {}),
        (10, 0, {}),
    ]
    for suffix, carry in carries.items():
        frames = []
        for tick, height, overrides in stages:
            p = pose(**overrides)
            p.update(copy.deepcopy(carry))
            impulse = {0: .45, 2: 1, 3.2: .9, 4.4: .85, 5: .8, 5.6: .35, 7.8: .15, 8.8: 0, 10: 0}[tick]
            # Keep the weapon upright and close; bent elbows absorb the step.
            p['right_arm'][0] += 7 * impulse
            p['right_forearm'][0] -= 8 * impulse
            p['right_hand'][0] += 2 * impulse
            # The knife leaves on the first frame: show the completed flick
            # immediately, then recover the arm while the projectile flies.
            offhand={
                0: ([94,8,9],[-4,0,-4],[-75,4,0]),
                2: ([97,12,7],[-2,0,-3],[-91,6,2]),
                3.2: ([38,4,21],[-26,0,-13],[-25,2,-3]),
                4.4: ([18,0,12],[-8,0,-9],[-7,0,-2]),
            }
            if tick in offhand:
                p['left_arm'],p['left_forearm'],p['left_hand']=copy.deepcopy(offhand[tick])
            frames.append((tick, p, [0, height, 0]))
        finish('step_approach' + suffix, frames, 10, metadata={
            'prepare_ticks': [0, 0], 'travel_ticks': [0, 8], 'settle_ticks': [8, 10],
            'offhand_throw_tick':0,
        })
