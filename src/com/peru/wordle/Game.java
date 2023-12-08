package com.peru.wordle;

/**
 * Classe che permette di gestire una partita di Wordle
 */
public class Game {
    private String[] entries; // Contiene i tentativi
    private User user; // Lo user che ha effettuato la partita
    private int entriesNumber; // Il numero di tentativi effettuati

    /**
     * Costruttore di una partita
     * @param user Lo user che ha avviato la partita
     */
    public Game(User user) {
        this.user = user;
        entries = new String[5];
        entriesNumber = 0;
    }

    /**
     * Aggiunge un tentativo
     * @param entry Il tentativo che si vuole aggiungere
     */
    public void addEntry(String entry) {
        entries[entriesNumber] = entry;
        entriesNumber++;
    }

    /**
     * Stampa la partita
     */
    public void displayGame() {
        System.out.println(user.getUsername() + " shared this game:");
        for (int i = 0; i < entriesNumber; i++) System.out.println("Entry #" + i + ": " + entries[i]);
    }
}
