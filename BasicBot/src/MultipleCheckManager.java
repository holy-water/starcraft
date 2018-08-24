import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bwapi.Player;
import bwapi.Position;
import bwapi.TechType;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Region;

public class MultipleCheckManager {

	private CommandUtil commandUtil = new CommandUtil();
	private Player self = MyBotModule.Broodwar.self();
	private Player enemy = MyBotModule.Broodwar.enemy();

	private static MultipleCheckManager instance = new MultipleCheckManager();

	public static MultipleCheckManager Instance() {
		return instance;
	}

	private MultipleExpansionManager multiExpMngr = MultipleExpansionManager.Instance();
	private InformationManager infoMngr = InformationManager.Instance();

	// 모든 지역 체크 완료했는지 판단하는 flag
	public boolean isAllChecked = false;
	public long endTime = Long.MAX_VALUE;

	// 견제 시작점
	private int minCheckNum;

	// 돌아야할 위치 리스트
	Map<Integer, BaseLocation> checkListMap = new HashMap<>();
	boolean[] isChecked;

	int targetOrderNum = -1;
	BaseLocation targetLocation = null;
	Unit currentMineVulture = null;

	public List<Unit> vultureMultiCheckList = new ArrayList<>();

	// 실행은 StrategyManager에서
	public void update() {

		// 모든 지역 체크 완료했으면 리턴
		if (isAllChecked) {
			return;
		}

		// 적진 위치를 기준으로 한 멀티 체크 리스트
		if (checkListMap == null || checkListMap.isEmpty()) {
			init();
			multiExpMngr.initialUpdate();
			checkListMap = multiExpMngr.orderOfBaseLocations;
			if (checkListMap == null || checkListMap.isEmpty()) {
				return;
			}
			isChecked = new boolean[checkListMap.size() + 1];
			// 맵별 마지막 견제 장소 세팅
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetOrderNum = 6;
				minCheckNum = 6;
			} else {
				targetOrderNum = 4;
				minCheckNum = 4;
			}
		}

		checkIfDead();

