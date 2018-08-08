import java.util.ArrayList;
import java.util.List;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;

/**
 * 유닛부대객체
 * @author SDS
 *
 */
public class Squad {
	
	SquadAttack squadAttack = new SquadAttack(); // 부대별 공격방법정의
	private List<Unit> unitList;       // 부대에 포함된 유닛
	private List<Unit> detectUnitList; // 부대에 포함된 Detecting 유닛
	private boolean isDetect;          // Detecting 유닛이 있는지 여부
	private SquadState squadState;     // 부대유닛의 시야의 게임환경
	private String name;               // 부대명
	private Unit centerUnit;           // 부대의 대표유닛
	private Position targetPosition;   // 공격지점
	private Position fleePosition;     // 도망지점
	private boolean combatMode;        // 공격모드
	private boolean dropshipReady;     // 드랍쉽탑승여부
	
	public Squad(String name) {
		this.setName(name);
		unitList = new ArrayList<Unit>();
		detectUnitList = new ArrayList<Unit>();
		SquadState ss = new SquadState(this);
		setSquadState(ss);
		isDetect = false;
		combatMode = false;
		dropshipReady = false;
	}
	
	public int getUnitCnt() {
		return unitList.size();
	}

	public void addUnit(Unit unit) {
		if (unit.getType() == UnitType.Terran_Science_Vessel || unit.getType() == UnitType.Spell_Scanner_Sweep
				|| unit.getType() == UnitType.Terran_Comsat_Station) {
			this.addDetectUnit(unit);
		} else {
			unitList.add(unit);
		}
	}
	
	public boolean isDetect() {
		return isDetect;
	}
	
	public void setDetect(boolean isDetect) {
		this.isDetect = isDetect;
	}
	
	public SquadState getSquadState() {
		return squadState;
	}

	public void setSquadState(SquadState ss) {
		this.squadState = ss;
	}

	public void addDetectUnit(Unit unit) {
		detectUnitList.add(unit);
		setDetect(true);
	}
	public void setCenterUnit() {
		if (unitList.size() > 0) {
			centerUnit = unitList.get(0);
		}
	}
	public Unit getCenterUnit() {
		return centerUnit;
	}

	public List<Unit> getUnits() {
		return unitList;
	}

	/**
	 * 각 부대의 특성에 맞게 유닛을 추가할건지를 리턴
	 * @param unit 
	 * @return
	 */
	public boolean isAddUnit(Unit unit) {
		if ("Dragoon".equals(this.name)) {
			if (this.getUnitCnt() < 12 && unit.getType() == UnitType.Protoss_Dragoon) {
				return true;
			}
			
			if (!this.isDetect && unit.getType() == UnitType.Protoss_Observer) {
				return true;
			}
		} else if ("ReaverDrop".equals(this.name)) {
			if (unit.getType() == UnitType.Protoss_Shuttle) {
				return true;
			}
			else if (unit.getType() == UnitType.Protoss_Reaver) {
				return true;
			}
			else if (unit.getType() == UnitType.Protoss_Zealot) {
				return true;
			}
		}
		return false;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean update() {
		if (MyBotModule.Broodwar.getFrameCount()%24 == 0) {
			this.getSquadState().update(this);
			this.setCenterUnit();
			
			// 리버드랍인 경우 리버 및 공격유닛이 셔틀에 탈때까지 공격가지 않는다.
			if (!this.isShuttleReady() && "ReaverDrop".equals(this.name)) {
				for (Unit unit : unitList) {
					if (unit.getType() == UnitType.Protoss_Reaver || unit.getType() == UnitType.Protoss_Zealot) {
						if (!unit.isLoaded()) {
							for (Unit shuttl : unitList) {
								if (shuttl.getType() == UnitType.Protoss_Shuttle) {
									if (shuttl.canLoad(unit)) {
										shuttl.rightClick(unit);
										return false;
									}
								}
							}
						}
					}
				}
				this.setShuttleReady(true);
			}
			
			// 전투모드인경우 타겟을 향해 전투한다.
			if (combatMode && targetPosition != null) {
				this.attak(targetPosition);
			}
			
			if (!combatMode && fleePosition != null) {
				this.flee(fleePosition);
			}
		}
		return true;
	}

	private void flee(Position fp) {
		for (Unit unit : unitList) {
			unit.move(fp);
		}
	}

	/**
	 * 각 부대별로 공격하는 방법을 정의한다.
	 * @param tp
	 * @return
	 */
	private boolean attak(Position tp) {
		SquadState ss = this.getSquadState();
		/*
		 * 적의 공격포인트가 더 큰경우 철수
		 */
		if (ss.getEneAttackPoint() > ss.getSelfAttackPoint()) {
			combatMode = false;
			return true;
		}
		
		if ("ReaverDrop".equals(this.name)) {
			squadAttack.reaverAttack(this, ss);
		} else if ("Dragoon".equals(this.name)) {
			squadAttack.dragoonAttack(this, ss);
		}
		
		return true;
	}
	
	public Position getTargetPosition() {
		return targetPosition;
	}

	public void setTargetPosition(Position targetPosition) {
		this.targetPosition = targetPosition;
	}

	public boolean isCombatMode() {
		return combatMode;
	}

	public void setCombatMode(boolean combatMode) {
		this.combatMode = combatMode;
	}

	public Position getFleePosition() {
		return fleePosition;
	}

	public void setFleePosition(Position fleePosition) {
		this.fleePosition = fleePosition;
	}

	public boolean isShuttleReady() {
		return dropshipReady;
	}

	public void setShuttleReady(boolean shuttleReady) {
		this.dropshipReady = shuttleReady;
	}
}