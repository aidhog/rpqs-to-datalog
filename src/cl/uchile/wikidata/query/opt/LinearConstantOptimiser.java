package cl.uchile.wikidata.query.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import cl.uchile.wikidata.query.BaseRPQ2Datalog;
import cl.uchile.wikidata.query.Utils;
import cl.uchile.wikidata.query.datalog.Atom;
import cl.uchile.wikidata.query.datalog.Program;
import cl.uchile.wikidata.query.datalog.Rule;
import cl.uchile.wikidata.query.opt.HardInlineOptimiser.UnionFindMap;

public class LinearConstantOptimiser implements ProgramOptimiser {
	public static final String UNSEEN_PREFIX = "u";

	public static boolean DEFAULT_ENABLE_CONSTANT = true;

	boolean constant = DEFAULT_ENABLE_CONSTANT;

	public LinearConstantOptimiser() {
		this(DEFAULT_ENABLE_CONSTANT);
	}

	public LinearConstantOptimiser(boolean constant) {
		this.constant = constant;
	}

	@Override
	public Program optimise(Program p){
		Program linear = new Program();

		HashMap<String,Integer> transP = new HashMap<String,Integer>();
		HashMap<String,Boolean> leftR = new HashMap<String,Boolean>();

		HashMap<String,String> replace = new HashMap<String,String>();

		HashMap<Rule,Integer> trules = new HashMap<Rule,Integer>();
		for(Rule r : p.getRules()) {
			for(Atom b: r.getBody()) {
				if(r.getHead().getPredicate().equals(b.getPredicate())) {
					if(!transP.containsKey(b.getPredicate())) {
						transP.put(b.getPredicate(), 1);
					} else {
						transP.put(b.getPredicate(), transP.get(b.getPredicate())+1);
						trules.put(r,transP.get(b.getPredicate()));
					}
				}
			}
		}

		for(Rule r : p.getRules()) {
			for(Atom b: r.getBody()) {
				if(transP.containsKey(b.getPredicate())) {
					if(Utils.isInt(b.getTerms()[0]) && constant) {
						leftR.put(b.getPredicate(),true);
						replace.put(b.getPredicate(),b.getTerms()[0]);
					} else if(Utils.isInt(b.getTerms()[1])) {
						leftR.put(b.getPredicate(),false);
						replace.put(b.getPredicate(),b.getTerms()[1]);
					}
				}
			}
		}

		HashMap<String,ArrayList<Rule>> headPredToRule = new HashMap<String,ArrayList<Rule>>();
		for(Rule r : p.getRules()) {
			if(transP.containsKey(r.getHead().getPredicate()) && transP.get(r.getHead().getPredicate()) > 1) {
				ArrayList<Rule> rs = headPredToRule.get(r.getHead().getPredicate());
				if(rs == null) {
					rs = new ArrayList<Rule>();
					headPredToRule.put(r.getHead().getPredicate(),rs);
				}
				rs.add(r);
			}
		}

		HashMap<Rule,UnionFindMap> ruleToEquiv = new HashMap<Rule,UnionFindMap>();

		for(Rule r : p.getRules()) {
			//			System.out.println("LC IN "+r);
			if(trules.containsKey(r)) {
				int count = 0;
				int right = trules.get(r);

				Boolean left = leftR.get(r.getHead().getPredicate());
				if(left == null) {
					left = false;
				}

				Rule rightRec = new Rule();
				rightRec.setHead(r.getHead().deepCopy());

				ArrayList<Rule> rightRecs = new ArrayList<Rule>();
				rightRecs.add(rightRec);

				for(Atom b : r.getBody()) {
					if(b.getPredicate().equals(r.getHead().getPredicate())) {
						count ++;

						if((left && count != 1) || (!left && count != right)) {
							ArrayList<Rule> rs = headPredToRule.get(r.getHead().getPredicate());
							ArrayList<Rule> rewritten = new ArrayList<Rule>();
							for(Rule rr: rightRecs) {
								for(Rule r0: rs) {
									if(!r.equals(r0)) {
										Rule rrc = rr.deepCopy();

										String[] targetTerms = b.getTerms();
										String[] sourceTerms = r0.getHead().getTerms();

										UnionFindMap equivs = ruleToEquiv.get(rrc);
										if(equivs == null) {
											equivs = new UnionFindMap();
										} else {
											equivs = equivs.deepCopy();
										}

										HashMap<String,String> rewriting = new HashMap<String,String>();

										for(int i=0; i<sourceTerms.length; i++) {
											String old = rewriting.put(sourceTerms[i], targetTerms[i]);
											if(old!=null) {
												equivs.addEquivalences(old, targetTerms[i]);
											}
										}

										Rule r0w = r0.deepCopy();
										r0w.rewrite(rewriting, UNSEEN_PREFIX);

										for(Atom bw:r0w.getBody()) {
											rrc.addBodyAtom(bw.deepCopy());
										}

										ruleToEquiv.put(rrc, equivs);

										rewritten.add(rrc);
									}
								}
							}
							rightRecs = rewritten;
						} else {
							ArrayList<Rule> newRightRecs = new ArrayList<Rule>();
							for(Rule rr: rightRecs) {
								Rule rr0 = rr.deepCopy();
								rr0.addBodyAtom(b.deepCopy());
								newRightRecs.add(rr0);

								UnionFindMap equivs = ruleToEquiv.get(rr);
								if(equivs!=null && !equivs.isEmpty()) {
									ruleToEquiv.put(rr0,equivs);
								}
							}
							rightRecs = newRightRecs;
						}
					} else {
						ArrayList<Rule> newRightRecs = new ArrayList<Rule>();
						for(Rule rr: rightRecs) {
							Rule rr0 = rr.deepCopy();
							rr0.addBodyAtom(b.deepCopy());
							newRightRecs.add(rr0);

							UnionFindMap equivs = ruleToEquiv.get(rr);
							if(equivs!=null && !equivs.isEmpty()) {
								ruleToEquiv.put(rr0,equivs);
							}
						}
						rightRecs = newRightRecs;
					}
				}

				for(Rule rr: rightRecs) {
					UnionFindMap equivs = ruleToEquiv.get(rr);

					Rule rrw = Rule.rewriteEquivalences(rr, equivs);

					if(rrw != null) {
						//						System.out.println("LC OU "+rrw);
						linear.addRule(rrw);
					}
				}
			} else {
				//				System.out.println("LC OU "+r);
				linear.addRule(r.deepCopy());
			}
		}

		for(String decl: p.getDeclarations()) {
			linear.addDeclaration(decl);
		}

		if(constant) {
			Program replaced = new Program();

			// skip constant replacement for predicates 
			// that have a mix of variables and constants in a
			// goal body
			HashSet<String> skip = new HashSet<String>();
			for(Rule r: linear.getRules()) {
				if(r.getHead().getPredicate().equals(BaseRPQ2Datalog.QUERY_PRED)) {
					for(Atom b: r.getBody()) {
						Boolean left = leftR.get(b.getPredicate());
						if(left != null) {
							if(left) {
								if(!Utils.isInt(b.getTerms()[0])) {
									skip.add(b.getPredicate());
								}
							} else {
								if(!Utils.isInt(b.getTerms()[1])) {
									skip.add(b.getPredicate());
								}
							}
						}

					}
				}
			}

			//		System.out.println("Skipping "+skip);

			for(Rule r: linear.getRules()) {
				//			System.out.println("RE IN "+r);
				Rule nr = new Rule();

				Atom head = r.getHead();

				Boolean left = leftR.get(head.getPredicate());
				String replTo = replace.get(head.getPredicate());
				String replFrom = null;

				if(replTo!=null && !skip.contains(head.getPredicate())) {
					Atom nhead = new Atom(head.getPredicate(),head.getArity()-1);
					if(left) {
						nhead.addTerm(head.getTerms()[1]);
						replFrom = head.getTerms()[0];
					} else {
						nhead.addTerm(head.getTerms()[0]);
						replFrom = head.getTerms()[1];
					}
					nr.setHead(nhead);
				} else {
					nr.setHead(head);
				}

				HashMap<String,String> replaceMap = new HashMap<String,String>();

				for(Atom b: r.getBody()) {
					left = leftR.get(b.getPredicate());

					if(left!=null && !skip.contains(b.getPredicate())) {
						Atom nbody = new Atom(b.getPredicate(),b.getArity()-1);
						if(left) {
							nbody.addTerm(b.getTerms()[1]);
						} else {
							nbody.addTerm(b.getTerms()[0]);
						}
						nr.addBodyAtom(nbody);
					}  else if(replTo!=null  && !skip.contains(b.getPredicate())) {
						replaceMap.put(replFrom, replTo);

						Atom bc = b.deepCopy();
						nr.addBodyAtom(bc);
					} else {
						nr.addBodyAtom(b.deepCopy());
					}
				}

				nr.rewrite(replaceMap);

				replaced.addRule(nr);
				//			System.out.println("RE OU "+nr);
			}

			for(String decl: linear.getDeclarations()) {
				replaced.addDeclaration(decl);
			}

			return replaced;
		} else {
			return linear;
		}
	}

	//	private static Rule rewrite(Rule r0, String[] sourceTerms, String[] targetTerms, HashMap<Rule, UnionFindMap> ruleToEquiv) {
	//		Rule rw = new Rule();
	//
	//		HashMap<String,String> rewriting = new HashMap<String,String>();
	//
	//		for(int i=0; i<sourceTerms.length; i++) {
	//			rewriting.put(sourceTerms[i], targetTerms[i]);
	//		}
	//
	//		Atom rh = r0.getHead().deepCopy();
	//		rh.rewrite(rewriting, UNSEEN_PREFIX);
	//		rw.setHead(rh);
	//
	//		for(Atom b: r0.getBody()) {
	//			Atom bh = b.deepCopy();
	//			bh.rewrite(rewriting, UNSEEN_PREFIX);
	//			rw.addBodyAtom(bh);
	//		}
	//
	//		return rw;
	//	}

	public static boolean isInt(String s) {
		try {
			Integer.parseInt(s);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
}
