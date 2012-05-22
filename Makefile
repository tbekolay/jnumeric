# JNumeric makefile
# http://jnumerical.sourceforge.net
# Daniel Lemire, Ph.D.
#
# This should really be a Ant build file.
# Sorry about that.
#
# This was setup by Tim but I've improved it a bit.
# You have to modify 
# the next line
#
#JAVAPATH=/usr/java/jdk/bin/
JAVAPATH=
#
#
VERSION = 0.1a3
#
# I have no idea what that should be.
#
INSTALLDIR = ../..
#
# Rest should work fine!
#
BASIC_SOURCES = BinaryUfunc.java JN_FFT.java Umath.java \
	JNumeric.java PyMultiarray.java UnaryUfunc.java \
	GeneralUfunc.java KeywordFunction.java PyMultiarrayPrinter.java

SOURCES = FFT.java Numeric.java $(addprefix JNumeric/, $(BASIC_SOURCES))

CP = -classpath ../lib/jython.jar 

JAR_FILE = jnumeric-${VERSION}.jar

#distribution: all
#	tar czf jnumeric-${VERSION}.tgz *.java JNumeric/*.java ../*.txt ../jnumeric-${VERSION}.jar Makefile

all: $(JAR_FILE)

$(JAR_FILE): ${SOURCES} 
	${JAVAPATH}javac  ${CP} -d ../classes ${SOURCES}
	cd ../classes; ${JAVAPATH}jar cf ../release/$(JAR_FILE)  Numeric.class FFT.class  JNumeric

javadoc: ${SOURCES}
	@ if [ ! -e ../javadoc ] ; then \
		mkdir ../javadoc; \
	fi
	javadoc -sourcepath . -d ../javadoc ${SOURCES} ${CP}

source: ${SOURCES}
	zip -9 ../release/jnumeric-${VERSION}-src.zip ${SOURCES}

test: $(SOURCES)
	@ if [ ! -e ../tests ] ; then \
		echo "The test directory does not exist."; \
		echo "Please check it out, and 'make test' again."; \
	fi
	@ ../tests/run_all_tests.py -v ../tests


release: ${SOURCES}
	cd ..; zip -9 -r release/jnumeric-${VERSION}-full.zip src classes *.txt

clean:
	- rm -r -f *~
	- rm -r -f JNumeric/*~
	- rm -r -f ../classes/*
	- rm -r -f ../release/*

distclean: clean
	- rm ../release/$(JAR_FILE)

install: $(JAR_FILE)
	cp $(JAR_FILE) ${INSTALLDIR}/$(JAR_FILE)





