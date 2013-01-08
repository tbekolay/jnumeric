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

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.util.Comparator;

import org.python.core.Py;
import org.python.core.PyArray;
import org.python.core.PyComplex;
import org.python.core.PyEllipsis;
import org.python.core.PyException;
import org.python.core.PyFloat;
import org.python.core.PyIgnoreMethodTag;
import org.python.core.PyInteger;
import org.python.core.PyList;
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.core.PySequence;
import org.python.core.PySlice;
import org.python.core.PyString;
import org.python.core.PyTuple;
import org.python.core.PyType;
import org.python.core.__builtin__;

// TODO Fix up interface to unary ufuncs.
// TODO Fix up object behaviour (doesn't work correctly(?) with e.g, tuples).
// TODO Pow uses Math.pow for integer types -- should be fixed to use an integer
//      pow.
//      Strategy: define Multiarray._pow(double, double), Multiarray._pow(int, int),
//      etc and use instead of
//      Math.pow. (double and float cases would use math.pow). (Look at CNumeric
//      umath).

/**
 * The container for n-dimensional arrays.
 * 
 * The array itself is stored as a one-dimensional array;
 * the mathematical routines manipulating these arrays
 * must treat it appropriately based on their shape
 * and stride.
 * 
 */
public class PyMultiarray extends PySequence {

    //
    // Variables and the basic constructor for Multiarray.
    // (These should only be accessed from within the Numeric (java) package.)
    //

    private static final long serialVersionUID = 4524353697384690762L;

    /**
     * Used to format arrays for str([0]) and repr([1])
     */
    public static int maxLineWidth = 77;
    /**
     * Used to format arrays for str([0]) and repr([1])
     */
    public static int precision = 8;
    /**
     * Used to format arrays for str([0]) and repr([1])
     */
    public static boolean suppressSmall = false;

    /**
     * Python class of PyMultiarray.
     * 
     * @see PyObject
     */
    public static final PyType ATYPE = PyType.fromClass(PyMultiarray.class);
    
    String docString = "PyMultiarray methods:\n" +
            "	astype(typecode)]\n" +
            "	itemsize()\n" +
            "	byteswapped()\n" +
            "	copy()\n" +
            "	typecode()\n" +
            "	iscontiguous()\n" +
            "	tostring()\n" +
            "	tolist()\n";
    
    /**
     * 1D Java array that holds array data. May be shared between arrays.
     */
    Object data;
    
    /**
     * Type code of the array. Allowable types are:
     *   '1':Byte
     *   's':Short
     *   'i':Int
     *   'l':Long
     *   'f':Float
     *   'd':Double
     *   'F':ComplexFloat
     *   'D':ComplexDouble
     *   'O':PyObject
     */
    char _typecode;
    
    // Start, dimensions, and strides determine the structure of the array.
    int start;
    int[] dimensions;
    int[] strides;
    
    // Many functions work only on contiguous arrays,
    // so we keep track of that here.
    boolean isContiguous;

    /**
     * Create a multiarray object given values of instance variables.
     * 
     * @param data The 1D array
     * @param _typecode Datatype of the array
     * @param start Start of the array
     * @param dimensions Dimensionality
     * @param strides Strides
     */
    public PyMultiarray(final Object data, final char _typecode, final int start, final int[] dimensions, final int[] strides) {
        super(PyMultiarray.ATYPE);
        this.javaProxy = this;
        this.data = data;
        this._typecode = _typecode;
        this.start = start;
        this.dimensions = dimensions;
        this.strides = strides;
        this.isContiguous = true;
        this.setIsContiguous();
    }

    /**
     * Create a multiarray object from a sequence and a type. 
     * @param seq Array-like
     * @param typecode Datatype of the array
     */
    public PyMultiarray(final PyObject seq, final char typecode) {
        super(PyMultiarray.ATYPE);
        this.javaProxy = this;
        final PyMultiarray a = PyMultiarray.array(seq, typecode);
        this.data = a.data;
        this._typecode = a._typecode;
        this.start = a.start;
        this.dimensions = a.dimensions;
        this.strides = a.strides;
        this.isContiguous = a.isContiguous;
    }

    /**
     * Create a multiarray object from a sequence. 
     * @param seq Array-like
     */
    public PyMultiarray(final PyObject seq) {
        super(PyMultiarray.ATYPE);
        this.javaProxy = this;
        final PyMultiarray a = PyMultiarray.array(seq, '\0');
        this.data = a.data;
        this._typecode = a._typecode;
        this.start = a.start;
        this.dimensions = a.dimensions;
        this.strides = a.strides;
        this.isContiguous = a.isContiguous;
    }

    /**
     * Create a multiarray object from a sequence (the slow general way)
     */
    private static PyMultiarray seqToMultiarray(final PyObject seq, char typecode) {
        final int[] newShape = PyMultiarray.shapeOf(seq);
        final PyObject[] flatData = PyMultiarray.seqToObjects(
                seq,
                PyMultiarray.shapeToNItems(newShape));
        typecode = (typecode == '\0') ? PyMultiarray.objectsToType(flatData)
                : typecode;
        final PyMultiarray newArray = PyMultiarray.zeros(
                PyMultiarray.shapeOf(seq),
                typecode);
        final int size = PyMultiarray.typeToNElements(typecode);
        for (int i = 0; i < flatData.length; i++) {
            Array.set(
                    newArray.data,
                    size * i,
                    PyMultiarray.objectToJava(flatData[i], typecode, true));
        }
        // Set complex elements if array is complex.
        if (size == 2) {
            for (int i = 0; i < flatData.length; i++) {
                Array.set(
                        newArray.data,
                        size * i + 1,
                        PyMultiarray.objectToJava(flatData[i], typecode, false));
            }
        }
        return newArray;
    }

    private static char arrayClassToType(final Class<?> klass) {
        if (klass.isArray()) {
            return PyMultiarray.arrayClassToType(klass.getComponentType());
        }
        return PyMultiarray.classToType(klass);
    }

    private static int[] arrayDataToShape(final Object data, final int depth) {
        final int length = Array.getLength(data);
        final Class<?> klass = data.getClass().getComponentType();
        // If data is an array of arrays:
        if (length != 0 && klass.isArray()) {
            final int[] shape = PyMultiarray.arrayDataToShape(
                    Array.get(data, 0),
                    depth + 1);
            shape[depth] = length;
            // Verify that the array is well formed.
            for (int i = 0; i < length; i++) {
                final int[] shape2 = PyMultiarray.arrayDataToShape(
                        Array.get(data, i),
                        depth + 1);
                if (shape.length != shape2.length) { throw Py
                        .ValueError("malformed array"); }
                for (int j = depth + 1; j < shape.length; j++) {
                    if (shape[j] != shape2[j]) { throw Py
                            .ValueError("malformed array"); }
                }
            }
            return shape;
        }
        // If data is just an array of Primitives:
        final int[] shape = new int[depth + 1];
        shape[depth] = length;
        return shape;
    }

    private static int arrayDataToFlat(final Object data, final Object flat, int offset) {
        final Class<?> klass = data.getClass().getComponentType();
        final int length = Array.getLength(data);
        if (klass.isArray()) {
            for (int i = 0; i < length; i++) {
                offset = PyMultiarray.arrayDataToFlat(
                        Array.get(data, i),
                        flat,
                        offset);
            }
            return offset;
        }
        System.arraycopy(data, 0, flat, offset, length);
        return offset + length;
    }

    /**
     * Create a multiarray object from a PyArray (jarray).
     * Don't copy unless forced to.
     */
    private static PyMultiarray arrayToMultiarray(final PyArray seq, final char typecode) {
        Object data = seq.__tojava__(Object.class);
        final char type = PyMultiarray.arrayClassToType(data.getClass());
        final int[] shape = PyMultiarray.arrayDataToShape(data, 0);
        final int[] strides = PyMultiarray.shapeToStrides(
                shape,
                PyMultiarray.typeToNElements(type));
        if (shape.length > 1) {
            final Object flat = Array.newInstance(
                    PyMultiarray.typeToClass(type),
                    PyMultiarray.shapeToNItems(shape));
            PyMultiarray.arrayDataToFlat(data, flat, 0);
            data = flat;
        }
        final PyMultiarray ma = new PyMultiarray(data, type, 0, shape, strides);
        return (typecode == '\0' || typecode == type) ? ma : PyMultiarray
                .array(ma, typecode);
    }

    //
    // Multiarray creation functions.
    //

    /**
     * Create a new multiarray from <code>seq</code> of type
     * <code>typecode</code> ('\0' indicates the type should be inferred from
     * seq).
     * @param seq Array-like
     * @param typecode Datatype of the array
     * @return The new multiarray
     */
    public static PyMultiarray array(final PyObject seq, final char typecode) {
        if (seq instanceof PyMultiarray) {
            final PyMultiarray a = (PyMultiarray) seq;
            final PyMultiarray b = PyMultiarray.zeros(
                    a.dimensions,
                    (typecode == '\0') ? a._typecode : typecode);
            PyMultiarray.copyAToB(a, b);
            return b;
        }
        if (seq instanceof PyArray) {
            final PyArray copyOfSeq = (PyArray) seq.__getslice__(
                    Py.None,
                    Py.None,
                    Py.None);
            return PyMultiarray.arrayToMultiarray(copyOfSeq, typecode);
        }
        return PyMultiarray.seqToMultiarray(seq, typecode);
    }

    /**
     * Create a new multiarray from <code>seq</code>. The type is determined by
     * examining <code>seq</code>.
     * @param seq Array-like
     * @return The new multiarray
     */
    public static PyMultiarray array(final PyObject seq) {
        return PyMultiarray.array(seq, '\0');
    }

    /**
     * Create a new multiarray of zeros with shape <code>shape</code> of type
     * <code>typecode</code>.
     * @param shape A tuple or list describing the dimensionality of the array
     *   (e.g., (10,) for a 10-element vector, [2, 5] for a 2 by 5 matrix))
     * @param typecode Datatype of the array
     * @return The new multiarray
     */
    public static PyMultiarray zeros(final int[] shape, final char typecode) {
        // int length = Math.max(1,
        // typeToNElements(typecode)*shapeToNItems(shape));
        final int length = PyMultiarray.typeToNElements(typecode)
                * PyMultiarray.shapeToNItems(shape);
        final Object data = Array.newInstance(
                PyMultiarray.typeToClass(typecode),
                length);
        if (typecode == 'O') {
            for (int i = 0; i < Array.getLength(data); i++) {
                Array.set(data, i, Py.Zero);
            }
        }
        final int[] strides = PyMultiarray.shapeToStrides(
                shape,
                PyMultiarray.typeToNElements(typecode));
        return new PyMultiarray(data, typecode, 0, shape, strides);
    }

    /**
     * Create a new multiarray of zeros with shape <code>shape</code> of type
     * <code>typecode</code>.
     * @param shape A tuple or list describing the dimensionality of the array
     *   (e.g., (10,) for a 10-element vector, [2, 5] for a 2 by 5 matrix))
     * @param typecode Datatype of the array
     * @return The new multiarray
     */
    public static PyMultiarray zeros(final Object shape, final char typecode) {
        return PyMultiarray.zeros(
                PyMultiarray.objectToInts(shape, true),
                typecode);
    }

    /**
     * Create a new multiarray of zeros with shape <code>shape</code> with type
     * inferenced from shape.
     * @param shape A tuple or list describing the dimensionality of the array
     *   (e.g., (10,) for a 10-element vector, [2, 5] for a 2 by 5 matrix))
     * @return The new multiarray
     */
    public static PyMultiarray zeros(final PyObject shape) {
        return PyMultiarray.zeros(
                PyMultiarray.objectToInts(shape, true),
                PyMultiarray.asarray(shape).typecode());
    }

    /**
     * Create a range of numbers in [start, stop) with the given step and
     * typecode.
     */
    static PyMultiarray arrayRange(final double start, final double stop, final double step, final char typecode) {
        final int length = Math.max(0, (int) Math.ceil((stop - start) / step));
        final PyMultiarray a = PyMultiarray.zeros(new int[] { length }, 'd');
        for (int i = 0; i < length; i++) {
            Array.setDouble(a.data, i, i * step + start);
        }
        return a.astype(typecode);
    }

    /**
     * Create a range of numbers in [start, stop) with the given step and
     * typecode.
     * @param start Start of the range (included)
     * @param stop End of the range (not included)
     * @param step Stepsize between the start and stop
     * @param typecode Datatype of the array
     * @return The new multiarray
     */
    public static PyMultiarray arrayRange(PyObject start, PyObject stop, final PyObject step, char typecode) {
        if (stop instanceof PyNone) {
            stop = start;
            start = Py.Zero;
        }
        if (typecode == '\0') {
            typecode = PyMultiarray.objectsToType(new PyObject[] {
                    start,
                    stop,
                    step });
        }
        if (typecode != 'O') { return PyMultiarray.arrayRange(start.__float__()
                .getValue(), stop.__float__().getValue(),
                step.__float__().getValue(), typecode); }
        // Treat objects specially.
        final PyObject lengthObject = (stop._sub(start))._div(step);
        final int length = Math.max(
                0,
                (int) Math.ceil(lengthObject.__float__().getValue()));
        start = __builtin__.coerce(start, lengthObject).__getitem__(0);
        final PyMultiarray a = PyMultiarray.zeros(new int[] { length }, 'O');
        for (int i = 0; i < length; i++) {
            a.set(i, start);
            start = start._add(step);
        }
        return a;
    }

    /**
     * Create a set of indices for use with fromFunction. 
     * @param o The input array 
     * @param typecode Datatype of the indices
     * @return The set of indices
     */
    public static PyMultiarray indices(final PyObject o, char typecode) {
        final int[] baseShape = PyMultiarray.objectToInts(o, true);
        final int[] shape = new int[baseShape.length + 1];
        for (int i = 0; i < baseShape.length; i++) {
            shape[i + 1] = baseShape[i];
        }
        shape[0] = baseShape.length;
        if (typecode == '\0') {
            typecode = PyMultiarray.asarray(o)._typecode;
        }
        final PyMultiarray result = PyMultiarray.zeros(shape, typecode);
        for (int i = 0; i < baseShape.length; i++) {
            final PyMultiarray subArray = PyMultiarray.swapAxes(
                    PyMultiarray.asarray(result.get(i)),
                    0,
                    i);
            subArray.__add__(
                    PyMultiarray.arrayRange(0, baseShape[i], 1, typecode),
                    subArray);
        }
        return result;
    }

    /**
     * Create an array by calling function the coordinates of each element of an
     * array with the given shape.
     * @param function The function to call
     * @param shape A tuple or list describing the dimensionality of the array
     *   (e.g., (10,) for a 10-element vector, [2, 5] for a 2 by 5 matrix))
     * @return The new multiarray
     */
    public static PyMultiarray fromFunction(final PyObject function, final PyObject shape) {
        final PyMultiarray index = PyMultiarray.indices(shape, '\0');
        final PyMultiarray result = PyMultiarray.zeros(shape);
        final PyMultiarray flatIndex = PyMultiarray.swapAxes(
                PyMultiarray.reshape(index, new int[] {
                        result.dimensions.length,
                        -1 }),
                0,
                1);
        final PyMultiarray flatResult = PyMultiarray.reshape(
                result,
                new int[] { -1 });
        PyObject[] args = new PyObject[0];
        final String[] keywords = new String[0];
        final Class<? extends PyObject[]> objectArray = args.getClass();
        for (int i = 0; i < flatResult.dimensions[0]; i++) {
            args = (PyObject[]) ((PyMultiarray) flatIndex.get(i)).tolist()
                    .__tojava__(objectArray);
            flatResult.set(i, function.__call__(args, keywords));
        }
        return result;
    }

    /**
     * Create a 1D array from a string.
     * @see #toString() 
     * @param s String from which to create the array
     * @param type Datatype of the array
     * @return The new multiarray
     */
    public static PyMultiarray fromString(final String s, final char type) {
        final int itemsize = PyMultiarray.typeToNBytes(type)
                * PyMultiarray.typeToNElements(type);
        if (s.length() % itemsize != 0) { throw Py
                .ValueError("string size must be a multiple of element size"); }
        Object data;
        try {
            data = PyMultiarray.fromByteArray(s.getBytes("ISO-8859-1"), type);
        } catch (final UnsupportedEncodingException e) {
            throw Py.RuntimeError("ISO-LATIN-1 encoding unavailable, can't convert from string.");
        }
        return new PyMultiarray(data, type, 0,
                new int[] { Array.getLength(data)
                        / PyMultiarray.typeToNElements(type) },
                new int[] { PyMultiarray.typeToNElements(type) });
    }

    /**
     * Return <code>seq</code> if it's a multiarray of type
     * <code>typecode</code>, otherwise returns a new multiarray.
     * @param seq Array-like
     * @param typecode Datatype of the new array
     * @return The new multiarray
     */
    public static PyMultiarray asarray(final PyObject seq, final char typecode) {
        if (seq instanceof PyMultiarray
                &&
                (typecode == '\0' || ((PyMultiarray) seq)._typecode == typecode)) {
            return (PyMultiarray) seq;
        }
        
        if (seq instanceof PyArray) {
            return PyMultiarray.arrayToMultiarray((PyArray) seq, typecode);
        }
        return PyMultiarray.array(seq, typecode);
    }

