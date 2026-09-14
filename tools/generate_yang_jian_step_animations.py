"""A short advancing martial step; world displacement belongs to the server.

The feet start driving on tick 0 and push/gather through tick 8, then settle by
tick 10. A left-hand blade flick releases on that same first tick; the right
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
    stages = [
        # Start already in the first drive pose; there is no planted two-tick
        # windup before the server begins moving the entity.
        (0, -1.05, dict(hips=[5, -3, 0], waist=[3, 3, 0], chest=[5, -4, 0], head=[-8, 4, 0],
            left_thigh=[22, 0, -5], right_thigh=[14, 0, 5], left_shin=[-29, 0, 0],
            right_shin=[-28, 0, 0], left_foot=[7, 0, 0], right_foot=[9, 0, 0])),
        (2, -.35, dict(hips=[8, -5, 0], waist=[4, 4, 0], chest=[7, -5, 0], neck=[-4, 1, 0], head=[-12, 4, 0],
            left_thigh=[34, 0, -5], right_thigh=[-28, 0, 5], left_shin=[-28, 0, 0],
            right_shin=[-12, 0, 0], left_foot=[-5, 0, 0], right_foot=[24, 0, 0])),
        (3.2, .1, dict(hips=[8, -4, 0], waist=[4, -3, 0], chest=[7, -9, -2], neck=[-4, 0, 0], head=[-11, 8, 0],
            left_thigh=[30, 0, -5], right_thigh=[-20, 0, 5], left_shin=[-22, 0, 0],
            right_shin=[-23, 0, 0], left_foot=[-7, 0, 0], right_foot=[21, 0, 0])),
        (4.4, .2, dict(hips=[7, 2, 0], waist=[4, 6, 0], chest=[8, 12, 1], neck=[-4, -2, 0], head=[-12, -10, 0],
            left_thigh=[27, 0, -4], right_thigh=[-14, 0, 4], left_shin=[-18, 0, 0],
            right_shin=[-30, 0, 0], left_foot=[-8, 0, 0], right_foot=[19, 0, 0])),
        (5, .2, dict(hips=[7, 3, 0], waist=[4, 7, 0], chest=[6, 14, 1], neck=[-4, -3, 0], head=[-10, -12, 0],
            left_thigh=[25, 0, -4], right_thigh=[-8, 0, 4], left_shin=[-15, 0, 0],
            right_shin=[-36, 0, 0], left_foot=[-8, 0, 0], right_foot=[17, 0, 0])),
        (5.6, -.65, dict(hips=[4, 2, 0], waist=[2, -2, 0], chest=[3, 2, 0], head=[-7, -2, 0],
            left_thigh=[18, 0, -4], right_thigh=[10, 0, 4], left_shin=[-26, 0, 0],
            right_shin=[-22, 0, 0], left_foot=[9, 0, 0], right_foot=[10, 0, 0])),
        (7.8, -.35, dict(hips=[2, 1, 0], waist=[1, -1, 0], chest=[1, 1, 0], head=[-3, -1, 0],
            left_thigh=[9, 0, -3], right_thigh=[5, 0, 3], left_shin=[-13, 0, 0],
            right_shin=[-10, 0, 0], left_foot=[4, 0, 0], right_foot=[5, 0, 0])),
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
