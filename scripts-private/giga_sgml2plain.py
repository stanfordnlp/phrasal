#!/usr/bin/env python
#
# Extract *all* text from Gigaword SGML format. Crucially,
# this script fixes line breaking inside <P></P> blocks.
#
# Requires: BeautifulSoup (bs4), which is easy to install:
#
#    pip install bs4
#
# author: Spence Green
#
import sys
import codecs
import re
from bs4 import BeautifulSoup

sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

def sentence_split(input_text):
    input_text = "<root>" + input_text + "</root>"
    soup = BeautifulSoup(input_text)
    paragraphs = []
    for doc in soup.find('root').find_all('doc'):
        if doc['type'] == 'story':
            headlines = doc('headline')
            for h in headlines:
                paragraphs.append(h.contents[0])
            datelines = doc('dateline')
            for d in datelines:
                paragraphs.append(d.contents[0])
            p_blocks = doc.find('text').find_all('p')
            for p in p_blocks:
                paragraphs.append(p.contents[0])
        elif doc['type'] == 'multi':
            paragraphs.append(doc.find('text').contents[0])

    sentences = [re.sub('\s+',' ',x.strip()) for x in paragraphs]
    return sentences


def main():
    if not sys.stdin.isatty():
        input_text = sys.stdin.read()

    sentences = sentence_split(input_text)
    for sent in sentences:
        print sent

if __name__ == '__main__':
    main()
