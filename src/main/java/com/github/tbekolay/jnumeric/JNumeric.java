/**
 * JNumeric - a Jython port of Numerical Java
 * Current Maintainer: Daniel Lemire, Ph.D.
 * (c) 1998, 1999 Timothy Hochberg, tim.hochberg@ieee.org
 * 
 * Free software under the Python license, see http://www.python.org
 * Home page: http://jnumerical.sourceforge.net
 * 
 */

package com.github.tbekolay.jnumeric;

import org.python.core.ClassDictInit;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyTuple;
import org.python.core.PyType;

/**
 * The main interface to JNumeric.
 * 
 * Encapsulates all of the Umath and FFT functions.
 */
public class JNumeric extends PyObject implements ClassDictInit {

    private static final long serialVersionUID = 6654647494727325270L;

    /**
     * Simple constructor, no special logic.
     */
    public JNumeric() {
        super(PyType.fromClass(JNumeric.class));
        this.javaProxy = this;
    }

    /**
     * Set up the class's __dict__.
     * 
     * @param dict The default __dict__ to modify
     */
    public static void classDictInit(final PyObject dict) {

        // import modules
        final Umath umath = new Umath();
        dict.__setitem__("umath", umath);
        dict.__setitem__("FFT", new FFT());
        dict.__setitem__("random", new JNumericRandom());
        dict.__setitem__("linalg", new LinAlg());

        dict.__setitem__("__doc__", Py.newString(JNumeric.__doc__));
        dict.__setitem__("__version__", Py.newString("0.2a6"));

        // from umath import * (more or less).
        Umath.classDictInit(dict);
        // from random import * (more or less).
        JNumericRandom.classDictInit(dict);
        // from linalg import * (more or less).
        LinAlg.classDictInit(dict);

        // constants

        dict.__setitem__("pi", Py.newFloat(java.lang.Math.PI));
        dict.__setitem__("e", Py.newFloat(java.lang.Math.E));
        dict.__setitem__("inf", Py.newFloat(Double.POSITIVE_INFINITY));

        dict.__setitem__("Int8", Py.newString("1"));
        dict.__setitem__("Int16", Py.newString("s"));
        dict.__setitem__("Int32", Py.newString("i"));
        dict.__setitem__("Int64", Py.newString("l"));
        
        // I'm using Int32 here because that is the native JPython integer type
        // and the default type for
        // an integer multiarray. The documentation claims this should be "the
        // largest version of the
        // given type," but I feel this is more natural. This will have to be
        // hashed out.
        dict.__setitem__("Int", Py.newString("i"));
        dict.__setitem__("Float32", Py.newString("f"));
        dict.__setitem__("Float64", Py.newString("d"));
        dict.__setitem__("Float", Py.newString("d"));
        dict.__setitem__("Complex64", Py.newString("F"));
        dict.__setitem__("Complex128", Py.newString("D"));
        dict.__setitem__("Complex", Py.newString("D"));

        dict.__setitem__("ArrayType", PyMultiarray.ATYPE);
        dict.__setitem__("NewAxis", Py.None);
        if (Py.py2int(PyMultiarray.fromString(
                "\001\000\000\000\000\000\000\000",
                'i').get(0)) == 1) {
            dict.__setitem__("LittleEndian", Py.One);
        } else {
            dict.__setitem__("LittleEndian", Py.Zero);
        }

        // numeric functions

        dict.__setitem__("arrayrange", JNumeric.arrayrange);
        dict.__setitem__("arange", JNumeric.arrayrange);
        dict.__setitem__("argmax", JNumeric.argmax);
        dict.__setitem__("argsort", JNumeric.argsort);
        dict.__setitem__("argmin", JNumeric.argmin);
        dict.__setitem__("array", JNumeric.array);
        dict.__setitem__("asarray", JNumeric.asarray);
        dict.__setitem__("bitwise_not", JNumeric.bitwise_not);
        dict.__setitem__("choose", JNumeric.choose);
        dict.__setitem__("clip", JNumeric.clip);
        dict.__setitem__("compress", JNumeric.compress);
        dict.__setitem__("concatenate", JNumeric.concatenate);
        dict.__setitem__("convolve", JNumeric.convolve);
        dict.__setitem__("cross_correlate", JNumeric.cross_correlate);
        dict.__setitem__("diagonal", JNumeric.diagonal);
        dict.__setitem__("dot", JNumeric.dot);
        dict.__setitem__("fromfunction", JNumeric.fromfunction);
        dict.__setitem__("fromstring", JNumeric.fromstring);
        dict.__setitem__("identity", JNumeric.identity);
        dict.__setitem__("indices", JNumeric.indices);
        dict.__setitem__("innerproduct", JNumeric.innerproduct);
        dict.__setitem__("linspace", JNumeric.linspace);
        dict.__setitem__("nonzero", JNumeric.nonzero);
        dict.__setitem__("ones", JNumeric.ones);
        dict.__setitem__("repeat", JNumeric.repeat);
        dict.__setitem__("reshape", JNumeric.reshape);
        dict.__setitem__("resize", JNumeric.resize);
        dict.__setitem__("ravel", JNumeric.ravel);
        dict.__setitem__("searchsorted", JNumeric.searchsorted);
        dict.__setitem__("shape", JNumeric.shape);
        dict.__setitem__("sort", JNumeric.sort);
        dict.__setitem__("take", JNumeric.take);
        dict.__setitem__("trace", JNumeric.trace);
        dict.__setitem__("transpose", JNumeric.transpose);
        dict.__setitem__("where", JNumeric.where);
        dict.__setitem__("zeros", JNumeric.zeros);

        // Abbreviations

        dict.__setitem__("sum", JNumeric.sum);
        dict.__setitem__("cumsum", JNumeric.cumsum);
        dict.__setitem__("product", JNumeric.product);
        dict.__setitem__("cumproduct", JNumeric.cumproduct);
        dict.__setitem__("alltrue", JNumeric.alltrue);
        dict.__setitem__("sometrue", JNumeric.sometrue);
    }

