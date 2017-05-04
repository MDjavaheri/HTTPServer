import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class HttpRequest {
	Path requestFile;
	
	public HttpRequest(String request) {
		requestFile = Paths.get(request);
	}
	
	public int makeRequest() {
		try(BufferedReader br = Files.newBufferedReader(requestFile)) {
			String host = "http://localhost:613";
			String requestLine = br.readLine();
			String[] requestData = requestLine.split(" ");
			host += requestData[1].trim();
			String line;
			HttpURLConnection conn = (HttpURLConnection)(new URL(host)).openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod(requestData[0]);
			while(!(line = br.readLine()).equals("")) {
				String[] header = line.split(":");
				conn.setRequestProperty(header[0].trim(), header[1].trim());
			}
			String payload = "";
			while((line = br.readLine()) != null && !line.isEmpty()) {
				payload += line + "\n";
			}
			if(!payload.equals("")) {
				OutputStream os = conn.getOutputStream();
				OutputStreamWriter osw = new OutputStreamWriter(os);
				osw.write(payload);
				os.close();
				osw.close();
			}
			int responseCode = conn.getResponseCode();
			return responseCode;
		}
		catch(Exception e) {
			e.printStackTrace();
			return 0;
		}
	}
}
