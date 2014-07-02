#!/usr/bin/perl
use strict;
use warnings;

binmode(STDIN, ":utf8");
binmode(STDOUT, ":utf8");
binmode(STDERR, ":utf8");


die "Usage: $0 input_sentences output_sentences output_line_indices num_sentences_to_select max_length_of_sentence" if scalar(@ARGV) != 5;


my $inputFile = shift;
my $outputFile = shift;
my $outputIndicesFile = shift;
my $numSentences = shift;
my $maxLength = shift;

srand(100);

my @lines = ();

my $lineNum=0;

open IN, $inputFile or die;
binmode(IN, ":utf8");
while(<IN>) {
    my $text = $_;

    s/\s+$//;
    s/^\s+//;

    my @words = split /\s+/, $_;
    my $sentenceLength = scalar(@words);

    if($sentenceLength <= $maxLength) {
	push @lines, [$text,$lineNum];
    }

    $lineNum++;    
}
close IN;

my @selected = ();

if(scalar(@lines) < $numSentences) {
    die "Cannot produce enough lines: only ".scalar(@lines)." sufficiently short lines exist but $numSentences were requested.";
}
else {
    while(scalar(@selected) < $numSentences) {
	my $index = int(rand(scalar(@lines)));
	my $selectedPair = splice(@lines, $index,1);
	push @selected, $selectedPair;
    }
    @selected = sort {$a->[1] <=> $b->[1]} @selected;
    open OUT, ">$outputFile" or die "Cannot open file for output: $outputFile";
    binmode(OUT, ":utf8");

    open INDICES, ">$outputIndicesFile" or die "Cannot open indices file for output: $outputIndicesFile";
    foreach my $selectedSent (@selected) {
	print OUT $selectedSent->[0];
	print INDICES $selectedSent->[1]."\n";
    }
    close OUT;
    close INDICES;
}
