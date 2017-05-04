import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 
 */

/**
 * @author morde
 *
 */
public class HTTPCache {
	private MyLinkedHashMap<String, ResponseBuilder> cache;
	
	public HTTPCache() {
		cache = new MyLinkedHashMap<>(16, .75f, true, 100);
	}
	
	public HTTPCache(int cacheMaxSize) {
		cache = new MyLinkedHashMap<>(16, .75f, true, cacheMaxSize);
	}
	
	/**
	 * 
	 * @param path
	 * @return
	 */
	public ResponseBuilder get(String path) {
		return cache.get(path);		
	}
	
	/**
	 * 
	 * @param path
	 * @param response
	 * @return
	 */
	synchronized public void insert(String path, ResponseBuilder response) {
		cache.put(path, response);
	}
	
	/**
	 * 
	 * @param path
	 */
	synchronized public void blow(String path) {
		cache.remove(path);
	}
	
	synchronized public boolean containsKey(String path) {
		return cache.containsKey(path);
	}
	
	/**
	 * 
	 * @author baeldung.com/java-linked-hashmap
	 * Overwrite LinkedHashMap to delete eldest entry, as an LRU cache, as described in JavaDocs
	 * @param <K>
	 * @param <V>
	 */
	public class MyLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
		 
		private static final long serialVersionUID = 1L; //did this to make eclipse happy. Shalom Bayis.
		private int maxEntries = 5;
	 
	    public MyLinkedHashMap(
	      int initialCapacity, float loadFactor, boolean accessOrder, int maxEntries) {
	        super(initialCapacity, loadFactor, accessOrder);
	        this.maxEntries = maxEntries;
	    }
	 
	    @Override
	    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
	        return size() > maxEntries;
	    }
	 
	}
}
