package edu.stanford.nlp.mt.syntax.util;


/*Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/


import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;

@SuppressWarnings("unchecked")
public class Tree {
	public Node root =null;

	public Tree(String tree_str){
		Stack t_stack = new Stack();
		root = new Node("fake_root");
		t_stack.push(root);

		for(int i=0; i<tree_str.length(); i++){
			if(tree_str.charAt(i)=='('){//start a tag
				int pos_space = tree_str.indexOf(' ', i+1);//pos of next space
				String tag = tree_str.substring(i+1, pos_space);

//				//add the tag into stack and update the parent
				Node t_node = new Node(tag);
				Node t_parent = (Node)t_stack.peek();
				t_parent.l_children.add(t_node);
				t_node.parent = t_parent;
				t_stack.push(t_node);

				//if((tree_str.charAt(pos_space+1)=='(') && (tag.compareTo("PU")!=0)){//start a new tag, go back to mainloop
				if((tree_str.charAt(pos_space+1)=='(') && (tree_str.charAt(pos_space+2)!=')')){//start a new tag, go back to mainloop
					i = pos_space;//i++ will be always performed
				}else{//the current parent is closed
					int pos_closing = tree_str.indexOf(')', pos_space+2);//pos of closing-bracket ")", skip 2 to avoid case like (PU ))
					String terminal_symbol = tree_str.substring(pos_space+1,pos_closing);

					i = pos_closing;//i++ will be always performed

					//remove the current tag from the stack and set is as pre-terminal
					Node t_node2=(Node)t_stack.pop();
					t_node2.set_as_pre_terminal(terminal_symbol);

					//check whether another tags are closed, clearly, a closing of tag will only be intialized by the event that a preterminal is closed
					i++;//either skip space or looking for another ")"
					while(i<tree_str.length() && tree_str.charAt(i)==')' ){//end of a tag
						t_stack.pop();
						i++;//either skip space or looking for another ")"
					}
				}
			}
		}
	}

	public void print_tree_terminals(BufferedWriter out){
		if(out==null)
			out = new BufferedWriter(new OutputStreamWriter(System.out));
		print_tree_terminals(root, out);
		FileUtility.write_lzf(out,"\n");
		FileUtility.flush_lzf(out);
	}

	private void print_tree_terminals(Node root, BufferedWriter out){
		if(root.terminal_symbol != ""){
			FileUtility.write_lzf(out,root.terminal_symbol+" ");
		}
		for(int i=0;i<root.l_children.size();i++){
			print_tree_terminals((Node)root.l_children.get(i),out);
		}
	}


	public void print_tree(BufferedWriter out){
		if(out==null)
			out = new BufferedWriter(new OutputStreamWriter(System.out));
		print_tree(root, out);
	}

	private void print_tree(Node root, BufferedWriter out){
		if(root.name.compareTo("fake_root")!=0){//not fake root
			//FileUtility.write_lzf(out,"("+root.name+" "+root.terminal_symbol);
			FileUtility.write_lzf(out,"("+root.name_after_reorder+" "+root.terminal_symbol);
		}
		for(int i=0;i<root.l_children.size();i++){
			print_tree((Node)root.l_children.get(i),out);
			if(i!=root.l_children.size()-1){
				FileUtility.write_lzf(out," ");
			}
		}
		if(root.name.compareTo("fake_root")!=0){
			FileUtility.write_lzf(out,")");
		}else{
			FileUtility.write_lzf(out,"\n");
		}
		FileUtility.flush_lzf(out);
	}

	public void print_tree_statistics(Node root){
		if(root.name.compareTo("fake_root")!=0){
			System.out.print("Name: " + root.name+"; "+"Terminal: " + root.terminal_symbol + "; "+"Children: " + root.l_children.size() +"\n");
		}else{
			System.out.print("\n------Tree Staistics are\n");
		}
		for(int i=0;i<root.l_children.size();i++){
			print_tree_statistics((Node)root.l_children.get(i));
		}
	}



//////////// for alignment,	begin
	private void update_min_max(int[] min_max, Vector t_v){
		for(int i=0; i< t_v.size(); i++){
			if( (Integer) t_v.get(i) <min_max[0]){
				min_max[0] = (Integer) t_v.get(i);
			}
			if( (Integer) t_v.get(i) > min_max[1]){
				min_max[1] = (Integer) t_v.get(i);
			}
		}
	}

	//each span is a vector with size 2: start pos and end pos
	//return a vector of vector (each of which is a span)
	private Vector union_of_spans(Vector v_of_spans, int total_len){
		if(v_of_spans==null || v_of_spans.size()<=0)
			return null;

		int[] buckets = new int[total_len];
		for(int i=0; i <total_len; i++)
			buckets[i]=-1;

		for(int i=0; i<v_of_spans.size();i++ ){
			Vector t_span = (Vector) v_of_spans.get(i);
			for(int j= (Integer)t_span.get(0); j<= (Integer)t_span.get(1); j++){
				buckets[j]=1;
			}
		}

		Vector res = new Vector();
		for(int i=0; i<total_len; i++){
			if(buckets[i]==1){
				Vector t_span = new Vector();
				res.add(t_span);

				t_span.add(Integer.valueOf(i));//begin
				while(i<total_len && buckets[i]==1){
					i++;
				}
				t_span.add(Integer.valueOf(i - 1));//end
			}
		}
		return res;
	}

	public void derive_complement_spans(int total_len){
		derive_complement_spans(root, total_len);
	}

	private void derive_complement_spans(Node root, int total_len){//total_len: len of target string
		//idea: my complement span is the union of: my parent's complement span + my siblings's span

		if(root.parent==null){//root nodes
			Vector t_res = new Vector();
			if( ((Integer)root.span.get(0)).intValue()>0){
				Vector t_v = new Vector();
				t_v.add(Integer.valueOf(0));
				t_v.add(Integer.valueOf(((Integer) root.span.get(0)).intValue() - 1));
				t_res.add(t_v);
			}
			if( ((Integer)root.span.get(1)).intValue()<total_len-1){
				Vector t_v = new Vector();
				t_v.add(Integer.valueOf(((Integer) root.span.get(1)).intValue()));
				t_v.add(Integer.valueOf(total_len - 1));
				t_res.add(t_v);
			}
			if(t_res.size()>0)
				root.complement_spans=t_res;
		}else{
			//get union of the spans of my siblings
			Vector v_of_spans = new Vector();
			if(root.parent.complement_spans!=null)
				v_of_spans.addAll(root.parent.complement_spans);//my parent's complement spans

			for(int i=0; i<root.parent.l_children.size();i++){
				Node t_n =(Node) root.parent.l_children.get(i);
				if(t_n!=root && t_n.span!=null){//exclude myself
					v_of_spans.add(t_n.span);
				}
			}
			root.complement_spans=union_of_spans(v_of_spans,total_len);
		}

		/*if(root.complement_spans!=null)
			System.out.println(root.name+": " +root.complement_spans.toString());*/

		//recursively find complement span for my chilren
		for(int i=0;i<root.l_children.size();i++){
			derive_complement_spans((Node)root.l_children.get(i),total_len);
		}
	}

	public void derive_span(Hashtable align_tbl){
		int[] terminal_pos = new int[1];
		derive_span(root, align_tbl,terminal_pos);
	}

	private void derive_span(Node root, Hashtable align_tbl, int[] terminal_pos){
		//idea: my span is the union of my chilren's spans

		//first get alignment of my chilren
		for(int i=0;i<root.l_children.size();i++){
			derive_span((Node)root.l_children.get(i),align_tbl,terminal_pos );
		}

		//find min.max
		int[] min_max = new int[2];
		min_max[0]=10000; //min
		min_max[1]=-1; //max

		//assembly the alignment from my children
		if(root.terminal_symbol!=""){//pre-terminal
			if(align_tbl.containsKey(Integer.valueOf(terminal_pos[0]))){//have link
				Vector t_v = (Vector)align_tbl.get(Integer.valueOf(terminal_pos[0]));
				update_min_max(min_max,t_v);
			}
			terminal_pos[0]++;
		}else{
			for(int i=0;i<root.l_children.size();i++){
				Vector span_child = ((Node)root.l_children.get(i)).span;
				if(span_child!=null){
					update_min_max(min_max,span_child);
				}
			}
		}
		if(min_max[0]!=10000 && min_max[1] !=-1){
			root.span = new Vector();
			root.span.add(Integer.valueOf(min_max[0]));//min
			root.span.add(Integer.valueOf(min_max[1]));//max
		}

		/*if(root.span!=null)
			System.out.println(root.name+": " +root.span.toString());*/
	}

	public void tag_frontier_node(){
		tag_frontier_node(root);
	}

	private void tag_frontier_node(Node root){
		root.set_frontier_flag();
		//System.out.println(root.name + "frontier: " + root.frontier_flag);
		for(int i=0; i< root.l_children.size();i++){
			Node n_child = (Node)root.l_children.get(i);
			tag_frontier_node(n_child);
		}
	}

	public void extract_rule(Hashtable rule_tbl, int len_tgt, BufferedWriter out){
		extract_rule(root,rule_tbl,len_tgt, out);
	}

	private void extract_rule(Node root,Hashtable rule_tbl, int len_tgt, BufferedWriter out ){
		root.derive_rule(rule_tbl, len_tgt, out);
		for(int i=0; i< root.l_children.size();i++){
			Node n_child = (Node)root.l_children.get(i);
			extract_rule(n_child,rule_tbl, len_tgt, out);
		}
	}