    /**
     * Return <code>seq</code> if it's a multiarray, otherwise returns a new
     * multiarray.
     * @param seq Array-like
     * @return The new multiarray
     */
    public static PyMultiarray asarray(final PyObject seq) {
        if (seq instanceof PyMultiarray) { return (PyMultiarray) seq; }
        return PyMultiarray.array(seq);
    }

    /**
     * Return <code>seq</code> if it's a contiguous multiarray, otherwise
     * returns a new multiarray.
     * @param seq Array-like
     * @param _typecode Datatype (a single character)
     * @return The new multiarray
     */
    public static PyMultiarray ascontiguous(final PyObject seq, final char _typecode) {
        if (seq instanceof PyMultiarray && ((PyMultiarray) (seq)).isContiguous
                &&
                ((PyMultiarray) seq)._typecode == _typecode) { return (PyMultiarray) seq; }
        return PyMultiarray.array(seq, _typecode);
    }

    /**
     * Return <code>seq</code> if it's a contiguous multiarray of type
     * <code>typecode</code>, otherwise returns a new multiarray.
     * @param seq Array-like
     * @return The new multiarray
     */
    public static PyMultiarray ascontiguous(final PyObject seq) {
        if (seq instanceof PyMultiarray && ((PyMultiarray) (seq)).isContiguous) { return (PyMultiarray) seq; }
        return PyMultiarray.array(seq);
    }

    //
    // Public multiarray methods and attributes.
    //

    /**
     * Return the typecode.
     * @return The typecode
     * @see #_typecode
     */
    public char typecode() {
        return this._typecode;
    }

    /**
     * Return the size (in bytes) of the items.
     * @return The size (in bytes) of the items
     */
    public int itemsize() {
        return PyMultiarray.typeToNElements(this._typecode)
                * PyMultiarray.typeToNBytes(this._typecode);
    }

    /** 
     * Return (1) if the array is contiguous, 0 otherwise. 
     * @return 1 if the array is contiguous, 0 otherwise
     */
    public int iscontiguous() {
        return this.isContiguous ? 1 : 0;
    }

    /**
     * Return multiarray coerced to <code>type</code>.
     * 
     * Note that CNumeric does the equivalent of return array(this, type) here.
     * @param type The output type
     * @return The new multiarray
     */
    public PyMultiarray astype(final char type) {
        return PyMultiarray.asarray(this, type);
    }

    /**
     * Return multiarray data represented as a string.
     * @return The string representation
     */
    public String tostring() {
        try {
            return new String(PyMultiarray.toByteArray(
                    PyMultiarray.array(this).data,
                    this._typecode), "ISO-8859-1");
        } catch (final UnsupportedEncodingException e) {
            throw Py.RuntimeError("ISO-LATIN-1 encoding unavailable, can't convert to string.");
        }
    }

    /** Return a multiarray with data byte swapped (little to big endian). */
    public final PyMultiarray byteswapped() {
        // This assumes typesize is even or 1 (it better be!).
        final PyMultiarray result = PyMultiarray.array(this);
        final byte[] bytes = PyMultiarray.toByteArray(
                result.data,
                this._typecode);
        final int typesize = PyMultiarray.typeToNBytes(this._typecode);
        final int swaps = typesize / 2;
        for (int i = 0; i < Array.getLength(result.data); i++) {
            for (int j = 0; j < swaps; j++) {
                final byte temp = bytes[i * typesize + j];
                bytes[i * typesize + j] = bytes[i * typesize
                        + (typesize - 1 - j)];
                bytes[i * typesize + (typesize - 1 - j)] = temp;
            }
        }
        result.data = PyMultiarray.fromByteArray(bytes, this._typecode);
        return result;
    }

    /** Return a copy . */
    public final PyMultiarray copy() {
        return PyMultiarray.array(this);
    }

    /** Return multiarray as a Python list. */
    public final PyList tolist() {
        if (this.dimensions.length == 0) { throw Py
                .ValueError("Can't convert a 0d array to a list"); }
        final PyObject[] items = new PyObject[this.dimensions[0]];
        if (this.dimensions.length == 1) {
            for (int i = 0; i < this.dimensions[0]; i++) {
                items[i] = this.get(i);
            }
        } else {
            for (int i = 0; i < this.dimensions[0]; i++) {
                items[i] = ((PyMultiarray) this.get(i)).tolist();
            }
        }
        return new PyList(items);
    }

    //
    // Operations on matrices.
    //

    /**
     * Return a reshaped multiarray (shares data with <code>a</code> if it is
     * contiguous).
     */
    public static PyMultiarray reshape(final PyObject o, int[] shape) {
        final PyMultiarray a = PyMultiarray.ascontiguous(o);
        shape = PyMultiarray.fixedShape(
                shape,
                PyMultiarray.shapeToNItems(a.dimensions));
        final int[] strides = PyMultiarray.shapeToStrides(
                shape,
                PyMultiarray.typeToNElements(a._typecode));
        return new PyMultiarray(a.data, a._typecode, a.start, shape, strides);
    }

    /**
     * Return a new array with the specified shape. The original array can have
     * any total size.
     */
    public static PyMultiarray resize(final PyObject o, final int[] shape) {
        final PyMultiarray a = PyMultiarray.ascontiguous(PyMultiarray.reshape(
                PyMultiarray.asarray(o),
                new int[] { -1 }));
        final int nItems = PyMultiarray.shapeToNItems(shape);
        final int nCopies = nItems / a.dimensions[0];
        final int extra = nItems % a.dimensions[0];
        final int nElements = PyMultiarray.typeToNElements(a._typecode);
        final PyMultiarray result = PyMultiarray.zeros(shape, a._typecode);
        for (int i = 0; i < nCopies; i++) {
            System.arraycopy(a.data, a.start, result.data, i * a.dimensions[0]
                    * nElements, a.dimensions[0] * nElements);
        }
        System.arraycopy(a.data, a.start, result.data, nCopies
                * a.dimensions[0] * nElements, extra * nElements);
        return result;
    }

    /** Return a new, sorted array. */
    public static PyMultiarray sort(final PyObject o, final int axis) {
        final PyMultiarray a = PyMultiarray.array(PyMultiarray.swapAxes(
                PyMultiarray.asarray(o),
                axis,
                -1));
        final int stride = a.dimensions[a.dimensions.length - 1];
        for (int i = 0; i < Array.getLength(a.data); i += stride) {
            switch (a._typecode) {
            case '1':
                java.util.Arrays.sort((byte[]) a.data, i, i + stride);
                break;
            case 's':
                java.util.Arrays.sort((short[]) a.data, i, i + stride);
                break;
            case 'i':
                java.util.Arrays.sort((int[]) a.data, i, i + stride);
                break;
            case 'l':
                java.util.Arrays.sort((long[]) a.data, i, i + stride);
                break;
            case 'f':
                java.util.Arrays.sort((float[]) a.data, i, i + stride);
                break;
            case 'd':
                java.util.Arrays.sort((double[]) a.data, i, i + stride);
                break;
            default:
                throw Py.ValueError("unsortable array type");
            }
        }
        return PyMultiarray.swapAxes(a, axis, -1);
    }

    /** See Numeric documentation. */
    public static PyMultiarray argSort(final PyObject o, final int axis) {
        // We depend on 'a' being 'vanilla' below.
        // There's a probable extra copy here though.
        final PyMultiarray a = PyMultiarray.array(PyMultiarray.swapAxes(
                PyMultiarray.asarray(o),
                axis,
                -1));
        if (a._typecode == 'F' || a._typecode == 'D' || a._typecode == 'O') { throw Py
                .ValueError("unsortable array type"); }
        // 'data' holds the argsorted indices.
        final int stride = a.dimensions[a.dimensions.length - 1];
        final int size = Array.getLength(a.data);
        final int data[] = new int[size];
        // Create an array of Numbers tagged with indices
        class IndexedArray {
            Number item;
            int index;
        }
        final IndexedArray ia[] = new IndexedArray[stride];
        for (int j = 0; j < stride; j++) {
            ia[j] = new IndexedArray();
        }
        // Create a comparator that sorts an IndexArray
        final Comparator comp = new Comparator() {
            public int compare(final Object o1, final Object o2) {
                final IndexedArray ia1 = (IndexedArray) o1;
                final IndexedArray ia2 = (IndexedArray) o2;
                if (ia1.item.equals(ia2.item)) {
                return 0;
                }
                final double d1 = ia1.item.doubleValue();
                final double d2 = ia2.item.doubleValue();
                if (d1 > d2) {
                return 1;
                }
                return -1;
            }
        };
        // Loop over all indices except the last.
        for (int i = 0; i < size; i += stride) {
            for (int j = 0; j < stride; j++) {
                ia[j].item = (Number) Array.get(a.data, i + j);
                ia[j].index = j;
            }
            // sort 'ia'
            java.util.Arrays.sort(ia, comp);
            // Load 'a' with the indices from 'ia' and change its type.
            for (int j = 0; j < stride; j++) {
                data[i + j] = ia[j].index;
            }
        }
        a._typecode = 'i';
        a.data = data;
        return PyMultiarray.swapAxes(a, axis, -1);
    }

    /**
     * Return a multiarray with the axes transposed according to
     * <code>perms</code> (shares data with <code>a</code>).
     */
    public static PyMultiarray transpose(final PyObject o, final int[] perms) {
        final PyMultiarray a = PyMultiarray.asarray(o);
        final boolean[] used = new boolean[perms.length];
        for (int i = 0; i < perms.length; i++) {
            int axis = perms[i];
            axis = (axis < 0) ? a.dimensions.length + axis : axis;
            if (axis < 0 || axis >= a.dimensions.length || used[axis]) { throw Py
                    .ValueError("illegal permutation"); }
            perms[i] = axis;
            used[axis] = true;
        }
        final PyMultiarray ans = new PyMultiarray(
                a.data,
                a._typecode,
                a.start,
                a.dimensions.clone(),
                a.strides.clone());
        for (int i = 0; i < perms.length; i++) {
            ans.dimensions[i] = a.dimensions[perms[i]];
            ans.strides[i] = a.strides[perms[i]];
        }
        ans.setIsContiguous();
        return ans;
    }

    // See David Ascher's Numeric Python documentation for what these do.
    /** Same as David Ascher's Numeric Python function. */
    public static PyMultiarray repeat(final PyObject oA, final PyObject oRepeats, int axis) {
        PyMultiarray a = PyMultiarray.asarray(oA), repeats = PyMultiarray
                .asarray(oRepeats);
        // Check axis and swap axis to zero.
        if (axis < 0) {
            axis += a.dimensions.length;
        }
        if (axis < 0 || axis >= a.dimensions.length) { throw Py
                .ValueError("illegal axis"); }
        a = PyMultiarray.swapAxes(a, 0, axis);
        // Check repeats argument, copy and cast to integer.
        repeats = PyMultiarray.array(repeats, 'i');
        if (repeats.dimensions.length != 1) { throw Py
                .ValueError("wrong number of dimensions"); }
        // Create the result array.
        final int[] dimensions = a.dimensions.clone();
        for (int i = dimensions[0] = 0; i < repeats.dimensions[0]; i++) {
            dimensions[0] += Array.getInt(repeats.data, i);
        }
        final PyMultiarray result = PyMultiarray.zeros(dimensions, a._typecode);
        int location = 0;
        for (int i = 0; i < repeats.dimensions[0]; i++) {
            final PyObject chunk = a.get(i);
            for (int j = 0; j < Array.getInt(repeats.data, i); j++) {
                result.set(location++, chunk);
            }
        }
        // Swap axis of result back to where it belongs and return.
        return PyMultiarray.swapAxes(result, 0, axis);
    }

    /** Same as David Ascher's Numeric Python function. */
    public static PyMultiarray take(final PyObject oA, final PyObject oIndices, int axis) {
        PyMultiarray a = PyMultiarray.asarray(oA), indices = PyMultiarray
                .asarray(oIndices);
        // Check axis and rotate the axis to zero.
        if (axis < 0) {
            axis += a.dimensions.length;
        }
        if (axis < 0 || axis >= a.dimensions.length) { throw Py
                .ValueError("illegal axis"); }
        a = PyMultiarray.ascontiguous(PyMultiarray.rotateAxes(a, -axis));
        // Check indices argument, copy and cast to integer.
        indices = PyMultiarray.array(indices, 'i');
        if (indices.dimensions.length != 1) { throw Py
                .ValueError("wrong number of dimensions"); }
        // Create the result array.
        final int[] dimensions = a.dimensions.clone();
        dimensions[0] = indices.dimensions[0];
        final PyMultiarray result = PyMultiarray.zeros(dimensions, a._typecode);
        final int stride = result.strides[0];
        final int start = a.start;
        for (int i = 0; i < indices.dimensions[0]; i++) {
            final int item = Array.getInt(indices.data, i);
            for (int j = 0; j < stride; j++) {
                Array.set(
                        result.data,
                        i * stride + j,
                        Array.get(a.data, start + item * stride + j));
            }
        }
        // Rotate axis of result back to where it belongs and return.
        return PyMultiarray.rotateAxes(result, axis);
    }

    /**
     * Same as David Ascher's Numeric Python function.
     * Implements <code>choose()/clip()/where()</code> functionality.
     * Choose elements from object <code>oA</code> based on set of indices
     * <code>b</code>.
     */
    public static PyMultiarray choose(final PyObject oA, final PyObject b) {
        PyMultiarray a = PyMultiarray.array(oA);
        final boolean debug_this = false;
        // Convert b into an array of PyMultiarrays.
        // (b must be a PyObject because it might not be rectangular).
        final int n = b.__len__();
        final PyMultiarray[] bs = new PyMultiarray[n];
        for (int i = 0; i < n; i++) {
            bs[i] = PyMultiarray.ascontiguous(b.__getitem__(i));
        }
        // Find a common type for the arrays in bs.
        char type = (n > 0) ? bs[0]._typecode : 'i';
        for (int i = 1; i < n; i++) {
            type = PyMultiarray.commonType(type, bs[i]._typecode);
        }
        // find bs array sizes and coerce its elements to correct type.
        a = PyMultiarray.ascontiguous(a, 'i');
        final int[] sizes = new int[n];
        for (int i = 0; i < n; i++) {
            if (a.dimensions.length < bs[i].dimensions.length) {
                if (debug_this) {
                    System.out.println("choose() got " + a);
                    System.out.println("Comparing with this: " + bs[i]);
                }
                throw Py.ValueError("choice array has too many dimensions: "
                        + bs[i].dimensions.length + " > " + a.dimensions.length);
            }
            for (int j = 0; j < bs[i].dimensions.length; j++) {
                if (a.dimensions[j + a.dimensions.length
                        - bs[i].dimensions.length] != bs[i].dimensions[j]) { throw Py
                        .ValueError("array dimensions must agree."); }
            }
            bs[i] = PyMultiarray.array(bs[i], type);
            sizes[i] = Array.getLength(bs[i].data);
        }
        final PyMultiarray result = PyMultiarray.zeros(a.dimensions, type);
        final PyMultiarray flat = PyMultiarray
                .reshape(result, new int[] { -1 });
        for (int i = 0; i < Array.getLength(flat.data); i++) {
            final int index = Array.getInt(a.data, i);
            if (index < 0 || index >= n) { throw Py
                    .ValueError("invalid entry in choice array"); }
            Array.set(flat.data, i, Array.get(bs[index].data, i % sizes[index]));
        }
        return result;
    }

    /** Same as David Ascher's Numeric Python function. */
    public static PyMultiarray concatenate(final PyObject po, int axis) {
        if (po.__len__() == 0) { return PyMultiarray.zeros(new int[] {}, 'i'); }
        // Check axis and rotate the axis to zero.
        PyMultiarray proto = PyMultiarray.asarray(po.__getitem__(0));
        if (axis < 0) {
            axis += proto.dimensions.length;
        }
        if (axis < 0 || axis >= proto.dimensions.length) { throw Py
                .ValueError("illegal axis"); }
        // Make array of multiarrays.
        final PyMultiarray[] as = new PyMultiarray[po.__len__()];
        as[0] = proto = PyMultiarray.rotateAxes(proto, -axis);
        char type = proto.typecode();
        final int[] dimensions = proto.dimensions.clone();
        for (int i = 1; i < as.length; i++) {
            as[i] = PyMultiarray.rotateAxes(
                    PyMultiarray.asarray(po.__getitem__(i)),
                    -axis);
            type = PyMultiarray.commonType(type, as[i]._typecode);
            if (as[i].dimensions.length != proto.dimensions.length) { throw Py
                    .ValueError("mismatched array dimensions"); }
            for (int j = 1; j < proto.dimensions.length; j++) {
                if (as[i].dimensions[j] != proto.dimensions[j]) { throw Py
                        .ValueError("mismatched array dimensions"); }
            }
            dimensions[0] += as[i].dimensions[0];
        }
        // Construct the result.
        final PyMultiarray result = PyMultiarray.zeros(dimensions, type);
        int start = 0;
        for (final PyMultiarray element : as) {
            final int end = start + element.dimensions[0];
            result.setslice(start, end, 1, element);
            start = end;
        }
        // Rotate axes back and return result.
        return PyMultiarray.rotateAxes(result, axis);
    }

