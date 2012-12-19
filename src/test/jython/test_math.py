
from com.github.jnumeric.JNumeric import *
import unittest
import java.lang.Math
#from whrandom import *


class Math_Test(unittest.TestCase):
    """Test math functions."""
    len = 8
    a = None
    
    def setUp(self):
        """Hook function, called by all other tests,
        before running themselves."""
        self.a = ones(self.len)

    def testNonsense(self):
        self.assertEqual(log(1.0), 0.0) # Jython's log() hangs on integer args.

    def testNormal(self):
        """Do the functions work?"""
        self.assertEqual(log(1.0), 0.0)
        self.assertEqual(log(0.0), java.lang.Float.NEGATIVE_INFINITY)
        self.assertTrue(java.lang.Float.isNaN(log(-1.0))) 
        self.assertEqual(log(java.lang.Float.POSITIVE_INFINITY), java.lang.Float.POSITIVE_INFINITY)

#    def testInverses(self):
#        """Check that log(exp(x)) == x, asinh(sinh(z)) == z, etc."""
#        x = random()
#        print "X = ", x
#        inverses = (
#            (log, exp),
#            (sin, arcsin),
#            (cos, arccos),
#            (tan, arctan)
#            )

#        for f, g in inverses:
#        assert(2 == 1, ' not dual')
#            print g, "(x) = ", g(x)
#            print f, "(g(x)) = ", f(g(x))
#        assert(log(exp(x)) == x, 'Log(exp()) not dual')
#        assert(exp(log(x)) == x, 'Exp(log()) not dual')
#        assert(arcsin(sin(x)) == x, 'Arcsin(sin()) not dual')
#        assert(arccos(cos(x)) == x+1, 'Arccos(cos()) not dual')
        

if __name__ == "__main__":
    unittest.main()
