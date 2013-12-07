#!/usr/bin/perl
# 
# Map Stanford NLP internal data set names to the offical names of each data
# set used by IBM. 
#
# Performing this mapping can be useful for determining the correct "section 
# name" (effectively data set name) argument to scripts that prepare data files 
# for submission to IBM such as make_IBM_XML_no_pp_scores.pl.
#
# author: danielcer@stanford.edu
#
###############################################################################

open fh, "$ENV{JAVANLP_HOME}/projects/mt/scripts-private/name_fix.list" or die;

while (<fh>) {
  chomp;
  @f = split /\t/; # print "$f[0] => $f[1]\n";
  $fixes{$f[0]} = $f[1]; 
}

while (<STDIN>) {
  chomp;
  if ("$fixes{$_}") {
    print "$fixes{$_}";
  } else {
    print "$_";
  }
}
