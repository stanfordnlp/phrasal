#!/usr/bin/perl

# TODO: mkayser needs to comment this!
# Sum fields in the format used for metadata annotation on bitext and test sets.

use strict;
use warnings;
use Getopt::Long;


# declare the perl command line flags/options we want to allow
my %options=();

my $group_field = undef;
my $sum_field = undef;

my $result = GetOptions(
    "groupBy=s" => \$group_field,
    "fieldToSum=s" => \$sum_field,
) or die $!;

die "Usage: $0 [--groupBy groupfield] --fieldToSum sumfield < input  > counts" if !defined($sum_field);
my %counts = ();

while(<STDIN>) {
    my $seg_info = &read_key_val_pairs($_);
   
#    foreach my $k (sort keys %$seg_info) {
#	print "$k ".$seg_info->{$k}."\n";
#    }
    my $n = &get_required($seg_info, $sum_field);
    my $group="";
    if(defined($group_field)) {
	$group = &get_required($seg_info, $group_field);
    }
    $counts{$group}+=$n;
}

foreach my $group (sort keys %counts) {
    print "$group\t".$counts{$group}."\n";
}

sub get_required {
    my ($hash, $key) = @_;
    die "Required key not found: $key" if !defined($hash->{$key});
    return $hash->{$key};
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
    my @tokens = split /\s+/, $s;
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
