package it.polimi.ingsw.am37.model;

import it.polimi.ingsw.am37.model.exceptions.AssistantImpossibleToPlay;
import it.polimi.ingsw.am37.model.exceptions.NoProfChangeException;
import it.polimi.ingsw.am37.model.student_container.FixedUnlimitedStudentsContainer;
import it.polimi.ingsw.am37.model.student_container.LimitedStudentsContainer;
import it.polimi.ingsw.am37.model.student_container.StudentsContainer;

import javax.management.InstanceAlreadyExistsException;
import java.util.*;

public class TurnManager {

    /**
     * If you can take a professor even if there is a draw
     */
    private boolean getProfWithDraw;

    /**
     * Players in the game
     */
    private final ArrayList<Player> players;

    /**
     * Who is playing
     */
    private Player currentPlayer;

    /**
     * If coins are enabled in the game
     */
    private final boolean coinsEnabled;

    /**
     * Total number of players
     */
    private final int numOfPlayers;

    /**
     * Map to memorize who used getProfWithDraw to steal professors, at the end of the turn it will be reverted
     */
    private HashMap<FactionColor, Player> stolenProf;

    /**
     * Players in the order of when they choose their assistant
     */
    private ArrayList<Player> orderPlayed;

    /**
     * It represents the Assistant played per Player in the current turn.
     */
    private HashMap<Player, Assistant> assistantPlayed;

    /**
     * It is needed to establish the players' next turn order
     */
    private HashMap<Player, Assistant> nextTurnPlayer;

    /**
     * Default constructor
     */
    public TurnManager(boolean coinsEnabled, int numOfPlayers) {
        this.players = new ArrayList<>();
        this.coinsEnabled = coinsEnabled;
        this.numOfPlayers = numOfPlayers;
        this.getProfWithDraw = false;
        this.assistantPlayed = new HashMap<>();
    }

    /**
     * This method moves students and checks also the professors and coins
     *
     * @param container The students added to current player's dining room
     */
    public void addStudentsToDining(StudentsContainer container) {

        currentPlayer.getBoard().getDiningRoom().uniteContainers(container);

        if (coinsEnabled) {
            for (int i = 0; i < currentPlayer.getBoard().calculateCoin(currentPlayer.getBoard().getDiningRoom()); i++) {
                currentPlayer.receiveCoin();
            }
        }
        for (FactionColor color : FactionColor.values()) {
            if (container.getByColor(color) > 0) {
                checkProfessors(color);
            }
        }
    }

    /**
     * This method also checks for professors, so if you lose students you can lose their professor too
     *
     * @param container The students needed to be removed from currentPlayer dining room
     * @throws NoProfChangeException When there is a draw situation
     */
    public void removeStudentsFromDining(StudentsContainer container) throws NoProfChangeException {

        HashMap<Player, Integer> playerPower = new HashMap<>();
        boolean[] controlledProf;
        Player exProfOwner;
        int boolToInt;
        boolean switchProf;
        int numStudentsControlling;
        int max1;
        int max2;
        Player playerMax1;
        Player playerMax2;
        Player currentProfOwner;


        currentPlayer.getBoard().getDiningRoom().removeContainer(container);
        if (coinsEnabled) currentPlayer.getBoard().checkCoins(currentPlayer.getBoard().getDiningRoom());


        for (FactionColor color : FactionColor.values()) {
            if (container.getByColor(color) > 0) {
                playerPower = new HashMap<>();
                switchProf = false;
                max1 = 0;
                max2 = 0;
                playerMax1 = null;
                playerMax2 = null;
                currentProfOwner = null;

                for (Player player : players) {
                    controlledProf = player.getBoard().getProfTable();
                    boolToInt = player.getBoard().getDiningRoom().getByColor(color);
                    if (controlledProf[color.getIndex()])
                        currentProfOwner = player;
                    playerPower.put(player, boolToInt);
                }

                for (Player player : players) {
                    if (playerPower.get(player) > max1) {
                        max2 = max1;
                        playerMax2 = playerMax1;
                        max1 = playerPower.get(player);
                        playerMax1 = player;
                    } else if (playerPower.get(player) > max2) {
                        max2 = playerPower.get(player);
                        playerMax2 = player;
                    }
                }
                if (max1 == max2 && max1 > playerPower.get(currentProfOwner) && playerMax1.getBoard().getTowers().getCurrentTower() != currentProfOwner.getBoard().getTowers().getCurrentTower() && playerMax2.getBoard().getTowers().getCurrentTower() != currentProfOwner.getBoard().getTowers().getCurrentTower())
                    throw new NoProfChangeException();

                numStudentsControlling = playerPower.get(currentProfOwner);
                exProfOwner = currentProfOwner;

                for (Player player : players)
                    if (playerPower.get(player) > numStudentsControlling && player.getBoard().getTowers().getCurrentTower() != currentProfOwner.getBoard().getTowers().getCurrentTower()) {
                        currentProfOwner = player;
                        numStudentsControlling = playerPower.get(player);
                        switchProf = true;
                    }

                if (switchProf) {
                    exProfOwner.getBoard().removeProf(color);
                    currentProfOwner.getBoard().addProf(color);
                }
            }
        }
    }

