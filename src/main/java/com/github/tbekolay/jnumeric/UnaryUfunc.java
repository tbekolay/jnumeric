package com.github.tbekolay.jnumeric;

import java.lang.reflect.Array;

import org.python.core.Py;
import org.python.core.PyComplex;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyType;

// TODO Get rid of dependance on umath
// TODO Change format of calls to Complex(a, r) where result is stuff into r.
//      this way two argument ufuncs could be made to work correctly also.
// TODO Change __call__ signature to official JPython call signature.

/**
 * Function that accepts one PyMultiarray.
 */
public class UnaryUfunc extends PyObject {

    private static final long serialVersionUID = 5249208075619641728L;

    /**
     * Returns arccos(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction arccos = new Arccos();
    
    /**
     * Returns arccosh(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction arccosh = new Arccosh();
    
    /**
     * Returns arcsin(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction arcsin = new Arcsin();
    
    /**
     * Returns arcsinh(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction arcsinh = new Arcsinh();
    
    /**
     * Returns arctan(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction arctan = new Arctan();
    
    /**
     * Returns arctanh(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction arctanh = new Arctanh();
    
    /**
     * Returns ceil(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction ceil = new Ceil();
    
    /**
     * Returns conjugate(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction conjugate = new Conjugate();
    
    /**
     * Returns cos(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction cos = new Cos();
    
    /**
     * Returns cosh(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction cosh = new Cosh();
    
    /**
     * Returns exp(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction exp = new Exp();
    
    /**
     * Returns floor(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction floor = new Floor();
    
    /**
     * Returns imaginary(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction imaginary = new Imaginary();
    
    /**
     * Returns log(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction log = new Log();
    
    /**
     * Returns log10(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction log10 = new Log10();
    
    /**
     * Returns the logical inverse of a and stores the result in r if supplied.
     */
    static final public UnaryFunction logicalNot = new LogicalNot();
    
    /**
     * Returns real(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction real = new Real();
    
    /**
     * Returns sin(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction sin = new Sin();
    
    /**
     * Returns sinh(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction sinh = new Sinh();
    
    /**
     * Returns sqrt(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction sqrt = new Sqrt();
    
    /**
     * Returns tan(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction tan = new Tan();
    
    /**
     * Returns tanh(a) and stores the result in r if supplied.
     */
    static final public UnaryFunction tanh = new Tanh();

    UnaryFunction function;
    
    /**
     * Wraps a UnaryFunction into a universal function.
     * 
     * @param function The function to wrap
     */
    public UnaryUfunc(final UnaryFunction function) {
        super(PyType.fromClass(UnaryFunction.class));
        this.javaProxy = this;
        this.function = function;
    }

    @Override public PyObject __findattr_ex__(final String name) {
        if (name == "__doc__") {
            return new PyString(this.function.docString());
        }
        return super.__findattr_ex__(name);
    }

    @Override public PyObject __call__(final PyObject o) {
        // TODO rework to provide separate functions for all cases
        // TODO have default behaviour of most functions call 'd' and 'D'
        PyMultiarray result;
        final PyMultiarray a = PyMultiarray.asarray(o);
        switch (a._typecode) {
        case 'F':
            result = this.function.ComplexFloat(PyMultiarray.array(a));
            break;
        case 'D':
            result = this.function.ComplexDouble(PyMultiarray.array(a));
            break;
        case 'f':
            result = this.function.Float(PyMultiarray.array(a));
            break;
        case '1':
        case 's':
        case 'i':
        case 'l':
        case 'd':
            // while sensible, this is not what Numeric does!
            // result =
            // function.Double(PyMultiarray.array(a,'d')).astype(a._typecode);
            result = this.function.Double(PyMultiarray.array(a, 'd'));
            break;
        default:
            throw Py.ValueError("typecode must be in [1silfFdD]");
        }
        return PyMultiarray.returnValue(result);
    }


    /**
     * Two argument unary functions are provided for compatibility with
     * CNumeric. As implemented, they are no more efficient in memory usage
     * or speed than one argument unary functions (and perhaps less so).
     * 
     * @param o Input array
     * @param result Array to store the result
     * @return The result, which is the same as result
     */
    public PyObject __call__(final PyObject o, final PyMultiarray result) {
        PyMultiarray.copyAToB(PyMultiarray.asarray(this.__call__(o)), result);
        return result;
    }
}

