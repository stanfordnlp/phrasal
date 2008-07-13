package mt.syntax.decoder.ckyDecoder;

import java.io.BufferedReader;
import java.util.HashMap ;
import java.util.List;

import mt.syntax.decoder.lzfUtility.FileUtility;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

public class Symbol {
	//terminal symbol may get from a tbl file, srilm, or a lm file
	//non-terminal symbol is always from myself	
	static HashMap  str_2_num_tbl = new HashMap ();
	static HashMap  num_2_str_tbl = new HashMap ();
	public static int lm_start_sym_id = 10000;//1-10000 reserved for non-terminal
	public static int lm_end_sym_id = 1000001;//max vocab 1000k
	
	static public boolean use_my_own_tbl=false;//for terminal only: use my own table(both java and remote mehtod use this) or srilm table
	
	public static int nonterminal_cur_id=1;//start from 1
	public static int terminal_cur_id =lm_start_sym_id ;
	
	
	//all the global sym id
	static String BACKOFF_WGHT_SYM="<bow>";
	static int BACKOFF_WGHT_SYM_ID=0;//used by LMModel
	static String BACKOFF_LEFT_LM_STATE_SYM="<lzfbo>";
	static int BACKOFF_LEFT_LM_STATE_SYM_ID=0;
	static String NULL_LEFT_LM_STATE_SYM="<lzflnull>";
	static int NULL_LEFT_LM_STATE_SYM_ID=0;
	static String NULL_RIGHT_LM_STATE_SYM="<lzfrnull>";
	static int NULL_RIGHT_LM_STATE_SYM_ID=0;
	static String LM_STATE_OVERLAP_SYM="<lzfoverlap>";
	static int LM_STATE_OVERLAP_SYM_ID=0;
	static String LM_HAVE_SUFFIX_SYM="<havelzfsuffix>"; //to indicate that lm trie node has children
	static int LM_HAVE_SUFFIX_SYM_ID=0; 
	static String LM_HAVE_PREFIX_SYM="<havelzfprefix>"; //to indicate that lm trie node has children
	static int LM_HAVE_PREFIX_SYM_ID=0; 
	static String LM_PROB_SYM="<lmlzfprob>";
	static int LM_PROB_SYM_ID=0;
	static String START_SYM="<s>";
	static int START_SYM_ID=0;
	static String STOP_SYM="</s>";
	static int STOP_SYM_ID=0;
	static String ECLIPS_SYM="<e>";
	static int ECLIPS_SYM_ID=0;
	static String UNK_SYM="<unk>";//unknown lm word
	static int UNK_SYM_ID=0;
	static String UNTRANS_SYM="<unt>";
	static int UNTRANS_SYM_ID=0;//untranslated word id
	static String GOAL_SYM="S"; 
	static int GOAL_SYM_ID=0;
	
	
	//important note: we should always first init lm (srilm, java, or remote), in which it will init the symbol tbl
	
/*	
	//srilm part
	//static int vocab_none;
	//static SWIGTYPE_p_Vocab p_srilm_nonterminal_vocab;//if use_srilm==true, then we will use srilm vocab
	
	//this function need to be called before anything happen
	static public void init_sym_tbl_from_decoder(){
		if(Decoder.use_remote_lm_server==true){//read from the map tbl from a file
			if(Decoder.remote_symbol_tbl.compareTo("null")==0){
				System.out.println("Error: use_remote_lm_server, but not have sym file");
				System.exit(0);		
			}
			read_index_tbl(Decoder.remote_symbol_tbl);
			use_my_own_tbl=true;
		}else{
			//do nothing, the tbl will be read from the LM file by myself later or by SRILM
		}
		add_global_symbols();
	}	
	
//	this function need to be called before anything happen
	static public void init_sym_tbl_from_lmserver(){
		if(LMServer.is_suffix_server==true){//read from the map tbl from a file
			if(LMServer.remote_symbol_tbl.compareTo("null")==0){
				System.out.println("Error: use_remote_lm_server, but not have sym file");
				System.exit(0);		
			}
			read_index_tbl(LMServer.remote_symbol_tbl);
			add_global_symbols();
			use_my_own_tbl=true;
		}else{
			//lmserver will call the function later add_global_symbols();
			//do nothing, the tbl will be read from the LM file by myself later or by SRILM
		}	
	}	
*/	
		
