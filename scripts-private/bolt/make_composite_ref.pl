use strict;
use warnings;

die "Usage: $0 root_dir component_names output_ref_prefix" if @ARGV != 3;

my ($root, $components, $out_prefix) = @ARGV;

my @c = split /,/, $components;

my @ref_lists = map {[glob("$root/$_/ref*")]} @c;


my $i=0;
my $done=0;
while(!$done) {
    $done = 1;
    open O, ">${out_prefix}$i" or die $!;
    for(my $j=0; $j<scalar(@ref_lists); $j++) {
	my $n_refs = scalar(@{$ref_lists[$j]});
	my $index = $i % $n_refs;
	&write(*O, $ref_lists[$j]->[$index]);
	$done = $done && ((($i+1)% $n_refs)==0);
    }
    close O;
    $i++;
}

sub write {
    my ($fh, $file) = @_;
    open IN, $file or die $!;
    while(<IN>) { print $fh $_; }
    close IN;
}
