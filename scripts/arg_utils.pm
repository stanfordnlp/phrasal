#!/usr/bin/perl

package arg_utils; 

#####################################################
# Powerful command-line handler. 
#
# Created by Michel Galley (galley@cs.columbia.edu)
# on Wed Nov 30 07:20:24 2005
# $Id: opt.pm 232 2009-01-09 21:28:37Z galley $
#####################################################

use strict;
use POSIX;
use Fatal qw(open close);
use Exporter;
use Getopt::Long;
Getopt::Long::Configure ("bundling");
use vars qw(@params %opts @args $msg);
use vars qw($VERSION @ISA @EXPORT @EXPORT_OK %EXPORT_TAGS);

$VERSION     = 1.00;
@ISA         = qw(Exporter);
@EXPORT      = qw();
@EXPORT_OK   = qw( &get_opts &get_args &help );

# Check that the nb of arguments is correct:
sub get_args {
	@args = @_;
	if(scalar @ARGV < scalar @args) {
	   warn "Incorrect # of arguments: ",scalar @ARGV,
	        " (minimum of ",scalar @args," required)\n";
	   help();
	   exit(-1);
	}
	my $i=0;
	while($#args < $#ARGV) {
		push @args, (++$i);
		#push @args, "extra".(++$i);
	}
	my %args = map {($args[$_],$ARGV[$_])} 0..$#args;
	return %args;
}

# Return command-line arguments:
sub get_opts {
	@params = @_;
	my @valid;
	# Add help by default:
	push @params, add_help();
	# Set valid arguments:
	my %mandatory;
	foreach my $i (0..$#params) {
		my ($longn,$def,$help,$mand) = @{$params[$i]};
		push @valid, $longn;
		$longn =~ s/=.*//;
		$opts{$longn} = $def;
		$mandatory{$longn} = 1 if $mand;
	}
	# Get options:
	GetOptions(\%opts,@valid);
	# Fail if any of the needed params is missing:
	foreach my $k (keys %mandatory) {
		unless($opts{$k}) {
			warn "Argument '$k' is missing\n";
			help();
		}
	}
	help() if $opts{h};
	return %opts;
}

# Add help to valid parameters:
sub add_help {
	return ['h',0,'this help message'];
}

# Print help message:
sub help {
  print STDERR "Usage: $0 [OPTIONS] ",join(' ',@args),"\n";
  print STDERR $msg if $msg;
  print STDERR "where OPTIONS are...\n";
  my $maxlen = -1;
  my @print;
  foreach my $param (@params) {
  	  my ($flag,$def,$help) = @{$param};
	  $flag =~ s/=s$/ ARG/; 
	  my $sl = (length($flag) == 1) ? '-' : '--';
	  $flag = " $sl$flag";
	  $maxlen = length($flag) if(length($flag) > $maxlen);
	  $help = "$help [default: $def]" if $def;
	  push @print, [$flag,"$help\n"];
  }
  foreach my $print (@print) {
  	  my $flagstr = sprintf("%-${maxlen}s",$print->[0]);
	  print STDERR "$flagstr : $print->[1]";  
  }
  exit -1;
}

###### TEST #########

if(0) {
my %opts = get_opts(['foo1=s','bar1','this is a test message'], 
                    ['foo2=s','bar2','this is another test message']);
my %args = get_args('file1','file2');
print "foo1=$opts{foo1}\n";
print "foo2=$opts{foo2}\n";
print "file1=$args{file1}\n";
}

1;
