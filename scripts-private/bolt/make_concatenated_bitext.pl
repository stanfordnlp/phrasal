#!/usr/bin/perl

# TODO: mkayser needs to comment this!

use strict;
use warnings;

die "Usage: $0 bitext_info_file src_lang_shortname tgt_lang_shortname output_prefix" if @ARGV != 4;

my $bitext_info_file = shift;
my $src_lang = shift;
my $tgt_lang = shift;
my $output_prefix = shift;

my $output_tgt_file = sprintf("%s.%s", $output_prefix, $tgt_lang);
my $output_src_file = sprintf("%s.%s", $output_prefix, $src_lang);
my $output_info_file = sprintf("%s.%s", $output_prefix, "seg_info");

open IN, $bitext_info_file or die $!;

open TOUT, ">$output_tgt_file" or die $!;
open SOUT, ">$output_src_file" or die $!;
open IOUT, ">$output_info_file" or die $!;

while(<IN>) {
    my $file_info = &read_key_val_pairs($_);
    my $tfile = &get_required($file_info, "target_file_name");
    my $sfile = &get_required($file_info, "source_file_name");
    my $file_id = &get_required($file_info, "file_id");
    my $genre   = &get_required($file_info, "presumed_genres");
    
    open TIN, $tfile or die $!;
    open SIN, $sfile or die $!;

    my $seg_index=0;
    while(1) {
        my $sline = <SIN>;
	my $tline = <TIN>;
	last if !defined($sline) && !defined($tline);
	die if !defined($sline) || !defined($tline);
	my %segment_info = &make_segment_info($sline, $tline, $seg_index, $file_id, $genre);

	&write_key_val_pairs(\%segment_info, *IOUT);
	print TOUT $tline;
	print SOUT $sline;

	$seg_index++;
    }

    close SIN;
    close TIN;
}

close TOUT;
close SOUT;
close IOUT;

close IN;

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
