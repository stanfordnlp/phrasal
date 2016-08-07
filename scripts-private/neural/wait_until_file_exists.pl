use strict;
use warnings;


die "Usage: $0 file" if scalar(@ARGV) != 1;
my $file = shift @ARGV;

&waitUntilExists($file);

sub waitUntilExists {
    my ($file) = @_;
    print STDERR "Waiting for file '$file' to exist...";
    while(! -e $file) {
	sleep(10);
    }
    print STDERR " done.\n";
}
