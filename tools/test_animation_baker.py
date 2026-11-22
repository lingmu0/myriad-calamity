"""Run with ``python tools/test_animation_baker.py``; no third-party dependencies."""
import unittest

from animation_baker import bake, sample


def keys(times, values, mode='pchip'):
    return [{'time': time, 'values': list(value), 'mode': mode}
            for time, value in zip(times, values)]


class AnimationBakerTests(unittest.TestCase):
    def assert_bounded(self, curve, loop=False):
        for left, right in zip(curve, curve[1:]):
            for step in range(101):
                time = left['time']+(right['time']-left['time'])*step/100
                value = sample(curve, time, loop)
                for axis in range(3):
                    lo, hi = sorted((left['values'][axis], right['values'][axis]))
                    self.assertGreaterEqual(value[axis], lo-1e-12)
                    self.assertLessEqual(value[axis], hi+1e-12)

    def test_nonuniform_segments_never_overshoot(self):
        curve = keys([0, .013, .8, .84, 3.2, 4],
                     [(0, 20, -40), (120, 18, -20), (120, -10, 5),
                      (-15, -10, 17), (30, 40, 17), (0, 15, 90)])
        self.assert_bounded(curve)
        for key in curve:
            self.assertEqual(sample(curve, key['time']), key['values'])

    def test_constant_plateau_has_no_wobble(self):
        curve = keys([0, .3, .8, 1.1],
                     [(0, -5, 7), (80, 20, 7), (80, 20, 7), (-50, -5, 7)])
        for step in range(101):
            self.assertEqual(sample(curve, .3+.5*step/100), [80, 20, 7])

    def test_loop_values_and_shared_nonzero_seam_tangent(self):
        curve = keys([0, .2, .62, 1.1, 1.5],
                     [(0, 3, 7), (1, 3, 8), (0, 3, 7), (-2, 3, 6), (0, 3, 7)])
        self.assert_bounded(curve, loop=True)
        self.assertEqual(sample(curve, 0, True), sample(curve, 1.5, True))
        epsilon = 1e-6
        first = sample(curve, 0, True)
        after = sample(curve, epsilon, True)
        before = sample(curve, 1.5-epsilon, True)
        for axis in range(3):
            incoming = (first[axis]-before[axis])/epsilon
            outgoing = (after[axis]-first[axis])/epsilon
            self.assertAlmostEqual(incoming, outgoing, delta=1e-4)
        self.assertGreater((after[0]-first[0])/epsilon, 1)
        baked = bake(curve, 1.5, True)
        self.assertEqual(baked[0][1:], baked[-1][1:])

    def test_two_keys_and_single_key(self):
        curve = keys([1, 3], [(0, 20, -10), (10, 0, 10)])
        self.assertEqual(sample(curve, 2), [5, 10, 0])
        self.assertEqual(sample(curve[:1], 2), [0, 20, -10])
        flat_loop = keys([0, 2], [(4, 5, 6), (4, 5, 6)])
        self.assertEqual(sample(flat_loop, 1, True), [4, 5, 6])

    def test_open_curve_cannot_be_claimed_as_a_continuous_loop(self):
        curve = keys([0, 1, 2], [(0, 0, 0), (2, 0, 0), (1, 0, 0)])
        with self.assertRaisesRegex(ValueError, 'equal first and final'):
            sample(curve, .5, True)

    def test_existing_modes_are_unchanged(self):
        values = [(0, 0, 0), (10, 10, 10), (10, 10, 10), (0, 0, 0)]
        self.assertEqual(sample(keys([0, 1, 2, 3], values, 'catmullrom'), 1.5),
                         [11.25, 11.25, 11.25])
        self.assertEqual(sample(keys([0, 1, 2, 3], values, 'linear'), .5), [5, 5, 5])
        self.assertEqual(sample(keys([0, 1, 2, 3], values, 'step'), .5), [0, 0, 0])


if __name__ == '__main__':
    unittest.main()
