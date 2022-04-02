package cl.uchile.wikidata.query;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.apache.jena.graph.Node;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpPath;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.path.Path;

import cl.uchile.wikidata.query.datalog.Program;
import cl.uchile.wikidata.query.datalog.Rule;
import cl.uchile.wikidata.query.opt.HardInlineOptimiser;
import cl.uchile.wikidata.query.opt.LinearConstantOptimiser;
import cl.uchile.wikidata.query.opt.NodeOptimiser;
import cl.uchile.wikidata.query.opt.PruneOptimiser;

public class ConvertRPQsToDatalog {
    public static String INPUT = "paths-input.tsv";
    public static String OUTPUT = "datalog-output-dir";
	
	public static String OUTPUT_PREFIX = "q";
	public static String OUTPUT_SUFFIX = ".logic";
	public static int OUTPUT_DIGITS = 4;
	public static final int SAMPLE = 1;

	public static String PREFIX = "http://ex.org/";
	public static String ABRV = ":";
	public static String NODE_PREFIX = "n";
	public static String PRED_PREFIX = "p";

	public static void main(String[] args) throws IOException {
		File f = new File(OUTPUT);
		f.mkdirs();
		
		BufferedReader br = new BufferedReader(new FileReader(INPUT));

		String line = null;
		int read=0;

		while((line=br.readLine())!=null) {
			String trim = line.trim();
			if(!trim.isEmpty() && !trim.startsWith("#")) {
				read++;
				
				if(read % SAMPLE == 0) {
					String[] split = line.split(" ");

					if(split.length==3) {
						String[] sparql = new String[3];
						for(int i: new int[] {0,2}) {
							if(split[i].startsWith("?")) {
								sparql[i] = split[i];
							} else {
								sparql[i] = ABRV+NODE_PREFIX+split[i];
							}
						}

						String re = "";
						boolean lastNum = false;
						for(int i=0; i<split[1].length(); i++){
							if(Character.isDigit(split[1].charAt(i))) {
								if(!lastNum) {
									re += ABRV+PRED_PREFIX;
									lastNum = true;
								}
							} else {
								lastNum = false;
							}
							re += split[1].charAt(i);
						}

						String sparqlQ = "PREFIX "+ABRV+" <"+PREFIX+"> SELECT * WHERE { "+sparql[0]+" "+re+" "+sparql[2] +" . }";

						Query query = QueryFactory.create(sparqlQ);

						Op op = Algebra.compile(query);

//						System.out.println("##rpq########################");
//						System.out.println(line);
//						System.out.println(sparqlQ);
//						System.out.println(op);

						if(op instanceof OpPath) {
							ArrayList<Rule> rules = opTransform((OpPath)op);
							Program p = new Program(rules);
//							System.out.println("\n--unoptimised---------------\n"+p.toString());
							
							String out = numToFileName(read);
							
//							System.out.println("---------I-----\n"+p);
							
							HardInlineOptimiser optI = new HardInlineOptimiser();
							p = optI.optimise(p);
//							System.out.println("---------H-----\n"+p);
							
							LinearConstantOptimiser optLC = new LinearConstantOptimiser(false);
							p = optLC.optimise(p);
//							System.out.println("---------L-----\n"+p);
							
							NodeOptimiser optN = new NodeOptimiser();
							p = optN.optimise(p);
//							System.out.println("---------N-----\n"+p);
							
							PruneOptimiser optP = new PruneOptimiser();
							p = optP.optimise(p);
//							System.out.println("---------P-----\n"+p);
							
//							LinearOptimiser opt2 = new LinearOptimiser(false);
//							p = opt2.optimise(p);
							
							for(Rule r:p.getRules()) {
								if(!Rule.validateLonelyVariable(r)) {
									System.err.println("Lonely variable in "+r+" "+line);
								}
							}
							
							PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(out)));
							pw.println("// "+trim+"\n");
							pw.println(p.toString());
							pw.close();
							
//							System.out.println("--optimised----------------\n"+p.toString());
						} else {
							System.err.println(op+" not of type OpPath");
						}
					} else {
						System.err.println("Line "+line+" has "+split.length+" elements, not 3.");
					}
				}
			}
		}

		br.close();
	}

	public static String numToFileName(int i) {
		String padded = String.format("%0"+OUTPUT_DIGITS+"d" , i);
		return OUTPUT+"/"+OUTPUT_PREFIX+padded+OUTPUT_SUFFIX;
	}

	private static String sparqlToNumString(String s) {
		if(s.startsWith("?")) {
			return s.substring(1);
		} else if(s.startsWith(PREFIX+NODE_PREFIX)) {
			return s.substring(PREFIX.length()+NODE_PREFIX.length());
		} else if(s.startsWith(PREFIX+PRED_PREFIX)) {
			return s.substring(PREFIX.length()+PRED_PREFIX.length());
		} else {
			throw new UnsupportedOperationException("Unrecognised SPARQL term "+s);
		}
	}

	public static String sparqlToNumString(Node n) {
		return sparqlToNumString(n.toString());
	}

	public static String[] sparqlToNumStrings(Node... n) {
		String[] result = new String[n.length];
		for(int i=0; i<result.length; i++) {
			result[i] = sparqlToNumString(n[i]);
		}
		return result;
	}

	private static ArrayList<Rule> opTransform(OpPath op) {
		return opTransform(op.getTriplePath());
	}

	private static ArrayList<Rule> opTransform(TriplePath triplePath) {
		return opTransform(triplePath.getPath(), triplePath.getSubject(), triplePath.getObject());
	}

	private static ArrayList<Rule> opTransform(Path path, Node subject, Node object) {
		BaseRPQ2Datalog p2d = new BaseRPQ2Datalog(subject, object);
		path.visit(p2d);
		return p2d.getProgram();
	}
}
