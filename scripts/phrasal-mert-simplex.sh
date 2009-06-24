#!/bin/bash

###############################################################################
# phrasal-mert.pl with settings that seem to work well:
# 1) run downhill simplex followed by "cer"
# 2) '~' indicates that "simplex" and "cer" are repeated until convergence
# 3) 20 starting points instead of 5 (using 4 threads instead of 1)
###############################################################################

phrasal-mert.pl --opt-flags=\"-o simplex+cer~ -t 4 -p 20\" $*
