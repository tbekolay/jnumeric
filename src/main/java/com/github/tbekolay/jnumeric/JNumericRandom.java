package com.github.tbekolay.jnumeric;

import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyType;

import java.lang.reflect.Array;
import java.util.Random;

public class JNumericRandom extends PyObject{
	private static final long serialVersionUID = -4304585345500259126L;
	
	/**
     * Simple constructor. No special logic.
	 * @return 
     */
	public JNumericRandom() {
		super(PyType.fromClass(JNumericRandom.class));
		this.javaProxy = this;
	}
	
	/**
     * Sets the appropriate random functions in the object's __dict__.
     * 
     * @param dict __dict__, which we want to modify.
     */
	public static void classDictInit(final PyObject dict) {
		dict.__setitem__("__doc__", new PyString("Random related functions"));
		
		dict.__setitem__("normal", JNumericRandom.normal);
	}
	
	/**
	 * normal(loc=0.0, scale=1.0, size=None)
	 */
	static final public PyObject normal = new NormalFunction();
}


// IMPLEMENTATIONS OF THE RANDOM FUNCTIONS
final class RandomFunction extends PyObject{
	private static final long serialVersionUID = 5723343986714030049L;

	static public PyMultiarray Normal(final float loc, final float scale, final Object size) {
	    int[] shape = PyMultiarray.objectToInts(size, true);
		int[] samples = {1};
		
		for (int i = 0; i < shape.length; i++) {
			samples[0] *= shape[i];
		}
		
		PyMultiarray a = PyMultiarray.zeros(samples, 'd');
		
		for (int i = 0; i < samples[0]; i++) {
			Array.setDouble(a.data, i, RandomFunction.NormalSample(loc, scale));
		}
		
		a = PyMultiarray.reshape(a, shape);
		return a;
	}
	
	static public double NormalSample(final float loc, final float scale) {
		Random rng = new Random();
		return scale * rng.nextGaussian() + loc; 
	}
}


// KEYWORD FUNCTIONS BELOW
final class NormalFunction extends KeywordFunction {
	private static final long serialVersionUID = -3862006095793888350L;

	NormalFunction() {
		this.docString = "normal(loc=0.0, scale=1.0, size=None";
		this.argNames = new String[] {"loc", "scale", "size" };
		this.defaultArgs = new PyObject[] {Py.newFloat(0), Py.newFloat(1), Py.None};
	}
	
	@Override public PyObject _call(final PyObject args[]) {
		if (args[2].equals(Py.None)) 
			return Py.newFloat(RandomFunction.NormalSample(Py.py2float(args[0]), Py.py2float(args[1])));
		else 
			return RandomFunction.Normal(Py.py2float(args[0]), Py.py2float(args[1]), args[2]);
	}
}