    /**
     * Return the diagonal of a matrix.
     * Actually, it returns a rank-one array containing all elements
     * of the original, such that the difference between their indices
     * along the specified axes is equal to the specified offset. This
     * means it can operate on non-square matrices too.
     */
    // XXX This is a direct translation from Python -- need to figure it out.
    public static PyMultiarray diagonal(final PyObject o, int offset, final int axis) {
        // XXX Check arguments.
        // Leave debugging statements here, but turned off, until thoroughly
        // tested.
        final boolean debug_this = false;
        if (debug_this) {
            System.out
                    .println("\n************************\nDiagonal gets passed in 'o' = "
                            + o +
                            "with offset = " + offset + ", and axis = "
                            + axis);
        }
        PyMultiarray a = PyMultiarray.rotateAxes(PyMultiarray.asarray(o), -2
                - axis);
        if (debug_this) {
            System.out.println("\nDiagonal converts 'o' to " + a);
        }
        final int lastDimension = a.dimensions[a.dimensions.length - 1];
        if (debug_this) {
            System.out.println("\nLast dimension = a.dimensions[" +
                    a.dimensions.length + " -1] = " +
                    a.dimensions[a.dimensions.length - 1] + " = "
                    + lastDimension);
        }
        final int[] shape = new int[a.dimensions.length - 1];
        for (int i = 0; i < shape.length; i++) {
            shape[i] = a.dimensions[i];
        }
        if (debug_this) {
            System.out.println("shape[" + (shape.length - 1) + "] = " +
                    shape[shape.length - 1] + " becomes " +
                    shape[shape.length - 1] * lastDimension);
        }
        shape[shape.length - 1] *= lastDimension;
        final int oldAshape = a.dimensions[a.dimensions.length - 1];

        a = PyMultiarray.reshape(a, shape);
        // System.out.println("Reshaping a to " + a);
        // Need to adjust last dimension of shape to prevent
        // wrap-around on higher off-diagonals.
        final int shapeLastDim = shape[shape.length - 1] / lastDimension;
        if (debug_this) {
            System.out.println("offset = " + offset + ", shapeLastDim = "
                    + shapeLastDim
                    + ", a.dim[a.dim.len -2] = " + oldAshape
                    + ", lastDimension - shapeLastDim = "
                    + (lastDimension - shapeLastDim));
        }
        final int oldShape = shape[shape.length - 1];
        if (offset > 0 && offset > lastDimension - shapeLastDim) {
            shape[shape.length - 1] -= (offset - (lastDimension - shapeLastDim))
                    * lastDimension;
            if (debug_this) {
                System.out.println("Offset changes shape[shape.length-1] from "
                        + oldShape
                        + " to " + shape[shape.length - 1]);
            }
        }
        if (offset < 0) {
            offset = -lastDimension * offset;
        }
        if (debug_this) {
            System.out.println("\nArrayRange("
                    + offset
                    + ","
                    +
                    shape[shape.length - 1]
                    + ","
                    +
                    (lastDimension + 1)
                    + ") = "
                    + PyMultiarray.arrayRange(
                            offset,
                            shape[shape.length - 1],
                            lastDimension + 1,
                            'i'));
        }
        a = PyMultiarray.take(a, PyMultiarray.arrayRange(
                offset,
                shape[shape.length - 1],
                lastDimension + 1,
                'i'), -1);
        // System.out.println("\nTake(a, arrayRange) leaves us with " + a );
        return PyMultiarray.rotateAxes(a, 2 + axis);
    }

    // XXX check again!
    /** Same as David Ascher's Numeric Python function. */
    public static PyObject innerProduct(final PyObject oA, final PyObject oB, int axisA, int axisB) {
        PyMultiarray a = PyMultiarray.ascontiguous(oA), b = PyMultiarray
                .ascontiguous(oB);
        // Check arguments
        // This next line emulates CNumeric behaviour that I'm not sure I like.
        if (a.dimensions.length == 0 || b.dimensions.length == 0) { return a
                .__mul__(b); }
        final char type = PyMultiarray.commonType(a._typecode, b._typecode);
        if (axisA < 0) {
            axisA += a.dimensions.length;
        }
        if (axisB < 0) {
            axisB += b.dimensions.length;
        }
        if (axisA < 0 || axisA >= a.dimensions.length || axisB < 0
                || axisB >= b.dimensions.length) { throw Py
                .ValueError("illegal axis"); }
        if (a.dimensions[axisA] != b.dimensions[axisB]) { throw Py
                .ValueError("arrays must be of same length along given axes"); }
        // Rotate given axes to 0.
        a = PyMultiarray.rotateAxes(a, -axisA);
        b = PyMultiarray.rotateAxes(b, -axisB);
        // Now do the inner product.
        final int nDimsA = a.dimensions.length, nDimsB = b.dimensions.length;
        final int[] dimensions = new int[nDimsA + nDimsB - 2];
        final int[] aDimensions = new int[nDimsA + nDimsB - 1];
        final int[] bDimensions = new int[nDimsA + nDimsB - 1];
        for (int i = 1; i < nDimsA; i++) {
            dimensions[i - 1] = aDimensions[i] = a.dimensions[i];
            bDimensions[i] = 1;
        }
        for (int i = 1; i < nDimsB; i++) {
            dimensions[nDimsA + i - 2] = bDimensions[nDimsA + i - 1] = b.dimensions[i];
            aDimensions[nDimsA + i - 1] = 1;
        }
        aDimensions[0] = bDimensions[0] = a.dimensions[0];
        a = PyMultiarray.reshape(a, aDimensions);
        b = PyMultiarray.reshape(b, bDimensions);
        final PyMultiarray result = PyMultiarray.zeros(dimensions, type);
        for (int i = 0; i < a.dimensions[0]; i++) {
            result.__add__(PyMultiarray.asarray(PyMultiarray.asarray(a.get(i))
                    .__mul__(b.get(i))), result);
        }
        // unrotate the axes and return.
        final int[] axes = new int[result.dimensions.length];
        for (int i = 0; i < nDimsA - 1; i++) {
            axes[i] = (i + axisA) % (nDimsA - 1);
        }
        for (int i = 0; i < nDimsB - 1; i++) {
            axes[nDimsA - 1 + i] = (nDimsA - 1) + (i + axisB - 1)
                    % (nDimsB - 1);
        }
        return PyMultiarray.returnValue(PyMultiarray.transpose(result, axes));
    }

    /**
     * Return an array of indices of where the items would be locatated in the
     * given array.
     */
    public static PyObject searchSorted(final PyObject o, final PyObject v) {
        final PyMultiarray a = PyMultiarray.ascontiguous(o);
        PyMultiarray values = PyMultiarray.ascontiguous(v);
        if (a.dimensions.length != 1 || values.dimensions.length > 1) { throw Py
                .ValueError("searchSorted only works on 1D arrays"); }
        if (a._typecode == 'F' || a._typecode == 'D') { throw Py
                .ValueError("cannot search complex arrays"); }
        final boolean singleValue = (values.dimensions.length == 0);
        if (singleValue) {
            values = PyMultiarray.stretchAxes(values);
        }
        final PyMultiarray result = PyMultiarray.zeros(
                new int[] { values.dimensions[0] },
                'i');
        for (int i = 0; i < values.dimensions[0]; i++) {
            int start = 0, stop = a.dimensions[0] - 1;
            int j = (start + stop) / 2;
            if (a._typecode != 'O' && values._typecode != 'O') {
                final double value = ((Number) Array.get(
                        values.data,
                        values.start + i)).doubleValue();
                while (start != stop) {
                    final double val = ((Number) Array.get(a.data, a.start + j))
                            .doubleValue();
                    if ((val == value) || (stop == j)) {
                        break;
                    } else if (val < value) {
                        start = j;
                    } else {
                        stop = j;
                    }
                    j = (start + stop + 1) / 2;
                }
            } else {
                final PyObject value = values.get(i);
                while (start != stop) {
                    final PyObject val = a.get(j);
                    if (val._eq(value).__nonzero__() || (stop == j)) {
                        break;
                    } else if (val._le(value).__nonzero__()) {
                        start = j;
                    } else {
                        stop = j;
                    }
                    j = (start + stop + 1) / 2;
                }
            }
            Array.setInt(result.data, i, j);
        }
        if (singleValue) { return result.get(0); }
        return result;
    }

    /** Return convolution of two vectors. */
    public static PyMultiarray convolve(final PyObject oA0, final PyObject oB0, final int mode) {
        PyMultiarray a0 = PyMultiarray.asarray(oA0), b0 = PyMultiarray
                .asarray(oB0);
        // Check arguments (If anyone cares, the 1d requirement could probably
        // be relaxed.)
        if (a0.dimensions.length != 1 || b0.dimensions.length != 1) { throw Py
                .ValueError("convolve only works on 1D arrays"); }
        // Make the arrays contiguous and then make a copy of the nondata parts.
        a0 = PyMultiarray.ascontiguous(a0);
        b0 = PyMultiarray.ascontiguous(b0);
        final PyMultiarray a = new PyMultiarray(
                a0.data,
                a0._typecode,
                a0.start,
                a0.dimensions.clone(),
                a0.strides.clone());
        final PyMultiarray b = new PyMultiarray(
                b0.data,
                b0._typecode,
                b0.start,
                b0.dimensions.clone(),
                b0.strides.clone());
        // Create the result array.
        final char type = PyMultiarray.commonType(a._typecode, b._typecode);
        int padl = 0;
        int length = Math.max(a.dimensions[0], b.dimensions[0]);
        final int n = Math.min(a.dimensions[0], b.dimensions[0]);
        switch (mode) {
        case 0:
            length = length - n + 1;
            break;
        case 1:
            padl = n / 2;
            break;
        case 2:
            length = length + n - 1;
            padl = n - 1;
            break;
        default:
            throw Py.ValueError("mode must be 0,1, or 2");
        }
        // Create the result.
        final PyMultiarray result = PyMultiarray.zeros(
                new int[] { length },
                type);
        final int aSize = PyMultiarray.typeToNElements(a._typecode);
        final int bSize = PyMultiarray.typeToNElements(b._typecode);
        a.strides[0] = -1;
        a.isContiguous = false;
        for (int i = (n - padl - 1); i < length + (n - padl - 1); i++) {
            final int j0 = Math.max(0, i - (a0.dimensions[0] - 1));
            final int j1 = Math.min(i + 1, b0.dimensions[0]);
            a.start = a0.start + aSize * (i - j0);
            b.start = b0.start + bSize * j0;
            a.dimensions[0] = b.dimensions[0] = j1 - j0;
            result.set(i - (n - padl - 1), Umath.add.reduce(a.__mul__(b)));
        }
        return result;
    }

    /**
     * Compute the padded length of a vector prior to using FFT.
     * This is a helper routine for cross_correlate()
     */
    public static int XC_padded_length(final int length) {
        // Length of padding for FFT.
        // Find the largest power of two that is less than 2*length.
        return (int) Math.pow(
                2.0,
                Math.ceil(Math.log(2.0 * length) / Math.log(2.0)));
    }

    /**
     * Perform padding on vector, prior to using FFT.
     * This is a helper routine for cross_correlate()
     */
    public static PyMultiarray XC_pad_vector(final PyMultiarray V, final int padl) {
        final char type = V.typecode();
        final PyMultiarray V_pad = PyMultiarray.zeros(new int[] { padl }, type);
        final int V_dim = V.dimensions[0];
        final int Lv2 = V_dim / 2;
        final boolean debug_this = false;

        if (V_dim % 2 == 0) {
            V_pad.setslice(0, Lv2, 1, V.getslice(Lv2, V_dim, 1));
            V_pad.setslice(padl - Lv2, padl, 1, V.getslice(0, Lv2, 1));
        }
        else {
            V_pad.setslice(0, Lv2 + 1, 1, V.getslice(Lv2, V_dim, 1));
            V_pad.setslice(padl - Lv2, padl, 1, V.getslice(0, Lv2, 1));
        }
        return V_pad;
    }

    /**
     * Recover the result of an FFT-based correlation.
     * This is a helper routine for cross_correlate()
     */
    public static PyMultiarray XC_unpad_vector(final PyMultiarray R, int lenA, int lenB, final int mode) {
        final boolean debug_this = false;
        if (debug_this) {
            System.out.println("R = " + R);
        }
        lenB = Math.max(lenA, lenB);
        lenA = Math.min(lenA, lenB);
        final int L = lenA + lenB;
        final int L2 = L / 2;
        final char type = R.typecode();
        final int R_dim = R.dimensions[0];

        /*
         * First pull out the non-zero part of the FFT. Regardless of
         * the mode we're in, we'll always be returning a subset of
         * this. For two vectors of length lenA and lenB, there will
         * always be (lenA + lenB - 1) of these.
         */

        final PyMultiarray tmp = PyMultiarray.zeros(new int[] { L - 1 }, type);

        if (L % 2 == 1) {
            if (debug_this) {
                System.out.println("UNPAD(odd): type = '" + type + "', L2 = "
                        + L2 + ", R_dim = " + R_dim);
            }
            tmp.setslice(
                    0,
                    L2 + 1,
                    1,
                    R.getslice(R_dim - (L2 + 1), R_dim + 1, 1));
            tmp.setslice(L2, L - 1, 1, R.getslice(0, L2 - 1, 1));
        }
        else {
            if (debug_this) {
                System.out.println("UNPAD(even): type = '" + type + "', L2 = "
                        + L2 + ", R_dim = " + R_dim);
            }
            tmp.setslice(0, L2 - 1, 1, R.getslice(R_dim - L2 + 1, R_dim, 1));
            tmp.setslice(L2 - 1, L + 1, 1, R.getslice(0, L2, 1));
        }

        final int start = L - lenB;
        if (debug_this) {
            System.out.println("\nUNPAD: tmp swapped = " + tmp);
        }
        PyMultiarray result;

        // Now pull out the desired elements, in accordance with 'mode'.
        switch (mode) {
        case 0:
            result = PyMultiarray.zeros(new int[] { lenB - lenA + 1 }, type);
            if (debug_this) {
                System.out.println("\nUNPAD: L = " + L + ", lenB = " + lenB
                        + ", start = " + start);
            }
            result.setslice(
                    0,
                    L - 2 * start + 1,
                    1,
                    tmp.getslice(start - 1, L - start, 1));
            break;
        case 1:
            result = PyMultiarray.zeros(new int[] { lenB }, type);
            if (debug_this) {
                System.out.println("\nUNPAD: lenB = " + lenB + ", start/2 = "
                        + start / 2
                        + ", L-start/2 = " + (L - start / 2));
            }
            result.setslice(
                    0,
                    lenB,
                    1,
                    tmp.getslice(start / 2, lenB + start / 2, 1));
            break;
        case 2: // Return all elements.
            result = tmp;
            break;
        default:
            throw Py.ValueError("mode must be 0,1, or 2");
        }

        // Finally, reverse the result inplace, then return.
        result.setslice(
                0,
                result.dimensions[0],
                1,
                result.getslice(result.dimensions[0], -1, -1));
        if (debug_this) {
            System.out.println("\nUNPAD: result = " + result);
        }
        return result;
    }

    /**
     * Return cross-correlation of two vectors.
     * Cross-correlation is performed in Fourier space, since the
     * computational complexity is Nlog(N), compared to N^2 in real space,
     * (N=length of vectors),
     */
    public static PyMultiarray cross_correlate(final PyObject oA0, final PyObject oB0, final int mode) {
        final boolean debug_this = false;
        if (debug_this) {
            System.out.println("MODE = " + mode);
        }
        PyMultiarray a0 = PyMultiarray.asarray(oA0), b0 = PyMultiarray
                .asarray(oB0);
        // Check arguments (If anyone cares, the 1d requirement could probably
        // be relaxed.)
        if (a0.dimensions.length != 1 || b0.dimensions.length != 1) { throw Py
                .ValueError("cross_correlate only works on 1D arrays"); }
        // Make the arrays contiguous and then make a copy of the nondata parts.
        a0 = PyMultiarray.ascontiguous(a0);
        b0 = PyMultiarray.ascontiguous(b0);
        final PyMultiarray a = new PyMultiarray(
                a0.data,
                a0._typecode,
                a0.start,
                a0.dimensions.clone(),
                a0.strides.clone());
        final PyMultiarray b = new PyMultiarray(
                b0.data,
                b0._typecode,
                b0.start,
                b0.dimensions.clone(),
                b0.strides.clone());

        final char type = PyMultiarray.commonType(a._typecode, b._typecode);
        final int length = Math.max(a.dimensions[0], b.dimensions[0]);

        /*
         * To do proper convolution(A, B), it's not enough to just FFT A and B,
         * multiply A and conjugate(B), and return the real part of the
         * inverse-FFT.
         * You need to pad.
         * Padding serves two purposes:
         * 1. It makes the vector's length equal to a power of two, so that the
         * FFT is happy.
         * 2. It prevents contamination of the results by the assumption of an
         * infinite series,
         * when any real-life series is finite.
         * 3. We do more than just padding - we re-arrange the data into a
         * particular format.
         * How much contamination is satisfactory?
         * That's determined by the choice of 'mode' variable. See User Guide
         * for CNumeric.
         * We use the FFT for a number of reasons:
         * 1. It's much faster than computing the convolution by the direct
         * method
         * (O[NlogN] compared to O[N^2])
         * 2. The FFT has already been implemented, so we don't have as much
         * work to do here.
         * 3. Any improvements made in the future to the FFT routines
         * will automatically be available here too.
         */

        // Create copies of the two arrays, arranged in particular format,
        // pad them out to a power of two.

        final int padl = PyMultiarray.XC_padded_length(length);
        if (debug_this) {
            System.out.println("Length = " + length + " --> PADL = " + padl);
        }

        final PyMultiarray a_pad = PyMultiarray.XC_pad_vector(a, padl);
        final PyMultiarray fft_a = FFT.fft(a_pad);

        final PyMultiarray b_pad = PyMultiarray.XC_pad_vector(b, padl);

        /*
         * FF-Transformed arrays are complex, so is their product.
         * Correlation works only on real (or integer) arguments and
         * the result should have the same type as the original
         * vectors. If integer, we need to make sure that we
         * round, not just truncate.
         * Finally, copy results into standard order ('unpad' them).
         */
        final PyMultiarray prod = PyMultiarray.zeros(new int[] { padl }, 'D');
        fft_a.__mul__(Umath.conjugate.__call__(FFT.fft(b_pad)), prod);
        PyMultiarray inverse_prod_real = FFT.inverse_fft(prod).getReal();

        PyMultiarray result;
        if (PyMultiarray.typeToKind(type) == PyMultiarray.INTEGER) {
            // This needs to be converted to a bona fide round() function.
            // Anyone know is there is one already?
            inverse_prod_real = (PyMultiarray) inverse_prod_real.__add__(Py
                    .newFloat(0.5));
            final PyMultiarray inverse_prod_real_round = PyMultiarray.asarray(
                    inverse_prod_real,
                    type);
            inverse_prod_real = inverse_prod_real_round;
            result = PyMultiarray.XC_unpad_vector(
                    inverse_prod_real_round,
                    a.dimensions[0],
                    b.dimensions[0],
                    mode);
        }
        else {
            result = PyMultiarray.XC_unpad_vector(
                    inverse_prod_real,
                    a.dimensions[0],
                    b.dimensions[0],
                    mode);
        }
        if (debug_this) {
            System.out.println("\nCROSS_CORRELATE: RESULT = " + result);
        }
        return result;
    }

