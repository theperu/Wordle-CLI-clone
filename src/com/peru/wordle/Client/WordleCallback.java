package com.peru.wordle.Client;
import com.peru.wordle.User;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interfaccia per l'aggiornamento del ranking
 */
public interface WordleCallback extends Remote {
    /**
     * Aggiorna il ranking con la nuova lista
     * @param rankedUsers La classifica con i nuovi user
     * @throws RemoteException
     */
    public void updateRanking(List<User> rankedUsers) throws RemoteException;
}
