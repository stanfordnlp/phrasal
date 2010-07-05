#!/usr/bin/perl -w

# $Id: clean-corpus-n.perl 1392 2007-05-21 13:06:40Z hieuhoang1972 $
use strict;
use Getopt::Long;
my $help;
my $lc = 0; # lowercase the corpus?
my $enc = "utf8"; # encoding of the input and output files
    # set to anything else you wish, but I have not tested it yet

GetOptions(
  "help" => \$help,
  "lowercase|lc" => \$lc,
  "encoding=s" => \$enc,
) or exit(1);

if (scalar(@ARGV) != 6 || $help) {
    print "syntax: clean-corpus-n.perl corpus l1 l2 clean-corpus min max\n";
    exit;
}

my $corpus = $ARGV[0];
my $l1 = $ARGV[1];
my $l2 = $ARGV[2];
my $out = $ARGV[3];
my $min = $ARGV[4];
my $max = $ARGV[5];

print STDERR "clean-corpus.perl: processing $corpus.$l1 & .$l2 to $out, cutoff $min-$max\n";

my $opn = undef;
my $l1input = "$corpus.$l1";
if (-e $l1input) {
  $opn = $l1input;
} elsif (-e $l1input.".gz") {
  $opn = "zcat $l1input.gz |";
}
open(F,$opn) or die "Can't open '$opn'";
my $l2input = "$corpus.$l2";
if (-e $l2input) {
  $opn = $l2input;
} elsif (-e $l2input.".gz") {
  $opn = "zcat $l2input.gz |";
}
open(E,$opn) or die "Can't open '$opn'";

open(FO,"| gzip >$out.$l1.gz") or die "Can't write $out.$l1";
open(EO,"| gzip >$out.$l2.gz") or die "Can't write $out.$l2";

# necessary for proper lowercasing
my $binmode;
if ($enc eq "utf8") {
  $binmode = ":utf8";
} else {
  $binmode = ":encoding($enc)";
}
binmode(F, $binmode);
binmode(E, $binmode);
binmode(FO, $binmode);
binmode(EO, $binmode);

my $innr = 0;
my $outnr = 0;
while(my $f = <F>) {
	my $skip=0;
  $innr++;
  print STDERR "." if $innr % 10000 == 0;
  print STDERR "($innr)" if $innr % 100000 == 0;
  my $e = <E>;
  die "$corpus.$l2 is too short!" if !defined $e;
  chomp($e);
  chomp($f);

  #if lowercasing, lowercase
  if ($lc) {
    $e = lc($e);
    $f = lc($f);
  }
  
  # $e =~ s/\|//g;  # kinda hurts in factored input
  $e =~ s/\s+/ /g;
  $e =~ s/^ //;
  $e =~ s/ $//;
  # $f =~ s/\|//g;  # kinda hurts in factored input
  $f =~ s/\s+/ /g;
  $f =~ s/^ //;
  $f =~ s/ $//;
  $skip=1 if $f eq '';
  $skip=1 if $e eq '';
  my @E = split(/ /,$e);
  my @F = split(/ /,$f);
	print STDERR scalar(@E)."\t".scalar(@F)."\n";
  $skip=1 if scalar(@E) > $max;
  $skip=1 if scalar(@F) > $max;
  $skip=1 if scalar(@E) < $min;
  $skip=1 if scalar(@F) < $min;
  $skip=1 if scalar(@E)/scalar(@F) > 9;
  $skip=1 if scalar(@F)/scalar(@E) > 9;
	if($skip==1) {
		print "Skipping:\n  $f\n  $e\n";
		print FO "emptyline\n";
		print EO "emptyline\n";
		next;
	}
  
	$f =~ s/[ \|]\|/ /;
	$e =~ s/[ \|]\|/ /;
    
  
  $outnr++;
  print FO $f."\n";
  print EO $e."\n";
}
print STDERR "\n";
my $e = <E>;
die "$corpus.$l2 is too long!" if defined $e;

print STDERR "Input sentences: $innr  Output sentences:  $outnr\n";
