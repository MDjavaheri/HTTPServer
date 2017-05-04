# HTTP Server
## Advanced Java, Spring 2017
Adam Brasch and Mordechai Djavaheri (actual coding done separately)
### Project Description

### Features
* GET, HEAD, POST, PUT, DELETE
### Things we're proud of
* On startup, the server checks the `/recovery` folder for any unfulfilled requests and blocks until they all finish via some snappy readLock/writeLock usage.
* `AcceptanceTests.java` takes the HTTP request samples from the `/test` folder and ensures that they all work against the server. In order to test, run both the server and the test suite separately from Eclipse.

### Assumptions Made/Be Aware of the Following
* `Accept` Headers: Though for specificity ordering, media type extensions were taken into account, our current implementation does not target them when selecting a resource a caveat, if `*/*` is present along with more specific values of lower q value, we will not honor the preference and just give everything a minimum qvalue of the `*/*`, not the more specific ones given the http standards are very unclear, we chose not to honor the more specific parts of the request if they have a lower `q` value than a `*/*` for more details, see *RFC 7231 Section 5.3.2*
* Internally, files are stored in the format of `name.extension_language`.
* Aliens must have tampered with our code, because `TRACE` and `OPTIONS` successfully return `501`, but `CONNNECT` goes through the entire thread and even sends the response, but our REST Client failed to show it.
* Planning Google Drive Folder: https://drive.google.com/drive/folders/0B2fBlHBfEFE9RjNES3gxYy1EMHM?usp=sharing


### Credit
* HTTP Reponse codes json mapped from GitHub for-GET/know-your-http-well: https://github.com/for-GET/know-your-http-well/blob/master/json/status-codes.json
