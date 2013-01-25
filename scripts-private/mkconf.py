#!/usr/bin/env python
#
# Generates a UCB aligner conf file for
# the given input options.
#
#
import sys
import os
from os.path import basename

args = sys.argv[1:]
if len(args) != 4:
    sys.stderr.write('Usage: python %s model_dir data_dir src_extension tgt_extension < template > conf_file%s' % (basename(sys.argv[0]), os.linesep))
    sys.exit(-1)

for line in sys.stdin:
    line = line.strip()
    if len(line) == 0 or line[0] == '#':
        print line
    else:
        opt = line.split('\t')[0]
        if opt == 'execDir':
            print '%s\t%s' % (opt, args[0])
        elif opt == 'foreignSuffix':
            # Source text
            print '%s\t%s' % (opt, args[2])
        elif opt == 'englishSuffix':
            # Target text
            print '%s\t%s' % (opt, args[3])
        elif opt == 'trainSources':
            print '%s\t%s' % (opt, args[1])
        else:
            print line
