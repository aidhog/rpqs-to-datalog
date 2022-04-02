package cl.uchile.wikidata.query.datalog;

import java.util.Arrays;
import java.util.HashMap;

import cl.uchile.wikidata.query.Utils;

public class Atom {
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + arity;
		result = prime * result + defined;
		result = prime * result + ((predicate == null) ? 0 : predicate.hashCode());
		result = prime * result + Arrays.hashCode(terms);
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
		Atom other = (Atom) obj;
		if (arity != other.arity)
			return false;
		if (defined != other.defined)
			return false;
		if (predicate == null) {
			if (other.predicate != null)
				return false;
		} else if (!predicate.equals(other.predicate))
			return false;
		if (!Arrays.equals(terms, other.terms))
			return false;
		return true;
	}

	private final String predicate;
	private final String[] terms;
	private final int arity;
	private int defined;

	public Atom(String predicate, int arity) {
		this.predicate = predicate;
		terms = new String[arity];
		this.arity = arity;
	}
	
	public void addTerm(String s) {
		terms[defined] = s;
		defined++;
	}

	public void addTerms(String... s) {
		for(int i=0; i<s.length; i++) {
			addTerm(s[i]);
		}
	}

	public String getPredicate() {
		return predicate;
	}

	public String[] getTerms() {
		return terms;
	}

	public int getArity() {
		return arity;
	}

	public String toString() {
		String str = predicate+"(";
		if(arity!=0) {
			str += terms[0];
			for(int i=1; i<arity; i++) {
				if(terms[i] == null) {
					str += ",_";
				} else {
					str += ","+terms[i];
				}
			}
		}
		str += ")";
		return str;
	}
	
	public Atom deepCopy() {
		Atom c = new Atom(predicate,arity);
		c.addTerms(terms);
		c.defined = this.defined;
		return c;
	}
	
	public void rewrite(HashMap<String,String> rw) {
		rewrite(rw, "");
	}
	
	public void rewrite(HashMap<String,String> rw, String prefixUnseen ) {
		for(int i=0; i<defined; i++) {
			if(!Utils.isInt(terms[i])) {
				String rt = rw.get(terms[i]);
				if(rt==null) {
					terms[i] = prefixUnseen + terms[i];
				} else {
					terms[i] = rt;
				}
			}
		}
	}
	
	public static Atom parse(String s) {
		String pred = s.substring(0,s.indexOf('(')).trim();
		
		String terms = s.substring(s.indexOf('(')+1,s.indexOf(')')).replaceAll(" ","");
		
		if(!terms.isEmpty()) {
			String[] termArray = terms.split(",");
			Atom a = new Atom(pred,termArray.length);
			a.addTerms(termArray);
			return a;
		} else {
			return new Atom(pred,0);
		}
		
	}
}
