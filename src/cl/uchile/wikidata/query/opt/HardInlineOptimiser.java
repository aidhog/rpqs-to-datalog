package cl.uchile.wikidata.query.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import cl.uchile.wikidata.query.BaseRPQ2Datalog;
import cl.uchile.wikidata.query.Utils;
import cl.uchile.wikidata.query.datalog.Atom;
import cl.uchile.wikidata.query.datalog.GraphAtom;
import cl.uchile.wikidata.query.datalog.NodeAtom;
import cl.uchile.wikidata.query.datalog.Program;
import cl.uchile.wikidata.query.datalog.Rule;

public class HardInlineOptimiser implements ProgramOptimiser {
	public static final String UNSEEN_PREFIX = "u";

	@Override
	public Program optimise(Program p){
		Program optimised = new Program();

		HashSet<String> keep = new HashSet<String>();

		for(Rule r : p.getRules()) {
			for(Atom b: r.getBody()) {
				if(r.getHead().getPredicate().equals(b.getPredicate())) {
					keep.add(r.getHead().getPredicate());
				}
			}
		}

		keep.add(BaseRPQ2Datalog.QUERY_PRED);
		keep.add(GraphAtom.GRAPH_PREDICATE);
		keep.add(NodeAtom.NODE_PREDICATE);
		keep.add(NodeOptimiser.SUBJECT_PREDICATE);
		keep.add(NodeOptimiser.OBJECT_PREDICATE);

		HashMap<String,ArrayList<Rule>> headPredToRule = new HashMap<String,ArrayList<Rule>>();
		for(Rule r : p.getRules()) {
			if(!keep.contains(r.getHead().getPredicate())) {
				ArrayList<Rule> rs = headPredToRule.get(r.getHead().getPredicate());
				if(rs == null) {
					rs = new ArrayList<Rule>();
					headPredToRule.put(r.getHead().getPredicate(),rs);
				}
				rs.add(r);
			}
		}

		for(Rule r : p.getRules()) {
			if(keep.contains(r.getHead().getPredicate())) {
				ArrayList<Rule> inlinedExpanded = inlineBody(r, headPredToRule, keep);

				for(Rule rr: inlinedExpanded) {
					optimised.addRule(rr);
				}
			}
		}

		for(String decl: p.getDeclarations()) {
			optimised.addDeclaration(decl);
		}

		return optimised;
	}
	
	private static ArrayList<Rule> inlineBody(Rule r, HashMap<String,ArrayList<Rule>> headPredToRule, HashSet<String> keep) {
		return inlineBody(r, headPredToRule, keep, new HashMap<Rule,UnionFindMap>());
	}
	
