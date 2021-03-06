package br.com.sbk.sbking.networking.server;

import static br.com.sbk.sbking.logging.SBKingLogger.LOGGER;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import br.com.sbk.sbking.core.Board;
import br.com.sbk.sbking.core.Card;
import br.com.sbk.sbking.core.Deal;
import br.com.sbk.sbking.core.Direction;
import br.com.sbk.sbking.core.Player;
import br.com.sbk.sbking.core.rulesets.RulesetFromShortDescriptionIdentifier;
import br.com.sbk.sbking.core.rulesets.abstractClasses.Ruleset;
import br.com.sbk.sbking.gui.models.KingGameScoreboard;
import br.com.sbk.sbking.gui.models.PositiveOrNegative;
import br.com.sbk.sbking.networking.core.serialization.DisconnectedObject;
import br.com.sbk.sbking.networking.core.serialization.Serializator;
import br.com.sbk.sbking.networking.messages.MessageConstants;

public class ClientGameSocket implements Runnable {

    private PlayerNetworkInformation playerNetworkInformation;
    private Table table;
    private boolean hasDisconnected = false;
    private Direction direction;

    public boolean isSpectator() {
        return direction == null;
    }

    public ClientGameSocket(PlayerNetworkInformation playerNetworkInformation, Direction direction, Table table) {
        this.playerNetworkInformation = playerNetworkInformation;
        this.table = table;
        this.direction = direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public void unsetDirection() {
        this.direction = null;
    }

    private void setup() throws IOException, InterruptedException {
        this.waitForClientSetup();
        if (this.isSpectator()) {
            this.sendIsSpectator();
        } else {
            this.sendIsNotSpectator();
            this.waitForClientSetup();
            this.sendDirection(direction);
        }
        this.waitForClientSetup();
    }

    @Override
    public void run() {
        LOGGER.info("Connected: " + this.getSocket());
        InetAddress inetAddress = this.getInetAddress();
        try {
            setup();
            while (!hasDisconnected) {
                processCommand();
            }
        } catch (Exception e) {
            LOGGER.error("Error:" + this.getSocket(), e);
        } finally {
            disconnect();
            LOGGER.info("Disconnected gracefully to IP:" + inetAddress);
        }
        LOGGER.info("Method run() has finished. Should TERMINATE Thread.");
    }

    private InetAddress getInetAddress() {
        return this.getSocket().getInetAddress();
    }

    private void processCommand() {

        Object readObject = this.getSerializator().tryToDeserialize(Object.class);
        if (readObject instanceof DisconnectedObject) {
            this.hasDisconnected = true;
            return;
        }

        if (this.isSpectator()) {
            if (readObject instanceof String) {
                String string = (String) readObject;
                LOGGER.info("A spectator sent this message: --" + string + "--");
                if (string.startsWith(MessageConstants.NICKNAME)) {
                    String nickname = string.substring(MessageConstants.NICKNAME.length());
                    LOGGER.info("Setting new nickname: --" + nickname + "--");
                    this.playerNetworkInformation.setNickname(nickname);
                }
            } else if (readObject instanceof Card) {
                Card playedCard = (Card) readObject;
                LOGGER.info("A spectator is trying to play the " + playedCard);
            } else if (readObject instanceof Direction) {
                Direction direction = (Direction) readObject;
                LOGGER.info("A spectator is trying to sit on " + direction);
                this.table.moveToSeat(this, direction);
            }
        } else {
            if (readObject instanceof String) {
                String string = (String) readObject;
                LOGGER.info(this.direction + " sent this message: --" + string + "--");
                if (string.startsWith(MessageConstants.NICKNAME)) {
                    String nickname = string.substring(MessageConstants.NICKNAME.length());
                    LOGGER.info("Setting new nickname: --" + nickname + "--");
                    this.playerNetworkInformation.setNickname(nickname);
                } else {
                    if (MessageConstants.POSITIVE.equals(string) || MessageConstants.NEGATIVE.equals(string)) {
                        KingGameServer kingGameServer = (KingGameServer) this.table.getGameServer();
                        PositiveOrNegative positiveOrNegative = new PositiveOrNegative();
                        if (MessageConstants.POSITIVE.equals(string)) {
                            positiveOrNegative.setPositive();
                        } else {
                            positiveOrNegative.setNegative();
                        }
                        kingGameServer.notifyChoosePositiveOrNegative(positiveOrNegative, this.direction);
                    } else {
                        MinibridgeGameServer kingGameServer = (MinibridgeGameServer) this.table.getGameServer();
                        Ruleset gameModeOrStrain = RulesetFromShortDescriptionIdentifier.identify(string);
                        if (gameModeOrStrain != null) {
                            kingGameServer.notifyChooseGameModeOrStrain(gameModeOrStrain, direction);
                        }
                    }
                }
            } else if (readObject instanceof Card) {
                Card playedCard = (Card) readObject;
                LOGGER.info(this.direction + " is trying to play the " + playedCard);
                table.getGameServer().notifyPlayCard(playedCard, this.direction);
            } else if (readObject instanceof Direction) {
                Direction direction = (Direction) readObject;
                LOGGER.info(this.direction + " is trying to leave his sit or sit on " + direction);
                this.table.moveToSeat(this, direction);
            }
        }
    }

    private void disconnect() {
        InetAddress inetAddress = this.getInetAddress();
        LOGGER.info("Entered disconnect with IP:" + inetAddress);
        try {
            LOGGER.info("Leaving from table.");
            this.leaveFromTable();
            LOGGER.info("Left table.");

            this.playerNetworkInformation.close();
        } catch (Exception e) {
            LOGGER.error(e);
        } finally {
            this.releaseResources();
        }
        LOGGER.info("Finished disconnect with IP:" + inetAddress);
    }

    private void releaseResources() {
        this.table = null;
        this.playerNetworkInformation = null;
    }

    private void leaveFromTable() {
        this.table.removeClientGameSocket(this);
    }

    private void waitForClientSetup() throws IOException, InterruptedException {
        LOGGER.info("Sleeping for 300ms waiting for client to setup itself");
        Thread.sleep(300);
    }

    public void sendDeal(Deal deal) {
        this.getSerializator().tryToSerialize(MessageConstants.DEAL);
        this.getSerializator().tryToSerialize(deal);
    }

    public void sendMessage(String string) {
        this.getSerializator().tryToSerialize(MessageConstants.MESSAGE);
        this.getSerializator().tryToSerialize(string);
    }

    public void sendBoard(Board board) {
        this.getSerializator().tryToSerialize(MessageConstants.BOARD);
        this.getSerializator().tryToSerialize(board);
    }

    public void sendDirection(Direction direction) {
        this.getSerializator().tryToSerialize(MessageConstants.DIRECTION);
        this.getSerializator().tryToSerialize(direction);
    }

    public void sendChooserPositiveNegative(Direction direction) {
        this.getSerializator().tryToSerialize(MessageConstants.CHOOSERPOSITIVENEGATIVE);
        this.getSerializator().tryToSerialize(direction);
    }

    public void sendPositiveOrNegative(String message) {
        this.getSerializator().tryToSerialize(MessageConstants.POSITIVEORNEGATIVE);
        this.getSerializator().tryToSerialize(message);
    }

    public void sendChooserGameModeOrStrain(Direction chooser) {
        this.getSerializator().tryToSerialize(MessageConstants.CHOOSERGAMEMODEORSTRAIN);
        this.getSerializator().tryToSerialize(chooser);
    }

    public void sendGameModeOrStrain(String message) {
        this.getSerializator().tryToSerialize(MessageConstants.GAMEMODEORSTRAIN);
        this.getSerializator().tryToSerialize(message);
    }

    public void sendInitializeDeal() {
        this.getSerializator().tryToSerialize(MessageConstants.INITIALIZEDEAL);
    }

    public void sendFinishDeal() {
        this.getSerializator().tryToSerialize(MessageConstants.FINISHDEAL);
    }

    public void sendFinishGame() {
        this.getSerializator().tryToSerialize(MessageConstants.FINISHGAME);
    }

    public void sendGameScoreboard(KingGameScoreboard gameScoreboard) {
        this.getSerializator().tryToSerialize(MessageConstants.GAMESCOREBOARD);
        this.getSerializator().tryToSerialize(gameScoreboard);
    }

    public void sendInvalidRuleset() {
        this.getSerializator().tryToSerialize(MessageConstants.INVALIDRULESET);
    }

    public void sendValidRuleset() {
        this.getSerializator().tryToSerialize(MessageConstants.VALIDRULESET);
    }

    public void sendIsSpectator() {
        this.getSerializator().tryToSerialize(MessageConstants.ISSPECTATOR);
    }

    public void sendIsNotSpectator() {
        this.getSerializator().tryToSerialize(MessageConstants.ISNOTSPECTATOR);
    }

    public Socket getSocket() {
        return this.playerNetworkInformation.getSocket();
    }

    public Player getPlayer() {
        return this.playerNetworkInformation.getPlayer();
    }

    public Serializator getSerializator() {
        return this.playerNetworkInformation.getSerializator();
    }

    public Direction getDirection() {
        return direction;
    }

    public PlayerNetworkInformation getPlayerNetworkInformation() {
        return playerNetworkInformation;
    }
}
