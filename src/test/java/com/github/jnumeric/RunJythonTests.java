/**
* JNumeric - a Jython port of Numerical Java
* Current Maintainer: Daniel Lemire, Ph.D.
* (c) 1998, 1999 Timothy Hochberg, tim.hochberg@ieee.org
*
* Free software under the Python license, see http://www.python.org
* Home page: http://jnumerical.sourceforge.net
*
*/

package com.github.jnumeric;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.Stack;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.python.util.PythonInterpreter;

/**
 * Java version of test/python/run_all_tests.py
 * 
 * @author Trevor Bekolay
 */
public final class RunJythonTests extends TestCase {
	private static final PythonInterpreter interp = new PythonInterpreter();

	private static File[] getAllTests(File testdir) throws FileNotFoundException {
		if (!testdir.exists() || !testdir.isDirectory()) {
			throw new FileNotFoundException(testdir.getPath() +
					" does not exist or is not a directory.");
		}
		
		return testdir.listFiles(new FilenameFilter() {
			public boolean accept(File d, String s) {
				return s.startsWith("test") && s.endsWith(".py");
			}
		});
	}

	private static void runAllTests(File[] testFiles, Boolean verbose) {
		if (testFiles == null || testFiles.length == 0) {
			System.out.println("No test files provided. Exiting.");
			return;
		}
		
		for (File test : testFiles) {
			if (verbose) {
				System.out.println("Running '"+test.getPath()+"'");
			}
			interp.execfile(test.getAbsolutePath());
		}
	}
	
	public static void run(boolean verbose) {
		File testdir = new File(System.getProperty("user.dir"),
								"src/test/python");
		File[] testfiles;
		if (verbose) {
		    System.out.println("Searching for test files in '" +
		                       testdir.getPath()+ "'");
		}
		
		try {
			testfiles = RunJythonTests.getAllTests(testdir);
		} catch(FileNotFoundException e) {
			System.out.println(e.getMessage());
			return;
		}
		
		RunJythonTests.runAllTests(testfiles, verbose);
	}
	
	public static void main(String[] args) {
		boolean verbose = (args.length > 0 && args[0].indexOf('v') != -1);
		RunJythonTests.run(verbose);
	}
}

/** OTHER STUFF TO TRY **/

class JythonTestSuiteExtractor extends TestSuite {

	/**
	 * 
	 * @param fileDir
	 *            be careful : with slashes, not backslashes !!!
	 * @param module
	 *            to be scanned for tests
	 * @param test
	 *            the class from which getting the test methods
	 */
	public JythonTestSuiteExtractor(String fileDir, String module, String test) {
		File f = new File(fileDir);
		String s = null;
		PythonInterpreter interp = new PythonInterpreter();

		interp.exec("import sys, pprint, junit");
		interp.exec("sys.path.append (\"" + fileDir + "\")");

		try {
			interp.exec("from " + module + " import " + test);
		} catch (Exception e) {
			System.err.println("Couldn't extract module " + module
					+ " from test " + test + " in file " + fileDir
					+ "; now running in " + System.getProperty("user.dir"));
			return;
		}
		interp.exec("suite = junit.framework.TestSuite()");
		interp.exec("[suite.addTest( " + test + "(f) ) for f in dir(" + test
				+ ") if f.startswith(\"test\")]");
		TestSuite suite = (TestSuite) interp.get("suite").__tojava__(
				TestSuite.class);
		addTest(suite);
	}
}

/**
 * @author ploix
 * 
 * Wrapper around the "real" python test cases. This class gets its tests from
 * the python sources that are defined in the stack. Before calling suite(), the
 * stack should be filled in with tests : directory, module, class name.
 */
class JythonTestCaseExtractor extends TestCase {

    /*
     * Stack used to store the tests to get from python code
     */
    static protected Stack<TestDesc> __stack = new Stack<TestDesc> ();

    /*
     * inner class to wrap the test descriptions 
     * 
     * @author ploix
     */
    static class TestDesc {
        public TestDesc(String dir, String module, String test) {
            __dir = dir;
            __module = module;
            __test = test;
        }

        public String getDir() {
            return __dir;
        }

        public String getModule() {
            return __module;
        }

        public String getTest() {
            return __test;
        }

        private String __dir = null;

        private String __module = null;

        private String __test = null;
    }

    /**
     * One should use the function above to generate its tests.
     * 
     * @param dir :
     *            file directory for python, but slashes in the name.
     * @param module :
     *            module name (without the .py)
     * @param test :
     *            python class name
     */
    public static void addPythonTest(String dir, String module, String test) {
        __stack.push(new TestDesc(dir, module, test));
    }

    public static Test suite() {
        TestDesc desc = null;
        TestSuite suite = new TestSuite();

        while (!__stack.isEmpty()) {
            desc = (TestDesc) (__stack.pop());

            TestSuite localSuite = new JythonTestSuiteExtractor(desc.getDir(),
                    desc.getModule(), desc.getTest());
            suite.addTest(localSuite);
        }
        /*
         * If no test has been added
         */
        if (suite.countTestCases() == 0)
            suite.addTest(new JythonTestCaseExtractor("testPythonIsEmpty"));
        return suite;
    }

    public void testPythonIsEmpty() {
        System.err.println("No test methods were found in class " + getClass());
        fail();
    }

    public JythonTestCaseExtractor(String name) {
        super(name);
    }
}

/**
 * To use a python test case inside JUnit, you need to define a class that
 * implements 2 (horrible) things : a static block and a suite method. The
 * reason comes from the way JUnit works, I'm innocent ;-). So define your class
 * that extends TestCase, and do it that way : the static block should add the
 * python tests with directory, module name and class name. The suite block
 * should call JythonTestCaseExtractor.suite(), that's all. God, it was a mess.
 * Oh, and if you want to integrate it inside another Suite, you shoud call
 * suite.addTest(MyJythonTestCase.suite()), and not
 * suite.addTestSuite(StreamsThreadedBridgeTest.class), which doesn't work.
 * 
 * @author ploix
 * 
 *  
 */
class MyJythonTestCase extends TestCase {

    static {
        JythonTestCaseExtractor.addPythonTest("bin/tests",
                "JythonTestSuite", "PythonTestCase");
    } 

    public static Test suite() {
        return JythonTestCaseExtractor.suite();
    }

}

class AllTests extends TestCase {

    public static Test suite() { 
        TestSuite suite = new TestSuite("Jython tests");
        suite.addTest(MyJythonTestCase.suite());
        return suite; 
    }
}