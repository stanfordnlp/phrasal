#!/usr/bin/perl
# Calculates the distance between two points in weight space.
#
# Usage:
#
#   ./phrasal_weight_delta.pl  phrasal_A.wts phrasal_B.wts
# 
# Author: Daniel Cer (Daniel.Cer@gmail.com)
#############################################################################
if ($ARGV[0] eq "-max") {
	shift;
	$max = 1;
}

if (@ARGV != 2) {
  $nm = $0; $nm =~ s/.*\///g;
  print stderr "Usage:\n\t$nm [-max] phrasal_A.wts phrasal_B.wts\n";
  exit -1;
}

open fh, $ARGV[0] or die;
while (<fh>) { chomp; @fields = split /\s+/; $vA{$fields[0]} = $fields[1]; } 
close fh;

open fh, $ARGV[1] or die;
while (<fh>) { chomp; @fields = split /\s+/; $vB{$fields[0]} = $fields[1]; } 
close fh;

foreach $key (keys %vA) { $allKeys{$key} = 1; }
foreach $key (keys %vB) { $allKeys{$key} = 1; }
if (!$max) {
  $v = 0;
  foreach $key (keys %allKeys) {
    $diff = $vA{$key} - $vB{$key};
    $v += $diff*$diff;
  }
  $v = sqrt($v);
} else {
	$max_diff = 0;
	foreach $key (keys %allKeys) {
        $diff = abs($vA{$key} - $vB{$key});
		$max_diff = $diff if ($diff > $max_diff);
	}
	$v = $max_diff;
}

print "$v\n";