	private static ArrayList<Rule> inlineBody(Rule r, HashMap<String,ArrayList<Rule>> headPredToRule, HashSet<String> keep, HashMap<Rule,UnionFindMap> ruleToEquiv) {
		// we need to remember variables equivalences
		// from higher up in the recursion
		UnionFindMap ruleEquivs = ruleToEquiv.get(r);
		
		Rule inlined = new Rule();
		inlined.setHead(r.getHead().deepCopy());

		ArrayList<Rule> partialInline = new ArrayList<Rule>();
		partialInline.add(inlined);

//		System.out.println("Inline IN "+r+"\t\t\t\t["+ruleToEquiv.get(r)+"]");		
		
		boolean recurse = false;

		for(Atom b : r.getBody()) {
			ArrayList<Rule> rewritten = new ArrayList<Rule>();

			if(keep.contains(b.getPredicate())){
				for(Rule pr: partialInline) {
					UnionFindMap equivs = ruleToEquiv.get(pr);
					
					if(equivs == null) {
						equivs  = new UnionFindMap();
					} else if(equivs != null) {
						equivs = equivs.deepCopy();
					}
					
					// add equivalences from higher levels of recursion
					if(ruleEquivs != null && !ruleEquivs.isEmpty()) {
						equivs.addAllEquivalences(ruleEquivs);
					}
					
					Rule pr0 = pr.deepCopy();
					pr0.addBodyAtom(b.deepCopy());
					ruleToEquiv.put(pr0, equivs);
					
					rewritten.add(pr0);
				}
			} else {
				ArrayList<Rule> antes = headPredToRule.get(b.getPredicate());

				for(Rule pr: partialInline) {
					
					for(Rule ante: antes) {
						Rule pr0 = pr.deepCopy();
						
						// needs to be here as we need to reset
						//   and clone for each ante
						UnionFindMap equivs = ruleToEquiv.get(pr);

						String[] targetTerms = b.getTerms();
						String[] sourceTerms = ante.getHead().getTerms();
						
						HashMap<String,String> rewriting = new HashMap<String,String>();
						
						if(equivs == null) {
							equivs  = new UnionFindMap();
						} else if(equivs != null) {
							equivs = equivs.deepCopy();
						}
						
						// add equivalences from higher levels of recursion
						if(ruleEquivs != null && !ruleEquivs.isEmpty()) {
							equivs.addAllEquivalences(ruleEquivs);
						}
						
						for(int i=0; i<sourceTerms.length; i++) {
							String old = rewriting.get(sourceTerms[i]);
							if(old == null && !Utils.isInt(sourceTerms[i]) && !Utils.isInt(targetTerms[i])) {
								rewriting.put(sourceTerms[i], targetTerms[i]);
							} else if(Utils.isInt(targetTerms[i]) && !targetTerms[i].equals(sourceTerms[i])) { 
								throw new UnsupportedOperationException("Cannot map constant "+sourceTerms[i]+" to "+targetTerms[i]);
							} else if(Utils.isInt(sourceTerms[i])) { 
								// we are mapping a constant to a variable
								// so need to remember the equality
								equivs.addEquivalences(sourceTerms[i],targetTerms[i]);
							} else if(old != null) {
								// we have a duplicate variable in the source
								// or a constant mapped to a variable
								// and need to track the equivalence for the target
								// to ensure we align variables correctly in the end
								equivs.addEquivalences(targetTerms[i],old);
							}
						}

						Rule r0w = rewrite(ante, rewriting);

						for(Atom bw:r0w.getBody()) {
							pr0.addBodyAtom(bw.deepCopy());

							if(!keep.contains(bw.getPredicate())) {
								recurse = true;
							}
						}

						// we may need to map variables in previous atoms
						// if we have a rule head with duplicate variables
						rewritten.add(pr0);
						
						// store equivalences for when they will be
						// used later
						ruleToEquiv.put(pr0, equivs);
					}
				}
			}
			partialInline = rewritten;
		}
		
		

		if(recurse) {
			ArrayList<Rule> deeper = new ArrayList<Rule>();
			for(Rule i:partialInline) {
//				System.out.println("Inline BR "+i+"\t\t\t\t["+ruleToEquiv.get(i)+"]");		
				deeper.addAll(inlineBody(i, headPredToRule, keep, ruleToEquiv));
				
//				for(Rule d: deeper) {
//					System.out.println("Inline AR "+d+"\t\t\t\t["+ruleToEquiv.get(r)+"]");		
//				}
			}
			partialInline = deeper;
		}
		
		// finally consolidate variables with equality
		ArrayList<Rule> aligned = new ArrayList<Rule>();
		for(Rule pr : partialInline) {
//			System.out.println("Inline BA "+pr+"\t\t\t\t["+ruleToEquiv.get(pr)+"]");	
			UnionFindMap equivs = ruleToEquiv.get(pr);
			
			Rule prw = Rule.rewriteEquivalences(pr, equivs);
			
			if(prw != null) {
				aligned.add(prw);
			}
			
//			if(equivs!=null && !equivs.isEmpty()) {
//				HashMap<String,String> rewriting = new HashMap<String,String>();
//				HashMap<String,HashSet<String>> unionMap = equivs.getUnionMap();
//				for(HashSet<String> ec : unionMap.values()) {
//					// pick term from the equivalence class:
//					// first pick constant,
//					//  then variable that appears first in the head
//					//    thereafter first variable in the body
//					//      (otherwise random but should not happen)
//					String pivot = null;
//					int cons = 0;
//					for(String e:ec) {
//						if(Utils.isInt(e)) {
//							cons ++;
//							pivot = e;
//						}
//					}
//					
//					if(cons > 1) {
//						// rule is unsatisfiable so skip
//						satisfiable = false;
//						break;
//					}
//					
//					if(pivot == null) {
//						for(String t: pr.getHead().getTerms()) {
//							if(ec.contains(t)) {
//								pivot = t;
//								break;
//							}
//						}
//					}
//					
//					if(pivot==null) {
//						for(Atom b: pr.getBody()) {
//							for(String t: b.getTerms()) {
//								if(ec.contains(t)) {
//									pivot = t;
//									break;
//								}
//							}
//						}
//					}
//					
//					if(pivot == null) {
//						pivot = ec.iterator().next();
//					}
//					
//					for(String e: ec) {
//						if(!e.equals(pivot)) {
//							rewriting.put(e,pivot);
//						}
//					}
//				}
//				
//				if(satisfiable) {
//					aligned.add(rewrite(pr, rewriting, ""));
//				}
//			} else {
//				aligned.add(pr);
//			}
		}
		
//		for(Rule d: aligned) {
//			System.out.println("Inline EN "+d);		
//		}

		return aligned;
	}
	
