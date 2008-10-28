#!/usr/bin/perl -w

@result = ();

while(<>) {
    chomp;
    ($auto, $hand) = split(/\t/);
    $auto =~ s/^\s+//;
    $auto =~ s/\s+$//;
    $hand =~ s/^\s+//;
    $hand =~ s/\s+$//;

    # Need to annotate
    if ($auto eq "flipped" or
        $auto =~ /^ordered/ or
        $auto =~ "undecided" or
        $auto =~ "fragmented") {
        if ($hand ne "") {
            if ($hand =~ /(.*) - .*/) {
                $fixed = $1;
            } else {
                $fixed = $hand;
            }
        } else {
            $fixed = $auto;
        }
    } else {
        $fixed = $auto;
    }


    # normalize $fixed
    if ($fixed =~ /A B \(.*\)/) {
        $fixed = "A B";
    }
    if ($fixed =~ /^no B/) {
        $fixed = "no B";
    }
    if ($fixed =~ /^other/) {
        $fixed = "other";
    }
    if ($fixed =~ /^relative clause/) {
        $fixed = "relative clause";
    }


    push @result, $fixed;
    
}

foreach $a (@result) {
    print $a,"\n";
}