    //
    // Accessory functions (may be useful for other members of JNumeric
    // package).
    //

    /** Return the real part of this multiarray. */
    final PyMultiarray getReal() {
        if (this._typecode == 'F' || this._typecode == 'D') {
            final int[] dims = this.dimensions.clone();
            final int[] strs = this.strides.clone();
            final char type = (this._typecode == 'F') ? 'f' : 'd';
            return new PyMultiarray(this.data, type, this.start, dims, strs);
        }
        return this;
    }

    /**
     * Return the imaginary part of this multiarray if its complex otherwise
     * return null.
     */
    final PyMultiarray getImag() {
        if (this._typecode == 'F' || this._typecode == 'D') {
            final int[] dims = this.dimensions.clone();
            final int[] strs = this.strides.clone();
            final char type = (this._typecode == 'F') ? 'f' : 'd';
            return new PyMultiarray(this.data, type, this.start + 1, dims, strs);
        }
        return null;
    }

    /** Return the shape of a Python sequence. */
    public final static int[] shapeOf(final PyObject seq) {
        if (seq instanceof PyMultiarray) { return ((PyMultiarray) seq).dimensions
                .clone(); }
        return PyMultiarray._shapeOf(seq, 0);
    }

    private final static int[] _shapeOf(final PyObject seq, final int depth) {
        int items;
        // Take care of special cases (strings, nonsequence, and empty
        // sequence).
        if (seq instanceof PyString) { return new int[depth]; }
        try {
            items = seq.__len__();
        } catch (final Throwable e) {
            return new int[depth];
        }
        if (items == 0) { return new int[depth + 1]; }
        // Loop over sequence elements and determine shape.
        final int[] shape = PyMultiarray
                ._shapeOf(seq.__getitem__(0), depth + 1);
        shape[depth] = items;
        for (int i = 1; i < items; i++) {
            final int[] shape2 = PyMultiarray._shapeOf(
                    seq.__getitem__(i),
                    depth + 1);
            if (shape.length != shape2.length) { throw Py
                    .ValueError("malformed array"); }
            for (int j = depth + 1; j < shape.length; j++) {
                if (shape[j] != shape2[j]) { throw Py
                        .ValueError("malformed array"); }
            }
        }
        return shape;
    }

    /** Return a multiarray with the axes rotated by n (axis 0->n, 1->n+1, etc.) */
    static PyMultiarray rotateAxes(final PyMultiarray a, int n) {
        final PyMultiarray result = new PyMultiarray(
                a.data,
                a._typecode,
                a.start,
                a.dimensions.clone(),
                a.strides.clone());
        while (n < 0) {
            n += a.dimensions.length;
        }
        for (int i = 0; i < a.dimensions.length; i++) {
            result.dimensions[(i + n) % a.dimensions.length] = a.dimensions[i];
            result.strides[(i + n) % a.dimensions.length] = a.strides[i];
        }
        result.setIsContiguous();
        return result;
    }

    /** Return a multiarray with the axess n0 and n1 swapped. */
    static PyMultiarray swapAxes(final PyMultiarray a, int n0, int n1) {
        final PyMultiarray result = new PyMultiarray(
                a.data,
                a._typecode,
                a.start,
                a.dimensions.clone(),
                a.strides.clone());
        if (n0 < 0) {
            n0 += a.dimensions.length;
        }
        if (n1 < 0) {
            n1 += a.dimensions.length;
        }
        if (n0 < 0 || n0 >= a.dimensions.length || n1 < 0
                || n1 >= a.dimensions.length) { throw Py
                .ValueError("illegal axis"); }
        result.dimensions[n0] = a.dimensions[n1];
        result.strides[n0] = a.strides[n1];
        result.dimensions[n1] = a.dimensions[n0];
        result.strides[n1] = a.strides[n0];
        result.setIsContiguous();
        return result;
    }

    /**
     * Return the number of elements for this typecode (2 if complex, otherwise
     * 1).
     */
    final static int typeToNElements(final char typecode) {
        switch (typecode) {
        case 'F':
        case 'D':
            return 2;
        default:
            return 1;
        }
    }

    /**
     * Return a common typecode given two typecodes <code>a</code> and
     * <code>b</code>.
     */
    final static char commonType(final char a, final char b) {
        if (a == b) { return a; }
        final short atype = PyMultiarray.typeToKind(a), btype = PyMultiarray
                .typeToKind(b);
        final short newtype = (atype > btype) ? atype : btype;
        if (newtype == PyMultiarray.PYOBJECT) { return 'O'; }
        final short asize = PyMultiarray.typeToNBytes(a), bsize = PyMultiarray
                .typeToNBytes(b);
        final short newsize = (asize > bsize) ? asize : bsize;
        return PyMultiarray.kindAndNBytesToType(newtype, newsize);
    }

    /** Convert a sequence to an array of ints. */
    static int[] objectToInts(final Object jo, final boolean forgiving) {
        if (jo instanceof int[]) { return (int[]) jo; }

        if (!(jo instanceof PyObject)) { throw Py
                .ValueError("cannot convert argument to array of ints"); }
        PyObject o = (PyObject) jo;
        if (PyMultiarray.shapeOf(o).length == 0) {
            if (forgiving) {
                // return new int []
                // {asarray(o).__getitem__(Py.Ellipsis).__int__().getValue()};
                o = PyMultiarray.asarray(o).__getitem__(Py.Ellipsis);
            } else {
                throw Py.ValueError("cannot convert argument to array of ints");
            }
        }
        int length;
        try {
            length = o.__len__();
        } catch (final Throwable t) {
            return new int[] { Py.py2int(o) };
        }
        final int[] intArray = new int[length];
        for (int i = 0; i < intArray.length; i++) {
            final PyObject item = o.__getitem__(i);
            if (!forgiving && !(item instanceof PyInteger)) { throw Py
                    .ValueError("cannot convert argument to array of ints"); }
            intArray[i] = Py.py2int(o.__getitem__(i));
        }
        return intArray;
    }

    //
    // Private methods (I can't see any other classes needing these.)
    //

    /**
     * Return an array of PyObjects that is a flattened version of sequence.
     * <code>size</code> is the size of the resulting array (determined
     * beforehand using shapeToNItems).
     */
    private final static PyObject[] seqToObjects(final PyObject seq, final int size) {
        final PyObject[] flat = new PyObject[size];
        PyMultiarray._seqToObjects(seq, flat, 0);
        return flat;
    }

    private final static int _seqToObjects(final PyObject seq, final PyObject[] flat, int offset) {
        int items;
        if (seq instanceof PyString) {
            flat[offset] = seq;
            return offset + 1;
        }
        try {
            items = seq.__len__();
        } catch (final Throwable t) {
            flat[offset] = seq;
            return offset + 1;
        }
        for (int i = 0; i < items; i++) {
            offset = PyMultiarray._seqToObjects(
                    seq.__getitem__(i),
                    flat,
                    offset);
        }
        return offset;
    }

    /**
     * Check shape for errors and replace the rubber index with any with an
     * appropriate size.
     */
    private static int[] fixedShape(int[] shape, final int totalSize) {
        shape = shape.clone();
        int size = 1;
        int rubberAxis = -1;
        for (int i = 0; i < shape.length; i++) {
            if (shape[i] == -1) {
                if (rubberAxis != -1) { throw Py.ValueError("illegal shape"); }
                rubberAxis = i;
            } else {
                size *= shape[i];
            }
        }
        if (rubberAxis != -1) {
            shape[rubberAxis] = totalSize / size;
            size *= shape[rubberAxis];
        }
        if (totalSize != size) { throw Py
                .ValueError("total size of new array must be unchanged"); }
        return shape;
    }

    /**
     * Convert a PyObject into a native Java type based on <code>typecode</code>
     * .
     * If returnReal is false, the complex part of the given object is returned,
     * this defaults to zero if the PyObject is not an instance of PyComplex.
     */
    private final static Object objectToJava(PyObject o, final char typecode, final boolean returnReal) {
        if (typecode == 'O') { return o; }
        if (o instanceof PyComplex) {
            o = Py.newFloat(returnReal ? ((PyComplex) o).real
                    : ((PyComplex) o).imag);
        } else if (!returnReal) {
            o = Py.Zero;
        }
        final Object number = o.__tojava__(PyMultiarray.typeToClass(typecode));
        if (number == Py.NoConversion) { throw Py.ValueError("coercion error"); }
        return number;
    }

    /** Return the appropriate typecode for a PyObject. */
    private final static char objectToType(final PyObject o) {
        if (o instanceof PyInteger) {
            return 'i';
        }
        else if (o instanceof PyFloat) {
            return 'd';
        }
        else if (o instanceof PyComplex) {
            return 'D';
        }
        else {
            return 'O';
        }
    }

    /** Find an appropriate common type for an array of PyObjects. */
    private final static char objectsToType(final PyObject[] objects) {
        if (objects.length == 0) { return 'i'; }
        short new_no, no = -1;
        short new_sz, sz = -1;
        for (final PyObject o : objects) {
            final char _typecode = PyMultiarray.objectToType(o);
            new_no = PyMultiarray.typeToKind(_typecode);
            new_sz = PyMultiarray.typeToNBytes(_typecode);
            no = (no > new_no) ? no : new_no;
            sz = (sz > new_sz) ? sz : new_sz;
        }
        return PyMultiarray.kindAndNBytesToType(no, sz);
    }

    /** Return a Java Class that matches typecode. */
    private final static Class typeToClass(final char typecode) {
        switch (typecode) {
        case '1':
            return Byte.TYPE;
        case 's':
            return Short.TYPE;
        case 'i':
            return Integer.TYPE;
        case 'l':
            return Long.TYPE;
        case 'f':
        case 'F':
            return Float.TYPE;
        case 'd':
        case 'D':
            return Double.TYPE;
        case 'O':
            return PyObject.class;
        default:
            throw Py.ValueError("typecode must be in [1silfFdDO]");
        }
    }

    /** Return a typecode that matches the given Java class */
    private final static char classToType(final Class klass) {
        if (klass.equals(Byte.TYPE)) { return '1'; }
        if (klass.equals(Short.TYPE)) { return 's'; }
        if (klass.equals(Integer.TYPE)) { return 'i'; }
        if (klass.equals(Long.TYPE)) { return 'l'; }
        if (klass.equals(Float.TYPE)) { return 'f'; }
        if (klass.equals(Double.TYPE)) { return 'd'; }
        if (klass.equals(PyObject.class)) { return 'O'; }
        throw Py.ValueError("unknown class in classToType");
    }

    /**
     * Return the number of items of a multiarray based on its shape
     * (dimensions).
     */
    private final static int shapeToNItems(final int[] shape) {
        int size = 1;
        for (final int element : shape) {
            if (element < 0) { throw Py
                    .ValueError("negative dimensions are not allowed"); }
            size *= element;
        }
        return size;
    }

    /**
     * Return the strides for a new multiarray based on its shape and the number
     * of elements per item.
     */
    private final static int[] shapeToStrides(final int[] shape, final int nElements) {
        final int[] strides = new int[shape.length];
        int stride = nElements;
        for (int i = shape.length - 1; i >= 0; i--) {
            strides[i] = stride;
            stride *= shape[i];
        }
        return strides;
    }

    // These enumerate the kinds of types. Lower numbered types can be cast to
    // higher, but not vice versa.
    private final static short INTEGER = 1;
    private final static short FLOATINGPOINT = 2;
    private final static short COMPLEX = 3;
    private final static short PYOBJECT = 4;

    /**
     * Return the kind of type this is (INTEGER, FLOATINGPOINT, COMPLEX, or
     * PYOBJECT).
     */
    private final static short typeToKind(final char typecode) {
        switch (typecode) {
        case '1':
        case 's':
        case 'i':
        case 'l':
            return PyMultiarray.INTEGER;
        case 'f':
        case 'd':
            return PyMultiarray.FLOATINGPOINT;
        case 'F':
        case 'D':
            return PyMultiarray.COMPLEX;
        case 'O':
            return PyMultiarray.PYOBJECT;
        default:
            throw Py.ValueError("internal error in typeToKind");
        }
    }

    /** Return the number of bytes per element for the given typecode. */
    private final static short typeToNBytes(final char typecode) {
        switch (typecode) {
        case '1':
            return 1;
        case 's':
            return 2;
        case 'i':
        case 'f':
        case 'F':
            return 4;
        case 'l':
        case 'd':
        case 'D':
            return 8;
        case 'O':
            return 0;
        default:
            throw Py.ValueError("internal error in typeToNBytes");
        }
    }

    /** Return a typecode that matches a given type kind and size (in bytes). */
    private final static char kindAndNBytesToType(final short kind, final short nbytes) {
        switch (kind) {
        case INTEGER:
            switch (nbytes) {
            case 1:
                return '1';
            case 2:
                return 's';
            case 4:
                return 'i';
            case 8:
                return 'l';
            default:
                break;
            }
            break;
        case FLOATINGPOINT:
            switch (nbytes) {
            case 4:
                return 'f';
            case 8:
                return 'd';
            default:
                break;
            }
            break;
        case COMPLEX:
            switch (nbytes) {
            case 4:
                return 'F';
            case 8:
                return 'D';
            default:
                break;
            }
            break;
        case PYOBJECT:
            return 'O';
        }
        throw Py.ValueError("internal error in kindAndNBytesToType");
    }

    /**
     * Set isContiguous to true if the strides indicate that this array is
     * contiguous.
     */
    private final void setIsContiguous() {
        final int[] contiguousStrides = PyMultiarray.shapeToStrides(
                this.dimensions,
                PyMultiarray.typeToNElements(this._typecode));
        for (int i = 0; i < this.strides.length; i++) {
            if (this.strides[i] != contiguousStrides[i]) {
                this.isContiguous = false;
                break;
            }
        }
    }

    /**
     * Return an array derived from <code>a</code> whose axes are suitable for
     * binary operations.
     */
    private final static PyMultiarray stretchAxes(final PyMultiarray a, final PyMultiarray b) {
        if (a.dimensions.length > b.dimensions.length) {
            final int[] dimensions = a.dimensions.clone();
            final int[] strides = a.strides.clone();
            final int excess = a.dimensions.length - b.dimensions.length;
            for (int i = excess; i < dimensions.length; i++) {
                if (dimensions[i] != b.dimensions[i - excess]) {
                    if (dimensions[i] == 1) {
                        dimensions[i] = b.dimensions[i - excess];
                        strides[i] = 0;
                    }
                    else if (b.dimensions[i - excess] != 1) { throw Py
                            .ValueError("matrices not aligned"); }
                }
            }
            return new PyMultiarray(
                    a.data,
                    a._typecode,
                    a.start,
                    dimensions,
                    strides);
        }
        else {
            final int[] dimensions = b.dimensions.clone();
            final int[] strides = new int[dimensions.length];
            final int excess = b.dimensions.length - a.dimensions.length;
            for (int i = excess; i < dimensions.length; i++) {
                if (dimensions[i] != a.dimensions[i - excess]) {
                    if (dimensions[i] == 1) {
                        dimensions[i] = a.dimensions[i - excess];
                    } else if (a.dimensions[i - excess] == 1) {
                        continue; // Don't set strides.
                    } else {
                        throw Py.ValueError("matrices not aligned");
                    }
                }
                strides[i] = a.strides[i - excess];
            }
            return new PyMultiarray(
                    a.data,
                    a._typecode,
                    a.start,
                    dimensions,
                    strides);
        }
    }

    /** Return an array with shape [1] for use when multiplying shape [] arrays. */
    private final static PyMultiarray stretchAxes(final PyMultiarray a) {
        final int[] dimensions = new int[] { 1 };
        final int[] strides = new int[] { 1 };
        return new PyMultiarray(
                a.data,
                a._typecode,
                a.start,
                dimensions,
                strides);
    }