	private static Rule rewrite(Rule r0, HashMap<String,String> rewriting, String unseenPrefix) {
		Rule rw = new Rule();
		
		Atom rh = r0.getHead().deepCopy();
		rh.rewrite(rewriting, unseenPrefix);
		rw.setHead(rh);
		
		for(Atom b: r0.getBody()) {
			Atom bh = b.deepCopy();
			bh.rewrite(rewriting, unseenPrefix);
			rw.addBodyAtom(bh);
		}

		return rw;
	}

	private static Rule rewrite(Rule r0, HashMap<String,String> rewriting) {
		return rewrite(r0, rewriting, UNSEEN_PREFIX);
	}

	public static boolean isInt(String s) {
		try {
			Integer.parseInt(s);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	public static class UnionFindMap {
		private HashMap<String,HashSet<String>> eToEs;
		
		public UnionFindMap() {
			eToEs = new HashMap<String,HashSet<String>>();
		}
		
		public void addAllEquivalences(UnionFindMap ruleEquivs) {
			for(Map.Entry<String,HashSet<String>> entry : ruleEquivs.eToEs.entrySet()) {
				// we do it this way as we need to ensure references
				// to equivalence sets are the same for keys of that set
				HashSet<String> equivs = entry.getValue();
				if(equivs.size()==1) {
					String e = equivs.iterator().next();
					addEquivalences(e, e);
				} else if(equivs.size()>1) {
					Iterator<String> iter = equivs.iterator();
					String e = equivs.iterator().next();
					while(iter.hasNext()) {
						addEquivalences(e, iter.next());
					}
				}
			}
		}

		public boolean isEmpty() {
			return eToEs.isEmpty();
		}
		
		public HashMap<String,HashSet<String>> getUnionMap() {
			return eToEs;
		}
		
		public int size() {
			return eToEs.size();
		}
		
		public void addEquivalences(String a, String b) {
			HashSet<String> esA = eToEs.get(a);
			HashSet<String> esB = eToEs.get(b);
			
			if(esA == null && esB == null) {
				HashSet<String> es = new HashSet<String>();
				es.add(a);
				es.add(b);
				
				eToEs.put(a, es);
				eToEs.put(b, es);
			} else if(esB == null) {
				esA.add(b);
				
				eToEs.put(b,esA);
			} else if(esA == null) {
				esB.add(a);
				
				eToEs.put(a,esB);
			} else {
				esA.addAll(esB);
				
				for(String eb: esB){			
					eToEs.put(eb, esA);
				}
			}
		}
		
		public HashSet<String> getEquivalences(String e){
			return eToEs.get(e);
		}
		
		public UnionFindMap deepCopy() {
			UnionFindMap ufm = new UnionFindMap();
			ufm.addAllEquivalences(this);
			return ufm;
		}
		
		public String toString() {
			return eToEs.toString();
		}
	}
}
