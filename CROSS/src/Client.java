import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

/**
 * Classe per la gestione della comunicazione (lato client) col server.
 */
public class Client {
    private final String CONFIG_DIR = "../config"; // percorso relativo alla cartella di configurazione
    private InetAddress serverAddress; // indirizzo del server
    private int serverTcpPort; // porta TCP del server
    private int serverUdpPort; // porta UDP del server
    private int timeout; // timeout (in ms) della connessione
    private int soTimeout; // timeout (in ms) delle operazioni nella connessione
    private Socket tcpSocket; // socket TCP per la comunicazione col server
    private BufferedWriter writer; // stream di scrittura della connessione
    private BufferedReader reader; // stream di lettura della connessione
    private ClientUDP udp; // thread per la gestione dell'ascolto delle notifiche UDP

    public Client() {
        loadConfiguration(CONFIG_DIR + "/userConfig.json"); // configuro le impostazioni del client dal file di configurazione
        try {
            // creo la socket del client e la connetto al server impostando i timeout
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(serverAddress, serverTcpPort), timeout);
            tcpSocket.setSoTimeout(soTimeout);
        } catch (IOException e) {
            System.err.println("[ERR] Error while connecting to server: " + e.getMessage());
        }
        // inizializzo gli stream di lettura e scrittura
        try {
            this.writer = new BufferedWriter(new OutputStreamWriter(tcpSocket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[ERR] Error while opening output stream: " + e.getMessage());
        }
         try {
             this.reader = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream(), StandardCharsets.UTF_8));
         } catch (IOException e) {
             System.err.println("[ERR] Error while opening input stream: " + e.getMessage());
         }
         this.udp = new ClientUDP(serverAddress, serverUdpPort); // inizializzo il ClientUDP
         new Thread(this.udp).start(); // avvio un thread per il ClientUDP (le notifiche vengono gestite in modo asincrono rispetto alla connessione TCP)
    }

    // #############################################
    // Ogni metodo è composto da 3 fasi:
    // 1. Scrittura dell'operazione da svolgere e in seguito della richiesta json sullo stream di output,
    // 2. Ricezione della risposta come stringa (formattata json),
    // 3. Conversione della stringa in JsonObject.
    // #############################################

    /**
     * Metodo per la gestione dell'invio e ricezione dei messsaggi per l'operazione di registrazione
     * @param username username scelto per la registrazione
     * @param password password scelta per la registrazione
     * @return risposta del server
     */
    public JsonObject register (String username, String password) {
        try {
            writer.write("register");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while sending action code: " + e.getMessage());
        }
        JsonObject query = new JsonObject();
        query.addProperty("username", username);
        query.addProperty("password", password);
        query.addProperty("UDPport", this.udp.getDs().getLocalPort()); // porta udp su cui il client rimane in ascolto per i messaggi di notifica
        try {
            writer.write(query.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while writing on the output stream: " + e.getMessage());
        }

        String response = "";
        try {
            response = reader.readLine();
        } catch (IOException e) {
            System.err.println("[ERR] Error while reading input stream: " + e.getMessage());
        }
        JsonObject result = JsonParser.parseString(response).getAsJsonObject();
        return result;
    }

    /**
     * Metodo per la gestione dell'invio e ricezione dei messsaggi per l'operazione di login
     * @param username username dell'account per il login
     * @param password password dell'account per il login
     * @return risposta del server
     */
    public JsonObject login (String username, String password) {
        try {
            writer.write("login");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while sending action code: " + e.getMessage());
        }
        JsonObject query = new JsonObject();
        query.addProperty("username", username);
        query.addProperty("password", password);
        query.addProperty("UDPport", this.udp.getDs().getLocalPort()); // come per la registrazione, per indicare la porta su cui il client rimane in ascolto per le notifiche
        try {
            writer.write(query.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while writing on the output stream: " + e.getMessage());
        }

        String response = "";
        try {
            response = reader.readLine();
        } catch (IOException e) {
            System.err.println("[ERR] Error while reading input stream: " + e.getMessage());
        }
        JsonObject result = JsonParser.parseString(response).getAsJsonObject();

        return result;
    }

    /**
     * Metodo per la gestione dell'invio e ricezione dei messsaggi per l'operazione di aggiornamento delle credenziali
     * @param username username dell'account con cui si è connessi
     * @param currentPassword password attuale (quella vecchia)
     * @param newPassword password nuova
     * @return risposta del server
     */
    public JsonObject updateCredentials (String username, String currentPassword, String newPassword) {
        try {
            writer.write("updateCredentials");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while sending action code: " + e.getMessage());
        }
        JsonObject query = new JsonObject();
        query.addProperty("username", username);
        query.addProperty("old_password", currentPassword);
        query.addProperty("new_password", newPassword);
        try {
            writer.write(query.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while writing on the output stream: " + e.getMessage());
        }

        String result = "";
        try {
            result = reader.readLine();
        } catch (IOException e) {
            System.err.println("[ERR] Error while reading input stream: " + e.getMessage());
        }
        JsonObject response = JsonParser.parseString(result).getAsJsonObject();
        return response;
    }

    /**
     * Metodo per la gestione dell'invio e ricezione dei messsaggi per l'operazione di logout
     * @param username username dell'account con cui si è connessi
     * @return risposta del server
     */
    public JsonObject logout (String username) {
        try {
            writer.write("logout");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while sending action code: " + e.getMessage());
        }
        String result = "";
        try {
            result = reader.readLine();
        } catch (IOException e) {
            System.err.println("[ERR] Error while reading input stream: " + e.getMessage());
        }
        JsonObject response = JsonParser.parseString(result).getAsJsonObject();
        return response;
    }

    /**
     * Metodo per la gestione dell'invio e ricezione dei messaggi per l'inserimento di un limit order
     * @param tipo tipo dell'ordine (ask o bid)
     * @param dimensione dimensione dell'ordine
     * @param prezzoLimite prezzo limite da pagare/ricevere
     * @return risposta del server
     */
    public JsonObject insertLimitOrder (String tipo, int dimensione, int prezzoLimite) {
        try {
            writer.write("insertLimitOrder");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while sending action code: " + e.getMessage());
        }
        JsonObject query = new JsonObject();
        query.addProperty("type", tipo);
        query.addProperty("size", dimensione);
        query.addProperty("price", prezzoLimite);
        try {
            writer.write(query.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while writing on the output stream: " + e.getMessage());
        }

        String result = "";
        try {
            result = reader.readLine();
        } catch (IOException e) {
            System.err.println("[ERR] Error while reading input stream: " + e.getMessage());
        }
        JsonObject response = JsonParser.parseString(result).getAsJsonObject();
        return response;
    }

    /**
     * Metodo per la gestione dell'invio e ricezione dei messaggi per l'inserimento di un market order
     * @param tipo tipo dell'ordine (ask o bid)
     * @param dimensione dimensione dell'ordine
     * @return risposta del server
     */
    public JsonObject insertMarketOrder (String tipo, int dimensione) {
        try {
            writer.write("insertMarketOrder");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while sending action code: " + e.getMessage());
        }
        JsonObject query = new JsonObject();
        query.addProperty("type", tipo);
        query.addProperty("size", dimensione);
        try {
            writer.write(query.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while writing on the output stream: " + e.getMessage());
        }

        String result = "";
        try {
            result = reader.readLine();
        } catch (IOException e) {
            System.err.println("[ERR] Error while reading input stream: " + e.getMessage());
        }
        JsonObject response = JsonParser.parseString(result).getAsJsonObject();
        return response;
    }

    /**
     * Metodo per la gestione dell'invio e ricezione dei messaggi per l'inserimento di uno stop order
     * @param tipo tipo dell'ordine (ask o bid)
     * @param dimensione dimensione dell'ordine
     * @param stopPrice prezzo soglia da pagare/ricevere
     * @return risposta del server
     */
    public JsonObject insertStopOrder (String tipo, int dimensione, int stopPrice) {
        try {
            writer.write("insertStopOrder");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while sending action code: " + e.getMessage());
        }
        JsonObject query = new JsonObject();
        query.addProperty("type", tipo);
        query.addProperty("size", dimensione);
        query.addProperty("price", stopPrice);
        try {
            writer.write(query.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while writing on the output stream: " + e.getMessage());
        }

        String result = "";
        try {
            result = reader.readLine();
        } catch (IOException e) {
            System.err.println("[ERR] Error while reading input stream: " + e.getMessage());
        }
        JsonObject response = JsonParser.parseString(result).getAsJsonObject();
        return response;
    }

    /**
     * Metodo per la gestione dell'invio e ricezione dei messaggi per la cancellazione di un ordine
     * @param orderID id dell'ordine da cancellare
     * @return risposta del server
     */
    public JsonObject cancelOrder (int orderID) {
        try {
            writer.write("cancelOrder");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while sending action code: " + e.getMessage());
        }
        JsonObject query = new JsonObject();
        query.addProperty("orderId", orderID);
        try {
            writer.write(query.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while writing on the output stream: " + e.getMessage());
        }

        String result = "";
        try {
            result = reader.readLine();
        } catch (IOException e) {
            System.err.println("[ERR] Error while reading input stream: " + e.getMessage());
        }
        JsonObject response = JsonParser.parseString(result).getAsJsonObject();
        return response;
    }

    /**
     * Metodo per la gestione dell'invio e ricezione dei messaggi per ottenere lo storico dei dati degli ordini evasi
     * @param mese mese di cui ricevere i dati
     * @return risposta del server
     */
    public JsonObject getPriceHistory (String mese) {
        try {
            writer.write("getPriceHistory");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while sending action code: " + e.getMessage());
        }
        JsonObject query = new JsonObject();
        query.addProperty("month", mese);
        try {
            writer.write(query.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while writing on the output stream: " + e.getMessage());
        }
        String result = "";
        try {
            result = reader.readLine();
        } catch (IOException e) {
            System.err.println("[ERR] Error while reading input stream: " + e.getMessage());
        }


        JsonObject response = JsonParser.parseString(result).getAsJsonObject();
        return response;
    }

    /**
     * Metodo per la gestione dell'invio e ricezione dei messaggi per disconnettersi dal server
     */
    public void exit() {
        try {
            writer.write("exit");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERR] Error while sending action code: " + e.getMessage());
        }
    }




    /**
     * Metodo per caricare la configurazione del client
     * @param path percorso al file contenente la configurazione del client
     */
    public void loadConfiguration(String path) {
        Gson gson = new Gson();
        try (JsonReader configReader = new JsonReader(new FileReader(path))) {
            configReader.beginObject();
            configReader.nextName();
            configReader.beginObject();
            configReader.nextName();
            configReader.beginObject();
            configReader.nextName();
            this.serverAddress = InetAddress.getByName(configReader.nextString());
            configReader.nextName();
            this.serverTcpPort = configReader.nextInt();
            configReader.nextName();
            this.serverUdpPort = configReader.nextInt();
            configReader.endObject();
            configReader.nextName();
            this.timeout = configReader.nextInt();
            configReader.nextName();
            this.soTimeout = configReader.nextInt();
            configReader.endObject();
            configReader.endObject();
        } catch (IOException e) {
            System.err.println("[ERR] Error while opening users' configuration file: " + e.getMessage());
        }
    }
}
