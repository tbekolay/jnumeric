
from com.github.jnumeric.JNumeric import *
from FFT import *
import unittest

def cross_correlateByHand(a, b, mode=0):
    """Return cross-correlation of two vectors, a and b.
    Use FFT for efficiency. Format of result determined by value of 'mode'.
    This is a testbed to implement this function in JNumeric."""
    max_len = 2 * max(len(a), len(b))
    aa = zeros(2 * len(a)).astype(Float)
    aa[:len(a)] = a[:]
    bb = zeros(2 * len(b)).astype(Float)
    bb[:len(b)] = b[:]
    fft_a = FFT.fft(aa)
    fft_b = FFT.fft(bb)
    result = FFT.inverse_fft(fft_a * conjugate(fft_b)).real

    # Computation done, now copy result in to final in accordance with 'mode'
    final = zeros(len(result)).astype(Float)
    if mode == 0:
        final = array((result[0],))
    elif mode == 1:
        L = len(result)
        if L % 4 != 0:
            len4 = L / 4
            final = concatenate((result[3 * len4 + 2:], result[:len4 + 1]))
        else:
            len4 = L / 4
            final = concatenate((result[3 * len4:], result[:len4]))
    elif mode == 2:
        len2 = len(result) / 2
        final = concatenate((result[len2 + 1:], result[:len2]))
    return final

class CC_Test(unittest.TestCase):
    len = 8
    a = None
    
    def setUp(self):
        """Hook function, called by all other tests,
        before running themselves."""
        self.len = 8
        self.a = ones(self.len)

    def testModes(self):
        """Try each of the different modes: only 0, 1, and 2 are valid."""
        # Verify that the default mode is what we think it is.
        default_mode = 0
        self.assertEqual(cross_correlate(self.a, self.a), cross_correlate(self.a, self.a, default_mode))

        # Verify that the allowed modes work.
        for mode in 0, 1, 2:
            xcorr = cross_correlate(self.a, self.a, mode)

        # Check the non-valid modes are rejected.
        for mode in 3, -1:
            try:
                cross_correlate(self.a, self.a, mode)
            except:
                pass
            else:
                fail("expected a ValueError")

    def testValues(self):
        """Test the values returned by cross_correlate()."""

        # Cross-correlation is symmetric.
        b = self.a ** 2 - 3.0
        self.assertEqual(cross_correlate(self.a, b), cross_correlate(b, self.a))

        # Compute cross-correlation 'by hand', compare.
        # Need to use kludgy "array((1e-8,))" syntax to get around
        # "__len__ of zero-dimensional array" exception in Jython.
        tol = array((1e-8,))
        self.assert_(abs(cross_correlate(self.a, b) - cross_correlateByHand(self.a, b)) < tol)
        
        # Auto-correlation of a vector of ones equals the sum of squares of the components.
        # Check that it works for variety of different vector lengths: odd, even, powers of two, etc.
        for len in range(1, 50):
            x = ones(len)
            xc = cross_correlate(x, x)[0]
            self.assertEqual(xc, sum(x * x))


if __name__ == "__main__":
    unittest.main()

