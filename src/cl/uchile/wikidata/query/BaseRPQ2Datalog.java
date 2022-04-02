package cl.uchile.wikidata.query;

import java.util.ArrayList;

import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.path.P_Alt;
import org.apache.jena.sparql.path.P_Distinct;
import org.apache.jena.sparql.path.P_FixedLength;
import org.apache.jena.sparql.path.P_Inverse;
import org.apache.jena.sparql.path.P_Link;
import org.apache.jena.sparql.path.P_Mod;
import org.apache.jena.sparql.path.P_Multi;
import org.apache.jena.sparql.path.P_NegPropSet;
import org.apache.jena.sparql.path.P_OneOrMore1;
import org.apache.jena.sparql.path.P_OneOrMoreN;
import org.apache.jena.sparql.path.P_ReverseLink;
import org.apache.jena.sparql.path.P_Seq;
import org.apache.jena.sparql.path.P_Shortest;
import org.apache.jena.sparql.path.P_ZeroOrMore1;
import org.apache.jena.sparql.path.P_ZeroOrMoreN;
import org.apache.jena.sparql.path.P_ZeroOrOne;
import org.apache.jena.sparql.path.Path;
import org.apache.jena.sparql.path.PathVisitor;

import cl.uchile.wikidata.query.datalog.Atom;
import cl.uchile.wikidata.query.datalog.GraphAtom;
import cl.uchile.wikidata.query.datalog.NodeAtom;
import cl.uchile.wikidata.query.datalog.Rule;

public class BaseRPQ2Datalog implements PathVisitor, RPQ2Datalog {
	public static final String SUBJECT_PRED = "S";
	public static final String OBJECT_PRED = "O";
	
	public static final String SUBJECT_VAR = "s";
	public static final String OBJECT_VAR = "o";
	
	public static final String QUERY_PRED = "_";
	
	private Node currentSubject;
	private Node currentObject;
	
	private String currentPredicate;

	private ArrayList<Rule> program;
	
	private String current_intermediate_var = "@";
	
	private static final String NODE_VAR = "n";

	public BaseRPQ2Datalog(Node subject, Node object) {
		currentSubject = subject;
		currentObject = object;
		currentPredicate = QUERY_PRED;
		
		program = new ArrayList<Rule>();
	}
	
	public static Atom getProjectedAtom(String pred, Node... ns) {
		ArrayList<Node> vars = new ArrayList<Node>();
		for(Node n:ns) {
			if(n instanceof Var) {
				vars.add(n);
			}
		}
		
		Atom head = new Atom(pred,vars.size());
		for(Node n: vars) {
			head.addTerm(ConvertRPQsToDatalog.sparqlToNumString(n));
		}
		
		return head;
	}
	
	public ArrayList<Rule> getProgram() {
		return program;
	}
	
	private void incrementIntermediateVar() {
		current_intermediate_var = Utils.incrementUppercaseVar(current_intermediate_var);
	}

	@Override
	public void visit(P_Link p) {
		ArrayList<Atom> constants = new ArrayList<Atom>();
		
		GraphAtom a = new GraphAtom(ConvertRPQsToDatalog.sparqlToNumString(currentSubject),ConvertRPQsToDatalog.sparqlToNumString(p.getNode()),ConvertRPQsToDatalog.sparqlToNumString(currentObject));
		Atom h = getProjectedAtom(currentPredicate,currentSubject,currentObject);
		
		Rule r = new Rule();
		r.addBodyAtom(a);
		for(Atom ca:constants) {
			r.addBodyAtom(ca);
		}
		r.setHead(h);
		
		program.add(r);
	}

	@Override
	public void visit(P_Inverse p) {	
		Node oldSubject = currentSubject;
		Node oldObject = currentObject;
		String oldPredicate = currentPredicate;
		
		currentPredicate = currentPredicate+"Inv";
		currentSubject = oldObject;
		currentObject = oldSubject;
		
		visit(p.getSubPath());
		
		
		Rule r = new Rule();
		Atom body = getProjectedAtom(currentPredicate,currentSubject,currentObject);
		r.addBodyAtom(body);
		
		currentSubject = oldSubject;
		currentObject = oldObject;
		currentPredicate = oldPredicate;
		
		Atom head = getProjectedAtom(currentPredicate,currentSubject,currentObject);
		r.setHead(head);
		
		program.add(r);
	}

	@Override
	public void visit(P_ZeroOrOne p) {
		Rule r = getZeroPathRule();
		if(r != null) {  // subject and object constant and different
			program.add(r);
		} 
		if(r == null || !r.getBody().isEmpty()) { // not same constant in subject and object
			visit(p.getSubPath());
		}
	}
	
	public Rule getZeroPathRule() {
		// maps all grpah nodes to current predicate with
		// correct arity (2 if both s and o are vars, 1 if one is a var, 0 otherwise)
		Rule r = new Rule();
		
		if(currentSubject instanceof Var && currentObject instanceof Var) {
			Atom nodeHead = new Atom(currentPredicate,2);
			nodeHead.addTerm(NODE_VAR);
			nodeHead.addTerm(NODE_VAR);
			r.setHead(nodeHead);
			
			Atom nodeBody = new NodeAtom(NODE_VAR);
			r.addBodyAtom(nodeBody);
		} else if (currentSubject instanceof Var || currentObject instanceof Var) {
			Node constant;
			if(!(currentSubject instanceof Var)) {
				constant = currentSubject;
			} else {
				constant = currentObject;
			}
			String cStr = ConvertRPQsToDatalog.sparqlToNumString(constant);
			
			Atom nodeHead = new Atom(currentPredicate,1);
			nodeHead.addTerm(cStr);
			r.setHead(nodeHead);
			
			Atom nodeBody = new NodeAtom(cStr);
			r.addBodyAtom(nodeBody);
		} else if(currentSubject.equals(currentObject)) {
			Atom nodeHead = new Atom(currentPredicate,0);
			r.setHead(nodeHead);
		} else {
			return null;
		}
		
		return r;
	}

