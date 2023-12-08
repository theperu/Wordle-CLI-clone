package com.peru.wordle.Server;

import com.peru.wordle.Client.WordleCallback;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interfaccia per la gestione delle registrazioni e delle callback
 */
public interface WordleRegistrationInterface extends Remote {

    /**
     * Registra un nuovo utente
     * @param username Il nome dell'utente
     * @param password La password dell'utente
     * @return 0 se la registrazione è andata a buon fine, -1 se l'utente è già registrato, -2 se la password non è valida, -3 se c'è un errore inaspettato
     * @throws RemoteException
     */
    int register(String username, String password) throws RemoteException;

    /**
     * Registra un client per gli aggiornamenti sul ranking
     * @param clientInterface Il client da registrare
     * @throws RemoteException
     */
    void registerForCallback(WordleCallback clientInterface) throws RemoteException;
}
