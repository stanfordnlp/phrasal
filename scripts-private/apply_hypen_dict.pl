#!/usr/bin/perl

if ($#ARGV != 0) {
   print stderr "Usage:\n\t$0 (dict) < trans > trans.fixed_hypen_casing";
   exit -1;
}

open fh, "$ARGV[0]" or die;
while (<fh>) {
   chomp;
   $c = $_;
   $l = lc($c); 
   if ($dict{$l}) {
      next if ($c =~ /^[A-Z]+$/);
   }
   $dict{$l} = $c;
}
 
while (<STDIN>) {
  chomp;
  @toks = split /\s+/;
  $l = "";
  for $tok (@toks) {
   if ($tok =~ /-/) {
     $prefix = $tok;
     $suffix = $tok;
     $prefix =~ s/-[^-]*$//;
     $suffix =~ s/^.*-//g;
     $lcpre = lc($prefix);
     if ($dict{$lcpre}) {
        $prefix = $dict{$lcpre};
        $lcsuf = lc($suffix);
        if ($dict{$lcsuf}) {
           $suffix = $dict{$lcsuf};
        } else {
           $suffix = ucfirst($suffix);
        }
     }
     $tok = $prefix."-".$suffix;
   } 
   $l .= "$tok ";
  }
  $l =~ s/ +$//g;
  print "$l\n";
}
