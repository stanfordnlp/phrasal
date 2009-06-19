#!/usr/bin/perl -w

## Draws an alignment grid, given files that are sets of 4 lines:
##   chinese
##   english
##   giza alignment
##   blank (ignored)

while(<>) {
    chomp;
    $ch = $_;
    $en = <>;
    chomp $en;
    $a = <>;
    chomp $en;
    <>;

    # init matrix
    @matrix = ();
    

    @chwords = split(/\s+/, $ch);
    @enwords = split(/\s+/, $en);
    @align = split(/\s+/, $a);
    
    ## parse alignment ##
    foreach $a (@align) {
        $a =~ /(\d+)\-(\d+)/;
        $chidx = $1;
        $enidx = $2;
        $matrix[$chidx][$enidx] = 1;
    }
    ## end of parsing alignment ##
    
    print "\n";
    print " ";
    for ($c=0; $c < $#chwords+1; $c++) {
        if ($c<10) {
            print " ";
        }
        print $c;
	if ($c < 100) {
          print " ";
        }
    }
    print "\n";
    print "+";
    for ($i= 0; $i < 3*($#chwords+1); $i++) {
        print "-";
    }
    print "+\n";
    
    for ($e=0; $e < $#enwords+1; $e++) {
        print "|";
        for ($c=0; $c < $#chwords+1; $c++) {
	    # print " ";
            if (defined $matrix[$c][$e] && $matrix[$c][$e] == 1) {
                print "##";
            } else {
                print " -";
            }
            print " ";
        }
        print "| $enwords[$e]-$e\n";
    }

    print "+";
    for ($i = 0; $i < 3*($#chwords+1); $i++) {
        print "-";
    }
    print "+\n";

    print " ";
    for ($c=0; $c < $#chwords+1; $c++) {
        if ($c<10) {
            print " ";
        }
        print $c;
	if ($c < 100) {
          print " ";
        }
    }
    print "\n";
    for ($c=0; $c < $#chwords+1; $c++) {
        print "$chwords[$c]-$c ";
    }
    print "\n";
    
}
