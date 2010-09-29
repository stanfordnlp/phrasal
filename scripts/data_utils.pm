#!/usr/bin/perl

###################################################
# Load files and OSPL data.
# (i.e. (O)ne (S)entence (P)er (L)ine)
# The idea: several files with same root, e.g. foo.txt and foo.parsed
# that have the same number of lines. i-th line of foo.txt corresponds
# to i-th line of foo.parsed.
# 
# author: Michel Galley
###################################################

package data_utils;

use strict;
use warnings;
use utf8;
use Fatal qw(open close);
use POSIX;
use Exporter;
use vars qw($VERSION @ISA @EXPORT @EXPORT_OK %EXPORT_TAGS);

$VERSION     = 1.00;
@ISA         = qw(Exporter);
@EXPORT      = ();
@EXPORT_OK   = qw(&process_file &load_ospl &save &exists &getnames &load_file);
#%EXPORT_TAGS = ( DEFAULT => [qw(&load &getnames)],
#                 Both    => [qw(&load &save &exists)]);

###################################################
# Functions to load/process a given file:
###################################################

sub process_file {
	my ($filename,$func,$func_args,$args) = @_;
	open(F,$filename);
	binmode(F,":utf8");
	while(my $line = <F>) {
		print STDERR "processing line: $line" if $args->{v};
		&{$func}($line,$filename, %{$func_args});
	}
	close(F);
}

sub load_file {
	my ($file,%args) = @_;
	my @d;
  	open(F,$file) || die "Can't open: $file\n";
		binmode(F,":utf8");
  	while(<F>) {
		chop;
		if($args{tab})  { push @d, [split(/\t+/)] }
		elsif($args{s}) { push @d, [split(/\s+/)] }
		else            { push @d, $_ }
  	}
  	close(F);
	return (\@d,scalar @d);
}

###################################################
# OSPL functions:
###################################################

# Get all root names that can be loaded:
sub getnames {
	my ($args) = @_;
	my $prefix = $args->{prefix} || '';
	my $suffix = $args->{suffix} || '';
	my @names;
	while(my $file = <${prefix}*${suffix}.*>) {
		if($file =~ /^(.*?)\./) {
			my $root = $1;
			push @names, $root;
			#print "N: $root\n";
		}
	}
	return \@names;
}

# Load data from all $rootname.* files in a hash of tables:
sub load_ospl  { 
	my ($rootname,$args) = @_;
	my $v = $args->{verbose};
	my $skip = $args->{skip};
	my $only = $args->{only};
	my $space = $args->{space};
	my %only;
	%only = map {("$rootname.$_",1)} split(/,/,$only) if $only;
	print STDERR "Rootname: $rootname\n" if $v;
	print STDERR "Skipping: $rootname.$skip.*\n" if $v && $skip;
	print STDERR "Only: ",join(' ',keys %only),"\n" if $v;
	my %data;
	my $len = -1;
	my $lenname = '';
	while(my $file = <$rootname.*>) {
	  next if $skip && $file =~ /$rootname\.$skip/;
	  next if $only && !$only{$file};
	  print STDERR "Loading: $file\n" if $v;
	  (my $ext = $file) =~ s/^$rootname\.//;
	  open(F,$file);
	  while(<F>) {
	  	chop;
		if($space) { push @{$data{$ext}}, [split(/\s+/)] }
		else       { push @{$data{$ext}}, [split(/\t/)] }
	  }
	  close(F);
	  if($len < 0) {
	    $len = $#{$data{$ext}}+1;
		$lenname = $file;
	  } else {
	  	if($len != $#{$data{$ext}}+1) {
			warn "length mismatch: $len != ", 
			     $#{$data{$ext}}+1, 
				 "\t: $file\t<-> $lenname\n";
			assert($len == $#{$data{$ext}}+1) 
			  if $args->{fail};
		}
	  }
	}
	if($len < 0) {
		die "No file could be loaded: $rootname\n";
	}
	return (\%data,$len);
}

# Save data in a format similar as the once prduced
# by load_ospl() to multiple files:
sub save {
	my ($rootname,$data,$fields) = @_;
	foreach my $k (keys %{$data}) {
		if(defined $fields) {
			next unless defined $fields->{$k};
		}
		open(F,">$rootname.$k");
		foreach my $i (0..$#{$data->{$k}}) {
			if(defined $data->{$k}[$i]) {
				print F join("\t",@{$data->{$k}[$i]}), "\n";
			} else {
				print F "\n";
			}
		}
		close(F);
	}
}

# Returns true if there is at least one file matching 
# $rootname.*:
sub exists {
	my ($rootname) = @_;
	return 1 + scalar <$rootname.*>;
}

1;
