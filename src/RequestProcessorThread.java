import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.stream.Stream;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * @author morde
 *
 */
public class RequestProcessorThread extends Thread {
	private Socket socket;
	private int socketNumber;
	private BufferedReader socketIn;
	private static int uriMaxLength = HTTPServer.getUriMaxLength();
	private static int payloadMaxSize = HTTPServer.getPayloadMaxSize();
	private static String docRoot = HTTPServer.getDocumentRoot();
	private ResponseBuilder rb;
	private ReadLock rwl;
	private String httpMethod, requestPath, fileName, payload, responsePayload;
	private File requestFile;
	private File targetFile;
	private HashMap<String, String> headers;
	private ArrayList<String> mimeTypes, formats, languages;
	private Charset payloadCharset;
	private LocalDateTime dt = LocalDateTime.now();
	private long receivedTime;
	private static JSONParser parser;
	private static JSONObject redirects, headerValidator, mimeValidator;
	
	/**
	 * Incoming version
	 */
	public RequestProcessorThread(Socket socket, int socketNumber) {
		super();
		this.socket = socket;
		this.setSocketNumber(socketNumber);
		setup();
		headers = new HashMap<>();
		rb = new ResponseBuilder();
		readSocket();
		payloadCharset = StandardCharsets.ISO_8859_1;
	}
	
	/**
	 * Recovery version
	 * TODO: Describe role
	 */
	public RequestProcessorThread(String recoveryFilePath, ReentrantReadWriteLock rwl) {
		super();
		setup();
		requestFile = new File(recoveryFilePath.toString());
		headers = new HashMap<>();
		rb = new ResponseBuilder();
		this.rwl = rwl.readLock();
		payloadCharset = StandardCharsets.ISO_8859_1;
	}
	
	public RequestProcessorThread(Path recoveryFilePath, ReentrantReadWriteLock rwl) {
		super();
		setup();
		requestFile = new File(recoveryFilePath.toString());
		headers = new HashMap<>();
		rb = new ResponseBuilder();
		this.rwl = rwl.readLock();
		payloadCharset = StandardCharsets.ISO_8859_1;
	}
	
	private static void setup() {
		try {
			parser = new JSONParser();
			headerValidator = (JSONObject) parser.parse(new FileReader("config/requestHeaders.json"));
			mimeValidator = (JSONObject) parser.parse(new FileReader("config/mimes.json"));
			redirects = (JSONObject) parser.parse(new FileReader("config/redirects.json"));
		} catch (IOException | ParseException e1) {
			e1.printStackTrace();
		}
	}
	