    /**
     * @param assistant Assistant played by the currentPlayer
     */
    private void useAssistant(Assistant assistant) {
        currentPlayer.useAssistant(assistant);
    }

    /**
     * This method prepare players, boards, currentPlayer and initialize other objects
     *
     * @param bag Bag containing total students of the game
     */
    public void setUp(Bag bag) {
        Random random = new Random();
        final int studentEntranceThreePlayers = 9;
        final int studentEntranceTwoPlayers = 7;
        this.orderPlayed = new ArrayList<>();
        this.stolenProf = new HashMap<>();
        this.nextTurnPlayer = new HashMap<>();

        for (int cont = 0; cont < numOfPlayers; cont++)
            this.players.add(new Player());
        int i = 0;
        for (Player player : players) {
            player.setBoard(new Board(numOfPlayers, TowerColor.values()[i], coinsEnabled, player));
            i++;
        }
        if (numOfPlayers == 2 || numOfPlayers == 4)
            for (Player player : players) {
                player.getBoard().addStudentsToEntrance(bag.extractStudents(studentEntranceTwoPlayers));
            }
        else
            for (Player player : players) {
                player.getBoard().addStudentsToEntrance(bag.extractStudents(studentEntranceThreePlayers));
            }
        this.currentPlayer = getPlayers().get(random.nextInt(getPlayers().size()));
        int counter = 0;
        i = players.indexOf(currentPlayer);
        while (counter <= numOfPlayers){
            this.orderPlayed.add(players.get(i));
            counter++;
            i = (i+1)%numOfPlayers;
        }
        this.orderPlayed.addAll(players);
    }

    /**
     * @param cloud Cloud from which you remove students and move them to currentPlayer's entrance
     */
    public void studentCloudToEntrance(Cloud cloud) {
        currentPlayer.getBoard().addStudentsToEntrance(cloud.removeStudents());
    }

    /**
     * Moves Students from the entrance to a specific Island
     *
     * @param island    Island to where you move students
     * @param container Students you have picked from currentPlayer's entrance
     */
    public void studentsEntranceToIsland(Island island, LimitedStudentsContainer container) {
        currentPlayer.getBoard().removeStudentsFromEntrance(container);
        FixedUnlimitedStudentsContainer temp = new FixedUnlimitedStudentsContainer();
        for (FactionColor color : FactionColor.values())
            temp.addStudents(container.getByColor(color), color);
        island.addStudents(temp);
    }

    /**
     * @return Total players in the game
     */
    public ArrayList<Player> getPlayers() {
        return players;
    }

    /**
     * Sets the flag to conquer professors even if you have same the number of students of your opponent
     */
    public void setProfWithDraw() {
        this.getProfWithDraw = true;
    }

    /**
     * Method used for deleting all characters' effects
     */
    public void resetFlags() {
        this.getProfWithDraw = false;
        for (FactionColor color : FactionColor.values())
            if (stolenProf.containsKey(color)) {
                stolenProf.get(color).getBoard().addProf(color);
                currentPlayer.getBoard().removeProf(color);
                stolenProf.remove(color);
            }
    }

    /**
     * @return The currentPlayer
     */
    public Player getCurrentPlayer() {
        return this.currentPlayer;
    }