    /** Return an array for storing the result of a binary operation on a and b. */
    private final static PyMultiarray getResultArray(final PyMultiarray a, final PyMultiarray b, char type) {
        if (type == '\0') {
            type = a._typecode;
        }
        final int[] dimensions = new int[a.dimensions.length];
        for (int i = 0; i < a.dimensions.length; i++) {
            dimensions[i] = Math.max(a.dimensions[i], b.dimensions[i]);
        }
        return PyMultiarray.zeros(dimensions, type);
    }

    /**
     * Check that <code>result</code> is consistent with <code>a</code> and
     * <code>b</code>.
     */
    private final static void checkResultArray(final PyMultiarray result, final PyMultiarray a, final PyMultiarray b) {
        if (result._typecode != a._typecode) { throw Py
                .ValueError("return array has incorrect type."); }
        if (result.dimensions.length != a.dimensions.length) { throw Py
                .ValueError("return array has the wrong number of dimensions"); }
        for (int i = 0; i < result.dimensions.length; i++) {
            if (result.dimensions[i] != Math.max(
                    a.dimensions[i],
                    b.dimensions[i])) { throw Py
                    .ValueError("return array has incorrect dimensions"); }
        }
    }

    //
    // Python special methods.
    //

    /** Convert <code>this</code> to a java object of Class <code>c</code>. */
    @Override public Object __tojava__(final Class c) {
        final Class type = PyMultiarray.typeToClass(this._typecode);
        if (this.dimensions.length == 0 || this._typecode == 'F'
                || this._typecode == 'D') { return super.__tojava__(c); // Punt!
        }
        if (c == Object.class
                || (c.isArray() && c.getComponentType().isAssignableFrom(type))) {
            final Object jarray = Array.newInstance(type, this.dimensions);
            final PyMultiarray contiguous = PyMultiarray.ascontiguous(this);
            if (this.dimensions.length == 1) {
                for (int i = 0; i < contiguous.__len__(); i++) {
                    Array.set(
                            jarray,
                            i,
                            Array.get(contiguous.data, contiguous.start + i));
                }
                return jarray;
            }
            else {
                for (int i = 0; i < contiguous.__len__(); i++) {
                    final Object subData = this.get(i).__tojava__(c);
                    Array.set(jarray, i, subData);
                }
                return jarray;
            }

        }
        return super.__tojava__(c);
    }

    /** Return the length of the array (along the first axis). */
    @Override public int __len__() {
        if (this.dimensions.length == 0) { throw Py
                .ValueError("__len__ of zero dimensional array"); }
        return this.dimensions[0];
    }

    /** Overide PyObject method so that get is invoked instead of __finditem__. */
    @Override public PyObject __getitem__(final int index) {
        return this.get(index);
    }

    /** Disable comparison of Multiarrays/ */
    @Override public int __cmp__(final PyObject other) {
        final boolean debug_this = false;
        if (!(other instanceof PyMultiarray)) { return -2; }
        final PyMultiarray o = (PyMultiarray) other;
        if (debug_this) {
            System.out.println("__cmp__() passed '"
                    + other.getClass().getName()
                    + "' object with typecode '" + o.typecode() + "': " + o);
            System.out.println("Comparing with this: '"
                    + this.getClass().getName() + "' " + this);
        }
        throw Py.TypeError("Comparison of multiarray objects is not implemented.");
    }

    /** Return the subarray or item indicated by indices. */
    @Override public PyObject __getitem__(final PyObject indices) {
        return PyMultiarray.returnValue(this.indicesToStructure(indices));
    }

    /** Set the subarray based on indices to PyValue. */
    @Override public void __setitem__(final PyObject indices, final PyObject pyValue) {
        // Get the shape of the subarray to set.
        final PyMultiarray toStructure = this.indicesToStructure(indices);
        // Convert value to array.
        final PyMultiarray value = PyMultiarray.array(pyValue, this._typecode);
        // Check that array shapes are consistent and get new shape for source
        // array.
        final int[] shape = toStructure.dimensions.clone();
        final int[] strides = new int[toStructure.dimensions.length];
        final int excess = toStructure.dimensions.length
                - value.dimensions.length;
        if (toStructure.dimensions.length < value.dimensions.length) { throw Py
                .ValueError("object too deep for desired array"); }
        for (int i = 0; i < excess; i++) {
            shape[i] = toStructure.dimensions[i];
        }
        for (int i = excess; i < toStructure.dimensions.length; i++) {
            shape[i] = value.dimensions[i - excess];
            strides[i] = value.strides[i - excess];
        }
        value.dimensions = shape;
        value.strides = strides;
        value.setIsContiguous();
        PyMultiarray.copyAToB(value, toStructure);
    }

    // XXX Optimize!!
    /**
     * Set a slice of a PyObject.
     * Changes elements from <code>start</code> up to,
     * but not including, <code>stop</code>.
     */
    @Override protected void setslice(final int start, final int stop, final int step, final PyObject value) {
        final PyObject startObject = (start >= 0) ? Py.newInteger(start)
                : Py.None;
        final PyObject stopObject = (stop >= 0) ? Py.newInteger(stop) : Py.None;
        this.__setitem__(
                new PySlice(startObject, stopObject, Py.newInteger(step)),
                value);
    }

    // XXX Optimize!!
    /**
     * Return a slice of a PyObject.
     * Gets elements from <code>start</code> up to,
     * but not including, <code>stop</code>
     */
    @Override protected PyObject getslice(final int start, final int stop, final int step) {
        final PyObject startObject = (start >= 0) ? Py.newInteger(start)
                : Py.None;
        final PyObject stopObject = (stop >= 0) ? Py.newInteger(stop) : Py.None;
        return this.__getitem__(new PySlice(startObject, stopObject, Py
                .newInteger(step)));
    }

    /** Return the repr for this Multiarray. */
    @Override public PyString __repr__() {
        return Py.newString(PyMultiarrayPrinter.array2string(
                this,
                PyMultiarray.maxLineWidth,
                PyMultiarray.precision,
                PyMultiarray.suppressSmall,
                ", ",
                true));
    }

    /** Return the str for this Multiarray. */
    @Override public PyString __str__() {
        return Py.newString(PyMultiarrayPrinter.array2string(
                this,
                PyMultiarray.maxLineWidth,
                PyMultiarray.precision,
                PyMultiarray.suppressSmall,
                " ",
                false));
    }

    /** Multiarray attributes are found using <code>__findattr__</code>. */
    @Override public PyObject __findattr_ex__(final String name) {
        // if (name == "__class__") return __class__;
        if (name == "__doc__") { return Py.newString(this.docString); }
        if (name == "shape") { return PyTuple.fromIterable(new PyArray(
                int.class,
                this.dimensions)); // return __builtin__.tuple(new
                                   // PyArray(int.class, dimensions));
        }
        if (name == "real") { return this.getReal(); }
        if (name == "imag" || name == "imaginary") { return this.getImag(); }
        if (name == "flat" && this.isContiguous) { return PyMultiarray.reshape(
                this,
                new int[] { -1 }); }
        if (name == "T" && this.dimensions.length == 2) { return PyMultiarray
                .transpose(this, new int[] { 1, 0 }); }
        return super.__findattr_ex__(name);
    }

    /** Multiarray attributes are set using <code>__setattr__</code>. */
    @Override public void __setattr__(final String name, final PyObject value)
            throws PyException {
        if (name == "shape") {
            if (!this.isContiguous) { throw Py
                    .ValueError("reshape only works on contiguous matrices"); }
            final int[] shape = (int[]) value.__tojava__(this.dimensions
                    .getClass());
            this.dimensions = PyMultiarray.fixedShape(
                    shape,
                    PyMultiarray.shapeToNItems(this.dimensions));
            this.strides = PyMultiarray.shapeToStrides(
                    shape,
                    PyMultiarray.typeToNElements(this._typecode));
            return;
        }
        if (name == "imag" || name == "imaginary") {
            final PyMultiarray imag = this.getImag();
            if (imag != null) {
                imag.__setitem__(new PySlice(null, null, null), value);
                return;
            }
        }
        if (name == "real") {
            this.getReal().__setitem__(new PySlice(null, null, null), value);
            return;
        }
        super.__setattr__(name, value);
    }

    // The numeric special methods are defined later because they tend to be
    // obnoxiously long...

    //
    // Sequence special methods.
    //

    protected PyObject get(final int i) {
        if (this.dimensions.length < 1) { throw Py
                .IndexError("too few dimensions"); }
        final int newStart = this.start + this.fixIndex(i, 0) * this.strides[0];
        final int[] newDimensions = new int[this.dimensions.length - 1];
        final int[] newStrides = new int[this.dimensions.length - 1];
        for (int j = 0; j < this.dimensions.length - 1; j++) {
            newDimensions[j] = this.dimensions[j + 1];
            newStrides[j] = this.strides[j + 1];
        }
        return PyMultiarray.returnValue(new PyMultiarray(
                this.data,
                this._typecode,
                newStart,
                newDimensions,
                newStrides));
    }

    @Override protected PyObject pyget(final int i) {
        return this.get(i);
    }

    @Override protected PyObject repeat(final int count) {
        throw Py.TypeError("can't apply '*' to arrays");
    }

    @Override protected void del(final int i) {
        throw Py.TypeError("can't remove from array");
    }

    protected void delRange(final int start, final int stop, final int step) {
        throw Py.TypeError("can't remove from array");
    }

    protected void set(final int i, final PyObject pyValue) {
        if (this.dimensions.length < 1) { throw Py
                .IndexError("too few dimensions"); }
        final int newStart = this.start + this.fixIndex(i, 0) * this.strides[0];
        final int[] newDimensions = new int[this.dimensions.length - 1];
        final int[] newStrides = new int[this.dimensions.length - 1];
        for (int j = 0; j < this.dimensions.length - 1; j++) {
            newDimensions[j] = this.dimensions[j + 1];
            newStrides[j] = this.strides[j + 1];
        }
        PyMultiarray.copyAToB(
                PyMultiarray.asarray(pyValue, this._typecode),
                new PyMultiarray(
                        this.data,
                        this._typecode,
                        newStart,
                        newDimensions,
                        newStrides));
    }

    /**
     * Convert negative indices to positive and throw exception if index out of
     * range.
     */
    protected int fixIndex(int index, final int axis) {
        if (index < 0) {
            index += this.dimensions[axis];
        }
        if (index < 0 || index >= this.dimensions[axis]) {
            throw Py.IndexError("index out of range");
        } else {
            return index;
        }
    }

    /** pulled out of PySequence */
    private static final int getIndex(final PyObject index, final int defaultValue) {
        if (index == Py.None || index == null) { return defaultValue; }
        if (!(index instanceof PyInteger)) { throw Py
                .TypeError("slice index must be int"); }
        return ((PyInteger) index).getValue();
    }

    /** from jython 2.2.1 PySequence */
    protected static final int getStep1(final PyObject s_step) {
        final int step = PyMultiarray.getIndex(s_step, 1);
        if (step == 0) { throw Py.TypeError("slice step of zero not allowed"); }
        return step;
    }

    /* Should go in PySequence */
    protected static final int getStart1(final PyObject s_start, final int step, final int length)
    {
        int start;
        if (step < 0) {
            start = PyMultiarray.getIndex(s_start, length - 1);
            if (start < -1) {
                start = length + start;
            }
            if (start < -1) {
                start = -1;
            }
            if (start > length - 1) {
                start = length - 1;
            }
        } else {
            start = PyMultiarray.getIndex(s_start, 0);
            if (start < 0) {
                start = length + start;
            }
            if (start < 0) {
                start = 0;
            }
            if (start > length) {
                start = length;
            }
        }
        return start;
    }

    protected static final int getStop1(final PyObject s_stop, final int start, final int step, final int length)
    {
        int stop;
        if (step < 0) {
            stop = PyMultiarray.getIndex(s_stop, -1);
            if (stop < -1) {
                stop = length + stop;
            }
            if (stop < -1) {
                stop = -1;
            }
            if (stop > length - 1) {
                stop = length - 1;
            }
        } else {
            stop = PyMultiarray.getIndex(s_stop, length);
            if (stop < 0) {
                stop = length + stop;
            }
            if (stop < 0) {
                stop = 0;
            }
            if (stop > length) {
                stop = length;
            }
        }
        if ((stop - start) * step < 0) {
            stop = start;
        }
        return stop;
    }

    /**
     * Convert a set of indices into a Multiarray object, but do not fill in the
     * data.
     */
    private final PyMultiarray indicesToStructure(final PyObject pyIndices) {
        // Convert the pyIndices into an array of PyObjects.
        // RGA PyObject indices[] = (pyIndices instanceof PyTuple) ?
        // ((PyTuple)pyIndices).list : new PyObject[] {pyIndices};
        final PyObject indices[] = (pyIndices instanceof PyTuple) ? ((PyTuple) pyIndices)
                .getArray() : new PyObject[] { pyIndices };
        // First pass: determine the size of the new dimensions.
        int nDimensions = this.dimensions.length, ellipsisLength = 0, axis = 0;
        for (int i = 0; i < indices.length; i++) {
            final PyObject index = indices[i];
            if (index instanceof PyEllipsis) {
                if (ellipsisLength > 0) {
                    continue;
                }
                ellipsisLength = this.dimensions.length
                        - (indices.length - i - 1 + axis);
                for (int j = i + 1; j < indices.length; j++) {
                    if (indices[j] instanceof PyNone) {
                        ellipsisLength++;
                    }
                }
                if (ellipsisLength < 0) { throw Py
                        .IndexError("too many indices"); }
                axis += ellipsisLength;
            }
            else if (index instanceof PyNone) {
                nDimensions++;
            } else if (index instanceof PyInteger) {
                nDimensions--;
                axis++;
            }
            else if (index instanceof PySlice) {
                axis++;
            } else {
                throw Py.ValueError("invalid index");
            }
        }
        if (axis > this.dimensions.length) { throw Py
                .ValueError("invalid index"); }
        // Second pass: now generate the dimensions.
        int newStart = this.start, newAxis = 0, oldAxis = 0;
        final int[] newDimensions = new int[nDimensions], newStrides = new int[nDimensions];
        for (final PyObject index : indices) {
            if (index instanceof PyEllipsis) {
                if (ellipsisLength > 0) {
                    for (int j = 0; j < ellipsisLength; j++) {
                        newDimensions[newAxis + j] = this.dimensions[oldAxis
                                + j];
                        newStrides[newAxis + j] = this.strides[oldAxis + j];
                    }
                    oldAxis += ellipsisLength;
                    newAxis += ellipsisLength;
                    ellipsisLength = 0;
                }
            }
            else if (index instanceof PyNone) {
                newDimensions[newAxis] = 1;
                newStrides[newAxis] = 0;
                newAxis++;
            }
            else if (oldAxis >= this.dimensions.length) {
                throw Py.IndexError("too many dimensions");
            } else if (index instanceof PyInteger) {
                final PyInteger integer = (PyInteger) index;
                newStart += this.fixIndex(integer.getValue(), oldAxis)
                        * this.strides[oldAxis];
                oldAxis++;
            }
            else if (index instanceof PySlice) {
                final PySlice slice = (PySlice) index;
                final int sliceStep = PyMultiarray.getStep1(slice.step);
                final int sliceStart = PyMultiarray.getStart1(
                        slice.start,
                        sliceStep,
                        this.dimensions[oldAxis]);
                final int sliceStop = PyMultiarray.getStop1(
                        slice.stop,
                        sliceStart,
                        sliceStep,
                        this.dimensions[oldAxis]);
                if (sliceStep > 0) {
                    newDimensions[newAxis] = 1 + (sliceStop - sliceStart - 1)
                            / sliceStep;
                } else {
                    newDimensions[newAxis] = 1 - (sliceStart - sliceStop - 1)
                            / sliceStep;
                }
                newStart += sliceStart * this.strides[oldAxis];
                newStrides[newAxis] = sliceStep * this.strides[oldAxis];
                oldAxis++;
                newAxis++;
            } else {
                throw Py.ValueError("illegal index");
            }
        }
        // Tack any extra indices onto the end.
        for (int i = 0; i < nDimensions - newAxis; i++) {
            newDimensions[newAxis + i] = this.dimensions[oldAxis + i];
            newStrides[newAxis + i] = this.strides[oldAxis + i];
        }
        return new PyMultiarray(
                this.data,
                this._typecode,
                newStart,
                newDimensions,
                newStrides);
    }

    /**
     * Return <code>a</code> unless it is zero dimensional, in which case
     * convert to a PyObject and return.
     */
    static final PyObject returnValue(final PyMultiarray a) {
        if (a.dimensions.length == 0) {
            if (PyMultiarray.typeToNElements(a._typecode) == 1) {
                return Py.java2py(Array.get(a.data, a.start));
            } else {
                return new PyComplex(
                        ((Number) Array.get(a.data, a.start)).doubleValue(),
                        ((Number) Array.get(a.data, a.start + 1)).doubleValue());
            }
        }
        return a;
    }

