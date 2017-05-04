import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
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
public class AcceptanceTests {
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
//		HTTPServer.main(args);
		
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
		    HttpRequest request = new HttpRequest("test/trace.txt");
		    assert(request.makeRequest() == 501);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	@Test
	public void unsupportedOptions() {
		try {
			HttpRequest request = new HttpRequest("test/options.txt");
		    assert(request.makeRequest() == 501);
		} catch (Exception e) {
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
			HttpRequest request = new HttpRequest("test/illegalMethod.txt");
		    assert(request.makeRequest() == 405);
		} catch (Exception e) {
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
		try {
			HttpRequest request = new HttpRequest("test/uriTooLong.txt");
		    assert(request.makeRequest() == 414);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// 1.4 Payload
	/**
	 * Payload too large
	 * Req: set a max payload size and then send a message with a bigger one
	 * Objectively - 413 and close, no Retry-After header
	 * Temporarily - 413 with Retry-After header
	 */
	@Test
	public void payloadTooLarge() {
		try {
			HttpRequest request = new HttpRequest("test/hugePayload.txt");
		    assert(request.makeRequest() == 413);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	//2.0 HTTP Method Specific Tests
	//2.1 GET
	
	/**
	 * Post a new file
	 */
	@Test
	public void postIndexNew() {		
		try {
			HttpRequest request = new HttpRequest("test/postIndex.txt");
		    assert(request.makeRequest() == 201);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * unsuccessfully get data that does not exist
	 */
	@Test
	public void getIndex() {
		try {
			HttpRequest request = new HttpRequest("test/getIndex.txt");
		    assert(request.makeRequest() == 200);
		} catch (Exception e) {
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
			HttpRequest request = new HttpRequest("test/headIndex.txt");
		    assert(request.makeRequest() == 200);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * unsuccessfully get header data of a resource that does not exist
	 */
	@Test
	public void headNonExisting() {
		try {
			HttpRequest request = new HttpRequest("test/headNonExistent.txt");
		    assert(request.makeRequest() == 404);
		} catch (Exception e) {
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
			HttpRequest request = new HttpRequest("test/postIndex.txt");
		    assert(request.makeRequest() == 200);
		} catch (Exception e) {
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
			HttpRequest request = new HttpRequest("test/postWithoutContentLength.txt");
		    assert(request.makeRequest() == 411);
		} catch (Exception e) {
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
			HttpRequest request = new HttpRequest("test/putSuccess.txt");
		    assert(request.makeRequest() == 201);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * PUT to replace existing data - 200 or 204
	 */
	@Test
	public void replacePut() {
		try {
			HttpRequest request = new HttpRequest("test/putSuccess.txt");
		    assert(request.makeRequest() == 200);
		} catch (Exception e) {
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
			HttpRequest request = new HttpRequest("test/putWithoutContentLength.txt");
		    assert(request.makeRequest() == 411);
		} catch (Exception e) {
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
			HttpRequest request = new HttpRequest("test/deleteSuccess.txt");
		    assert(request.makeRequest() == 200);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * Unsccessful DELETE - 200
	 */
	@Test
	public void deleteNonExisting() {
		try {
			HttpRequest request = new HttpRequest("test/deleteNonExistent.txt");
		    assert(request.makeRequest() == 404);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
