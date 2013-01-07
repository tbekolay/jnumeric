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

import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyType;

// There are faster ways to do much of this, but I'm writing the simplest
// possible FFT.

public class FFT extends PyObject {
    private static final long serialVersionUID = 5458819189675402442L;

    public FFT() {
        super(PyType.fromClass(FFT.class));
        this.javaProxy = this;
    }

    static PyMultiarray _fft(final PyObject o, final boolean inverse) {
        PyMultiarray a = PyMultiarray.asarray(o, 'D');
        if (PyMultiarray.shapeOf(a).length != 1) { throw Py
                .ValueError("FFT only available for 1D arrays"); }
        final int N = a.__len__();
        // Compute log2(N)
        int log2N = 0;
        while ((1 << log2N) < N) {
            ++log2N;
        }
        if ((1 << log2N) != N) { throw Py
                .ValueError("array length is not a power of two"); }
        // 'bit reverse' a.
        final int[] factors = new int[log2N];
        final int[] perms = new int[log2N];
        for (int i = 0; i < log2N; i++) {
            factors[i] = 2;
            perms[i] = log2N - i - 1;
        }
        // Copy the array both to make it continuous and so that we don't
        // overwrite.
        a = PyMultiarray.transpose(PyMultiarray.reshape(a, factors), perms);
        a = PyMultiarray.array(PyMultiarray.reshape(a, new int[] { N }));
        // Grab data out of array and operate on it directly
        final double[] data = (double[]) a.data;
        // Core of FFT algorithm
        final double signedTwoPi = (inverse ? 1 : -1) * 2 * Math.PI;
        final int twoN = 2 * N;
        int step = 4;
        while (step <= twoN) {
            final int halfStep = step / 2;
            final double theta0 = signedTwoPi / step;
            for (int start = 0; start < halfStep; start += 2) {
                final double cosTheta = Math.cos(start * theta0);
                final double sinTheta = Math.sin(start * theta0);
                for (int jR = start; jR < twoN; jR += step) {
                    final int kR = jR + halfStep;
                    final int jI = jR + 1, kI = kR + 1;
                    final double tempR = cosTheta * data[kR] - sinTheta
                            * data[kI];
                    final double tempI = sinTheta * data[kR] + cosTheta
                            * data[kI];
                    data[kR] = data[jR] - tempR;
                    data[kI] = data[jI] - tempI;
                    data[jR] += tempR;
                    data[jI] += tempI;
                }
            }
            step *= 2;
        }
        if (inverse) {
            for (int i = 0; i < twoN; i += 2) {
                data[i] /= N;
                data[i + 1] /= N;
            }
        }
        return a;
    }

    static public PyMultiarray fft(final PyObject o) {
        return FFT._fft(o, false);
    }

    static public PyMultiarray inverse_fft(final PyObject o) {
        return FFT._fft(o, true);
    }
}
