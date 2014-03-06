#!/usr/bin/env python2.7
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Mon Mar  3 17:05:39 PST 2014

"""
Module docstrings.
"""

usage = 'Find new translations from the nbest list given the set of sentences we have already scored.' 

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
  parser.add_argument('nbest_file', metavar='nbest_file', type=str, help='nbest file') 
  parser.add_argument('nbest_distinct_file', metavar='nbest_distinct_file', type=str, help='nbest distinct file consisting of hose translations we have already scored.') 
  parser.add_argument('out_file', metavar='out_file', type=str, help='output file') 

  # optional arguments
  parser.add_argument('-o', '--option', dest='opt', type=int, default=0, help='option (default=0)')
  parser.add_argument('-d', '--debug', dest='debug', action='store_true', default=False, help='enable debugging mode (default: false)') 
  
  args = parser.parse_args()
  return args

def check_dir(out_file):
  dir_name = os.path.dirname(out_file)

  if dir_name != '' and os.path.exists(dir_name) == False:
    sys.stderr.write('! Directory %s doesn\'t exist, creating ...\n' % dir_name)
    os.makedirs(dir_name)

def clean_line(line):
  """
  Strip leading and trailing spaces
  """

  line = re.sub('(^\s+|\s$)', '', line);
  return line

def process_files(nbest_file, nbest_distinct_file, out_file):
  """
  Read data from nbest_file, nbest_distinct_file, and output to out_file
  """

  sys.stderr.write('# nbest_file = %s, nbest_distinct_file = %s, and out_file = %s\n' % (nbest_file, nbest_distinct_file, out_file))
  # input
  sys.stderr.write('# Loading nbest_distinct_file %s.\n' % (nbest_distinct_file))
  inf = codecs.open(nbest_file, 'r', 'utf-8')
  distinct_map = {}
  line_id = 0
  for line in inf:
    line = clean_line(line)
    distinct_map[line] = 1

    line_id = line_id + 1
    if (line_id % 10000 == 0):
      sys.stderr.write(' (%d) ' % line_id)
  inf.close()

  # input
  sys.stderr.write('# Loading %s.\n' % (nbest_file))
  inf = codecs.open(nbest_file, 'r', 'utf-8')
  
  line_id = 0
  sys.stderr.write('# Processing file %s ...\n' % (nbest_file))
  new_translation_map = {}
  for line in inf:
    line = clean_line(line)
    if line=='':
      continue
    tokens = re.split(' \|\|\| ', line)
    translation = tokens[1]
    if translation not in distinct_map:
      new_translation_map[translation] = 1

    line_id = line_id + 1
    if (line_id % 10000 == 0):
      sys.stderr.write(' (%d) ' % line_id)
  sys.stderr.write('Done! Num lines = %d. Num new translations = %d\n' % (line_id, len(new_translation_map.keys())))
  inf.close()

  # output
  if out_file == '':
    sys.stderr.write('# Output to stdout.\n')
    ouf = sys.stdout
  else:
    sys.stderr.write('Output to %s\n' % out_file)
    check_dir(out_file)
    ouf = codecs.open(out_file, 'w', 'utf-8')

  for translation in new_translation_map.keys():
    ouf.write('%s\n' % translation)
  ouf.close()

if __name__ == '__main__':
  args = process_command_line()
  process_files(args.nbest_file, args.nbest_distinct_file, args.out_file)
