#!/usr/bin/perl

# 
# Usage:
# 
# ./LDC2005T34_replace_quote_internal_slashes.pl \
#      sloppy_LDC2005T34_file.txt  > clean_file.txt 2> log
#
# Author: Daniel Cer (Daniel.Cer@gmail.com)
#
#####################################################################

use utf8;

binmode(STDIN,":utf8");
binmode(STDOUT,":utf8");
binmode(STDERR,":utf8");

while (<STDIN>) { chomp;
   @chars = split //;
   $past_tab = 0;
   $in_quote = 0; 
   foreach $c (@chars) {
     if (!$past_tab) {
        print $c;
        $past_tab = 1 if ($c eq "\t");
        next;
     }
     if ($in_quote && $c eq "/") {
        print "&slash;";
        next;
     }
     print "$c";
     if ($c eq "\"") {
       $in_quote = !$in_quote;
     }
   }
   print "\n";
}
