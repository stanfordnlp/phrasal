#!/usr/bin/perl

while(<>) {
 s/(\$[^\$]+\$)/\\mbox{\1}/g;
 print $_;
}
