
# This file is intended to test functions used to build arrays from existing arrays.

from com.github.jnumeric.JNumeric import *
import unittest

class Build_Test(unittest.TestCase):
    len = 8
    a = None
    
    def setUp(self):
        """Hook function, called by all other tests, before running themselves."""
        self.len = 100
        self.a = arange(self.len)


    def test_concatenate(self):
        """Does concatenate() work correctly?"""
        b = concatenate((self.a, self.a, self.a))
        self.assertEqual(len(b), 3 * len(self.a))
        self.assertEqual(concatenate((ones(self.len), ones(self.len), ones(self.len))), ones(3 * self.len))
        
        
    def test_reshape(self):
        """Does reshape() work correctly?"""
        # Number of elements must be same in old and new.
        b = reshape(self.a, (5, 20))
        self.assertEqual(len(self.a), len(b) * len(b[0]))
        # Should get exception if try to reshape to different number of elements.
        try: 
            b = reshape(self.a, (5, 19))
        except Exception, e:
#            print "\nException thrown: ", e
            pass
        else:
            fail("reshape() should throw an exception if old and new shapes not congruent.")


        
    def test_resize(self):
        """Does resize() work correctly?"""
        # Number of elements does not have to be same in old and new.
        b = resize(self.a, (5, 10))
        self.assert_(len(self.a) >= len(b) * len(b[0]))

        
    def test_repeat(self):
        """Does repeat() work correctly?"""


    
if __name__ == "__main__":
    unittest.main()








