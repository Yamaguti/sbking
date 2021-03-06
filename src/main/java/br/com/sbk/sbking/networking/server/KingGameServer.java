package br.com.sbk.sbking.networking.server;

import static br.com.sbk.sbking.logging.SBKingLogger.LOGGER;

import br.com.sbk.sbking.core.Card;
import br.com.sbk.sbking.core.Deal;
import br.com.sbk.sbking.core.Direction;
import br.com.sbk.sbking.core.KingGame;
import br.com.sbk.sbking.core.Player;
import br.com.sbk.sbking.core.exceptions.SelectedPositiveOrNegativeInAnotherPlayersTurnException;
import br.com.sbk.sbking.core.rulesets.abstractClasses.Ruleset;
import br.com.sbk.sbking.gui.models.PositiveOrNegative;
import br.com.sbk.sbking.networking.server.notifications.GameModeOrStrainNotification;
import br.com.sbk.sbking.networking.server.notifications.PositiveOrNegativeNotification;

public class KingGameServer extends GameServer {

    private PositiveOrNegativeNotification positiveOrNegativeNotification;
    private PositiveOrNegative currentPositiveOrNegative;
    private GameModeOrStrainNotification gameModeOrStrainNotification;
    private Ruleset currentGameModeOrStrain;
    private boolean isRulesetPermitted;

    private KingGame kingGame;

    public KingGameServer() {
        this.game = new KingGame();
    }

