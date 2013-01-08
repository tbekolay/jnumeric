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

import java.lang.reflect.Array;

import org.python.core.Py;
import org.python.core.PyComplex;
import org.python.core.PyObject;
import org.python.core.PyString;

/**
 * Universal functions that take two PyMultiarrays as input.
 */
public class BinaryUfunc extends KeywordFunction {

    private static final long serialVersionUID = 2592949830660376736L;

    static final public BinaryFunction add = new Add();
    static final public BinaryFunction subtract = new Subtract();
    static final public BinaryFunction multiply = new Multiply();
    static final public BinaryFunction divide = new Divide();
    static final public BinaryFunction remainder = new Remainder();
    static final public BinaryFunction power = new Power();

    static final public BinaryFunction maximum = new Maximum();
    static final public BinaryFunction minimum = new Minimum();

    static final public BinaryFunction equal = new Equal();
    static final public BinaryFunction notEqual = new NotEqual();
    static final public BinaryFunction less = new Less();
    static final public BinaryFunction lessEqual = new LessEqual();
    static final public BinaryFunction greater = new Greater();
    static final public BinaryFunction greaterEqual = new GreaterEqual();

    static final public BinaryFunction logicalAnd = new LogicalAnd();
    static final public BinaryFunction logicalOr = new LogicalOr();
    static final public BinaryFunction logicalXor = new LogicalXor();

    static final public BinaryFunction bitwiseAnd = new BitwiseAnd();
    static final public BinaryFunction bitwiseOr = new BitwiseOr();
    static final public BinaryFunction bitwiseXor = new BitwiseXor();

    static final public BinaryFunction argMax = new ArgMax();
    static final public BinaryFunction argMin = new ArgMin();

    String docString() {
        return "This object has the following methods:\n"
             + "   reduce(a [,axis])\n"
             + "      Works just like reduce(ufunc, a, [ufunc's identity element]) except\n"
             + "      you get to choose the axis to perform the reduction along. Note that\n"
             + "      if the length of a long axis is 0, then the appropriate identity element\n"
             + "      for the ufunc will be returned.\n"
             + "   accumulate(a [,axis])\n"
             + "      This is the same as reduce, except that all the intermediate results are\n"
             + "      kept along the way.\n"
             + "   outer(a, b)\n"
             + "      This will take the outer product of a and b. The new results shape will\n"
             + "      be the same as a.shape+b.shape (where plus means concatenate, not add!)\n"
             + "   reduceat(a, indices [,axis])\n"
             + "      This is a weird function, and most people should just ignore it. It will\n"
             + "      reduce a to each of the given indices so that as new size along the given\n"
             + "      axis will be the same as the length of indices.\n"
             + "      If axis is not supplied it defaults to zero.";
    }

    BinaryFunction function;

    public BinaryUfunc(final BinaryFunction function) {
        this.function = function;
        this.argNames = new String[] { "a", "b", "result" };
        this.defaultArgs = new PyObject[] { null, null, Py.None };
    }

    @Override public PyObject __findattr_ex__(final String name) {
        if (name == "__doc__") { return new PyString(this.function.docString()
                + this.docString()); }
        return super.__findattr_ex__(name);
    }

    public PyObject outer(final PyObject poa, final PyObject pob) {
        final PyMultiarray a = PyMultiarray.asarray(poa);
        final PyMultiarray b = PyMultiarray.asarray(pob);
        final char type = PyMultiarray.commonType(a._typecode, b._typecode);
        final PyMultiarray af = PyMultiarray.reshape(
                PyMultiarray.ascontiguous(a, type),
                new int[] { -1 });
        final PyMultiarray bf = PyMultiarray.reshape(
                PyMultiarray.ascontiguous(b, type),
                new int[] { -1 });
        final PyMultiarray result = PyMultiarray.zeros(
                new int[] { af.dimensions[0] * bf.dimensions[0] },
                type);
        for (int i = 0; i < af.dimensions[0]; i++) {
            final PyMultiarray temp = (PyMultiarray) this.function.call(
                    bf,
                    af.get(i));
            System.arraycopy(
                    temp.data,
                    0,
                    result.data,
                    i * bf.dimensions[0],
                    bf.dimensions[0]);
        }
        final int[] newDimensions = new int[a.dimensions.length
                + b.dimensions.length];
        for (int i = 0; i < a.dimensions.length; i++) {
            newDimensions[i] = a.dimensions[i];
        }
        for (int i = 0; i < b.dimensions.length; i++) {
            newDimensions[a.dimensions.length + i] = b.dimensions[i];
        }
        return PyMultiarray.reshape(result, newDimensions);
    }

