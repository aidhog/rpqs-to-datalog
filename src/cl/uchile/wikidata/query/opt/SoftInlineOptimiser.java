package cl.uchile.wikidata.query.opt;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import cl.uchile.wikidata.query.BaseRPQ2Datalog;
import cl.uchile.wikidata.query.Utils;
import cl.uchile.wikidata.query.datalog.Atom;
import cl.uchile.wikidata.query.datalog.Program;
import cl.uchile.wikidata.query.datalog.Rule;

public class SoftInlineOptimiser implements ProgramOptimiser {
	
	@Override
	public Program optimise(Program p) {
		TreeMap<String,Integer> ipreds = new TreeMap<String,Integer>();
		TreeSet<String> rpreds = new TreeSet<String>();
		
		Program optimised = new Program();
		
		for(String decl:p.getDeclarations()) {
			optimised.addDeclaration(decl);
		}
		
		for(Rule r: p.getRules()) {
			optimised.addRule(r.deepCopy());
			
			Atom head = r.getHead();
			if(head.getArity()>0) {
				if(!head.getPredicate().equals(BaseRPQ2Datalog.QUERY_PRED)) {
					ipreds.put(head.getPredicate(),head.getArity());
					
					for(Atom b: r.getBody()) {
						if(b.getPredicate().equals(head.getPredicate())) {
							rpreds.add(b.getPredicate());
						}
					}
				}
			}
		}
		
		for(Map.Entry<String, Integer> ipredArity :ipreds.entrySet()) {
			String ipred = ipredArity.getKey();
			int arity = ipredArity.getValue();
			if(!rpreds.contains(ipred)) {
				String def = ipred+"(";
				String var = "A";
				def += var;
				for(int i=1; i<arity; i++) {
					var = Utils.incrementUppercaseVar(var);
					def += ","+var;
				}
				def += ")";
				
				def += " -> ";
				var = "A";
				def += "int("+var+")";
				for(int i=1; i<arity; i++) {
					var = Utils.incrementUppercaseVar(var);
					def += ", int("+var+")";
				}
				def += ".";
				
				p.addDeclaration(def);
				p.addDeclaration("lang:derivationType[`" + ipred+ "] = \"Derived\".");
			}
		}
		
		return p;
	}
}
