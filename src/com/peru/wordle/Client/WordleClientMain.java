package com.peru.wordle.Client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.rmi.RemoteException;
import java.util.Scanner;

public class WordleClientMain {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Welcome to Wordle Client CLI!");
        WordleClient wordleClient = null;

        try {
            JsonElement fileElement = JsonParser.parseReader(new FileReader("src" + File.separator + "com" + File.separator + "peru" + File.separator + "wordle" + File.separator + "resources" + File.separator + "config.json"));
            JsonObject fileObject = fileElement.getAsJsonObject();
            //estraggo il dati di configurazione dal file Json
            int port = fileObject.get("server_port").getAsInt();
            String address = fileObject.get("server_hostname").getAsString();

            wordleClient = new WordleClient(port, address); // Creo l'istanza di WordleClient
        } catch (FileNotFoundException e) {
            System.out.println("Something went wrong with the config file. Unable to proceed.");
            System.exit(0);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        int loggedInUserId = -1;

        while (true) {
            System.out.println("\nMenu:");
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. Exit");

            System.out.print("Enter your choice: ");
            int choice = scanner.nextInt();

            switch (choice) {
                case 1:
                    // Registrazione
                    if (loggedInUserId == -1) {
                        scanner.nextLine();
                        System.out.print("Enter your username: ");
                        String username = scanner.nextLine();
                        System.out.print("Enter your password: ");
                        String password = scanner.nextLine();

                        // Cerco di registrare l'utente
                        int result = wordleClient.authentication(username, password);

                        // Gestisco il risultato della registrazione
                        switch (result) {
                            case 0:
                                loggedInUserId = 1;
                                System.out.println("Registration successful.");
                                launchLoggedInMenu(wordleClient, username, scanner);
                                loggedInUserId = -1; // Quando torno qua significa che è stato effettuato un logout
                                break;
                            case -1:
                                System.out.println("Registration failed: Invalid username or password.");
                                break;
                            case -2:
                                System.out.println("Registration failed: Empty password.");
                                break;
                            case -3:
                                System.out.println("Registration failed: RMI error.");
                                break;
                            // Handle other error codes as needed
                            default:
                                System.out.println("Result: " + result);
                                System.out.println("Registration failed: Unknown error.");
                        }
                    } else {
                        System.out.println("You are already logged in. Logout first.");
                    }
                    break;

                case 2:
                    // Login
                    if (loggedInUserId == -1) {
                        scanner.nextLine();
                        System.out.print("Enter your username: ");
                        String username = scanner.nextLine();
                        System.out.print("Enter your password: ");
                        String password = scanner.nextLine();

                        // Effettuo il login
                        int result = wordleClient.authentication(username, password);

                        // Gestisco il risultato del login
                        switch (result) {
                            case 0:
                                loggedInUserId = 1; // Set the logged-in user's ID
                                System.out.println("Login successful.");
                                launchLoggedInMenu(wordleClient, username, scanner);
                                loggedInUserId = -1; // Quando torno qua significa che è stato effettuato un logout
                                break;
                            case -1:
                                System.out.println("Login failed: Invalid username or password.");
                                break;
                            case -2:
                                System.out.println("Login failed: Empty password.");
                                break;
                            case -3:
                                System.out.println("Login failed: RMI error.");
                                break;
                            // Handle other error codes as needed
                            default:
                                System.out.println("Login failed: Unknown error.");
                        }
                    } else {
                        System.out.println("You are already logged in.");
                    }
                    break;

                case 3:
                    // Exit
                    System.out.println("Exiting Wordle Client CLI.");
                    System.exit(0);

                default:
                    System.out.println("Invalid choice. Please enter a valid option.");
            }
        }
    }

    /**
     * Gestisce il menu dell'utente che ha completato il login
     * @param wc L'istanza di WordleClient da usare per le richieste al server
     * @param username Il nome dell'utente loggato
     * @param scanner Lo scanner per gestire l'
     *
     *                input
     */
    public static void launchLoggedInMenu(WordleClient wc, String username, Scanner scanner) {
        System.out.println("\nHi " + username + "!");
        while (true) {
            System.out.println("\nMenu:");
            System.out.println("1. Play");
            System.out.println("2. Show user info");
            System.out.println("3. Share latest game");
            System.out.println("4. Show shared games");
            System.out.println("5. Show ranking");
            System.out.println("6. Logout");

            System.out.print("Enter your choice: ");
            int choice = scanner.nextInt();

            switch (choice) {
                // Nuova partita
                case 1:
                    System.out.println("Are you ready to play?");
                    wc.playWordle();
                    break;
                // Mostra le informazioni dell'utente
                case 2:
                    System.out.println("Your name is: " + username);
                    wc.showMeStatistics();
                    break;
                // Share game
                case 3:
                    System.out.println("Sharing game...");
                    wc.shareGame();
                    break;
                // Show shared games
                case 4:
                    System.out.println("This are the shared games in your group");
                    wc.showMeSharing();
                    break;
                // Show ranking
                case 5:
                    wc.showRanking();
                    break;
                // Logout
                case 6:
                    int result = wc.logoutUser(username);
                    switch (result) {
                        case 0:
                            System.out.println("See you next time! :)");
                            return;
                        case 1:
                            System.out.println("User already logged out.");
                            System.out.println("Going back to the main menu...");
                            return;
                        case 2:
                            System.out.println("An error as occurred, a different user is logged in and not " + username);
                            break;
                        case 3:
                            System.out.println("An unexpected error has occurred while logging out.");
                            System.out.println("If the error persists try closing and reopening the client.");
                            break;
                    }
                    return;
                // Input non valido
                default:
                    System.out.println("Invalid choice. Please enter a valid option.");
            }
        }
    }
}
