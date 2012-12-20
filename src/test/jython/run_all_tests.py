#!/usr/bin/env jython
# This script finds all Python files in this directory, and runs them all.
# It is intended that each file should contain the tests
# (using the 'unittest' module) for a given JNumeric module.

# Make a list of all the Python files in the current directory.
import os
import string
from string import *
import sys

from os.path import exists, join, abspath
from os import pathsep

def find_file(filename, search_path):
   """Given a search path, find the first file with a given name."""
   file_found = 0
   paths = string.split(search_path, pathsep)
   for path in paths:
      if exists(join(path, filename)):
          file_found = 1
          break
   if file_found:
      return abspath(join(path, filename))
   else:
      return None

this_script = os.path.basename(sys.argv[0])


import getopt
verbose = 0
if verbose: print "Args: ", sys.argv[1:]
try:
    opts, pargs = getopt.getopt(sys.argv[1:], 'v', ['verbose'])
except NameError, e:
    print "Unrecognized option", e
    
for o, a in opts:
    if o in ("-v", "--verbose"):
        verbose = 1


search_dir = os.path.dirname(sys.argv[0])
if verbose:
    print "Searching for test files in '" + search_dir + "'"
    print "Ignoring '" + this_script + "'."

all_files = os.listdir(search_dir)
all_files = filter(lambda x: x.endswith(".py"), all_files)

if all_files == None:
    print "No Python files found in ", search_dir, ". Exiting."
    sys.exit(0)

for file in all_files:
    if file == this_script or not file.endswith('.py'): continue
    module = os.path.splitext(file)[0]
    if verbose: print "Running " + module
    try:
        exec "import " + module
    except Exception, e:
        print "Exception while running " + module + ":"
        print e
