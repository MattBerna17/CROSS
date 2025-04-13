import java.util.Collection;

/**
 * Interfaccia per la gestione della persistenza dei dati di ordini e utenti.
 */
public interface PersistanceManager {

    // Metodo per la memorizzazione degli ordini passati come parametro.
    public void storeIssuedOrders(Collection<Order> issuedOrders);

    // Metodo per la memorizzazione degli utenti passati come parametro.
    public void storeUsers(Collection<User> users);

    // Metodo per la lettura degli ordini dal dispositivo di memorizzazione.
    public Collection<Order> readOrders();

    // Metodo per la lettura degli utenti dal dispositivo di memorizzazione.
    public Collection<User> readUsers();
}