#!/usr/bin/perl

while(<>) {
  chomp;
  @toks = split /\s+/;
  shift @toks;
  foreach $tok (@toks) {
     if ($tok =~ /\p{Upper}[^-]+-\p{Upper}[^-]+/) {
        $prefix = $tok;
        $prefix =~ s/-[^-]*$//;
        $cnt{$prefix}++;
        $lcp = lc($prefix);
        $map{$lcp}{$prefix} = 1; 
     }
  }
} 

foreach $key (keys %map) {
   $best = "";
   $best_cnt = -1;
   foreach $alt (keys %{$map{$key}}) {
      if ($cnt{$alt} > $best_cnt) {
         $best_cnt = $cnt{$alt};
         $best = $alt;  
      }
   } 
   print "$best\n";
}