    // Numeric functions

    /**
     * arrayrange(start=0, stop, step=1, typecode=None)
     */
    static final public PyObject arrayrange = new ArrayrangeFunction();
    /**
     * argmax(a, axis=-1)
     */
    static final public PyObject argmax = new ArgmaxFunction();
    /**
     * argsort(a, axis=-1)
     */
    static final public PyObject argsort = new ArgsortFunction();
    /**
     * argsort(a, axis=-1)
     */
    static final public PyObject argmin = new ArgminFunction();
    /**
     * array(sequence, typecode=None, copy=1)
     */
    static final public PyObject array = new ArrayFunction();
    /**
     * asarray(sequence, typecode=None)
     */
    static final public PyObject asarray = new AsarrayFunction();
    /**
     * bitwise_not(a)
     */
    static final public PyObject bitwise_not = new BitwiseNotFunction();
    /**
     * choose(a, indices)
     */
    static final public PyObject choose = new ChooseFunction();
    /**
     * clip(a, a_min, a_max)
     */
    static final public PyObject clip = new ClipFunction();
    /**
     * compress(condition, a, [dimension=-1])
     */
    static final public PyObject compress = new CompressFunction();
    /**
     * concatenate(arrays, axis=0)
     */
    static final public PyObject concatenate = new ConcatenateFunction();
    /**
     * convolve(a, b, mode=0)
     */
    static final public PyObject convolve = new ConvolveFunction();
    /**
     * cross_correlate(a, b, mode=0)
     */
    static final public PyObject cross_correlate = new Cross_correlateFunction();
    /**
     * diagonal(a, offset=0, axis=-2)
     */
    static final public PyObject diagonal = new DiagonalFunction();
    /**
     * dot(a, b, axisA=-1, axisB=0)
     */
    static final public PyObject dot = new DotFunction();
    /**
     * fromfunction(function, dimensions)
     */
    static final public PyObject fromfunction = new FromfunctionFunction();
    /**
     * fromstring(string, typecode)
     */
    static final public PyObject fromstring = new FromstringFunction();
    /**
     * identity(n)
     */
    static final public PyObject identity = new IdentityFunction();
    /**
     * indices(dimensions, typecode=None)
     */
    static final public PyObject indices = new IndicesFunction();
    /**
     * innerproduct(a, b, axisA=-1, axisB=-1)
     */
    static final public PyObject innerproduct = new InnerproductFunction();
    /**
     * nonzero(a)
     */
    static final public PyObject nonzero = new NonzeroFunction();
    /**
     * linspace(start, stop, num=50, endpoint=True, retstep=False)
     */
    static final public PyObject linspace = new LinspaceFunction();
    /**
     * ones(shape, typecode=None)
     */
    static final public PyObject ones = new OnesFunction();
    /**
     * repeat(a, repeats, axis=0)
     */
    static final public PyObject repeat = new RepeatFunction();
    /**
     * reshape(a, shape)
     */
    static final public PyObject reshape = new ReshapeFunction();
    /**
     * resize(a, shape)
     */
    static final public PyObject resize = new ResizeFunction();
    /**
     * ravel(a)
     */
    static final public PyObject ravel = new RavelFunction();
    /**
     * searchsorted(a, values)
     */
    static final public PyObject searchsorted = new SearchsortedFunction();
    /**
     * shape(a)
     */
    static final public PyObject shape = new ShapeFunction();
    /**
     * sort(a, axis=-1)
     */
    static final public PyObject sort = new SortFunction();
    /**
     * take(a, indices, axis=-1)
     */
    static final public PyObject take = new TakeFunction();
    /**
     * trace(a, offset=0, axis1=-2, axis1=-1)
     */
    static final public PyObject trace = new TraceFunction();
    /**
     * transpose(a, axes=None)
     */
    static final public PyObject transpose = new TransposeFunction();
    /**
     * where(condition, x, y)
     */
    static final public PyObject where = new WhereFunction();
    /**
     * zeros(shape, typecode=None)
     */
    static final public PyObject zeros = new ZerosFunction();

