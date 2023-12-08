package com.peru.wordle.Server;

import java.rmi.RemoteException;
import java.util.Scanner;

/**
 * Main del Server per Wordle
 */
public class WordleServerMain {
    public static void main(String[] args) {
        try {
            WordleServer server = new WordleServer();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

    }
}