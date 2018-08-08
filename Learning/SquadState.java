import java.util.List;

import bwapi.Game;
import bwapi.Player;
import bwapi.Region;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;

/**
 * 부대의 게임환경의 상태정의
 * @author SDS
 *
 */
public class SquadState {
	
	private int selfAttackPoint; // 아군의 공격점수
	private int eneAttackPoint;  // 적군의 공격점수
	private Unit targetUnit;     // 공격해야할 유닛
	private int eneUnitCnt;      // 적군의 수
	private int selfCooldown;    // 아군의 Cooldown
	private int minDistance;     // 적군과의 최소거리
	private bwta.Region selfUnitRegion;
	
	public SquadState(Squad squad) {
		update(squad);
	}
	
	public void update(Squad squad) {

		Player enemy = MyBotModule.Broodwar.enemy();
		Unit squadCenterUnit = squad.getCenterUnit();
		List<Unit> squadUnits = squad.getUnits();
		
		/*
		 * 아군
		 */
		selfAttackPoint = 0;
		selfCooldown = 0;
		for (Unit selfUnit : squadUnits) {
			if (selfUnit == null || !selfUnit.exists()) {
				continue;
			}
			if (selfUnit.getType() == UnitType.Protoss_Dragoon) {
				selfAttackPoint = selfAttackPoint + 5;
			} else if (selfUnit.getType() == UnitType.Protoss_Zealot) {
				selfAttackPoint = selfAttackPoint + 3;
			}
			if (selfUnit.getGroundWeaponCooldown() > 0) {
				selfCooldown++;
			}
			if (selfUnit.getType() == UnitType.Protoss_Shuttle) {
				if (BWTA.getRegion(selfUnit.getTilePosition()) != null) {
					System.out.println("BWTA.getRegion(selfUnit.getTilePosition()) != null");
					this.setSelfUnitRegion(BWTA.getRegion(selfUnit.getTilePosition()));
				}
			}
		}
		
		/*
		 * 적군
		 */
		eneAttackPoint = 0;
		eneUnitCnt = 0;
		int unitDistance = Integer.MAX_VALUE;
		int tmpUnitDistance = Integer.MAX_VALUE;
		minDistance = Integer.MAX_VALUE;
		
		for (Unit enemyUnit : enemy.getUnits()) {
			if (squadCenterUnit == null || !squadCenterUnit.exists()) {
				continue;
			}
			if (enemyUnit == null || !enemyUnit.exists()) {
				continue;
			}
			// 시야에 보이지 않는 유닛은 스킵
			unitDistance = squadCenterUnit.getDistance(enemyUnit);
			if (unitDistance > 500) {
				continue;
			}
			
			eneUnitCnt++;
			
			if (enemyUnit.getType() == UnitType.Protoss_Dragoon) {
				eneAttackPoint = eneAttackPoint + 5;
			} else if (enemyUnit.getType() == UnitType.Protoss_Zealot) {
				eneAttackPoint = eneAttackPoint + 3;
			}
			
			// 가장가까운 유닛으로 타겟지정
			if (tmpUnitDistance > unitDistance) {
				tmpUnitDistance = unitDistance;
				minDistance = unitDistance;
				this.setTargetUnit(enemyUnit);
			}
		}
	}
	
	public int getSelfAttackPoint() {
		return selfAttackPoint;
	}
	public void setSelfAttackPoint(int selfAttackPoint) {
		this.selfAttackPoint = selfAttackPoint;
	}
	public int getEneAttackPoint() {
		return eneAttackPoint;
	}
	public void setEneAttackPoint(int eneAttackPoint) {
		this.eneAttackPoint = eneAttackPoint;
	}

	public Unit getTargetUnit() {
		return targetUnit;
	}

	public void setTargetUnit(Unit targetUnit) {
		this.targetUnit = targetUnit;
	}

	public int getEneUnitCnt() {
		return eneUnitCnt;
	}

	public int getSelfCooldown() {
		return selfCooldown;
	}

	public void setSelfCooldown(int selfCooldown) {
		this.selfCooldown = selfCooldown;
	}

	public int getMinDistance() {
		return minDistance;
	}

	public void setMinDistance(int minDistance) {
		this.minDistance = minDistance;
	}

	public bwta.Region getSelfUnitRegion() {
		return selfUnitRegion;
	}

	public void setSelfUnitRegion(bwta.Region region) {
		this.selfUnitRegion = region;
	}
}
