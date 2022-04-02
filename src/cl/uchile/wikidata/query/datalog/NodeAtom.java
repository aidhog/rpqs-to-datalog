package cl.uchile.wikidata.query.datalog;

public class NodeAtom extends Atom {
	public static final String NODE_PREDICATE = "V";
	
	public NodeAtom(String n) {
		super(NODE_PREDICATE, 1);
		addTerms(n);
	}
	
	public NodeAtom deepCopy() {
		return new NodeAtom(getTerms()[0]);
	}
}
