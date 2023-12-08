package com.peru.wordle;

import java.io.Serializable;

/**
 * Classe che permette la gestione di un utente registrato su Wordle
 */
public class User implements Comparable<User>, Serializable {
    private String username;
    private String password;
    private int totalGames = 0; // Partite effettuate
    private float guessDistribution = 0; // Numero di tentativi medi per indovinare una parola
    private int victories = 0;
    private int bestStreak = 0; // Miglior serie di vittorie di fila
    private int lastStreak = 0; // Attuale serie di vittorie di fila
    private boolean onStreak = false;

    /**
     * Costruttore per un nuovo user
     * @param username Nome
     * @param password Password associata
     */
    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.victories = 0;
        this.bestStreak = 0;
        this.lastStreak = 0;
    }

    /**
     * Costruttore user esistente
     * @param username Nome
     * @param password Password associata
     * @param victories Numero di vittorie
     * @param bestStreak Miglior serie di vittorie
     * @param lastStreak Attuale serie di vittorie
     * @param totalGames Partite totali
     * @param guessDistribution Numero di tentativi medi per indovinare una parola
     */
    public User(String username, String password, int victories, int bestStreak, int lastStreak, int totalGames, float guessDistribution) {
        this.username = username;
        this.password = password;
        this.victories = victories;
        this.bestStreak = bestStreak;
        this.lastStreak = lastStreak;
        this.totalGames = totalGames;
        this.guessDistribution = guessDistribution;
    }

    public String getUsername() { return username; }

    public String getPassword() { return password; }

    public int getVictories() { return victories; }

    public int getLastStreak() { return lastStreak; }

    public int getBestStreak() { return bestStreak; }

    public int getTotalGames() { return totalGames; }
    public float getGuessDistribution() { return guessDistribution; }

    /**
     * Aggiorna la serie di vittorie attuale
     * @param won true se ha vinto l'ultima partita, false altrimenti
     * @param guesses Tentativi utilizzati
     */
    public void updateStreak (boolean won, int guesses) {
        totalGames++; // Aggiorno il numero di partite
        if (won) {
            onStreak = true;
            lastStreak++;
            if(lastStreak > bestStreak) bestStreak = lastStreak;
            guessDistribution = ((guessDistribution * victories) + guesses) / (victories + 1);
            victories++;
        } else {
            lastStreak = 0;
            onStreak = false;
        }
    }

    @Override
    public int compareTo(User otherUser) {
        return Float.compare(otherUser.getVictories() * otherUser.getGuessDistribution(), this.victories * this.guessDistribution);
    }
}
