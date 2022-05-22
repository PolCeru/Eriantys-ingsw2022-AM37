package it.polimi.ingsw.am37.controller;

import it.polimi.ingsw.am37.message.*;
import it.polimi.ingsw.am37.model.Assistant;
import it.polimi.ingsw.am37.model.Cloud;
import it.polimi.ingsw.am37.model.GameManager;
import it.polimi.ingsw.am37.model.Island;
import it.polimi.ingsw.am37.model.character.Character;
import it.polimi.ingsw.am37.model.character.Option;
import it.polimi.ingsw.am37.model.exceptions.AssistantImpossibleToPlay;
import it.polimi.ingsw.am37.model.exceptions.CharacterImpossibleToPlay;
import it.polimi.ingsw.am37.model.exceptions.MNmovementWrongException;
import it.polimi.ingsw.am37.model.exceptions.StudentSpaceException;
import it.polimi.ingsw.am37.network.MessageReceiver;
import it.polimi.ingsw.am37.network.exceptions.InternetException;
import it.polimi.ingsw.am37.network.server.ClientHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * It represents the in game Lobby
 */
public class Lobby implements Runnable, MessageReceiver {
    /**
     * A Logger.
     */
    private static Logger LOGGER;

    /**
     * The unique ID of the match.
     */
    private final int matchID;

    /**
     * It saves how many Players can enter the lobby.
     */
    private final int lobbySize;

    /**
     * It represents if the lobby is set to advanced mode or not.
     */
    private final boolean advancedMode;

    /**
     * If the lobby is full the game is ready.
     */
    private boolean isGameReady;

    /**
     * It represents the client connected in the lobby and therefore the Players.
     */
    private final HashMap<String, ClientHandler> players;

    /**
     * Keeps track of the disconnected clients
     */
    private static HashMap<String, ClientHandler> disconnectedPlayers;

    /**
     * exposed model
     */
    private final GameManager gameManager;

    /**
     * A controller that observe what is changed in the model and stores it.
     */
    private final UpdateController updateController;

    /**
     * It represents the clientHandlers and theirs nickname
     */
    private final HashMap<String, String> playerNicknames;

    /**
     * Flags that checks if a Player already played a Character in his turn.
     */
    private boolean characterPlayed;

    /**
     * Keeps track of the number of Students moved.
     */
    private int numberOfStudentsMoved;

    /**
     * Default constructor.
     */
    public Lobby(int lobbySize, boolean advancedMode, int matchID) {
        LOGGER = LogManager.getLogger(Lobby.class);
        this.lobbySize = lobbySize;
        this.advancedMode = advancedMode;
        this.players = new HashMap<>();
        this.playerNicknames = new HashMap<>();
        this.isGameReady = false;
        this.gameManager = new GameManager(lobbySize, advancedMode);
        this.matchID = matchID;
        this.updateController = new UpdateController();
        reset();
    }