    public PyObject reduceat(final PyObject po, final int[] indices, int axis) {
        // This could probably be made faster by doing it directly,
        // but I don't think I care.
        PyMultiarray a = PyMultiarray.ascontiguous(po);
        axis = (axis < 0) ? axis + a.dimensions.length : axis;
        if (axis < 0 || axis >= a.dimensions.length) { throw Py
                .ValueError("axis out of legal range"); }
        final int[] eIndices = new int[indices.length + 1];
        eIndices[indices.length] = a.dimensions[axis];
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] < 0 || indices[i] >= a.dimensions[axis]) { throw Py
                    .IndexError("invalid index to reduceat"); }
            eIndices[i] = indices[i];
        }
        final int[] shape = a.dimensions.clone();
        shape[axis] = indices.length;
        final PyMultiarray result = PyMultiarray.zeros(shape, a._typecode);
        a = PyMultiarray.rotateAxes(a, -axis);
        final PyMultiarray r = PyMultiarray.rotateAxes(result, -axis);
        for (int i = 0; i < indices.length; i++) {
            r.set(i, this.reduce(a.getslice(eIndices[i], eIndices[i + 1], 1)));
        }
        return result;
    }

    public PyObject reduceat(final PyObject po, final int[] indices) {
        return this.reduceat(po, indices, 0);
    }

    public PyObject reduce(final PyObject po, int axis) {
        PyMultiarray a = PyMultiarray.asarray(po);
        if (axis < 0) {
            axis += a.dimensions.length;
        }
        if (axis < 0 || axis >= a.dimensions.length) { throw Py
                .ValueError("axis out of legal range"); }
        a = PyMultiarray.rotateAxes(PyMultiarray.asarray(po), -axis);
        if (a.dimensions[0] == 0) { return PyMultiarray.asarray(
                this.function.identity(),
                a._typecode); }
        // Get the array b;
        final int[] shape = new int[a.dimensions.length - 1];
        for (int i = 0; i < a.dimensions.length - 1; i++) {
            shape[i] = a.dimensions[i + 1];
        }
        PyMultiarray b = PyMultiarray.zeros(
                shape,
                BinaryFunction.returnsInt ? 'i' : a._typecode);
        // Loop over other axes and reduce...
        a = PyMultiarray.reshape(a, new int[] { a.dimensions[0], -1 });
        b = PyMultiarray.reshape(b, new int[] { 1, -1 });
        final int s0 = a.strides[0], d0 = a.dimensions[0];
        final int s1 = a.strides[a.dimensions.length - 1], d1 = a.dimensions[a.dimensions.length - 1];
        for (int i = 0; i < d1; i++) {
            this.function.accumulate(
                    a.data,
                    a.start + i * s1,
                    d0,
                    s0,
                    b.data,
                    i * s1,
                    1,
                    0,
                    a._typecode);
        }
        b = PyMultiarray.rotateAxes(PyMultiarray.reshape(b, shape), axis);
        return PyMultiarray.returnValue(b); // This will need to be swapped in
                                            // accumulate
    }

    public PyObject reduce(final PyObject po) {
        return this.reduce(po, 0);
    }

    public PyObject accumulate(final PyObject po, int axis) {
        PyMultiarray a = PyMultiarray.asarray(po);
        if (axis < 0) {
            axis += a.dimensions.length;
        }
        if (axis < 0 || axis >= a.dimensions.length) { throw Py
                .ValueError("axis out of legal range"); }
        a = PyMultiarray.rotateAxes(PyMultiarray.asarray(po), -axis);
        if (a.dimensions[0] == 0) { return PyMultiarray.asarray(
                this.function.identity(),
                a._typecode); }
        // Get the array b;
        final int[] shape = a.dimensions.clone();
        PyMultiarray b = PyMultiarray.zeros(
                shape,
                BinaryFunction.returnsInt ? 'i' : a._typecode);
        // Loop over other axes and reduce...
        a = PyMultiarray.reshape(a, new int[] { a.dimensions[0], -1 });
        b = PyMultiarray.reshape(b, new int[] { 1, -1 });
        final int s0 = a.strides[0], d0 = a.dimensions[0];
        final int s1 = a.strides[a.dimensions.length - 1], d1 = a.dimensions[a.dimensions.length - 1];
        for (int i = 0; i < d1; i++) {
            this.function.accumulate(
                    a.data,
                    a.start + i * s1,
                    d0,
                    s0,
                    b.data,
                    i * s1,
                    d0,
                    s0,
                    a._typecode);
        }
        b = PyMultiarray.rotateAxes(PyMultiarray.reshape(b, shape), axis);
        return PyMultiarray.returnValue(b); // This will need to be swapped in
                                            // accumulate
    }

    public PyObject accumulate(final PyObject po) {
        return this.accumulate(po, 0);
    }

    @Override public PyObject _call(final PyObject args[]) {
        if (args[2] == Py.None) {
            return this.function.call(PyMultiarray.asarray(args[0]),
                    PyMultiarray.asarray(args[1]));// __call__(args[0],
                                                   // args[1]);
        } else {
            if (!(args[2] instanceof PyMultiarray)) { throw Py
                    .ValueError("result must be an array"); }
            final PyMultiarray a = PyMultiarray.asarray(args[0]);
            final PyMultiarray b = PyMultiarray.asarray(args[1]);
            final PyMultiarray result = (PyMultiarray) args[2];
            // It is assumed that somewhere down the line, the dimensions of
            // result get checked.
            // This generally happens in __XX__(a,b).
            return this.function.call(a, b, result);// return __call__(args[0],
                                                    // args[1], args[2]);
        }
    }
}