	public void readSocket() {
		receivedTime = System.currentTimeMillis();
		requestFile = new File("recovery/" + receivedTime + ".txt");
		try(
			FileWriter fw = new FileWriter(requestFile);
			BufferedWriter out = new BufferedWriter(fw); 
		){
			socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String line = socketIn.readLine();
			httpMethod = line.split(" ")[0];
			int contentLength = 0;
			while(!line.isEmpty()){
				if (line.startsWith("content-length")){
					contentLength = Integer.parseInt(line.split("content-length:")[1].trim());
				}
				out.write(line + "\n");
				line = socketIn.readLine().trim().toLowerCase();
			}
			out.write("\n");
			if (httpMethod.matches("^(POST|PUT)$")) { //TODO: read payload even without a content-length and not block
				while(contentLength > 0) {
					out.write((char) socketIn.read());
					contentLength--;
				}
			}
			socket.shutdownInput();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Where everything happens
	 */
	public void run() {
		if (rwl != null){//would rather this go in the constructor, but it leaves room for an error when unlocking
			rwl.lock();
		}
		
		if(generalParse()){
			if(headerParse()){
				execute();
			}
		}

		requestFile.delete();
		log();

		if(rwl != null) { //in recovery mode, nowhere to send a response anyway, so not going to finish it.
			rwl.unlock();
		}
		else {
			sendResponse(rb);
			try {
				socketIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	/**
	 * Sends the response builder response through the socket
	 * @param rsbdr
	 */
	private void sendResponse(ResponseBuilder rsbdr) {
		try{
			BufferedWriter headerWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1));
			String[] response = rsbdr.build();
			headerWriter.write(response[0]);
			headerWriter.flush();
			if (response[1] != null)  {
				BufferedWriter payloadWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), payloadCharset));
				payloadWriter.write(response[1]);
				payloadWriter.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/*
	 * PARSING & VALIDATION
	 */
	
	@SuppressWarnings("unchecked")
	private boolean generalParse() {
		try (
			BufferedReader in = Files.newBufferedReader(Paths.get(requestFile.getPath()));
		)
		{
			
			String requestLine = in.readLine();
			if(requestLine == null || requestLine.isEmpty()){
				Thread.currentThread().interrupt();//TODO: find a better way to abort, this doesn't really work
				return false;
			}
			
			String[] rlArray = requestLine.split(" ");
			if ((rlArray[0] + rlArray[1]).length() > uriMaxLength) {
				rb.setCode(414);
				return false;
			}
			
			
			//cut out path 
			httpMethod = rlArray[0];
			int lastSlash = rlArray[1].lastIndexOf("/");
			requestPath = rlArray[1].substring(0, lastSlash + 1);
			
			//Check for any redirects
			redirectCheck();
			
			//Add webroot to path
			requestPath = docRoot + requestPath;
			
			//Check file name
			fileName = rlArray[1].substring(lastSlash + 1, rlArray[1].length());
			
			if (fileName.equals("") && httpMethod.equals("GET")) {
				fileName = "index";
			}
			else if (fileName.equals("") && httpMethod.matches("^(POST|PUT)$")) {
				return fourHundred();
			}

			//Validate Request Line
			if (!httpMethod.matches((String) headerValidator.get("supported-methods"))){
				rb.setCode(501);
				return false;
			}
			else if(httpMethod.matches((String) headerValidator.get("unsupported-methods"))) {
				rb.setCode(405);
				return false;
			}
			else if(!requestLine.matches((String) headerValidator.get("request-line"))){
				return fourHundred();
			}

			//Validate headers
			String headerLine = in.readLine();
			String[] hlArray;
			String currentVal; //the current value for the given field in the headers map
			while (headerLine != null && !headerLine.isEmpty()) {
				if (headerLine.matches((String) headerValidator.get("header-field"))){
					hlArray = trimAndLower(headerLine.split(":"));
					currentVal = (String) headers.get(hlArray[0]);
					if(currentVal == null) {
						headers.put(hlArray[0], hlArray[1]);
					}
					else if (hlArray[0].matches("host") || hlArray[0].matches("content-length")) { 
						return fourHundred();
					}
					else{ //if a duplicate exists, append this value to it instead of replacing
						headers.put(hlArray[0], currentVal + ", " + hlArray[1]);
					}
				}
				else {
					return fourHundred();
				}			
				headerLine = in.readLine();
			}
			
			//get the payload if it exists, if content-length is missing, 411
			if (httpMethod.matches("^(POST|PUT)$")) { 
				String conLen = headers.get("content-length");
				if (conLen == null || conLen.isEmpty() || conLen.equals("0")) {
					rb.setCode(411);
					return false;
				}
				else {
					payload = "";
					String line = in.readLine();
					while(line != null) {
						payload += line;
						line = in.readLine();						
					}

					if (payload.getBytes().length > payloadMaxSize){
						rb.setCode(413);
						return false;
					}
				}
			}
			
			boolean deleteDirectory = httpMethod.equals("DELETE") && fileName.equals("");
			if (httpMethod.matches("^(POST|PUT|DELETE)$") && !deleteDirectory) {//Make sure this doesn't break DELETEing a directory
				boolean missingHeaders = !headers.containsKey("content-language") || !headers.containsKey("content-type");
				if (missingHeaders) {
					return fourHundred();
				}
				else {
					String[] format = ((headers.get("content-type").split(",")[0]).split(";")[0]).split("/");
					String ext = (String) ((HashMap<Object, Object>) mimeValidator.get(format[0])).get(format[1]);
					String language = headers.get("content-language").split(",")[0];
					fileName += ext + "_" + language;
				}
			}
		} catch (IOException e) {
			return false;
		}
		
		//Host must appear and be valid
		String host = headers.get("host");
		if (host == null || !host.matches((String) headerValidator.get("host"))) { 
			return fourHundred();
		}
				
		return true;
	}
	
	/**
	 * Checks path to redirect if necessary, responds with appropriate code
	 * TODO: Doesn't yet add appropriate redirect response headers, check for later
	 */
	private void redirectCheck() {
		JSONObject redirect = (JSONObject) redirects.get(requestPath);
		if (redirect != null) {
			requestPath = (String) redirect.get("newPath");
			ResponseBuilder redirectedResponse = new ResponseBuilder();
			redirectedResponse.setCode(((Long) redirect.get("code")).intValue());
			sendResponse(redirectedResponse);
		}
	}
	/**
	 * Parses and validates all the headers in the request. 
	 * Creates a prioritized list of formats and langauges
	 * @return 
	 */	
	private boolean headerParse() {
		headers.forEach((key,val) -> {
			String regex = (String) headerValidator.get(key);
			if(val != null && regex != null && !val.matches(regex)){
				rb.setCode(400);
			}
		});
		return rb.getCode() != 400;
	}

	/**
	 * Take the first charset in the list and set
	 * @param acceptableCharset
	 */
	private Charset charsetParse() {
		String charset = headers.containsKey("accept-charset") ? headers.get("accept-charset").split(",")[0].split(";")[0].trim() : "";
		if (charset.startsWith("utf-16")){
			return StandardCharsets.UTF_16;
		}
		else if (charset.equals("utf-8")) {
			return StandardCharsets.UTF_8;			
		}
		else if (charset.equals("us-ascii")){
			return StandardCharsets.US_ASCII;			
		}
		else {
			return StandardCharsets.ISO_8859_1;
		}
	}
	
	/**
	 * Figure out which file extensions to target based on the provided Accept header
	 */
	private void parseFormats() {
		if(headers.containsKey("accept")){
			mimeTypes = formatParse(headers.get("accept")); 
			formats = mimeToFileExt(mimeTypes); 
		}
	}
	
	/**
	 * figure out which languages to target based on the provided AcceptLanguages header
	 */
	private void parseLanguages(){
		if (headers.containsKey("accept-language")) {
			languages = splitLanguages(headers.get("accept-language"));
		}
	}
	/**
	 * Prioritizes the Accept header of the request into a priorized list of formats
	*/
	private ArrayList<String> formatParse(String acceptHeaderVal) {
		String[] formats = acceptHeaderVal.split(",");
		ArrayList<String[]> parsedFormats = new ArrayList<String[]>();
		String notStar = "\\w+[^\\*]*\\w+"; //for ease of regex
		String[] mediaRange, elData;
		String priority;
		int index;
		boolean inserted;
		for(String el: formats) {
			priority = "";
			elData = trimAndLower(el.split(";\\s*q="));
			if (elData.length == 1) {
				priority += 1000;
			}
			else {
				priority += Float.parseFloat(elData[1]) * 1000;
				priority = "0" + priority.split("\\.")[0];
			}

			mediaRange = elData[0].split(";");

			if (mediaRange[0].matches(notStar + "\\/" + notStar)){
				priority += 2;
			}
			else if (mediaRange[0].matches(notStar + "\\/\\*")){
				priority += 1;
			}
			else {
				priority += 0;
			}

			priority += mediaRange.length - 1;
			
			//See readme 
			String[] formatData = {mediaRange[0], priority};
			index = parsedFormats.size() - 1;
			inserted = false;
			while(!inserted){
				if (index == -1 || Integer.parseInt(formatData[1]) >= Integer.parseInt(parsedFormats.get(index)[1])){
					parsedFormats.add(index + 1, formatData);
					inserted = true;
				}
				else{
					index--;
				}
			}
		}

		ArrayList<String> mimeTypes = new ArrayList<>();
		parsedFormats.forEach(el->mimeTypes.add(el[0]));
		return mimeTypes;
	}

	/**
	 * Converts the given list of formats to extentions
	 * @param mimeTypes
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private ArrayList<String> mimeToFileExt(ArrayList<String> mimeTypes){
		ArrayList<String> fileExts = new ArrayList<>();
		int index = 0;
		boolean kochavNolad = false;
		int size = mimeTypes.size();
		String notStar = "\\w+[^\\*]*\\w+";
		String type;
		String[] mediaRange;

		while(index < size && !kochavNolad){
			type = mimeTypes.get(index);
			if (type.matches(notStar + "\\/" + notStar)){
				mediaRange = type.split("\\/");
				fileExts.add((String) ((HashMap<Object, Object>) mimeValidator.get(mediaRange[0])).get(mediaRange[1]));
			}
			else if (type.matches(notStar + "\\/\\*")) {
				type = type.split("\\/")[0];
				((HashMap<String, String>) mimeValidator.get(type)).forEach((name, ext) ->fileExts.add(ext));
			}
			else {
				mimeValidator.forEach((k,v)->{((HashMap<String, String>) (v)).forEach((x, y)->fileExts.add(y));});
				kochavNolad = true;
			}
			index++;
		}
		return fileExts;
	}

	/**
	 * Parses the Accept-Language header of the request into a prioritized list of languages
	 * @param fieldVal
	 * @return
	 */
	private ArrayList<String> splitLanguages(String fieldVal) {
		String[] languages = fieldVal.split(",");
		ArrayList<String[]> parsedLangs = new ArrayList<>();
		String[] elData;
		int index;
		boolean inserted;
		String priority;
		for(String el:languages) {
			priority = "";
			elData = el.split(";\\sq=");
			if (elData.length == 1) {
				priority += 1000;
			}
			else {
				priority += Float.parseFloat(elData[1]) * 1000;
				priority = priority.split(".")[0];
			}
			index = parsedLangs.size() - 1;
			inserted = false;
			while (!inserted) {
				String[] parsedData = {elData[0], priority};
				if (index == -1 || Integer.parseInt(priority) >= Integer.parseInt(parsedLangs.get(index)[1])) {
					parsedLangs.add(index + 1, parsedData);
					inserted = true;
				}
				else {
					index--;
				}
			}
		}

		ArrayList<String> langsList = new ArrayList<>();
		parsedLangs.forEach(langArray->langsList.add(langArray[0]));
		return langsList;
	}
	
	/*
	 * FILE FILTERING
	 */
	
	private boolean filter() {
		try {
			Optional<Path> path;
			if(formats != null && languages != null){
				path = filterFilesFormatLang();
			}
			else if (formats != null) {
				path = filterFilesFormatsOnly();
			}
			else if (languages != null){
				path = filterLanguagesOnly();
			}
			else {
				path = filterFilesDoubleStar();
			}
			if (path != null && path.isPresent()){
				Path targetPath = path.get();
				if (Files.notExists(targetPath, LinkOption.NOFOLLOW_LINKS)){
					rb.setCode(404);
				}
				else if (!Files.isReadable(path.get())) {
					rb.setCode(403);
				}
				else {
					targetFile = new File(targetPath.toString());		
					rb.setCode(200);
				}
			}
			else {
				rb.setCode(404);		
				return false;
			}
		}
		catch (NoSuchFileException e) {
			rb.setCode(404);
			return false;
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}
	
	private Optional<Path> filterFilesFormatLang() throws IOException {
		int formatIndex = 0;
		int languageIndex;
		Stream<Path> pathStream = null;
		Optional<Path> path = null;
		while(formatIndex < formats.size()) {
			languageIndex = 0;
			String fileFormat = fileName + formats.get(formatIndex);
			pathStream = Files.list(Paths.get(requestPath));
			if(pathStream.anyMatch(f -> f.getFileName().toString().startsWith(fileFormat))){
				pathStream.close();
				while(languageIndex < languages.size()) {
					pathStream = Files.list(Paths.get(requestPath));
					String fileLanguage = fileFormat + "-" + languages.get(languageIndex);
					if(pathStream.anyMatch(f -> f.getFileName().toString().startsWith(fileLanguage))){
						pathStream.close();
						pathStream = Files.list(Paths.get(requestPath));
						path = pathStream.filter(f -> f.getFileName().toString().startsWith(fileLanguage)).findAny();
						pathStream.close();
						return path;
					}
					else {
						pathStream.close();
						languageIndex++;
					}
				}
				languageIndex = 0;
			}
			formatIndex++;
		}
		if (path == null) {
			path = filterFilesFormatsOnly();//or 406
		}
		return path;
	}
	
	private Optional<Path> filterFilesDoubleStar() throws IOException {
		return Files.list(Paths.get(requestPath)).filter(f -> f.getFileName().toString().startsWith(fileName)).findAny();
	}
	
	private Optional<Path> filterFilesFormatsOnly() throws IOException {
		int formatIndex = 0;
		Stream<Path> pathStream = null;
		while (formatIndex < formats.size()){
			String fileFormat = fileName + formats.get(formatIndex);
			pathStream = Files.list(Paths.get(requestPath));
			if(pathStream.anyMatch(f -> f.getFileName().toString().startsWith(fileFormat))){
				pathStream.close();
				Optional<Path> path = Files.list(Paths.get(requestPath)).filter(f -> f.getFileName().toString().startsWith(fileFormat)).findAny();
				pathStream.close();
				return path;
			}
			formatIndex++;
		}
		return null;	
	}
	
	@SuppressWarnings("resource")
	private Optional<Path> filterLanguagesOnly() throws IOException {
		int languageIndex = 0;
		Stream<Path> pathStream = null;
		while (languageIndex < languages.size()) {
			String langName = fileName + "\\.\\w+_" + languages.get(languageIndex);
			pathStream = Files.list(Paths.get(requestPath));
			if(pathStream.anyMatch(f -> f.getFileName().toString().matches(langName))){
				pathStream.close();
				pathStream = Files.list(Paths.get(requestPath));
				Optional<Path> path = pathStream.filter(f -> f.getFileName().toString().matches(langName)).findAny();
				pathStream.close();
				return path;
			}
			languageIndex++;
			pathStream.close();
		}
		return null;
	}
	
	/*
	 * HTTP METHOD EXECUTION
	 */

	private void execute() {
		switch(httpMethod) { //TODO: declare as field
		case "GET":
			get();
			break;
		case "HEAD":
			head();
			break;
		case "POST":
			post();
			break;
		case "PUT":
			put();
			break;
		case "DELETE":
			delete();
			break;
		}
	}
	
	/**
	 * 
	 */
	private void get() {
		parseFormats();
		parseLanguages();
		if (filter()) {
			
			payloadCharset = charsetParse();
			
			try (
				FileInputStream fi = new FileInputStream(targetFile);
				FileChannel fc = fi.getChannel();
				BufferedReader bf = new BufferedReader(new InputStreamReader(fi));
			) {
				fc.lock(0L, Long.MAX_VALUE, true);
				String line = bf.readLine();
				while(line != null){
					payload += line + "\n";
					line = bf.readLine();
				}
				fc.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	
			String finalFileName = targetFile.getName().toString();
			int extIndex = finalFileName.lastIndexOf(".");
			int langIndex = extIndex < finalFileName.lastIndexOf("_") ? finalFileName.lastIndexOf("_") : finalFileName.length();
			
			String ext = finalFileName.substring(extIndex, langIndex);
			String type = findMimeType(ext);
			rb.addHeader("Content-Type", type + "; charset=" + payloadCharset);
			String language = finalFileName.substring(langIndex + 1, finalFileName.length());
			rb.addHeader("Content-Language", language); 
			rb.addHeader("Content-Length", "" + payload.length()); //TODO: figure out how to fit this to each charset
			if (HTTPServer.cache.containsKey(requestPath + finalFileName)) {
				rb.addPayload(HTTPServer.cache.get(requestPath + finalFileName).getPayload());
			}
			else {
				rb.addPayload(payload);
				HTTPServer.cache.insert(requestPath + finalFileName, rb);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private String findMimeType(String ext) {
		String mime = "";
		Set<String> types = mimeValidator.keySet();
		HashMap<String, String> subTypes; 
		Set<String> subTypesSet;
		for (String type : types) {
			subTypes = (HashMap<String, String>) mimeValidator.get(type);
			subTypesSet = subTypes.keySet();
			for (String subType : subTypesSet) {
				if (subTypes.get(subType).equals(ext)) {
					mime = type + "/" + subType;
				}
			}
		}
		return mime;
	}
	private void head() {
		get();
		rb.removePayload();
	}
	
	private void post() {
		findFile();
		try (
			FileOutputStream fi = rb.getCode() == 200 ? new FileOutputStream(targetFile, true) : new FileOutputStream(targetFile);
			FileChannel fc = fi.getChannel();
			BufferedWriter bf = rb.getCode() == 200 ? new BufferedWriter(new FileWriter(targetFile, true)) : new BufferedWriter(new FileWriter(targetFile));
		) {
			fc.lock(0L, Long.MAX_VALUE, false);
			bf.write(payload);
			fc.close();			
			bf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		HTTPServer.cache.blow(requestPath + targetFile.getName().toString());
	}
	
	private void put() {
		findFile();
		try (
			FileOutputStream fi = new FileOutputStream(targetFile);
			FileChannel fc = fi.getChannel();
			BufferedWriter bf = new BufferedWriter(new OutputStreamWriter(fi));
			) {
			fc.lock(0L, Long.MAX_VALUE, false);
			bf.write(payload);
			bf.close();
			fc.close();
			HTTPServer.cache.blow(requestPath + targetFile.getName().toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void delete() {
		try {
			targetFile = new File(requestPath + fileName);
			if (targetFile.isDirectory()) {//TODO prevent null pt ex here
				Files.list(targetFile.toPath()).forEach(p -> p.toFile().delete());		
				//Alternatively, File[] dirFiles = targetFile.list() and iterate, but this is cleaner
			}
			if (Files.deleteIfExists(targetFile.toPath())){
				rb.setCode(200);		
				HTTPServer.cache.blow(requestPath + targetFile.getName().toString());
			}
			else {
				rb.setCode(404);
			}
		}
		catch (SecurityException | AccessDeniedException e){
			rb.setCode(403);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Simply finds the targeted file for POSTs and PUTS
	 * If the file exists, returns true,
	 * if files doesn't exist, is unavailable, or protected, set the appropriate
	 * response code and return false
	 */
	private boolean findFile() {
		if (Files.notExists(Paths.get(requestPath + fileName), LinkOption.NOFOLLOW_LINKS)){
			rb.setCode(201);
		}
		else if (!Files.isWritable(Paths.get(requestPath + fileName))) {
			rb.setCode(403);
			return false;
		}
		else {
			rb.setCode(200);
		}		
		try {
			targetFile = new File(requestPath + fileName);	
			File dir = new File(requestPath);
			dir.mkdirs();
			if (!targetFile.exists()){
				targetFile.createNewFile();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}
	
	/**
	* A simple 400 helper method, as it appears so often.
	*/
	private boolean fourHundred() {
		rb.setCode(400);
		return false;
	}

	private String[] trimAndLower(String[] array) {
		for(int i = 0; i < array.length; i++) {
			array[i] = array[i].toLowerCase().trim();
		}
		return array;
	}

	private void log() {
		try	(BufferedWriter fw = new BufferedWriter(new FileWriter("log.txt", true))) {
			fw.write(dt + " " + httpMethod + " " + rb.getCode() + ": " + requestPath + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}

	/**
	 * @return the socketNumber
	 */
	public int getSocketNumber() {
		return socketNumber;
	}

	/**
	 * @param socketNumber the socketNumber to set
	 */
	public void setSocketNumber(int socketNumber) {
		this.socketNumber = socketNumber;
	}
}
