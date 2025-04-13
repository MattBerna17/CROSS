import com.google.gson.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Classe per la gestione della comunicazione (lato server) con un client.
 */
public class ServerTask implements Runnable {
    private Socket clientSocket; // socket usata per comunicare col client
    private BufferedReader reader; // stream di input della socket
    private BufferedWriter writer; // stream di output della socket
    private ConcurrentLinkedQueue<User> users; // lista degli utenti registrati
    private Collection<Order> issuedOrders; // lista degli ordini evasi
    private final JsonPersistanceManager persistenceManager; // gestore della persistenza
    private User user; // utente attualmente connesso a questa istanza di ServerTask
    private ConcurrentSkipListSet<Order> askOrders; // ordini ask attualmente in sospeso (che si possono evadere)
    private ConcurrentSkipListSet<Order> bidOrders; // ordini bid attualmente in sospeso (che si possono evadere)
    private ConcurrentLinkedQueue<Order> stopOrders; // stop orders attualmente in sospeso (che si possono evadere)
    private ServerUDP udp; // servizio UDP di comunicazione delle notifiche


    public ServerTask(Socket socket, ServerUDP udp, JsonPersistanceManager persistenceManager, ConcurrentLinkedQueue<User> users, Collection<Order> issuedOrders,
                      ConcurrentSkipListSet<Order> askOrders, ConcurrentSkipListSet<Order> bidOrders, ConcurrentLinkedQueue<Order> stopOrders) {
        this.clientSocket = socket;
        this.persistenceManager = persistenceManager;
        System.out.println("\tServerThread " + Thread.currentThread().getId() + " [INFO] Client connected: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
        this.users = users;
        this.issuedOrders = issuedOrders;
        this.user = null; // inizialmente non sappiamo quale sia l'account connesso all'utente collegato
        this.askOrders = askOrders;
        this.bidOrders = bidOrders;
        this.stopOrders = stopOrders;
        this.udp = udp;
        // ottengo gli stream di input e di output dalla socket
        try {
            this.reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while opening input stream: " + e.getMessage());
        }
        try {
            this.writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while opening output stream: " + e.getMessage());
        }

    }

    /**
     * Metodo eseguito in modo continuo per ottenere l'operazione che il client decide di eseguire
     */
    @Override
    public void run() {
        String action = "";
        do {
            // ottieni l'azione che il client ha scelto di eseguire (e.g. login, register, logout, ...)
            try {
                action = reader.readLine();
            } catch (IOException e) {
                System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while reading action code: " + e.getMessage());
            }
            // se action == null, la connessione è caduta in modo inaspettato, quindi chiudiamo la connessione lato server
            if (action == null) {
                System.out.println("\tServerThread " + Thread.currentThread().getId() + " [INFO] Client disconnected unexpectedly.");
                exit();
                break;
            }
            // in base all'azione, eseguiamo il metodo rispettivo
            switch (action) {
                case "login": login(); break;
                case "register": register(); break;
                case "updateCredentials": updateCredentials(); break;
                case "logout": logout(); break;
                case "getPriceHistory": getPriceHistory(); break;
                case "insertMarketOrder": insertMarketOrder(); break;
                case "insertLimitOrder": insertLimitOrder(); break;
                case "insertStopOrder": insertStopOrder(); break;
                case "cancelOrder": cancelOrder(); break;
                case "exit": exit(); break;
                default: // non dovrebbe mai andare qui
                    System.err.println("[ERR] Error while reading action to execute.");
                    break;
            }
        } while (!action.equals("exit")); // ripeti fino a che l'utente non si disconnette
    }

    // #############################################
    // Ogni metodo è composto da 4 fasi:
    // (0. Lettura dell'operazione da eseguire, eseguito nel metodo di run())
    // 1. Lettura della richiesta json sullo stream di output,
    // 2. Esecuzione dell'algoritmo per determinare una risposta
    // 3. Costruzione della risposta in formato json
    // 4. Scrittura sullo stream della risposta json
    // #############################################

    /**
     * Metodo per la registrazione di un utente
     */
    public void register() {
        String request = "";
        try {
            request = reader.readLine();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while reading input stream of register: " + e.getMessage());
        }

        JsonObject query = JsonParser.parseString(request).getAsJsonObject();
        String username = query.get("username").getAsString();
        String password = query.get("password").getAsString();

        JsonObject response = new JsonObject();
        boolean usernameExists = false;

        // uso un blocco synchronized sulla struttura dati users per accedervi in modo threadsafe
        synchronized (users) {
            for (User user : users) {
                // lo username è già stato usato, costruisco la risposta ed esco dal loop
                if (user.getUsername().equals(username)) {
                    usernameExists = true;
                    response.addProperty("response", 102);
                    response.addProperty("errorMessage", "Username not available.");
                    break;
                }
            }

            // se non è stato già usato, creo l'account, che viene assegnato a this.user, e lo memorizzo in users
            // inoltre, ottengo la porta udp dalla query e l'indirizzo dalla socket. costruisco il messaggio e lo invio
            if (!usernameExists) {
                this.user = new User(username, password);
                this.user.setOnline(true);
                this.users.add(this.user); // aggiungo l'utente nella lista degli utenti
                response.addProperty("response", 100);
                response.addProperty("errorMessage", "OK.");
                this.user.setAddress(this.clientSocket.getInetAddress());
                this.user.setPort(query.get("UDPport").getAsInt());
            }
        }

        // memorizzo in modo persistente i nuovi utenti (solo se ci sono stati cambiamenti)
        if (!usernameExists) {
            this.persistenceManager.storeUsers(this.users);
        }

        try {
            writer.write(response.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while sending response of register: " + e.getMessage());
        }
    }

    /**
     * Metodo per il login di un utente
     */
    public void login() {
        String request = "";
        try {
            request = reader.readLine();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while reading input stream of login: " + e.getMessage());
        }

        JsonObject query = JsonParser.parseString(request).getAsJsonObject();
        String username = query.get("username").getAsString();
        String password = query.get("password").getAsString();

        JsonObject response = new JsonObject();
        boolean userExists = false;

        // sincronizzo sulla struttura dati condivisa
        synchronized (users) {
            for (User user : users) {
                // se ho trovato un account che ha username e password uguali a quelle passate...
                if (user.getUsername().equals(username) && user.getPassword().equals(password)) {
                    userExists = true;
                    // se è online, quindi è usato da un altro utente, ritorno errore
                    if (user.isOnline()) {
                        response.addProperty("response", 102);
                        response.addProperty("errorMessage", "User already logged in.");
                    } else {
                        // altrimenti associo l'utente all'account specificato e ottengo porta udp e indirizzo
                        response.addProperty("response", 100);
                        response.addProperty("errorMessage", "OK.");
                        user.setOnline(true);
                        this.user = user; // Assign the current thread's user
                        this.user.setAddress(this.clientSocket.getInetAddress());
                        this.user.setPort(query.get("UDPport").getAsInt());
                    }
                    break;
                }
            }
        }

        // se non ho trovato l'account, ritorno errore
        if (!userExists) {
            response.addProperty("response", 101);
            response.addProperty("errorMessage", "Username/password mismatch or non-existent username.");
        }

        try {
            writer.write(response.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while sending response of login: " + e.getMessage());
        }
    }

    /**
     * Metodo per la modifica delle credenziali di un account
     */
    public void updateCredentials() {
        JsonObject query = new JsonObject();
        try {
            query = JsonParser.parseString(reader.readLine()).getAsJsonObject();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while reading input stream: " + e.getMessage());
        }

        String username = query.get("username").getAsString();
        String oldPassword = query.get("old_password").getAsString();
        String newPassword = query.get("new_password").getAsString();

        JsonObject response = new JsonObject();
        boolean userFound = false;

        // sincronizzo sulla collezione condivisa di utenti per accederci
        synchronized (users) {
            for (User user : users) {
                // se ho trovato l'account ed è online, ritorno errore, altrimenti aggiorno la password
                if (user.getUsername().equals(username) && user.getPassword().equals(oldPassword)) {
                    userFound = true;
                    if (user.isOnline()) {
                        response.addProperty("response", 104);
                        response.addProperty("errorMessage", "User currently logged in.");
                    } else {
                        user.setPassword(newPassword);
                        response.addProperty("response", 100);
                        response.addProperty("errorMessage", "OK.");
                    }
                    break;
                }
            }
        }

        // se non ho trovato l'utente ritorno errore
        if (!userFound) {
            response.addProperty("response", 102);
            response.addProperty("errorMessage", "Username/old password mismatch or non existent username.");
        } else {
            // altrimenti modifico la lista utenti e la memorizzo persistentemente
            this.persistenceManager.storeUsers(this.users);
        }

        try {
            writer.write(response.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while sending response of updateCredentials: " + e.getMessage());
        }
    }

    /**
     * Metodo per il logout dell'utente
     */
    public void logout() {
        JsonObject response = new JsonObject();
        // se non ha fatto login, ritorna errore, altrimenti imposta lo stato di online a falso e ritorna OK
        if (this.user == null) {
            response.addProperty("response", 101);
            response.addProperty("errorMessage", "User not logged in.");
        } else {
            this.user.setOnline(false);
            response.addProperty("response", 100);
            response.addProperty("errorMessage", "OK.");
        }
        try {
            writer.write(response.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while sending response of logout: " + e.getMessage());
        }

    }

    /**
     * Metodo per ottenere le informazioni sui prezzi per ogni giorno del mese specificato
     */
    public void getPriceHistory() {
        JsonObject query = new JsonObject();
        try {
            query = JsonParser.parseString(reader.readLine()).getAsJsonObject();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while reading input stream: " + e.getMessage());
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMMuuuu").withZone(ZoneId.systemDefault());
        LocalDate date = LocalDate.parse("01" + query.get("month").getAsString(), formatter);
        int numberOfDays = date.lengthOfMonth();

        JsonObject response = new JsonObject();
        response.addProperty("response", 100);
        response.addProperty("errorMessage", "OK.");
        JsonArray daysInfo = new JsonArray();

        // sincronizzo l'accesso a issuedOrders (che è condivisa)
        synchronized (issuedOrders) {
            // per ogni giorno del mese specificato...
            for (int i = 1; i <= numberOfDays; i++) {
                float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
                Order open = null, close = null;
                // per ogni ordine evaso...
                for (Order order : issuedOrders) {
                    // se la data dell'ordine corrisponde alla data di cui calcolare le statistiche in questa iterazione del for...
                    if (order.getDate().getDayOfMonth() == i
                            && order.getDate().getMonth() == date.getMonth()
                            && order.getDate().getYear() == date.getYear()) {
                        int price = order.getPrice();
                        // confronto il prezzo col massimo e il minimo e prendo, rispettivamente, il nuovo massimo e il nuovo minimo
                        if (price < min)
                            min = price;
                        if (price > max)
                            max = price;
                        // se l'ordine è il primo trovato oppure è prima del primo ordine trovato, riassegno
                        open = ((open == null) || (open.getDate().isAfter(order.getDate())) ? order : open);
                        // se l'ordine è il primo trovato oppure è dopo dell'ultimo ordine trovato, riassegno
                        close = ((close == null) || (close.getDate().isBefore(order.getDate())) ? order : close);
                    }
                }

                JsonObject infoDay = new JsonObject();
                // se c'è stato almeno un ordine nella giornata, inserisco i dati trovati
                if (open != null) {
                    infoDay.addProperty("open", open.getPrice());
                    infoDay.addProperty("close", close.getPrice());
                    infoDay.addProperty("min", min);
                    infoDay.addProperty("max", max);
                    JsonObject obj = new JsonObject();
                    obj.add(Integer.toString(i), infoDay);
                    daysInfo.add(obj); // aggiungo le info del giorno i-esimo all'array di info del mese
                } else {
                    // altrimenti, ritorno "none" per indicare che non ci sono stati valori nella giornata
                    infoDay.addProperty("open", "none");
                    infoDay.addProperty("close", "none");
                    infoDay.addProperty("min", "none");
                    infoDay.addProperty("max", "none");
                    JsonObject obj = new JsonObject();
                    obj.add(Integer.toString(i), infoDay);
                    daysInfo.add(obj); // aggiungo le info dell'i-esimo giorno
                }
            }
        }

        response.add("info", daysInfo);

        try {
            writer.write(response.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while sending response of getPriceHistory: " + e.getMessage());
        }
    }

    /**
     * Metodo di inserimento di un market order
     */
    public void insertMarketOrder() {
        JsonObject query = new JsonObject();
        try {
            query = JsonParser.parseString(reader.readLine()).getAsJsonObject();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while reading input stream: " + e.getMessage());
        }

        query.addProperty("orderType", "market");
        // controllo se posso eseguire il market order
        JsonObject response = marketIssue(query);
        // controllo se posso evadere degli stop order
        checkStopOrders();

        try {
            writer.write(response.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while sending response of insertMarketOrder: " + e.getMessage());
        }
    }

    /**
     * Metodo di inserimento di uno stop order
     */
    public void insertStopOrder() {
        JsonObject query = new JsonObject();
        try {
            query = JsonParser.parseString(reader.readLine()).getAsJsonObject();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while reading input stream: " + e.getMessage());
        }
        JsonObject response = new JsonObject();
        query.addProperty("orderType", "stop");
        if (query.get("type").getAsString().equals("ask")) {
            boolean stopCondition = false;
            // verifico se posso eseguire lo stop order inserito
            synchronized (this.bidOrders) {
                if (!this.bidOrders.isEmpty())
                    stopCondition = this.bidOrders.first().getPrice() <= query.get("price").getAsInt();
            }
            if (stopCondition) {
                // se posso, lo tratto come market issue
                response = marketIssue(query);
            } else {
                synchronized (this.stopOrders) {
                    // altrimenti lo inserisco negli stop orders in coda
                    stopOrders.add(new Order(query.get("type").getAsString(), "stop", query.get("size").getAsInt(), query.get("price").getAsInt(), this.user));
                }
                response.addProperty("orderId", -2);
            }
        } else if (query.get("type").getAsString().equals("bid")) {
            // altrimenti, se è un bid, controllo negli ask orders se posso eseguirlo
            boolean stopCondition = false;
            synchronized (this.askOrders) {
                 if (!this.askOrders.isEmpty())
                    stopCondition = this.askOrders.first().getPrice() >= query.get("price").getAsInt();
            }
            // se posso eseguirlo, lo tratto come un market order
            if (stopCondition) {
                response = marketIssue(query);
            } else {
                // altrimenti lo inserisco nella coda degli stop order in attesa di evasione
                synchronized (this.stopOrders) {
                    stopOrders.add(new Order(query.get("type").getAsString(), "stop", query.get("size").getAsInt(), query.get("price").getAsInt(), this.user));
                }
                response.addProperty("orderId", -2);
            }
        }

        try {
            writer.write(response.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while sending response of insertMarketOrder: " + e.getMessage());
        }
    }

    /**
     * Metodo di inserimento di un limit order
     */
    public void insertLimitOrder() {
        JsonObject query = new JsonObject();
        try {
            query = JsonParser.parseString(reader.readLine()).getAsJsonObject();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while reading input stream: " + e.getMessage());
        }

        String type = query.get("type").getAsString();
        int size = query.get("size").getAsInt();
        int price = query.get("price").getAsInt();
        int sizeCopy = size;
        JsonObject response = new JsonObject();

        // creo una lista di ordini modificati per memorizzare gli ordini della searchListSet che posso evadere
        // nell'evasione del limit order passato
        ArrayList<Order> changedOrders = new ArrayList<>();
        // lista di ordini in cui devo andare a cercare per evadere l'ordine
        ConcurrentSkipListSet<Order> searchListSet = (type.equals("ask") ? bidOrders : askOrders);
        // lista di ordini in cui devo inserire il limit order se non posso evaderlo
        ConcurrentSkipListSet<Order> insertListSet = (type.equals("ask") ? askOrders : bidOrders);
        // lista di pezzi in cui il limit order viene diviso se posso evaderlo
        ArrayList<Order> limitPieces = new ArrayList<>();

        // sincronizzo sulla lista di ricerca (controllo se posso evadere l'ordine prima di inserirlo)
        synchronized (searchListSet) {
            Iterator<Order> iterator = searchListSet.iterator();
            // finché ci sono ordini nella search list e la dimensione del limit order è > 0
            while (iterator.hasNext() && size > 0) {
                Order order = iterator.next();
                // se l'ordine che sto analizzando ora nella search list è candidato per far evadere il limit order da creare...
                if ((type.equals("ask") && order.getPrice() >= price) ||
                        (type.equals("bid") && order.getPrice() <= price)) {
                    // aggiorno la dimensione del limit order da evadere
                    int min = Math.min(size, order.getSize());
                    size -= min;
                    // aggiungo l'ordine della search list a quelli che potenzialmente uso per far evadere il limit order
                    changedOrders.add(order);
                }
            }
            // se sono riuscito a evadere il limit order...
            if (size == 0) {
                synchronized (searchListSet) {
                    // aggiorno gli ordini della lista di ordini usati per evadere il limit order
                    for (Order changedOrder: changedOrders) {
                        int min = Math.min(sizeCopy, changedOrder.getSize());
                        sizeCopy -= min;
                        // inserisco i pezzi di limit order evasi
                        limitPieces.add(new Order(type, "limit", min, changedOrder.getPrice(), this.user));
                        changedOrder.setSize(changedOrder.getSize() - min);
                        // se la dimensione dell'ordine analizzato è 0, lo rimuovo dalla lista degli ordini di ricerca (perché è evaso)
                        if (changedOrder.getSize() == 0) {
                            searchListSet.remove(changedOrder);
                        }
                    }
                    // se l'ultimo ordine non ha dimensione 0, lo aggiorno senza rimuoverlo dalla search list e senza aggiungerlo agli ordini evasi
                    if (changedOrders.get(changedOrders.size()-1).getSize() > 0) {
                        changedOrders.remove(changedOrders.size()-1);
                    }
                }
                response.addProperty("orderId", limitPieces.get(limitPieces.size()-1).getId());
                changedOrders.addAll(limitPieces); // aggiungo i pezzi del limit order evasi a tutti gli ordini modificati
                synchronized (issuedOrders) {
                    issuedOrders.addAll(changedOrders);
                }
                // invio le notifiche degli ordini modificati
                sendIssuedOrdersNotification(changedOrders);
                // aggiorno la persistenza
                persistenceManager.storeIssuedOrders(issuedOrders);
            } else if (!iterator.hasNext()) {
                // se invece non posso evadere l'ordine, lo inserisco normalmente nella lista di inserimento
                Order order = new Order(type, "limit", sizeCopy, price, this.user);
                synchronized (insertListSet) {
                    insertListSet.add(order);
                }
                response.addProperty("orderId", order.getId());
            }
        }

        checkStopOrders(); // controllo se, con l'aggiornamento degli ordini, posso eseguire qualche stop order

        try {
            writer.write(response.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while sending response of insertLimitOrder: " + e.getMessage());
        }
    }

    /**
     * Metodo di cancellazione di un ordine
     */
    public void cancelOrder() {
        JsonObject query = new JsonObject();
        try {
            query = JsonParser.parseString(reader.readLine()).getAsJsonObject();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while reading input stream: " + e.getMessage());
        }
        int orderId = query.get("orderId").getAsInt();
        boolean found = false;
        Order order = null;

        synchronized (askOrders) {
            for (Order o: askOrders) {
                if (o.getId() == orderId && o.getOwner().getUsername().equals(this.user.getUsername())) {
                    order = o;
                    found = true;
                }
            }
            if (found) {
                askOrders.remove(order);
            }
        }
        if (!found) {
            synchronized (bidOrders) {
                for (Order o: bidOrders) {
                    if (o.getId() == orderId && o.getOwner().getUsername().equals(this.user.getUsername())) {
                        order = o;
                        found = true;
                    }
                }
                if (found) {
                    bidOrders.remove(order);
                }
            }
        }

        JsonObject response = new JsonObject();
        if (found) {
            response.addProperty("response", "100");
            response.addProperty("errorMessage", "OK.");
        } else {
            response.addProperty("response", "101");
            response.addProperty("errorMessage", "Order does not exist or belongs to different user or has already been finalized.");
        }

        try {
            writer.write(response.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while sending response of cancelOrder: " + e.getMessage());
        }
    }

    /**
     * Metodo per chiudere la connessione TCP col client
     */
    public void exit() {
        this.user.setOnline(false); // cambio lo stato dell'account a offline
        // chiudo la socket e gli stream relativi
        try {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            // invio la notifica di uscita al client
            this.udp.sendExitNotification(this.user);
            System.out.println("\tServerThread " + Thread.currentThread().getId() + " [INFO] Client disconnected: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
        } catch (IOException e) {
            System.err.println("ServerThread " + Thread.currentThread().getId() + " [ERR] Error while closing connection: " + e.getMessage());
        }
    }

    /**
     * Metodo per controllare si possono far evadere degli stop order in coda
     */
    private void checkStopOrders() {
        // sincronizzo sugli stop order
        synchronized (stopOrders) {
            Iterator<Order> iterator = this.stopOrders.iterator();
            Order stopOrder;
            while (iterator.hasNext()) {
                stopOrder = iterator.next();
                // prendo gli ordini in cui devo andare a cercare per controllare se posso evadere lo stop order
                // che sto attualmente analizzando
                ConcurrentSkipListSet<Order> orders = (stopOrder.getType().equals("ask") ? bidOrders : askOrders);
                synchronized (orders) { // sincronizzo su tali ordini
                    // se posso evadere lo stop order (perché vale la condizione espressa riguardo lo stop order)
                    if ((stopOrder.getType().equals("ask") && !this.bidOrders.isEmpty() && this.bidOrders.first().getPrice() <= stopOrder.getPrice())
                            || (stopOrder.getType().equals("bid") && !this.askOrders.isEmpty() && this.askOrders.first().getPrice() >= stopOrder.getPrice())) {
                        JsonObject order = new JsonObject();
                        order.addProperty("type", stopOrder.getType());
                        order.addProperty("orderType", "stop");
                        order.addProperty("size", stopOrder.getSize());

                        // evado lo stop order come se fosse un market order
                        JsonObject response = marketIssue(order);
                        // se sono riuscito a evaderlo, lo rimuovo dalla lista di stop orders
                        if (response.get("orderId").getAsInt() != -1) {
                            iterator.remove();
                        }
                    }
                }
            }
        }
    }


    /**
     * Metodo per evadere un market order (/stop order con condizione di esecuzione)
     * @param order ordine da far evadere
     * @return risposta json da inviare
     */
    private JsonObject marketIssue(JsonObject order) {
        String type = order.get("type").getAsString();
        int size = order.get("size").getAsInt();
        String orderType = order.get("orderType").getAsString();
        int sizeCopy = size;
        JsonObject response = new JsonObject();
        // prendo la lista di ordini da controllare in base al tipo del market order eseguito
        ConcurrentSkipListSet<Order> orderList = (type.equals("ask") ? this.bidOrders : this.askOrders);

        // lista degli ordini modificati
        List<Order> changedOrders = new ArrayList<>();
        // lista di pezzi in cui il market order viene diviso se posso evaderlo
        ArrayList<Order> marketPieces = new ArrayList<>();

        // sincronizzo sulla lista di ordini da controllare
        synchronized (orderList) {
            Iterator<Order> iterator = orderList.iterator();
            // finché ci sono ordini nella search list e la dimensione del market order è > 0
            while (iterator.hasNext() && size > 0) {
                Order o = iterator.next();
                // aggiorno la dimensione del market order da evadere
                int min = Math.min(size, o.getSize());
                size -= min;
                // aggiungo l'ordine della search list a quelli che potenzialmente uso per far evadere il market order
                changedOrders.add(o);
            }
            // se sono riuscito a evadere il market order...
            if (size == 0) {
                // aggiorno gli ordini della lista di ordini usati per evadere il market order
                for (Order changedOrder: changedOrders) {
                    int min = Math.min(sizeCopy, changedOrder.getSize());
                    sizeCopy -= min;
                    // inserisco i pezzi di market order evasi
                    marketPieces.add(new Order(type, orderType, min, changedOrder.getPrice(), this.user));
                    changedOrder.setSize(changedOrder.getSize() - min);
                    // se la dimensione dell'ordine analizzato è 0, lo rimuovo dalla lista degli ordini di ricerca (perché è evaso)
                    if (changedOrder.getSize() == 0) {
                        orderList.remove(changedOrder);
                    }
                }
                // se l'ultimo ordine non ha dimensione 0, lo aggiorno senza rimuoverlo dalla search list e senza aggiungerlo agli ordini evasi
                if (changedOrders.get(changedOrders.size()-1).getSize() > 0) {
                    changedOrders.remove(changedOrders.size()-1);
                }
                response.addProperty("orderId", marketPieces.get(marketPieces.size()-1).getId());
                changedOrders.addAll(marketPieces); // aggiungo i pezzi del market order evasi a tutti gli ordini modificati
                synchronized (issuedOrders) {
                    issuedOrders.addAll(changedOrders);
                }
                // invio le notifiche degli ordini modificati
                sendIssuedOrdersNotification(changedOrders);
                // aggiorno la persistenza
                persistenceManager.storeIssuedOrders(issuedOrders);
            } else if (!iterator.hasNext()) {
                // se invece non posso evadere il market order, ritorno errore
                response.addProperty("orderId", -1);
            }
        }

        return response;
    }

    /**
     * Metodo stub per l'invio delle notifiche UDP di evasione degli ordini, gestendo le eccezioni
     * @param orders ordini evasi
     */
    private void sendIssuedOrdersNotification(Collection<Order> orders) {
        try {
            this.udp.sendIssuedOrdersNotification(orders); // invio di notifiche dal server UDP
        } catch (IOException e) {
            System.err.println("UDP Server [ERR] Error while closing connection: " + e.getMessage());
        }
    }
}