class BinaryFunction {
    String docString() {
        return "binary_function(a, b [, r])\n";
    }

    static final PyMultiarray one = PyMultiarray.array(Py.One, '1');
    static final PyMultiarray zero = PyMultiarray.array(Py.Zero, '1');
    // Change -- add returnType() and _returnType (= '\0');
    static final boolean returnsInt = false;

    PyMultiarray identity() {
        return null;
    }

    PyObject call(final PyObject oa, final PyObject ob) {
        throw Py.ValueError("call not implemented");
    }

    // Those wishing three argument calls to be efficient should override this.
    PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        PyMultiarray.copyAToB(PyMultiarray.asarray(this.call(a, b)), result);
        return result;
    }

    // Those wishing reduce and accumulate to be fast should to override this.
    void accumulate(final Object aData, final int aStart, final int aDim, final int aStride,
            final Object rData, final int rStart, final int rDim, final int rStride, final char _typecode) {
        final PyMultiarray array = new PyMultiarray(
                aData,
                _typecode,
                aStart,
                new int[] { aDim },
                new int[] { aStride });
        final PyMultiarray result = new PyMultiarray(
                rData,
                _typecode,
                rStart,
                new int[] { rDim },
                new int[] { rStride });
        if (aDim == 0) { return; }
        PyObject r = array.get(0);
        result.set(0, r);
        final int jStride = (rStride == 0) ? 0 : 1;
        for (int i = 1, j = rStride; i < aDim; i++, j += jStride) {
            r = this.call(r, array.get(i));
            result.set(j, r);
        }
    }
}

final class Add extends BinaryFunction {
    @Override String docString() {
        return "add(a, b [,r]) returns a+b and stores the result in r if supplied.\n";
    }

    @Override final PyMultiarray identity() {
        return BinaryFunction.zero;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        switch (type) {
        case '1':
            byte last1 = 0;
            final byte[] rData1 = (byte[]) rData,
            aData1 = (byte[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rData1[sr] = (last1 += aData1[sa]);
            }
            break;
        case 's':
            short lasts = 0;
            final short[] rDatas = (short[]) rData,
            aDatas = (short[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatas[sr] = (lasts += aDatas[sa]);
            }
            break;
        case 'i':
            int lasti = 0;
            final int[] rDatai = (int[]) rData,
            aDatai = (int[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = (lasti += aDatai[sa]);
            }
            break;
        case 'l':
            long lastl = 0;
            final long[] rDatal = (long[]) rData,
            aDatal = (long[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatal[sr] = (lastl += aDatal[sa]);
            }
            break;
        case 'f':
            float lastf = 0;
            final float[] rDataf = (float[]) rData,
            aDataf = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataf[sr] = (lastf += aDataf[sa]);
            }
            break;
        case 'd':
            double lastd = 0;
            final double[] rDatad = (double[]) rData,
            aDatad = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatad[sr] = (lastd += aDatad[sa]);
            }
            break;
        case 'F':
            float lastfr = 0,
            lastfi = 0;
            final float[] rDataF = (float[]) rData,
            aDataF = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataF[sr] = (lastfr += aDataF[sa]);
                rDataF[sr + 1] = (lastfi += aDataF[sa + 1]);
            }
            break;
        case 'D':
            double lastdr = 0,
            lastdi = 0;
            final double[] rDataD = (double[]) rData,
            aDataD = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataD[sr] = (lastdr += aDataD[sa]);
                rDataD[sr + 1] = (lastdi += aDataD[sa + 1]);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdFDO]");
        }
    }

    @Override final PyObject call(final PyObject o1, final PyObject o2) {
        return o1.__add__(o2);
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__add__(b, result);
    }
}

final class Subtract extends BinaryFunction {
    @Override String docString() {
        return "subtract(a, b [,r]) returns a-b and stores the result in r if supplied.\n";
    }

    @Override final PyMultiarray identity() {
        return BinaryFunction.zero;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        switch (type) {
        case '1':
            byte last1 = 0;
            final byte[] rData1 = (byte[]) rData,
            aData1 = (byte[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rData1[sr] = (last1 -= aData1[sa]);
            }
            break;
        case 's':
            short lasts = 0;
            final short[] rDatas = (short[]) rData,
            aDatas = (short[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatas[sr] = (lasts -= aDatas[sa]);
            }
            break;
        case 'i':
            int lasti = 0;
            final int[] rDatai = (int[]) rData,
            aDatai = (int[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = (lasti -= aDatai[sa]);
            }
            break;
        case 'l':
            long lastl = 0;
            final long[] rDatal = (long[]) rData,
            aDatal = (long[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatal[sr] = (lastl -= aDatal[sa]);
            }
            break;
        case 'f':
            float lastf = 0;
            final float[] rDataf = (float[]) rData,
            aDataf = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataf[sr] = (lastf -= aDataf[sa]);
            }
            break;
        case 'd':
            double lastd = 0;
            final double[] rDatad = (double[]) rData,
            aDatad = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatad[sr] = (lastd -= aDatad[sa]);
            }
            break;
        case 'F':
            float lastfr = 0,
            lastfi = 0;
            final float[] rDataF = (float[]) rData,
            aDataF = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataF[sr] = (lastfr -= aDataF[sa]);
                rDataF[sr + 1] = (lastfi -= aDataF[sa + 1]);
            }
            break;
        case 'D':
            double lastdr = 0,
            lastdi = 0;
            final double[] rDataD = (double[]) rData,
            aDataD = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataD[sr] = (lastdr -= aDataD[sa]);
                rDataD[sr + 1] = (lastdi -= aDataD[sa + 1]);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdFDO]");
        }
    }

