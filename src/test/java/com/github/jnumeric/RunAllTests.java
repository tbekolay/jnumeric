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

import org.python.util.PythonInterpreter;

/**
 * Java version of test/python/run_all_tests.py
 * 
 * @author Trevor Bekolay
 */
public final class RunAllTests {
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
			testfiles = RunAllTests.getAllTests(testdir);
		} catch(FileNotFoundException e) {
			System.out.println(e.getMessage());
			return;
		}
		
		RunAllTests.runAllTests(testfiles, verbose);
	}
	
	public static void main(String[] args) {
		boolean verbose = (args.length > 0 && args[0].indexOf('v') != -1);
		RunAllTests.run(verbose);
	}
}
