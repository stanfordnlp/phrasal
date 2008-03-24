#!/usr/bin/perl
# Converts the feature weights files used by phrasal to the feature weights 
# file expected by cmert. 
#
# Usage: 
# 
# ./phrasal_weights_to_cmert_weights.pl phrasal_weights_file \
#     phrasal_nbest_list > cmert_weights_file 2>names_for_cmert_weights
#
# Author: Daniel Cer (Daniel.Cer@gmail.com)
#############################################################################

$FEATURE_FIELD = 2;

if (@ARGV != 2) {
  $name = $0; $name =~ s/.*\///g;
  print stderr "Usage:\n\t$name phrasal.wts \\\n".
    "\t   phrasal_or_cmert_nbest.list > cmert_weights 2> log\n";
  exit -2;
}

open pwt, $ARGV[0] or die;

while(<pwt>) { chomp;
  @fields = split /\s+/;
  $feature_name = $fields[0];
  $feature_weight = $fields[1];
  $feature_weights{$feature_name.":"} = $feature_weight; 
}
close pwt;

@feature_names = keys %feature_weights;
$cnt_feature_names = @feature_names;
#print stderr "Features accounted for by weights file: $cnt_feature_names\n"; 

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
#print stderr "Features found in nbest list: $cnt_nbest_feature_names\n"; 

foreach $feature_name (sort @nbest_feature_names) {
  $wt = $feature_weights{$feature_name} || 0.0;
  print stderr "$feature_name ";
  print "$wt ";
}
print stderr "\n";
print "\n";
