import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.*;
import java.time.ZoneId;
import java.util.Date;

/**
 * Classe per la ricezione dei messaggi UDP inviati dal server.
 */
public class ClientUDP implements Runnable {
    // Socket UDP in ascolto
    private DatagramSocket ds;

    public ClientUDP(InetAddress serverAddress, int serverPort) {
        try {
            this.ds = new DatagramSocket();
            this.ds.connect(new InetSocketAddress(serverAddress, serverPort)); // connetto la socket al server
        } catch (SocketException e) {
            System.err.println("Error while opening UDP socket: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        int length = 2048; // dimensione massima del payload
        byte[] buffer = new byte[length];
        // rimani sempre in ascolto
        while (true) {
            DatagramPacket dp = new DatagramPacket(buffer, length);
            try {
                // ricevi il datagramma UDP dal server
                this.ds.receive(dp);
            } catch (IOException e) {
                System.err.println("[ERR] Error while receiving UDP message: " + e.getMessage());
            }
            String data = new String(dp.getData(), 0, dp.getLength());
            // se il server ha inviato il messaggio "EXIT", il client si è disconnesso, quindi termino l'esecuzione
            // del listener UDP del client
            if (data.equals("EXIT")) {
                break;
            }
            System.out.println("\n[NOTIFICATION] Issued Orders:\n" + formatJson(data) + "\n");
        }
    }

    /**
     * Funzione per formattare in modo chiaro i trades inviati dal server.
     * @param data stringa in formato json
     * @return stringa formattata
     */
    private String formatJson(String data) {
        StringBuilder formattedData = new StringBuilder();
        // prendo il messaggio json e faccio parsing, prendendo l'array seguito dal messaggio "trades"
        JsonArray trades = JsonParser.parseString(data).getAsJsonObject().get("trades").getAsJsonArray();
        // per ogni json element (ovvero ogni trade)
        for (JsonElement element: trades) {
            JsonObject trade = element.getAsJsonObject();
            // aggiungo alla stringa formattata le informazioni passate del trade
            formattedData.append("\tOrderId: ").append(trade.get("orderId").getAsInt()).append("\n");
            formattedData.append("\tType: ").append(trade.get("type").getAsString()).append("\n");
            formattedData.append("\tOrder Type: ").append(trade.get("orderType").getAsString()).append("\n");
            formattedData.append("\tSize: ").append(trade.get("size").getAsInt()).append("\n");
            formattedData.append("\tPrice: ").append(trade.get("price").getAsInt()).append("\n");
            formattedData.append("\tTimestamp: ").append(new Date(trade.get("timestamp").getAsInt() * 1000L).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()).append("\n\n");
        }
        return formattedData.toString();
    }

    public DatagramSocket getDs() {
        return this.ds;
    }
}
