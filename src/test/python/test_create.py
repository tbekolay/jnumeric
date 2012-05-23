
from com.github.jnumeric.JNumeric import *
import unittest

class Create_Test(unittest.TestCase):
    len = 8
    a = None
    
    def setUp(self):
        """Hook function, called by all other tests, before running themselves."""
        self.len = 8
        self.a = arange(1, 10)

    def test_identity(self):
        """Does identity() work correctly?"""
        I = identity(self.len)

        for i in range(len(I)): # Is it square? Check each row
            self.assertEqual(len(I), len(I[i])) 

        for i in range(len(I)): # Check value of each element
            for j in range(len(I)):
                if i == j: self.assertEqual(I[i][j], 1)
                else: self.assertEqual(I[i][j], 0)


    def test_ones(self):
        """Does ones() work correctly?"""
        O = ones(self.len)
        for o in O:
            self.assertEqual(o, 1)


    def test_zeros(self):
        """Does zeros() work correctly?"""
        Z = zeros(self.len)
        for z in Z:
            self.assertEqual(z, 0)

            
    def test_arrayrange(self):
        """Does arrayrange() work correctly?"""


    def test_arange(self):
        """Does arange() work correctly?
        It's a synonym for arrayrange()."""


    def test_array(self):
        """Does array() work correctly?
        It takes a list and turns it into an array."""

    
if __name__ == "__main__":
    unittest.main()








