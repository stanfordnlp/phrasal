#!/usr/bin/env python
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Tue Aug  6 13:33:55 PDT 2013

"""
Module docstrings.
"""

__author__ = "Minh-Thang Luong"
__copyright__ = "Copyright \251"
__version__ = "Version 1.0"
usage = '%s %s' % (__copyright__, __author__) 

### Module imports ###
import sys
import argparse # option parsing
import re # regular expression
import os
import codecs

### Global variables ###


### Class declarations ###


### Function declarations ###
def process_command_line():
  """
  Return a 1-tuple: (args list).
  `argv` is a list of arguments, or `None` for ``sys.argv[1:]``.
  """
  
  parser = argparse.ArgumentParser(description=usage) # add description
  # positional arguments
  parser.add_argument('in_file', metavar='in_file', type=str, help='input file') 
  parser.add_argument('out_file', metavar='out_file', type=str, help='output file') 

  parser.add_argument('-o', '--option', dest='opt', type=int, default=0, help='option (default=0)')
  
	# version info
  parser.add_argument('-v', '--version', action='version', version=__version__ )

  # optional arguments
  parser.add_argument('-d', '--debug', dest='debug', action='store_true', default=False, help='enable debugging mode (default: false)') 
  
  args = parser.parse_args()
  #sys.stderr.write("# parsed arguments: %s\n" % str(args))

  return args

def check_dir(out_file):
  dir_name = os.path.dirname(out_file)
  if dir_name != '' and os.path.exists(dir_name) == False:
    sys.stderr.write('! Directory %s doesn\'t exist, creating ...\n' % dir_name)
    os.makedirs(dir_name)

def clean_line(input_line):
  """
  Strip leading and trailing spaces
  """

  input_line = re.sub('(^\s+|\s$)', '', input_line);
  return input_line

def process_files(in_file, out_file):
  """
  Read data from in_file, and output to out_file
  """

  #sys.stderr.write(' # in_file = %s, out_file = %s\n' % (in_file, out_file))
  inf = codecs.open(in_file, 'r', 'utf-8')

  check_dir(out_file)
  distinct_ouf = codecs.open(out_file, 'w', 'utf-8')
  line_id = 0
  sys.stderr.write('  processing file %s ... ' % (in_file))
  sent_map = {}
  for eachline in inf:
    eachline = clean_line(eachline)
    tokens = re.split(' \|\|\| ', eachline)
    if len(tokens)>=2:
      sent_map[tokens[1]] = 1
    else:
      sys.stderr.write('! Line % doesn\'t have a translation\n' % line_id)
      sys.exit(1)

    line_id = line_id + 1
    if (line_id % 10000 == 0):
      sys.stderr.write(' (%d) ' % line_id)

  sys.stderr.write('done! Num lines = %d, num distinct lines = %d\n' % (line_id, len(sent_map)))

  for eachline in sent_map:
    distinct_ouf.write('0 %s\n' % eachline) 

  inf.close()
  distinct_ouf.close()

if __name__ == '__main__':
  args = process_command_line()
  
  if args.debug == True:
    sys.stderr.write('Debug mode\n')

  process_files(args.in_file, args.out_file)
