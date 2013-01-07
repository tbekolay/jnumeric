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
import org.python.core.PyString;
import org.python.core.PyType;

public class KeywordFunction extends PyObject {
    /**
     * 
     */
    private static final long serialVersionUID = -1882779412300741219L;

    public KeywordFunction() {
        super(PyType.fromClass(KeywordFunction.class));
        this.javaProxy = this;
    }

    protected String docString = "abstract KeywordFunction";
    protected String[] argNames;
    protected PyObject[] defaultArgs;

    protected PyObject _call(final PyObject args[]) {
        return Py.None;
    }

    @Override public PyObject __call__(final PyObject args[], final String keywords[]) {
        return this._call(this.processArgs(args, keywords));
    }

    protected PyObject[] processArgs(final PyObject args[], final String keywords[]) {
        // Note that that all nulls in defaultArgs must occur at beginning.
        if (args.length > this.argNames.length) { throw Py
                .ValueError("too many arguments"); }
        final PyObject[] allArgs = new PyObject[this.argNames.length];
        final int nPosArgs = args.length - keywords.length;
        for (int i = 0; i < nPosArgs; i++) {
            allArgs[i] = args[i];
        }
        for (int i = 0; i < keywords.length; i++) {
            int j;
            for (j = 0; j < this.argNames.length; j++) {
                if (keywords[i] == this.argNames[j]) {
                    break;
                }
            }
            if (j == this.argNames.length) { throw Py
                    .TypeError("unexpected keyword parameter: " + keywords[i]); }
            if (allArgs[j] != null) { throw Py
                    .TypeError("keyword parameter redefined"); }
            allArgs[j] = args[i + nPosArgs];
        }
        int nNulls = 0;
        for (int i = 0; i < this.defaultArgs.length; i++) {
            if (allArgs[i] == null) {
                if (this.defaultArgs[i] == null) {
                    nNulls++;
                } else {
                    allArgs[i] = this.defaultArgs[i];
                }
            }
        }
        if (nNulls != 0) { throw Py.TypeError("not enough arguments; expected "
                + (nNulls + nPosArgs) + ", got " + nPosArgs); }
        return allArgs;
    }

    @Override public PyObject __findattr_ex__(final String name) {
        if (name == "__doc__") { return new PyString(this.docString); }
        return super.__findattr_ex__(name);
    }
}