	@Override
	public void visit(P_ZeroOrMore1 p) {
		Rule r = getZeroPathRule();
		if(r != null) {  // subject and object constant and different
			program.add(r);
		} 
		if(r == null || !r.getBody().isEmpty()) { // not same constant in subject and object
			visitTrans(p.getSubPath());
		}
	}

	@Override
	public void visit(P_OneOrMore1 p) {
		visitTrans(p.getSubPath());
	}
	
	private void visitTrans(Path subPath) {
		incrementIntermediateVar();
		
		String newVarL = current_intermediate_var+"L";
		String newVarR = current_intermediate_var+"R";
		String newVarM = current_intermediate_var+"M";
		
		Node oldSubject = currentSubject;
		Node oldObject = currentObject;
		String oldPredicate = currentPredicate;
		
		currentSubject = Var.alloc(newVarL);
		currentObject = Var.alloc(newVarR);
		currentPredicate = currentPredicate+"Trans"+current_intermediate_var;
		
		visit(subPath);
	
		Atom head = getProjectedAtom(currentPredicate, currentSubject, currentObject);
		Rule rTrans = new Rule();
		rTrans.setHead(head);
		
		Atom bodyL = new Atom(currentPredicate,2);
		bodyL.addTerm(ConvertRPQsToDatalog.sparqlToNumString(currentSubject));
		bodyL.addTerm(newVarM);
		rTrans.addBodyAtom(bodyL);
		
		Atom bodyR = new Atom(currentPredicate,2);
		bodyR.addTerm(newVarM);
		bodyR.addTerm(ConvertRPQsToDatalog.sparqlToNumString(currentObject));
		rTrans.addBodyAtom(bodyR);
		
		program.add(rTrans);
		
		currentSubject = oldSubject;
		currentObject = oldObject;
		
		Rule r = new Rule();
		Atom body = new Atom(currentPredicate,2);
		body.addTerm(ConvertRPQsToDatalog.sparqlToNumString(currentSubject));
		body.addTerm(ConvertRPQsToDatalog.sparqlToNumString(currentObject));
		r.addBodyAtom(body);
		
		currentPredicate = oldPredicate;
		head = getProjectedAtom(currentPredicate, currentSubject, currentObject);
		r.setHead(head);
		
		program.add(r);
	
		incrementIntermediateVar();
	}

	@Override
	public void visit(P_Alt p) {
		visit(p.getLeft());
		visit(p.getRight());
	}

	@Override
	public void visit(P_Seq p) {
		incrementIntermediateVar();
		
		Node oldSubj = currentSubject;
		Node oldObj = currentObject;
		String oldPred = currentPredicate;
		Node intermediateVar = Var.alloc(current_intermediate_var);
		
		String predicateLeft = currentPredicate+"SeqL"+current_intermediate_var;
		String predicateRight = currentPredicate+"SeqR"+current_intermediate_var;
		
		currentPredicate = predicateLeft;
		currentObject = intermediateVar;
		visit(p.getLeft());
		
		currentPredicate = predicateRight;
		currentSubject = intermediateVar;
		currentObject = oldObj;
		visit(p.getRight());
		
		currentSubject = oldSubj;
		currentObject = oldObj;
		currentPredicate = oldPred;
		
		Atom head = getProjectedAtom(currentPredicate,currentSubject,currentObject);
		
		Atom bodyL = getProjectedAtom(predicateLeft,currentSubject,intermediateVar);
		Atom bodyR = getProjectedAtom(predicateRight,intermediateVar,currentObject);	
		
		Rule r = new Rule();
		r.setHead(head);
		r.addBodyAtom(bodyL);
		r.addBodyAtom(bodyR);
		program.add(r);
	}

	public void visit(Path p) {
		if(p instanceof P_Link) {
			visit((P_Link)p);
		} else if (p instanceof P_ReverseLink) {
			visit((P_ReverseLink)p);
		} else if (p instanceof P_Inverse) {
			visit((P_Inverse)p);
		} else if (p instanceof P_ZeroOrOne) {
			visit((P_ZeroOrOne)p);
		} else if (p instanceof P_ZeroOrMore1) {
			visit((P_ZeroOrMore1)p);
		} else if (p instanceof P_ZeroOrMoreN) {
			visit((P_ZeroOrMoreN)p);
		} else if (p instanceof P_OneOrMore1) {
			visit((P_OneOrMore1)p);
		} else if (p instanceof P_OneOrMoreN) {
			visit((P_OneOrMoreN)p);
		} else if (p instanceof P_Alt) {
			visit((P_Alt)p);
		} else if (p instanceof P_Seq) {
			visit((P_Seq)p);
		}

		// System.err.println("Unrecognised path type "+p+" | "+p.getClass());
	}
	
	@Override
	public void visit(P_ReverseLink p) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(P_NegPropSet p) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(P_ZeroOrMoreN p) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(P_OneOrMoreN p) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void visit(P_Mod p) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(P_FixedLength p) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(P_Distinct p) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(P_Multi p) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(P_Shortest p) {
		throw new UnsupportedOperationException();
	}


}