package com.peru.wordle.Client;

import com.google.gson.Gson;
import com.peru.wordle.Game;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;


/**
 * Gestore delle notifiche
 */
public class NotificationsHandler implements Runnable {
    private InetAddress group; // gruppo per la condivisione delle partite
    private int port; // porta utilizzata
    private ArrayList<Game> games; // partite condivise
    private int timeout;

    /**
     * Costruttore del gestore delle notifiche
     * @param group gruppo per la condivisione delle partite
     * @param port porta utilizzata
     * @param games lista delle partite condivise
     * @param timeout
     */
    public NotificationsHandler(InetAddress group, int port, ArrayList<Game> games, int timeout) {
        this.group = group;
        this.port = port;
        this.games = games;
        this.timeout = timeout;
    }

    public void run() {
        try {
            MulticastSocket multicastSocket = new MulticastSocket(port); // Creazione di un socket multicast sulla porta specificata
            multicastSocket.joinGroup(group); // Si aggiunge al gruppo
            byte[] buffer = new byte[8192];
            multicastSocket.setSoTimeout(timeout);
            boolean messageReceived;
            while (!Thread.currentThread().isInterrupted()) {
                messageReceived = true;
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                try {
                    multicastSocket.receive(datagramPacket);
                } catch (SocketTimeoutException e) {
                    messageReceived = false;
                }
                if (messageReceived) {
                    // Elaboro il messaggio ricevuto
                    Gson gson = new Gson();
                    String s = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
                    Game game = gson.fromJson(s, Game.class);
                    games.add(game); // Aggiungo la partita
                }
            }
            multicastSocket.leaveGroup(group); // Lascio il gruppo
            multicastSocket.close(); // Chiudo la socket
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
