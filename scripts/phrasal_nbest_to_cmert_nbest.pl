#!/usr/bin/perl
# This script converts phrasal style n-best lists to
# the n-best lists expected by cmert
#
# The n-best lists produced by phrasal and those expected by cmert are
# highly similar, but differ in the following ways:
#
# - n-best lists produced by phrasal can have sparse feature representations.
#   This in turn leads to different translations having different sets of
#   features listed for them.
#
# - n-best lists produced by phrasal can include additional information after
#   the score field
#
# Usage:
#
# ./phrasal_nbest_to_cmert_nbest.pl < phrasal.nbest > cmert.nbest 2> log
#
# Author: Daniel Cer (Daniel.Cer@gmail.com)
#############################################################################

$ID_FIELD = 0;
$TRANS_FIELD = 1;
$FEATURES_FIELD =2;
$SCORE_FIELD = 3;

while (<>) { chomp;
  push @lines, $_;   
  @fields = split /\s*\|\|\|\s*/;
  $features = $fields[$FEATURES_FIELD];
  @features = split /\s+/, $features;
  foreach $feature (@features) {
    $feature_set{$feature} = 1 if ($feature =~ /.*:$/);
  }
}

print stderr "Complete Feature Set\n";
for $feature (sort keys %feature_set) {
   print stderr "\t'$feature'\n";
}

foreach $line (@lines) { 
  @fields = split /\s*\|\|\|\s*/, $line;
  $features = $fields[$FEATURES_FIELD];
  @features = split /\s+/, $features;
  for $feature (keys %feature_set) { 
     $feature_hash{$feature} = 0.0; 
  }
  for ($i = 0; $i < $#features; $i += 2) {
    if (not ($features[$i] =~ /.*:$/)) {
       die "feature: '$features[$i]' data pt: $line";
    }
    if ($i+1 > $#features) {
       die "feature: '$features[$i]' data pt: $line";
    }
    $feature_hash{$features[$i]} = $features[$i+1];
  }
  $features_field = "";
  for $feature (sort keys %feature_set) {
    $features_field .= $feature;
    $features_field .= " ";
    $features_field .= sprintf "%f", $feature_hash{$feature};
    $features_field .= " ";
   }
  
   print "$fields[$ID_FIELD] ||| $fields[$TRANS_FIELD] ||| $features_field ||| $fields[$SCORE_FIELD]\n";
}