    // Abbreviations

    /**
     * sum(a, [axis])
     */
    static final public PyObject sum = new SumFunction();
    /**
     * cumsum(a, [axis])
     */
    static final public PyObject cumsum = new CumsumFunction();
    /**
     * product(a, [axis])
     */
    static final public PyObject product = new ProductFunction();
    /**
     * cumproduct(a, [axis])
     */
    static final public PyObject cumproduct = new CumproductFunction();
    /**
     * alltrue(a, [axis])
     */
    static final public PyObject alltrue = new AlltrueFunction();
    /**
     * sometrue(a, [axis])
     */
    static final public PyObject sometrue = new SometrueFunction();

    /**
     * DocString (JNumeric -- Numeric for the Jython platform)
     */
    static final public String __doc__ = "JNumeric -- Numeric for the Jython platform\n";
}

final class ArrayrangeFunction extends KeywordFunction {
    private static final long serialVersionUID = 5746081813573451761L;

    ArrayrangeFunction() {
        this.docString = "arrayrange(start=0, stop, step=1, typecode=None)";
        this.argNames = new String[] { "start", "stop", "step", "typecode" };
        this.defaultArgs = new PyObject[] {
                null,
                Py.None,
                Py.One,
                Py.newString("\0") };
    }

    @Override public PyObject _call(final PyObject args[]) {
        if (args[3].equals(Py.None)) {
            args[3] = this.defaultArgs[3];
        }
        return PyMultiarray.arrayRange(
                args[0],
                args[1],
                args[2],
                Py.py2char(args[3]));
    }
}

final class ArgmaxFunction extends KeywordFunction {
    private static final long serialVersionUID = -4129702317640731140L;

