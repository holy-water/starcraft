import java.util.ArrayList;
import java.util.List;

import bwapi.Player;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BaseLocation;

public class DefenseManager {

	private static DefenseManager instance = new DefenseManager();

	public static DefenseManager Instance() {
		return instance;
	}

	private CommandUtil commandUtil = new CommandUtil();
	private UnitData unitdata = new UnitData();
	private InformationManager infoMngr = InformationManager.Instance();
	private Player self = MyBotModule.Broodwar.self();

	// 위험지역으로 파견될 방어 병력 리스트 - Unit 리스트
	public List<Unit> groundDefenseList = new ArrayList<>();
	public List<Unit> airDefenseList = new ArrayList<>();

	private boolean defenseFlag = false;

	public void update() {
		// 1초에 한번 실행 > StrategyManager에서 수행하게 할 것
		// 위험지역이 존재할 경우 방어병력 투입
		DangerousLocation curDangerLoca = infoMngr.currentDangerousLocation;
		if (curDangerLoca != null) {
			if (!defenseFlag) {
				defenseFlag = true;
			}
			executeDefense(curDangerLoca);
		} else {
			// 병력 역할 해제
			if (defenseFlag) {
				deactivateDefense();
			}
		}
	}

	// 방어병력 투입
	public void executeDefense(DangerousLocation curDangerLoca) {

		BaseLocation dangerLocation = curDangerLoca.getBaseLocation(); // 위험지역 위치

		int groundCnt = curDangerLoca.getGroundCnt(); // 적의 지상 병력
		int airCnt = curDangerLoca.getAirCnt(); // 적의 공중 병력

		// 죽은 유닛 List 삭제
		checkIfDead();

		if (curDangerLoca.getEnemyCnt() >= 6) {
			// 위험 지역에 병력 모두 투입
			assignAllCombatUnit();
		} else {
			// 지상 유닛 배정
			if (groundDefenseList.size() < groundCnt) {
				assignGroundCombatUnit(groundCnt - groundDefenseList.size());
			}
			// 공중 유닛 배정
			if (airDefenseList.size() < airCnt) {
				assignAirCombatUnit(airCnt - airDefenseList.size());
			}
		}

		// 방어병력 위험지역으로 이동
		if (groundDefenseList.size() > 0) {
			for (int i = 0; i < groundDefenseList.size(); i++) {
				commandUtil.attackMove(groundDefenseList.get(i), dangerLocation.getPosition());
			}
		}
		if (airDefenseList.size() > 0) {
			for (int i = 0; i < airDefenseList.size(); i++) {
				commandUtil.attackMove(airDefenseList.get(i), dangerLocation.getPosition());
			}
		}
	}

	// 죽은 Unit 제거
	private void checkIfDead() {

		if (groundDefenseList.size() > 0) {
			for (int i = 0; i < groundDefenseList.size(); i++) {
				if (groundDefenseList.get(i) == null || !groundDefenseList.get(i).exists()
						|| groundDefenseList.get(i).getHitPoints() <= 0) {
					groundDefenseList.remove(i);
				}
			}
		}
		if (airDefenseList.size() > 0) {
			for (int i = 0; i < airDefenseList.size(); i++) {
				if (airDefenseList.get(i) == null || !airDefenseList.get(i).exists()
						|| airDefenseList.get(i).getHitPoints() <= 0) {
					airDefenseList.remove(i);
				}
			}
		}
	}

	// 총공격
	private void assignAllCombatUnit() {

		List<Unit> unitList = self.getUnits();
		for (Unit unit : unitList) {
			if (unit == null || !unit.exists() || !unit.isCompleted()) {
				continue;
			}

			if (unit.getType().isWorker() || unit.getType().isBuilding()) {
				continue;
			}

			if (infoMngr.getUnitData(self).unitJobMap.containsKey(unit)) {
				continue;
			}

			if (infoMngr.isCombatUnitType(unit.getType())) {
				if (unit.getType().isFlyer()) {
					airDefenseList.add(unit);
				} else {
					groundDefenseList.add(unit);
				}
				infoMngr.getUnitData(self).unitJobMap.put(unit, UnitData.UnitJob.Defense);
			}
		}
	}

