
from Numeric import *
import unittest

# The tests in this case are for functions which are really just
# abbreviations for combinations of other more elemental
# functions. Thus, alltrue is an abbreviation for 'reduce applied to
# logical and'.

class Abbrev_Test(unittest.TestCase):
    len = 8
    a = None
    false = 0
    true = 1

    
    def setUp(self):
        """Hook function, called by all other tests, before running themselves."""
        self.len = 8
        self.a = arange(1, 10)
        self.A = arange(50)
        self.Nrows, self.Ncols = 5, 10
        self.B = reshape(self.A, (self.Nrows, self.Ncols))
        self.mysum = zeros(self.Ncols)


    def test_sum(self):
        """Does sum() work correctly?"""
        # Make a rectangular array. Check that sum works on the columns.
        for c in range(self.Ncols):
            for r in range(self.Nrows):
                self.mysum[c] += self.B[r][c]
        self.assertEqual(sum(self.B), self.mysum)


    def test_cumsum(self):
        """Does cumsum() work correctly?"""


    def test_product(self):
        """Does product() work correctly?"""
        # Make a rectangular array. Check that sum works on the columns.
        self.mysum = ones(self.Ncols)
        for c in range(self.Ncols):
            for r in range(self.Nrows):
                self.mysum[c] *= self.B[r][c]
        self.assertEqual(product(self.B), self.mysum)

        
    def test_cumproduct(self):
        """Does cumproduct() work correctly?"""


    def test_alltrue(self):
        """Does alltrue() work correctly?"""
        self.assertEqual(alltrue(ones(self.len)), self.true) # All true
        self.assertEqual(alltrue(zeros(self.len)), self.false) # All false
        Z = zeros(self.len)
        Z[len(Z) / 2] = 1 
        self.assertNotEqual(alltrue(Z), self.true) # Not all true

        
    def test_sometrue(self):
        """Does sometrue() work correctly?"""
        self.assertEqual(sometrue(ones(self.len)), self.true) # All true
        self.assertEqual(sometrue(zeros(self.len)), self.false) # None true
        Z = zeros(self.len)
        Z[len(Z) / 2] = 1 
        self.assertEqual(sometrue(Z), self.true) # Some true


    def test_allclose(self):
        """Does allclose() work correctly?"""
        # Needs to be implemented in JNumeric
        try:        # These work under CNumeric
            self.assertEqual(allclose(ones(self.len), 0.999999), self.true)
            self.assertEqual(allclose(ones(self.len), 0.99), self.false)
        except:
            print "allclose() not yet implemented in JNumeric."
            pass
        else:
            fail("expected an exception for allclose()")
    
if __name__ == "__main__":
    unittest.main()








