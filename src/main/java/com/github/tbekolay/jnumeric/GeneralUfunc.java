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

import org.python.core.PyObject;
import org.python.core.PyType;

// TODO migrate various pieces of PyMultiarray to here.
/**
 * Class to encapsulate PyMultiarray logic. Currently unused.
 */
public class GeneralUfunc extends PyObject {
    private static final long serialVersionUID = 2988200647889866329L;

    /**
     * Simple constructor, no special logic.
     */
    public GeneralUfunc() {
        super(PyType.fromClass(GeneralUfunc.class));
        this.javaProxy = this;
    }
}