    ArgmaxFunction() {
        this.docString = "argmax(a, axis=-1)";
        this.argNames = new String[] { "a", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.newInteger(-1) };
    }

    static final BinaryUfunc argmax_ = new BinaryUfunc(BinaryUfunc.argMax);

    @Override public PyObject _call(final PyObject args[]) {
        return ArgmaxFunction.argmax_.reduce(args[0], Py.py2int(args[1]));
    }
}

final class ArgsortFunction extends KeywordFunction {
    private static final long serialVersionUID = 306756599399721463L;

    ArgsortFunction() {
        this.docString = "argsort(a, axis=-1)";
        this.argNames = new String[] { "a", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.newInteger(-1) };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.argSort(args[0], Py.py2int(args[1]));
    }
}

final class ArgminFunction extends KeywordFunction {
    private static final long serialVersionUID = 8745320868186865391L;

    ArgminFunction() {
        this.docString = "argmin(a, axis=-1)";
        this.argNames = new String[] { "a", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.newInteger(-1) };
    }

    static final BinaryUfunc argmin_ = new BinaryUfunc(BinaryUfunc.argMin);

    @Override public PyObject _call(final PyObject args[]) {
        return ArgminFunction.argmin_.reduce(args[0], Py.py2int(args[1]));
    }
}

final class ArrayFunction extends KeywordFunction {
    private static final long serialVersionUID = 3022516972317954103L;

    ArrayFunction() {
        this.docString = "array(sequence, typecode=None, copy=1)";
        this.argNames = new String[] { "sequence", "typecode", "copy" };
        this.defaultArgs = new PyObject[] { null, Py.newString("\0"), Py.One };
    }

    @Override public PyObject _call(final PyObject args[]) {
        if (args[1].equals(Py.None)) {
            args[1] = this.defaultArgs[1];
        }
        if (args[2].__nonzero__()) {
            return PyMultiarray.array(args[0], Py.py2char(args[1]));
        } else {
            return PyMultiarray.asarray(args[0], Py.py2char(args[1]));
        }
    }
}

final class AsarrayFunction extends KeywordFunction {
    private static final long serialVersionUID = 4963183512231760555L;

    AsarrayFunction() {
        this.docString = "asarray(sequence, typecode=None)";
        this.argNames = new String[] { "sequence", "typecode" };
        this.defaultArgs = new PyObject[] { null, Py.newString("\0") };
    }

    @Override public PyObject _call(final PyObject args[]) {
        if (args[1].equals(Py.None)) {
            args[1] = this.defaultArgs[1];
        }
        return PyMultiarray.asarray(args[0], Py.py2char(args[1]));
    }
}

final class BitwiseNotFunction extends KeywordFunction {
    private static final long serialVersionUID = 733464928332987972L;

    BitwiseNotFunction() {
        this.docString = "bitwise_not(a)";
        this.argNames = new String[] { "a" };
        this.defaultArgs = new PyObject[] { null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.asarray(args[0]).__not__();
    }
}

final class ChooseFunction extends KeywordFunction {
    private static final long serialVersionUID = 1636534146777194754L;

    ChooseFunction() {
        this.docString = "choose(a, indices)";
        this.argNames = new String[] { "a", "indices" };
        this.defaultArgs = new PyObject[] { null, null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.choose(args[0], args[1]);
    }
}

final class ClipFunction extends KeywordFunction {
    private static final long serialVersionUID = -8844211418827105029L;

    ClipFunction() {
        this.docString = "clip(a, a_min, a_max)";
        this.argNames = new String[] { "a", "a_min", "a_max" };
        this.defaultArgs = new PyObject[] { null, null, null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        final PyObject Two = Py.newInteger(2);
        // XXX Turn into PyMultiarray.clip ?
        return PyMultiarray.choose(
                Umath.less.__call__(args[0], args[1]).__add__(
                        Umath.greater.__call__(args[0], args[2]).__mul__(Two)),
                new PyTuple(args));
    }
}

final class CompressFunction extends KeywordFunction {
    private static final long serialVersionUID = 8669912274727022895L;

