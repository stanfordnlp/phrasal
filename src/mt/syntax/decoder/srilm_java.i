%module srilm
%{
/* Include the header files etc. here */
#include "Ngram.h"
#include "Vocab.h"
#include "Prob.h"
#include "srilm.h"
%}

%include carrays.i
%array_functions(unsigned,unsigned_array);

%include srilm.h


