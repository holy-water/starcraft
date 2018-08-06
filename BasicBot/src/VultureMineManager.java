import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
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
	public Map<Unit, Integer> vultureForMine = new HashMap<Unit, Integer>();

	private static int[] mineXLocationForCircuit = { 36, 90, 36, 90 };
	private static int[] mineYLocationForCircuit = { 41, 41, 85, 85 };
	private static int[] mineXLocationForSpirit = { 36, 83, 44, 89 };
	private static int[] mineYLocationForSpirit = { 44, 32, 95, 82 };

	private static Position targetPosition = Position.None;
	private static Position minePlacementPosition = Position.None;

	public static int enemyLocationOfBase;

	public static int doneWithMinePlacement;

	public static int complete;

	public enum VultureStatus {
		TargetNotAssigned, /// < 벌쳐 유닛의 target 위치 지정이 안된 상태
		MovingToEnemyBridge, /// < 적군의 다리 앞으로 마인을 심으러 가는 상태
		PlaceSpiderMine, /// < 적군 다리에 마인 심는 상태
		ComingBacktoMainBaseLocation, /// < 마인 심은 후 아군 BaseLocation으로 돌아오는 상태
		RunningAwayFromEnemy, /// < 마인 심지 못하여 아군 진영으로 돌아오는 상태
		AttackEnemy, /// < 마인 심으러 이동 중 적군을 공격하는 상태
		MissionComplete /// < 마인 심고 복귀 완료한 상태
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
		// 0805 - 최혜진 수정 idle 상태가 아닌 attack/move가 아닌 것으로 변경
		if (vultureForMine.size() < 6) {
			for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
				if (unit.getType() == UnitType.Terran_Vulture) {
					if (vultureForMine.containsKey(unit)) {
						continue;
					}
					if (unit.isAttacking() == false && unit.isMoving() == false) {
						vultureForMine.put(unit, VultureStatus.TargetNotAssigned.ordinal());
					}
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
			// 0806 - 최혜진 추가 적 본진 모를때 로직 수행 불가
			if(enemyBaseLocation == null || enemyFirstExpansion == null || enemySecondChockPoint == null) {
				return;
			}
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
				// 0806 - 죽은 Vulture에 대한 Nullpointer 에러 핸들링
				if (vulture.exists()) {
					vulture.move(targetPosition);
					vultureForMine.replace(vulture, VultureStatus.MovingToEnemyBridge.ordinal());
				}
			}
		} else {
			if (vultureForMine.size() == 0 || vultureForMine.isEmpty() == true) {
				return;
			}
			for (Unit vulture : vultureForMine.keySet()) {
				// 0806 - 죽은 Vulture에 대한 Nullpointer 에러 핸들링
				if (vulture.exists()) {
					if (vultureForMine.get(vulture) == VultureStatus.TargetNotAssigned.ordinal()) {
						vulture.move(targetPosition);
						vultureForMine.replace(vulture, VultureStatus.MovingToEnemyBridge.ordinal());
					} else if (vultureForMine.get(vulture) == VultureStatus.MovingToEnemyBridge.ordinal()) {
						if (vulture.getPosition().getDistance(targetPosition) < 10) {
							// vulture가 Mine을 심을 장소에 도착
							vultureForMine.replace(vulture, VultureStatus.PlaceSpiderMine.ordinal());
						} else if (vulture.isIdle()) {
							vulture.move(targetPosition);
						}
					} else if (vultureForMine.get(vulture) == VultureStatus.PlaceSpiderMine.ordinal()) {
						if (vulture.isAttacking() == false && vulture.isMoving() == false) {
							if (vulture.getSpiderMineCount() > 0) {
								findTargetPostion(vulture);
								useSpiderMineTech(vulture, targetPosition);
							} else {
								vulture.move(InformationManager.Instance()
										.getSecondChokePoint(MyBotModule.Broodwar.self()).getCenter());
								vultureForMine.replace(vulture, VultureStatus.ComingBacktoMainBaseLocation.ordinal());
							}
						}
					} else if (vultureForMine.get(vulture) == VultureStatus.ComingBacktoMainBaseLocation.ordinal()) {
						if (vulture.getPosition().getDistance(InformationManager.Instance()
								.getSecondChokePoint(MyBotModule.Broodwar.self()).getCenter()) < 50) {
							vultureForMine.replace(vulture, VultureStatus.MissionComplete.ordinal());
						} else if (vulture.isIdle()) {
							vulture.move(InformationManager.Instance().getSecondChokePoint(MyBotModule.Broodwar.self())
									.getCenter());
						}

					} else if (vultureForMine.get(vulture) == VultureStatus.MissionComplete.ordinal()) {
						complete++;
					}

				}

				// if (vultureForMine.size() == complete) {
				// vultureForMine.clear();
				// }
			}
		}
	}

	// 0805 - 최혜진 추가 Vulture가 Sprider Mine을 심을 위치를 찾아주는 메서드
	private void findTargetPostion(Unit vulture) {

		int lowerlimit = -4;
		int upperlimit = 4;

		int plusX = (int) (Math.random() * (upperlimit - lowerlimit + 1)) + lowerlimit;
		int plusY = (int) (Math.random() * (upperlimit - lowerlimit + 1)) + lowerlimit;

		int currentX = targetPosition.toTilePosition().getX();
		int currentY = targetPosition.toTilePosition().getY();
		TilePosition resultPosition = new TilePosition(currentX + plusX, currentY + plusY);
		minePlacementPosition = resultPosition.toPosition();

	}

	// 0804 - 최혜진 추가 원하는 위치에 해당 vulture가 Spider Mine을 심는 메서드
	private void useSpiderMineTech(Unit vulture, Position position) {
		vulture.useTech(TechType.Spider_Mines, minePlacementPosition);
	}
}