class UnaryFunction {
    String docString() {
        return "unary_function(a, [,r])\n";
    }

    // Some constants that are useful when using complex numbers
    static final PyMultiarray cp1 = PyMultiarray.array(new PyComplex(1, 0));
    static final PyMultiarray cpj = PyMultiarray.array(new PyComplex(0, 1));
    static final PyMultiarray cn1 = PyMultiarray.array(new PyComplex(-1, 0));
    static final PyMultiarray cnj = PyMultiarray.array(new PyComplex(0, -1));
    static final PyMultiarray c0 = PyMultiarray.array(new PyComplex(0, 0));
    static final PyMultiarray cpj_2 = PyMultiarray.array(new PyComplex(0, 0.5));
    static final PyMultiarray cp1_2 = PyMultiarray.array(new PyComplex(0.5, 0));
    static final PyMultiarray cp2 = PyMultiarray.array(new PyComplex(2, 0));

    // a is used as scratch space by these functions,
    // so make sure to pass a copy!
    PyMultiarray Double(final PyMultiarray a) {
        throw Py.NotImplementedError("Double not implemented");
    }

    PyMultiarray ComplexDouble(final PyMultiarray a) {
        throw Py.NotImplementedError("ComplexDouble not implemented");
    }

    // This should be overridden by functions concerned with efficiency / speed.
    PyMultiarray Float(final PyMultiarray a) {
        return this.ComplexDouble(a.astype('d')).astype('f');
    }

    PyMultiarray ComplexFloat(final PyMultiarray a) {
        return this.ComplexDouble(a.astype('D')).astype('F');
    }
}

final class Arccos extends UnaryFunction {
    @Override String docString() {
        return "arccos(a [,r]) returns arccos(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.acos(Array.getDouble(a.data, i)));
        }
        return a;
    }

    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        double re, re1, re2, re3, re4;
        double im, im1, im2, im3, im4;
        double mag, phi;
        
        for (int i = 0; i < Array.getLength(a.data); i += 2) {
            // arccos(z) = -j*log(z + j*sqrt(1 - z**2)
            re = Array.getDouble(a.data, i);
            im = Array.getDouble(a.data, i + 1);
            re1 = 1 + im * im - re * re;
            im1 = -2 * im * re;
            mag = Math.pow(re1 * re1 + im1 * im1, 0.25);
            phi = Math.atan2(im1, re1) / 2.;
            re2 = mag * Math.cos(phi);
            im2 = mag * Math.sin(phi);
            re3 = re - im2;
            im3 = im + re2;
            re4 = Math.log(re3 * re3 + im3 * im3) / 2.;
            im4 = Math.atan2(im3, re3);
            Array.setDouble(a.data, i, im4);
            Array.setDouble(a.data, i + 1, -re4);
        }
        return a;
    }
}

final class Arccosh extends UnaryFunction {
    @Override String docString() {
        return "arccosh(a [,r]) returns arccosh(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            final double d = Array.getDouble(a.data, i);
            Array.setDouble(a.data, i, Math.log(d + Math.sqrt(d * d - 1)));
        }
        return a;
    }

    // TODO Rewrite without umath
    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        return PyMultiarray.asarray(Umath.log.__call__(a
                .__add__(UnaryFunction.cpj
                        .__mul__(Umath.sqrt.__call__(UnaryFunction.cp1
                                .__sub__(a.__mul__(a)))))));
    }
}

final class Arcsin extends UnaryFunction {
    @Override String docString() {
        return "arcsin(a [,r]) returns arcsin(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.asin(Array.getDouble(a.data, i)));
        }
        return a;
    }

    // TODO Rewrite without umath
    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        return PyMultiarray.asarray(UnaryFunction.cnj.__mul__(Umath.log
                .__call__(UnaryFunction.cpj.__mul__(
                        a).__add__(
                        Umath.sqrt.__call__(UnaryFunction.cp1.__sub__(a
                                .__mul__(a)))))));
    }
}

final class Arcsinh extends UnaryFunction {
    @Override String docString() {
        return "arcsinh(a [,r]) returns arcsinh(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            final double d = Array.getDouble(a.data, i);
            Array.setDouble(a.data, i, -Math.log(Math.sqrt(1 + d * d) - d));
        }
        return a;
    }

    // TODO Rewrite without umath
    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        return PyMultiarray.asarray(Umath.log.__call__(
                Umath.sqrt.__call__(UnaryFunction.cp1.__add__(a.__mul__(a)))
                        .__sub__(a))
                .__neg__());
    }
}

