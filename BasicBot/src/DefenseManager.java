import java.util.ArrayList;
import java.util.List;

import bwapi.Player;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BaseLocation;

public class DefenseManager {

	private static DefenseManager instance = new DefenseManager();

	/// static singleton 객체를 리턴합니다
	public static DefenseManager Instance() {
		return instance;
	}
	
	private InformationManager infoMngr = InformationManager.Instance();
	private Player self = MyBotModule.Broodwar.self();
	
	// 위험지역으로 파견될 방어 병력 리스트 - Unit 리스트
	private List<Unit> defenseList = new ArrayList<>();
	
	public void update() {		
		// 1초에 한번 실행 > StrategyManager에서 수행하게 할 것
		// 위험지역이 존재할 경우 방어병력 투입
		DangerousLocation curDangerLoca = infoMngr.currentDangerousLocation;
		if (curDangerLoca != null) {
			executeDefense(curDangerLoca);
		}
	}
	
	// 방어병력 투입
	public void executeDefense(DangerousLocation curDangerLoca) {
		
		BaseLocation dangerLocation = curDangerLoca.getBaseLocation();	// 위험지역의 위치
		int enemyCnt = curDangerLoca.getEnemyCnt();						// 적의 병력
		int groundCnt = curDangerLoca.getGroundCnt();					// 적의 지상 병력
		int airCnt = curDangerLoca.getAirCnt();							// 적의 공중 병력
		String attackType = curDangerLoca.getAttackType().name();		// 공격 타입
		
		// 리스트 초기화
		defenseList = new ArrayList<>();
		
		switch(attackType) {
		// 지상공격시 - 탱크 + 벌쳐
		case "Ground":
			assignGroundCombatUnit(enemyCnt);
			break;
		// 공중공격시 - 골리앗
		case "Air":
			assignAirCombatUnit(enemyCnt);
			break;
		// 지상+공중공격시 - 탱크 + 벌쳐 + 골리앗
		case "Both":
			assignBothCombatUnit(groundCnt, airCnt);
			break;
		}
		
		// 방어병력 위험지역으로 이동
		if (defenseList.size() > 0) {
			
		}
	}

	private void assignGroundCombatUnit(int enemyCnt) {
		// 탱크와 벌처 보유 중인지 확인하기
		if (self.completedUnitCount(UnitType.Terran_Siege_Tank_Tank_Mode) <= 0) {
			return;
		}
		if (self.completedUnitCount(UnitType.Terran_Siege_Tank_Siege_Mode) <= 0) {
			return;
		}
		if (self.completedUnitCount(UnitType.Terran_Vulture) <= 0) {
			return;
		}	
		
		int tankCnt = 0;
		int vultureCnt = 0;
		List<Unit> unitList = self.getUnits();
		
		for(Unit unit: unitList) {
			if (unit == null || !unit.exists() || !unit.isCompleted()) continue;
			if (unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode 
					|| unit.getType() == UnitType.Terran_Siege_Tank_Siege_Mode) {
				
			} else if (unit.getType() == UnitType.Terran_Vulture) {
				
			}
		}
	}

	private void assignAirCombatUnit(int enemyCnt) {
		// 골리앗 보유 중인지 확인하기
		if (self.completedUnitCount(UnitType.Terran_Goliath) <= 0) {
			return;
		}
		
		int goliathCnt = 0;
		List<Unit> unitList = self.getUnits();
		
		for(Unit unit: unitList) {
			if (unit == null || !unit.exists() || !unit.isCompleted()) continue;
			if (unit.getType() == UnitType.Terran_Goliath) {
				defenseList.add(unit);
				goliathCnt++;
				if (goliathCnt == enemyCnt) {
					break;
				}
			}
		}
	}
	
	private void assignBothCombatUnit(int groundCnt, int airCnt) {
		// 골리앗, 탱크, 벌처 보유 중인지 확인하기
		if (self.completedUnitCount(UnitType.Terran_Goliath) <= 0) {
			return;
		}
		if (self.completedUnitCount(UnitType.Terran_Siege_Tank_Tank_Mode) <= 0) {
			return;
		}
		if (self.completedUnitCount(UnitType.Terran_Siege_Tank_Siege_Mode) <= 0) {
			return;
		}
		if (self.completedUnitCount(UnitType.Terran_Vulture) <= 0) {
			return;
		}
		
		int goliathCnt = 0;
		int tankCnt = 0;
		int vultureCnt = 0;
		List<Unit> unitList = self.getUnits();
		
		for(Unit unit: unitList) {
			if (unit == null || !unit.exists() || !unit.isCompleted()) continue;
			if (unit.getType() == UnitType.Terran_Goliath) {
				if (goliathCnt == airCnt) {
					continue;
				}
				defenseList.add(unit);
				goliathCnt++;
			} else if (unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode 
					|| unit.getType() == UnitType.Terran_Siege_Tank_Siege_Mode) {
				
			} else if (unit.getType() == UnitType.Terran_Vulture) {
				
			}
		}
	}


	
	
	
}