	//make sure the file contains all the global symbols
	@SuppressWarnings("unchecked") 
	static public void init_sym_tbl_from_file(String fname, boolean u_own_tbl){	
		use_my_own_tbl=u_own_tbl;
		
		//### read file
		HashMap tbl_str_2_id = new HashMap();
		HashMap tbl_id_2_str = new HashMap();
		BufferedReader t_reader_sym = FileUtility.getReadFileStream(fname,"UTF-8");
		String line;		
		while((line=FileUtility.read_line_lzf(t_reader_sym))!=null){
			String[] fds = line.split("\\s+");
			if(fds.length!=2){
			    System.out.println("Warning: read index, bad line: " + line);
			    continue;
			}
			String str = fds[0].trim();
			int id = new Integer(fds[1]);

			String uqniue_str;
			if(tbl_str_2_id.get(str)!=null){//it is quite possible that java will treat two stings as the same when other language (e.g., C or perl) treat them differently, due to unprintable symbols
				 System.out.println("Warning: duplicate string (add fake): " + line);
				 uqniue_str=str+id;//fake string
				 //System.exit(0);//TODO				 
			}else{
				uqniue_str=str;
			}
			tbl_str_2_id.put(uqniue_str,id);
			
			//it is guranteed that the strings in tbl_id_2_str are different
			if(tbl_id_2_str.get(id)!=null){
				 System.out.println("Error: duplicate id, have to exit; " + line);
				 System.exit(0);
			}else{
				tbl_id_2_str.put(id, uqniue_str);
			}
		}
		FileUtility.close_read_file(t_reader_sym);
		
		if(tbl_id_2_str.size()>=lm_end_sym_id-lm_start_sym_id){
			System.out.println("Error: read symbol tbl into srilm, tlb is too big");
			System.exit(0);	
		}
		
		//#### now add the tbl into srilm
		int n_added=0;
		for(int i=lm_start_sym_id; i<lm_end_sym_id; i++){
			String str = (String) tbl_id_2_str.get(i);//it is guranteed that the strings in tbl_id_2_str are different
			int res_id;
			if(str!=null){
				res_id = add_terminal_symbol(str);
				n_added++;
			}else{//non-continous index
				System.out.println("Warning: add fake symbol, be alert");
				res_id = add_terminal_symbol("lzf"+i);
			}	
			if(res_id!=i){
				System.out.println("id supposed: " + i +" != assinged " + res_id);
				System.exit(0);
			}		
			if(n_added>=tbl_id_2_str.size())
				break;
		}
		Symbol.add_global_symbols(u_own_tbl); // the above already load all the symbols except the global non-terminal symbols, but we need to call this function to set the SYM_ID correctly
	}
	
	 static public void add_global_symbols(boolean use_own_tbl){
		use_my_own_tbl=use_own_tbl;
		BACKOFF_WGHT_SYM_ID = add_terminal_symbol(BACKOFF_WGHT_SYM);
		BACKOFF_LEFT_LM_STATE_SYM_ID = add_terminal_symbol(BACKOFF_LEFT_LM_STATE_SYM);
		NULL_LEFT_LM_STATE_SYM_ID = add_terminal_symbol(NULL_LEFT_LM_STATE_SYM);
		NULL_RIGHT_LM_STATE_SYM_ID =  add_terminal_symbol(NULL_RIGHT_LM_STATE_SYM);
		LM_STATE_OVERLAP_SYM_ID = add_terminal_symbol(LM_STATE_OVERLAP_SYM);
		LM_HAVE_SUFFIX_SYM_ID = add_terminal_symbol(LM_HAVE_SUFFIX_SYM);
		LM_HAVE_PREFIX_SYM_ID = add_terminal_symbol(LM_HAVE_PREFIX_SYM);
		LM_PROB_SYM_ID = add_terminal_symbol(LM_PROB_SYM);
		START_SYM_ID = add_terminal_symbol(START_SYM);
		STOP_SYM_ID = add_terminal_symbol(STOP_SYM);
		ECLIPS_SYM_ID = add_terminal_symbol(ECLIPS_SYM);
		UNK_SYM_ID = add_terminal_symbol(UNK_SYM);
		UNTRANS_SYM_ID = add_terminal_symbol(UNTRANS_SYM);
		GOAL_SYM_ID = add_non_terminal_symbol(GOAL_SYM);
	}
	
