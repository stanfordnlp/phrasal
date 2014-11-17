#!/usr/bin/perl
use strict;
use warnings;

my $VERBOSE = 1;

die "Usage: $0 transfile regionsfile\n" unless scalar(@ARGV)==2;

my ($trans,$regions) = @ARGV;

my $refsdir= "/scr/nlp/data/gale/BOLT/zhen-2014/corpora/eval";
my $scripts = $ENV{"JAVANLP_HOME"}."/projects/mt/scripts-private/bolt";

die if !defined($trans) || !defined($regions);

open IN, $regions or die $!;
while(<IN>) {
    s/\s+$//;
    my @tokens = split /\s+/, $_;
    &score_set($refsdir, $trans, @tokens);
}
close IN;

sub score_set {
    my ($refsdir, $trans, $name, $orig_name, $part, $first, $last) = @_;
    my @ref_files = glob("$refsdir/$name/ref*");
    if(scalar(@ref_files)>0) {
	my $refglob = "$refsdir/$name/ref*";
	my $tercmd = "cat $trans | gawk 'NR>=$first && NR<=$last' | java edu.stanford.nlp.mt.tools.Evaluate \"ter\" $refglob | gawk 'NR==2{print \$3}'";
	my $bleucmd = "cat $trans | gawk 'NR>=$first && NR<=$last' | bleu $refglob | gawk 'NR==2{print \$3}' | perl -pe 's/,//;'";
        my $scorecmd = "cat $trans | gawk 'NR>=$first && NR<=$last' | java edu.stanford.nlp.mt.tools.Evaluate \"bleu-ter/2\" $refglob | gawk 'NR==2{print \$3}'";
        my $lencmd = "cat $trans | gawk 'NR>=$first && NR<=$last' | perl $scripts/get_length_ratio.pl $refglob | gawk 'NR==1{print \$3}'";

	my $score = &runCommand($scorecmd);
	my $ter = &runCommand($tercmd);
        my $bleu = &runCommand($bleucmd);
        my $len = &runCommand($lencmd);
	$ter =~ s/\s+//g;
	$bleu =~ s/\s+//g;
        $score =~ s/\s+//g;
        $len =~ s/\s+//g;
	print "$trans $name $ter $bleu $score $len\n";
    }
    else {
	warn "no references for name: $name\n";
    }
}

sub runCommand {
    my ($cmd) = @_;
    if($VERBOSE) {
	print STDERR "running: $cmd\n";
    }
    return `$cmd`;
}
