#!/usr/bin/env python2.7
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
  parser.add_argument('distinct_nbest_file', metavar='distinct_nbest_file', type=str, help='nbest file containing distinct sentences') 
  parser.add_argument('score_file', metavar='score_file', type=str, help='nbest score file with the same number of lines as distinct_nbest_file') 
  parser.add_argument('out_file', metavar='out_file', type=str, help='output file') 

  parser.add_argument('-e', '--escape', dest='is_escape', action='store_true', default=False, help='escape \uFFFA by space when output (default: false)') 
  parser.add_argument('-o', '--option', dest='opt', type=int, default=0, help='option (default=0)')
  # version info
  parser.add_argument('-v', '--version', action='version', version=__version__ )

  # optional arguments
  parser.add_argument('-d', '--debug', dest='debug', action='store_true', default=False, help='enable debugging mode (default: false)') 
  
  args = parser.parse_args()
  #sys.stderr.write("# parsed arguments: %s\n" % str(args))

  return args

def clean_line(input_line):
  """
  Strip leading and trailing spaces
  """

  input_line = re.sub('(^\s+|\s$)', '', input_line);
  return input_line

def load_nbest_score(nbest_file, score_file):
  nbest_inf = codecs.open(nbest_file, 'r', 'utf-8')
  score_inf = open(score_file, 'r')
  nbest_map = {}
 
  sys.stderr.write('  loading nbest scores %s, %s ...' % (nbest_file, score_file));
  line_id = 0
  for nbest_line in nbest_inf:
    nbest_line = clean_line(nbest_line)
    while True:
      score = clean_line(score_inf.readline())
      if score!='' and re.search('^[a-zA-Z#]', score)==None: # skip debug text
        break

    tokens = re.split(' ', nbest_line)
    nbest_map[' '.join(tokens[1:])] = score
    line_id = line_id + 1
    if (line_id % 10000 == 0):
      sys.stderr.write(' (%d) ' % line_id)

  sys.stderr.write(' done! Num lines = %d\n' % line_id)

  nbest_inf.close()
  score_inf.close()

  return nbest_map

def process_files(in_file, distinct_nbest_file, score_file, out_file, is_escape):
  """
  Read data from in_file, and output to out_file
  """

  nbest_map = load_nbest_score(distinct_nbest_file, score_file) # map a translation into an rnnlm score
  sys.stderr.write('  processing in_file = %s and out_file = %s ... ' % (in_file, out_file))
  inf = codecs.open(in_file, 'r', 'utf-8')
  ouf = codecs.open(out_file, 'w', 'utf-8')

  line_id = 0
  for eachline in inf:
    eachline = clean_line(eachline)
    tokens = re.split(' \|\|\| ', eachline)
    
    if len(tokens)<3:
      break

    id = tokens[0]
    translation = tokens[1]
    rnnlm_score = nbest_map[translation] 
    
    if is_escape: # replace \uFFFA by space. Important: this line should come after nbest_map lookup
      translation = re.sub(ur'\uFFFA', ' ', translation)
    
    if len(tokens)>3:
      decode_score = tokens[3]
    else:
      decode_score = tokens[2]
    ouf.write('%s ||| %s ||| rnnlm: %s DM: %s ||| %s\n' % (id, translation, rnnlm_score, decode_score, decode_score))

    line_id = line_id + 1
    if (line_id % 10000 == 0):
      sys.stderr.write(' (%d) ' % line_id)

  sys.stderr.write('  done! Num lines = %d\n' % line_id)

  inf.close()
  ouf.close()

if __name__ == '__main__':
  args = process_command_line()
  
  if args.debug == True:
    debug = True

  process_files(args.in_file, args.distinct_nbest_file, args.score_file, args.out_file, args.is_escape)
