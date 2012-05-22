
from Numeric import *
import unittest

# This suite tests functions that didn't fit into any of the other categories.
# Feel free to re-assign them if it makes more sense.

class Select_Test(unittest.TestCase):
    len = 8
    a = None
    
    def setUp(self):
        """Hook function, called by all other tests, before running themselves."""
        self.len = 10
        self.a = arange(self.len)


    def test_choose(self):
        """Does choose() work correctly?"""
#        print "\n\ntest_select: CHOOSE"
        limit = 23
        # All below the limit
        limit = 1000
        self.assert_(choose(greater(self.a, limit), (self.a, limit)) == self.a)
        # All above the limit
        limit = -1000
        self.assert_(choose(greater(self.a, limit), (self.a, limit))/limit == ones(len(self.a)))
            

    def test_clip(self):
        """Does clip() work correctly?"""
#        print "\n\ntest_select: CLIP"
        A = arange(9)
        min, max = 1.5, 7.5
        Aclip = clip(A, min, max)
        for a in Aclip:
            self.assert_(a >= min)
            self.assert_(a <= max)


    def test_compress(self):
        """Does compress() work?"""
#        print "\n\ntest_select: COMPRESS"
        L = len(self.a)
        self.assertEqual(compress(greater(self.a, 5.0*ones(L)), self.a, 0), arange(6,self.len))

    
    def test_where(self):
        """Does where() work correctly?"""
#        print "\n\ntest_select: WHERE"
        A = arange(9,-1,-1)
#        print "A = ", A
        try:
            self.assertEqual(where(equal(A,A), A, 0), A)
            self.assertEqual(where(A == A, A, 0), A)
            self.assertEqual(where(A > A, A, 0), zeros(len(A)))
        except Exception, e:
            pass
        else:
            self.fail("JNumeric doesn't handle symbolic relationships well in where().\n*** You may have fixed this problem. ***")

            



if __name__ == "__main__":
    unittest.main()