		if (vultureMultiCheckList.size() < 4) {
			assignVultureForCheck();
		}
		executeMultiCheck();
	}

	private void init() {
		// 모든 지역 체크 완료했는지 판단하는 flag
		isAllChecked = false;
		endTime = Long.MAX_VALUE;

		targetOrderNum = -1;
		targetLocation = null;
		currentMineVulture = null;
	}

	// 죽은 유닛 제거
	private void checkIfDead() {

		Unit unit;
		Position pos;
		boolean tempFlag = false;

		for (int i = 0; i < vultureMultiCheckList.size(); i++) {
			unit = vultureMultiCheckList.get(i);
			if (unit == null || !unit.exists() || unit.getHitPoints() <= 0) {
				vultureMultiCheckList.remove(unit);
				infoMngr.getUnitData(self).unitJobMap.remove(unit);
				continue;
			}
			tempFlag = false;
			if (!unit.isMoving()) {
				pos = unit.getPosition();
				for (int j = minCheckNum; j <= checkListMap.size(); j++) {
					if (BWTA.getRegion(pos) == checkListMap.get(j).getRegion()) {
						tempFlag = true;
						break;
					}
				}
				if (!tempFlag) {
					vultureMultiCheckList.remove(unit);
					infoMngr.getUnitData(self).unitJobMap.remove(unit);
				}
			}
		}
	}

	// 멀티 견제용 벌처를 4마리 이내로 뽑는다.
	private void assignVultureForCheck() {

		if (self.completedUnitCount(UnitType.Terran_Vulture) <= 2) {
			return;
		}

		for (Unit unit : self.getUnits()) {
			if (vultureMultiCheckList.size() >= 4) {
				break;
			}
			if (unit == null || !unit.exists() || !unit.isCompleted() || unit.getType() != UnitType.Terran_Vulture
					|| infoMngr.getUnitData(self).unitJobMap.containsKey(unit)
					|| vultureMultiCheckList.contains(unit)) {
				continue;
			}
			// 마인 2개 이상 가지고 있는 벌처만 뽑기
			if (unit.getSpiderMineCount() < 2) {
				continue;
			}
			vultureMultiCheckList.add(unit);
			infoMngr.getUnitData(self).unitJobMap.put(unit, UnitData.UnitJob.Check);
		}
	}

	// 견제용 벌처를 각 멀티로 투입, 어택땅
	// 적의 본진에서 가까운 데부터 시작해서 탐색
	// 아무도 없으면 마인 심기(한마리) / 먹혔으면 공격
	private void executeMultiCheck() {

		if (vultureMultiCheckList.size() <= 0) {
			return;
		}

		/// 다음 멀티 위치 정하고 이동
		if (targetLocation == null) {
			targetLocation = checkListMap.get(targetOrderNum);

			/// 다 돌았으면 체크모드 해제
			if (targetOrderNum > checkListMap.size()) {
				isAllChecked = true;
				endTime = System.currentTimeMillis();
				deactivateCheckMode();
				return;
			}

			// 벌처 -> 목적지로 이동
			for (Unit unit : vultureMultiCheckList) {
				if (unit == null || !unit.exists()) {
					continue;
				}
				commandUtil.attackMove(unit, targetLocation.getPosition());
			}
		}
		/// 목표지가 정해진 경우
		else if (targetLocation != null) {

			/// 목표지 도달 여부 확인
			boolean isInTarget = false;
			for (Unit unit : vultureMultiCheckList) {
				if (unit == null || !unit.exists()) {
					continue;
				}
				if (unit.getDistance(targetLocation.getPosition()) < 5 * Config.TILE_SIZE) {
					if (unit.getSpiderMineCount() > 0) {
						isInTarget = true;
						currentMineVulture = unit;
						break;
					}
				}
			}

			if (!isInTarget)
				return;

			List<BaseLocation> enemyBaseList = infoMngr.getOccupiedBaseLocations(enemy);
			if (!isChecked[targetOrderNum]) {
				// 적이 없으면 벌처 한마리 골라서 마인 심기
				boolean hasDone = false;
				if (!enemyBaseList.contains(targetLocation)) {
					if (isMineHere(targetLocation)) {
						hasDone = true;
					} else {
						hasDone = currentMineVulture.useTech(TechType.Spider_Mines,
								targetLocation.getRegion().getCenter());
					}
				}
				// 적이 있으면 어택땅으로 출발했기 때문에 추가 구현 불필요
				else {
					Unit enemyWorker = infoMngr.getWorkerInRegion(targetLocation.getRegion());
					if (enemyWorker == null) {
						hasDone = true;
					} else {
						for (Unit unit : vultureMultiCheckList) {
							if (unit == null || !unit.exists() || unit.isAttacking()) {
								continue;
							}
							commandUtil.attackMove(unit, enemyWorker.getPosition());
							break;
						}
					}
				}
				if (hasDone) {
					isChecked[targetOrderNum] = true;
					targetOrderNum++;
					currentMineVulture = null;
					targetLocation = null;
				}
			}
		}
	}

	// 벌처의 견제모드 해제
	private void deactivateCheckMode() {

		if (vultureMultiCheckList == null || vultureMultiCheckList.isEmpty()) {
			return;
		}

		for (Unit unit : vultureMultiCheckList) {
			infoMngr.getUnitData(self).unitJobMap.remove(unit);
		}

		vultureMultiCheckList.clear();
	}

	// 특정 벌처의 견제모드 해제
	public void deactivateCheckMode(Unit unit) {
		vultureMultiCheckList.remove(unit);
	}

	// 마인 여부 체크
	public boolean isMineHere(BaseLocation targetLocation) {

		if (self.completedUnitCount(UnitType.Terran_Vulture_Spider_Mine) <= 0) {
			return false;
		}

		boolean flag = false;
		Region region = targetLocation.getRegion();

		List<Unit> list = self.getUnits();
		for (Unit unit : list) {
			if (unit == null || !unit.exists() || unit.getHitPoints() < 0) {
				continue;
			}
			if (unit.getType() == UnitType.Terran_Vulture_Spider_Mine) {
				if (BWTA.getRegion(unit.getPosition()) == region) {
					flag = true;
					break;
				}
			}
		}
		return flag;
	}
}
