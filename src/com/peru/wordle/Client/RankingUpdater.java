package com.peru.wordle.Client;

import com.peru.wordle.Server.WordleRegistrationInterface;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Registra il client per le callback con gli aggiornamenti della classifica
 */
public class RankingUpdater implements Runnable {
    private WordleClient client; // Il client da registrare
    private int port; // porta utilizzata

    /**
     * Costruttore per RankingUpdater
     * @param client il client da registrare
     * @param port la porta utilizzata
     */
    public RankingUpdater(WordleClient client, int port) {
        this.client = client;
        this.port = port;
    }
    public void run() {
        try {
            System.out.println("Looking for the server...");
            Registry registry = LocateRegistry.getRegistry(port);
            WordleRegistrationInterface server = (WordleRegistrationInterface) registry.lookup("WordleService");
            WordleCallback callbackObj = this.client;
            WordleCallback stub = (WordleCallback) UnicastRemoteObject.exportObject(callbackObj, 0);
            server.registerForCallback(stub); // Registro il client per la callback
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (NotBoundException | RemoteException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
