mert_smoothing.pdf: mert_smoothing.dvi
	dvipdfm mert_smoothing

mert_smoothing.bbl: mert_smoothing.bib mert_smoothing.tex
	latex mert_smoothing
	bibtex mert_smoothing
 
mert_smoothing.dvi: mert_smoothing.tex mert_smoothing.bbl
	latex mert_smoothing