    @Override final PyObject call(final PyObject po1, final PyObject po2) {
        return po1.__sub__(po2);
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__sub__(b, result);
    }
}

final class Multiply extends BinaryFunction {
    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        switch (type) {
        case '1':
            byte last1 = 1;
            final byte[] rData1 = (byte[]) rData,
            aData1 = (byte[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rData1[sr] = (last1 *= aData1[sa]);
            }
            break;
        case 's':
            short lasts = 1;
            final short[] rDatas = (short[]) rData,
            aDatas = (short[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatas[sr] = (lasts *= aDatas[sa]);
            }
            break;
        case 'i':
            int lasti = 1;
            final int[] rDatai = (int[]) rData,
            aDatai = (int[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = (lasti *= aDatai[sa]);
            }
            break;
        case 'l':
            long lastl = 1;
            final long[] rDatal = (long[]) rData,
            aDatal = (long[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatal[sr] = (lastl *= aDatal[sa]);
            }
            break;
        case 'f':
            float lastf = 1;
            final float[] rDataf = (float[]) rData,
            aDataf = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataf[sr] = (lastf *= aDataf[sa]);
            }
            break;
        case 'd':
            double lastd = 1;
            final double[] rDatad = (double[]) rData,
            aDatad = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatad[sr] = (lastd *= aDatad[sa]);
            }
            break;
        case 'F':
            float lastfr = 1,
            lastfi = 0;
            final float[] rDataF = (float[]) rData,
            aDataF = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                final float re = lastfr * aDataF[sa] - lastfi * aDataF[sa + 1];
                rDataF[sr + 1] = (lastfi = lastfr * aDataF[sa + 1] + lastfi
                        * aDataF[sa]);
                rDataF[sr] = (lastfr = re);
            }
            break;
        case 'D':
            double lastdr = 1,
            lastdi = 0;
            final double[] rDataD = (double[]) rData,
            aDataD = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                final double re = lastdr * aDataD[sa] - lastdi * aDataD[sa + 1];
                rDataD[sr + 1] = (lastdi = lastdr * aDataD[sa + 1] + lastdi
                        * aDataD[sa]);
                rDataD[sr] = (lastdr = re);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdFDO]");
        }
    }

    @Override final PyObject call(final PyObject po1, final PyObject po2) {
        return po1.__mul__(po2);
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__mul__(b, result);
    }
}

final class Divide extends BinaryFunction {
    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        switch (type) {
        case '1':
            byte last1 = 1;
            final byte[] rData1 = (byte[]) rData,
            aData1 = (byte[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rData1[sr] = (last1 /= aData1[sa]);
            }
            break;
        case 's':
            short lasts = 1;
            final short[] rDatas = (short[]) rData,
            aDatas = (short[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatas[sr] = (lasts /= aDatas[sa]);
            }
            break;
        case 'i':
            int lasti = 1;
            final int[] rDatai = (int[]) rData,
            aDatai = (int[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = (lasti /= aDatai[sa]);
            }
            break;
        case 'l':
            long lastl = 1;
            final long[] rDatal = (long[]) rData,
            aDatal = (long[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatal[sr] = (lastl /= aDatal[sa]);
            }
            break;
        case 'f':
            float lastf = 1;
            final float[] rDataf = (float[]) rData,
            aDataf = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataf[sr] = (lastf /= aDataf[sa]);
            }
            break;
        case 'd':
            double lastd = 1;
            final double[] rDatad = (double[]) rData,
            aDatad = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatad[sr] = (lastd /= aDatad[sa]);
            }
            break;
        case 'F':
            float lastfr = 1,
            lastfi = 0;
            final float[] rDataF = (float[]) rData,
            aDataF = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                final float den = aDataF[sa] * aDataF[sa] + aDataF[sa + 1]
                        * aDataF[sa + 1];
                final float re = (lastfr * aDataF[sa] + lastfi * aDataF[sa + 1])
                        / den;
                rDataF[sr + 1] = (lastfi = (-lastfr * aDataF[sa + 1] + lastfi
                        * aDataF[sa])
                        / den);
                rDataF[sr] = (lastfr = re);
            }
            break;
        case 'D':
            double lastdr = 1,
            lastdi = 0;
            final double[] rDataD = (double[]) rData,
            aDataD = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                final double den = aDataD[sa] * aDataD[sa] + aDataD[sa + 1]
                        * aDataD[sa + 1];
                final double re = (lastdr * aDataD[sa] + lastdi
                        * aDataD[sa + 1])
                        / den;
                rDataD[sr + 1] = (lastdi = (-lastdr * aDataD[sa + 1] + lastdi
                        * aDataD[sa])
                        / den);
                rDataD[sr] = (lastdr = re);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdFDO]");
        }
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return po1.__div__(po2);
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__div__(b, result);
    }
}

