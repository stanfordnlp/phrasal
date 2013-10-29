#!/usr/bin/perl
# 
# Usage:
# 
# ./procLDC2005T34_to_tgtt.pl \
#      LDC2005T34_file.proc > file.tgtt 2> log
#
# Converts cleaned LDC2005T34 data files into
# a tagged translation table (tgtt)
#
# Author: Daniel Cer (Daniel.Cer@gmail.com)
#
###################################################

use utf8;

$filename = $ARGV[0];
open fh, $filename or die;

binmode(fh,":utf8");
binmode(STDOUT,":utf8");
binmode(STDERR,":utf8");

$DELIM = "|||";

$supertag = "LDC05T34";
$tag =  $filename;
$tag =~ s/\_ce\_v1.*//g;
$tag =~ s/propernames/pn/g;
$tag =~ s/^.*ldc\_//;

while (<fh>) { chomp;
  @fields = split /\t/;
  $zh = $fields[0];
  $enList = $fields[1];
  $addInfo = $fields[3];
  $sub_tag = $addInfo;
  $sub_tag =~ s/\s/_/g;
  $enList =~ s/^\///g;
  $enList =~ s/\/$//g;
  @ens = split /\//, $enList;
  foreach $en (@ens) {
     $en = lc($en); 
     if ($sub_tag ne "") { 
       print "$zh ||| $en ||| $supertag:$tag:$sub_tag\n";
     } else {
       print "$zh ||| $en ||| $supertag:$tag\n";
     }
  } 
}
