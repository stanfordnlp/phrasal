#!/usr/bin/perl
#
# Description:  Remove IBM's class based 
# annotation of numbers, dates, ordinals, 
# urls, etc.
#
# Replace each annotated span with IBM's 
# rule based translation of each annotated 
# span (e.g., $num_(三十六||36) -> 36).
#
# Author: Daniel Cer (danielcer@stanford.edu)
#############################################

use open qw(:std :utf8);

while (<>) { chomp;
    s/[[:cntrl:]]/ /g;
    s/\$\S+\_\(([^|]+)\|\|([^)]*)\)/\2/g;
    s/\$\S+\_\(([^|)]+)\)/\1/g;
    while (/\s(\S+)\@\@(\S+)\s/) { 
      s/\s(\S+)\@\@(\S+)\s/ \1 \2 /g; 
    }
    $o = lc($_);
    s/\x{FFFA}/ /g;
    print "$o\n";
}
