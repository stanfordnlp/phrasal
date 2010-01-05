package edu.stanford.nlp.mt.syntax.util;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;


/*Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

@SuppressWarnings("unchecked")
public class ReorderRule{
	public Tree tree = null;//the tree fragment
	public String name ="";//the lhs
	public String[] tgt_order = null;//a vector of string, we do not need hiearchy of the target
	public int count_applied=0;
	
	public ReorderRule(String rule_str){
		//VP ||| (x0:PP x1:VP) ||| x1 x0 ||| 0 0 0 0 0	
		String[] fds = rule_str.split("\\s+\\|{3}\\s+");
		name=fds[0];
		tree=new Tree(fds[1]);
		tree.root.name=name;
		tree.root.name_after_reorder=name;
		tgt_order = fds[2].split("\\s+");//TODO: assume the chinese words does not have x0, x1, and so on
		//System.out.println("Reordering rule----\nName: "+ name);
		//tree.print_tree(null);
	}	
	
	public void print_rule(){
		tree.print_tree(null);
		System.out.print(" => ");
		for(int i=0; i < tgt_order.length; i++)
		System.out.print( tgt_order[i] +" ");		
	}
	
	public boolean reorder(Tree.Node tree_node){//tree_node: the node of the parsing tree of the source sentence
		Hashtable reorder_tbl=new Hashtable();
		if(tree_node.is_subsume(tree.root,reorder_tbl)==false){						
			return false;
		}else{
			//System.out.println("applicable reordering rule: !!!");
			//duplicat the contens of table
			Hashtable dup = new Hashtable();			
			Set keys = reorder_tbl.keySet();
			Iterator iter = keys.iterator();
			while (iter.hasNext())
			{
				String tag = (String)iter.next();
				Tree.Node t_node = (Tree.Node) reorder_tbl.get(tag); 
				Tree.Node t_node2 =new Tree.Node();
				t_node2.replace_contents_with(t_node);
				dup.put(tag, t_node2);
			}			
			
			for(int i=0; i<tgt_order.length; i++){
				if(tgt_order[i].matches("x\\d+")){
					Tree.Node from_node =(Tree.Node)reorder_tbl.get("x"+i);					
					Tree.Node to_node =(Tree.Node)dup.get(tgt_order[i]);
					from_node.replace_contents_with(to_node);
					from_node.name_after_reorder=from_node.name+"lzf"+tgt_order[i];
				}
			}
			count_applied++;
			return true;
		}				
	}		
	
	

}
