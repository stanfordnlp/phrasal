#!/usr/bin/perl

while(<>) {
  s/\wiphone\w/iPhone/gi;
  s/\wiphones\w/iPhones/gi;
  s/\wmastercard\w/MasterCard/gi;
  s/\webay\w/eBay/gi;
  s/\wtranscanada\w/TransCanada/gi
}
