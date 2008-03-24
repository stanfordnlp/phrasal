/u/apache/htdocs/local/acl08dd/discrim_decoding.pdf: discrim_decoding.pdf
	cp discrim_decoding.pdf /u/apache/htdocs/local/acl08dd

discrim_decoding.pdf: discrim_decoding.dvi
	dvipdfm discrim_decoding

discrim_decoding.bbl: discrim_decoding.bib discrim_decoding.tex
	latex discrim_decoding
	bibtex discrim_decoding
 
discrim_decoding.dvi: discrim_decoding.tex discrim_decoding.bbl
	latex discrim_decoding


