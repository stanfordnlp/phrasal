#!/usr/bin/env python
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Thu Aug  1 14:45:05 PDT 2013

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
  parser.add_argument('vocab_file', metavar='vocab_file', type=str, help='vocab file') 
  parser.add_argument('in_file', metavar='in_file', type=str, help='input file') 
  parser.add_argument('out_file', metavar='out_file', type=str, help='output file') 

  parser.add_argument('-o', '--option', dest='opt', type=int, default=0, help='option (default=0)')
  
	# version info
  parser.add_argument('-v', '--version', action='version', version=__version__ )

  # optional arguments
  parser.add_argument('-d', '--debug', dest='debug', action='store_true', default=False, help='enable debugging mode (default: false)') 
  
  args = parser.parse_args()
  #sys.stderr.write("# parsed arguments: %s" % str(args))

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

def load_vocab(vocab_file):
  """
  Load vocab file in which each line contains 3 tokens "word id freq".
  Return: word2id (map word into id), freqs array, and words array.
  """

  inf = codecs.open(vocab_file, 'r', 'utf-8') #open(vocab_file, 'r')
  sys.stderr.write('  load vocab file %s ... ' % (vocab_file))
  word2id = {}
  freqs = []
  words = []

  line_id = 0
  for eachline in inf:
    tokens = re.split('\s+', clean_line(eachline))
    assert len(tokens)>0
    
    # word
    word = tokens[0]
    words.append(word)
    
    if len(tokens) == 3:
      word2id[word] = int(tokens[1])
      freqs.append(int(tokens[2]))
    else:
      word2id[word] = line_id
      freqs.append(1)
    assert len(words)>word2id[word], 'len(words) %d <= word2id[%s] %d\n' % (len(words), word, word2id[word])

    line_id = line_id + 1
    if (line_id % 100000 == 0):
      sys.stderr.write(' (%d) ' % line_id)
  inf.close()
  sys.stderr.write('done! Num tokens = %d\n' % line_id)
  return (word2id, freqs, words)


def process_files(vocab_file, in_file, out_file):
  """
  Read data from vocab_file, in_file, and output to out_file
  """

  #sys.stderr.write('# vocab_file = %s, in_file = %s, out_file = %s\n' % (vocab_file, in_file, out_file))
  (word2id, freqs, words) = load_vocab(vocab_file)

  inf = codecs.open(in_file, 'r', 'utf-8')

  check_dir(out_file)
  ouf = codecs.open(out_file, 'w', 'utf-8')
  line_id = 0
  sys.stderr.write('  processing file %s ... ' % (in_file))
  for eachline in inf:
    eachline = clean_line(eachline)
    tokens = re.split('\s+', eachline)
    for index in range(len(tokens)):
      if tokens[index] not in word2id:
        tokens[index] = '<UNK>'
    ouf.write('%s\n' % ' '.join(tokens))

    line_id = line_id + 1
    if (line_id % 10000 == 0):
      sys.stderr.write(' (%d) ' % line_id)

  sys.stderr.write('done! Num lines = %d\n' % line_id)

  inf.close()
  ouf.close()

if __name__ == '__main__':
  args = process_command_line()
  
  if args.debug == True:
    sys.stderr.write('Debug mode\n')

  process_files(args.vocab_file, args.in_file, args.out_file)
