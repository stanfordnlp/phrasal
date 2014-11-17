#!/usr/bin/env python2.7
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Tue May 27 02:25:23 PDT 2014

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
  parser.add_argument('out_file', metavar='out_file', type=str, help='output file') 

  # optional arguments
  parser.add_argument('-o', '--option', dest='opt', type=int, default=0, help='0 -- remove feature field (the third field), 1 -- keep lm features and add decoder score as dm, output contains only id/translation/features, 2 -- clean feature field, only retain LM field, output id/translation/features, 3 -- extract scores from last field only, 4 -- add decoder score, keep all LM+len scores, keep all fields in n-best list, to facilitate downstream processing. (default=0)')
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

def process_files(in_file, out_file, opt):
  """
  Read data from in_file, and output to out_file
  """

  sys.stderr.write('# in_file = %s, out_file = %s\n' % (in_file, out_file))
  # input
  if in_file == '':
    sys.stderr.write('# Input from stdin.\n')
    inf = sys.stdin 
  else:
    sys.stderr.write('# Input from %s.\n' % (in_file))
    inf = codecs.open(in_file, 'r', 'utf-8')
  
  # output
  if out_file == '':
    sys.stderr.write('# Output to stdout.\n')
    ouf = sys.stdout
  else:
    sys.stderr.write('Output to %s\n' % out_file)
    check_dir(out_file)
    ouf = codecs.open(out_file, 'w', 'utf-8')

  line_id = 0
  sys.stderr.write('# Processing file %s ...\n' % (in_file))
  feature_list = []
  feature_map = {}
  for line in inf:
    line = clean_line(line)
    if re.search('^#', line): # comment line
      print("Skip line: ", line)
      continue
    
    tokens = re.split(' \|\|\| ', line)
    if opt==1 or opt==2 or opt==4:
      # find lm score
      features = re.split(' ', tokens[2])
      lmTokens = []
      for ii in range(len(features)/2):
        name = features[2*ii]
        value = features[2*ii+1]
        if re.search('LM', name) or re.search('len', name):
          lmTokens.append(name)
          lmTokens.append(value)

          if name not in feature_map:
            feature_list.append(name)
            feature_map[name] = 1

      if len(lmTokens)/2!=len(feature_list):
        sys.stderr.write('\n! Failed to find LM features: %s\n' % (line))
        lmTokens = []
        for name in feature_list:
          lmTokens.append(name)
          lmTokens.append('0.0000E0')
      featureStr = ' '.join(lmTokens)
     
      if opt==1: # retain only LM scores + dm score
        new_tokens = []
        new_tokens.append(tokens[0])
        new_tokens.append(tokens[1])
        dmScore = tokens[3].strip()
        featureStr = featureStr + ' dm: ' + dmScore
        new_tokens.append(featureStr)
      elif opt==2: # only retain lm features
        new_tokens = tokens 
        new_tokens[2] = featureStr
      elif opt==4: # like opt=1 but preserve all nbest fields
        new_tokens = tokens
        dmScore = tokens[3].strip()
        featureStr = featureStr + ' dm: ' + dmScore
        new_tokens[2] = featureStr
    elif opt==3: # only extract scores
      new_tokens = []
      features = re.split(' ', tokens[len(tokens)-1])
      for ii in range(len(features)/2):
        name = features[2*ii]
        value = features[2*ii+1]
        new_tokens.append(value)
    else:
      new_tokens = tokens 
      new_tokens.pop(2) # remove features

    ouf.write('%s\n' % ' ||| '.join(new_tokens))
    line_id = line_id + 1
    if (line_id % 10000 == 0):
      sys.stderr.write(' (%d) ' % line_id)

  sys.stderr.write('Done! Num lines = %d\n' % line_id)

  inf.close()
  ouf.close()

if __name__ == '__main__':
  args = process_command_line()
  process_files(args.in_file, args.out_file, args.opt)
