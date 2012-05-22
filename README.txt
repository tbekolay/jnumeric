WHAT IS JNUMERIC?  
----------------- 

JNumeric provides the functionality for JPython that Numeric does for
CPython. As the Numeric documentation states, Numeric is "...a
collection of extension modules to provide high-performance
multidimensional numeric arrays to the Python programming language."
JNumeric provides the same functionality as the core of Numeric
module and aims to provide akk of the standard extensions to
Numeric modules (FFT, LinearAlgebra, RandomArray).

BUILDING
---------

Go to src and type "make". The jar file should appear in the
release directory.


INSTALLATION (Hassan Siddiqui)
------------------------------

(This was an installation procedure suggested by Hassan.)

Install jython, download jnumeric-XXX.jar, and add
jython-XXX.jar to your $CLASSPATH'.

You can then use JNumeric in jython in exactly the same way as you would use Numerical
Python under Python (but check the DIFFERENCES.txt file).

INSTALLATION (Recent-Linux with Java2 already installed)
-------------------------------------------------------

First, make sure you get recent version from Github:

> fill in

Next, go build it!

> ls
jnumeric

> cd jnumeric
> ls
CVS  jnumeric
> cd jnumeric
> ls
CHANGES.txt  CVS              lib          README.txt  src
classes      DIFFERENCES.txt  LICENSE.txt  release     tests
> cd src
> make
javac  -classpath ../lib/jython.jar  -d ../classes FFT.java Numeric.java
JNumeric/BinaryUfunc.java JNumeric/JN_FFT.java JNumeric/Umath.java
JNumeric/JNumeric.java JNumeric/PyMultiarray.java JNumeric/UnaryUfunc.java
JNumeric/GeneralUfunc.java JNumeric/KeywordFunction.java
JNumeric/PyMultiarrayPrinter.java
cd ../classes; jar cf ../release/jnumeric-0.1a3.jar  Numeric.class FFT.class
JNumeric

Next, install it!

> cd ..
> cd lib
> su
Password:
> which java
/opt/IBMJava2-131/bin/java
> cp jython.jar /opt/IBMJava2-131/jre/lib/ext
> cd ..
> cd release
> ls
CVS  jnumeric-0.1a3.jar
> cp jnumeric-0.1a3.jar /opt/IBMJava2-131/jre/lib/ext
> exit
exit


Next test it!

> java org.python.util.jython
*sys-package-mgr*: processing new jar, '/opt/IBMJava2-131/jre/lib/rt.jar'
*sys-package-mgr*: processing new jar, '/opt/IBMJava2-131/jre/lib/i18n.jar'
*sys-package-mgr*: processing new jar,
'/opt/IBMJava2-131/jre/lib/ext/jnumeric-0.1a3.jar'
*sys-package-mgr*: processing new jar,
'/opt/IBMJava2-131/jre/lib/ext/ibmjcaprovider.jar'
*sys-package-mgr*: processing new jar,
'/opt/IBMJava2-131/jre/lib/ext/indicim.jar'
*sys-package-mgr*: processing new jar,
'/opt/IBMJava2-131/jre/lib/ext/jython.jar'
Jython 2.1 on java1.3.1 (JIT: jitc)
>>> from Numeric import *
>>> a = array(10)
>>> b = array( [[1,0],[0,1]])
>>> print b
[[1 0]
 [0 1]]

If you made it that far, you are ok!

OBTAINING JNUMERIC
------------------
Our home page is found at

http://jnumerical.sourceforge.net


Also, please see the file DIFFERENCES.txt for a list of intentional
differences between JNumeric and CNumeric.

I highly recomend that you also download at JPython1.1b1 or later. 
Earlier versions of JPython have a fairly serious bug in getslice.

WHENCE FROM HERE?
-----------------

In addition to tracking down any bugs, here are a couple of things
that I would like to do (or have done) with JNumeric at some point:
    
	* Add the standard extensions (FFT, LinearAlgebra, and RandomArray).
	* Cleaned up ufunc interface.
	* Other assorted code cleanup.

I would realy love FFT, LinearAlgebra, or RandomArray code -- (if you're
interested in working on LinearAlgebrea let me know I have some preliminary 
ideas for this). 

COPYING
-------

All files in this distribution are Copyright (c) 1998 , 1989 Timothy
Hochberg except where indicated otherwise in the source (includes
work by Daniel Lemire and Frank Gibbons as of July 2002). Do not
redistribute without an unmodified copy of this file. This code
provided in the hope that it will be useful, however it comes with no
warranty of any kind. See LICENCE.txt.

Note: Some recent code might be copyrighted by D. Lemire or D. Bagnell
but given the licensing scheme, copyright disputes are irrelevant.

Enjoy!

____ 
Current maintainer: Trevor Bekolay (tbekolay@gmail.com)
Previous maintainer: Daniel Lemire (lemire@ondelette.com)
Previous previous maintainer and founder: Tim Hochberg
Developers: Frank Gibbons, Drew Bagnell


