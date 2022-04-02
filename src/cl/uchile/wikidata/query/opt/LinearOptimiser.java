package cl.uchile.wikidata.query.opt;

import java.util.ArrayList;
import java.util.HashMap;

import cl.uchile.wikidata.query.datalog.Atom;
import cl.uchile.wikidata.query.datalog.Program;
import cl.uchile.wikidata.query.datalog.Rule;

public class LinearOptimiser implements ProgramOptimiser {
	public static final String UNSEEN_PREFIX = "u";
	
	private boolean left;
	
	public LinearOptimiser(boolean left) {
		this.left = left;
	}

	@Override
	public Program optimise(Program p){
		Program optimised = new Program();

		HashMap<String,Integer> transP = new HashMap<String,Integer>();

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

		for(Rule r : p.getRules()) {
			if(trules.containsKey(r)) {
				int count = 0;
				int right = trules.get(r);

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

										Rule r0w = rewrite(r0, sourceTerms, targetTerms);

										for(Atom bw:r0w.getBody()) {
											rrc.addBodyAtom(bw.deepCopy());
										}
										
										rewritten.add(rrc);
									}
								}
							}
							rightRecs = rewritten;
						} else {
							for(Rule rr: rightRecs) {
								rr.addBodyAtom(b.deepCopy());
							}
						}
					} else {
						for(Rule rr: rightRecs) {
							rr.addBodyAtom(b.deepCopy());
						}
					}
				}

				for(Rule rr: rightRecs) {
					optimised.addRule(rr);
				}
			} else {
				optimised.addRule(r.deepCopy());
			}
		}


		for(String decl: p.getDeclarations()) {
			optimised.addDeclaration(decl);
		}

		return optimised;
	}

	private static Rule rewrite(Rule r0, String[] sourceTerms, String[] targetTerms) {
		Rule rw = new Rule();

		HashMap<String,String> rewriting = new HashMap<String,String>();

		for(int i=0; i<sourceTerms.length; i++) {
			rewriting.put(sourceTerms[i], targetTerms[i]);
		}

		Atom rh = r0.getHead().deepCopy();
		rh.rewrite(rewriting, UNSEEN_PREFIX);
		rw.setHead(rh);

		for(Atom b: r0.getBody()) {
			Atom bh = b.deepCopy();
			bh.rewrite(rewriting, UNSEEN_PREFIX);
			rw.addBodyAtom(bh);
		}

		return rw;
	}

	public static boolean isInt(String s) {
		try {
			Integer.parseInt(s);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
}
