#!/usr/bin/python

import sys

h = {}
with open(sys.argv[2]) as longfh:
   for line in longfh:
      fields = line.split(" ||| ")
      id = (fields[0], fields[1])
      h[id] = line


with open(sys.argv[1]) as shortfh:
   for line in shortfh:
     sfields = line.split(" ||| ")
     id = (sfields[0], sfields[1])
     score = sfields[2].rstrip()
     lfields = h[id].split(" ||| ")
     lfields[3] = score
     new_long_entry = " ||| ".join(lfields);
     print new_long_entry.rstrip()
  