    CompressFunction() {
        this.docString = "compress(condition, a, [dimension=-1])";
        this.argNames = new String[] { "condition", "a", "dimension" };
        this.defaultArgs = new PyObject[] { null, null, Py.newInteger(-1) };
    }

    @Override public PyObject _call(final PyObject args[]) {
        // XXX Turn into PyMultiarray.compress ?
        final PyObject nonZero = PyMultiarray.repeat(
                JNumeric.arrayrange.__call__(Py.newInteger(args[0].__len__())),
                Umath.not_equal.__call__(args[0], Py.Zero),
                0);
        return PyMultiarray.take(args[1], nonZero, Py.py2int(args[2]));
    }
}

final class ConcatenateFunction extends KeywordFunction {
    private static final long serialVersionUID = 7118393324382148007L;

    ConcatenateFunction() {
        this.docString = "concatenate(arrays, axis=0)";
        this.argNames = new String[] { "arrays", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.concatenate(args[0], Py.py2int(args[1]));
    }
}

final class ConvolveFunction extends KeywordFunction {
    private static final long serialVersionUID = -8689380547621360238L;

    ConvolveFunction() {
        this.docString = "convolve(a, b, mode=0)";
        this.argNames = new String[] { "a", "b", "mode" };
        this.defaultArgs = new PyObject[] { null, null, Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.convolve(args[0], args[1], Py.py2int(args[2]));
    }
}

final class Cross_correlateFunction extends KeywordFunction {
    private static final long serialVersionUID = 993749022355592940L;

    Cross_correlateFunction() {
        this.docString = "cross_correlate(a, b, mode=0)";
        this.argNames = new String[] { "a", "b", "mode" };
        this.defaultArgs = new PyObject[] { null, null, Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.cross_correlate(
                args[0],
                args[1],
                Py.py2int(args[2]));
    }
}

final class DiagonalFunction extends KeywordFunction {
    private static final long serialVersionUID = 710786726737662684L;

    DiagonalFunction() {
        this.docString = "diagonal(a, offset=0, axis=-2)";
        this.argNames = new String[] { "a", "offset", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.Zero, Py.newInteger(-2) };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.diagonal(
                args[0],
                Py.py2int(args[1]),
                Py.py2int(args[2]));
    }
}

final class DotFunction extends KeywordFunction {
    private static final long serialVersionUID = -4845146524858011834L;

    DotFunction() {
        this.docString = "dot(a, b, axisA=-1, axisB=0)";
        this.argNames = new String[] { "a", "b", "axisA", "axisB" };
        this.defaultArgs = new PyObject[] {
                null,
                null,
                Py.newInteger(-1),
                Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.innerProduct(
                args[0],
                args[1],
                Py.py2int(args[2]),
                Py.py2int(args[3]));
    }
}

final class FromfunctionFunction extends KeywordFunction {
    private static final long serialVersionUID = -8569059879817807363L;

    FromfunctionFunction() {
        this.docString = "fromfunction(function, dimensions)";
        this.argNames = new String[] { "function", "dimensions" };
        this.defaultArgs = new PyObject[] { null, null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.fromFunction(args[0], args[1]);
    }
}

final class FromstringFunction extends KeywordFunction {
    private static final long serialVersionUID = 134207923841227124L;

    FromstringFunction() {
        this.docString = "fromstring(string, typecode)";
        this.argNames = new String[] { "string", "typecode" };
        this.defaultArgs = new PyObject[] { null, null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        // XXX add error checking to args[0]?
        return PyMultiarray.fromString(args[0].toString(), Py.py2char(args[1]));
    }
}

final class IdentityFunction extends KeywordFunction {
    private static final long serialVersionUID = -9178247895975583251L;

