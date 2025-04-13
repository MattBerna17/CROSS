import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Comparator;
import java.util.concurrent.*;

public class ServerMain {
    public static int tcpPort; // porta TCP su cui rimane in ascolto per nuovi utenti
    public static int udpPort; // porta UDP da cui il server invia le notifiche
    public static int CORE_THREAD_POOL_SIZE; // dimensione minima della thread pool
    public static int MAX_THREAD_POOL_SIZE; // dimensione massima della thread pool
    public static int KEEP_ALIVE; // tempo di keepalive (in ms)
    public static String ordersInput; // file di input da cui leggere gli ordini evasi
    public static String ordersOutput; // file di output su cui scrivere gli ordini evasi
    public static String usersInput; // file di input da cui leggere gli utenti registrati
    public static String usersOutput; // file di output su cui scrivere gli utenti registrati

    public static void main(String[] args) {
        final String CONFIG_DIR = "../config"; // path relativo alla directory contenente le configurazioni
        loadConfiguration(CONFIG_DIR + "/serverConfig.json");
        // creazione del server UDP
        ServerUDP udp = null;
        try {
            udp = new ServerUDP(udpPort);
        } catch (SocketException e) {
            System.err.println("[ERR] UDP Server could not be initialized: " + e.getMessage());
        }
        // creazione del gestore di persistenza
        final JsonPersistanceManager persistenceManager = new JsonPersistanceManager(ordersInput, ordersOutput, usersInput, usersOutput);
        ConcurrentLinkedQueue<User> users = persistenceManager.readUsers(); // caricamento utenti registrati
        ConcurrentLinkedQueue<Order> issuedOrders = persistenceManager.readOrders(); // caricamento ordini evasi
        // creazione di liste ordinate per ordini di ask, bid e stop orders attualmente in sospeso (che possono essere evasi)
        ConcurrentSkipListSet<Order> askOrders = new ConcurrentSkipListSet<>(new AskOrderComparator());
        ConcurrentSkipListSet<Order> bidOrders = new ConcurrentSkipListSet<>(new BidOrderComparator());
        ConcurrentLinkedQueue<Order> stopOrders = new ConcurrentLinkedQueue<>();

        // creazione della working queue contenente i task da eseguire
        LinkedBlockingQueue<Runnable> workingQueue = new LinkedBlockingQueue<>();
        try (ServerSocket serverSocket = new ServerSocket(tcpPort)) { // apertura della socket
            System.out.println("Server running on port " + tcpPort + "...");
            // creazione della thread pool
            Executor pool = new ThreadPoolExecutor(CORE_THREAD_POOL_SIZE, MAX_THREAD_POOL_SIZE,
                    KEEP_ALIVE, TimeUnit.MILLISECONDS, workingQueue);
            while (true) {
                // crea un nuovo task da eseguire con la connessione accettata sulla porta di welcome (tcpPort)
                // il ServerUDP, gestore di persistenza, lista di utenti registrati, ordini evasi e ordini della "sessione" sono comuni a tutti i thread che vengono eseguiti
                pool.execute(new ServerTask(serverSocket.accept(), udp, persistenceManager, users, issuedOrders, askOrders, bidOrders, stopOrders));
            }
        } catch (IOException e) {
            System.err.println("[ERR] Server could not be started: " + e.getMessage());
        }
    }

    /**
     * Metodo per il caricamento delle impostazioni di configurazione del server
     * @param path percorso al file json di configurazione del server
     */
    public static void loadConfiguration(String path) {
        Gson gson = new Gson();
        try (JsonReader configReader = new JsonReader(new FileReader(path))) {
            configReader.beginObject();
            configReader.nextName();
            configReader.beginObject();
            configReader.nextName();
            tcpPort = configReader.nextInt();
            configReader.nextName();
            udpPort = configReader.nextInt();
            configReader.nextName();
            configReader.beginObject();
            configReader.nextName();
            CORE_THREAD_POOL_SIZE = configReader.nextInt();
            configReader.nextName();
            MAX_THREAD_POOL_SIZE = configReader.nextInt();
            configReader.nextName();
            KEEP_ALIVE = configReader.nextInt();
            configReader.endObject();
            configReader.nextName();
            configReader.beginObject();
            configReader.nextName();
            ordersInput = configReader.nextString();
            configReader.nextName();
            ordersOutput = configReader.nextString();
            configReader.nextName();
            usersInput = configReader.nextString();
            configReader.nextName();
            usersOutput = configReader.nextString();
            configReader.endObject();
            configReader.endObject();
            configReader.endObject();
        } catch (IOException e) {
            System.err.println("[ERR] Error while opening users' configuration file: " + e.getMessage());
        }
    }
}


/**
 * Classe per l'ordinamento degli ask orders, ordinati per costo crescente, e a parità di costo per timestamp crescente.
 */
class AskOrderComparator implements Comparator<Order> {
    public int compare(Order o1, Order o2) {
        int res = Integer.compare(o1.getPrice(), o2.getPrice());
        if (res == 0) {
            return o1.getDate().compareTo(o2.getDate());
        }
        return res;
    }
}

/**
 * Classe per l'ordinamento degli bid orders, ordinati per costo decrescente, e a parità di costo per timestamp crescente.
 */
class BidOrderComparator implements Comparator<Order> {
    public int compare(Order o1, Order o2) {
        int res = Integer.compare(o2.getPrice(), o1.getPrice());
        if (res == 0) {
            return o1.getDate().compareTo(o2.getDate());
        }
        return res;
    }
}