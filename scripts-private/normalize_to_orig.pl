#!/usr/bin/perl
#
# Description:  Remove IBM's class based 
# annotation of numbers, dates, ordinals, 
# urls, etc.
#
# Replace each annotated span with just the 
# original source/target material (e.g.,
# $num_(三十六||36) -> 三十六).
#
# Author: Daniel Cer (danielcer@stanford.edu)
#############################################

while (<>) { chomp;
    s/[[:cntrl:]]/ /g;
    s/\$\S+\_\(([^|]+)\|\|[^)]*\)/\1/g;
    s/\$\S+\_\(([^|)]+)\)/\1/g;
    while (/\s(\S+)\@\@(\S+)\s/) { 
      s/\s(\S+)\@\@(\S+)\s/ \1 \2 /g; 
    }
    $o = lc($_);
    print "$o\n"; 
}
