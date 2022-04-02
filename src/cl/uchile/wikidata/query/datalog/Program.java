package cl.uchile.wikidata.query.datalog;

import java.util.ArrayList;
import java.util.HashSet;

public class Program {
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((declDedup == null) ? 0 : declDedup.hashCode());
		result = prime * result + ((decls == null) ? 0 : decls.hashCode());
		result = prime * result + ((ruleDedup == null) ? 0 : ruleDedup.hashCode());
		result = prime * result + ((rules == null) ? 0 : rules.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Program other = (Program) obj;
		if (declDedup == null) {
			if (other.declDedup != null)
				return false;
		} else if (!declDedup.equals(other.declDedup))
			return false;
		if (decls == null) {
			if (other.decls != null)
				return false;
		} else if (!decls.equals(other.decls))
			return false;
		if (ruleDedup == null) {
			if (other.ruleDedup != null)
				return false;
		} else if (!ruleDedup.equals(other.ruleDedup))
			return false;
		if (rules == null) {
			if (other.rules != null)
				return false;
		} else if (!rules.equals(other.rules))
			return false;
		return true;
	}

	private final ArrayList<Rule> rules;
	private final ArrayList<String> decls;
	
	private HashSet<String> ruleDedup;
	private HashSet<String> declDedup;
	
	public Program() {
		this(new ArrayList<Rule>(),new ArrayList<String>());
	}
	
	public Program(ArrayList<Rule> rules) {
		this(rules,new ArrayList<String>());
	}

	public Program(ArrayList<Rule> rules, ArrayList<String> decls) {
		ruleDedup = new HashSet<String>();
		declDedup = new HashSet<String>();
		
		this.rules = new ArrayList<Rule>();
		for(Rule r : rules) {
			addRule(r);
		}
		
		this.decls = new ArrayList<String>();
		for(String d: decls) {
			addDeclaration(d);
		}
	}

	public ArrayList<Rule> getRules() {
		return rules;
	}

	public ArrayList<String> getDeclarations() {
		return decls;
	}
	
	public boolean addRule(Rule r) {
		if(ruleDedup.add(r.toString())) {
			return rules.add(r);
		}
		return false;
	}
	
	public boolean addDeclaration(String s) {
		if(declDedup.add(s)) {
			return decls.add(s);
		}
		return false;
	}
	
	public String toString() {
		String str = "";
		for(String d:decls) {
			str+=d+"\n";
		}
		if(!decls.isEmpty()) {
			str += "\n";
		}
		for(Rule r:rules) {
			str+=r+"\n";
		}
		return str;
		
	}
	
	public Program deepCopy() {
		Program copy = new Program();
		
		for(Rule r: this.rules) {
			copy.addRule(r.deepCopy());
		}
		
		for(String d: this.decls) {
			copy.addDeclaration(d);
		}
		
		return copy;
	}
}
