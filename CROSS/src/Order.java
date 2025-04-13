import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Classe che rappresenta un ordine.
 */
public class Order {
    public static int nextId; // attributo di classe per mantenere il conteggio degli id

    private String type; // ask o bid
    private String orderType; // limit, market o stop
    private int size;
    private int price;
    private LocalDateTime date;
    private int id; // id proprio di ogni istanza
    private User owner; // utente che ha inserito l'ordine
    private int initialSize; // riferimento alla dimensione iniziale dell'ordine

    /**
     * Costruttore usato nella creazione di un nuovo ordine in modo interattivo con il client
     */
    public Order(String type, String orderType, int size, int price, User owner) {
        this.type = type;
        this.orderType = orderType;
        this.size = size;
        this.initialSize = size;
        this.price = price;
        this.date = new Date(System.currentTimeMillis()).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        this.id = nextId;
        nextId++;
        this.owner = owner;
    }

    /**
     * Costruttore usato nel caricamento degli ordini durante la lettura del file json che contiene lo storico degli ordini
     */
    public Order(int id, String type, String orderType, int size, int price, long timestamp) {
        this.type = type;
        this.orderType = orderType;
        this.size = size;
        this.price = price;
        this.initialSize = size;
        this.date = new Date(timestamp*1000).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        this.id = id;
        nextId = Math.max(id + 1, nextId);
        owner = null;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "OrderId: " + getId() + "\nOrderType: " + getOrderType() + "\nType: " + getType() + "\nSize: " + getSize() + "\nPrice: " + getPrice() + "\nDate " + getDate();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public int getInitialSize() {
        return initialSize;
    }
}
