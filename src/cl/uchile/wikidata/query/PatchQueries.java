package cl.uchile.wikidata.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;

import cl.uchile.wikidata.query.datalog.Atom;
import cl.uchile.wikidata.query.datalog.Rule;

public class PatchQueries {
	public static String INPUT_DIR = "C:\\Users\\aidhog\\Documents\\Research\\papers\\2021\\ring-paths\\scripts\\lb\\datalog\\broken-hinline-lc-node";

	public static void main(String[] args) throws IOException {
		File in = new File(INPUT_DIR);
		File[] files = in.listFiles();

		TreeSet<String> fix = new TreeSet<String>();
		
		for(File f:files) {
			if(f.getName().endsWith(".logic")) {
				BufferedReader br = new BufferedReader(new FileReader(f));

				String line = null;

				while((line=br.readLine())!=null) {
					line = line.trim();

					if(!line.isEmpty() && !line.startsWith("//")) {
						Rule r = Rule.parse(line);
						//						if(!line.equals(r.toString())) {
						//							System.err.println(line+" diff from "+r.toString());
						//						}

						HashSet<String> hvars = new HashSet<String>();

						for(String ht: r.getHead().getTerms()) {
							if(!Utils.isInt(ht)) {
								hvars.add(ht);
							}
						}

						HashMap<String,Integer> bodyCount = new HashMap<String,Integer>();
						for(Atom b: r.getBody()) {
							HashSet<String> termSet = new HashSet<String>();
							for(String bs: b.getTerms()) {
								if(!Utils.isInt(bs)) {
									if(termSet.add(bs)) {
										Integer c = bodyCount.get(bs);
										if(c == null) {
											bodyCount.put(bs, 1);
										} else {
											bodyCount.put(bs, c+1);
										}
									}
								}
							}
						}

						for(Map.Entry<String, Integer> e : bodyCount.entrySet()) {
							if(e.getValue() == 1 && !hvars.contains(e.getKey())) {
								fix.add(f.getName());
							}
						}
					}
				}

				br.close();
			}
		}
		
		for(String s: fix) {
			System.out.println(s);
		}
	}
}