final class Remainder extends BinaryFunction {
    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        switch (type) {
        case '1':
            byte last1 = 1;
            final byte[] rData1 = (byte[]) rData,
            aData1 = (byte[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rData1[sr] = (last1 %= aData1[sa]);
            }
            break;
        case 's':
            short lasts = 1;
            final short[] rDatas = (short[]) rData,
            aDatas = (short[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatas[sr] = (lasts %= aDatas[sa]);
            }
            break;
        case 'i':
            int lasti = 1;
            final int[] rDatai = (int[]) rData,
            aDatai = (int[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = (lasti %= aDatai[sa]);
            }
            break;
        case 'l':
            long lastl = 1;
            final long[] rDatal = (long[]) rData,
            aDatal = (long[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatal[sr] = (lastl %= aDatal[sa]);
            }
            break;
        case 'f':
            float lastf = 1;
            final float[] rDataf = (float[]) rData,
            aDataf = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataf[sr] = (lastf %= aDataf[sa]);
            }
            break;
        case 'd':
            double lastd = 1;
            final double[] rDatad = (double[]) rData,
            aDatad = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatad[sr] = (lastd %= aDatad[sa]);
            }
            break;
        case 'F':
            float lastfr = 1,
            lastfi = 0;
            final float[] rDataF = (float[]) rData,
            aDataF = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                // This nomenclature is a little weird 'cause I stole this code
                // from PyMultiarray.
                final float reA = lastfr, imA = lastfi, reB = aDataF[sa], imB = aDataF[sa + 1];
                final float den = reB * reB + imB * imB;
                final float n = (float) Math.floor((reA * reB + imA * imB)
                        / den);
                rDataF[sr] = (lastfr = reA - n * reB);
                rDataF[sr + 1] = (lastfi = imA - n * imB);
            }
            break;
        case 'D':
            double lastdr = 1,
            lastdi = 0;
            final double[] rDataD = (double[]) rData,
            aDataD = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                // This nomenclature is a little weird 'cause I stole this code
                // from PyMultiarray.
                final double reA = lastdr, imA = lastdi, reB = aDataD[sa], imB = aDataD[sa + 1];
                final double den = reB * reB + imB * imB;
                final double n = Math.floor((reA * reB + imA * imB) / den);
                rDataD[sr] = (lastdr = reA - n * reB);
                rDataD[sr + 1] = (lastdi = imA - n * imB);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdFDO]");
        }
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return po1.__mod__(po2);
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__mod__(b, result);
    }
}

final class Power extends BinaryFunction {
    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        switch (type) {
        case '1':
            byte last1 = 1;
            final byte[] rData1 = (byte[]) rData,
            aData1 = (byte[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rData1[sr] = (last1 = PyMultiarray.pow(last1, aData1[sa]));
            }
            break;
        case 's':
            short lasts = 1;
            final short[] rDatas = (short[]) rData,
            aDatas = (short[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatas[sr] = (lasts = PyMultiarray.pow(lasts, aDatas[sa]));
            }
            break;
        case 'i':
            int lasti = 1;
            final int[] rDatai = (int[]) rData,
            aDatai = (int[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = (lasti = PyMultiarray.pow(lasti, aDatai[sa]));
            }
            break;
        case 'l':
            long lastl = 1;
            final long[] rDatal = (long[]) rData,
            aDatal = (long[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatal[sr] = (lastl = PyMultiarray.pow(lastl, aDatal[sa]));
            }
            break;
        case 'f':
            float lastf = 1;
            final float[] rDataf = (float[]) rData,
            aDataf = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataf[sr] = (lastf = PyMultiarray.pow(lastf, aDataf[sa]));
            }
            break;
        case 'd':
            double lastd = 1;
            final double[] rDatad = (double[]) rData,
            aDatad = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatad[sr] = (lastd = PyMultiarray.pow(lastd, aDatad[sa]));
            }
            break;
        case 'F':
            float lastfr = 1,
            lastfi = 0;
            final float[] rDataF = (float[]) rData,
            aDataF = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                // This nomenclature is a little weird 'cause I stole this code
                // from PyMultiarray.
                final PyComplex Z = (PyComplex) new PyComplex(lastfr, lastfi)
                        .__pow__(new PyComplex(aDataF[sa], aDataF[sa + 1]));
                rDataF[sr] = (lastfi = (float) Z.real);
                rDataF[sr + 1] = (lastfr = (float) Z.imag);
            }
            break;
        case 'D':
            double lastdr = 1,
            lastdi = 0;
            final double[] rDataD = (double[]) rData,
            aDataD = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                // This nomenclature is a little weird 'cause I stole this code
                // from PyMultiarray.
                final PyComplex Z = (PyComplex) new PyComplex(lastdr, lastdi)
                        .__pow__(new PyComplex(aDataD[sa], aDataD[sa + 1]));
                rDataD[sr] = (lastdi = Z.real);
                rDataD[sr + 1] = (lastdr = Z.imag);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdFDO]");
        }
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return po1.__pow__(po2);
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__pow__(b, result);
    }
}

