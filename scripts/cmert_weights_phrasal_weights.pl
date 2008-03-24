#!/usr/bin/perl
# Converts the feature weights files produced by cmert to the feature weights 
# file expected by phrasal. 
#
# Usage: 
# 
# ./cmert_weights_to_phrasal_weights.pl cmert_weights_file \
#     phrasal_nbest_list > phrasal_weights_file 2>log
#
# Author: Daniel Cer (Daniel.Cer@gmail.com)
#############################################################################

$FEATURE_FIELD = 2;

if (@ARGV != 2) {
  $name = $0; $name =~ s/.*\///g;
  print stderr "Usage:\n\t$name cmert.wts \\\n".
    "\t   phrasal_or_cmert_nbest.list > phrasal.wts 2> log\n";
  exit -2;
}

open pwt, $ARGV[0] or die;
while(<pwt>) { chomp; @feature_wt = split /\s+/; last; } close pwt;

$cnt_feature_weights = @feature_wt;
print stderr "Features accounted for by weights file: $cnt_feature_weights\n"; 

open pnbest, $ARGV[1] or die;
while (<pnbest>) {
  @fields = split /\s*\|\|\|\s*/;
  $features = $fields[$FEATURE_FIELD];
  @features = split /\s+/, $features;
  foreach $feature (@features) {
    $feature_set{$feature} = 1 if ($feature =~ /.*:$/);
  }
}

@nbest_feature_names = keys %feature_set;

$cnt_nbest_feature_names = @nbest_feature_names;
print stderr "Features found in nbest list: $cnt_nbest_feature_names\n"; 
die if ($cnt_feature_weights != $cnt_nbest_feature_names);

$i = 0;
foreach $feature_name (sort @nbest_feature_names) {
  $feature_name =~ s/:$//;
  print "$feature_name $feature_wt[$i++]\n";
}

