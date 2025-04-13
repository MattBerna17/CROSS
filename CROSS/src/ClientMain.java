import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Scanner;

/**
 * Classe main per il client.
 */
public class ClientMain {
    public static boolean loggedOut; // stato di attività del client: se ha svolto logout oppure ha fatto accesso
    public static String username; // username usato dal client

    public static void main(String[] args) {
        Client client = new Client();
        Scanner in = new Scanner(System.in); // per leggere informazioni da riga di comando

        System.out.println(" ______     ______     ______     ______     ______    \n" +
                "/\\  ___\\   /\\  == \\   /\\  __ \\   /\\  ___\\   /\\  ___\\   \n" +
                "\\ \\ \\____  \\ \\  __<   \\ \\ \\/\\ \\  \\ \\___  \\  \\ \\___  \\  \n" +
                " \\ \\_____\\  \\ \\_\\ \\_\\  \\ \\_____\\  \\/\\_____\\  \\/\\_____\\ \n" +
                "  \\/_____/   \\/_/ /_/   \\/_____/   \\/_____/   \\/_____/ \n" +
                "                                                       \n");

        // schermata di login oppure registrazione (se non si ha un account)
        int choice;
        do {
            System.out.println("Type 1 to login, 2 to register:");
            choice = in.nextInt();
        } while (choice != 1 && choice != 2);
        if (choice == 1) {
            login(client, in);
        } else {
            register(client, in);
        }

        // schermata principale una volta che si è fatto l'accesso
        choice = loggedScreen(client, in);
        do {
            if (loggedOut) {
                choice = loggedOutScreen(client, in);
            } else {
                choice = loggedScreen(client, in);
            }
            System.out.println();
        } while (choice != -1); // finché non si fa logout e dopo -1 (exit), si continua ad interagire col sistema

        System.out.println("Bye!\n");
    }

    /**
     * Metodo per la gestione della schermata di interazione principale in caso l'utente abbia fatto accesso.
     * @param client istanza della classe Client per interagire col server
     * @param in Scanner per la lettura dei dati da riga di comando
     * @return scelta dell'operazione da eseguire
     */
    public static int loggedScreen(Client client, Scanner in) {
        System.out.println("\nSelect one of the following actions to perform:");
        System.out.println("1. Insert limit order");
        System.out.println("2. Insert market order");
        System.out.println("3. Insert stop order");
        System.out.println("4. Cancel order");
        System.out.println("5. Get price history");
        System.out.println("6. Logout");
        System.out.println();
        int choice = in.nextInt();
        System.out.println();

        switch (choice) {
            case 1: // inserimento di un limit order
                System.out.println("Insert type (ask/bid), size and price of the limit order:");
                String type;
                int size, price;
                do {
                    type = in.next();
                    if (!type.equals("ask") && !type.equals("bid")) {
                        System.out.println("Type is not valid: insert only ask or bid.");
                    }
                } while (!type.equals("ask") && !type.equals("bid"));
                do {
                    size = in.nextInt();
                    if (size <= 0) {
                        System.out.println("Size is not valid: insert only positive (non-zero) value.");
                    }
                } while (size <= 0);
                do {
                    price = in.nextInt();
                    if (price <= 0) {
                        System.out.println("Price is not valid: insert only positive (non-zero) value.");
                    }
                } while (price <= 0);
                JsonObject response = client.insertLimitOrder(type, size, price); // interazione col server per ottenere la risposta all'operazione
                int id = response.get("orderId").getAsInt();
                // se id == -1, l'ordine non è stato inserito nel sistema, altrimenti sì
                if (id == -1) {
                    System.out.println("[RESPONSE]: Order could not be issued.");
                } else {
                    System.out.println("[RESPONSE]: Limit Order inserted with id " + response.get("orderId"));
                }
                break;
            case 2: // inserimento market order
                System.out.println("Insert type (ask/bid) and size of the market order:");
                do {
                    type = in.next();
                    if (!type.equals("ask") && !type.equals("bid")) {
                        System.out.println("Type is not valid: insert only ask or bid.");
                    }
                } while (!type.equals("ask") && !type.equals("bid"));
                do {
                    size = in.nextInt();
                    if (size <= 0) {
                        System.out.println("Size is not valid: insert only positive (non-zero) value.");
                    }
                } while (size <= 0);
                response = client.insertMarketOrder(type, size);
                id = response.get("orderId").getAsInt();
                if (id == -1) {
                    System.out.println("[RESPONSE]: Order could not be issued.");
                } else {
                    System.out.println("[RESPONSE]: Market Order inserted with id " + response.get("orderId"));
                }
                break;
            case 3: // inserimento stop order
                System.out.println("Insert type (ask/bid), size and price of the stop order:");
                do {
                    type = in.next();
                    if (!type.equals("ask") && !type.equals("bid")) {
                        System.out.println("Type is not valid: insert only ask or bid.");
                    }
                } while (!type.equals("ask") && !type.equals("bid"));
                do {
                    size = in.nextInt();
                    if (size <= 0) {
                        System.out.println("Size is not valid: insert only positive (non-zero) value.");
                    }
                } while (size <= 0);
                do {
                    price = in.nextInt();
                    if (price <= 0) {
                        System.out.println("Price is not valid: insert only positive (non-zero) value.");
                    }
                } while (price <= 0);
                response = client.insertStopOrder(type, size, price);
                id = response.get("orderId").getAsInt();
                // se id == -1, la condizione di esecuzione era verificata ma l'ordine è fallito (come un market order)
                // se id == -2, la condizione non era verificata, quindi l'ordine è stato inserito nella lista di stop order in attesa di esecuzione
                // altrimenti, è stato eseguito con successo
                if (id == -1) {
                    System.out.println("[RESPONSE]: Order could not be issued.");
                } else if (id == -2) {
                    System.out.println("[RESPONSE]: Order in queue.");
                } else {
                    System.out.println("[RESPONSE]: Stop Order inserted with id " + response.get("orderId"));
                }
                break;
            case 4: // cancellazione ordine
                System.out.println("Insert ID of the order to cancel:");
                int orderId = in.nextInt();
                response = client.cancelOrder(orderId);
                System.out.println("[RESPONSE]: " + response.get("errorMessage").getAsString());
                break;
            case 5: // ottenere informazioni sugli ordini storici di un determinato mese
                System.out.println("Insert month in MMMYYYY format (e.g. Sep2024):");
                String month = in.next();
                response = client.getPriceHistory(month);
                System.out.println("[RESPONSE]: " + response.get("errorMessage").getAsString());
                int i = 1;
                // per ogni mese nella risposta, stampa prezzo d'apertura, di chiusura, massimo e minimo
                for (JsonElement day: response.get("info").getAsJsonArray()) {
                    JsonObject dayobj = day.getAsJsonObject();
                    JsonObject dayObject = dayobj.get(Integer.toString(i)).getAsJsonObject();
                    System.out.println("\t" + i + " " + month);
                    System.out.println("\t\tOpening price: " + dayObject.get("open").getAsString());
                    System.out.println("\t\tClosing price: " + dayObject.get("close").getAsString());
                    System.out.println("\t\tMinimum price: " + dayObject.get("min").getAsString());
                    System.out.println("\t\tMaximum price: " + dayObject.get("max").getAsString());
                    i++;
                }
                break;
            case 6: // logout
                response = client.logout(username);
                System.out.println("[RESPONSE]: " + response.get("errorMessage").getAsString());
                loggedOut = true; // l'utente ha fatto logout
                break;
            default: // nessuna azione associata
                System.out.println("No action associated with this choice.");
                choice = 0;
                break;
        }
        return choice;
    }