    IdentityFunction() {
        this.docString = "identity(n)";
        this.argNames = new String[] { "n" };
        this.defaultArgs = new PyObject[] { null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        // XXX move to pyMultiarrray?
        final int n = Py.py2int(args[0]);
        final PyMultiarray a = PyMultiarray.zeros(new int[] { n * n }, 'i');
        for (int i = 0; i < n; i++) {
            a.set(i * (n + 1), Py.One);
        }
        return PyMultiarray.reshape(a, new int[] { n, n });
    }
}

final class IndicesFunction extends KeywordFunction {
    private static final long serialVersionUID = 2184217250691275960L;

    IndicesFunction() {
        this.docString = "indices(dimensions, typecode=None)";
        this.argNames = new String[] { "dimensions", "typecode" };
        this.defaultArgs = new PyObject[] { null, Py.newString('\0') };
    }

    @Override public PyObject _call(final PyObject args[]) {
        if (args[1].equals(Py.None)) {
            args[1] = this.defaultArgs[1];
        }
        return PyMultiarray.indices(args[0], Py.py2char(args[1]));
    }
}

final class InnerproductFunction extends KeywordFunction {
    private static final long serialVersionUID = 7497182188999451403L;

    InnerproductFunction() {
        this.docString = "innerproduct(a, b, axisA=-1, axisB=-1)";
        this.argNames = new String[] { "a", "b", "axisA", "axisB" };
        this.defaultArgs = new PyObject[] {
                null,
                null,
                Py.newInteger(-1),
                Py.newInteger(-1) };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.innerProduct(
                args[0],
                args[1],
                Py.py2int(args[2]),
                Py.py2int(args[3]));
    }
}

final class LinspaceFunction extends KeywordFunction {
    private static final long serialVersionUID = 5746081813573451761L;

    LinspaceFunction() {
        this.docString = "linspace(start, stop, num=50, endpoint=True, retstep=False)";
        this.argNames = new String[] { "start", "stop", "num", "endpoint", "retstep" };
        this.defaultArgs = new PyObject[] {
                null,
                null,
                Py.newInteger(50),
                Py.newBoolean(true),
                Py.newBoolean(false) };
    }

    @Override public PyObject _call(final PyObject args[]) {
    	System.out.println(args[2]);
    	
    	float step = 0;
    	float start = Py.py2float(args[0]);
    	float stop = Py.py2float(args[1]);
    	float num = Py.py2float(args[2]);
    	
    	if (Py.py2boolean(args[3])) {
    		step = (stop - start) / (num-1);
    		stop = stop + step;
    	}
    	else {
    		step = (stop - start) / num;
    	}
    	
    	PyMultiarray r = PyMultiarray.arrayRange(args[0],
                								 Py.newFloat(stop),
                								 Py.newFloat(step),
    											 'f');

    	if (Py.py2boolean(args[4])) 
    		return new PyTuple(r, Py.newFloat(step));
    	else
    		return r;
    }
}

final class NonzeroFunction extends KeywordFunction {
    private static final long serialVersionUID = 1732261811691013401L;

    NonzeroFunction() {
        this.docString = "nonzero(a)";
        this.argNames = new String[] { "a" };
        this.defaultArgs = new PyObject[] { null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        // XXX Turn into PyMultiarray.nonzero ?
        return PyMultiarray.repeat(
                JNumeric.arrayrange.__call__(Py.newInteger(args[0].__len__())),
                Umath.not_equal.__call__(args[0], Py.Zero),
                0);
    }
}

final class OnesFunction extends KeywordFunction {
    private static final long serialVersionUID = 3467881618102625225L;

    OnesFunction() {
        this.docString = "ones(shape, typecode=None)";
        this.argNames = new String[] { "shape", "typecode" };
        this.defaultArgs = new PyObject[] { null, Py.None };
    }

    @Override public PyObject _call(final PyObject args[]) {
        if (args[1].equals(Py.None)) {
            return PyMultiarray.zeros(args[0]).__add__(Py.One);
        } else {
            return PyMultiarray.zeros(args[0], Py.py2char(args[1])).__add__(
                    PyMultiarray.array(Py.One, '1'));
        }
    }
}

final class RepeatFunction extends KeywordFunction {
    private static final long serialVersionUID = -3346152024620868094L;

