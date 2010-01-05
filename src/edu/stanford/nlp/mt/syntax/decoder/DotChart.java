package edu.stanford.nlp.mt.syntax.decoder;

import java.util.ArrayList;

import edu.stanford.nlp.mt.syntax.decoder.Bin.SuperItem;
import edu.stanford.nlp.mt.syntax.decoder.TMGrammar.TrieNode;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

//#################### DotChart class: 
/*purpose of DotChart: (1) do CKY parsing in an efficient way (i.e., identify the applicable rules fastly); (2) binarization on the fly; (3) remember the partial application of rules
*/

@SuppressWarnings("unchecked")
public class DotChart
{ 
	public Chart p_chart; //pointer the Chart that it associated with
	public TMGrammar p_grammar;//pointer to the grammar
	DotBin[][] l_dot_bins;//note that in some cell, it might be null
	int[] foreign_sent;
	int sent_len;
	
	public DotChart(int[] sent, TMGrammar gr, Chart chart)
	{
		//initialize the variables
		p_chart=chart;
		p_grammar=gr;
		foreign_sent=sent;
		sent_len = foreign_sent.length;
		l_dot_bins = new DotBin[sent_len][sent_len+1];
	}

	/*add intial dot items*/
	public void seed(){
		for(int j=0; j<=sent_len-1; j++){
			if(p_grammar.filter_span(j, j, sent_len))
				add_dot_item(p_grammar.get_root(), j,j, null, null);
		}	
	}
	
	public void expand_cell(int i, int j){
		//there are two kinds of symbols in the foreign side: (1) non-terminal (e.g., X or NP); (2) CN-side terminal
		//therefore, two ways to extend the dot postion
		//(1) if the dot is just to the left of a non-terminal variable, looking for theorems or axioms in the Chart that may apply and extend the dot pos
		for(int k=i+1; k<j; k++)
			extend_dotitems(i,k,j,false);
		
		//(2)the dot-item is looking for a CN-side terminal symbol: so we just need a CN-side terminal symbol to advance the dot
		//note: this will deal with the seeding case (translation of terminal cn words)
		//also deal with case in starting dotitems which start from a terminal, see start_dotitems
		int last_word=foreign_sent[j-1];
		
		//in seeding case: j=i+1, therefore, it will look for l_dot_bins[i][i]
		if(l_dot_bins[i][j-1]!=null)
			for(DotItem dt: l_dot_bins[i][j-1].l_dot_items){//dotitem in dot_bins[i][k]: looking for an item in the right to the dot			
				TrieNode child_tnode = dt.tnode.match_symbol(last_word);//match the terminal
				if (child_tnode!=null){				
					add_dot_item(child_tnode, i, j, dt.l_ant_super_items, null);//we do not have an ant for the terminal
				}						
			}	
	}

	//note: (i,j) is a non-terminal, this cannot be a cn-side terminal, which have been handled in case2 of dotchart.expand_cell
	//zero-width dot-item
	public void start_dotitems(int i, int j){
		extend_dotitems(i,i,j,true);
	}
	
	//looking for theorems or axioms in the "Chart" that may apply and extend the dot pos
	private void extend_dotitems(int i, int k, int j, boolean start_dotitems){
		if(l_dot_bins[i][k]==null || p_chart.l_bins[k][j]==null)
			return;
		
		for(DotItem dt : l_dot_bins[i][k].l_dot_items){//dotitem in dot_bins[i][k]: looking for an item in the right to the dot
			ArrayList<SuperItem> t_ArrayList = new ArrayList(p_chart.l_bins[k][j].get_sorted_super_items().values());
			//Support.write_log_line(String.format("Add a dotitem with"), Support.DEBUG);
			for(SuperItem s_t : t_ArrayList){//see if it is match what the dotitem is looking for
				TrieNode child_tnode = dt.tnode.match_symbol(s_t.lhs);//match rule and complete part
				if (child_tnode!=null){
					if(start_dotitems==true && child_tnode.is_no_child_trienodes())continue;//TODO: byg
					//Support.write_log_line(String.format("Add a dotitem with superitem.lhs= %s",s_t.lhs), Support.DEBUG);
					add_dot_item(child_tnode, i, j, dt.l_ant_super_items, s_t);
				}
			}			
		}
	}
	
	private void add_dot_item(TrieNode tnode, int i, int j,  ArrayList<SuperItem> ant_s_items_in, SuperItem cur_s_item){
		ArrayList<SuperItem> ant_s_items= new ArrayList<SuperItem>();
		if(ant_s_items_in!=null)
			ant_s_items.addAll(ant_s_items_in);
		if(cur_s_item!=null)
			ant_s_items.add(cur_s_item);
		
		DotItem tem = new DotItem(i, j, tnode, ant_s_items);
		if(l_dot_bins[i][j]==null)
			l_dot_bins[i][j]=new DotBin();
		l_dot_bins[i][j].add_dot_item(tem);
		p_chart.n_dotitem_added++;
		//Support.write_log_line(String.format("Add a dotitem in cell (%d, %d), n_dotitem=%d",i,j,p_chart.n_dotitem_added), Support.DEBUG);
	}	

//	#################### DotBin class
	/*Bin is a cell in parsing terminology*/
	public class DotBin
	{	
		public ArrayList<DotItem> l_dot_items=new ArrayList<DotItem>();
		public void add_dot_item(DotItem dt){			
			/*if(l_dot_items==null)
				l_dot_items= new ArrayList<DotItem>();*/
			l_dot_items.add(dt);			
		}
	}
	
//	#################### DotItem class
	//remember the dot position in which a rule has been applied so far, and remember the old complete items
	public class DotItem
	{
		int i, j; //start and end position in the chart
		TrieNode tnode=null;//dot_position, point to grammar trie node, this is the only place that the DotChart points to the grammar
		ArrayList<SuperItem> l_ant_super_items=null; //pointer to SuperItem in Chart

		public DotItem(int i_in, int j_in, TrieNode tnode_in, ArrayList<SuperItem> ant_super_items_in){
			i = i_in;
			j = j_in;
			tnode = tnode_in;
			l_ant_super_items = ant_super_items_in;
		}
		
	}
}