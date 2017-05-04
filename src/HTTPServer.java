import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * @author morde
 *
 */
public class HTTPServer {
	private static String configPath;
	private static int portNumber, uriMaxLength, payloadMaxSize;
	private static ConnectionsManager conMan;
	private static JSONParser parser = new JSONParser();
	private static JSONObject config;
	private static String documentRoot;
	protected static HTTPCache cache;
	/**
	 * Parse the config file
	 * Check if path is valid directory. If not, use current working directory of the server program
	 * Check for config file in the working directory. If present, use it. If not, use default hard-coded values for concurrency and cache size.
	 */
	public HTTPServer() {
		
	}
	
	/**
	 * @param args config.json path
	 *  Instantiate static ConnectionMaster object with max number
	 */
	public static void main(String[] args) {
		setup(args);
		recover();
		run();
	}
	
	/**
	 * Setup all the necessary config files
	 * @param args
	 */
	private static void setup(String[] args) {
		configPath = args.length > 0 ? args[0] : "config/config.json";
		try {
			config = (JSONObject) parser.parse(new FileReader(configPath));
		} catch (IOException | ParseException e1) {
			e1.printStackTrace();
		}
		portNumber = ((Long) config.get("port")).intValue(); 
		uriMaxLength = ((Long) config.get("uriMaxLength")).intValue(); 
		payloadMaxSize = ((Long) config.get("payloadMaxSize")).intValue(); 
		conMan = new ConnectionsManager(((Long) config.get("maxRequests")).intValue());
		cache = new HTTPCache(((Long) config.get("cacheSize")).intValue());
		documentRoot = ((String) config.get("documentRoot"));
	}
	
	/**
	 * 
	 */
	private static void recover() {
		ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
		try {
			Files.list(Paths.get("recovery/")).forEach(p -> {
				RequestProcessorThread thread = new RequestProcessorThread(p, rwl);
				thread.start();
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
		rwl.writeLock().lock(); //lock until all the threads are done.
   }

	private static void run() {
		int connectionIndex;
		Socket clientSocket;				
		try (
			ServerSocket serverSocket = new ServerSocket(portNumber);
		)
		{
			while (true) {
				connectionIndex = conMan.check();
				clientSocket = serverSocket.accept();
				conMan.addSocket(clientSocket, connectionIndex);
				RequestProcessorThread thread = new RequestProcessorThread(clientSocket, connectionIndex);
				thread.start();
			}
		} 
		catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * @return the documentRoot
	 */
	public static String getDocumentRoot() {
		return documentRoot;
	}
	/**
	 * @return the portNumber
	 */
	protected static int getPortNumber() {
		return portNumber;
	}

	/**
	 * @return the uriMaxLength
	 */
	protected static int getUriMaxLength() {
		return uriMaxLength;
	}

	/**
	 * @return the payloadMaxSize
	 */
	protected static int getPayloadMaxSize() {
		return payloadMaxSize;
	}
}
