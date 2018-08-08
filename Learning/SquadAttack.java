import java.util.List;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitCommand;
import bwapi.UnitType;

public class SquadAttack {
	
	private CommandUtil commandUtil = new CommandUtil();
	
	public boolean catchAndAttackUnit(Unit attacker, Unit target) {
		if (target == null || !target.exists()) {
			return false;
		}
		if (!target.isMoving() || !attacker.canMove() || attacker.isInWeaponRange(target))
		{
			commandUtil.attackUnit(attacker, target);
		}
		else
		{
			Position destination = PredictMovement(target, 8);	// the number is how many frames to look ahead
			//BWAPI::Broodwar->drawLineMap(attacker->getPosition(), destination, BWAPI::Colors::Blue);
			commandUtil.move(attacker, destination);
		}
		return true;
	}

	private Position PredictMovement(Unit target, int frames) {
		Position pos =  new Position(
				target.getPosition().getX() + (int)(frames * target.getVelocityX()),
				target.getPosition().getY() + (int)(frames * target.getVelocityY())
			);
		//ClipToMap(pos);
		return pos;
	}

	private void ClipToMap(Position pos) {
		int x = pos.getX();
		int y = pos.getY();
		
		if (pos.getX() < 0)
		{
			x = 0;
		}
		else if (pos.getX() >= 32 * MyBotModule.Broodwar.mapWidth())
		{
			x = 32 * MyBotModule.Broodwar.mapWidth() - 1;
		}

		if (pos.getY() < 0)
		{
			y = 0;
		}
		else if (pos.getY() >= 32 * MyBotModule.Broodwar.mapHeight())
		{
			y = 32 * MyBotModule.Broodwar.mapHeight() - 1;
		}
		
		pos = new Position(x, y);
	}
	
	/**
	 * 리버 공격
	 * @param squad
	 * @param ss
	 */
	public void reaverAttack(Squad squad, SquadState ss) {
		
		int enemyCnt = ss.getEneUnitCnt();
		List<Unit> unitList = squad.getUnits();
		
		if (enemyCnt > 0 && ss.getSelfUnitRegion() != null && ss.getTargetUnit() != null) {
			if (ss.getSelfUnitRegion().getPolygon().isInside(ss.getTargetUnit().getPosition())) {
				for (Unit unit : unitList) {
					if (unit.getType() == UnitType.Protoss_Shuttle) {
						if (unit.canUnload()) {
							unit.unloadAll();
						}
					} else if(!unit.isLoaded()) {
						commandUtil.attackMove(unit, squad.getTargetPosition());
					}
				}
			}

		} else {
			for (Unit unit : unitList) {
				if (unit.getType() == UnitType.Protoss_Shuttle) {
					unit.rightClick(squad.getTargetPosition());
				}
			}
		}
	}

	public void attackMove(Squad squad, Position targetPosition) {
		List<Unit> unitList = squad.getUnits();
		
		for (Unit unit : unitList) {
			commandUtil.attackMove(unit, targetPosition);
		}
	}

	public void movingAttack(Squad squad, SquadState ss, Position targetPosition, Position fleePosition) {
		
		List<Unit> unitList = squad.getUnits();
		
		if (ss.getSelfCooldown() > 3) {
			for (Unit unit : unitList) {
				unit.move(fleePosition);
			}
		} else {
			for (Unit unit : unitList) {
				if (ss.getTargetUnit() == null || !ss.getTargetUnit().exists()) {
					commandUtil.attackMove(unit, targetPosition);
				} else {
					unit.attack(ss.getTargetUnit());
				}
			}
		}
	}

	public void catchAndAttack(Squad squad, Unit targetUnit) {
		
		List<Unit> unitList = squad.getUnits();
		
		for (Unit unit : unitList) {
			catchAndAttackUnit(unit, targetUnit);
		}
	}

	/**
	 * 드라군 부대의 공격메서드
	 * @param squad
	 * @param ss
	 */
	public void dragoonAttack(Squad squad, SquadState ss) {
		
		int enemyCnt = ss.getEneUnitCnt();
		
		// 적군이 시야에 1~2마리 인경우 추격하면서 공격한다.
		if (enemyCnt > 0 && enemyCnt < 3) {
			catchAndAttack(squad, ss.getTargetUnit());
		} else if (enemyCnt == 0) {
			// 적군이 시야에 없으면 최종공격지점을 향해 AtackMove한다.
			attackMove(squad, squad.getTargetPosition());
		} else {
			// 적군이 시야에 3마리 이상인 경우 movingAttack을 한다.
			movingAttack(squad, ss, squad.getTargetPosition(), squad.getFleePosition());
		}
	}
}
