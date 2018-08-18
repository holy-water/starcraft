import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bwapi.Player;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BaseLocation;

public class MultipleCheckManager {

	private CommandUtil commandUtil = new CommandUtil();
	private Player self = MyBotModule.Broodwar.self();
	
	private static MultipleCheckManager instance = new MultipleCheckManager();
	public static MultipleCheckManager Instance() {
		return instance;
	}
	
	private MultipleExpansionManager multiExpMngr = MultipleExpansionManager.Instance();
	private InformationManager infoMngr = InformationManager.Instance();
	
	// 돌아야할 위치 리스트
	Map<Integer, BaseLocation> checkListMap = new HashMap<>();
	
	public List<Unit> vultureMultiCheckList = new ArrayList<>();
	
	// 실행은 StrategyManager에서
	public void update() {
		
		if (checkListMap.isEmpty()) {
			checkListMap = multiExpMngr.orderOfBaseLocations;
		}
		assignVultureForCheck();
		executeMultiCheck();
	}
	
	// 멀티 견제용 벌처를 6마리 이내로 뽑는다.
	private void assignVultureForCheck() {
		
		if (self.completedUnitCount(UnitType.Terran_Vulture) <= 0) {
			return;
		}
		
		for (Unit unit:self.getUnits()) {
			if (vultureMultiCheckList.size() >= 6) {
				break;
			}
			if (unit == null || !unit.exists() || !unit.isCompleted()
					|| infoMngr.getUnitData(self).unitJobMap.containsKey(unit)
					|| unit.isAttacking() || unit.isMoving()) {
				continue;
			}
			// TODO 리스트 초기화여부에 따라 포함여부 체크해야
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
		
		BaseLocation targetLoca;
		for (int i=0; i<checkListMap.size(); i++) {
			targetLoca = checkListMap.get(i);
			if (MyBotModule.Broodwar.isExplored(targetLoca.getTilePosition())) {
				for (int j=0; j<vultureMultiCheckList.size(); i++) {
					commandUtil.attackMove(vultureMultiCheckList.get(j), targetLoca.getPosition());
				}
			}
		}
	}
}
