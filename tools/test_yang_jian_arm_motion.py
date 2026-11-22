"""Regression poses that previously folded a wrist back while keeping the weapon ahead."""
import math
import unittest

import numpy as np
from scipy.spatial.transform import Rotation

from yang_jian_arm_motion import (WRIST_LIMITS, forward, original_transform, retarget,
                                 forearm_matrix, joint_angles, rig, rotation)


class ArmMotionTest(unittest.TestCase):
    def test_weapon_reach_and_aim_survive_wrist_correction(self):
        # Original runtime shoulder xyz, elbow xyz, wrist xyz; frames from the
        # horizontal hit, throwing release, thrust, block, and advancing throw.
        cases = [
            ('right', (-77,19,-16,9,12,18,142,13,-16)),
            ('right', (-92,6,-15,2,0,18,152,0,0)),
            ('right', (-72,8,-16,8,0,18,118,0,0)),
            ('right', (-76,12,-8,43,0,18,34,0,52)),
        ]
        for side, old in cases:
            with self.subTest(side=side, pose=old):
                solved = np.array(retarget(side, old))
                self.assertTrue(all(abs(a)<=b+1e-6 for a,b in zip(solved[6:],WRIST_LIMITS)))
                old_position, old_rotation = original_transform(side,old)
                new_position, new_rotation, _ = forward(side,solved)
                # Old backward elbows cannot retain the old grip point exactly.
                # Permit a new reach, while keeping the weapon's aim close.
                self.assertLess(np.linalg.norm(new_position-old_position)/16,.50)
                orientation_error = Rotation.from_matrix(old_rotation.T@new_rotation).magnitude()
                self.assertLess(math.degrees(orientation_error),10)
                self.assertGreaterEqual(solved[3],0)
                self.assertLessEqual(abs(solved[4]),12.00001)

    def test_relaxed_carry_is_unchanged(self):
        original=(-4,-4,-8,10,0,18,0,0,0)
        solved=np.asarray(retarget('right',original))
        old_p,old_r=original_transform('right',original)
        p,r,_=forward('right',solved)
        np.testing.assert_allclose(p,old_p,atol=1e-8)
        np.testing.assert_allclose(r,old_r,atol=1e-8)

    def test_valid_floor_contact_pose_is_preserved(self):
        original=(-52,-8,-19,14,0,18,66,0,0)
        solved=np.asarray(retarget('right',original,True))
        old_p,old_r=original_transform('right',original)
        p,r,_=forward('right',solved)
        np.testing.assert_allclose(p,old_p,atol=1e-8)
        np.testing.assert_allclose(r,old_r,atol=1e-8)

    def test_free_hand_and_laser_are_corrected_even_without_extreme_wrists(self):
        for original in ((-6,0,8,10,0,-18,0,0,0),(-109,-15,12,69,6,-18,22,0,13)):
            solved=np.asarray(retarget('left',original))
            self.assertGreaterEqual(solved[3],8)
            self.assertLessEqual(abs(solved[4]),12)
            self.assertLessEqual(max(abs(solved[6:])),25)
            self.assertLessEqual(abs(solved[0]),95)

    def test_pronation_never_moves_the_wrist(self):
        for side in ('left','right'):
            lower=rig(side)[2]
            for flex in (5,45,90,125):
                baseline=forearm_matrix(side,(flex,8,0))@lower
                for twist in (-100,-40,40,100):
                    np.testing.assert_allclose(forearm_matrix(side,(flex,8,twist))@lower,baseline,atol=1e-8)

    def test_anatomical_round_trip_on_both_asymmetric_rigs(self):
        for side in ('left','right'):
            for values in ((5,8,35),(95,-12,-100),(125,12,100),(40,0,0)):
                matrix=forearm_matrix(side,values)
                np.testing.assert_allclose(forearm_matrix(side,joint_angles(side,matrix)),matrix,atol=1e-8)


if __name__=='__main__':
    unittest.main()
