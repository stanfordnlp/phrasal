#!/usr/bin/perl
use strict;
use warnings;


die "Usage: $0 field_num <phrtab  >extracted_field" if @ARGV !=1;

my $fieldNum = shift;


binmode(STDIN, ":utf8");
binmode(STDOUT, ":utf8");
binmode(STDERR, ":utf8");


while(<STDIN>) {
    s/\s+$//;
    my @fields = split /\|\|\|/, $_;
    die "Bad field num. Note that the field number is zero-based!" if !defined($fields[$fieldNum]); 
    my $selected = $fields[$fieldNum];
    print $selected."\n";
}
