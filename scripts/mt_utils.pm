#!/usr/bin/perl

###################################################
# Functions to handle Moses phrase tables, MT
# SGML/XML files, etc.
###################################################

package mt_utils;

use strict;
use warnings;
use utf8;

use Fatal qw(open close);
use POSIX qw(assert);
use Exporter;
use List::Util qw(min max);
use vars qw($VERSION @ISA @EXPORT @EXPORT_OK %EXPORT_TAGS);

$VERSION     = 1.00;
@ISA         = qw(Exporter);
@EXPORT      = ();
@EXPORT_OK   = qw(&load_ptable &load_ptable_fh &dump_ptable &load_phrases &filter_ptable 
                  &remove_unreachable_phrases &load_mt_sgml &load_ibm_doc_scores &add_feature);

# Load phrase pairs, alignment, and scores from a phrase table:
sub load_ptable {
	my ($file,%opts) = @_;
	my $fh;
  die "Can't open: $file\n" unless -f $file;
	if($file =~ /\.gz$/) { open($fh,"zcat $file |") } 
	else { open($fh,$file); }
	my ($ptable,$sz) = load_ptable_fh($fh,%opts);
	#close($fh);
	return ($ptable,$sz);
}

# Load phrase pairs, alignment, and scores from a phrase table:
sub load_ptable_fh {
	my ($fh,%opts) = @_;
	my $align = $opts{align};
	my $last = $opts{last} || -1;
	my %ptable;
	binmode($fh,":utf8");
	my $sz = -1;
	my $i=0;
	while(<$fh>) {
		chomp;
		print STDERR "$i...\n" if(++$i % 100000 == 0);
		last if($last == $i);
		my ($f,$e,@els) = split(/ \|\|\| /);
		my $feat;
		my $id;
		my ($al1,$al2);
		if($align) {
			$feat = $els[2];
			($al1,$al2) = ($els[0],$els[1]);
		} else {
			$feat = $els[0];
		}
		my @feat = split(/\s+/,$feat);
		$ptable{$f}{$e} = [\@feat, $al1, $al2];
		my $lsz = scalar @feat;
		if($sz == -1) { $sz = $lsz } else { assert($sz == $lsz) }
	}
	print STDERR "read $i phrase pairs\n";
	return (\%ptable,$sz);
}

# Add feature to phrase table:
sub add_feature {
	my ($ptable, $val) = @_;
	foreach my $p (values %$ptable) {
		foreach my $v (values %$p) {
			push @{$v->[0]}, $val;
		}
	}
}

# Print phrase table:
sub dump_ptable {
	my ($ptable,%opts) = @_;
	my $index = $opts{index};
	my $rindex = $opts{rindex};
	my $first = defined $opts{first} ? $opts{first} : -1;
	my $size = defined $opts{size} ? $opts{size} : -1;
	my $last = $first+$size-1;
	my $fh = $opts{fh} || *STDOUT;
	my $align = $opts{align};
	print STDERR "first: $first last: $last\n";
	my ($f,$p);
	while (($f,$p) = each %{$ptable}) {
		my ($e,$v);
		while (($e,$v) = each %$p) {
			next unless defined $v;
			my $scores; 
			my $v2 = $v->[0];
	    if($first >= 0) {
				$scores = join(' ',@{$v2}[$first..$last]);
			} else {
				$scores = join(' ',@$v2);
			}
			if($align) {
				print $fh "$f ||| $e ||| $v->[1] ||| $v->[2] ||| $scores\n";
			} else {
				print $fh "$f ||| $e ||| $scores\n";
			}
		}
	}
}

# Remove low-probability phrases:
sub filter_ptable {
	my ($ptable,$pos,$minp,%opts) = @_;
	my $n = $opts{n} || 20; # value N
	print STDERR "filtering using feature $pos. minimum score: $minp\n";
	my ($total,$deleted) = (0,0);
	my ($f,$p);
	while (($f,$p) = each %{$ptable}) {
		my $options = scalar keys %$p;
		$total += $options;
		next if($options <= $n);
		my @del;
		my ($e,$v);
		while (($e,$v) = each %$p) {
			if($v->[0][$pos] < $minp) {
				$p->{$e} = undef;
				++$deleted;
			}
		}
	}
	print STDERR "deleted $deleted/$total phrase pairs.\n";
}