    /**
     * Copy the array A to the array B. The arrays must be the same shape.
     * (Length 1 axes can be converted to length N axes if the appropriate
     * stride is set to zero.)
     */
    static void copyAToB(final PyMultiarray a, final PyMultiarray b) {
        if (a.dimensions.length != b.dimensions.length) { throw Py
                .ValueError("copied matrices must have the same number of dimensions"); }
        for (int i = 0; i < a.dimensions.length; i++) {
            if (a.dimensions[i] != b.dimensions[i]) { throw Py
                    .ValueError("matrices not aligned for copy"); }
        }
        if (a._typecode == b._typecode && a.isContiguous && b.isContiguous) {
            System.arraycopy(
                    a.data,
                    a.start,
                    b.data,
                    b.start,
                    PyMultiarray.typeToNElements(a._typecode)
                            * PyMultiarray.shapeToNItems(a.dimensions));
        } else if (a.dimensions.length == 0) {
            b.__setitem__(Py.Ellipsis, a.__getitem__(Py.Ellipsis));
        } else if (a._typecode == b._typecode && a._typecode != 'F'
                && a._typecode != 'D') {
            PyMultiarray.copyAxToBx(
                    a.data,
                    a.start,
                    a.strides,
                    b.data,
                    b.start,
                    b.strides,
                    b.dimensions,
                    0);
        } else if (a._typecode == 'O') {
            PyMultiarray.copyAOToB(
                    a.data,
                    a.start,
                    a.strides,
                    b.data,
                    b.start,
                    b.strides,
                    b._typecode,
                    b.dimensions,
                    0);
        } else {
            switch (b._typecode) {
            case '1':
                PyMultiarray.copyAToBb(
                        a.data,
                        a.start,
                        a.strides,
                        b.data,
                        b.start,
                        b.strides,
                        b.dimensions,
                        0);
                break;
            case 's':
                PyMultiarray.copyAToBs(
                        a.data,
                        a.start,
                        a.strides,
                        b.data,
                        b.start,
                        b.strides,
                        b.dimensions,
                        0);
                break;
            case 'i':
                PyMultiarray.copyAToBi(
                        a.data,
                        a.start,
                        a.strides,
                        b.data,
                        b.start,
                        b.strides,
                        b.dimensions,
                        0);
                break;
            case 'l':
                PyMultiarray.copyAToBl(
                        a.data,
                        a.start,
                        a.strides,
                        b.data,
                        b.start,
                        b.strides,
                        b.dimensions,
                        0);
                break;
            case 'f':
                PyMultiarray.copyAToBf(
                        a.data,
                        a.start,
                        a.strides,
                        b.data,
                        b.start,
                        b.strides,
                        b.dimensions,
                        0);
                break;
            case 'd':
                PyMultiarray.copyAToBd(
                        a.data,
                        a.start,
                        a.strides,
                        b.data,
                        b.start,
                        b.strides,
                        b.dimensions,
                        0);
                break;
            case 'F':
                if (a._typecode == 'F' || a._typecode == 'D') {
                    PyMultiarray.copyAToBF(
                            a.data,
                            a.start,
                            a.strides,
                            b.data,
                            b.start,
                            b.strides,
                            b.dimensions,
                            0);
                } else {
                    PyMultiarray.copyAToBf(
                            a.data,
                            a.start,
                            a.strides,
                            b.data,
                            b.start,
                            b.strides,
                            b.dimensions,
                            0);
                }
                break;
            case 'D':
                if (a._typecode == 'F' || a._typecode == 'D') {
                    PyMultiarray.copyAToBD(
                            a.data,
                            a.start,
                            a.strides,
                            b.data,
                            b.start,
                            b.strides,
                            b.dimensions,
                            0);
                } else {
                    PyMultiarray.copyAToBd(
                            a.data,
                            a.start,
                            a.strides,
                            b.data,
                            b.start,
                            b.strides,
                            b.dimensions,
                            0);
                }
                break;
            case 'O':
                PyMultiarray.copyAToBO(
                        a.data,
                        a.start,
                        a.strides,
                        b.data,
                        b.start,
                        b.strides,
                        b.dimensions,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
    }

    static void copyAxToBx(final Object aData, final int aStart, final int[] aStrides,
            final Object bData, final int bStart, final int[] bStrides, final int[] dimensions, final int depth) {
        final int jMax = bStart + dimensions[depth] * bStrides[depth];
        if (depth < dimensions.length - 1) {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                PyMultiarray.copyAxToBx(
                        aData,
                        i,
                        aStrides,
                        bData,
                        j,
                        bStrides,
                        dimensions,
                        depth + 1);
            }
        } else {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                Array.set(bData, j, Array.get(aData, i));
            }
        }
    }

    static void copyAOToB(final Object aData, final int aStart, final int[] aStrides,
            final Object bData, final int bStart, final int[] bStrides, final char bType, final int[] dimensions, final int depth) {
        final int jMax = bStart + dimensions[depth] * bStrides[depth];
        if (depth < dimensions.length - 1) {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                PyMultiarray.copyAOToB(
                        aData,
                        i,
                        aStrides,
                        bData,
                        j,
                        bStrides,
                        bType,
                        dimensions,
                        depth + 1);
            }
        } else {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                Array.set(bData, j, ((PyObject) Array.get(aData, i))
                        .__tojava__(PyMultiarray.typeToClass(bType)));
            }
        }
    }

    static void copyAToBb(final Object aData, final int aStart, final int[] aStrides,
            final Object bData, final int bStart, final int[] bStrides, final int[] dimensions, final int depth) {
        final int jMax = bStart + dimensions[depth] * bStrides[depth];
        if (depth < dimensions.length - 1) {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                PyMultiarray.copyAToBb(
                        aData,
                        i,
                        aStrides,
                        bData,
                        j,
                        bStrides,
                        dimensions,
                        depth + 1);
            }
        } else {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                Array.setByte(
                        bData,
                        j,
                        ((Number) Array.get(aData, i)).byteValue());
            }
        }
    }

    static void copyAToBs(final Object aData, final int aStart, final int[] aStrides,
            final Object bData, final int bStart, final int[] bStrides, final int[] dimensions, final int depth) {
        final int jMax = bStart + dimensions[depth] * bStrides[depth];
        if (depth < dimensions.length - 1) {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                PyMultiarray.copyAToBs(
                        aData,
                        i,
                        aStrides,
                        bData,
                        j,
                        bStrides,
                        dimensions,
                        depth + 1);
            }
        } else {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                Array.setShort(
                        bData,
                        j,
                        ((Number) Array.get(aData, i)).shortValue());
            }
        }
    }

    static void copyAToBi(final Object aData, final int aStart, final int[] aStrides,
            final Object bData, final int bStart, final int[] bStrides, final int[] dimensions, final int depth) {
        final int jMax = bStart + dimensions[depth] * bStrides[depth];
        if (depth < dimensions.length - 1) {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                PyMultiarray.copyAToBi(
                        aData,
                        i,
                        aStrides,
                        bData,
                        j,
                        bStrides,
                        dimensions,
                        depth + 1);
            }
        } else {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                Array.setInt(
                        bData,
                        j,
                        ((Number) Array.get(aData, i)).intValue());
            }
        }
    }

    static void copyAToBl(final Object aData, final int aStart, final int[] aStrides,
            final Object bData, final int bStart, final int[] bStrides, final int[] dimensions, final int depth) {
        final int jMax = bStart + dimensions[depth] * bStrides[depth];
        if (depth < dimensions.length - 1) {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                PyMultiarray.copyAToBl(
                        aData,
                        i,
                        aStrides,
                        bData,
                        j,
                        bStrides,
                        dimensions,
                        depth + 1);
            }
        } else {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                Array.setLong(
                        bData,
                        j,
                        ((Number) Array.get(aData, i)).longValue());
            }
        }
    }

    static void copyAToBf(final Object aData, final int aStart, final int[] aStrides,
            final Object bData, final int bStart, final int[] bStrides, final int[] dimensions, final int depth) {
        final int jMax = bStart + dimensions[depth] * bStrides[depth];
        if (depth < dimensions.length - 1) {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                PyMultiarray.copyAToBf(
                        aData,
                        i,
                        aStrides,
                        bData,
                        j,
                        bStrides,
                        dimensions,
                        depth + 1);
            }
        } else {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                Array.setFloat(
                        bData,
                        j,
                        ((Number) Array.get(aData, i)).floatValue());
            }
        }
    }

    static void copyAToBd(final Object aData, final int aStart, final int[] aStrides,
            final Object bData, final int bStart, final int[] bStrides, final int[] dimensions, final int depth) {
        final int jMax = bStart + dimensions[depth] * bStrides[depth];
        if (depth < dimensions.length - 1) {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                PyMultiarray.copyAToBd(
                        aData,
                        i,
                        aStrides,
                        bData,
                        j,
                        bStrides,
                        dimensions,
                        depth + 1);
            }
        } else {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                Array.setDouble(
                        bData,
                        j,
                        ((Number) Array.get(aData, i)).doubleValue());
            }
        }
    }

    static void copyAToBF(final Object aData, final int aStart, final int[] aStrides,
            final Object bData, final int bStart, final int[] bStrides, final int[] dimensions, final int depth) {
        final int jMax = bStart + dimensions[depth] * bStrides[depth];
        if (depth < dimensions.length - 1) {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                PyMultiarray.copyAToBF(
                        aData,
                        i,
                        aStrides,
                        bData,
                        j,
                        bStrides,
                        dimensions,
                        depth + 1);
            }
        } else {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                Array.setFloat(
                        bData,
                        j,
                        ((Number) Array.get(aData, i)).floatValue());
                Array.setFloat(
                        bData,
                        j + 1,
                        ((Number) Array.get(aData, i + 1)).floatValue());
            }
        }
    }

    static void copyAToBD(final Object aData, final int aStart, final int[] aStrides,
            final Object bData, final int bStart, final int[] bStrides, final int[] dimensions, final int depth) {
        final int jMax = bStart + dimensions[depth] * bStrides[depth];
        if (depth < dimensions.length - 1) {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                PyMultiarray.copyAToBD(
                        aData,
                        i,
                        aStrides,
                        bData,
                        j,
                        bStrides,
                        dimensions,
                        depth + 1);
            }
        } else {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                Array.setDouble(
                        bData,
                        j,
                        ((Number) Array.get(aData, i)).doubleValue());
                Array.setDouble(
                        bData,
                        j + 1,
                        ((Number) Array.get(aData, i + 1)).doubleValue());
            }
        }
    }

    static void copyAToBO(final Object aData, final int aStart, final int[] aStrides,
            final Object bData, final int bStart, final int[] bStrides, final int[] dimensions, final int depth) {
        final int jMax = bStart + dimensions[depth] * bStrides[depth];
        if (depth < dimensions.length - 1) {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                PyMultiarray.copyAToBO(
                        aData,
                        i,
                        aStrides,
                        bData,
                        j,
                        bStrides,
                        dimensions,
                        depth + 1);
            }
        } else {
            for (int i = aStart, j = bStart; j != jMax; i += aStrides[depth], j += bStrides[depth]) {
                Array.set(bData, j, Py.java2py(Array.get(aData, i)));
            }
        }
    }

    /** This converts data (which must be an array) to an array of bytes. */
    static byte[] toByteArray(final Object data, final char type) {
        try {
            final java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
            final java.io.DataOutputStream dataStream = new java.io.DataOutputStream(
                    byteStream);
            switch (type) {
            case '1':
                for (int i = 0; i < Array.getLength(data); i++) {
                    dataStream.writeByte(Array.getByte(data, i));
                }
                break;
            case 's':
                for (int i = 0; i < Array.getLength(data); i++) {
                    dataStream.writeShort(Array.getShort(data, i));
                }
                break;
            case 'i':
                for (int i = 0; i < Array.getLength(data); i++) {
                    dataStream.writeInt(Array.getInt(data, i));
                }
                break;
            case 'l':
                for (int i = 0; i < Array.getLength(data); i++) {
                    dataStream.writeLong(Array.getLong(data, i));
                }
                break;
            case 'f':
            case 'F':
                for (int i = 0; i < Array.getLength(data); i++) {
                    dataStream.writeFloat(Array.getFloat(data, i));
                }
                break;
            case 'd':
            case 'D':
                for (int i = 0; i < Array.getLength(data); i++) {
                    dataStream.writeDouble(Array.getDouble(data, i));
                }
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
            return byteStream.toByteArray();
        } catch (final java.io.IOException e) {
            throw Py.RuntimeError("ioexception:" + e.getMessage());
        }
    }

    static Object fromByteArray(final byte[] bytes, final char type) {
        if (bytes.length % PyMultiarray.typeToNBytes(type) != 0) { throw Py
                .ValueError("array size must be a multiple of the type size"); }
        try {
            final java.io.ByteArrayInputStream byteStream = new java.io.ByteArrayInputStream(
                    bytes);
            final java.io.DataInputStream dataStream = new java.io.DataInputStream(
                    byteStream);
            final Object data = Array.newInstance(
                    PyMultiarray.typeToClass(type),
                    bytes.length / PyMultiarray.typeToNBytes(type));
            switch (type) {
            case '1':
                for (int i = 0; i < Array.getLength(data); i++) {
                    Array.setByte(data, i, dataStream.readByte());
                }
                break;
            case 's':
                for (int i = 0; i < Array.getLength(data); i++) {
                    Array.setShort(data, i, dataStream.readShort());
                }
                break;
            case 'i':
                for (int i = 0; i < Array.getLength(data); i++) {
                    Array.setInt(data, i, dataStream.readInt());
                }
                break;
            case 'l':
                for (int i = 0; i < Array.getLength(data); i++) {
                    Array.setLong(data, i, dataStream.readLong());
                }
                break;
            case 'f':
            case 'F':
                for (int i = 0; i < Array.getLength(data); i++) {
                    Array.setFloat(data, i, dataStream.readFloat());
                }
                break;
            case 'd':
            case 'D':
                for (int i = 0; i < Array.getLength(data); i++) {
                    Array.setDouble(data, i, dataStream.readDouble());
                }
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
            return data;
        } catch (final java.io.IOException e) {
            throw Py.RuntimeError("ioexception:" + e.getMessage());
        }
    }

    /**
     * Return a string representation of the byte array corresponding to this
     * array
     */
    @Override public String toString() {
        final StringBuffer buf = new StringBuffer("arrayObject(data=[");
        final int dataLength = Array.getLength(this.data);
        for (int i = 0; i < dataLength - 1; i++) {
            buf.append((Array.get(this.data, i)).toString());
            buf.append(", ");
        }
        if (dataLength > 0) {
            buf.append((Array.get(this.data, dataLength - 1)).toString());
        }
        buf.append("], typecode=");
        buf.append(this._typecode);
        buf.append(", start=");
        buf.append((new Integer(this.start)).toString());
        buf.append(", dimensions=[");
        for (int i = 0; i < this.dimensions.length - 1; i++) {
            buf.append((new Integer(this.dimensions[i])).toString());
            buf.append(",");
        }
        if (this.dimensions.length > 0) {
            buf.append((new Integer(this.dimensions[this.dimensions.length - 1]))
                    .toString());
        }
        buf.append("], strides=[");
        for (int i = 0; i < this.strides.length - 1; i++) {
            buf.append((new Integer(this.strides[i])).toString());
            buf.append(",");
        }
        if (this.strides.length > 0) {
            buf.append((new Integer(this.strides[this.strides.length - 1]))
                    .toString());
        }
        buf.append("])");
        return buf.toString();
    }

    //
    // Functions used by __add__, etc that are NOT generated.
    //

    @Override public PyObject __radd__(final PyObject po) {
        return this.__add__(po);
    }

    @Override public PyObject __rsub__(final PyObject po) {
        return PyMultiarray.array(po).__sub__(this);
    }

    @Override public PyObject __rmul__(final PyObject po) {
        return this.__mul__(po);
    }

    @Override public PyObject __rdiv__(final PyObject po) {
        return PyMultiarray.array(po).__div__(this);
    }

    @Override public PyObject __rmod__(final PyObject po) {
        return PyMultiarray.array(po).__mod__(this);
    }

    @Override public PyObject __divmod__(final PyObject po) {
        final PyObject mod = this.__mod__(po);
        final PyObject div = this.__sub__(mod).__div__(po);
        return new PyTuple(new PyObject[] { div, mod });
    }

    @Override public PyObject __rdivmod__(final PyObject po) {
        return PyMultiarray.array(po).__divmod__(this);
    }

    final static void mulComplexFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                final float re = aData[sa] * bData[sb] - aData[sa + 1]
                        * bData[sb + 1];
                rData[sr + 1] = aData[sa] * bData[sb + 1] + aData[sa + 1]
                        * bData[sb];
                rData[sr] = re;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.mulComplexFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void mulComplexDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                final double re = aData[sa] * bData[sb] - aData[sa + 1]
                        * bData[sb + 1];
                rData[sr + 1] = aData[sa] * bData[sb + 1] + aData[sa + 1]
                        * bData[sb];
                rData[sr] = re;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.mulComplexDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void divComplexFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                final float den = bData[sb] * bData[sb] + bData[sb + 1]
                        * bData[sb + 1];
                final float re = (aData[sa] * bData[sb] + aData[sa + 1]
                        * bData[sb + 1])
                        / den;
                rData[sr + 1] = (-aData[sa] * bData[sb + 1] + aData[sa + 1]
                        * bData[sb])
                        / den;
                rData[sr] = re;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.divComplexFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void divComplexDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                final double den = bData[sb] * bData[sb] + bData[sb + 1]
                        * bData[sb + 1];
                final double re = (aData[sa] * bData[sb] + aData[sa + 1]
                        * bData[sb + 1])
                        / den;
                rData[sr + 1] = (-aData[sa] * bData[sb + 1] + aData[sa + 1]
                        * bData[sb])
                        / den;
                rData[sr] = re;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.divComplexDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void addComplexFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = aData[sa] + bData[sb];
                rData[sr + 1] = aData[sa + 1] + bData[sb + 1];
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.addComplexFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void addComplexDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = aData[sa] + bData[sb];
                rData[sr + 1] = aData[sa + 1] + bData[sb + 1];
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.addComplexDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void subComplexFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = aData[sa] - bData[sb];
                rData[sr + 1] = aData[sa + 1] - bData[sb + 1];
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.subComplexFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void subComplexDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = aData[sa] - bData[sb];
                rData[sr + 1] = aData[sa + 1] - bData[sb + 1];
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.subComplexDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void modComplexFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                final float reA = aData[sa], imA = aData[sa + 1], reB = bData[sb], imB = bData[sb + 1];
                final float den = reB * reB + imB * imB;
                final float n = (float) Math.floor((reA * reB + imA * imB)
                        / den);
                rData[sr] = reA - n * reB;
                rData[sr + 1] = imA - n * imB;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.modComplexFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void modComplexDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                final double reA = aData[sa], imA = aData[sa + 1], reB = bData[sb], imB = bData[sb + 1];
                final double den = reB * reB + imB * imB;
                final double n = Math.floor((reA * reB + imA * imB) / den);
                rData[sr] = reA - n * reB;
                rData[sr + 1] = imA - n * imB;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.modComplexDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void powComplexFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                final PyComplex Z = (PyComplex) new PyComplex(
                        aData[sa],
                        aData[sa + 1]).__pow__(new PyComplex(
                        bData[sb],
                        bData[sb + 1]));
                rData[sr] = (float) Z.real;
                rData[sr + 1] = (float) Z.imag;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.powComplexFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void powComplexDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                final PyComplex Z = (PyComplex) new PyComplex(
                        aData[sa],
                        aData[sa + 1]).__pow__(new PyComplex(
                        bData[sb],
                        bData[sb + 1]));
                rData[sr] = Z.real;
                rData[sr + 1] = Z.imag;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.powComplexDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void maxComplexFloat(final int sa, final PyMultiarray a, final int sb, final PyMultiarray b, final int sr, final PyMultiarray r, final int d) {
        throw Py.ValueError("cannot perform ordered compare on complex numbers");
    }

    final static void maxComplexDouble(final int sa, final PyMultiarray a, final int sb, final PyMultiarray b, final int sr, final PyMultiarray r, final int d) {
        throw Py.ValueError("cannot perform ordered compare on complex numbers");
    }

    final static void minComplexFloat(final int sa, final PyMultiarray a, final int sb, final PyMultiarray b, final int sr, final PyMultiarray r, final int d) {
        throw Py.ValueError("cannot perform ordered compare on complex numbers");
    }

    final static void minComplexDouble(final int sa, final PyMultiarray a, final int sb, final PyMultiarray b, final int sr, final PyMultiarray r, final int d) {
        throw Py.ValueError("cannot perform ordered compare on complex numbers");
    }

    final static void eqComplexFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sa] = (aData[sa] == bData[sb] && aData[sa + 1] == bData[sb + 1]) ? 1
                        : 0;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.eqComplexFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void eqComplexDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sa] = (aData[sa] == bData[sb] && aData[sa + 1] == bData[sb + 1]) ? 1
                        : 0;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.eqComplexDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void neqComplexFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sa] = (aData[sa] == bData[sb] && aData[sa + 1] == bData[sb + 1]) ? 0
                        : 1;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.neqComplexFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void neqComplexDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sa] = (aData[sa] == bData[sb] && aData[sa + 1] == bData[sb + 1]) ? 0
                        : 1;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.neqComplexDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void landComplexFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sa] = ((aData[sa] != 0 || aData[sa + 1] != 0) && (bData[sb] != 0 || bData[sb + 1] != 0)) ? 1
                        : 0;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.landComplexFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void landComplexDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sa] = ((aData[sa] != 0 || aData[sa + 1] != 0) && (bData[sb] != 0 || bData[sb + 1] != 0)) ? 1
                        : 0;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.landComplexDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void lorComplexFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sa] = ((aData[sa] != 0 || aData[sa + 1] != 0) || (bData[sb] != 0 || bData[sb + 1] != 0)) ? 1
                        : 0;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lorComplexFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    final static void lorComplexDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], finalSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sa] = ((aData[sa] != 0 || aData[sa + 1] != 0) || (bData[sb] != 0 || bData[sb + 1] != 0)) ? 1
                        : 0;
            }
        } else {
            for (; sr != finalSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lorComplexDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lxorComplexFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sa] = ((aData[sa] != 0 || aData[sa + 1] != 0) ^ (bData[sb] != 0 || bData[sb + 1] != 0)) ? 1
                        : 0;
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lxorComplexFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lxorComplexDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sa] = ((aData[sa] != 0 || aData[sa + 1] != 0) ^ (bData[sb] != 0 || bData[sb + 1] != 0)) ? 1
                        : 0;
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lxorComplexDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    PyMultiarray absComplexFloat(final PyMultiarray a) {
        // Assumes a continuous array.
        final int length = PyMultiarray.shapeToNItems(a.dimensions);
        final float[] aData = (float[]) a.data;
        final float[] result = new float[length];
        for (int i = a.start, j = 0; j < length; i += 2, j++) {
            result[j] = (float) Math.sqrt(aData[i] * aData[i] + aData[i + 1]
                    * aData[i + 1]);
        }
        return new PyMultiarray(
                result,
                'f',
                0,
                new int[] { length },
                new int[] { 1 });
    }

    PyMultiarray absComplexDouble(final PyMultiarray a) {
        // Assumes a continuous array.
        final int length = PyMultiarray.shapeToNItems(a.dimensions);
        final double[] aData = (double[]) a.data;
        final double[] result = new double[length];
        for (int i = a.start, j = 0; j < length; i += 2, j++) {
            result[j] = Math.sqrt(aData[i] * aData[i] + aData[i + 1]
                    * aData[i + 1]);
        }
        return new PyMultiarray(
                result,
                'd',
                0,
                new int[] { length },
                new int[] { 1 });
    }

    PyMultiarray negComplexFloat(final PyMultiarray a) {
        final float[] aData = (float[]) a.data;
        for (int i = 0; i < aData.length; i++) {
            aData[i] = -aData[i];
        }
        return a;
    }

    PyMultiarray negComplexDouble(final PyMultiarray a) {
        final double[] aData = (double[]) a.data;
        for (int i = 0; i < aData.length; i++) {
            aData[i] = -aData[i];
        }
        return a;
    }

    // These pow routines adopted from CNumeric.

    static final long pow(final long x, final long n) {
        /* Overflow check: overflow will occur if log2(abs(x)) * n > nbits. */
        final int nbits = 63;
        long r = 1;
        long p = x;
        double logtwox;
        long mask = 1;
        if (n < 0) { throw Py.ValueError("Integer to a negative power"); }
        if (x != 0) {
            logtwox = Math.log(Math.abs((double) x)) / Math.log(2.0);
            if (logtwox * n > nbits) { throw new PyException(
                    Py.ArithmeticError,
                    "Integer overflow in power."); }
        }
        while (mask > 0 && n >= mask) {
            if ((n & mask) != 0) {
                r *= p;
            }
            mask <<= 1;
            p *= p;
        }
        return r;
    }

    static final int pow(final int x, final int n) {
        /* Overflow check: overflow will occur if log2(abs(x)) * n > nbits. */
        final int nbits = 31;
        long r = 1;
        long p = x;
        double logtwox;
        long mask = 1;
        if (n < 0) { throw Py.ValueError("Integer to a negative power"); }
        if (x != 0) {
            logtwox = Math.log(Math.abs((double) x)) / Math.log(2.0);
            if (logtwox * n > nbits) { throw new PyException(
                    Py.ArithmeticError,
                    "Integer overflow in power."); }
        }
        while (mask > 0 && n >= mask) {
            if ((n & mask) != 0) {
                r *= p;
            }
            mask <<= 1;
            p *= p;
        }
        return (int) r;
    }

    static final short pow(final short x, final short n) {
        /* Overflow check: overflow will occur if log2(abs(x)) * n > nbits. */
        final int nbits = 15;
        long r = 1;
        long p = x;
        double logtwox;
        long mask = 1;
        if (n < 0) { throw Py.ValueError("Integer to a negative power"); }
        if (x != 0) {
            logtwox = Math.log(Math.abs((double) x)) / Math.log(2.0);
            if (logtwox * n > nbits) { throw new PyException(
                    Py.ArithmeticError,
                    "Integer overflow in power."); }
        }
        while (mask > 0 && n >= mask) {
            if ((n & mask) != 0) {
                r *= p;
            }
            mask <<= 1;
            p *= p;
        }
        return (short) r;
    }

    static final byte pow(final byte x, final byte n) {
        /* Overflow check: overflow will occur if log2(abs(x)) * n > nbits. */
        final int nbits = 7;
        long r = 1;
        long p = x;
        double logtwox;
        long mask = 1;
        if (n < 0) { throw Py.ValueError("Integer to a negative power"); }
        if (x != 0) {
            logtwox = Math.log(Math.abs((double) x)) / Math.log(2.0);
            if (logtwox * n > nbits) { throw new PyException(
                    Py.ArithmeticError,
                    "Integer overflow in power."); }
        }
        while (mask > 0 && n >= mask) {
            if ((n & mask) != 0) {
                r *= p;
            }
            mask <<= 1;
            p *= p;
        }
        return (byte) r;
    }

    static final double pow(final double x, final double n) {
        return Math.pow(x, n);
    }

    static final float pow(final float x, final float n) {
        return (float) Math.pow(x, n);
    }

    /*
     * Generated code.
     */

    // Begin generated code (genOps).

    private final static void addByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final byte[] rData = (byte[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (byte) (aData[sa] + bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.addByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void addShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final short[] rData = (short[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (short) (aData[sa] + bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.addShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void addInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] + bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.addInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void addLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final long[] rData = (long[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] + bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.addLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void addFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] + bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.addFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void addDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] + bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.addDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void addObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final PyObject[] rData = (PyObject[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa]._add(bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.addObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    @Override public PyObject __add__(final PyObject o) {
        return this.__add__(o, null);
    }

    PyObject __add__(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, '\0');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(
                    Py.Ellipsis,
                    a.__add__(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.addByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.addShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.addInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.addLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.addFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.addDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.addObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.addComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.addComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void subByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final byte[] rData = (byte[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (byte) (aData[sa] - bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.subByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void subShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final short[] rData = (short[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (short) (aData[sa] - bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.subShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void subInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] - bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.subInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void subLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final long[] rData = (long[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] - bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.subLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void subFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] - bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.subFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void subDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] - bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.subDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void subObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final PyObject[] rData = (PyObject[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa]._sub(bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.subObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    @Override public PyObject __sub__(final PyObject o) {
        return this.__sub__(o, null);
    }

    PyObject __sub__(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, '\0');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(
                    Py.Ellipsis,
                    a.__sub__(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.subByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.subShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.subInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.subLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.subFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.subDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.subObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.subComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.subComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void mulByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final byte[] rData = (byte[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (byte) (aData[sa] * bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.mulByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void mulShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final short[] rData = (short[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (short) (aData[sa] * bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.mulShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void mulInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] * bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.mulInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void mulLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final long[] rData = (long[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] * bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.mulLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void mulFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] * bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.mulFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void mulDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] * bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.mulDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void mulObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final PyObject[] rData = (PyObject[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa]._mul(bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.mulObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    @Override public PyObject __mul__(final PyObject o) {
        return this.__mul__(o, null);
    }

    PyObject __mul__(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, '\0');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(
                    Py.Ellipsis,
                    a.__mul__(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.mulByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.mulShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.mulInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.mulLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.mulFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.mulDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.mulObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.mulComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.mulComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void divByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final byte[] rData = (byte[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (byte) (aData[sa] / bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.divByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void divShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final short[] rData = (short[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (short) (aData[sa] / bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.divShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void divInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] / bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.divInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void divLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final long[] rData = (long[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] / bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.divLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void divFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] / bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.divFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void divDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] / bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.divDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void divObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final PyObject[] rData = (PyObject[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa]._div(bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.divObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    @Override public PyObject __div__(final PyObject o) {
        return this.__div__(o, null);
    }

    PyObject __div__(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = this.astype(type);
        b = b.astype(type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, '\0');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        try {
            if (result.dimensions.length == 0) {
                result.__setitem__(
                        Py.Ellipsis,
                        a.__div__(PyMultiarray.stretchAxes(b)).__getitem__(0));
            } else {
                switch (type) {
                case '1':
                    PyMultiarray.divByte(
                            a.start,
                            a,
                            b.start,
                            b,
                            result.start,
                            result,
                            0);
                    break;
                case 's':
                    PyMultiarray.divShort(
                            a.start,
                            a,
                            b.start,
                            b,
                            result.start,
                            result,
                            0);
                    break;
                case 'i':
                    PyMultiarray.divInt(
                            a.start,
                            a,
                            b.start,
                            b,
                            result.start,
                            result,
                            0);
                    break;
                case 'l':
                    PyMultiarray.divLong(
                            a.start,
                            a,
                            b.start,
                            b,
                            result.start,
                            result,
                            0);
                    break;
                case 'f':
                    PyMultiarray.divFloat(
                            a.start,
                            a,
                            b.start,
                            b,
                            result.start,
                            result,
                            0);
                    break;
                case 'd':
                    PyMultiarray.divDouble(
                            a.start,
                            a,
                            b.start,
                            b,
                            result.start,
                            result,
                            0);
                    break;
                case 'O':
                    PyMultiarray.divObject(
                            a.start,
                            a,
                            b.start,
                            b,
                            result.start,
                            result,
                            0);
                    break;
                case 'F':
                    PyMultiarray.divComplexFloat(
                            a.start,
                            a,
                            b.start,
                            b,
                            result.start,
                            result,
                            0);
                    break;
                case 'D':
                    PyMultiarray.divComplexDouble(
                            a.start,
                            a,
                            b.start,
                            b,
                            result.start,
                            result,
                            0);
                    break;
                default:
                    throw Py.ValueError("typecode must be in [1silfFdDO]");
                }
            }
        } catch (final java.lang.ArithmeticException ex) {
            if (ex.getMessage().equals("/ by zero")) { throw Py
                    .ZeroDivisionError("divide by zero"); }
            throw ex;
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void modByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final byte[] rData = (byte[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (byte) (aData[sa] % bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.modByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void modShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final short[] rData = (short[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (short) (aData[sa] % bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.modShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void modInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] % bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.modInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void modLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final long[] rData = (long[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] % bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.modLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void modFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] % bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.modFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void modDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] % bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.modDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void modObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final PyObject[] rData = (PyObject[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa]._mod(bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.modObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    @Override public PyObject __mod__(final PyObject o) {
        return this.__mod__(o, null);
    }

    PyObject __mod__(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, '\0');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(
                    Py.Ellipsis,
                    a.__mod__(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.modByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.modShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.modInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.modLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.modFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.modDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.modObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.modComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.modComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void powByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final byte[] rData = (byte[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (PyMultiarray.pow(aData[sa], bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.powByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void powShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final short[] rData = (short[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (PyMultiarray.pow(aData[sa], bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.powShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void powInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (PyMultiarray.pow(aData[sa], bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.powInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void powLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final long[] rData = (long[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (PyMultiarray.pow(aData[sa], bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.powLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void powFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (PyMultiarray.pow(aData[sa], bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.powFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void powDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (PyMultiarray.pow(aData[sa], bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.powDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void powObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final PyObject[] rData = (PyObject[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa]._pow(bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.powObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    @Override public PyObject __pow__(final PyObject o) {
        return this.__pow__(o, null);
    }

    PyObject __pow__(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, '\0');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(
                    Py.Ellipsis,
                    a.__pow__(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.powByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.powShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.powInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.powLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.powFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.powDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.powObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.powComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.powComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void maxByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final byte[] rData = (byte[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.maxByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void maxShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final short[] rData = (short[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.maxShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void maxInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.maxInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void maxLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final long[] rData = (long[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.maxLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void maxFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.maxFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void maxDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.maxDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public static PyObject myMax(final PyObject a, final PyObject b) {
        return b._gt(a).__nonzero__() ? b : a;
    }

    public static PyObject myMin(final PyObject a, final PyObject b) {
        return b._lt(a).__nonzero__() ? b : a;
    }

    private final static void maxObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final PyObject[] rData = (PyObject[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr)
            {
                rData[sr] = PyMultiarray.myMax(aData[sa], bData[sb]); // (PyObject)(__builtin__.max(new
                                                                      // PyObject
                                                                      // []
                                                                      // {aData[sa],
                                                                      // bData[sb]}));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.maxObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public PyObject __max(final PyObject o) {
        return this.__max(o, null);
    }

    PyObject __max(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, '\0');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(Py.Ellipsis, a
                    .__max(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.maxByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.maxShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.maxInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.maxLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.maxFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.maxDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.maxObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.maxComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.maxComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void minByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final byte[] rData = (byte[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.minByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void minShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final short[] rData = (short[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.minShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void minInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.minInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void minLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final long[] rData = (long[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.minLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void minFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final float[] rData = (float[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.minFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void minDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final double[] rData = (double[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? aData[sa] : bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.minDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void minObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final PyObject[] rData = (PyObject[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr)
            {
                rData[sr] = PyMultiarray.myMin(aData[sa], bData[sb]); // (PyObject)(__builtin__.min(new
                                                                      // PyObject
                                                                      // []
                                                                      // {aData[sa],
                                                                      // bData[sb]}));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.minObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public PyObject __min(final PyObject o) {
        return this.__min(o, null);
    }

    PyObject __min(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, '\0');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(Py.Ellipsis, a
                    .__min(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.minByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.minShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.minInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.minLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.minFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.minDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.minObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.minComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.minComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void eqByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] == bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.eqByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void eqShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] == bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.eqShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void eqInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] == bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.eqInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void eqLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] == bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.eqLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void eqFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] == bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.eqFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void eqDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] == bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.eqDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void eqObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((__builtin__.cmp(aData[sa], bData[sb]) == 0) ? 1
                        : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.eqObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public PyObject __eq(final PyObject o) {
        return this.__eq(o, null);
    }

    PyObject __eq(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, 'i');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(Py.Ellipsis, a.__eq(PyMultiarray.stretchAxes(b))
                    .__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.eqByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.eqShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.eqInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.eqLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.eqFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.eqDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.eqObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.eqComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.eqComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void neqByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] != bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.neqByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void neqShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] != bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.neqShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void neqInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] != bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.neqInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void neqLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] != bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.neqLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void neqFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] != bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.neqFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void neqDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] != bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.neqDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void neqObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((__builtin__.cmp(aData[sa], bData[sb]) != 0) ? 1
                        : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.neqObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public PyObject __neq(final PyObject o) {
        return this.__neq(o, null);
    }

    PyObject __neq(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, 'i');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(Py.Ellipsis, a
                    .__neq(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.neqByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.neqShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.neqInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.neqLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.neqFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.neqDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.neqObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.neqComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.neqComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void leByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] <= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.leByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void leShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] <= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.leShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void leInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] <= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.leInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void leLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] <= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.leLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void leFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] <= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.leFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void leDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] <= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.leDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void leObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((__builtin__.cmp(aData[sa], bData[sb]) <= 0) ? 1
                        : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.leObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public PyObject __le(final PyObject o) {
        return this.__le(o, null);
    }

    PyObject __le(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, 'i');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(Py.Ellipsis, a.__le(PyMultiarray.stretchAxes(b))
                    .__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.leByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.leShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.leInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.leLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.leFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.leDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.leObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                throw Py.ValueError("le not supported for type ComplexFloat");
            case 'D':
                throw Py.ValueError("le not supported for type ComplexDouble");
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void ltByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.ltByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void ltShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.ltShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void ltInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.ltInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void ltLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.ltLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void ltFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.ltFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void ltDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] < bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.ltDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void ltObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((__builtin__.cmp(aData[sa], bData[sb]) < 0) ? 1
                        : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.ltObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public PyObject __lt(final PyObject o) {
        return this.__lt(o, null);
    }

    PyObject __lt(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, 'i');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(Py.Ellipsis, a.__lt(PyMultiarray.stretchAxes(b))
                    .__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.ltByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.ltShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.ltInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.ltLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.ltFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.ltDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.ltObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                throw Py.ValueError("lt not supported for type ComplexFloat");
            case 'D':
                throw Py.ValueError("lt not supported for type ComplexDouble");
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void geByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] >= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.geByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void geShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] >= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.geShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void geInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] >= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.geInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void geLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] >= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.geLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void geFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] >= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.geFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void geDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] >= bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.geDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void geObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((__builtin__.cmp(aData[sa], bData[sb]) >= 0) ? 1
                        : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.geObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public PyObject __ge(final PyObject o) {
        return this.__ge(o, null);
    }

    PyObject __ge(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, 'i');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(Py.Ellipsis, a.__ge(PyMultiarray.stretchAxes(b))
                    .__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.geByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.geShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.geInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.geLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.geFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.geDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.geObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                throw Py.ValueError("ge not supported for type ComplexFloat");
            case 'D':
                throw Py.ValueError("ge not supported for type ComplexDouble");
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void gtByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.gtByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void gtShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.gtShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void gtInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.gtInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void gtLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.gtLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void gtFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.gtFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void gtDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa] > bData[sb]) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.gtDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void gtObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((__builtin__.cmp(aData[sa], bData[sb]) > 0) ? 1
                        : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.gtObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public PyObject __gt(final PyObject o) {
        return this.__gt(o, null);
    }

    PyObject __gt(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, 'i');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(Py.Ellipsis, a.__gt(PyMultiarray.stretchAxes(b))
                    .__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.gtByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.gtShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.gtInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.gtLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.gtFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.gtDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.gtObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                throw Py.ValueError("gt not supported for type ComplexFloat");
            case 'D':
                throw Py.ValueError("gt not supported for type ComplexDouble");
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void landByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) & (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.landByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void landShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) & (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.landShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void landInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) & (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.landInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void landLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) & (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.landLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void landFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) & (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.landFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void landDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) & (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.landDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void landObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa].__nonzero__() & bData[sb].__nonzero__()) ? 1
                        : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.landObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public PyObject __land(final PyObject o) {
        return this.__land(o, null);
    }

    PyObject __land(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, 'i');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(
                    Py.Ellipsis,
                    a.__land(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.landByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.landShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.landInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.landLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.landFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.landDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.landObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.landComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.landComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void lorByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) | (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lorByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lorShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) | (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lorShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lorInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) | (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lorInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lorLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) | (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lorLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lorFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) | (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lorFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lorDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) | (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lorDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lorObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa].__nonzero__() | bData[sb].__nonzero__()) ? 1
                        : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lorObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public PyObject __lor(final PyObject o) {
        return this.__lor(o, null);
    }

    PyObject __lor(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, 'i');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(Py.Ellipsis, a
                    .__lor(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.lorByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.lorShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.lorInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.lorLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.lorFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.lorDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.lorObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.lorComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.lorComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void lxorByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) ^ (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lxorByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lxorShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) ^ (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lxorShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lxorInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) ^ (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lxorInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lxorLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) ^ (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lxorLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lxorFloat(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final float[] aData = (float[]) a.data, bData = (float[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) ^ (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lxorFloat(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lxorDouble(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final double[] aData = (double[]) a.data, bData = (double[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (((aData[sa] != 0) ^ (bData[sb] != 0)) ? 1 : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lxorDouble(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void lxorObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = ((aData[sa].__nonzero__() ^ bData[sb].__nonzero__()) ? 1
                        : 0);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.lxorObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    public PyObject __lxor(final PyObject o) {
        return this.__lxor(o, null);
    }

    PyObject __lxor(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, 'i');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(
                    Py.Ellipsis,
                    a.__lxor(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.lxorByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.lxorShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.lxorInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.lxorLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                PyMultiarray.lxorFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'd':
                PyMultiarray.lxorDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'O':
                PyMultiarray.lxorObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                PyMultiarray.lxorComplexFloat(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'D':
                PyMultiarray.lxorComplexDouble(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void andByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final byte[] rData = (byte[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (byte) (aData[sa] & bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.andByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void andShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final short[] rData = (short[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (short) (aData[sa] & bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.andShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void andInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] & bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.andInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void andLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final long[] rData = (long[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] & bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.andLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void andObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final PyObject[] rData = (PyObject[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa].__and__(bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.andObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    @Override public PyObject __and__(final PyObject o) {
        return this.__and__(o, null);
    }

    PyObject __and__(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, '\0');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(
                    Py.Ellipsis,
                    a.__and__(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.andByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.andShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.andInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.andLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                throw Py.ValueError("and not supported for type Float");
            case 'd':
                throw Py.ValueError("and not supported for type Double");
            case 'O':
                PyMultiarray.andObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                throw Py.ValueError("and not supported for type ComplexFloat");
            case 'D':
                throw Py.ValueError("and not supported for type ComplexDouble");
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void orByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final byte[] rData = (byte[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (byte) (aData[sa] | bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.orByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void orShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final short[] rData = (short[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (short) (aData[sa] | bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.orShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void orInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] | bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.orInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void orLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final long[] rData = (long[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] | bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.orLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void orObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final PyObject[] rData = (PyObject[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa].__or__(bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.orObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    @Override public PyObject __or__(final PyObject o) {
        return this.__or__(o, null);
    }

    PyObject __or__(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, '\0');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(
                    Py.Ellipsis,
                    a.__or__(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.orByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.orShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.orInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.orLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                throw Py.ValueError("or not supported for type Float");
            case 'd':
                throw Py.ValueError("or not supported for type Double");
            case 'O':
                PyMultiarray.orObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                throw Py.ValueError("or not supported for type ComplexFloat");
            case 'D':
                throw Py.ValueError("or not supported for type ComplexDouble");
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    private final static void xorByte(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final byte[] aData = (byte[]) a.data, bData = (byte[]) b.data;
            final byte[] rData = (byte[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (byte) (aData[sa] ^ bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.xorByte(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void xorShort(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final short[] aData = (short[]) a.data, bData = (short[]) b.data;
            final short[] rData = (short[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (short) (aData[sa] ^ bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.xorShort(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void xorInt(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final int[] aData = (int[]) a.data, bData = (int[]) b.data;
            final int[] rData = (int[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] ^ bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.xorInt(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void xorLong(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final long[] aData = (long[]) a.data, bData = (long[]) b.data;
            final long[] rData = (long[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa] ^ bData[sb]);
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.xorLong(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    private final static void xorObject(int sa, final PyMultiarray a, int sb, final PyMultiarray b, int sr, final PyMultiarray r, final int d) {
        final int dsa = a.strides[d], dsb = b.strides[d], dsr = r.strides[d], maxSr = sr
                + r.dimensions[d] * r.strides[d];
        if (d == r.dimensions.length - 1) {
            final PyObject[] aData = (PyObject[]) a.data, bData = (PyObject[]) b.data;
            final PyObject[] rData = (PyObject[]) r.data;
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                rData[sr] = (aData[sa].__xor__(bData[sb]));
            }
        } else {
            for (; sr != maxSr; sa += dsa, sb += dsb, sr += dsr) {
                PyMultiarray.xorObject(sa, a, sb, b, sr, r, d + 1);
            }
        }
    }

    @Override public PyObject __xor__(final PyObject o) {
        return this.__xor__(o, null);
    }

    PyObject __xor__(final PyObject o, PyMultiarray result) {
        PyMultiarray a, b = PyMultiarray.asarray(o);
        final char type = PyMultiarray.commonType(this._typecode, b._typecode);
        a = PyMultiarray.asarray(this, type);
        b = PyMultiarray.asarray(b, type);
        a = PyMultiarray.stretchAxes(a, b);
        b = PyMultiarray.stretchAxes(b, this);
        if (result == null) {
            result = PyMultiarray.getResultArray(a, b, '\0');
        } else {
            PyMultiarray.checkResultArray(result, a, b);
        }
        if (result.dimensions.length == 0) {
            result.__setitem__(
                    Py.Ellipsis,
                    a.__xor__(PyMultiarray.stretchAxes(b)).__getitem__(0));
        } else {
            switch (type) {
            case '1':
                PyMultiarray.xorByte(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 's':
                PyMultiarray.xorShort(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'i':
                PyMultiarray.xorInt(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'l':
                PyMultiarray.xorLong(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'f':
                throw Py.ValueError("xor not supported for type Float");
            case 'd':
                throw Py.ValueError("xor not supported for type Double");
            case 'O':
                PyMultiarray.xorObject(
                        a.start,
                        a,
                        b.start,
                        b,
                        result.start,
                        result,
                        0);
                break;
            case 'F':
                throw Py.ValueError("xor not supported for type ComplexFloat");
            case 'D':
                throw Py.ValueError("xor not supported for type ComplexDouble");
            default:
                throw Py.ValueError("typecode must be in [1silfFdDO]");
            }
        }
        return PyMultiarray.returnValue(result);
    }

    @Override public boolean isNumberType() throws PyIgnoreMethodTag {
        return true;
    }  // so abs() will work

    @Override public PyObject __abs__() {
        return PyMultiarray.returnValue(this.__abs__(PyMultiarray.array(this)));
    }

    PyMultiarray __abs__(PyMultiarray a) {
        if (!a.isContiguous) { throw Py
                .ValueError("internal __abs__ requires contiguous matrix as argument"); }
        final int maxI = a.start + PyMultiarray.shapeToNItems(a.dimensions);
        switch (a._typecode) {
        case '1':
            final byte aData1[] = (byte[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aData1[i] = (byte) ((aData1[i] > 0) ? aData1[i] : -aData1[i]);
            }
            break;
        case 's':
            final short aDatas[] = (short[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDatas[i] = (short) ((aDatas[i] > 0) ? aDatas[i] : -aDatas[i]);
            }
            break;
        case 'i':
            final int aDatai[] = (int[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDatai[i] = ((aDatai[i] > 0) ? aDatai[i] : -aDatai[i]);
            }
            break;
        case 'l':
            final long aDatal[] = (long[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDatal[i] = ((aDatal[i] > 0) ? aDatal[i] : -aDatal[i]);
            }
            break;
        case 'f':
            final float aDataf[] = (float[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDataf[i] = ((aDataf[i] > 0) ? aDataf[i] : -aDataf[i]);
            }
            break;
        case 'd':
            final double aDatad[] = (double[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDatad[i] = ((aDatad[i] > 0) ? aDatad[i] : -aDatad[i]);
            }
            break;
        case 'O':
            final PyObject aDataO[] = (PyObject[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDataO[i] = (aDataO[i].__abs__());
            }
            break;
        case 'F':
            a = this.absComplexFloat(a);
            break;
        case 'D':
            a = this.absComplexDouble(a);
            break;
        default:
            throw Py.ValueError("typecode must be in [1silfFdDO]");
        }
        return a;
    }

    @Override public PyObject __neg__() {
        return PyMultiarray.returnValue(this.__neg__(PyMultiarray.array(this)));
    }

    PyMultiarray __neg__(PyMultiarray a) {
        if (!a.isContiguous) { throw Py
                .ValueError("internal __neg__ requires contiguous matrix as argument"); }
        final int maxI = a.start + PyMultiarray.shapeToNItems(a.dimensions);
        switch (a._typecode) {
        case '1':
            final byte aData1[] = (byte[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aData1[i] = (byte) (-aData1[i]);
            }
            break;
        case 's':
            final short aDatas[] = (short[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDatas[i] = (short) (-aDatas[i]);
            }
            break;
        case 'i':
            final int aDatai[] = (int[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDatai[i] = (-aDatai[i]);
            }
            break;
        case 'l':
            final long aDatal[] = (long[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDatal[i] = (-aDatal[i]);
            }
            break;
        case 'f':
            final float aDataf[] = (float[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDataf[i] = (-aDataf[i]);
            }
            break;
        case 'd':
            final double aDatad[] = (double[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDatad[i] = (-aDatad[i]);
            }
            break;
        case 'O':
            final PyObject aDataO[] = (PyObject[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDataO[i] = (aDataO[i].__neg__());
            }
            break;
        case 'F':
            a = this.negComplexFloat(a);
            break;
        case 'D':
            a = this.negComplexDouble(a);
            break;
        default:
            throw Py.ValueError("typecode must be in [1silfFdDO]");
        }
        return a;
    }

    @Override public PyObject __not__() {
        return PyMultiarray.returnValue(this.__not__(PyMultiarray.array(this)));
    }

    PyMultiarray __not__(final PyMultiarray a) {
        if (!a.isContiguous) { throw Py
                .ValueError("internal __not__ requires contiguous matrix as argument"); }
        final int maxI = a.start + PyMultiarray.shapeToNItems(a.dimensions);
        switch (a._typecode) {
        case '1':
            final byte aData1[] = (byte[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aData1[i] = (byte) (~aData1[i]);
            }
            break;
        case 's':
            final short aDatas[] = (short[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDatas[i] = (short) (~aDatas[i]);
            }
            break;
        case 'i':
            final int aDatai[] = (int[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDatai[i] = (~aDatai[i]);
            }
            break;
        case 'l':
            final long aDatal[] = (long[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDatal[i] = (~aDatal[i]);
            }
            break;
        case 'f':
            throw Py.ValueError("not not supported for type Float");
        case 'd':
            throw Py.ValueError("not not supported for type Double");
        case 'O':
            final PyObject aDataO[] = (PyObject[]) a.data;
            for (int i = 0; i < Array.getLength(a.data); i++) {
                aDataO[i] = (aDataO[i].__not__());
            }
            break;
        case 'F':
            throw Py.ValueError("not not supported for type ComplexFloat");
        case 'D':
            throw Py.ValueError("not not supported for type ComplexDouble");
        default:
            throw Py.ValueError("typecode must be in [1silfFdDO]");
        }
        return a;
    }

    // End generated code (genOps).

}
