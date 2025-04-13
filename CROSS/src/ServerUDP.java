import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Classe per l'invio dei messaggi UDP lato server.
 */
public class ServerUDP {
    private DatagramSocket datagramSocket; // socket UDP usata per inviare i datagrammi

    public ServerUDP(int port) throws SocketException {
        this.datagramSocket = new DatagramSocket(port); // creo la socket sulla porta specificata
    }

    /**
     * Metodo per l'invio di notifiche per l'evasione degli ordini specificati
     * @param orders ordini evasi da notificare
     * @throws IOException
     */
    public synchronized void sendIssuedOrdersNotification(Collection<Order> orders) throws IOException {
        Iterator<Order> iterator = orders.iterator();
        Order order;
        JsonObject notification = new JsonObject();
        JsonArray trades = new JsonArray();
        // mantengo una lista di utenti da notificare
        ConcurrentSkipListSet<User> users = new ConcurrentSkipListSet<>(new UserComparator());
        while (iterator.hasNext()) {
            order = iterator.next();
            users.add(order.getOwner()); // aggiungo l'utente che possiede l'ordine alla lista di utenti da notificare
            JsonObject trade = new JsonObject();
            trade.addProperty("orderId", order.getId());
            trade.addProperty("type", order.getType());
            trade.addProperty("orderType", order.getOrderType());
            trade.addProperty("size", order.getInitialSize());
            trade.addProperty("price", order.getPrice());
            trade.addProperty("timestamp", order.getDate().atZone(ZoneId.systemDefault()).toInstant().getEpochSecond());
            trades.add(trade); // aggiungo l'oggetto json alla lista di trades da notificare
        }
        notification.add("trades", trades);
        byte[] buffer = notification.toString().getBytes();
        // per ogni utente da notificare, invio il datagramma UDP a tale utente sulla sua porta UDP di ascolto
        for (User user : users) {
            this.datagramSocket.send(new DatagramPacket(buffer, buffer.length, user.getAddress(), user.getPort()));
        }
    }

    /**
     * Metodo per l'invio di notifica all'utente disconnesso
     * @param user utente disconnesso
     * @throws IOException
     */
    public synchronized void sendExitNotification(User user) throws IOException {
        byte[] buffer = "EXIT".getBytes();
        // invio il messaggio "EXIT" all'utente che si è disconnesso sulla porta sulla quale rimane in ascolto
        this.datagramSocket.send(new DatagramPacket(buffer, buffer.length, user.getAddress(), user.getPort()));
    }

}

/**
 * Classe per la comparazione di due utenti. Serve a mantenere la struttura di ConcurrentSkipListSet usata sopra ordinata per username.
 */
class UserComparator implements Comparator<User> {
    public int compare(User o1, User o2) {
        return o1.getUsername().compareTo(o2.getUsername());
    }
}
