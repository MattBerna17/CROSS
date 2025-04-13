import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.*;
import java.time.ZoneId;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Classe per la gestione della persistenza dei dati in file json.
 */
public class JsonPersistanceManager implements PersistanceManager {
    private String ordersFileOutput; // file di output dove memorizzare gli ordini evasi
    private String ordersFileInput; // file di input da cui  leggere gli ordini evasi
    private String usersFileOutput; // file di output dove memorizzare gli utenti registrati
    private String usersFileInput; // file di input da cui  leggere gli utenti registrati

    public JsonPersistanceManager(String ordersFileInput, String ordersFileOutput, String usersFileInput, String usersFileOutput) {
        this.ordersFileInput = ordersFileInput;
        this.ordersFileOutput = ordersFileOutput;
        this.usersFileInput = usersFileInput;
        this.usersFileOutput = usersFileOutput;
    }

    /**
     * Metodo per la memorizzazione degli ordini evasi nel file ordersFileOutput.
     * @param issuedOrders collezione che contiene gli ordini da memorizzare.
     */
    @Override
    public synchronized void storeIssuedOrders(Collection<Order> issuedOrders) {
        Gson gson = new Gson();
        try (JsonWriter ordersWriter = new JsonWriter(new FileWriter(this.ordersFileOutput))) {
            ordersWriter.beginObject();
            ordersWriter.name("trades");
            ordersWriter.beginArray();
            for (Order order: issuedOrders) {
                ordersWriter.beginObject();
                ordersWriter.name("orderId");
                ordersWriter.value(order.getId());
                ordersWriter.name("type");
                ordersWriter.value(order.getType());
                ordersWriter.name("orderType");
                ordersWriter.value(order.getOrderType());
                ordersWriter.name("size");
                ordersWriter.value(order.getInitialSize());
                ordersWriter.name("price");
                ordersWriter.value(order.getPrice());
                ordersWriter.name("timestamp");
                ordersWriter.value(order.getDate().atZone(ZoneId.systemDefault()).toEpochSecond());
                ordersWriter.endObject();
            }
            ordersWriter.endArray();
            ordersWriter.endObject();
        } catch (IOException e) {
            System.err.println("[ERR] Error while writing issued orders: " + e.getMessage());
        }
    }

    /**
     * Metodo per la memorizzazione degli utenti registrati nel file usersFileOutput
     * @param users
     */
    @Override
    public synchronized void storeUsers(Collection<User> users) {
        Gson gson = new Gson();
        try (JsonWriter usersWriter = new JsonWriter(new FileWriter(this.usersFileOutput))) {
            usersWriter.beginObject();
            usersWriter.name("users");
            usersWriter.beginArray();
            for (User user: users) {
                usersWriter.beginObject();
                usersWriter.name("username");
                usersWriter.value(user.getUsername());
                usersWriter.name("password");
                usersWriter.value(user.getPassword());
                usersWriter.endObject();
            }
            usersWriter.endArray();
            usersWriter.endObject();
        } catch (IOException e) {
            System.err.println("[ERR] Error while writing users: " + e.getMessage());
        }
    }

    /**
     * Metodo per il caricamento degli ordini dal file ordersFileInput
     * @return lista di ordini storici caricati dal file
     */
    @Override
    public synchronized ConcurrentLinkedQueue<Order> readOrders() {
        Gson gson = new Gson();
        // memorizzo lo storico degli ordini in una ConcurrentLinkedQueue di ordini (Orders)
        ConcurrentLinkedQueue<Order> issuedOrders = new ConcurrentLinkedQueue<>();
        try(JsonReader ordersReader = new JsonReader(new FileReader(this.ordersFileInput))) {
            ordersReader.beginObject();
            ordersReader.nextName();
            ordersReader.beginArray();
            while (ordersReader.hasNext()) {
                ordersReader.beginObject();
                ordersReader.nextName();
                int orderId = ordersReader.nextInt();
                ordersReader.nextName();
                String type = ordersReader.nextString();
                ordersReader.nextName();
                String orderType = ordersReader.nextString();
                ordersReader.nextName();
                int size = ordersReader.nextInt();
                ordersReader.nextName();
                int price = ordersReader.nextInt();
                ordersReader.nextName();
                long timestamp = ordersReader.nextLong();
                ordersReader.endObject();
                issuedOrders.add(new Order(orderId, type, orderType, size, price, timestamp));
            }
            ordersReader.endArray();
            ordersReader.endObject();
        } catch (IOException e) {
            System.err.println("[ERR] Error while opening orders' file: " + e.getMessage());
        }

        return issuedOrders;
    }

    /**
     * Metodo per il caricamento degli utenti dal file usersFileInput
     * @return lista degli utenti registrati caricati dal file
     */
    @Override
    public synchronized ConcurrentLinkedQueue<User> readUsers() {
        Gson gson = new Gson();
        ConcurrentLinkedQueue<User> users = new ConcurrentLinkedQueue<>();
        try (JsonReader usersReader = new JsonReader(new FileReader(this.usersFileInput))) {
            usersReader.beginObject();
            usersReader.nextName();
            usersReader.beginArray();
            while (usersReader.hasNext()) {
                usersReader.beginObject();
                usersReader.nextName();
                String username = usersReader.nextString();
                usersReader.nextName();
                String password = usersReader.nextString();
                usersReader.endObject();
                users.add(new User(username, password));
            }
            usersReader.endArray();
            usersReader.endObject();
        } catch (IOException e) {
            System.err.println("[ERR] Error while opening users' file: " + e.getMessage());
        }

        return users;
    }
}