// Need fast ufuncs
final class Maximum extends BinaryFunction {
    @Override final PyMultiarray identity() {
        throw Py.ValueError("zero size array to ufunc without identity");
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        switch (type) {
        case '1':
            final byte[] rData1 = (byte[]) rData,
            aData1 = (byte[]) aData;
            byte last1 = aData1[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rData1[sr] = last1 = ((last1 > aData1[sa]) ? last1 : aData1[sa]);
            }
            break;
        case 's':
            final short[] rDatas = (short[]) rData,
            aDatas = (short[]) aData;
            short lasts = aDatas[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatas[sr] = lasts = ((lasts > aDatas[sa]) ? lasts : aDatas[sa]);
            }
            break;
        case 'i':
            final int[] rDatai = (int[]) rData,
            aDatai = (int[]) aData;
            int lasti = aDatai[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = lasti = ((lasti > aDatai[sa]) ? lasti : aDatai[sa]);
            }
            break;
        case 'l':
            final long[] rDatal = (long[]) rData,
            aDatal = (long[]) aData;
            long lastl = aDatal[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatal[sr] = lastl = ((lastl > aDatal[sa]) ? lastl : aDatal[sa]);
            }
            break;
        case 'f':
            final float[] rDataf = (float[]) rData,
            aDataf = (float[]) aData;
            float lastf = aDataf[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataf[sr] = lastf = ((lastf > aDataf[sa]) ? lastf : aDataf[sa]);
            }
            break;
        case 'd':
            final double[] rDatad = (double[]) rData,
            aDatad = (double[]) aData;
            double lastd = aDatad[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatad[sr] = lastd = ((lastd > aDatad[sa]) ? lastd : aDatad[sa]);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdO]");
        }
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__max(PyMultiarray.asarray(po2));
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__max(b, result);
    }
}

final class Minimum extends BinaryFunction {
    @Override final PyMultiarray identity() {
        throw Py.ValueError("zero size array to ufunc without identity");
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        switch (type) {
        case '1':
            final byte[] rData1 = (byte[]) rData,
            aData1 = (byte[]) aData;
            byte last1 = aData1[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rData1[sr] = last1 = ((last1 < aData1[sa]) ? last1 : aData1[sa]);
            }
            break;
        case 's':
            final short[] rDatas = (short[]) rData,
            aDatas = (short[]) aData;
            short lasts = aDatas[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatas[sr] = lasts = ((lasts < aDatas[sa]) ? lasts : aDatas[sa]);
            }
            break;
        case 'i':
            final int[] rDatai = (int[]) rData,
            aDatai = (int[]) aData;
            int lasti = aDatai[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = lasti = ((lasti < aDatai[sa]) ? lasti : aDatai[sa]);
            }
            break;
        case 'l':
            final long[] rDatal = (long[]) rData,
            aDatal = (long[]) aData;
            long lastl = aDatal[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatal[sr] = lastl = ((lastl < aDatal[sa]) ? lastl : aDatal[sa]);
            }
            break;
        case 'f':
            final float[] rDataf = (float[]) rData,
            aDataf = (float[]) aData;
            float lastf = aDataf[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDataf[sr] = lastf = ((lastf < aDataf[sa]) ? lastf : aDataf[sa]);
            }
            break;
        case 'd':
            final double[] rDatad = (double[]) rData,
            aDatad = (double[]) aData;
            double lastd = aDatad[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatad[sr] = lastd = ((lastd < aDatad[sa]) ? lastd : aDatad[sa]);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdO]");
        }
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__min(PyMultiarray.asarray(po2));
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__min(b, result);
    }
}

// I don't think equal, notEqual, etc need fast reductions (accumulate) since
// they don't make much sense.
final class Equal extends BinaryFunction {
    boolean returnsInt = true;

    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__eq(PyMultiarray.asarray(po2));
    }
}

final class NotEqual extends BinaryFunction {
    boolean returnsInt = true;

    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__neq(PyMultiarray.asarray(po2));
    }
}

final class Less extends BinaryFunction {
    boolean returnsInt = true;

    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__lt(PyMultiarray.asarray(po2));
    }
}

final class LessEqual extends BinaryFunction {
    boolean returnsInt = true;

    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__le(PyMultiarray.asarray(po2));
    }
}

final class Greater extends BinaryFunction {
    boolean returnsInt = true;

    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__gt(PyMultiarray.asarray(po2));
    }
}

final class GreaterEqual extends BinaryFunction {
    boolean returnsInt = true;

    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__ge(PyMultiarray.asarray(po2));
    }
}

