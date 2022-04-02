package cl.uchile.wikidata.query.opt;

import cl.uchile.wikidata.query.Utils;
import cl.uchile.wikidata.query.datalog.Atom;
import cl.uchile.wikidata.query.datalog.GraphAtom;
import cl.uchile.wikidata.query.datalog.Program;
import cl.uchile.wikidata.query.datalog.Rule;

public class NodeOptimiser implements ProgramOptimiser {
	public static final String SUBJECT_PREDICATE = "_S";
	public static final String OBJECT_PREDICATE = "_O";
	
	public static final String SUBJECT_VARIABLE = "s";
	public static final String OBJECT_VARIABLE = "o";
	
	@Override
	public Program optimise(Program p){
		Program optimised = new Program();
		
		for(Rule r : p.getRules()) {
			Rule or = new Rule();
			or.setHead(r.getHead().deepCopy());
			
			if(r.getBody()!=null) {
				for(Atom b: r.getBody()) {
					if(b instanceof GraphAtom) {
						GraphAtom g = (GraphAtom) b;
						String sub = g.getSubject();

						if(Utils.isInt(g.getSubject())) {
							sub = SUBJECT_VARIABLE;
							
							Atom sC = new Atom(SUBJECT_PREDICATE,1);
							sC.addTerm(g.getSubject());
							Rule scR = new Rule();
							scR.setHead(sC);
							optimised.addRule(scR);
							
							Atom sV = new Atom(SUBJECT_PREDICATE,1);
							sV.addTerm(SUBJECT_VARIABLE);
							or.addBodyAtom(sV);
						}
						
						String obj = g.getObject();
						if(Utils.isInt(g.getObject())) {
							obj = OBJECT_VARIABLE;
							
							Atom oC = new Atom(OBJECT_PREDICATE,1);
							oC.addTerm(g.getObject());
							Rule ocR = new Rule();
							ocR.setHead(oC);
							optimised.addRule(ocR);
							
							Atom oV = new Atom(OBJECT_PREDICATE,1);
							oV.addTerm(OBJECT_VARIABLE);
							or.addBodyAtom(oV);
						}
						
						GraphAtom gNew = new GraphAtom(sub,g.getProperty(),obj);
						or.addBodyAtom(gNew);
					} else {
						or.addBodyAtom(b.deepCopy());
					}
				}
			}
			
			optimised.addRule(or);
		}
		
		for(String decl: p.getDeclarations()) {
			optimised.addDeclaration(decl);
		}
		
		return optimised;
	}
}