    /**
     * Runs the thread.
     */
    @Override
    public synchronized void run() {
        while (true) {
            if (isGameReady()) {
                startGame();
                LOGGER.info("Everything is ready, game is about to start");
                break;
            } else {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Starts the game and notifies the client
     */
    private void startGame() {
        Timer timer = new Timer();
        gameManager.prepareGame();
        gameManager.registerListener(updateController);
        int i = 0;
        for (String nickname :
                playerNicknames.values()) {
            gameManager.getTurnManager().getPlayers().get(i).setPlayerId(nickname);
            i++;
        }
        gameManager.getTurnManager().getOrderPlayed().forEach(el -> System.out.println(el.getPlayerId()));
        sendMessage(new StartGameMessage());
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendMessage(new UpdateMessage(updateController.getUpdatedObjects(), MessageType.START_GAME,
                        "StartGame"));
            }
        }, 100);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendMessage(new PlanningPhaseMessage(findUUIDByUsername(gameManager.getTurnManager()
                        .getCurrentPlayer()
                        .getPlayerId())));
            }
        }, 300);

    }

    /**
     * adds the player in the Lobby and checks if the Lobby is full
     *
     * @param ch the Client to be added.
     */
    public synchronized void addPlayerInLobby(String UUID, ClientHandler ch, String nickname) {
        players.put(UUID, ch);
        isGameReady = isFull();
        playerNicknames.put(UUID, nickname);
        LOGGER.info("+1");
        notifyAll();
    }

    /**
     * @return true if the player is found, otherwise false.
     */
    public boolean isPlayerInLobby(String UUID) {
        return players.containsKey(UUID);
    }

    /**
     * @return True if the Lobby is full otherwise False
     */
    private boolean isFull() {
        return players.size() == lobbySize;
    }

    /**
     * @return the players connected in the lobby
     */
    public HashMap<String, ClientHandler> getPlayers() {
        return players;
    }

    /**
     * @return the players nickname in the lobby
     */
    public HashMap<String, String> getPlayerNicknames() {
        return playerNicknames;
    }

    /**
     * @return the flag that saves if the Lobby is ready to start the game.
     */
    public boolean isGameReady() {
        return isGameReady;
    }

    /**
     * @return the Lobby size.
     */
    public int getLobbySize() {
        return lobbySize;
    }

    /**
     * @return the unique id of the match.
     */
    public int getMatchID() {
        return matchID;
    }

    /**
     * @return the flag that states if the Lobby is in advanced mode.
     */
    public boolean isAdvancedMode() {
        return advancedMode;
    }

    /**
     * Find the UUID associated to the given username in the current registered uid - username map
     *
     * @param username the username to find the UUID
     * @return the UUID associated
     */
    public String findUUIDByUsername(String username) {
        //noinspection OptionalGetWithoutIsPresent
        return playerNicknames.entrySet().stream().filter(entry -> Objects.equals(entry.getValue(), username)).findFirst().get().getKey();
    }

    /**
     * resets the variables needed
     */
    private void reset() {
        characterPlayed = false;
        numberOfStudentsMoved = 0;
    }

    /**
     * When a message is received perform a specific action based on the Message type.
     *
     * @param message the Message received.
     * @param ch      the ClientHandler that called the method.
     */
    private void playAssistantCase(Message message, ClientHandler ch) {
        Message response;
        //Refill clouds if is the first player of the round
        if (Objects.equals(findUUIDByUsername(gameManager.getTurnManager().getOrderPlayed().get(0).getPlayerId()), message.getUUID())) {
            for (Cloud c : gameManager.getClouds()) {
                c.addStudents(gameManager.getBag()
                        .extractStudents(c.getIsFor2()
                                ? c.getStudentsPerCloud2Players()
                                : c.getStudentsPerCloud3Players()));
            }
            LOGGER.debug("Clouds Refilled! (he's the first player of the round)");
        }
        HashMap<Integer, Assistant> deck = gameManager.getTurnManager().getCurrentPlayer().getAssistantsDeck();
        try {
            gameManager.playAssistant(deck.get(((PlayAssistantMessage) message).getCardValue()));
        } catch (AssistantImpossibleToPlay | IllegalArgumentException e) {
            LOGGER.error("Assistant impossible to play");
            LOGGER.error(e.getMessage());
            response = new ErrorMessage(message.getUUID(), e.getMessage());
            sendMessage(response);
        }
        //TODO: Controllare che se tutti hanno giocato l'assistente allora si manda un nextTurn.
        gameManager.getTurnManager().getOrderPlayed().forEach(el -> System.out.println(el.getPlayerId()));
        if (Objects.equals(findUUIDByUsername(gameManager.getTurnManager().getOrderPlayed().get(gameManager.getTurnManager().getOrderPlayed().size() - 1).getPlayerId()), message.getUUID()))
            response = new NextTurnMessage(findUUIDByUsername(gameManager.getTurnManager().getCurrentPlayer().getPlayerId()), gameManager.getTurnManager().getCurrentPlayer().getPlayerId());
        else {
            response = new PlanningPhaseMessage(findUUIDByUsername(gameManager.getTurnManager().getCurrentPlayer().getPlayerId()));
        }
        sendMessage(response);
    }

    /**
     * When a message is received perform a specific action based on the Message type.
     *
     * @param message the Message received.
     * @param ch      the ClientHandler that called the method.
     */
    private void studentsToDining(Message message, ClientHandler ch) {
        Message response;
        int students;
        int movableStudents = 3;
        students = ((StudentsToDiningMessage) message).getContainer().size();
        if (numberOfStudentsMoved + students < movableStudents) {
            try {
                gameManager.moveStudentsToDining(((StudentsToDiningMessage) message).getContainer());
            } catch (IllegalArgumentException e) {
                response = new ErrorMessage(message.getUUID(), e.getMessage());
                sendMessage(response);
            }
            numberOfStudentsMoved += students;
            response = new UpdateMessage(updateController.getUpdatedObjects(), message.getMessageType(), message.getMessageType().getClassName());
            sendMessage(response);
        } else
            ch.disconnect();
    }

    /**
     * When a message is received perform a specific action based on the Message type.
     *
     * @param message the Message received.
     * @param ch      the ClientHandler that called the method.
     */
    private void studentsToIslandCase(Message message, ClientHandler ch) {
        Message response;
        int students;
        int movableStudents = 3;
        students = ((StudentsToIslandMessage) message).getContainer().size();
        if (numberOfStudentsMoved + students < movableStudents) {
            try {
                gameManager.moveStudentsToIsland(((StudentsToIslandMessage) message).getContainer(),
                        ((StudentsToIslandMessage) message).getIslandId());
            } catch (IllegalArgumentException e) {
                response = new ErrorMessage(message.getUUID(), e.getMessage());
                sendMessage(response);
            }
            numberOfStudentsMoved += students;
            response = new UpdateMessage(updateController.getUpdatedObjects(), message.getMessageType(), message.getMessageType().getClassName());
            sendMessage(response);
        } else
            ch.disconnect();
    }

    /**
     * When a message is received perform a specific action based on the Message type.
     *
     * @param message the Message received.
     * @param ch      the ClientHandler that called the method.
     */
    private void moveMotherNatureCase(Message message, ClientHandler ch) {
        Message response;
        boolean exists = false;
        for (Island island : gameManager.getIslandsManager().getIslands()) {
            if (((MoveMotherNatureMessage) message).getIslandId() == island.getIslandId()) {
                exists = true;
                break;
            }
        }
        if (exists) {
            try {
                gameManager.moveMotherNature(((MoveMotherNatureMessage) message).getIslandId());
                response = new UpdateMessage(updateController.getUpdatedObjects(), message.getMessageType(), message.getMessageType().getClassName());
                sendMessage(response);
            } catch (MNmovementWrongException e) {
                response = new ErrorMessage(message.getUUID(), e.getMessage());
                sendMessage(response);
            }
        } else
            ch.disconnect();
    }

    /**
     * When a message is received perform a specific action based on the Message type.
     *
     * @param message the Message received.
     * @param ch      the ClientHandler that called the method.
     */
    private void playCharacterCase(Message message, ClientHandler ch) {
        Message response;
        if (characterPlayed) {
            Character characterToBePlayed = new Character(((PlayCharacterMessage) message).getChosenCharacter().getInitialPrice(), ((PlayCharacterMessage) message).getChosenCharacter());
            Option optionNeeded = ((PlayCharacterMessage) message).getOption();
            try {
                gameManager.playCharacter(characterToBePlayed, optionNeeded);
            } catch (CharacterImpossibleToPlay e) {
                response = new ErrorMessage(message.getUUID(), e.getMessage());
                sendMessage(response);
            }
            response = new UpdateMessage(updateController.getUpdatedObjects(), message.getMessageType(), message.getMessageType().getClassName());
            sendMessage(response);
            characterPlayed = true;
        } else
            ch.disconnect();
    }

    /**
     * When a message is received perform a specific action based on the Message type.
     *
     * @param message the Message received.
     * @param ch      the ClientHandler that called the method.
     */
    private void chooseCloudCase(Message message, ClientHandler ch) {
        Message response;
        try {
            gameManager.chooseCloud(((ChooseCloudMessage) message).getCloudId());
            response = new UpdateMessage(updateController.getUpdatedObjects(), message.getMessageType(), message.getMessageType().getClassName());
            sendMessage(response);
            gameManager.nextTurn();
        } catch (IllegalArgumentException | StudentSpaceException e) {
            response = new ErrorMessage(message.getUUID(), e.getMessage());
            sendMessage(response);
        }

        if (!ch.isConnectedToClient()) {
            ClientHandler newCh;
            do {
                onDisconnect(message.getUUID());
                gameManager.nextTurn();
                newCh = players.get(findUUIDByUsername(gameManager.getTurnManager().getCurrentPlayer().getPlayerId()));
            } while (!newCh.isConnectedToClient());
            ch = newCh;
        }
        if (ch.isConnectedToClient()) {
            if (!Objects.equals(findUUIDByUsername(gameManager.getTurnManager().getOrderPlayed().get(gameManager.getTurnManager().getOrderPlayed().size() - 1).getPlayerId()), message.getUUID()))
                response = new NextTurnMessage(findUUIDByUsername(gameManager.getTurnManager().getCurrentPlayer().getPlayerId()), gameManager.getTurnManager().getCurrentPlayer().getPlayerId());
            else {
                response = new PlanningPhaseMessage(findUUIDByUsername(gameManager.getTurnManager().getCurrentPlayer().getPlayerId()));
                gameManager.getTurnManager().getAssistantPlayed().clear();
            }
        }
        sendMessage(response);
    }

    /**
     * When a message is received perform a specific action based on the Message type.
     *
     * @param message the Message received.
     * @param ch      the ClientHandler that called the method.
     */
    @Override
    public void onMessageReceived(Message message, ClientHandler ch) throws InternetException {
        switch (message.getMessageType()) {
            case PLAY_ASSISTANT -> {
                LOGGER.info("PlayAssistant Message received from: " + playerNicknames.get(message.getUUID()));
                playAssistantCase(message, ch);
            }
            case STUDENTS_TO_DINING -> {
                LOGGER.info("StudentsToDining Message received from: " + playerNicknames.get(message.getUUID()));
                studentsToDining(message, ch);
            }
            case STUDENTS_TO_ISLAND -> {
                LOGGER.info("StudentsToIsland Message received from: " + playerNicknames.get(message.getUUID()));
                studentsToIslandCase(message, ch);
            }
            case MOVE_MOTHER_NATURE -> {
                LOGGER.info("MoveMotherNature Message received from: " + playerNicknames.get(message.getUUID()));
                moveMotherNatureCase(message, ch);
            }
            case PLAY_CHARACTER -> {
                LOGGER.info("PlayCharacter Message received from: " + playerNicknames.get(message.getUUID()));
                playCharacterCase(message, ch);
            }
            case CHOOSE_CLOUD -> {
                LOGGER.info("ChooseCloud Message received from: " + playerNicknames.get(message.getUUID()));
                chooseCloudCase(message, ch);
            }
            default -> throw new IllegalStateException("Unexpected value: " + message.getMessageType());
        }
    }

    /**
     * @param message the Message that must be sent.
     */
    @Override
    public void sendMessage(Message message) throws InternetException {
        switch (message.getMessageType()) {
            case NEXT_TURN, END_GAME, START_GAME, UPDATE -> {
                for (ClientHandler ch : players.values()) {
                    ch.sendMessageToClient(message);
                }
            }
            case ERROR, PLANNING_PHASE -> {
                ClientHandler ch = players.get(message.getUUID());
                ch.sendMessageToClient(message);
            }
            default ->
                    System.err.println("The server doesn't know where to send this message: " + message.getMessageType().getClassName());
        }
    }

    /**
     * Perform actions when client wants to disconnect
     *
     * @param clientUUID the UUID of the client to disconnect.
     */
    @Override
    public void onDisconnect(String clientUUID) {
        //TODO: Va rimosso il player e il suo nickname? No se vogliamo fare la Resilienza, semplicemente lo si
        // disattiva somehow
        // gestire poi anche cosa fare con le sue informazioni nel server.
        // Se si vuole gestire la resilienza le sue informazioni non andranno eliminate lato server.
        // ma sarà necessario toglierlo dal model


        //TODO: Set Player to disconnected, gestire il turn manager per far saltare i player disconnessi.
        ClientHandler clientToDisconnect = players.get(clientUUID);
        disconnectedPlayers.put(clientUUID, clientToDisconnect);
        players.remove(clientUUID);
    }
}
