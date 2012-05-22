
from Numeric import *
import unittest

class Arg_Test(unittest.TestCase):
    len = 8
    a = None
    
    def setUp(self):
        """Hook function, called by all other tests,
        before running themselves."""
        self.len = 8
        self.a = arange(1, 10)

    def test_Argmax(self):
        """Does argmax() work correctly?"""
        self.assertEqual(argmax(self.a), len(self.a) - 1)
    
    def test_Argmin(self):
        """Does argmin() work correctly?"""
        self.assertEqual(argmin(self.a), 0)
    

    
if __name__ == "__main__":
    unittest.main()

