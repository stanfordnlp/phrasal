#!/usr/bin/env ruby 

## quick and dirty! first arg should be the sysid you want extracted,
## or nothing for all.

sysid = ARGV.shift

punc = '.,"()-'
skip = false

ARGF.each do |l|
  case l
  when /<doc .* sysid="(.*?)"/i
    skip = true unless sysid.nil? || sysid == $1
  when /<\/doc>/i
    skip = false
  when /<seg id=.*?> *(.*?) *<\/seg>/i
    next if skip
    text = $1
## not sure what this is for---some kind of limited english tokenization, it looks like
    puts text #.downcase.gsub(/(\S)'s/, '\1 \'s').gsub(/(\S)([#{punc}])/, '\1 \2').gsub(/([#{punc}])(\S)/, '\1 \2').gsub(/\s+/, " ")
  end
end
