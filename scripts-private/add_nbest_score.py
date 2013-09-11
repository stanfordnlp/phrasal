#!/usr/bin/env python
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Tue Aug  6 23:04:23 PDT 2013

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
debug = False

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
  parser.add_argument('score_file', metavar='score_file', type=str, help='nbest score file') 
  parser.add_argument('out_file', metavar='out_file', type=str, help='output file') 

  parser.add_argument('-o', '--option', dest='opt', type=int, default=0, help='option (default=0)')
  # version info
  parser.add_argument('-v', '--version', action='version', version=__version__ )

  # optional arguments
  parser.add_argument('-d', '--debug', dest='debug', action='store_true', default=False, help='enable debugging mode (default: false)') 
  
  args = parser.parse_args()
  sys.stderr.write("# parsed arguments: %s" % str(args))

  return args

def clean_line(input_line):
  """
  Strip leading and trailing spaces
  """

  input_line = re.sub('(^\s+|\s$)', '', input_line);
  return input_line

def process_files(in_file, score_file, out_file):
  """
  Read data from in_file, and output to out_file
  """

  if debug==True:
    sys.stderr.write('# in_file = %s, score_file = %s, out_file = %s\n' % (in_file, score_file, out_file))
  
  inf = codecs.open(in_file, 'r', 'utf-8')
  score_inf = codecs.open(score_file, 'r', 'utf-8')
  ouf = codecs.open(out_file, 'w', 'utf-8')

  line_id = 0
  for eachline in inf:
    eachline = clean_line(eachline)
    tokens = re.split(' \|\|\| ', eachline)
    rnnlm_score = clean_line(score_inf.readline())
    
    if len(tokens)<3:
      break

    id = tokens[0]
    translation = tokens[1]
    if len(tokens)>3:
      decode_score = tokens[3]
    else:
      decode_score = tokens[2]
    ouf.write('%s ||| %s ||| rnnlm: %s DM: %s ||| %s\n' % (id, translation, rnnlm_score, decode_score, decode_score))

    if debug==True:
      line_id = line_id + 1
      if (line_id % 10000 == 0):
        sys.stderr.write(' (%d) ' % line_id)

  if debug==True:
    sys.stderr.write('Done! Num lines = %d\n' % line_id)

  inf.close()
  ouf.close()

if __name__ == '__main__':
  args = process_command_line()
  
  if args.debug == True:
    debug = True

  process_files(args.in_file, args.score_file, args.out_file)
