import junit
import unittest
import inspect

class JNumericTestCase(junit.framework.TestCase, unittest.TestCase):
    def __init__(self):
        junit.framework.TestCase.__init__(self,self.__class__.__name__)
        
    @staticmethod
    def istest(method):
        return inspect.ismethod(method) and method.__name__.startwish('test')
    
    def runTest(self):
        tests = inspect.getmembers(self,JNumericTestCase.istest)
        for test in tests:
            self.__getattribute__(test)()
