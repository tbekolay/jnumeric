from com.github.tbekolay.jnumeric.JNumeric import *
import unittest


class Other_Test(unittest.TestCase):
    """This suite tests functions that didn't fit into any of the other categories.
    Feel free to re-assign them if it makes more sense.

    """
    len = 8
    a = None
    
    def setUp(self):
        """Hook function, called by all other tests, before running themselves."""
        self.len = 100
        self.a = arange(self.len)


    def test_asarray(self):
        """Does asarray() work correctly?"""

            
    def test_diagonal(self):
        """Does diagonal() work correctly?"""
        msg = None
        self.len = 4
        self.assertEqual(diagonal(reshape(arange(100), (10, 10))), arange(0, 100, 11))
        self.assertEqual(diagonal(identity(self.len), 0), ones(self.len))
        for d in range(1, self.len):
            self.assertEqual(diagonal(identity(self.len), d), zeros(self.len - d))
            self.assertEqual(diagonal(identity(self.len), -d), zeros(self.len - d))
        for d in range(1, 5):
            if d < 3:
                self.assertEqual(len(diagonal(reshape(arange(9, -6, -1), (3, 5)), d)), 3)
            else:
                self.assertEqual(len(diagonal(reshape(arange(9, -6, -1), (3, 5)), d)), 3 - (d - 2))


    def test_fromfunction(self):
        """Does fromfunction() work correctly?"""


    def test_fromstring(self):
        """Does fromstring() work correctly?"""


    def test_indices(self):
        """Does indices() work correctly?"""


    def test_ravel(self):
        """Does ravel() work correctly?
        It should return the argument array as a one-dimensional array.
        It should be equivalent to reshape(a, (-1,)) or a.flat."""
        A = arange(100)
        A = reshape(A, (10, 10))
        self.assertEqual(ravel(A), A.flat)
        self.assertEqual(ravel(A), reshape(A, (-1,)))
        


    def test_shape(self):
        """Does shape() work correctly?"""


    def test_take(self):
        """Does take() work correctly?"""
        L = 100
        A = arange(L)
        self.assertEqual(take(A, arange(0, L, 2)), arange(0, L, 2))

    def test_transpose(self):
        """Does transpose() work correctly?"""
        L = 100
        Nrows, Ncols = 10, 10
        A = ones(100)
        B = reshape(A, (Nrows, Ncols))
        Bt = transpose(B)
        self.assertEqual(B, Bt)
        self.assertEqual(identity(L), transpose(identity(L)))

    def test_trace(self):
        """Does trace() work correctly?"""
        I = identity(self.len)
        self.assertEqual(trace(I), self.len)
        self.assertEqual(trace(I, 0), self.len)
        for i in range(1, self.len):
            self.assertEqual(trace(I, i), 0)
        

    def test_where(self):
        """Does where() work correctly?"""


    def test_bitwise_not(self):
        """Does bitwise_not() work correctly?"""


    def test_nonzero(self):
        """Does nonzero() work correctly?"""
        # Works only on 1D arrays, refuses other shapes.
        self.assertEqual(nonzero(self.a), self.a[1:])
        try:
            nonzero(ones((10, 10)))
        except ValueError, e:
            pass
        else:
            fail("Nonzero should throw ValueError for arrays which are not one-dimensional")
            

if __name__ == "__main__":
    suite = unittest.TestLoader().loadTestsFromTestCase(Other_Test)
    unittest.TextTestRunner(verbosity=2).run(suite)
