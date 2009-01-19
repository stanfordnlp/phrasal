#!/usr/bin/perl -w

$ta = 0;
$tp = 0;
$de = 0;
$de_contiguous = 0;
$de_fragmented = 0;
$skipped = 0;
$processed = 0;
%counter = ();

while(<>) {
    chomp;

    if (/Skip \d+/) {
        $skipped++;
    }
    if (/Processing  \d+/) {
        $processed++;
    }
    if (/\# valid translation alignment = (\d+)/) {
        $ta += $1;
    }
    if (/\# Tree Pairs = (\d+)/) {
        $tp += $1;
    }
    if (/\# NPs with DE = (\d+)/) {
        $de += $1;
    }
    if (/\# NPs with DE \(contiguous\)= (\d+)/) {
        $de_contiguous += $1;
    }
    if (/\# NPs with DE \(fragmented\)= (\d+)/) {
        $de_fragmented += $1;
    }

    # take the counter output
    if (/^{(.*)}/) {
        $dummy = $1;
        @list = split(", ",$dummy);
        foreach $item (@list) {
            $item =~ s/^\s+//;
            $item =~ s/\s+$//;
            $item =~ /^(.*)=(\d+)\.0$/ or die "format error: $item";
            $counter{$1} += $2;
        }
    }

}

print "Skipped = $skipped\n";
print "Processed = $processed\n";
print "# valid translation alignment = $ta\n";
print "# Tree Pairs = $tp\n";
print "# NPs with DE = $de\n";
print "# NPs with DE (contiguous)= $de_contiguous\n";
print "# NPs with DE (fragmented)= $de_fragmented\n";
foreach $k (keys %counter) {
    print "[type] $k = ".$counter{$k}."\n";
}