# Remove all entries of the phrase table that are unreachable
# if the decoder allows no more than N translations for each input phrase:
sub remove_unreachable_phrases {
	my ($ptable,%opts) = @_;
	my $minw = 1e-3; # min feature weight (unless something weird happended during MERT)
	my $maxw = 1;    # max feature weight
	my $n = $opts{n} || 20; # value N
	my ($f,$p);
	my $i=0;
	my $fcount = scalar keys %{$ptable};
	while (($f,$p) = each %{$ptable}) {
		++$i;
		my @w;
		my %reachable;

		# If too few translation options, don't prune:
		my $total = scalar keys %$p;
		if($total <= $n) {
			print STDERR "$i/$fcount\t$f: erased 0/$total unreachable translations.\n";
			next;
		}

		# Take log:
		my (@e,@v);
		{
			my ($e,$v);
			while (($e,$v) = each %$p) {
				next unless defined $v;
				push @e, $e;
				push @v, [slog($v->[0][0]), slog($v->[0][1]), slog($v->[0][2]), slog($v->[0][3])];
			}
		}

		# Find all reachable translations:
		for($w[0] = $minw; $w[0] <= $maxw; $w[0] += $maxw-$minw) {
			for($w[1] = $minw; $w[1] <= $maxw; $w[1] += $maxw-$minw) {
				for($w[2] = $minw; $w[2] <= $maxw; $w[2] += $maxw-$minw) {
					for($w[3] = $minw; $w[3] <= $maxw; $w[3] += $maxw-$minw) {
						if($w[0] > $minw || $w[1] > $minw || $w[2] > $minw || $w[3] > $minw) {
							my @nbest;
							foreach my $v (@v) {
								push @nbest, $w[0]*$v->[0] + $w[1]*$v->[1] + $w[2]*$v->[2] + $w[3]*$v->[3];
							}
							my $k=0;
							foreach my $goodi (sort {$nbest[$b] <=> $nbest[$a]} 0..$#nbest) {
								my $good = $e[$goodi];
								$reachable{$good} = 1;
								last if ++$k == $n;
							}
						}
					}
				}
			}
		}
	
		# Erase unreachable translations:
		my $erased = 0;
		foreach my $e (keys %{$p}) {
			if(!$reachable{$e}) {
				$p->{$e} = undef;
				++$erased;
			}
		}
		print STDERR "$i/$fcount\t$f: erased $erased/$total unreachable translations.\n";
	}
}

# Extracts all phrases from a given text file:
sub load_phrases {
	my ($txt,%opts) = @_;
	my $maxLen = $opts{maxLen} || 7;
	my $fh;
	my %phrases;
	open($fh,$txt);
	binmode($fh,":utf8");
	while(<$fh>) {
		s/^\s*//;
		s/\s*$//;
		my @w = split(/\s+/);
		for(my $i=0; $i<=$#w; ++$i) {
			my $m = min($#w,$i+$maxLen-1);
			for(my $j=$i; $j<=$m; ++$j) {
				my $sent = join(' ',@w[$i..$j]);
				$phrases{$sent} = 1;
			}
		}
	}
	close($fh);
	return \%phrases;
}

# Load MT SGML or XML file:
sub load_mt_sgml {
	my ($file,%args) = @_;
	my $fdocs = $args{docs};
	my (@segs,%segs,%docs,$fh);
	open($fh,$file) || die "Can't open: $file\n";
	binmode($fh,":utf8");
	my $docid = '';
	while(<$fh>) {
		if(/<doc docid=(\S+)/i) { 
			$docid = $1; 
			$docid =~ s/[">]//g;
		}
		elsif(/<seg id=(\S+)>(.*)<\/seg>/i) {
			if(!defined $fdocs || $fdocs->{$docid}) {
				my ($segid,$txt) = ($1,$2);
				$segid =~ s/"//g;
				if($segid =~ s/^\d+s/s/) { $segid =~ s/000//g }
				$docs{$docid} ||= scalar @segs;
				if(defined $segs{$docid}{$segid}) {
					push @{$segs[$segs{$docid}{$segid}][2]}, $txt;
				} else {
					push @segs, [$docid, $segid, [$txt]]; 
					$segs{$docid}{$segid} = $#segs;
				}
			} else {
				print STDERR "skipping document: $docid\n" if $args{verbose};
			}
		}
	}
	close($fh);
	if($args{verbose}) {
		foreach my $i (0..$#segs) {
			my $s = $segs[$i];
			print "docid=$s->[0] segid=$s->[1]:\n";
			foreach my $j (0..$#{$s->[2]}) {
				print "trans_$j: ".$s->[2][$j]." ";
			}
			print "\n";
		}
	}
	return (\@segs,\%segs,\%docs);
}

# Load 6-column score file produced by IBM scripts:
sub load_ibm_doc_scores {
	my ($file,%opts) = @_;
	my @scores;
	open(F,$file) || die "Can't open: $file\n";
	my $tail = 0;
	<F>;
	while(<F>) {
		 $tail = 1 if(/\-{20}/);
	   if(/^\s*\*?\s*(\d+):\s+(\S+)\s+(\S+)\s+(\S+)\s+(\S+)/) {
		 		my %attr = ( rank => $1, id => $2, ter => $3, bleu => $4, score => $5, tail => $tail );
				push @scores, \%attr;	
				print STDERR "id=$1 doc=$2 ter=$3 bleu=$4 score=$5\n" if $opts{verbose};
		 }
	}
	close(F);
	return \@scores;
}

# Prevent log(0):
sub slog {
	return -100 if $_[0] < 1e-40;
	return log($_[0]);
}

1;