////////////for alignment,	end


	public static class Node {
		public String name="";
		public String name_after_reorder="";

		public Vector l_children = new Vector();
		public Node parent=null;
		//public int pos=-1;//what is my position in my parent
		public String terminal_symbol="";

		//with alignment information
		int terminal_id=-1; //if this is a pre-terminal, then remember the terminal id (start from 0)
		Vector span = null;//according to ISI's syntaxMT, this only consider the first/last word index in the english string, e.g., 1-3 or 10-10
		Vector complement_spans = null;//according to ISI's syntaxMT, it can be : 1-3,6-8,10-10
		boolean frontier_flag=false;

		public Node(){

		}

		public Node(String n){
			name=n;
			name_after_reorder=name;
		}

		public void set_as_pre_terminal(String symbol){
			terminal_symbol = symbol;
		}

		public void replace_contents_with(Node to){
			this.name=to.name;
			this.name_after_reorder=to.name_after_reorder;
			this.l_children=to.l_children;
			this.parent=to.parent;
			terminal_symbol=to.terminal_symbol;
		}


		/* return value
		 * 0: "AND"/"OR" name-matches-stage fail
		 * 1: "AND" name-matches-stage susscessful, need to do full-chilren match
		 * 2: "OR" name-matches-stage susscessful, means that for children match, we should return true as long as one match the pattern, do not consider the number of chilren
		 * */
		private int pattern_match(String from ,String to){
			if(from.matches("\\|.+")){
				String from2 = from.replaceFirst("\\|", "");
				if(to.compareTo(from2)==0)
					return 2;
			}else{
				if(from.compareTo("*")==0 || to.compareTo("*")==0)//anything will match
					return 1;
				else if(from.matches("\\!.+")){
					String from2 = from.replaceFirst("\\!", "");
					if(to.compareTo(from2)!=0)
						return 1;
				}else if(to.matches("\\!.+")){
					String to2 = to.replaceFirst("\\!", "");
					if(from.compareTo(to2)!=0)
						return 1;
				}else if(from.compareTo(to)==0){
					return 1;
				}
			}
			return 0;
		}

		public boolean is_subsume(Node from, Hashtable tag_tbl){//check whether the "from" is subsumed by myself, from may be taged by X
			//tag_tbl will remember how the node is taged according rule in "from"
			boolean res=true;
			String from_str = new String(from.name);
			String this_str = new String(this.name);
			if(from_str.matches("x\\d+\\:.+")){
				tag_tbl.put(from_str.substring(0, 2),this);//TODO: assume the d is between [0,9]
				//System.out.println("from_str: " + from_str + " Size: " +tag_tbl.size());
			}
			from_str = from_str.replaceAll("x\\d+\\:", "");//e.g., skip "x0:" in x0:NP

			//if(from_str.compareTo(this_str)!=0){//first, see whether the name match
			int res_match = pattern_match(from_str, this_str);
			if(res_match==0){//first, see whether the name match
				res=false;
				//tag_tbl.clear();
			}else if(res_match==1){//name match pass, need recursively "AND" chilren match
				//System.out.println("AND children match");
				if(from.l_children.size()>0){//if from has no children, then return true
					if(from.l_children.size()==this.l_children.size()){
						//System.out.println("chilren size:" + this.l_children.size());
						for(int i=0; i<from.l_children.size();i++){
							if(((Node)this.l_children.get(i)).is_subsume((Node) from.l_children.get(i),tag_tbl)==false){
								res=false;
								//tag_tbl.clear();
								break;
							}
						}
					}else{
						res=false;
						//tag_tbl.clear();
					}
				}
			}else if(res_match==2){//name match pass, need recursively "OR" chilren match
				//System.out.println("OR children match");
				boolean t_res=false;
				if(from.l_children.size()==1){//In "OR" condition, must have one and only one child
					for(int i=0; i<this.l_children.size();i++){
						/*note that we should not clear the tag_tbl if one of the chilren fails, that's why we delete tag_tbl.clear()*/
						if(((Node)this.l_children.get(i)).is_subsume((Node) from.l_children.get(0),tag_tbl)==true){//any sucess
							t_res=true;
							break;
						}
					}
				}
				if(t_res==false){
					res=false;
					//tag_tbl.clear();
				}
			}else{
				//this should not happen
			}
			//System.out.println("res: " + res + " size: " + tag_tbl.size());
			return res;
		}


		//########################## with alignment information
		public boolean set_frontier_flag(){
			if(span==null){//unaligned source node
				frontier_flag = false;
				return false;
			}else if(complement_spans==null || complement_spans.size()<=0){//span over all the english words
				frontier_flag=true;
				return true;
			}else{
				int span_start=((Integer)span.get(0)).intValue();
				int span_end=((Integer)span.get(1)).intValue();
				for(int i=0; i< complement_spans.size(); i++){
					Vector t_comp = (Vector) complement_spans.get(i);
					if( ( span_start >= ((Integer)t_comp.get(0)).intValue() && span_start <= ((Integer)t_comp.get(1)).intValue()) ||
						( span_end >= ((Integer)t_comp.get(0)).intValue() && span_end <= ((Integer)t_comp.get(1)).intValue()) ||
						( span_start <= ((Integer)t_comp.get(0)).intValue() && span_end >= ((Integer)t_comp.get(1)).intValue())//subsume
						){
						frontier_flag = false;
						return false;
					}
				}
				frontier_flag=true;
				return true;
			}
		}

	private void ctrl_derive_subrule(int[] x_id, String[] str_lhs, String[] v_rhs_frags, String[] v_rhs_unaligned){
		//System.out.print("(" + name + " ");
		str_lhs[0] += "(" + name + " ";
		for(int i=0; i<l_children.size(); i++){
			Node t_child = (Node) l_children.get(i);
			t_child.derive_subrule(x_id,str_lhs,v_rhs_frags, v_rhs_unaligned);
			if(i<l_children.size()-1){
				//System.out.print(" ");
				str_lhs[0] +=" ";
			}
		}
		//System.out.print(")");
		str_lhs[0] +=")";
	}

	private void add_rhs(String[] v_rhs_unaligned, int pos, String sym){
		if(v_rhs_unaligned[pos]==null)
			v_rhs_unaligned[pos] = sym;
		else
			v_rhs_unaligned[pos] +=" "+ sym;
	}

	private void derive_subrule(int[] x_id,String[] str_lhs, String[] v_rhs_frags, String[] v_rhs_unaligned){
		if(this.frontier_flag==true){//note: pre-terminal can be frontier node
			//System.out.print("(x"+x_id[0]+":"+name + " f)");
			str_lhs[0]+="(x"+x_id[0]+":"+name + " f)";
			add_rhs(v_rhs_frags, (Integer)span.get(0), "x"+x_id[0]);
			x_id[0]++;
		}else if(terminal_symbol != ""){//pre-terminal, chilren are special
			if(span==null){
				//System.out.print("(" + name + " ("+terminal_symbol+" n))");
				str_lhs[0]+="(" + name + " ("+terminal_symbol+" n))";
				add_rhs(v_rhs_unaligned, v_rhs_frags.length, terminal_symbol);//remember how many continuos span before me
			}else{//non-frontier pre-terminal
				/*TODO: now, we sort the words according to their start span, this may not be good for non-coninous translation
				 * problem is due to 1-to-m non-continuous translation*/
				//System.out.print("(" + name + " ("+terminal_symbol+" f))");
				str_lhs[0]+="(" + name + " ("+terminal_symbol+" f))";
				add_rhs(v_rhs_frags, (Integer)span.get(0), terminal_symbol);
			}
		}else{//call my children
			ctrl_derive_subrule( x_id, str_lhs, v_rhs_frags,v_rhs_unaligned);
		}
	}

		private void derive_rule(Hashtable rule_tbl, int len_tgt, BufferedWriter out){
			//NP ||| (x0:DNP (LCP fake) (DEG fake)) (x1:NP fake) ||| x1 x0 ||| 0 0 0 0 0
			if(frontier_flag==true && l_children.size()>1){//only extract rule for frontier node with more than one children
				int[] x_id = new int[1];
				x_id[0]=0;

				String[] v_rhs_frags= new String[len_tgt];//the index is the start pos in the target, value is the rhs symbol
				String[] v_rhs_unaligned= new String[len_tgt+1];//the index remember how many spans should put before it
				String[] str_lhs = new String[1];
				str_lhs[0]="";
				//get the lhs symbols
				ctrl_derive_subrule( x_id, str_lhs, v_rhs_frags, v_rhs_unaligned);

				//now begin to work on the rhs symbols
				System.out.print(str_lhs[0]+" => ");
				String str_rhs="";
				int num_comsumed_span=0;
				for(int start_pos = 0; start_pos< v_rhs_frags.length; start_pos++){
					if(v_rhs_frags[start_pos]!=null){
						//before we print aligned symbol, look at unaligned one
						for(int n_left = 0; n_left<v_rhs_unaligned.length && n_left<=num_comsumed_span; n_left++){
							if(v_rhs_unaligned[n_left]!=null){
								//System.out.print(v_rhs_unaligned[n_left] +" ");
								str_rhs += v_rhs_unaligned[n_left] +" ";
								v_rhs_unaligned[n_left]=null;
							}
						}
						//print aligned symbols
						//System.out.print(v_rhs_frags[start_pos] +" ");
						str_rhs += v_rhs_frags[start_pos] +" ";
						num_comsumed_span++;
					}
				}
				//print all the remaining unalinged words
				for(int n_left = 0; n_left<v_rhs_unaligned.length; n_left++){
					if(v_rhs_unaligned[n_left]!=null){
						//System.out.print(v_rhs_unaligned[n_left] +" ");
						str_rhs += v_rhs_unaligned[n_left] +" ";
						v_rhs_unaligned[n_left]=null;
					}
				}
				if(out==null)
					System.out.print(str_lhs[0].trim()+" => " + str_rhs.trim() +"\n");
				else{
					FileUtility.write_lzf(out,str_lhs[0].trim()+" ||| " + str_rhs.trim() +" ||| 1\n");
				}
			}
		}//end of this function
	}//end of node class
}
