use strict;
use warnings;

die "Usage: $0 data_root set_name" if @ARGV != 2;

my $root = shift @ARGV;
my $set = shift @ARGV;

my $s = "$root/$set/src";
my @refs = glob("$root/$set/ref*");

my $n = &getNumLines($s);

my $n1 = int($n/2);
my $n2 = $n-$n1;

my $outdir1 = "$root/${set}.split1";
my $outdir2 = "$root/${set}.split2";

mkdir $outdir1;
mkdir $outdir2;

foreach my $fn (($s,@refs)) {
    my $b = `basename $fn`;
    my $outfn1 = "$outdir1/$b";
    my $outfn2 = "$outdir2/$b";

    &writeSplitFiles($fn,$n1,$n2,$outfn1,$outfn2);
}


sub getNumLines {
    my ($f) = @_;
    open IN, $f or die $!;
    my $n=0;
    while(<IN>) { $n++; }
    close IN;
    return $n;
}


sub writeSplitFiles {
    my ($fn, $n1, $n2, $outfn1, $outfn2) = @_;
    open IN, $fn or die $!;
    my @lines = <IN>;
    close IN;
    die if scalar(@lines) != $n1+$n2;
    my @lines1 = @lines[0..$n1-1];
    my @lines2 = @lines[$n1..$n1+$n2-1];
    &writeLines(\@lines1, $outfn1);
    &writeLines(\@lines2, $outfn2);
}

sub writeLines {
    my ($lines, $fn) = @_;
    open OUT, ">$fn" or die $!;
    foreach my $line (@$lines) {
	print OUT $line;
    }
    close OUT;
}