// Back to fast reductions.
final class BitwiseAnd extends BinaryFunction {
    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        switch (type) {
        case '1':
            final byte[] rData1 = (byte[]) rData,
            aData1 = (byte[]) aData;
            byte last1 = aData1[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rData1[sr] = (last1 &= aData1[sa]);
            }
            break;
        case 's':
            final short[] rDatas = (short[]) rData,
            aDatas = (short[]) aData;
            short lasts = aDatas[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatas[sr] = (lasts &= aDatas[sa]);
            }
            break;
        case 'i':
            final int[] rDatai = (int[]) rData,
            aDatai = (int[]) aData;
            int lasti = 1;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = (lasti &= aDatai[sa]);
            }
            break;
        case 'l':
            final long[] rDatal = (long[]) rData,
            aDatal = (long[]) aData;
            long lastl = 1;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatal[sr] = (lastl &= aDatal[sa]);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdO]");
        }
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__and__(PyMultiarray.asarray(po2));
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__and__(b, result);
    }
}

final class BitwiseOr extends BinaryFunction {
    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        switch (type) {
        case '1':
            final byte[] rData1 = (byte[]) rData,
            aData1 = (byte[]) aData;
            byte last1 = aData1[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rData1[sr] = (last1 |= aData1[sa]);
            }
            break;
        case 's':
            final short[] rDatas = (short[]) rData,
            aDatas = (short[]) aData;
            short lasts = aDatas[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatas[sr] = (lasts |= aDatas[sa]);
            }
            break;
        case 'i':
            final int[] rDatai = (int[]) rData,
            aDatai = (int[]) aData;
            int lasti = 1;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = (lasti |= aDatai[sa]);
            }
            break;
        case 'l':
            final long[] rDatal = (long[]) rData,
            aDatal = (long[]) aData;
            long lastl = 1;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatal[sr] = (lastl |= aDatal[sa]);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdO]");
        }
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__or__(PyMultiarray.asarray(po2));
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__or__(b, result);
    }
}

final class BitwiseXor extends BinaryFunction {
    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        switch (type) {
        case '1':
            final byte[] rData1 = (byte[]) rData,
            aData1 = (byte[]) aData;
            byte last1 = aData1[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rData1[sr] = (last1 ^= aData1[sa]);
            }
            break;
        case 's':
            final short[] rDatas = (short[]) rData,
            aDatas = (short[]) aData;
            short lasts = aDatas[sa];
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatas[sr] = (lasts ^= aDatas[sa]);
            }
            break;
        case 'i':
            final int[] rDatai = (int[]) rData,
            aDatai = (int[]) aData;
            int lasti = 1;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = (lasti ^= aDatai[sa]);
            }
            break;
        case 'l':
            final long[] rDatal = (long[]) rData,
            aDatal = (long[]) aData;
            long lastl = 1;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatal[sr] = (lastl ^= aDatal[sa]);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdO]");
        }
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__xor__(PyMultiarray.asarray(po2));
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__xor__(b, result);
    }
}

final class LogicalAnd extends BinaryFunction {
    boolean returnsInt = true;

    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        int last = 1;
        final int[] rDatai = (int[]) rData;
        switch (type) {
        case '1':
            final byte[] aData1 = (byte[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) & (aData1[sa] != 0)) ? 1 : 0);
            }
            break;
        case 's':
            final short[] aDatas = (short[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) & (aDatas[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'i':
            final int[] aDatai = (int[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) & (aDatai[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'l':
            final long[] aDatal = (long[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) & (aDatal[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'f':
            final float[] aDataf = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) & (aDataf[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'd':
            final double[] aDatad = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) & (aDatad[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'F':
            final float[] aDataF = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) & (aDataF[sa] != 0 || aDataF[sa + 1] != 0)) ? 1
                        : 0);
            }
            break;
        case 'D':
            final double[] aDataD = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) & (aDataD[sa] != 0 || aDataD[sa + 1] != 0)) ? 1
                        : 0);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdO]");
        }
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__land(PyMultiarray.asarray(po2));
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__land(b, result);
    }
}

final class LogicalOr extends BinaryFunction {
    boolean returnsInt = true;

    @Override final PyMultiarray identity() {
        return BinaryFunction.zero;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        int last = 0;
        final int[] rDatai = (int[]) rData;
        switch (type) {
        case '1':
            final byte[] aData1 = (byte[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) | (aData1[sa] != 0)) ? 1 : 0);
            }
            break;
        case 's':
            final short[] aDatas = (short[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) | (aDatas[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'i':
            final int[] aDatai = (int[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) | (aDatai[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'l':
            final long[] aDatal = (long[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) | (aDatal[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'f':
            final float[] aDataf = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) | (aDataf[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'd':
            final double[] aDatad = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) | (aDatad[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'F':
            final float[] aDataF = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) | (aDataF[sa] != 0 || aDataF[sa + 1] != 0)) ? 1
                        : 0);
            }
            break;
        case 'D':
            final double[] aDataD = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) | (aDataD[sa] != 0 || aDataD[sa + 1] != 0)) ? 1
                        : 0);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdO]");
        }
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__lor(PyMultiarray.asarray(po2));
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__lor(b, result);
    }
}

final class LogicalXor extends BinaryFunction {
    boolean returnsInt = true;