final class Arctan extends UnaryFunction {
    @Override String docString() {
        return "arctan(a [,r]) returns arctan(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.atan(Array.getDouble(a.data, i)));
        }
        return a;
    }

    // TODO Rewrite without umath
    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        return PyMultiarray.asarray(UnaryFunction.cpj_2.__mul__(Umath.log
                .__call__(UnaryFunction.cpj
                        .__add__(a).__div__(UnaryFunction.cpj.__sub__(a)))));
    }
}

final class Arctanh extends UnaryFunction {
    @Override String docString() {
        return "arctanh(a [,r]) returns arctanh(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            final double d = Array.getDouble(a.data, i);
            Array.setDouble(a.data, i, 0.5 * Math.log((1. + d) / (1. - d)));
        }
        return a;
    }

    // TODO Rewrite without umath
    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        return PyMultiarray.asarray(UnaryFunction.cp1_2.__mul__(Umath.log
                .__call__(UnaryFunction.cp1
                        .__add__(a).__div__(UnaryFunction.cp1.__sub__(a)))));
    }
}

final class Ceil extends UnaryFunction {
    @Override String docString() {
        return "ceil(a [,r]) returns ceil(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.ceil(Array.getDouble(a.data, i)));
        }
        return a;
    }
}

final class Conjugate extends UnaryFunction {
    @Override String docString() {
        return "conjugate(a [,r]) returns conjugate(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        return a;
    }

    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i += 2) {
            Array.setDouble(a.data, i + 1, -Array.getDouble(a.data, i + 1));
        }
        return a;
    }
}

final class Cos extends UnaryFunction {
    @Override String docString() {
        return "cos(a [,r]) returns cos(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.cos(Array.getDouble(a.data, i)));
        }
        return a;
    }

    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i += 2) {
            final double re = Array.getDouble(a.data, i), im = Array.getDouble(
                    a.data, i + 1);
            final double eim = Math.exp(im), cosre = Math.cos(re), sinre = Math
                    .sin(re);
            Array.setDouble(a.data, i, 0.5 * cosre * (eim + 1. / eim));
            Array.setDouble(a.data, i + 1, -0.5 * sinre * (eim - 1. / eim));
        }
        return a;
    }
}

final class Cosh extends UnaryFunction {
    @Override String docString() {
        return "cosh(a [,r]) returns cosh(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            final double d = Array.getDouble(a.data, i);
            final double ed = Math.exp(d);
            Array.setDouble(a.data, i, 0.5 * ed + 0.5 / ed);
        }
        return a;
    }

    // TODO Rewrite without umath
    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        final PyObject ea = Umath.exp.__call__(a);
        return PyMultiarray.asarray(UnaryFunction.cp1_2.__mul__(ea)
                .__add__(UnaryFunction.cp1_2.__div__(ea)));
    }
}

final class Exp extends UnaryFunction {
    @Override String docString() {
        return "exp(a [,r]) returns exp(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.exp(Array.getDouble(a.data, i)));
        }
        return a;
    }

    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i += 2) {
            final double re = Array.getDouble(a.data, i), im = Array.getDouble(
                    a.data, i + 1);
            final double ere = Math.exp(re), cosim = Math.cos(im), sinim = Math
                    .sin(im);
            Array.setDouble(a.data, i, ere * cosim);
            Array.setDouble(a.data, i + 1, ere * sinim);
        }
        return a;
    }
}

final class Floor extends UnaryFunction {
    @Override String docString() {
        return "floor(a [,r]) returns floor(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.floor(Array.getDouble(a.data, i)));
        }
        return a;
    }
}

final class Imaginary extends UnaryFunction {
    @Override String docString() {
        return "imaginary(a [,r]) returns imaginary(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        return PyMultiarray.zeros(a.dimensions, 'd');
    }

    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        return a.getImag();
    }
}

final class Log extends UnaryFunction {
    @Override String docString() {
        return "log(a [,r]) returns log(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.log(Array.getDouble(a.data, i)));
        }
        return a;
    }

    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i += 2) {
            final double re = Array.getDouble(a.data, i), im = Array.getDouble(
                    a.data, i + 1);
            Array.setDouble(a.data, i, Math.log(im * im + re * re) / 2.);
            Array.setDouble(a.data, i + 1, Math.atan2(im, re));
        }
        return a;
    }
}

