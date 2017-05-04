import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 
 */

/**
 * @author mdjavaheri
 *
 */
public class ConnectionsManager {
	private int numConnections;
	private Socket[] sockets;
	private ReentrantLock[] locks;
	
	public ConnectionsManager(int n) {
		numConnections = n;
		sockets = new Socket[n];
		locks = new ReentrantLock[n];
		for (int x = 0; x < n; x++) {
			locks[x] = new ReentrantLock();
		}
	}
	
	/**
	 * Check for open sockets by linearly looking for the first opening
	 * @return the slot index of an open slot or -1 if none exist
	 * @throws InterruptedException 
	 */
	public int check() throws InterruptedException {
		int found = -1;
		int counter = 0;
		while (found == -1 && counter < numConnections) {
			locks[counter].lock();
			if (sockets[counter] == null) {
				found = counter;
			}
			locks[counter].unlock();
			counter++;
			if (counter == numConnections && found == -1) {
				synchronized(this){
					wait();
				}
				counter = 0;
			}
		}
		return found;
	}
	
	/**
	 * Checks for an available connection within the limit to add the socket to
	 * Waits if none are available
	 * @param socket the new socket to add, index of socket in the array
	 * @throws InterruptedException 
	 */
	public void addSocket(Socket socket, int index) throws InterruptedException {
		locks[index].lock();
		sockets[index] = socket;
		locks[index].unlock();
	}
	
	/**
	 * 
	 * @param index
	 */
	public void nullOut(int index) {
		locks[index].lock();
		sockets[index] = null;
		locks[index].unlock();
		synchronized(this) {
			notify();
		}
	}
}