    /**
     * Metodo per la gestione della schermata di interazione principale in caso l'utente abbia fatto logout
     * @param client istanza della classe Client per interagire col server
     * @param in Scanner per la lettura dei dati da riga di comando
     * @return scelta dell'operazione da eseguire
     */
    public static int loggedOutScreen(Client client, Scanner in) {
        System.out.println("\nSelect one of the following actions to perform:");
        System.out.println("1. Update credentials");
        System.out.println("2. Login");
        System.out.println("\nTo exit, press -1.\n");
        int choice = in.nextInt();
        System.out.println();

        switch (choice) {
            case 1: // aggiorna le credenziali (posso farlo perché sono uscito dall'account col logout)
                System.out.println("Insert old password and new password:");
                String old_pwd = in.next();
                String new_pwd;
                do {
                    new_pwd = in.next();
                    if (new_pwd.isEmpty()) {
                        System.out.println("New password mustn't be empty.");
                    }
                    if (new_pwd.equals(old_pwd)) {
                        System.out.println("New password must be different from old password.");
                    }
                } while (new_pwd.isEmpty() || new_pwd.equals(old_pwd));
                JsonObject response = client.updateCredentials(username, old_pwd, new_pwd);
                System.out.println("[RESPONSE]: " + response.get("errorMessage").getAsString());
                break;
            case 2: // login
                login(client, in);
                break;
            case -1: // uscita dal programma
                client.exit();
                System.out.println("\nExiting CROSS...");
                break;
            default: // nessuna azione associata
                System.out.println("No action associated with this choice.");
                break;
        }
        return choice;
    }

    /**
     * Metodo per la gestione della schermata di login
     * @param client istanza della classe Client per interagire col server
     * @param in Scanner per la lettura dei dati da riga di comando
     */
    public static void login(Client client, Scanner in) {
        int code;
        do {
            System.out.println("Insert username and password:");
            username = in.next();
            String pwd;
            do {
                pwd = in.next();
                if (pwd.isEmpty()) {
                    System.out.println("Password mustn't be empty.");
                }
            } while (pwd.isEmpty());
            JsonObject response = client.login(username, pwd);
            code = response.get("response").getAsInt();
            System.out.println("[RESPONSE]: " + response.get("errorMessage").getAsString());
        } while(code != 100); // finché non si esegue un login corretto, si ripete l'operazione
        loggedOut = false; // abbiamo fatto correttamente login, quindi non siamo nello stato di logout
    }

    /**
     * Metodo per la gestione della schermata di registrazione
     * @param client istanza della classe Client per interagire col server
     * @param in Scanner per la lettura dei dati da riga di comando
     */
    public static void register(Client client, Scanner in) {
        int code;
        do {
            System.out.println("Insert username and password:");
            username = in.next();
            String pwd;
            do {
                pwd = in.next();
                if (pwd.isEmpty()) {
                    System.out.println("Password mustn't be empty.");
                }
            } while (pwd.isEmpty());
            JsonObject response = client.register(username, pwd);
            code = response.get("response").getAsInt();
            System.out.println("[RESPONSE]: " + response.get("errorMessage").getAsString());
        } while (code != 100); // finché non si esegue correttamente la registrazione, si ripete l'operazione
        loggedOut = false; // abbiamo svolto la registrazione correttamente, quindi non siamo in stato di logout
    }
}
