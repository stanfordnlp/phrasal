#!/usr/bin/env python
# coding=utf-8
#
# Cleans weird stuff from raw text. Includes:
#
#   1. EOL normalization for the current platform (by default via
#      the python line reading function.
#   2. Whitespace normalization
#   3. Cleaning of *ML tags
#   4. Cleaning of bullets
#
# Author: Spence Green
#
import sys
import re
import codecs
from argparse import ArgumentParser
import HTMLParser
import string

# Important: DO NOT uses the codecs package, which splits
# on U+2028
# http://stackoverflow.com/questions/1105106/how-to-exclude-u2028-from-line-separators-in-python-when-reading-file
#sys.stdin = codecs.getreader('utf-8')(sys.stdin)

sys.stdout = codecs.getwriter('utf-8')(sys.stdout)
sys.stderr = codecs.getwriter('utf-8')(sys.stderr)

# When --latin is enabled, percentage of non-latin characters
# in a string before the line skipped.
latin_skip_threshold = 0.8

# Percentage of punctuation characters in a sentence before it is
# skipped
punct_skip_threshold = 0.8

# Match various forms of whitespace, including spaces and newline
# characters. Normalize to a single ASCII space character.
p_ws = re.compile(ur'[ \t\u00A0\u2000-\u200A\u2028\u2029\u000B\u000C\u0085\u3000]+')

# Sgml tags
p_tag = re.compile(ur'</?[^<>]*>')

# Bullets
p_bullet = re.compile(ur'[\*\u2022\u2023\u2043\u204c\u204d\u2219\u25c9\u25d8\u25e6\u2619\u2765\u2767\u29be\u29bf]+')

# Long sequences of underscore
p_under = re.compile(ur'_{3,}')

# Soft hyphens (for typesetting)
p_soft = re.compile(ur'\u00AD+')

# Various symbols (from PTBTokenizer)
p_symbol = re.compile(ur'[¦\u00A7¨\u00A9\u00AC\u00AE¯\u00B0-\u00B3\u00B4-\u00BA\u00D7\u00F7\u0387\u05BE\u05C0\u05C3\u05C6\u05F3\u05F4\u0600-\u0603\u0606-\u060A\u060C\u0614\u061B\u061E\u066A\u066D\u0703-\u070D\u07F6\u07F7\u07F8\u0964\u0965\u0E4F\u1FBD\u2016\u2017\u2020-\u2023\u2030-\u2038\u203B\u203E-\u2042\u2044\u207A-\u207F\u208A-\u208E\u2100-\u214F\u2190-\u21FF\u2200-\u2BFF\u3012\u30FB\uFF01-\uFF0F\uFF1A-\uFF20\uFF3B-\uFF40\uFF5B-\uFF65\uFF65]+')

# Heuristic match of HTML pages
p_html = re.compile(ur'(br|td|tr|doctype|abbr|head|title|html|img|href|body|frame|iframe|meta|script|span|div|style|cdata|attlist|vspace|hspace)', re.IGNORECASE)

# Heuristic match of SQL queries
p_sql = re.compile(ur'(select|insert|join|create|table|between|foreign|column|distinct|with|exists|add)', re.IGNORECASE)

# [SG|HT|X]ML escape characters
p_sgml = re.compile(ur'(&#?\w+;)', re.IGNORECASE)

# Anything outside of the Latin code charts
p_not_latin = re.compile(ur'[^\u0000-\u024f\u2c60-\u2c7f\ua720-\ua7ff\u1e00-\u1eff\ufb00-\ufb4f]')

# ASCII punctuation characters
p_punct = re.compile(ur'([%s])' % (re.escape(''.join(set(string.punctuation)))))

def clean_text(no_sql, no_html, latin_only):
    """ Apply heuristic text cleaning
    Args:
    Returns:
    Raises:
    """
    h = HTMLParser.HTMLParser()
    for line in sys.stdin:
        line = line.decode('utf-8').strip()
        # Heuristics for skipping lines
        if len(line) == 0:
            print
            continue
        if latin_only:
            m = p_not_latin.findall(line)
            perc = float(len(m)) / float(len(line))
            if perc > latin_skip_threshold:
                print
                continue
        if no_html:
            m = p_html.findall(line)
            if len(m) > 3:
                print
                continue
        if no_sql:
            m = p_sql.findall(line)
            if len(m) > 3:
                print
                continue
        
        # SGML escaping
        m = p_sgml.search(line)
        if m:
            line = h.unescape(line)
            
        # Character stripping rules
        line = p_tag.sub('', line)
        line = p_bullet.sub('', line)
        line = p_under.sub('', line)
        line = p_symbol.sub('', line)
        line = p_soft.sub('', line)

        # Check for too much punctuation
        m = p_punct.findall(line)
        perc = float(len(m)) / float(len(line))
        if perc > punct_skip_threshold:
            print
            continue
        else:
            # Cleanup whitespace and print the line
            line = p_ws.sub(' ', line)
            print line

def main():
    desc='Clean raw text'
    parser=ArgumentParser(description=desc)
    parser.add_argument('-q','--sql',
                        dest='no_sql',
                        action='store_true',
                        help='Strip SQL strings')
    parser.add_argument('-s','--sgml',
                        dest='no_html',
                        action='store_true',
                        help='Strip *ML (SGML,HTML,XML) strings')
    parser.add_argument('-l','--latin',
                        dest='latin_only',
                        action='store_true',
                        help='Strip strings with non-latin characters')
    
    args = parser.parse_args()
    
    clean_text(args.no_sql, args.no_html, args.latin_only)
    
if __name__ == '__main__':
    main()
