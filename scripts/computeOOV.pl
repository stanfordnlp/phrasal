#!/usr/bin/perl -w

##################################################
# Compute OOV rate on test set.
# Author: Pichuan Chang
##################################################

open(TRAIN, $ARGV[0]) or die;
open(TEST, $ARGV[1]) or die;
while(<TRAIN>) {
    chomp;
    @toks = split(/\s+/);
    foreach $t (@toks) {
        if (not defined $trainLex{$t}) {
            $trainLex{$t} = 1;
        } else {
            $trainLex{$t}++;
        }
    }
}

print STDERR "done loading training: $ARGV[0]\n";

$oov_tokens = 0;
$oov_tokens_lg_than_one = 0;
$total_tokens = 0;

while(<TEST>) {
    chomp;
    @toks = split(/\s+/);
    foreach $t (@toks) {
        if (not defined $testLex{$t}) {
            $testLex{$t} = 1;
        } else {
            $testLex{$t}++;
        }
        
        if (not defined $trainLex{$t}) {
            $oov_tokens++;
            $oov_tokens_lg_than_one++;
        } elsif ($trainLex{$t} == 1) {
            $oov_tokens_lg_than_one++;
        }
        $total_tokens++;
    }
}

print STDERR "done loading testing: $ARGV[1]\n";

@tl = keys %trainLex;
$trainLexSize = $#tl+1;
print "training lexicon size=".$trainLexSize."\n";
@testl = keys %testLex;
$testLexSize = $#testl+1;
print "testing lexicon size=".$testLexSize."\n";
$oov = 0;
$oov_lg_than_one = 0;

foreach $testK (@testl) {
    if (not defined $trainLex{$testK}) {
        $oov++;
        $oov_lg_than_one++;
    } elsif ($trainLex{$testK} == 1) {
        $oov_lg_than_one++;
    }
}
print "OOV (type) rate: ".$oov/$testLexSize."\n";
print "OOV (type) rate (if count1 is oov): ".$oov_lg_than_one/$testLexSize."\n";
print "OOV (token) rate: ".$oov_tokens/$total_tokens."\n";
print "OOV (token) rate (if count1 is oov): ".$oov_tokens_lg_than_one/$total_tokens."\n";
