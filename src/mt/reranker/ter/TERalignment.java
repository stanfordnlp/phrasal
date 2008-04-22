package mt.reranker.ter;

/*
                                                                
Copyright 2006 by BBN Technologies and University of Maryland (UMD)

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

TERalignment.java v1
Matthew Snover (snover@cs.umd.edu)                           

*/



/* Storage Class for TER alignments */
public class TERalignment {
    public String toString() {
	String s = "Ref: " + join(" ", ref) + 
	    "\nHyp: " + join(" ", hyp) + 
	    "\nHyp': " + join(" ", aftershift);
	if (alignment != null) {
	    s += "\nAlignment: (";
	    for (int i = 0; i < alignment.length; i++) {s+=alignment[i];}
	    s += ")";
	}
	if (allshifts == null) {
	    s += "\nNumShifts: 0";
	} else {
	    s += "\nNumShifts: " + allshifts.length;
	    for (int i = 0; i < allshifts.length; i++) {
		s += "\n  " + allshifts[i];
	    }
	}
	s += "\nScore: " + this.score() + 
	    " (" + this.numEdits + "/" + this.numWords + ")";
	return s;
    }

    public double score() { 
	if (numWords <= 0.0) { return 0.0; } 
	return (double) numEdits / numWords;
    }    

    public static String join(String delim, Comparable[] arr) {
	if (arr == null) return "";
	if (delim == null) delim = new String("");
	String s = new String("");
	for (int i = 0; i < arr.length; i++) {
	    if (i == 0) { s += arr[i]; }
	    else { s += delim + arr[i]; }
	}
	return s;
    }

    public Comparable[] ref;
    public Comparable[] hyp;
    public Comparable[] aftershift;

    public TERshift[] allshifts = null;

    public double numEdits = 0;
    public double numWords = 0.0;
    public char[] alignment = null;
}
