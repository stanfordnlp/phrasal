Copyright© 2006 by BBN Technologies and University of Maryland (UMD)

BBN and UMD grant a nonexclusive, source code, royalty-free right to
use this Software known as Translation Error Rate COMpute (the
"Software") solely for research purposes. Provided, you must agree
to abide by the license and terms stated herein. Title to the
Software and its documentation and all applicable copyrights, trade
secrets, patents and other intellectual rights in it are and remain
with BBN and UMD and shall not be used, revealed, disclosed in
marketing or advertisement or any other activity not explicitly
permitted in writing.

BBN and UMD make no representation about suitability of this
Software for any purposes.  It is provided "AS IS" without express
or implied warranties including (but not limited to) all implied
warranties of merchantability or fitness for a particular purpose.
In no event shall BBN or UMD be liable for any special, indirect or
consequential damages whatsoever resulting from loss of use, data or
profits, whether in an action of contract, negligence or other
tortuous action, arising out of or in connection with the use or
performance of this Software.

Without limitation of the foregoing, user agrees to commit no act
which, directly or indirectly, would violate any U.S. law,
regulation, or treaty, or any other international treaty or
agreement to which the United States adheres or with which the
United States complies, relating to the export or re-export of any
commodities, software, or technical data.  This Software is licensed
to you on the condition that upon completion you will cease to use
the Software and, on request of BBN and UMD, will destroy copies of
the Software in your possession.                                                

-------------------------------------------------------------------

This tarball contains the following 4 Java classes, and their source code

TERtest - A very basic command line UI for the code.  It takes in two
files, one for the ref and one for the hyp.  It assumes one segment
per line, in the same order.  It calculates the TER for each line, one
at a time, and outputs to standard output.  It does use segment ids
(if present they will be considered words).  If this tool is meant as
a replacement for the TERCOM perl script, then a new class should be
written to replace TERtest

TERcalc - This does all of the work calculating TER.  All of the
methods are static, so no instantiations should ever be made.  To
calculate TER, call: TERcalc.TER(String a, String b).  This tokenizes
and then runs TER.  It returns a TERalignment object.  If the input is
pretokenized (or tokenized and then converted to another type, such as
Integer (which give better performance)), then you can call
TER(Comparable[], Comparable[]).  If you want to use a different
scoring module (see TERcost class), then use the functions
TER(Comparable[], Comparable[], TERcost) or TER(String, String,
TERcost).

TERshift - This is a tiny storage class, which stores the information
for indiviual shifts.

TERalignment - This contains the output of TER.  Some processing might
be needed to make it as pretty as TERcom pra output.

TERcost - This class is used to determine the cost of insertions,
deletions, substitutions, matches, and shifts.  The base class gives
all edits a cost of 1, and matches a cost of 0.  If a researcher wants
to experiment with alternative cost functions, or cost matrices, then
a new child class of TERcost should be made that calculates those cost
functions.  This can be passed in as the third arguement to the TER
function of TERcalc.  It should be noted that this version of the code
only does word to word cost matrices (not phrasal matrices), and that
all costs must be in the range of 0.0 to 1.0.

-----------

The tarball also contains two sample input files:

hyp.txt
ref.txt

Invoke the test code with:
 java TERtest hyp.txt ref.txt

This should give the same output as found in:
 output.txt

This code should be equivilent (in TER scoring) to running tercom_v6b with the flags:
-r ref.txt -h hyp.txt -N -s -b 20

Last update: 4/28/06