    RepeatFunction() {
        this.docString = "repeat(a, repeats, axis=0)";
        this.argNames = new String[] { "a", "repeats", "axis" };
        this.defaultArgs = new PyObject[] { null, null, Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.repeat(args[0], args[1], Py.py2int(args[2]));
    }
}

final class ReshapeFunction extends KeywordFunction {
    private static final long serialVersionUID = 6537950470573822590L;

    ReshapeFunction() {
        this.docString = "reshape(a, shape)";
        this.argNames = new String[] { "a", "shape" };
        this.defaultArgs = new PyObject[] { null, null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.reshape(
                args[0],
                PyMultiarray.objectToInts(args[1], false));
    }
}

final class ResizeFunction extends KeywordFunction {
    private static final long serialVersionUID = 7092728715870395077L;

    ResizeFunction() {
        this.docString = "resize(a, shape)";
        this.argNames = new String[] { "a", "shape" };
        this.defaultArgs = new PyObject[] { null, null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.resize(
                args[0],
                PyMultiarray.objectToInts(args[1], false));
    }
}

final class RavelFunction extends KeywordFunction {
    private static final long serialVersionUID = -8124772805856856572L;

    RavelFunction() {
        this.docString = "ravel(a)";
        this.argNames = new String[] { "a" };
        this.defaultArgs = new PyObject[] { null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.reshape(args[0], new int[] { -1 });
    }
}

final class SearchsortedFunction extends KeywordFunction {
    private static final long serialVersionUID = -6897451098438572030L;

    SearchsortedFunction() {
        this.docString = "searchsorted(a, values)";
        this.argNames = new String[] { "a", "values" };
        this.defaultArgs = new PyObject[] { null, null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.searchSorted(args[0], args[1]);
    }
}

final class ShapeFunction extends KeywordFunction {
    private static final long serialVersionUID = -6004264270333563355L;

    ShapeFunction() {
        this.docString = "shape(a)";
        this.argNames = new String[] { "a" };
        this.defaultArgs = new PyObject[] { null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        final int[] shapeOf = PyMultiarray.shapeOf(args[0]);
        final PyObject[] pyShapeOf = new PyObject[shapeOf.length];
        for (int i = 0; i < shapeOf.length; i++) {
            pyShapeOf[i] = Py.newInteger(shapeOf[i]);
        }
        return new PyTuple(pyShapeOf);
    }
}

final class SortFunction extends KeywordFunction {
    private static final long serialVersionUID = -8748798385755543671L;

    SortFunction() {
        this.docString = "sort(a, axis=-1)";
        this.argNames = new String[] { "a", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.newInteger(-1) };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.sort(args[0], Py.py2int(args[1]));
    }
}

final class TakeFunction extends KeywordFunction {
    private static final long serialVersionUID = -2646084886841367336L;

    TakeFunction() {
        this.docString = "take(a, indices, axis=-1)";
        this.argNames = new String[] { "a", "indices", "axis" };
        this.defaultArgs = new PyObject[] { null, null, Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.take(args[0], args[1], Py.py2int(args[2]));
    }
}

final class TraceFunction extends KeywordFunction {
    private static final long serialVersionUID = 1190892189817524086L;

    TraceFunction() {
        this.docString = "trace(a, offset=0, axis1=-2, axis1=-1)";
        this.argNames = new String[] { "a", "offset", "axis1", "axis2" };
        this.defaultArgs = new PyObject[] {
                null,
                Py.Zero,
                Py.newInteger(-2),
                Py.newInteger(-1) };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return Umath.add.reduce(
                JNumeric.diagonal.__call__(args[0], args[1], args[2]), -1);
    }
}

final class TransposeFunction extends KeywordFunction {
    private static final long serialVersionUID = -6407099071609214991L;

