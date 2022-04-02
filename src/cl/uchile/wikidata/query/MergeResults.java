package cl.uchile.wikidata.query;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

public class MergeResults {
	public static final String INPUT = "C:\\Users\\aidhog\\Documents\\Research\\papers\\2021\\ring-paths\\results\\nolimit\\lb-partial.txt";
	public static final String OVERRIDE = "C:\\Users\\aidhog\\Documents\\Research\\papers\\2021\\ring-paths\\results\\nolimit\\hinline-lc-node-prune-fix.txt";
	
	public static final String OUTPUT = "C:\\Users\\aidhog\\Documents\\Research\\papers\\2021\\ring-paths\\results\\nolimit\\logicbloxs.txt";

	public static void main(String[] args) throws IOException {
		BufferedReader ov = new BufferedReader(new FileReader(OVERRIDE));
		String line = null;
		HashMap<String,String[]> k2v = new HashMap<String,String[]>();
		
		while((line = ov.readLine())!=null) {
			line = line.trim();
			if(!line.isEmpty()) {
				String[] parse = line.split(",");
				if(parse.length==3) {
					k2v.put(parse[0], parse);
				} else {
					System.err.println("Unrecognised line "+line);
				}
			}
		}
		
		PrintWriter pw = new PrintWriter(new FileWriter(OUTPUT));
		
		BufferedReader in = new BufferedReader(new FileReader(INPUT));
		int read = 0;
		while((line = in.readLine())!=null) {
			line = line.trim();
			if(!line.isEmpty()) {
				read++;
				String[] parse = line.split(",");
				if(parse.length==3) {
					String[] overhead = k2v.get(parse[0]);
					if(overhead != null) {
						parse = overhead;
					}
					
					double times = Double.parseDouble(parse[1]);
					long timens = (long)(times * 1000 * 1000 * 1000);
					String out = read+";"+parse[2]+";"+timens;
					
					pw.println(out);
					
				} else {
					System.err.println("Unrecognised line "+line);
				}
			}
		}
		
		pw.close();
		in.close();
	}
}
