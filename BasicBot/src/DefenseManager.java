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

	private CommandUtil commandUtil = new CommandUtil();
	private InformationManager infoMngr = InformationManager.Instance();
	private Player self = MyBotModule.Broodwar.self();

	// 위험지역으로 파견될 방어 병력 리스트 - Unit 리스트
	public List<Unit> defenseList = new ArrayList<>();

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

		BaseLocation dangerLocation = curDangerLoca.getBaseLocation(); // 위험지역의
																		// 위치
		int groundCnt = curDangerLoca.getGroundCnt(); // 적의 지상 병력
		int airCnt = curDangerLoca.getAirCnt(); // 적의 공중 병력

		// 리스트 초기화
		defenseList = new ArrayList<>();

		// 유닛 배정
		assignGroundCombatUnit(groundCnt);
		assignAirCombatUnit(airCnt);

		// 방어병력 위험지역으로 이동
		if (defenseList.size() > 0) {
			for (int i = 0; i < defenseList.size(); i++) {
				commandUtil.attackMove(defenseList.get(i), dangerLocation.getPosition());
			}
		}
	}

	// 지상공격시 - 탱크 + 벌쳐
	private void assignGroundCombatUnit(int enemyCnt) {

		if (enemyCnt == 0)
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
			if (unit == null || !unit.exists() || !unit.isCompleted())
				continue;
			if (unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode) {
				if (tankCnt == enemyCnt / 2)
					continue;
				defenseList.add(unit);
				tankCnt++;
			} else if (unit.getType() == UnitType.Terran_Siege_Tank_Siege_Mode) {
				if (tankCnt == enemyCnt / 2)
					continue;
				unit.unsiege();
				defenseList.add(unit);
				tankCnt++;
			} else if (unit.getType() == UnitType.Terran_Vulture) {
				if (vultureCnt == enemyCnt / 2)
					continue;
				defenseList.add(unit);
				vultureCnt++;
			}
		}

		// 탱크가 부족한 만큼 벌처로 보완
		if (tankCnt < enemyCnt / 2 && vultureCnt == enemyCnt / 2) {
			for (Unit unit : unitList) {
				if (unit == null || !unit.exists() || !unit.isCompleted())
					continue;
				if (unit.getType() == UnitType.Terran_Vulture) {
					defenseList.add(unit);
					vultureCnt++;
					if (vultureCnt + tankCnt == enemyCnt)
						break;
				}
			}
		}
		// 벌처가 부족한 만큼 탱크로 보완
		else if (tankCnt == enemyCnt / 2 && vultureCnt < enemyCnt / 2) {
			for (Unit unit : unitList) {
				if (unit == null || !unit.exists() || !unit.isCompleted())
					continue;
				if (unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode) {
					defenseList.add(unit);
					tankCnt++;
					if (tankCnt + vultureCnt == enemyCnt)
						break;
				} else if (unit.getType() == UnitType.Terran_Siege_Tank_Siege_Mode) {
					unit.unsiege();
					defenseList.add(unit);
					tankCnt++;
					if (tankCnt + vultureCnt == enemyCnt)
						break;
				}
			}
		}
	}

	// 공중공격시 - 골리앗
	private void assignAirCombatUnit(int enemyCnt) {

		if (enemyCnt == 0)
			return;

		// 골리앗 보유 중인지 확인하기
		if (self.completedUnitCount(UnitType.Terran_Goliath) <= 0) {
			return;
		}

		int goliathCnt = 0;
		List<Unit> unitList = self.getUnits();

		for (Unit unit : unitList) {
			if (unit == null || !unit.exists() || !unit.isCompleted())
				continue;
			if (unit.getType() == UnitType.Terran_Goliath) {
				defenseList.add(unit);
				goliathCnt++;
				if (goliathCnt == enemyCnt) {
					break;
				}
			}
		}
	}
}
