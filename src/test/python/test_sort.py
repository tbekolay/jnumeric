
from com.github.jnumeric.JNumeric import *
import unittest

class Sort_Test(unittest.TestCase):
    len = 8
    a = None
    
    def setUp(self):
        """Hook function, called by all other tests,
        before running themselves."""
        self.len = 8
        self.a = arange(1, 10)

    def test_sort(self):
        """Does sort() work correctly?"""
        # Sorting an already sorted array gives the array back again.
        self.assertEqual(self.a, sort(self.a))

        # Sorting an array of identical elements should give the same array back again.
        self.assertEqual(ones(self.len), sort(ones(self.len)))

        # Sorting an array of disordered elements should give the same elements in sorted order.
        b = (23, 10, -3, 0, 32190, -1.1, 0.3, 111.111)
        sort_b = sort(b)
        self.assertEqual(len(b), len(sort(b))) # Same length
        for i in range(len(b)):
            self.assert_(b[i] in sort_b) # Same contents
            if i + 1 != len(b): self.assert_(sort_b[i] < sort_b[i + 1]) # Correct order
        
        
    def test_argsort(self):
        """Does argsort() work correctly?"""
        self.assertEqual(arange(len(self.a)), argsort(self.a))

    def test_searchsorted(self):
        """Does searchsorted() work correctly?"""
        middle_index = len(self.a) / 2
        middle_element = self.a[middle_index]
        self.assertEqual(searchsorted(self.a, middle_element), middle_index)
    
if __name__ == "__main__":
    unittest.main()