    TransposeFunction() {
        this.docString = "transpose(a, axes=None)";
        this.argNames = new String[] { "a", "axes" };
        this.defaultArgs = new PyObject[] { null, Py.None };
    }

    @Override public PyObject _call(final PyObject args[]) {
        int[] axes;
        // Move some of this to PyMultiarray?
        if (args[1].equals(Py.None)) {
            axes = new int[PyMultiarray.shapeOf(args[0]).length];
            for (int i = 0; i < axes.length; i++) {
                axes[i] = axes.length - 1 - i;
            }
        }
        else {
            axes = new int[args[1].__len__()];
            for (int i = 0; i < axes.length; i++) {
                axes[i] = Py.py2int(args[1].__getitem__(i));
            }
        }
        return PyMultiarray.transpose(args[0], axes);
    }
}

final class WhereFunction extends KeywordFunction {
    private static final long serialVersionUID = 7102514462359313033L;

    WhereFunction() {
        this.docString = "where(condition, x, y)";
        this.argNames = new String[] { "condition", "x", "y" };
        this.defaultArgs = new PyObject[] { null, null, null };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return PyMultiarray.choose(Umath.not_equal.__call__(args[0], Py.Zero),
                new PyTuple(args[2], args[1]));
    }
}

final class ZerosFunction extends KeywordFunction {
    private static final long serialVersionUID = 8468266769577881289L;

    ZerosFunction() {
        this.docString = "zeros(shape, typecode=None)";
        this.argNames = new String[] { "shape", "typecode" };
        this.defaultArgs = new PyObject[] { null, Py.None };
    }

    @Override public PyObject _call(final PyObject args[]) {
        if (args[1].equals(Py.None)) {
            return PyMultiarray.zeros(args[0]);
        } else {
            return PyMultiarray.zeros(args[0], Py.py2char(args[1]));
        }
    }
}

// Abbreviations
final class SumFunction extends KeywordFunction {
    private static final long serialVersionUID = 1743179887623752980L;

    SumFunction() {
        this.docString = "sum(a, [axis])";
        this.argNames = new String[] { "a", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return Umath.add.reduce(args[0], Py.py2int(args[1]));
    }
}

final class CumsumFunction extends KeywordFunction {
    private static final long serialVersionUID = 8556851667750567307L;

    CumsumFunction() {
        this.docString = "cumsum(a, [axis])";
        this.argNames = new String[] { "a", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return Umath.add.accumulate(args[0], Py.py2int(args[1]));
    }
}

final class ProductFunction extends KeywordFunction {
    private static final long serialVersionUID = -864706255525505341L;

    ProductFunction() {
        this.docString = "product(a, [axis])";
        this.argNames = new String[] { "a", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return Umath.multiply.reduce(args[0], Py.py2int(args[1]));
    }
}

final class CumproductFunction extends KeywordFunction {
    private static final long serialVersionUID = -6772092265237505172L;

    CumproductFunction() {
        this.docString = "cumproduct(a, [axis])";
        this.argNames = new String[] { "a", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return Umath.multiply.accumulate(args[0], Py.py2int(args[1]));
    }
}

final class AlltrueFunction extends KeywordFunction {
    private static final long serialVersionUID = -3910946436832357924L;

    AlltrueFunction() {
        this.docString = "alltrue(a, [axis])";
        this.argNames = new String[] { "a", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return Umath.logical_and.reduce(args[0], Py.py2int(args[1]));
    }
}

final class SometrueFunction extends KeywordFunction {
    private static final long serialVersionUID = -5098199415954538663L;

    SometrueFunction() {
        this.docString = "sometrue(a, [axis])";
        this.argNames = new String[] { "a", "axis" };
        this.defaultArgs = new PyObject[] { null, Py.Zero };
    }

    @Override public PyObject _call(final PyObject args[]) {
        return Umath.logical_or.reduce(args[0], Py.py2int(args[1]));
    }
}
