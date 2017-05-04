import java.io.FileReader;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
/**
 * @author mordechai
 *
 */
public class ResponseBuilder {
	private int code;
	private final double version = 1.1;
	private String codeDescription;
	private String payload;
	private HashMap<String, String> headers;
	//should replace with a clean way of opening from file, but this will have to do 
	private static JSONParser parser = new JSONParser();
	private static JSONObject responseCodes;

	
	public ResponseBuilder() {
		headers = new HashMap<>();
		try {
			responseCodes = (JSONObject) parser.parse(new FileReader("config/responseCodes.json"));
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}

	}

	/**
	 * @return the code
	 */
	public int getCode() {
		return code;
	}

	/**
	 * @param code the code to set
	 * @throws JSONException 
	 */
	public void setCode(int code) {
		this.code = code;
		codeDescription = (String) responseCodes.get("" + code);
	}

	/**
	 * @return the codeDescription
	 */
	public String getCodeDescription() {
		return codeDescription;
	}

	/**
	 * @param codeDescription the codeDescription to set
	 */
	public void setCodeDescription(String codeDescription) {
		this.codeDescription = codeDescription;
	}
	
	/**
	 * 
	 * @param key
	 * @param value
	 */
	public void addHeader(String key, String value) {
		headers.put(key, value);
	}
	
	/**
	 * 
	 * @param payload
	 */
	public void addPayload(String payload){
		this.payload = payload;
	}
	
	/**
	 * Removes the payload, so HEAD requests can just used caches responses for GETs 
	 * after removing the payload
	 */
	public void removePayload() {
		this.payload = null;
	}
	
	/**
	 * 
	 * @return
	 */
	public String[] build(){
		StringBuilder builder = new StringBuilder();
		builder.append("HTTP/" + version + " " + code + " " + codeDescription + " \r\n");
		String date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("GMT")));
		builder.append("Date: " + date + "\r\n");
		headers.forEach((key, val)->builder.append(key + ": " + val + "\r\n")); //#Modernish
		builder.append("\r\n");
		if (payload != null) {
			builder.append(payload);
		}
		String[] response = {builder.toString(), payload};
		return response;
	}

	public String getPayload() {
		return payload;
	}
}
