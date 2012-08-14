#!/usr/bin/env python
#
# Copies target translations to a maise directory.
#
#
import sys
from argparse import ArgumentParser
from os.path import basename
from shutil import copy2


def copy_files(project_name, outdir, extension, file_list):
    """

    Args:
    Returns:
    Raises:
    """
    for infile in file_list:
        filename = basename(infile)
        user_id = infile[0:filename.find('.')]
        outfile =  '%s/%s.%s.%s' % (outdir, project_name, extension, user_id)
        copy2(infile, outfile)
        print 'Copying:',infile
        print '   to:',outfile


def main():
    desc='Copies target translations to a maise directory.'
    parser = ArgumentParser(description=desc)
    parser.add_argument('project_name',
                        help='Human evaluation project name.')
    parser.add_argument('output_dir',
                        help='Output directory.')
    parser.add_argument('lang_extension',
                        help='Maise language extension (e.g. en-ar)')
    parser.add_argument('tgt_files',
                        nargs='+',
                        help='Target translation files.')
    args = parser.parse_args()

    copy_files(args.project_name,
               args.output_dir,
               args.lang_extension,
               args.tgt_files)
    
if __name__ == '__main__':
    main()
