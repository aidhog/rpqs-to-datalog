package cl.uchile.wikidata.query;

public class Utils {
	public static String incrementUppercaseVar(String var) {
		int len = var.length();
		String newvar;
		if(var.charAt(len-1) == 'Z') {
			newvar = "A";
			for(int i=0; i<len; i++) {
				newvar += "A";
			}
		} else {
			newvar = var.substring(0,len-1) + ((char)(var.charAt(len-1)+1)); 
		}
		return newvar;
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
