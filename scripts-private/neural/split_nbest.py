#!/usr/bin/env python2.7
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Mon Mar  3 16:34:12 PST 2014

"""
Module docstrings.
"""

usage = 'USAGE DESCRIPTION.' 

### Module imports ###
import sys
import os
import argparse # option parsing
import re # regular expression
import codecs
#sys.path.append(os.environ['HOME'] + '/lib/') # add our own libraries

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
  parser.add_argument('num_sents', metavar='num_sents', type=int, help='num translation sents for the first part (tune).') 

  # optional arguments
  parser.add_argument('-o', '--option', dest='opt', type=int, default=0, help='option (default=0)')
  parser.add_argument('-d', '--debug', dest='debug', action='store_true', default=False, help='enable debugging mode (default: false)') 

  args = parser.parse_args()
  return args

def check_dir(num_sents):
  dir_name = os.path.dirname(num_sents)

  if dir_name != '' and os.path.exists(dir_name) == False:
    sys.stderr.write('! Directory %s doesn\'t exist, creating ...\n' % dir_name)
    os.makedirs(dir_name)

def clean_line(line):
  """
  Strip leading and trailing spaces
  """

  line = re.sub('(^\s+|\s$)', '', line);
  return line

def process_files(in_file, num_sents):
  """
  Read data from in_file, and output to num_sents
  """

  sys.stderr.write('# in_file = %s, num_sents = %s\n' % (in_file, num_sents))

  # input
  sys.stderr.write('# Input form %s.\n' % (in_file))
  inf = codecs.open(in_file, 'r', 'utf-8')
  
  # output
  tune_out_file = in_file + '.tune'
  sys.stderr.write('Output to %s \n' % tune_out_file)
  tune_ouf = codecs.open(tune_out_file, 'w', 'utf-8')
  test_out_file = in_file + '.test'
  sys.stderr.write('Output to %s \n' % test_out_file)
  test_ouf = codecs.open(test_out_file, 'w', 'utf-8')
 
  line_id = 0
  sys.stderr.write('# Processing file %s ...\n' % (in_file))
  for line in inf:
    line = clean_line(line)
    if line == '':
      continue
    tokens = re.split(' \|\|\| ', line)
    id = int(tokens[0])

    if id<num_sents:
      tune_ouf.write('%s\n' % line)
    else:
      test_ouf.write('%s\n' % line)

    line_id = line_id + 1
    if (line_id % 10000 == 0):
      sys.stderr.write(' (%d) ' % line_id)

  sys.stderr.write('Done! Num lines = %d\n' % line_id)

  inf.close()
  tune_ouf.close()
  test_ouf.close()

if __name__ == '__main__':
  args = process_command_line()
  process_files(args.in_file, args.num_sents)
