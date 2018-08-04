import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import bwapi.Color;
import bwapi.Position;
import bwapi.Race;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WalkPosition;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Chokepoint;
import bwta.Region;

public class VultureMineManager {

	// 0804 - 최혜진 수정 적군의 다리 앞으로 가는 Vulture의 unit과 각각의 상태를 관리하는 map 구조로 변경
	public static Map<Unit, Integer> vultureForMine = new HashMap<Unit, Integer>();

	// 0804 - 최혜진 추가 Vulture가 가지고 있는 Mine의 갯수를 관리하는 map
	public static Map<Unit, Integer> vultureMineCount = new HashMap<Unit, Integer>();

	private Map<Unit, Integer> duringWar;

	private static int[] mineXLocationForCircuit = { 38, 88, 34, 91 };
	private static int[] mineYLocationForCircuit = { 39, 40, 84, 84 };
	private static int[] mineXLocationForSpirit = { 0, 0, 0, 0 };
	private static int[] mineYLocationForSpirit = { 0, 0, 0, 0 };

	private static Position targetPosition = Position.None;

	public static int enemyLocationOfBase;

	private static int numberOfMineBuiltInEnemyBridge;

	public enum VultureStatus {
		NoVulture, /// < 벌쳐 유닛을 미지정한 상태
		MovingToEnemyBridge, /// < 적군의 다리 앞으로 마인을 심으러 가는 상태
		UseSpiderMine, /// < 다리 앞에 도착하여 다수의 마인을 심는 상태
		ComingBacktoMainBaseLocation, /// < 마인 심은 후 아군 BaseLocation으로 돌아오는 상태
		RunningAwayFromEnemy, /// < 마인 심지 못하여 아군 진영으로 돌아오는 상태
		AttackEnemy /// < 마인 심으러 이동 중 적군을 공격하는 상태
	};

	private CommandUtil commandUtil = new CommandUtil();

	private static VultureMineManager instance = new VultureMineManager();

	/// static singleton 객체를 리턴합니다
	public static VultureMineManager Instance() {
		return instance;
	}

	public void update() {
		// 1초에 4번만 실행합니다
		if (MyBotModule.Broodwar.getFrameCount() % 6 != 0)
			return;

		// VultureUnit 을 지정하고, VultureUnit 의 이동을 컨트롤함.
		assignVultureIfNeeded();
		moveVultureUnit();

	}

	private void assignVultureIfNeeded() {

		// 0804 - 최혜진 수정 적군으로 가는 Vulture가 없는 경우 idle 상태인 vulture 지정
		if (vultureForMine.size() == 0) {
			for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
				if (unit.getType() == UnitType.Terran_Vulture) {
					if (unit.isIdle() == true) {
						vultureForMine.put(unit, VultureStatus.MovingToEnemyBridge.ordinal());
						vultureMineCount.put(unit, 3);
					}
					// if (unit.isAttacking() == false && unit.isMoving() == false) {
					// vultureForMine.put(unit, VultureStatus.MovingToEnemyBridge.ordinal());
					// }
				}
			}
		}
	}

	private void moveVultureUnit() {

		if (vultureForMine.size() == 0) {
			return;
		}

		TilePosition tempTargetTilePosition = TilePosition.None;
		if (targetPosition == null || targetPosition == Position.None) {
			// 0802 - 최혜진 추가 Target Position이 정해지지 않은 경우 적군의 위치를 파악 후 위치 지정
			BaseLocation enemyBaseLocation = InformationManager.Instance()
					.getMainBaseLocation(MyBotModule.Broodwar.enemy());
			BaseLocation enemyFirstExpansion = InformationManager.Instance()
					.getFirstExpansionLocation(MyBotModule.Broodwar.enemy());
			Chokepoint enemySecondChockPoint = InformationManager.Instance()
					.getSecondChokePoint(MyBotModule.Broodwar.enemy());
			int dx = enemyBaseLocation.getX() - enemySecondChockPoint.getCenter().getX();
			int dy = enemyBaseLocation.getTilePosition().getY() - enemyFirstExpansion.getTilePosition().getY();
			if (dx < 0 && dy < 0) { // BaseLocation이 좌상단 위치
				enemyLocationOfBase = 1;
			} else if (dx > 0 && dy < 0) { // BaseLocation이 우상단 위치
				enemyLocationOfBase = 2;
			} else if (dx < 0 && dy > 0) { // BaseLocation이 좌하단 위치
				enemyLocationOfBase = 3;
			} else if (dx > 0 && dy > 0) { // BaseLocation이 우하단 위치
				enemyLocationOfBase = 4;
			}

			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				tempTargetTilePosition = new TilePosition(mineXLocationForCircuit[enemyLocationOfBase - 1],
						mineYLocationForCircuit[enemyLocationOfBase - 1]);
			} else {
				tempTargetTilePosition = new TilePosition(mineXLocationForSpirit[enemyLocationOfBase - 1],
						mineYLocationForSpirit[enemyLocationOfBase - 1]);
			}
			targetPosition = tempTargetTilePosition.toPosition();
			for (Unit vulture : vultureForMine.keySet()) {
				vulture.move(targetPosition);
				//useSpiderMineTech(vulture, targetPosition);
				//vultureMineCount.replace(vulture, 2);
			}
		} else {
			for (Unit vulture : vultureForMine.keySet()) {
				if(vulture.getPosition().getDistance(targetPosition)<100) {
					vulture.useTech(TechType.Spider_Mines);
					
					//vultureMineCount.replace(vulture, 2);
				}
			}
		}

	}

	// 0804 - 최혜진 추가 원하는 위치에 해당 vulture가 Spider Mine을 심는 메서드
	public void useSpiderMineTech(Unit vulture, Position position) {
		vulture.useTech(TechType.Spider_Mines, targetPosition);
	}
}
