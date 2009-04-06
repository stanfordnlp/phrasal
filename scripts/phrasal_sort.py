#!/usr/bin/env python

# phrasal_sort
#
# Author: Spence Green
######################
import sys
import re
import codecs
from getopt import getopt

prog_nym = 'phrasal_output_sort'
USAGE = prog_nym + ': <Phrasal translation list>\n   Output goes to <Phrasal translation list>.sorted\n'

def sort_translation_output(translation_file):
	line_index = re.compile('(\d+):', re.U)
	line_buffer = {}
	OUT_FILE = codecs.open(translation_file + '.sorted', 'w', encoding='utf-8')
	out_line_ctr = 0

	in_line_ctr = 1
	IN_FILE = codecs.open(translation_file, encoding='utf-8')
	for line in IN_FILE:
		m = line_index.match(line)
		if m:
			this_line = int(m.group(1))
			line_buffer[this_line] = line_index.sub('', line)
			for line_key in sorted(line_buffer.keys()):
				if line_key == out_line_ctr:
					OUT_FILE.write(line_buffer[line_key])
					del line_buffer[line_key]
					out_line_ctr = out_line_ctr + 1
				else:
					break
		else:
			print prog_nym + ': Not a multithreaded translation list. No sorting needed.\n'
			break
		in_line_ctr = in_line_ctr + 1

	OUT_FILE.close()
	IN_FILE.close()

def main():
	global USAGE
	try:
		opts, args = getopt(sys.argv[1:], '')
	except getopt.error, msg:
		print msg
		sys.exit(-1)

	if len(args) != 1:
		print USAGE
		sys.exit(-1)

	sort_translation_output(args[0])
	sys.exit(0)

if __name__ == '__main__':
	main()
