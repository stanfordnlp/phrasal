#!/usr/bin/env python
# Author: Thang Luong <luong.m.thang@gmail.com>, created on Fri Aug 29 01:27:45 PDT 2014

"""
"""

usage = 'USAGE DESCRIPTION.' 

### Module imports ###
import sys
import os
import argparse # option parsing
import re # regular expression
import codecs

### Function declarations ###
def process_command_line():
  """
  Return a 1-tuple: (args list).
  `argv` is a list of arguments, or `None` for ``sys.argv[1:]``.
  """
  
  parser = argparse.ArgumentParser(description=usage) # add description
  # positional arguments
  parser.add_argument('nbest_file', metavar='nbest_file', type=str, help='input file') 
  parser.add_argument('id_file', metavar='id_file', type=str, help='id file (format: name start_id end_id). Note start_id/end_id are inclusive and start from 1.') 
  parser.add_argument('out_dir', metavar='out_dir', type=str, help='output file') 

  # optional arguments
  parser.add_argument('--offset', dest='offset', type=int, default=1, help='offset value for the start_id/end_id of the id_file (default=1)')
  parser.add_argument('--update_id', dest='update_id', action='store_true', help='to reset id to start with 0 (default=False)')
  
  args = parser.parse_args()
  return args

def check_dir(dir_name):
  #dir_name = os.path.dirname(out_dir)

  if dir_name != '' and os.path.exists(dir_name) == False:
    sys.stderr.write('! Directory %s doesn\'t exist, creating ...\n' % dir_name)
    os.makedirs(dir_name)

def process_files(nbest_file, id_file, out_dir, offset, update_id):
  """
  Read data from nbest_file, and output to out_dir
  """

  sys.stderr.write('# nbest_file = %s, id_file = %s, out_dir = %s\n' % (nbest_file, id_file, out_dir))
  inf = codecs.open(nbest_file, 'r', 'utf-8')
  id_inf = codecs.open(id_file, 'r', 'utf-8')
  check_dir(out_dir)

  line_id = 0
  sys.stderr.write('# Processing file %s ...\n' % (nbest_file))
  end_id = -1
  output_count = 0
  for nbest_line in inf:
    nbest_tokens = re.split(' \|\|\| ', nbest_line.strip())
    nbest_id = int(nbest_tokens[0])
    if nbest_id>end_id: # start a new file
      if end_id!=-1: # close the previous file
        ouf.close()
        sys.stderr.write('  num lines written=%d\n' % count) 
      id_line = id_inf.readline()
      if id_line == None: # no more file
        break
      tokens = re.split('\s+', id_line.strip())
      assert len(tokens)>=3
      name = tokens[0]
      start_id = int(tokens[-2])-offset
      end_id = int(tokens[-1])-offset
      count = 0

      out_file = out_dir + '/' + name + '.nbest'
      sys.stderr.write('\n# Output to %s, num sents=%d\n' % (out_file, end_id-start_id+1))
      sys.stderr.write('  id_line = %s' % id_line)
      ouf = codecs.open(out_file, 'w', 'utf-8')
    
    assert nbest_id>=start_id and nbest_id<=end_id
    if update_id==True:
      tokens = re.split(' \|\|\| ', nbest_line)
      tokens[0] = str(int(tokens[0])-start_id)
      nbest_line = ' ||| '.join(tokens)
    ouf.write('%s' % nbest_line)
    count += 1
    line_id = line_id + 1
    if (line_id % 10000 == 0):
      sys.stderr.write(' (%d) ' % line_id)

  sys.stderr.write('Done! Num lines = %d\n' % line_id)
   
  inf.close()
  id_inf.close()
  ouf.close()
  
  # remove the last incomplete file
  if nbest_id != end_id:
    sys.stderr.write('! The last file %s is incomplete, nbest_id %d != end_id %d. Remove.\n' % (out_file, nbest_id, end_id))
    os.remove(out_file)

if __name__ == '__main__':
  args = process_command_line()
  process_files(args.nbest_file, args.id_file, args.out_dir, args.offset, args.update_id)
