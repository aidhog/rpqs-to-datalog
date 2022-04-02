package cl.uchile.wikidata.query.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

import cl.uchile.wikidata.query.BaseRPQ2Datalog;
import cl.uchile.wikidata.query.Utils;
import cl.uchile.wikidata.query.datalog.Atom;
import cl.uchile.wikidata.query.datalog.NodeAtom;
import cl.uchile.wikidata.query.datalog.Program;
import cl.uchile.wikidata.query.datalog.Rule;

public class PruneOptimiser implements ProgramOptimiser {
	@Override
	public Program optimise(Program p){
		Program optimised = new Program();
		TreeSet<String> sigs = new TreeSet<String>();

		for(Rule r : p.getRules()) {
			HashSet<String> guardedTerms = new HashSet<String>();
			for(Atom b: r.getBody()) {
				if(!b.getPredicate().equals(BaseRPQ2Datalog.QUERY_PRED) && 
						!b.getPredicate().equals(NodeOptimiser.SUBJECT_PREDICATE) &&
						!b.getPredicate().equals(NodeOptimiser.OBJECT_PREDICATE) &&
						!b.getPredicate().equals(NodeAtom.NODE_PREDICATE)) {
					for(String s: b.getTerms()) {
						guardedTerms.add(s);
					}
				}
			}

			Rule r0 = new Rule();
			r0.setHead(r.getHead().deepCopy());

			for(Atom b: r.getBody()) {
				if(b.getPredicate().equals(NodeAtom.NODE_PREDICATE)) {
					if(!guardedTerms.contains(b.getTerms()[0])){
						r0.addBodyAtom(b.deepCopy());
					}
				} else {
					r0.addBodyAtom(b.deepCopy());
				}
			}

			if(sigs.add(signature(r0))){
				optimised.addRule(r0);
			}
		}



		for(String decl: p.getDeclarations()) {
			optimised.addDeclaration(decl);
		}

		return optimised;
	}

	private static String signature(Rule r) {
		HashMap<String,Integer> can = new HashMap<String,Integer>();
		ArrayList<Atom> atoms = new ArrayList<Atom>();
		atoms.add(r.getHead());
		atoms.addAll(r.getBody());

		for(Atom a:atoms) {
			for(String s: a.getTerms()) {
				if(!Utils.isInt(s)) {
					Integer i = can.get(s);
					if(i == null) {
						can.put(s, can.size());
					}
				}
			}
		}

		Atom h0 = new Atom(r.getHead().getPredicate(),r.getHead().getArity());

		for(String s: r.getHead().getTerms()) {
			if(Utils.isInt(s)) {
				h0.addTerm(s);
			} else {
				Integer c = can.get(s);
				h0.addTerm("v"+c);
			}
		}

		TreeSet<String> body = new TreeSet<String>();
		for(Atom b:r.getBody()) {
			Atom b0 = new Atom(b.getPredicate(),b.getArity());
			for(String s: b.getTerms()) {
				if(Utils.isInt(s)) {
					b0.addTerm(s);
				} else {
					Integer c = can.get(s);
					b0.addTerm("v"+c);
				}
			}
			body.add(b0.toString());
		}

		return h0+"*"+body.toString(); 
	}
}