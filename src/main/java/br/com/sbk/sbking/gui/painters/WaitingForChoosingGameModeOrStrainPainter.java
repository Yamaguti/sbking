package br.com.sbk.sbking.gui.painters;

import static br.com.sbk.sbking.gui.constants.FrameConstants.TABLE_HEIGHT;
import static br.com.sbk.sbking.gui.constants.FrameConstants.TABLE_WIDTH;

import java.awt.Container;
import java.awt.Point;

import br.com.sbk.sbking.core.Direction;
import br.com.sbk.sbking.gui.elements.ChooseGameModeOrStrainElement;
import br.com.sbk.sbking.gui.elements.GameScoreboardElement;
import br.com.sbk.sbking.gui.elements.WaitingForChooserGameModeOrStrainElement;
import br.com.sbk.sbking.gui.elements.YouArePlayerElement;
import br.com.sbk.sbking.gui.models.GameScoreboard;
import br.com.sbk.sbking.networking.SBKingClient;

public class WaitingForChoosingGameModeOrStrainPainter {

	private Direction myDirection;
	private Direction chooserDirection;
	private boolean isPositive;
	private SBKingClient sbKingClient;
	private GameScoreboard gameScoreboard;

	public WaitingForChoosingGameModeOrStrainPainter(Direction myDirection, Direction chooserDirection,
			boolean isPositive, SBKingClient sbKingClient, GameScoreboard gameScoreboard) {
		this.myDirection = myDirection;
		this.chooserDirection = chooserDirection;
		this.isPositive = isPositive;
		this.sbKingClient = sbKingClient;
		this.gameScoreboard = gameScoreboard;
	}

	public void paint(Container contentPane) {
		Point middleOfScreen = new Point(TABLE_WIDTH / 2, TABLE_HEIGHT / 2);
		new GameScoreboardElement(gameScoreboard, contentPane, middleOfScreen);
		if (myDirection != chooserDirection) {
			YouArePlayerElement.add(this.myDirection, contentPane);
			WaitingForChooserGameModeOrStrainElement.add(contentPane, chooserDirection);
		} else {
			ChooseGameModeOrStrainElement chooseGameModeOrStrainElement = new ChooseGameModeOrStrainElement(contentPane,
					this.chooserDirection, this.sbKingClient, this.isPositive);
			chooseGameModeOrStrainElement.add();
		}
		contentPane.validate();
		contentPane.repaint();

	}

}