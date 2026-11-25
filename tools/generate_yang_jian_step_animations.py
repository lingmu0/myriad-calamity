"""A short advancing martial step; world displacement belongs to the server.

Full-body coordinated martial lunge: ground push-off on tick 0 with immediate
off-hand dart flick, smooth hip-led mid-stride weight transfer (no abrupt torso
flip), natural weapon inertia per carried weapon, and grounded braking plant
through ticks 6..10.
"""
import copy

from generate_yang_jian_animations import CARRY_ANGLE


def append_steps(pose, finish):
    carries = {
        '': {},
        '_axe': {'right_arm': [11, -8, 8], 'right_forearm': [-20, 0, -CARRY_ANGLE], 'left_arm': [13, 0, -5]},
        '_sword': {'right_arm': [16, -11, 8], 'right_forearm': [-16, 0, -CARRY_ANGLE], 'right_hand': [-23, 0, 0]},
        '_whip': {'right_arm': [24, -9, 8], 'right_forearm': [-26, 0, -CARRY_ANGLE], 'right_hand': [-5, 0, 0]},
    }
    # Weapon-specific inertia curves so each carried weapon responds organically to acceleration & brake.
    weapon_inertia = {
        '':       dict(arm_x=-10, arm_y=-6, arm_z=5,  fore_x=-11, hand_x=-9,  hand_z=4),
        '_axe':   dict(arm_x=8,   arm_y=-4, arm_z=6,  fore_x=-14, hand_x=-6,  hand_z=3),
        '_sword': dict(arm_x=-8,  arm_y=-8, arm_z=7,  fore_x=-10, hand_x=-12, hand_z=5),
        '_whip':  dict(arm_x=6,   arm_y=-5, arm_z=8,  fore_x=-12, hand_x=-5,  hand_z=6),
    }

    # Kinetic chain: hips lead the push-off and weight transfer; waist and chest follow smoothly 1 tick later;
    # head stays counter-stabilized directly onto the opponent throughout the entire 10-tick step.
    stages = [
        # Tick 0: committed first-frame lunge push-off + immediate off-hand dart throw release
        (0, -1.25, dict(
            hips=[12, -10, -2], waist=[8, -6, -1], chest=[12, -9, -2], neck=[-4, 6, 1], head=[-10, 12, 1],
            left_thigh=[34, 0, -6], right_thigh=[-14, 0, 6], left_shin=[-40, 0, 0],
            right_shin=[-28, 0, 0], left_foot=[12, 0, 0], right_foot=[18, 0, 0],
        )),
        # Tick 1.8: explosive peak extension, rear leg driving off toes, front knee reaching forward
        (1.8, -0.25, dict(
            hips=[15, -7, -2], waist=[10, -4, -2], chest=[15, -6, -3], neck=[-5, 4, 1], head=[-13, 10, 2],
            left_thigh=[58, 0, -7], right_thigh=[-48, 0, 7], left_shin=[-42, 0, 0],
            right_shin=[-14, 0, 0], left_foot=[-14, 0, 0], right_foot=[34, 0, 0],
        )),
        # Tick 3.4: apex glide — hips begin transferring forward (+yaw) ahead of chest!
        (3.4, 0.22, dict(
            hips=[13, 3, 1], waist=[8, -1, -1], chest=[13, -5, -2], neck=[-5, 2, 1], head=[-12, 5, 1],
            left_thigh=[50, 0, -7], right_thigh=[-34, 0, 7], left_shin=[-34, 0, 0],
            right_shin=[-30, 0, 0], left_foot=[-16, 0, 0], right_foot=[28, 0, 0],
        )),
        # Tick 4.8: smooth weight-shift follow-through — chest catches up to hips as rear leg gathers underneath
        (4.8, 0.16, dict(
            hips=[11, 8, 2], waist=[7, 6, 1], chest=[12, 9, 2], neck=[-4, -4, -1], head=[-11, -9, -1],
            left_thigh=[40, 0, -6], right_thigh=[-16, 0, 6], left_shin=[-26, 0, 0],
            right_shin=[-44, 0, 0], left_foot=[-15, 0, 0], right_foot=[22, 0, 0],
        )),
        # Tick 6.0: front-foot braking touchdown & deep knee cushion absorb
        (6.0, -0.82, dict(
            hips=[9, 5, 1], waist=[5, 3, 0], chest=[8, 5, 1], neck=[-3, -3, 0], head=[-8, -5, 0],
            left_thigh=[28, 0, -6], right_thigh=[12, 0, 6], left_shin=[-36, 0, 0],
            right_shin=[-26, 0, 0], left_foot=[12, 0, 0], right_foot=[14, 0, 0],
        )),
        # Tick 7.8: grounded deceleration & spring rise back toward poise
        (7.8, -0.32, dict(
            hips=[4, 2, 0], waist=[2, 1, 0], chest=[3, 2, 0], neck=[-1, -1, 0], head=[-4, -2, 0],
            left_thigh=[13, 0, -4], right_thigh=[6, 0, 4], left_shin=[-18, 0, 0],
            right_shin=[-13, 0, 0], left_foot=[6, 0, 0], right_foot=[7, 0, 0],
        )),
        (9.0, -0.05, dict(
            hips=[1, 0, 0], waist=[1, 0, 0], chest=[1, 0, 0], head=[-1, 0, 0],
            left_thigh=[3, 0, -2], right_thigh=[1, 0, 2], left_shin=[-4, 0, 0], right_shin=[-3, 0, 0],
        )),
        (10, 0, {}),
    ]

    impulse_curve = {0: 0.55, 1.8: 1.0, 3.4: 0.92, 4.8: 0.72, 6.0: 0.35, 7.8: 0.12, 9.0: 0.02, 10: 0.0}
    brake_curve = {0: 0.0, 1.8: 0.0, 3.4: 0.1, 4.8: 0.35, 6.0: 0.85, 7.8: 0.35, 9.0: 0.05, 10: 0.0}

    for suffix, carry in carries.items():
        inert = weapon_inertia[suffix]
        frames = []
        for tick, height, overrides in stages:
            p = pose(**overrides)
            p.update(copy.deepcopy(carry))
            imp = impulse_curve[tick]
            brk = brake_curve[tick]
            # Dynamic weapon-arm sweep back during acceleration, cushioning cleanly forward on brake!
            p['right_arm'][0] += inert['arm_x'] * imp + 6 * brk
            p['right_arm'][1] += inert['arm_y'] * imp + 3 * brk
            p['right_arm'][2] += inert['arm_z'] * imp
            p['right_forearm'][0] += inert['fore_x'] * imp - 5 * brk
            p['right_hand'][0] += inert['hand_x'] * imp + 5 * brk
            p['right_hand'][2] += inert['hand_z'] * imp
            # Crisp immediate off-hand dart flick (tick 0: left_arm X > 65 -> runtime X < -65),
            # expressive wrist snap at tick 1.8 (well within natural_free_hand bounds), sweeping back into counterbalance!
            offhand = {
                0:   ([88, 12, 14],  [-14, 0, -4],  [-26, 14, -8]),
                1.8: ([94, 18, 8],   [-6,  0, -2],  [-48, -12, 14]),
                3.4: ([46, 10, 16],  [-32, 0, -14], [-18, -6, 6]),
                4.8: ([12, -8, 22],  [-26, 0, -16], [6, -4, -4]),
                6.0: ([18, -4, 14],  [-22, 0, -10], [4, 0, -2]),
            }
            if tick in offhand:
                p['left_arm'], p['left_forearm'], p['left_hand'] = copy.deepcopy(offhand[tick])
            frames.append((tick, p, [0, height, 0]))
        finish(
            'step_approach' + suffix,
            frames,
            10,
            metadata={
                'prepare_ticks': [0, 0],
                'travel_ticks': [0, 8],
                'settle_ticks': [8, 10],
                'offhand_throw_tick': 0,
            },
        )