    @Override final PyMultiarray identity() {
        return BinaryFunction.one;
    }

    @Override final void accumulate(final Object aData, int sa, final int dim, final int dsa, final Object rData, int sr, final int rDim, final int dsr, final char type) {
        final int maxSa = sa + dim * dsa;
        int last = 1;
        final int[] rDatai = (int[]) rData;
        switch (type) {
        case '1':
            final byte[] aData1 = (byte[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) ^ (aData1[sa] != 0)) ? 1 : 0);
            }
            break;
        case 's':
            final short[] aDatas = (short[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) ^ (aDatas[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'i':
            final int[] aDatai = (int[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) ^ (aDatai[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'l':
            final long[] aDatal = (long[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) ^ (aDatal[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'f':
            final float[] aDataf = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) ^ (aDataf[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'd':
            final double[] aDatad = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) ^ (aDatad[sa] != 0)) ? 1 : 0);
            }
            break;
        case 'F':
            final float[] aDataF = (float[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) ^ (aDataF[sa] != 0 || aDataF[sa + 1] != 0)) ? 1
                        : 0);
            }
            break;
        case 'D':
            final double[] aDataD = (double[]) aData;
            for (; sa != maxSa; sa += dsa, sr += dsr) {
                rDatai[sr] = last = (((last != 0) ^ (aDataD[sa] != 0 || aDataD[sa + 1] != 0)) ? 1
                        : 0);
            }
            break;
        case 'O':
            super.accumulate(aData, sa, dim, dsa, rData, sr, rDim, dsr, type);
            break;
        default:
            throw Py.ValueError("typecd must be in [zcbhilfdO]");
        }
    }

    @Override final public PyObject call(final PyObject po1, final PyObject po2) {
        return PyMultiarray.asarray(po1).__lxor(PyMultiarray.asarray(po2));
    }

    @Override final PyObject call(final PyMultiarray a, final PyMultiarray b, final PyMultiarray result) {
        return a.__lxor(b, result);
    }
}

final class ArgMax extends BinaryFunction {
    boolean returnsInt = true;

    @Override final PyMultiarray identity() {
        return BinaryFunction.zero;
    }

    @Override final void accumulate(final Object aData, int aStart, final int aDim, final int aStride,
            final Object rData, int rStart, final int rDim, final int rStride, final char _typecode) {
        if (aDim == 0) { return; }
        int lastIndex = 0;
        switch (_typecode) {
        case '1':
        case 's':
        case 'i':
        case 'l':
        case 'f':
        case 'd':
            double lastd = ((Number) Array.get(aData, aStart)).doubleValue(),
            tempd;
            for (int i = 0; i < aDim; i++, aStart += aStride, rStart += rStride) {
                if ((tempd = ((Number) Array.get(aData, aStart)).doubleValue()) > lastd) {
                    lastIndex = i;
                    lastd = tempd;
                }
                Array.setInt(rData, rStart, lastIndex);
            }
            break;
        case 'O':
            PyObject lastO = (PyObject) Array.get(aData, aStart),
            tempO;
            for (int i = 0; i < aDim; i++, aStart += aStride, rStart += rStride) {
                if ((tempO = ((PyObject) Array.get(aData, aStart)))
                        .__cmp__(lastO) > 0) {
                    lastIndex = i;
                    lastO = tempO;
                }
                Array.setInt(rData, rStart, lastIndex);
            }
            break;
        default:
            throw Py.ValueError("typecode must be in [zcbhilfd]");
        }
    }
}

final class ArgMin extends BinaryFunction {
    boolean returnsInt = true;

    @Override final PyMultiarray identity() {
        return BinaryFunction.zero;
    }

    @Override final void accumulate(final Object aData, int aStart, final int aDim, final int aStride,
            final Object rData, int rStart, final int rDim, final int rStride, final char _typecode) {
        if (aDim == 0) { return; }
        int lastIndex = 0;
        switch (_typecode) {
        case '1':
        case 's':
        case 'i':
        case 'l':
        case 'f':
        case 'd':
            double lastd = ((Number) Array.get(aData, aStart)).doubleValue(),
            tempd;
            for (int i = 0; i < aDim; i++, aStart += aStride, rStart += rStride) {
                if ((tempd = ((Number) Array.get(aData, aStart)).doubleValue()) < lastd) {
                    lastIndex = i;
                    lastd = tempd;
                }
                Array.setInt(rData, rStart, lastIndex);
            }
            break;
        case 'O':
            PyObject lastO = (PyObject) Array.get(aData, aStart),
            tempO;
            for (int i = 0; i < aDim; i++, aStart += aStride, rStart += rStride) {
                if ((tempO = ((PyObject) Array.get(aData, aStart)))
                        .__cmp__(lastO) < 0) {
                    lastIndex = i;
                    lastO = tempO;
                }
                Array.setInt(rData, rStart, lastIndex);
            }
            break;
        default:
            throw Py.ValueError("typecode must be in [zcbhilfd]");
        }
    }
}
