import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bwapi.Player;
import bwapi.TechType;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BaseLocation;

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
	private boolean isAllChecked = false;
	
	// 마지막으로 체크할 멀티진영 번호
	private int minCheckNum;
	
	// 돌아야할 위치 리스트
	Map<Integer, BaseLocation> checkListMap = new HashMap<>();
	Map<BaseLocation, Boolean> isCheckedMap = new HashMap<>();
	
	BaseLocation targetLocation = null;
	
	public List<Unit> vultureMultiCheckList = new ArrayList<>();
	
	// 실행은 StrategyManager에서
	public void update() {
		
		// 모든 지역 체크 완료했으면 리턴
		if (isAllChecked) {
			return;
		}
		
		// 적진 위치를 기준으로 한 멀티 체크 리스트
		if (checkListMap == null || checkListMap.isEmpty()) {
			multiExpMngr.initialUpdate();
			checkListMap = multiExpMngr.orderOfBaseLocations;
			if (checkListMap == null || checkListMap.isEmpty()) {
				return;
			}
			BaseLocation tempLoca;
			for(int i=1; i<=checkListMap.size(); i++) {
				tempLoca = checkListMap.get(i);
				isCheckedMap.put(tempLoca, false);
			}
			// 맵별 마지막 견제 장소 세팅
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				minCheckNum = 6;
			} else {
				minCheckNum = 3;
			}
		}
				
		if (vultureMultiCheckList.size() < 6) {
			assignVultureForCheck();
		}
		executeMultiCheck();
	}
	
	// 멀티 견제용 벌처를 6마리 이내로 뽑는다.
	private void assignVultureForCheck() {
		
		if (self.completedUnitCount(UnitType.Terran_Vulture) <= 6) {
			return;
		}
		
		for (Unit unit:self.getUnits()) {
			if (vultureMultiCheckList.size() >= 6) {
				break;
			}
			if (unit == null || !unit.exists() || !unit.isCompleted()
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

		if (checkListMap.isEmpty()) {
			return;
		}
		if (vultureMultiCheckList.size() <= 0) {
			return;
		}
		
		/// 다음 멀티 위치 정하고 이동
		if (targetLocation == null) {
			BaseLocation tempLoca;
			for (int i=checkListMap.size(); i>=minCheckNum; i--) {
				tempLoca = checkListMap.get(i);
				if (!isCheckedMap.get(tempLoca)) {
					targetLocation = tempLoca;
					break;
				}
			}
			/// 다 돌았으면 체크모드 해제
			if (targetLocation == null) {
				System.out.println("다했다!");
				isAllChecked = true;
				deactivateCheckMode();
				return;
			}
			for (Unit unit: vultureMultiCheckList) {
				if (unit == null || !unit.exists()) {
					continue;
				}
				commandUtil.attackMove(unit, targetLocation.getPosition());
			}
		}
		/// 목표지에 도달했을 경우
		else if (targetLocation != null 
					&& vultureMultiCheckList.get(0).getDistance(targetLocation.getPosition()) < 5 * Config.TILE_SIZE) {
			if (!isCheckedMap.get(targetLocation)) {
				List<BaseLocation> enemyBaseList = infoMngr.getOccupiedBaseLocations(enemy);
				// 적이 있으면 어택땅으로 출발했기 때문에 추가 구현 불필요
				// 적이 없으면 벌처 한마리 골라서 마인 심기
				if (!enemyBaseList.contains(targetLocation)) {
					for (Unit unit: vultureMultiCheckList) {
						if (unit == null || !unit.exists() || unit.getSpiderMineCount() <= 0) {
							continue;
						}
						unit.useTech(TechType.Spider_Mines, targetLocation.getPosition());
						targetLocation = null;
						break;
					}
				}
				isCheckedMap.put(targetLocation, true);				
			} else {
				
				boolean isAttackMode = false;
				for (Unit unit: vultureMultiCheckList) {
					if (unit == null || !unit.exists()) {
						continue;
					}
					if (unit.isAttacking()) {
						isAttackMode = true; 
						break;
					}
				}
				if (!isAttackMode) {
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
		
		for (Unit unit: vultureMultiCheckList) {
			if (unit == null || !unit.exists()) {
				continue;
			}
			infoMngr.getUnitData(self).unitJobMap.remove(unit);
		}
		
		vultureMultiCheckList.clear();
	}
	
	// 특정 벌처의 견제모드 해제
	public void deactivateCheckMode(Unit unit) {
		
		if (unit == null || !unit.exists()) {
			return;
		}
		vultureMultiCheckList.remove(unit);
	}
}