	static public String get_string(int id){
		if(use_my_own_tbl==false &&  is_nonterminal(id)==false){			
			return get_terminal_str_srilm(id);
		}
		
		String res = (String)num_2_str_tbl.get(id);
		if(res==null){
			System.out.println("unknown id: "+id);
			//throw new IOException();
			System.exit(0);
		}else{
			return res;
		}
		return res;
	}
	
	static public String get_string(Integer[] ids){
		String res = "";
		for(int t=0; t<ids.length; t++)
			res += " " + get_string(ids[t]);
		return res;
	}
	static public String get_string(int[] ids){
		String res = "";
		for(int t=0; t<ids.length; t++)
			res += " " + get_string(ids[t]);
		return res;
	}
	static public String get_string(List ids){
		String res = "";
		for(int t=0; t<ids.size(); t++)
			res += " " + get_string((Integer)ids.get(t));
		return res;
	}
	static public int[] get_ids(String[] strings){
		int[] res =new int[strings.length];
		for(int t=0; t<strings.length; t++)
			res[t]=add_terminal_symbol(strings[t]);
		return res;
	}
	
	@SuppressWarnings("unchecked") 
	static public int add_terminal_symbol(String str){
		if(use_my_own_tbl==false)
			return get_terminal_sym_id_srilm(str);
		else{
			Integer res_id = (Integer)str_2_num_tbl.get(str);
			if(res_id!=null){//already have this symbol
				if(is_nonterminal(res_id)==true){
					System.out.println("Error, terminal symbol mix with non-terminal, Sym: " + str + "; have id: " + res_id);
					System.exit(0);
				}
				return res_id;
			}else{			
				str_2_num_tbl.put(str, terminal_cur_id);
				num_2_str_tbl.put(terminal_cur_id, str);
				terminal_cur_id++;
				//System.out.println("Sym: " + str + "; id: " + positive_id);
				return (terminal_cur_id-1);
			}
		}
	}
	
//	####### following funcitons used for TM only
	@SuppressWarnings("unchecked") 
	static public int add_non_terminal_symbol(String str){
		Integer res_id = (Integer)str_2_num_tbl.get(str);
		if(res_id!=null){//already have this symbol
			if(is_nonterminal(res_id)==false){
				System.out.println("Error, NONTSym: " + str + "; have id: " + res_id);
				System.exit(0);
			}
			return res_id;
		}else{	
			str_2_num_tbl.put(str, nonterminal_cur_id);
			num_2_str_tbl.put(nonterminal_cur_id, str);
			nonterminal_cur_id++;

			//System.out.println("Sym: " + str + "; id: " + negative_id);
			return (nonterminal_cur_id-1);		
		}
	}
	
	static public boolean is_nonterminal(int id){		
		return (id<lm_start_sym_id)? true : false;
	}
	
	static public int get_eng_non_terminal_id(int id){
		if(is_nonterminal(id)==false)//terminal
			return -1;
		else{
			return TMGrammar.get_eng_non_terminal_id(get_string(id));//TODO
		}
	}
	
//	srilm begin
	 /*private static int get_nonterminal_sym_id_srilm(String str){//
		 return  (int)srilm.getIndexForWord_Vocab(p_srilm_nonterminal_vocab, str);
	 }*/
	 
	 private static int get_terminal_sym_id_srilm(String str){
		 return  (int)srilm.getIndexForWord(str);
	 }
	 
	 /*private static String get_nonterminal_str_srilm(int id){//
		 return  srilm.getWordForIndex_Vocab(p_srilm_nonterminal_vocab, id);
	 }*/
	 
	 private static String get_terminal_str_srilm(int id){
		 return  srilm.getWordForIndex(id);
	 }
}
