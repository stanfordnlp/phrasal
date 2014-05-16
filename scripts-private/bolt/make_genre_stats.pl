#!/usr/bin/perl

# TODO: mkayser needs to comment this!
# Take a file with segment info and produce a file giving counts for each genre

use strict;
use warnings;

die "Usage: $0 bitext_seg_info output_genre_stats_file" if @ARGV != 2;

my $bitext_seg_info_file = shift;
my $output_info_file = shift;

open IN, $bitext_seg_info_file or die $!;

my %genre_to_info = ();

while(<IN>) {
    my $seg_info = &read_key_val_pairs($_);
    
    my $genre = &get_required($seg_info, "genre");
    my $raw_trg_len = &get_required($seg_info, "raw_trg_len");
    my $raw_src_len = &get_required($seg_info, "raw_src_len");

    if(!defined($genre_to_info{$genre})) {
	$genre_to_info{$genre} = 
	{
	    "genre" => $genre,
	    "raw_target_num_words" => 0,
	    "raw_source_num_words" => 0,
	    "num_segments" => 0,
	};
    }
    $genre_to_info{$genre}->{"raw_target_num_words"} += $raw_trg_len;
    $genre_to_info{$genre}->{"raw_source_num_words"} += $raw_src_len;
    $genre_to_info{$genre}->{"num_segments"} += 1;
}

close IN;

open OUT, ">$output_info_file" or die $!;

foreach my $genre (sort {$genre_to_info{$b}->{"raw_target_num_words"} <=> $genre_to_info{$a}->{"raw_target_num_words"}} keys %genre_to_info) {
    &write_key_val_pairs($genre_to_info{$genre}, *OUT);
}

close OUT;

sub get_required {
    my ($hash, $key) = @_;
    die "Required key not found: $key" if !defined($hash->{$key});
    return $hash->{$key};
}

sub make_segment_info {
    my ($sline, $tline, $seg_index, $file_id, $genre) = @_;
    my @stok = &get_tokens($sline);
    my @ttok = &get_tokens($tline);

    my %info = ();
    $info{"raw_src_len"} = scalar(@stok);
    $info{"raw_trg_len"} = scalar(@ttok);
    $info{"seg_id"} = $seg_index;
    $info{"file_id"} = $file_id;
    $info{"genre"} = $genre;
    return %info;
}

sub get_tokens {
    my ($str) = @_;
    $str =~ s/\s+$//;
    my @tokens = split /\s+/, $str;
    return @tokens;
}

sub read_key_val_pairs {
    my ($s) = @_;
    $s =~ s/\s+$//;
    my @tokens = split /\t/, $s;
    my %pairs = ();
    foreach my $t (@tokens) {
	my @components = split /=/, $t, 2;
	my ($key, $val) = @components;
	die if defined($pairs{$key});
	$pairs{$key} = $val;
    }
    return \%pairs;
}

sub write_key_val_pairs {
    my ($pairs, $fh) = @_;
    my @strings = ();
    foreach my $key (sort keys %$pairs) {
	push @strings, sprintf("%s=%s", $key, $pairs->{$key});
    }
    print $fh join("\t", @strings)."\n";
}
