package mt.syntax.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPInputStream;


/*Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/


//!!!!!!!!!!!!!!!utility functions for file operations
public class FileUtility{
	public static BufferedReader getReadFileStream(String filename, String enc) {
		BufferedReader in =null;
		try {
			if(filename.endsWith(".gz")==true){
				in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)),enc));
			}else{
				in = new BufferedReader(new InputStreamReader(new FileInputStream(filename),enc));
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return in;
	}
	public static BufferedReader getReadFileStream(String filename) {
		BufferedReader in =null;
		try {
			if(filename.endsWith(".gz")==true){
				in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)),"UTF-8"));
			}else{
				in = new BufferedReader(new InputStreamReader(new FileInputStream(filename),"UTF-8"));
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return in;
	}

	public static int number_lines_in_file(String file){
		BufferedReader t_reader_test = FileUtility.getReadFileStream(file,"UTF-8");
		int i=0;
		while((read_line_lzf(t_reader_test))!=null){
			i++;
		}
		close_read_file(t_reader_test);
		return i;
	}
	
	public static BufferedWriter getWriteFileStream(String filename, String enc) {
		BufferedWriter out=null;
		try {
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename) , enc));	
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return out;
	}
	
	//do not overwrite, append
	public static BufferedWriter getWriteFileStream_append(String filename, String enc) {
		BufferedWriter out=null;
		try {
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename,true) , enc));	
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return out;
	}
	
	public static 	BufferedWriter getWriteFileStream(String filename) {
		BufferedWriter out=null;
		try {
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename) , "UTF-8"));	
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return out;
	}
	
	public static String read_line_lzf(BufferedReader in){
		String str="";
		try {
			str = in.readLine();	
		}
		catch (IOException e) {
			e.printStackTrace();
		}	
		return str;
	}
	
	public static void write_lzf(BufferedWriter out, String str){
		try {
			out.write(str);	
		}
		catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	public static void flush_lzf(BufferedWriter out){
		try {
			out.flush();	
		}
		catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	public static void close_write_file(BufferedWriter out){
		try {
			out.close();	
		}
		catch (IOException e) {
			e.printStackTrace();
		}		
	}
	public static void close_read_file(BufferedReader in){
		try {
			in.close();	
		}
		catch (IOException e) {
			e.printStackTrace();
		}		
	}
}
//end of utility for file options
