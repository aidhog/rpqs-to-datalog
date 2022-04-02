package cl.uchile.wikidata.query.datalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import cl.uchile.wikidata.query.Utils;
import cl.uchile.wikidata.query.opt.HardInlineOptimiser.UnionFindMap;

public class Rule {
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((body == null) ? 0 : body.hashCode());
		result = prime * result + ((head == null) ? 0 : head.hashCode());
		
//		System.out.println("\tHC "+body+" "+head+" "+bodySet+" "+toString());
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
		Rule other = (Rule) obj;
		if (body == null) {
			if (other.body != null)
				return false;
		} else if (!body.equals(other.body))
			return false;
		if (head == null) {
			if (other.head != null)
				return false;
		} else if (!head.equals(other.head))
			return false;
		return true;
	}

	private Atom head;
	private ArrayList<Atom> body;
	private HashSet<Atom> bodySet;

	public Rule() {
		body = new ArrayList<Atom>();
		bodySet = new HashSet<Atom>();
	}

	public Atom getHead() {
		return head;
	}

	public void setHead(Atom head) {
		this.head = head;
	}

	public ArrayList<Atom> getBody() {
		return body;
	}

	public void setBody(ArrayList<Atom> body) {
		for(Atom b:body) {
			addBodyAtom(b);
		}
	}

	public void addBodyAtom(Atom ba) {
		if(bodySet.add(ba)) {
			this.body.add(ba);
		}
	}

	public String toString() {
		String str = "";
		if(head!=null) {
			str = head.toString();
		}

		if(body != null && !body.isEmpty()) {
			str += " <- ";
			str += body.get(0).toString();
			for(int i=1; i<body.size(); i++) {
				str += ", "+body.get(i).toString();
			}
		}

		str += ".";
		return str;
	}
	
	public Rule deepCopy() {
		Rule r = new Rule();
		r.setHead(head.deepCopy());
		
		for(Atom b : body) {
			r.addBodyAtom(b.deepCopy());
		}
		
		return r;
	}
	
	public static Rule parse(String s) {
		Rule r = new Rule();
		
		s = s.trim();
		if(s.contains("<-")) {
			String sh = s.substring(0,(s.indexOf("<")-1));
			Atom h = Atom.parse(sh);
			r.setHead(h);
			
			String bh = s.substring(sh.length()+3);
			String[] bss = bh.split(" ");
			
			for(String bs:bss) {
				if(!bs.trim().isEmpty()) {
					Atom b = Atom.parse(bs.replace('.', ' ').trim());
					r.addBodyAtom(b);
				}
			}
		} else {
			Atom h = Atom.parse(s.replace('.', ' ').trim());
			r.setHead(h);
		}
		return r;
	}
	
	public static boolean validateLonelyVariable(Rule r) {
		HashSet<String> hvars = new HashSet<String>();

		for(String ht: r.getHead().getTerms()) {
			if(!Utils.isInt(ht)) {
				hvars.add(ht);
			}
		}

		HashMap<String,Integer> bodyCount = new HashMap<String,Integer>();
		for(Atom b: r.getBody()) {
			HashSet<String> termSet = new HashSet<String>();
			for(String bs: b.getTerms()) {
				if(!Utils.isInt(bs)) {
					if(termSet.add(bs)) {
						Integer c = bodyCount.get(bs);
						if(c == null) {
							bodyCount.put(bs, 1);
						} else {
							bodyCount.put(bs, c+1);
						}
					}
				}
			}
		}

		for(Map.Entry<String, Integer> e : bodyCount.entrySet()) {
			if(e.getValue() == 1 && !hvars.contains(e.getKey())) {
				return false;
			}
		}
		
		return true;
	}
	
	public void rewrite(HashMap<String,String> rw) {
		rewrite(rw, "");
	}
	
	public void rewrite(HashMap<String,String> rw, String unseenPrefix) {
		head.rewrite(rw, unseenPrefix);
		
		// clear as the rewrite might affect
		// the hashcode of the rule
		bodySet.clear();
		
		for(Atom b:body) {
			b.rewrite(rw, unseenPrefix);
		}
		
		bodySet.addAll(body);
	}
	
	public static Rule rewriteEquivalences(Rule r, UnionFindMap equivs) {
		if(equivs!=null && !equivs.isEmpty()) {
			HashMap<String,String> rewriting = new HashMap<String,String>();
			HashMap<String,HashSet<String>> unionMap = equivs.getUnionMap();
			for(HashSet<String> ec : unionMap.values()) {
				// pick term from the equivalence class:
				// first pick constant,
				//  then variable that appears first in the head
				//    thereafter first variable in the body
				//      (otherwise random but should not happen)
				String pivot = null;
				int cons = 0;
				for(String e:ec) {
					if(Utils.isInt(e)) {
						cons ++;
						pivot = e;
					}
				}
				
				if(cons > 1) {
					return null;
				}
				
				if(pivot == null) {
					for(String t: r.getHead().getTerms()) {
						if(ec.contains(t)) {
							pivot = t;
							break;
						}
					}
				}
				
				if(pivot==null) {
					for(Atom b: r.getBody()) {
						for(String t: b.getTerms()) {
							if(ec.contains(t)) {
								pivot = t;
								break;
							}
						}
					}
				}
				
				if(pivot == null) {
					pivot = ec.iterator().next();
				}
				
				for(String e: ec) {
					if(!e.equals(pivot)) {
						rewriting.put(e,pivot);
					}
				}
			}
			
			Rule r0 = r.deepCopy();
			r0.rewrite(rewriting);
			
			return r0;
		} else {
			return r.deepCopy();
		}
	}
}
