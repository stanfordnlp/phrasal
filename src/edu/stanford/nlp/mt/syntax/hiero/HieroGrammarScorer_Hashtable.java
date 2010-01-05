package edu.stanford.nlp.mt.syntax.hiero;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

@SuppressWarnings("unchecked")
public class HieroGrammarScorer_Hashtable {
	public static int NUM_FEATS=4;//P(e|f),P(f|e), P_lex(e|f) , P_lex(f|e)	
	HashMap p_gram = new HashMap();
	
	public HieroGrammarScorer_Hashtable( int num_feats){
		NUM_FEATS=num_feats;
	}
	
	public void add_raw_rule(Rule rl){
		String sig = rl.get_signature();
		Rule old_rule = (Rule) p_gram.get(sig);
		if(old_rule!=null){
			for(int i=0; i< rl.feat_scores.length; i++)
				old_rule.feat_scores[i] += rl.feat_scores[i];
		}else{
			p_gram.put(sig, rl);
		}
			
	}
	
	public void score_grammar(){
		//######first get the sum weights
		HashMap sum_fr = new HashMap();
		HashMap sum_eng = new HashMap();
		HashMap xsum = new HashMap();//for nonterminals
		
		Iterator rules = p_gram.values().iterator();
		while(rules.hasNext()) {
			Rule rl = (Rule)rules.next();
			float weight = rl.feat_scores[0];
			String fr_sig = rl.get_fr_signature();
			String eng_sig = rl.get_eng_signature();
			//for french
			Float old_w = (Float)sum_fr.get(fr_sig);
			if(old_w==null)
				sum_fr.put(fr_sig,weight);
			else
				sum_fr.put(fr_sig,old_w+weight);
			//for english
			old_w = (Float)sum_eng.get(eng_sig);
			if(old_w==null)
				sum_eng.put(eng_sig,weight);
			else
				sum_eng.put(eng_sig,old_w+weight);
			//for lhs
			old_w = (Float)xsum.get(rl.lhs);
			if(old_w==null)
				xsum.put(rl.lhs,weight);
			else
				xsum.put(rl.lhs,old_w+weight);
		}
		
		//######now normalize the rules
		int num_infinite=0;
		rules = p_gram.values().iterator();
		while(rules.hasNext()) {
			Rule rl = (Rule)rules.next();
			float fr_sum = (Float)sum_fr.get(rl.get_fr_signature());
			float eng_sum = (Float)sum_eng.get(rl.get_eng_signature());
			float x_sum = (Float)xsum.get(rl.lhs);	
			float[] new_scores = new float[NUM_FEATS+1];
			float weight = rl.feat_scores[0];
			if(weight == 0.0f)
				continue;
			new_scores[0] = -(float)Math.log10(weight/x_sum);//P(e,f|lhs)
			new_scores[1] = -(float)Math.log10(weight/eng_sum);//P(f|e)
			new_scores[2] = -(float)Math.log10(weight/fr_sum);//P(e|f)			
			if(NUM_FEATS==4){
				new_scores[3] = -(float)Math.log10(rl.feat_scores[1]/weight);//weighted avg
				new_scores[4] = -(float)Math.log10(rl.feat_scores[2]/weight);//weighted avg
				if( Float.isInfinite(new_scores[3]) || Float.isInfinite(new_scores[4])) num_infinite++;
			}
			rl.feat_scores = new_scores;
			//System.out.println("f_sum " + fr_sum + " e_sum " + eng_sum + " old: "+rl.feat_scores + " new " + new_scores);
			rl.print_info();
		}
		System.out.println("invalid is "+num_infinite +"; number of unique rules are " +p_gram.size());
		//dump the rules??
		
	}
		
	public static class Rule{
		//Rule formate: [Phrase] ||| french ||| english ||| feature scores
		public int lhs;//tag of this rule, state to upper layer
		public int[] french;
		public int[] english;		 
		public float[] feat_scores;//the feature scores for this rule
		public ArrayList alignments;
		
		public Rule(int lhs_in, int[] fr_in, int[] eng_in){
			lhs=lhs_in;
			french = fr_in;
			english = eng_in;			
		}			
				
		public String get_signature(){//lhs, french, and english
			StringBuffer res = new StringBuffer();			
			res.append(lhs);
			res.append(" ");
			for(int i=0; i<french.length; i++){
				res.append(french[i]);
				res.append(" ");
			}
			for(int i=0; i<english.length; i++){
				res.append(english[i]);
				res.append(" ");
			}			
			return res.toString();
		}
		
		public String get_fr_signature(){//lhs, french, and english
			StringBuffer res = new StringBuffer();					
			for(int i=0; i<french.length; i++){
				res.append(french[i]);
				res.append(" ");
			}			
			return res.toString();
		}
		
		public String get_eng_signature(){//lhs, french, and english
			StringBuffer res = new StringBuffer();					
			for(int i=0; i<english.length; i++){
				res.append(english[i]);
				res.append(" ");
			}			
			return res.toString();
		}		
		
		public void print_info(){
			//Support.write_log("Rule is: "+ lhs + " ||| " + Support.arrayToString(french, " ") + " ||| " + Support.arrayToString(english, " ") + " |||", level);
			System.out.print("Rule is: "+ edu.stanford.nlp.mt.syntax.decoder.Symbol.get_string(lhs) +  " ||| ");
			for(int i=0; i<french.length;i++)
				 System.out.print(edu.stanford.nlp.mt.syntax.decoder.Symbol.get_string(french[i]) +" ");
			System.out.print("||| ");
			for(int i=0; i<english.length;i++)
				 System.out.print(edu.stanford.nlp.mt.syntax.decoder.Symbol.get_string(english[i]) +" ");
			System.out.print("||| ");
			for(int i=0; i< feat_scores.length; i++)
				System.out.print(" " + feat_scores[i]);
			System.out.print("\n");
		}
	}	
}
