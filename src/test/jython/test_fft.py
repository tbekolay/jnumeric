from com.github.tbekolay.jnumeric.JNumeric import *
import unittest


class FFT_Test(unittest.TestCase):
    """Fast Fourier Transform module.
    Test basic cases:
     -  constant <--> delta function
     -  delta function <--> constant
     -  transform(inverse transform) = identity
    Test fancy things:
     -  correlation(A, B) should equal inverse_fft(product(fft(A), conjugate(fft(B))))
     -  correlation the slow way, should equal the built-in function
    Test weird things:
     -  anything that doesn't fall into the above categories.

    Names of test cases must start with string 'test' for this framework
    to work properly as it stands now. Not my idea, that's how it's set up.

    """
    
    
    len = 8
    a = None
    
    def setUp(self):
        """Hook function, called by all other tests,
        before running themselves."""
        self.len = 8
        self.a = ones(self.len)

    def testFFT(self):
        """Does fft() give the right answer?"""
        fft_ones = FFT.fft(ones(self.len))
        self.assertEqual(real(fft_ones[0]), float(self.len))
        self.assertEqual(imaginary(fft_ones[0]), 0)
        delta = zeros(self.len).astype(Float)
        # Transform of a real delta function should have all components equal to 1+0j.
        delta[0] = 1.0
        fft_delta = FFT.fft(delta)
        self.assertEqual(real(fft_delta), ones(self.len))
        self.assertEqual(imaginary(fft_delta), zeros(self.len))

    def testInverseFFT(self):
        """Inverse_fft() after fft() should return the original vector."""
        inverse_fft_fft_a = FFT.inverse_fft(FFT.fft(self.a))
        self.assertEqual(inverse_fft_fft_a, self.a)


if __name__ == "__main__":
    suite = unittest.TestLoader().loadTestsFromTestCase(FFT_Test)
    unittest.TextTestRunner(verbosity=2).run(suite)

