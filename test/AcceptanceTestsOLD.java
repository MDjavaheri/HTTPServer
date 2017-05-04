import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 
 */

/**
 * @author mordechai djavaheri
 *
 */
public class AcceptanceTestsOLD {
	OutputStream os;
	HttpURLConnection conn;
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * @throws IOException 
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws IOException {
		//May have to change this line depending on implementation
		String[] args = {"test/config.json"};
		HTTPServer.main(args);		
		URL url = new URL("http://localhost:613");
		conn = (HttpURLConnection) url.openConnection();
		conn.setDoOutput(true);
		conn.setConnectTimeout(1000);
		os = conn.getOutputStream();
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	// 1. REQUEST HEADER TESTS
	// 1.1 Unsupported HTTP Methods
	@Test
	public void unsupportedTrace() {
		try {
		    Files.copy(new File("test/trace.txt").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 501);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	@Test
	public void unsupportedOptions() {
		try {
		    Files.copy(new File("test/options.txt").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 501);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void unsupportedConnect() {
		try {
		    Files.copy(new File("test/connect.txt").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 501);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 405 comes with Allow header field, which has a list of those allowed on this resource. 
	 * Verify that it matches the ones specified in the config file
	 */
	@Test
	public void illegalMethod() {
		try {
		    Files.copy(new File("test/illegalMethodOnResource.txt").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 405);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// 1.2 Accept Headers
	/**
	 * When no available representations match the ones specified in the accept headers
	 * Either 406 or disregard the accept
	 * Write one per type of accept header
	 */
	@Test
	public void noAvailableReps() {
		try {
		    Files.copy(new File("test/noAvailableReps.txt").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 406);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// 1.3 URI
	/**
	 * URI exceeds maximum length
	 * Req: generate random URI string about 2000 bytes in size
	 * 414
	 */
	@Test
	public void uriLength() {
		char[] charArray = new char[2500];
		Arrays.fill(charArray, 'a');
		String str = "GET " + new String(charArray) + " HTTP/1.1";
		try {
			os.write(str.getBytes());
		    Files.copy(new File("test/uriTooLong.txt").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 414);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// 1.4 Payload
	/**
	 * Payload type unsupported
	 * 415
	 */
	@Test
	public void unsupportedPayloadType() {
		//עוד חזון למועד
	}

	/**
	 * Payload too large
	 * Req: set a max payload size and then send a message with a bigger one
	 * Objectively - 413 and close, no Retry-After header
	 * Temporarily - 413 with Retry-After header
	 */
	@Test
	public void payloadTooLarge() {
		try {
		    Files.copy(new File("test/payloadTooLarge.txt").toPath(), os);
		    Files.copy(new File("hugePayload.pdf").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 413);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//1.5 Expect
	
	/**
	 * Correct Header Value: "100-continue" is the only supported value
	 * Sends a request with an invalid Expect value, 417
	 */
	@Test
	public void invalidContinueVal() {
		try {
		    Files.copy(new File("test/invalidContinue.txt").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 413);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Server follows up with a final response code in addition to the 100
	 */
	@Test
	public void finalResponseReceived() {
		//עוד חזון למועד
	}

	//2.0 HTTP Method Specific Tests
	//2.1 GET
	
	/**
	 * successfully get data that does exist
	 */
	@Test
	public void getExisting() {		
		try {
			File index = new File("public_html/index.html");
			index.createNewFile();
			FileWriter fw = new FileWriter(index);
			fw.write("<h1>YOU FOUND ME</h1>");
			fw.close();
		    Files.copy(new File("test/getIndex.txt").toPath(), os);
		    os.flush();
			conn.connect();
			BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
			//TODO: read
			assert(conn.getResponseCode() == 200);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * unsuccessfully get data that does not exist
	 */
	@Test
	public void getNonExisting() {
		try {
		    Files.copy(new File("test/getNonExistent.txt").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 404);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//2.2 HEAD
	
	/**
	 * successfully get header data of a resource does exist, ensure no payload!
	 */
	@Test
	public void headExisting() {
		try {
		    Files.copy(new File("test/headIndex.txt").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 200);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * unsuccessfully get header data of a resource that does not exist
	 */
	@Test
	public void headNonExisting() {
		try {
		    Files.copy(new File("test/headIndex.txt").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 404);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
		
	//2.3 POST
	/**
	 * Successful POST - 201
	 * Also anything but 206, 304, and 416
	 */
	@Test
	public void postNew() {
		try {
		    Files.copy(new File("test/postSuccess.txt").toPath(), os);
		    os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 201);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Successful POST - 200
	 * Also anything but 206, 304, and 416
	 */
	@Test
	public void postExisting() {
		try {
			Files.copy(new File("test/postSuccess.txt").toPath(), os);
			os.flush();
			conn.connect();
			//Check that the text is "Hello World" once
			Files.copy(new File("test/postSuccess.txt").toPath(), os);
			os.flush();
			conn.connect();
			//Check that the text is "Hello World" twice
			assert(conn.getResponseCode() == 200);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Unsuccessful POST - 411
	 * Missing Content-Length header
	 */
	@Test
	public void postWithoutContentLength() {
		try {
			Files.copy(new File("test/postWithoutContentLength.txt").toPath(), os);
			os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 411);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//2.4 PUTs
	/**
	 * PUT new Data - 201
	 */
	@Test
	public void newPut() {
		try {
			Files.copy(new File("test/putSuccess.txt").toPath(), os);
			os.flush();
			conn.connect();
			//Check that the text is "<h1>201</h1><p>Put successfully!</p>" once
			assert(conn.getResponseCode() == 201);
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	/**
	 * PUT to replace existing data - 200 or 204
	 */
	@Test
	public void replacePut() {
		try {
			Files.copy(new File("test/putSuccess.txt").toPath(), os);
			os.flush();
			conn.connect();
			//Check that the text is "<h1>201</h1><p>Put successfully!</p>" once
			Files.copy(new File("test/putSuccess.txt").toPath(), os);
			os.flush();
			conn.connect();
			//Check that the text is "<h1>201</h1><p>Put successfully!</p>" once
			assert(conn.getResponseCode() == 200);
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	
	/**
	 * PUT with Content-Range field - 400
	 */
	@Test
	public void badPutHeader() {
		try {
			Files.copy(new File("test/putWithContentRange.txt").toPath(), os);
			os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 400);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Unsuccessful PUT - 411
	 * Missing Content-Length header
	 */
	@Test
	public void putWithoutContentLength() {
		try {
			Files.copy(new File("test/putWithoutContentLength.txt").toPath(), os);
			os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 411);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//2.5 DELETE
	/**
	 * Successful DELETE - 200
	 */
	@Test
	public void deleteExisting() {
		try {
			Files.copy(new File("test/postSuccess.txt").toPath(), os);
			os.flush();
			conn.connect();
			//Check that the text is "Hello World" once
			Files.copy(new File("test/deleteSuccess.txt").toPath(), os);
			os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 200);
			Files.copy(new File("test/getSuccess.txt").toPath(), os);
			os.flush();
			conn.connect();			
			assert(conn.getResponseCode() == 200);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Unsccessful DELETE - 200
	 */
	@Test
	public void deleteNonExisting() {
		try {
			Files.copy(new File("test/deleteNonExistent.txt").toPath(), os);
			os.flush();
			conn.connect();
			assert(conn.getResponseCode() == 404);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//2.6 TRACE
	//See 1.1
	//2.7 CONNECT
	//See 1.1
	//2.8 OPTIONS
	//See 1.1

	//3.0 Resource Related
	//3.1 Redirects
	/**
	 * GETs resource that has been moved
	 */
	@Test
	public void getRedirected() {
		//עוד חזון למועד
	}
	/**
	 * HEADs resource that has been moved
	 */
	@Test
	public void headRedirected() {
		//עוד חזון למועד
	}
	/**
	 * POSTs to resource that has been moved
	 */
	@Test 
	public void postRedirected() {
		//עוד חזון למועד
	}
	/**
	 * PUTs resource that has been moved
	 */
	@Test
	public void putRedirected() {
		//עוד חזון למועד
	}
	/**
	 * DELETEs resource that has been moved
	 */
	@Test 
	public void deleteRedirected() {
		//עוד חזון למועד
	}
	
	//3.2 User Request Issues
	/**
	 * 403 Forbidden/unauthorized
	 */
	@Test
	public void forbidden() {
		File dir = new File(HTTPServer.getDocumentRoot() + "files/");
		dir.mkdirs();
		File file = new File("index.html");
		file.setReadOnly();
		//try posting to it
	}
	
	/**
	 * 404, 410 Resource not found
	 */
	@Test
	public void cantFindResource() {
		//עוד חזון למועד
	}
	
	/**
	 * 409, 415 Conflict, inconsistent PUT and server side constraints
	 */
	
	//3.3 Content negotiation
	/**
	 * 409, 415 Inconsistent PUT representation and server side constraints on the resources
	 */
	
	//4.0 Stam
	/**
	 * No deprecated response codes
	 * Find a way to make sure never get 305 or 306
	 */
	@Test
	public void noDeprecateds() {
		//עוד חזון למועד
	}
	/**
	 * No timeouts 408
	 */
	@Test
	public void timeouts() {
		//עוד חזון למועד
	}
}