	// 지상공격시 - 탱크 + 벌쳐
	private void assignGroundCombatUnit(int enemyCnt) {

		if (enemyCnt <= 0)
			return;

		// 탱크와 벌처 보유 중인지 확인하기
		if (self.completedUnitCount(UnitType.Terran_Siege_Tank_Tank_Mode) <= 0
				&& self.completedUnitCount(UnitType.Terran_Siege_Tank_Siege_Mode) <= 0
				&& self.completedUnitCount(UnitType.Terran_Vulture) <= 0) {
			return;
		}

		if (enemyCnt % 2 == 1)
			enemyCnt++;

		int tankCnt = 0;
		int vultureCnt = 0;
		List<Unit> unitList = self.getUnits();

		for (Unit unit : unitList) {
			// 유닛이 정상 유닛이고 임무가 없는 상태인 경우에만 선택
			if (unit == null || !unit.exists() || !unit.isCompleted()
					|| infoMngr.getUnitData(self).unitJobMap.containsKey(unit)) {
				continue;
			}

			if (unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode) {
				if (tankCnt == enemyCnt / 2) {
					continue;
				}
				groundDefenseList.add(unit);
				infoMngr.getUnitData(self).unitJobMap.put(unit, UnitData.UnitJob.Defense);
				tankCnt++;
			} else if (unit.getType() == UnitType.Terran_Siege_Tank_Siege_Mode) {
				if (tankCnt == enemyCnt / 2) {
					continue;
				}
				unit.unsiege();
				groundDefenseList.add(unit);
				infoMngr.getUnitData(self).unitJobMap.put(unit, UnitData.UnitJob.Defense);
				tankCnt++;
			} else if (unit.getType() == UnitType.Terran_Vulture) {
				if (vultureCnt == enemyCnt / 2) {
					continue;
				}
				groundDefenseList.add(unit);
				infoMngr.getUnitData(self).unitJobMap.put(unit, UnitData.UnitJob.Defense);
				vultureCnt++;
			}
		}

		// 탱크가 부족한 만큼 벌처로 보완
		if (tankCnt < enemyCnt / 2 && vultureCnt == enemyCnt / 2) {
			for (Unit unit : unitList) {
				if (unit == null || !unit.exists() || !unit.isCompleted()
						|| infoMngr.getUnitData(self).unitJobMap.containsKey(unit)) {
					continue;
				}
				if (unit.getType() == UnitType.Terran_Vulture) {
					groundDefenseList.add(unit);
					infoMngr.getUnitData(self).unitJobMap.put(unit, UnitData.UnitJob.Defense);
					vultureCnt++;
					if (vultureCnt + tankCnt == enemyCnt)
						break;
				}
			}
		}
		// 벌처가 부족한 만큼 탱크로 보완
		else if (tankCnt == enemyCnt / 2 && vultureCnt < enemyCnt / 2) {
			for (Unit unit : unitList) {
				if (unit == null || !unit.exists() || !unit.isCompleted()
						|| infoMngr.getUnitData(self).unitJobMap.containsKey(unit)) {
					continue;
				}
				if (unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode) {
					groundDefenseList.add(unit);
					infoMngr.getUnitData(self).unitJobMap.put(unit, UnitData.UnitJob.Defense);
					tankCnt++;
					if (tankCnt + vultureCnt == enemyCnt)
						break;
				} else if (unit.getType() == UnitType.Terran_Siege_Tank_Siege_Mode) {
					unit.unsiege();
					groundDefenseList.add(unit);
					infoMngr.getUnitData(self).unitJobMap.put(unit, UnitData.UnitJob.Defense);
					tankCnt++;
					if (tankCnt + vultureCnt == enemyCnt)
						break;
				}
			}
		}

		// 그래도 부족한 경우 Mine, Check 임무 가지고 있는 벌처로 보완
		if (vultureCnt + tankCnt < enemyCnt) {
			for (Unit unit : infoMngr.getUnitData(self).unitJobMap.keySet()) {
				if (unit == null || !unit.exists() || !unit.isCompleted()
						|| infoMngr.getUnitData(self).unitJobMap.get(unit) == UnitData.UnitJob.Defense) {
					continue;
				}
				if (unit.getType() == UnitType.Terran_Vulture) {
					groundDefenseList.add(unit);
					infoMngr.getUnitData(self).unitJobMap.put(unit, UnitData.UnitJob.Defense);
					VultureMineManager.Instance().removeFromVultureForMine(unit);
					MultipleCheckManager.Instance().deactivateCheckMode(unit);
					vultureCnt++;
					if (vultureCnt + tankCnt == enemyCnt)
						break;
				}
			}
		}
	}

	// 공중공격시 - 골리앗
	private void assignAirCombatUnit(int enemyCnt) {

		if (enemyCnt <= 0)
			return;

		// 골리앗 보유 중인지 확인하기
		if (self.completedUnitCount(UnitType.Terran_Goliath) <= 0) {
			return;
		}

		int goliathCnt = 0;
		List<Unit> unitList = self.getUnits();

		for (Unit unit : unitList) {
			if (unit == null || !unit.exists() || !unit.isCompleted()
					|| infoMngr.getUnitData(self).unitJobMap.containsKey(unit)) {
				continue;
			}
			if (unit.getType() == UnitType.Terran_Goliath) {
				airDefenseList.add(unit);
				infoMngr.getUnitData(self).unitJobMap.put(unit, UnitData.UnitJob.Defense);
				goliathCnt++;
				if (goliathCnt == enemyCnt) {
					break;
				}
			}
		}
	}

	// 방어병력 공격모드 해제
	private void deactivateDefense() {

		Unit defenseUnit;
		for (int i = 0; i < groundDefenseList.size(); i++) {
			defenseUnit = groundDefenseList.get(i);
			infoMngr.getUnitData(self).unitJobMap.remove(defenseUnit);
		}
		for (int i = 0; i < airDefenseList.size(); i++) {
			defenseUnit = airDefenseList.get(i);
			infoMngr.getUnitData(self).unitJobMap.remove(defenseUnit);
		}
		defenseFlag = false;
		groundDefenseList.clear();
		airDefenseList.clear();
	}
}