    @Override
    public void run() {
        LOGGER.info("Sleeping for 500ms waiting for clients to setup themselves");
        sleepFor(500);

        this.game = new KingGame();
        this.kingGame = (KingGame) this.game;

        while (!game.isFinished()) {
            this.game.dealNewBoard();

            do {
                for (Direction direction : Direction.values()) {
                    Player player = this.table.getPlayerOf(direction);
                    this.game.setPlayerOf(direction, player);
                }

                this.gameModeOrStrainNotification = new GameModeOrStrainNotification();
                this.positiveOrNegativeNotification = new PositiveOrNegativeNotification();
                this.table.getMessageSender().sendInitializeDealAll();
                LOGGER.info("Sleeping for 300ms waiting for clients to initialize its deals.");
                sleepFor(300);
                this.table.getMessageSender().sendBoardAll(this.game.getCurrentBoard());
                sleepFor(300);
                this.table.getMessageSender()
                        .sendChooserPositiveNegativeAll(this.getCurrentPositiveOrNegativeChooser());

                synchronized (positiveOrNegativeNotification) {
                    // wait until object notifies - which relinquishes the lock on the object too
                    try {
                        LOGGER.info(
                                "I am waiting for some thread to notify that it wants to choose positive or negative");
                        positiveOrNegativeNotification.wait(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    while (positiveOrNegativeNotification.getPositiveOrNegative() == null) {
                        try {
                            LOGGER.info(
                                    "I am waiting for some thread to notify that it wants to choose game Mode Or Strain");
                            positiveOrNegativeNotification.wait(3000);
                            this.table.getMessageSender()
                                    .sendChooserPositiveNegativeAll(this.getCurrentGamePositiveOrNegativeChooser());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                LOGGER.info("I received that is going to be "
                        + positiveOrNegativeNotification.getPositiveOrNegative().toString());
                this.currentPositiveOrNegative = positiveOrNegativeNotification.getPositiveOrNegative();
                this.table.getMessageSender().sendPositiveOrNegativeAll(this.currentPositiveOrNegative);
                sleepFor(300);
                this.table.getMessageSender().sendChooserGameModeOrStrainAll(this.getCurrentGameModeOrStrainChooser());

                synchronized (gameModeOrStrainNotification) {
                    // wait until object notifies - which relinquishes the lock on the object too
                    while (gameModeOrStrainNotification.getGameModeOrStrain() == null) {
                        LOGGER.info("getGameModeOrStrain:" + gameModeOrStrainNotification.getGameModeOrStrain());
                        try {
                            LOGGER.info(
                                    "I am waiting for some thread to notify that it wants to choose game Mode Or Strain");
                            gameModeOrStrainNotification.wait(3000);
                            this.table.getMessageSender().sendPositiveOrNegativeAll(this.currentPositiveOrNegative);
                            sleepFor(300);
                            this.table.getMessageSender()
                                    .sendChooserGameModeOrStrainAll(this.getCurrentGameModeOrStrainChooser());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                LOGGER.info("I received that is going to be "
                        + gameModeOrStrainNotification.getGameModeOrStrain().getShortDescription());
                this.currentGameModeOrStrain = gameModeOrStrainNotification.getGameModeOrStrain();

                isRulesetPermitted = this.kingGame.isGameModePermitted(this.currentGameModeOrStrain,
                        this.getCurrentGameModeOrStrainChooser());

                if (!isRulesetPermitted) {
                    LOGGER.info("This ruleset is not permitted. Restarting choose procedure");
                    this.table.getMessageSender().sendInvalidRulesetAll();
                } else {
                    this.table.getMessageSender().sendValidRulesetAll();
                }

            } while (!isRulesetPermitted);

            this.table.getMessageSender()
                    .sendGameModeOrStrainShortDescriptionAll(this.currentGameModeOrStrain.getShortDescription());

            LOGGER.info("Sleeping for 300ms waiting for everything come out right.");
            sleepFor(300);
            LOGGER.info("Everything selected! Game commencing!");
            this.kingGame.addRuleset(currentGameModeOrStrain);

            Deal currentDeal = this.game.getCurrentDeal();
            for (Direction direction : Direction.values()) {
                currentDeal.setPlayerOf(direction, this.table.getPlayerOf(direction));
            }

            this.dealHasChanged = true;
            LOGGER.info("Sleeping for 300ms waiting for all clients to prepare themselves.");
            sleepFor(300);
            while (!this.game.getCurrentDeal().isFinished()) {
                if (this.dealHasChanged) {
                    LOGGER.info("Sending new 'round' of deals");
                    this.table.getMessageSender().sendDealAll(this.game.getCurrentDeal());
                    this.dealHasChanged = false;
                }
                synchronized (cardPlayNotification) {
                    // wait until object notifies - which relinquishes the lock on the object too
                    try {
                        LOGGER.info("I am waiting for some thread to notify that it wants to play a card.");
                        cardPlayNotification.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Direction directionToBePlayed = cardPlayNotification.getDirection();
                Card cardToBePlayed = cardPlayNotification.getCard();
                LOGGER.info(
                        "Received notification that " + directionToBePlayed + " wants to play the " + cardToBePlayed);
                try {
                    this.playCard(cardToBePlayed, directionToBePlayed);
                } catch (Exception e) {
                    throw e;
                }
            }

            LOGGER.info("Sending last 'round' of deals");
            this.table.getMessageSender().sendDealAll(this.game.getCurrentDeal());
            LOGGER.info("Sleeping for 3000ms for everyone to see the last card.");
            sleepFor(3000);
            this.game.finishDeal();
            this.table.getMessageSender().sendGameScoreboardAll(this.kingGame.getGameScoreboard());

            LOGGER.info("Sleeping for 300ms waiting for all clients to prepare themselves.");
            sleepFor(300);
            this.table.getMessageSender().sendFinishDealAll();
            LOGGER.info("Deal finished!");
            LOGGER.info("Sleeping for 300ms waiting for all clients to prepare themselves.");
            sleepFor(300);
        }

        this.table.getMessageSender().sendFinishGameAll();
        LOGGER.info("Game has ended.");
    }

    public void notifyChoosePositiveOrNegative(PositiveOrNegative positiveOrNegative, Direction direction) {
        synchronized (positiveOrNegativeNotification) {
            if (this.getCurrentPositiveOrNegativeChooser() == direction) {
                this.positiveOrNegativeNotification.notifyAllWithPositiveOrNegative(positiveOrNegative);
            } else {
                throw new SelectedPositiveOrNegativeInAnotherPlayersTurnException();
            }
        }
    }

    public void notifyChooseGameModeOrStrain(Ruleset gameModeOrStrain, Direction direction) {
        synchronized (gameModeOrStrainNotification) {
            if (this.getCurrentGameModeOrStrainChooser() == direction) {
                this.gameModeOrStrainNotification.notifyAllWithGameModeOrStrain(gameModeOrStrain);
            } else {
                throw new SelectedPositiveOrNegativeInAnotherPlayersTurnException();
            }
        }
    }

    private Direction getCurrentPositiveOrNegativeChooser() {
        return this.game.getDealer().getPositiveOrNegativeChooserWhenDealer();
    }

    private Direction getCurrentGameModeOrStrainChooser() {
        return this.game.getDealer().getGameModeOrStrainChooserWhenDealer();
    }

    private Direction getCurrentGamePositiveOrNegativeChooser() {
        return this.game.getDealer().getPositiveOrNegativeChooserWhenDealer();
    }

}