    /**
     * @param player Player to be set as currentPlayer
     */
    public void setCurrentPlayer(Player player) {
        this.currentPlayer = player;
    }

    /**
     * This method is used to check after adding students to dining room if it's possible to take control of a professor
     *
     * @param color Faction of students you want to check professors
     */
    public void checkProfessors(FactionColor color) {
        boolean switchProf = false;
        boolean profNotAssigned = true;
        Player exControllingStudents = null;

        for (Player player : players)
            if (player.getBoard().getProfTable()[color.getIndex()])
                profNotAssigned = false;

        if (profNotAssigned) {
            currentPlayer.getBoard().addProf(color);
        } else {
            if (!currentPlayer.getBoard().getProfTable()[color.getIndex()]) {
                for (Player player : players) {
                    if (getProfWithDraw) {
                        if (currentPlayer.getBoard().getDiningRoom().getByColor(color) >= player.getBoard().getDiningRoom().getByColor(color) && player.getBoard().getProfTable()[color.getIndex()] && currentPlayer.getBoard().getTowers().getCurrentTower() != player.getBoard().getTowers().getCurrentTower()) {
                            exControllingStudents = player;
                            stolenProf.put(color, player);
                            switchProf = true;
                        }
                    } else {
                        if (currentPlayer.getBoard().getDiningRoom().getByColor(color) > player.getBoard().getDiningRoom().getByColor(color) && player.getBoard().getProfTable()[color.getIndex()] && currentPlayer.getBoard().getTowers().getCurrentTower() != player.getBoard().getTowers().getCurrentTower()) {
                            exControllingStudents = player;
                            switchProf = true;
                        }
                    }
                }
            }
            if (switchProf) {
                exControllingStudents.getBoard().removeProf(color);
                currentPlayer.getBoard().addProf(color);
            }
        }
    }

    /**
     * Checks whether the given Assistant can be played and if so it plays it
     *
     * @param assistant the Assistant that the current Player wants to play
     * @throws AssistantImpossibleToPlay
     */
    public void playAssistant(Assistant assistant) throws AssistantImpossibleToPlay {
        for (Player p : players) {
            if (!p.equals(currentPlayer)) {
                if (p.getLastAssistantPlayed() == assistant)
                    //if in the current player's deck there is another playable card different from another player's card
                    if (currentPlayer.getAssistantsDeck().keySet().stream().anyMatch(cardValue -> (assistant.getCardValue() != cardValue)))
                        throw new AssistantImpossibleToPlay("Can't play an Assistant already played by another Player in the same turn, play an another one");

            }
        }
        useAssistant(assistant);
        this.nextTurnPlayer.put(currentPlayer, assistant);
        if (orderPlayed.indexOf(currentPlayer) != orderPlayed.size())
            setCurrentPlayer(orderPlayed.get(orderPlayed.indexOf(currentPlayer) + 1));

    }

    /**
     *
     */
    public void createDeck(WizardTeam team) throws InstanceAlreadyExistsException {
        try {
            currentPlayer.createDeck(team);
        } catch (InstanceAlreadyExistsException exception) {
            throw new InstanceAlreadyExistsException(exception.toString());
        }
    }

    /**
     * last method called before the turn ends
     */
    public void nextTurn() {
        resetFlags();
        //orderPlayed.clear();
        this.orderPlayed.addAll(this.nextTurnPlayer.keySet().stream().sorted(
                new Comparator<>() {
                    /**
                     * Compares the card value played by two Players.
                     *
                     * @param p1 the Player to compare
                     * @param p2 the Player to compare
                     * @return <0: if p1 < p2, then p1 should play before p2
                     * >0: if p1 > p2, then p1 should play after p2
                     */
                    @Override
                    public int compare(Player p1, Player p2) {
                        if ((nextTurnPlayer.get(p1).getCardValue() - nextTurnPlayer.get(p2).getCardValue()) == 0) {
                            return (orderPlayed.indexOf(p1) < orderPlayed.indexOf(p2)) ? -1 : 1;
                        }
                        return nextTurnPlayer.get(p1).getCardValue() - nextTurnPlayer.get(p2).getCardValue();
                    }
                }).toList());
        setCurrentPlayer(orderPlayed.get(0));
    }
}