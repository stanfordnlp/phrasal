#!/usr/bin/perl
BEGIN { push @INC, "/u/mgalley/programming/perl-src/"; }

#####################################################
# Wrapper around 
#  /user/mgalley/mt/mt-scripts/generic/tercom_v6b.pl
# which generates a hyp/ref file pair from a set of
# references and an hypothesis file.
#
# Created by Michel Galley (mgalley@stanford.edu)
# on Thu 01 Nov 2007 04:58:01 PM PDT
# $Id$
#####################################################

use strict;
use POSIX;
use Fatal qw(open close);
use utf8;
binmode(STDIN,":utf8");
binmode(STDOUT,":utf8");
binmode(STDERR,":utf8");

use utils::opt  qw(&get_args &get_opts);
use utils::exec qw(&print_exec &get_exec &get_files_in_dir &sys &sysout);

my %opts = get_opts
  (['v',0,'program will be more verbose'],
	 ['sgm=s','','template sgm file with IDs'],
	 ['n=s',-1,'stop after n sentences']);
my $tmp=$ENV{TMPDIR} || '/tmp';
my %args = get_args('ref','hyp','target');
my $ref = $args{ref};
my $target = $args{target};
my $N = $opts{n};

# Load sgml file with IDs:
my @ids;
if($opts{sgm}) {
	open(S,$opts{sgm});
	my $id = '';
	while(<S>) {
		if(/<DOC docid="(.*?)"/) {
			$id = $1;
			next;
		}
		if(/^<seg/) {
			assert($id ne '');
			push @ids, $id;
		}
	}
	close(S);
	print "segs: ", scalar @ids, "\n";
}

# Find all refs:
my $i=0;
my @refs;
while(<$ref*>) {
	next unless /$ref(\d+)$/;
	print STDERR "opening: $ref$1\n";
	open my $f, $_;
	push @refs, $f;
	++$i;
}

# Read one sent at a time:
open(H,$args{hyp});
my $reftmp=`mktemp /tmp/refXXXXXX`; chomp $reftmp;
(my $hyptmp = $reftmp) =~ s/ref/hyp/;
print STDERR "temporary reference file: $reftmp\n";
print STDERR "temporary hypothesis file: $hyptmp\n";
assert($hyptmp ne $reftmp);
open(RO,">$reftmp");
open(HO,">$hyptmp");
my $si=1;
while(my $hsent = <H>) {
	chomp $hsent;
	my $id = $ids[$si-1] || 'ID'; 
	print HO "$hsent (${id}_$si)\n";
	foreach my $ref (@refs) {
		my $rsent = <$ref>;
		chomp $rsent;
		print RO "$rsent (${id}_$si)\n";
	}
	last if $N == ++$si;
}
close(RO);
close(HO);

# Compute TER:
print STDERR "hyp: $hyptmp ref: $reftmp\n";
sys("time java mt.reranker.ter.TERtest -h $hyptmp -r $reftmp -o sum -n $target", noprint => !$opts{v});

# Create target file:
sys("mv $target.sum $target", noprint => !$opts{v});

# Cleanup:
unlink $reftmp, $hyptmp;