final class Log10 extends UnaryFunction {
    @Override String docString() {
        return "log10(a [,r]) returns log10(a) and stores the result in r if supplied.\n";
    }

    final double log10 = Math.log(10);

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.log(Array.getDouble(a.data, i))
                    / this.log10);
        }
        return a;
    }

    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i += 2) {
            final double re = Array.getDouble(a.data, i), im = Array.getDouble(
                    a.data, i + 1);
            Array.setDouble(a.data, i, Math.log(Math.sqrt(im * im + re * re))
                    / this.log10);
            Array.setDouble(a.data, i + 1, Math.atan2(im, re) / this.log10);
        }
        return a;
    }
}

final class LogicalNot extends UnaryFunction {
    @Override String docString() {
        return "logical_not(a [,r]) returns the logical inverse of a and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        return PyMultiarray.asarray(a.__eq(Py.Zero));
    }

    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        return PyMultiarray.asarray(a.__eq(Py.Zero));
    }
}

final class Real extends UnaryFunction {
    @Override String docString() {
        return "real(a [,r]) returns real(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        return a;
    }

    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        return a.getReal();
    }
}

final class Sin extends UnaryFunction {
    @Override String docString() {
        return "sin(a [,r]) returns sin(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.sin(Array.getDouble(a.data, i)));
        }
        return a;
    }

    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i += 2) {
            final double re = Array.getDouble(a.data, i), im = Array.getDouble(
                    a.data, i + 1);
            final double eim = Math.exp(im), cosre = Math.cos(re), sinre = Math
                    .sin(re);
            Array.setDouble(a.data, i, 0.5 * sinre * (eim + 1. / eim));
            Array.setDouble(a.data, i + 1, 0.5 * cosre * (eim - 1. / eim));
        }
        return a;
    }
}

final class Sinh extends UnaryFunction {
    @Override String docString() {
        return "sinh(a [,r]) returns sinh(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            final double d = Array.getDouble(a.data, i);
            final double ed = Math.exp(d);
            Array.setDouble(a.data, i, 0.5 * ed - 0.5 / ed);
        }
        return a;
    }

    // TODO Rewrite without umath
    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        final PyObject ea = Umath.exp.__call__(a);
        return PyMultiarray.asarray(UnaryFunction.cp1_2.__mul__(ea)
                .__sub__(UnaryFunction.cp1_2.__div__(ea)));
    }
}

final class Sqrt extends UnaryFunction {
    @Override String docString() {
        return "sqrt(a [,r]) returns sqrt(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.sqrt(Array.getDouble(a.data, i)));
        }
        return a;
    }

    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i += 2) {
            final double re = Array.getDouble(a.data, i), im = Array.getDouble(
                    a.data, i + 1);
            final double mag = Math.pow(re * re + im * im, 0.25), phi = Math
                    .atan2(
                            im, re) / 2.;
            Array.setDouble(a.data, i, mag * Math.cos(phi));
            Array.setDouble(a.data, i + 1, mag * Math.sin(phi));
        }
        return a;
    }
}

final class Tan extends UnaryFunction {
    @Override String docString() {
        return "tan(a [,r]) returns tan(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            Array.setDouble(a.data, i, Math.tan(Array.getDouble(a.data, i)));
        }
        return a;
    }

    // TODO Rewrite without umath
    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        final PyMultiarray sina = Umath.sin.function.ComplexDouble(PyMultiarray
                .array(a));
        return PyMultiarray.asarray(sina.__div__(Umath.cos.function
                .ComplexDouble(a)));
    }
}

final class Tanh extends UnaryFunction {
    @Override String docString() {
        return "tanh(a [,r]) returns tanh(a) and stores the result in r if supplied.\n";
    }

    @Override public PyMultiarray Double(final PyMultiarray a) {
        for (int i = 0; i < Array.getLength(a.data); i++) {
            final double d = Array.getDouble(a.data, i);
            final double e2d = Math.exp(2 * d);
            Array.setDouble(a.data, i, (e2d - 1) / (e2d + 1));
        }
        return a;
    }

    // TODO Rewrite without umath
    @Override public PyMultiarray ComplexDouble(final PyMultiarray a) {
        final PyObject e2a = Umath.exp.__call__(UnaryFunction.cp2.__mul__(a));
        return PyMultiarray.asarray(e2a.__sub__(UnaryFunction.cp1).__div__(
                e2a.__add__(UnaryFunction.cp1)));
    }
}
