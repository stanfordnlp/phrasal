#!/usr/bin/perl -w

open(TRAINDEVTEST, $ARGV[0]) or die;
open(TRAIN, $ARGV[1]) or die;
open(DEV, $ARGV[2]) or die;
open(TEST, $ARGV[3]) or die;
open(OTHER, $ARGV[4]) or die;

while(<TRAINDEVTEST>) {
    chomp;
    s/^\s+//;
    s/\s+$//;
    push @traindevtest, $_;
}

while(<TRAIN>) {
    chomp;
    s/^\s+//;
    s/\s+$//;
    push @train, $_;
}

while(<DEV>) {
    chomp;
    s/^\s+//;
    s/\s+$//;
    push @dev, $_;
}

while(<TEST>) {
    chomp;
    s/^\s+//;
    s/\s+$//;
    push @test, $_;
}

while(<OTHER>) {
    chomp;
    s/^\s+//;
    s/\s+$//;
    push @other, $_;
}

$trainIdx = 0;
$devIdx = 0;
$testIdx = 0;
$otherIdx = 0;
foreach $set (@traindevtest) {
    if ($set eq "train") {
        print $train[$trainIdx],"\n";
        $trainIdx++;
    }
    elsif ($set eq "dev") {
        print $dev[$devIdx],"\n";
        $devIdx++;
    }
    elsif ($set eq "test") {
        print $test[$testIdx],"\n";
        $testIdx++;
    }
    elsif ($set eq "n/a") {
        print $other[$otherIdx],"\n";
        $otherIdx++;
    } else {
        die;
    }
}
