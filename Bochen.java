import java.util.regex.Pattern;

/**
 * 
 */

/**
 * @author morde
 *
 */
public class Bochen {
//	Pattern Tchar = Pattern.compile("(!|#|\\$|%|&|'|\\*|\\+|-|\\.|\\^|`|\\||~|\\w)");


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String tchar = "(!|#|\\$|%|&|'|\\*|\\+|-|\\.|\\^|`|\\||~|\\w)";
		String token =  "(" + tchar + "+)";
		String OWS = "( |\\t)*";
		String CRLF =  "(\\r\\n)";
		String unreserved = "(\\w|-|\\.|~)";
		String hexdig = "(\\d|[A-F])";
		String pctEncoding = "(%" + hexdig + "{2})";
		String subDelims = "(!|\\$|&|'|\\(|\\)|\\+|,|;|=)";
		String pchar = "(" + unreserved + "|" + pctEncoding + "|" + subDelims + "|:|@)";
		String segment = "(" + pchar + "*)";
		String absolutePath = "((\\/" + segment + ")+)";
		String query = "((" + pchar + "|\\/|\\?)*)";
		String originForm = "(" + absolutePath + "(\\?" + query + ")?)";
		String obsText = "(%x[8-9A-F][0-9A-F])";
		String vchar = "([[:ascii:]])";
		String fieldVchar = "(" + vchar + "|" + obsText + ")";
		String fieldContent = "(" + fieldVchar + "(( |\\t)+" + fieldVchar + ")?)";
		String obsFold = CRLF + "( |\\t)+";
		String fieldValue = "(" + fieldContent + "|" + obsFold + ")";
		String fieldName = token;
		String LWS = "(" + CRLF + "?" + "( |\\t)+)";
		System.out.println("hi");
	}

}
