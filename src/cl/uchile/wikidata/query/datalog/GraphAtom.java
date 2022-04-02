package cl.uchile.wikidata.query.datalog;

public class GraphAtom  extends Atom {
	
	public static String GRAPH_PREDICATE = "E";
	
	public GraphAtom(String s, String p, String o) {
		super(GRAPH_PREDICATE, 3);
		addTerms(s,p,o);
	}
	
	public String getSubject() {
		return getTerms()[0];
	}
	
	public String getObject() {
		return getTerms()[2];
	}
	
	public String getProperty() {
		return getTerms()[1];
	}
	
	public GraphAtom deepCopy() {
		return new GraphAtom(getSubject(),getProperty(),getObject());
	}

}
