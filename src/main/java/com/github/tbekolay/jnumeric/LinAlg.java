package com.github.tbekolay.jnumeric;

import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyType;
import org.python.util.PythonInterpreter;

public class LinAlg extends PyObject {
	private static final long serialVersionUID = 2167789225384727587L;

	/**
     * Simple constructor. No special logic.
	 * @return 
     */
	public LinAlg() {
		super(PyType.fromClass(LinAlg.class));
		this.javaProxy = this;
	}

	/**
     * Sets the appropriate random functions in the object's __dict__.
     * 
     * @param dict __dict__, which we want to modify.
     */
	public static void classDictInit(final PyObject dict) {
		dict.__setitem__("__doc__", new PyString("Linear algebra related functions"));
		
		dict.__setitem__("norm", LinAlg.norm);
	}
	
	/**
	 * norm(x, ord=None)
	 */
	static final public PyObject norm = new NormFunction();
}

final class LinAlgFunction extends PyObject {
	private static final long serialVersionUID = 2431304949411513723L;

	static public PyObject norm(final PyObject x, final PyObject ord) {
		final PyMultiarray a = PyMultiarray.asarray(x);
		final PythonInterpreter pint = new PythonInterpreter();
		
		if (a.dimensions.length == 1) {
			if (ord.equals(Py.None))
				return Py.newFloat(Math.sqrt(Py.py2float(Umath.add.reduce(a.__pow__(Py.newFloat(2))))));
			double o = Py.py2double(ord);
			if (o == Double.POSITIVE_INFINITY) {
				pint.set("x", a.__abs__());
				pint.exec("r = max(x)");
				return pint.get("r");
			}
			if (o == Double.NEGATIVE_INFINITY){
				pint.set("x", a.__abs__());
				pint.exec("r = min(x)");
				return pint.get("r");
			}
			if (o == 0){
				pint.set("x", a);
				pint.exec("r = sum(filter(lambda x: x != 0, x))");
				return pint.get("r");
			}
			// TODO: Implement sum(abs(x)**ord)**(1./ord)
			return Py.newFloat(Double.NaN);
		}
		else {
			if (ord.equals(Py.None)) {
				final PyMultiarray c = PyMultiarray.reshape(a, new int[] {-1});
				return Py.newFloat(Math.sqrt(Py.py2float(Umath.add.reduce(c.__abs__().__pow__(Py.newFloat(2))))));
			}
			double o = Py.py2double(ord);
			if (o == Double.POSITIVE_INFINITY) {
				pint.set("x", Umath.add.reduce(a.__abs__(), 1));
				pint.exec("r = max(x)");
				return pint.get("r");
			}
			if (o == Double.NEGATIVE_INFINITY) {
				pint.set("x", Umath.add.reduce(a.__abs__(), 1));
				pint.exec("r = min(x)");
				return pint.get("r");
			}
			if (o == 1){
				pint.set("x", Umath.add.reduce(a.__abs__(), 0));
				pint.exec("r = max(x)");
				return pint.get("r");
			}
			if (o == -1) {
				pint.set("x", Umath.add.reduce(a.__abs__(), 0));
				pint.exec("r = min(x)");
				return pint.get("r");
			}
			if (o == 2)
				// TODO: Implement smallest sing. value for matrix)
				return Py.newFloat(Double.NaN);
			if (o == -2)
				// TODO: Implement 2-norm (largest sing. value for matrix)
				return Py.newFloat(Double.NaN);
		}
		return Py.None;
	}
		
}

final class NormFunction extends KeywordFunction {
	private static final long serialVersionUID = -588523339866458789L;
	
	NormFunction() {
		this.docString = "norm(x, ord=None)";
		this.argNames = new String[] {"x", "ord"};
		this.defaultArgs = new PyObject[] {null, Py.None};
	}
	
	@Override public PyObject _call(final PyObject args[]) {
		return LinAlgFunction.norm(args[0], args[1]);
	}
}

