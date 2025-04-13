import java.net.InetAddress;

/**
 * Classe per la rappresentazione degli utenti all'interno del sistema.
 */
public class User {
    private String username;
    private String password;
    private boolean isOnline; // per verificare se l'account è attualmente collegato da un altro dispositivo
    private InetAddress address; // indirizzo dell'utente che sta attualmente usando l'account
    private int port; // porta UDP usata per l'ascolto delle notifiche

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.isOnline = false;
    }


    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
