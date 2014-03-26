#!/usr/bin/env python2.7
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Mon Mar  3 14:59:59 PST 2014

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
  parser.add_argument('in_file1', metavar='in_file1', type=str, help='input file 1') 
  parser.add_argument('in_file2', metavar='in_file2', type=str, help='input file 2') 
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

def process_files(in_file1, in_file2, out_file):
  """
  Read data from in_file1, in_file2, and output to out_file
  """

  sys.stderr.write('# in_file1 = %s, in_file2 = %s, out_file = %s\n' % (in_file1, in_file2, out_file))
  # input
  sys.stderr.write('# Input from %s.\n' % (in_file1))
  inf1 = codecs.open(in_file1, 'r', 'utf-8')
  sys.stderr.write('# Input from %s.\n' % (in_file2))
  inf2 = codecs.open(in_file2, 'r', 'utf-8')
  
  # output
  if out_file == '':
    sys.stderr.write('# Output to stdout.\n')
    ouf = sys.stdout
  else:
    sys.stderr.write('Output to %s\n' % out_file)
    check_dir(out_file)
    ouf = codecs.open(out_file, 'w', 'utf-8')

  line_id = 0
  sys.stderr.write('# Processing file %s ...\n' % (in_file1))
  for line1 in inf1:
    line1 = clean_line(line1)
    tokens1 = re.split(' \|\|\| ', line1)

    line2 = clean_line(inf2.readline())
    tokens2 = re.split(' \|\|\| ', line2)
   
    if tokens1[0]!=tokens2[0] or tokens1[1]!=tokens2[1] or tokens1[3]!=tokens2[3]:
      sys.stderr.write('! Mismatch %s vs %s\n' % (line1, line2))
      sys.exit(1)

    features1 = re.split(' ', tokens1[2])
    features2 = re.split(' ', tokens2[2])
    feature_map = {}
    for ii in range(len(features1)/2):
      name = features1[2*ii]
      value = features1[2*ii+1]
      feature_map[name] = value
    for ii in range(len(features2)/2):
      name = features2[2*ii]
      value = features2[2*ii+1]
      feature_map[name] = value
  
    new_features = []
    for name in feature_map.keys():
      new_features.append(name + ' ' + feature_map[name])
    tokens1[2] =  ' '.join(new_features)
    ouf.write('%s\n' % ' ||| '.join(tokens1))
    
    line_id = line_id + 1
    if (line_id % 10000 == 0):
      sys.stderr.write(' (%d) ' % line_id)

  sys.stderr.write('Done! Num lines = %d\n' % line_id)

  inf1.close()
  ouf.close()

if __name__ == '__main__':
  args = process_command_line()
  process_files(args.in_file1, args.in_file2, args.out_file)
