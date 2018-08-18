import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import bwapi.Player;
import bwapi.Position;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BaseLocation;
import bwta.Chokepoint;

public class VultureMineManager {

	// 0804 - 최혜진 수정 적군의 다리 앞으로 가는 Vulture의 unit과 각각의 상태를 관리하는 map 구조로 변경
	public Map<Unit, Integer> vultureForMine = new HashMap<Unit, Integer>();

	private static int[] mineXLocationForCircuit = { 36, 90, 36, 90 };
	private static int[] mineYLocationForCircuit = { 41, 41, 85, 85 };
	private static int[] mineXLocationForSpirit = { 36, 83, 44, 89 };
	private static int[] mineYLocationForSpirit = { 44, 32, 95, 82 };

	private static int[] mineXOtherLocationForSpirit = { 49, 77, 36, 49, 77, 90, 36, 49, 77, 90, 49, 77 };
	private static int[] mineYOtherLocationForSpirit = { 45, 45, 55, 55, 55, 55, 77, 77, 77, 77, 83, 83 };

	private static Position targetPosition = Position.None;
	private static Position minePlacementPosition = Position.None;

	public int enemyLocationOfBase;

	public static int doneWithMinePlacement;

	public static int complete;

	private static boolean isExecuted;

	private InformationManager informationMgr = InformationManager.Instance();

	public enum VultureStatus {
		TargetNotAssigned, /// < 벌쳐 유닛의 target 위치 지정이 안된 상태
		MovingToEnemyBridge, /// < 적군의 다리 앞으로 마인을 심으러 가는 상태
		PlaceSpiderMine, /// < 적군 다리에 마인 심는 상태
		ComingBacktoMainBaseLocation, /// < 마인 심은 후 아군 BaseLocation으로 돌아오는 상태
		RunningAwayFromEnemy, /// < 마인 심지 못하여 아군 진영으로 돌아오는 상태
		AttackEnemy, /// < 마인 심으러 이동 중 적군을 공격하는 상태
		MissionComplete /// < 마인 심고 복귀 완료한 상태
	};

	private static VultureMineManager instance = new VultureMineManager();
	private Player self = MyBotModule.Broodwar.self();

	/// static singleton 객체를 리턴합니다
	public static VultureMineManager Instance() {
		return instance;
	}

	public void update() {

		if (isExecuted == false) {
			// VultureUnit 을 지정하고, VultureUnit 의 이동을 컨트롤함.
			assignVultureIfNeeded();
			moveVultureUnit();
		}
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
					if (vultureForMine.size() > 6) {
						break;
					}
					if (unit.isAttacking() == false && unit.isMoving() == false
							&& informationMgr.getUnitData(self).unitJobMap.containsKey(unit) == false) {
						vultureForMine.put(unit, VultureStatus.TargetNotAssigned.ordinal());
						informationMgr.getUnitData(self).unitJobMap.put(unit, UnitData.UnitJob.Mine);
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
			if (enemyBaseLocation == null || enemyFirstExpansion == null || enemySecondChockPoint == null) {
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
				if (vulture != null && vulture.exists()) {
					// 0808 - 최혜진 추가 vulture의 위치가 겹치지 않도록 랜덤 배정
					findFirstTargetPostion(vulture);
					vulture.move(minePlacementPosition);
					vultureForMine.replace(vulture, VultureStatus.MovingToEnemyBridge.ordinal());
				}
			}
		} else {
			if (vultureForMine.size() == 0 || vultureForMine.isEmpty() == true) {
				return;
			}
			for (Unit vulture : vultureForMine.keySet()) {
				// 0806 - 죽은 Vulture에 대한 Nullpointer 에러 핸들링
				if (vulture != null && vulture.exists()) {
					if (vultureForMine.get(vulture) == VultureStatus.TargetNotAssigned.ordinal()) {
						// 0808 - 최혜진 추가 vulture의 위치가 겹치지 않도록 랜덤 배정
						findFirstTargetPostion(vulture);
						vulture.move(targetPosition);
						vultureForMine.replace(vulture, VultureStatus.MovingToEnemyBridge.ordinal());
					} else if (vultureForMine.get(vulture) == VultureStatus.MovingToEnemyBridge.ordinal()) {
						// 0808 - 최혜진 추가 Vulture 공격 받을 시 도망가는 로직
						if (vulture.isUnderAttack()) {
							vulture.move(StrategyManager.Instance().getRallyPosition().toPosition());
							vultureForMine.replace(vulture, VultureStatus.RunningAwayFromEnemy.ordinal());
							continue;
						}
						// 0808 - 최혜진 추가 Vulture와 적군이 일정거리 이상 가까워지기만 해도 도망가는 로직
						Unit closestEnemy = WorkerManager.Instance().getClosestEnemyUnitFromWorker(vulture);
						if (closestEnemy != null && closestEnemy.exists()) {
							if (closestEnemy.getDistance(vulture) < 50) {
								vulture.move(StrategyManager.Instance().getRallyPosition().toPosition());
								vultureForMine.replace(vulture, VultureStatus.RunningAwayFromEnemy.ordinal());
								continue;
							}
						}
						if (vulture.getPosition().getDistance(targetPosition) < 100) {
							// vulture가 Mine을 심을 장소에 도착
							vultureForMine.replace(vulture, VultureStatus.PlaceSpiderMine.ordinal());
						} else if (vulture.isIdle()) {
							// 0808 - 최혜진 추가 vulture의 위치가 겹치지 않도록 랜덤 배정
							findFirstTargetPostion(vulture);
							vulture.move(minePlacementPosition);
						}
					} else if (vultureForMine.get(vulture) == VultureStatus.PlaceSpiderMine.ordinal()) {
						if (vulture.isAttacking() == false && vulture.isMoving() == false) {
							if (vulture.getSpiderMineCount() == 3) {
								findTargetPostion(vulture, targetPosition);
								useSpiderMineTech(vulture, minePlacementPosition);
							} else if (vulture.getSpiderMineCount() == 2) {
								TilePosition tempTilePosition = TilePosition.None;
								int locationOfBase = ConstructionPlaceFinder.locationOfBase;
								if (locationOfBase == 1) {
									if (enemyLocationOfBase == 2) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[1],
												mineYOtherLocationForSpirit[1]);
									} else if (enemyLocationOfBase == 3) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[6],
												mineYOtherLocationForSpirit[6]);
									} else if (enemyLocationOfBase == 4) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[8],
												mineYOtherLocationForSpirit[8]);
									}
								} else if (locationOfBase == 2) {
									if (enemyLocationOfBase == 1) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[0],
												mineYOtherLocationForSpirit[0]);
									} else if (enemyLocationOfBase == 3) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[7],
												mineYOtherLocationForSpirit[7]);
									} else if (enemyLocationOfBase == 4) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[9],
												mineYOtherLocationForSpirit[9]);
									}
								} else if (locationOfBase == 3) {
									if (enemyLocationOfBase == 1) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[2],
												mineYOtherLocationForSpirit[2]);
									} else if (enemyLocationOfBase == 2) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[4],
												mineYOtherLocationForSpirit[4]);
									} else if (enemyLocationOfBase == 4) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[11],
												mineYOtherLocationForSpirit[11]);
									}
								} else if (locationOfBase == 4) {
									if (enemyLocationOfBase == 1) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[3],
												mineYOtherLocationForSpirit[3]);
									} else if (enemyLocationOfBase == 2) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[5],
												mineYOtherLocationForSpirit[5]);
									} else if (enemyLocationOfBase == 3) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[10],
												mineYOtherLocationForSpirit[10]);
									}

								}

								findTargetPostion(vulture, tempTilePosition.toPosition());
								useSpiderMineTech(vulture, minePlacementPosition);
							} else if (vulture.getSpiderMineCount() == 1) {
								TilePosition tempTilePosition = TilePosition.None;
								int locationOfBase = ConstructionPlaceFinder.locationOfBase;
								if (locationOfBase == 1) {
									if (enemyLocationOfBase == 2) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[0],
												mineYOtherLocationForSpirit[0]);
									} else if (enemyLocationOfBase == 3) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[2],
												mineYOtherLocationForSpirit[2]);
									} else if (enemyLocationOfBase == 4) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[3],
												mineYOtherLocationForSpirit[3]);
									}
								} else if (locationOfBase == 2) {
									if (enemyLocationOfBase == 1) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[1],
												mineYOtherLocationForSpirit[1]);
									} else if (enemyLocationOfBase == 3) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[4],
												mineYOtherLocationForSpirit[4]);
									} else if (enemyLocationOfBase == 4) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[5],
												mineYOtherLocationForSpirit[5]);
									}
								} else if (locationOfBase == 3) {
									if (enemyLocationOfBase == 1) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[6],
												mineYOtherLocationForSpirit[6]);
									} else if (enemyLocationOfBase == 2) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[7],
												mineYOtherLocationForSpirit[7]);
									} else if (enemyLocationOfBase == 4) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[10],
												mineYOtherLocationForSpirit[10]);
									}
								} else if (locationOfBase == 4) {
									if (enemyLocationOfBase == 1) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[8],
												mineYOtherLocationForSpirit[8]);
									} else if (enemyLocationOfBase == 2) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[9],
												mineYOtherLocationForSpirit[9]);
									} else if (enemyLocationOfBase == 3) {
										tempTilePosition = new TilePosition(mineXOtherLocationForSpirit[11],
												mineYOtherLocationForSpirit[11]);
									}

								}

								findTargetPostion(vulture, tempTilePosition.toPosition());
								useSpiderMineTech(vulture, minePlacementPosition);
							} else {
								vulture.move(StrategyManager.Instance().getRallyPosition().toPosition());
								vultureForMine.replace(vulture, VultureStatus.ComingBacktoMainBaseLocation.ordinal());
							}
						}
					} else if (vultureForMine.get(vulture) == VultureStatus.ComingBacktoMainBaseLocation.ordinal()
							|| vultureForMine.get(vulture) == VultureStatus.RunningAwayFromEnemy.ordinal()) {
						// 0808 - 최혜진 수정 도착 인식 지점 변경
						if (vulture.getPosition()
								.getDistance(StrategyManager.Instance().getRallyPosition().toPosition()) < 100) {
							vultureForMine.replace(vulture, VultureStatus.MissionComplete.ordinal());
						} else if (vulture.isIdle()) {
							vulture.move(StrategyManager.Instance().getRallyPosition().toPosition());
						}
					} else if (vultureForMine.get(vulture) == VultureStatus.MissionComplete.ordinal()) {
						complete++;
					}

				}

			}
			// 0818 - 최혜진 수정 모두 완료된 후에 VultureForMine Map을 다 삭제한다
			if (vultureForMine.size() == complete) {
				removeFromVultureForMine();
			}
		}
	}

	public void removeFromVultureForMine(Unit unit) {
		vultureForMine.remove(unit);
	}

	private void removeFromVultureForMine() {

		for (Unit unit : vultureForMine.keySet()) {
			informationMgr.getUnitData(self).unitJobMap.remove(unit);
		}
		vultureForMine.clear();
		isExecuted = true;
	}

	// 0805 - 최혜진 추가 Vulture가 Sprider Mine을 심을 위치를 찾아주는 메서드
	private void findFirstTargetPostion(Unit vulture) {

		int lowerlimit = -2;
		int upperlimit = 2;

		int plusX = (int) (Math.random() * (upperlimit - lowerlimit + 1)) + lowerlimit;
		int plusY = (int) (Math.random() * (upperlimit - lowerlimit + 1)) + lowerlimit;

		int currentX = targetPosition.toTilePosition().getX();
		int currentY = targetPosition.toTilePosition().getY();
		TilePosition resultPosition = new TilePosition(currentX + plusX, currentY + plusY);
		minePlacementPosition = resultPosition.toPosition();
		// 0808 - 최혜진 추가 vulture가 갈 수 없는 곳이라면 다시 지정
		if (!MyBotModule.Broodwar.isWalkable(StrategyManager.Instance().toWalkPosition(minePlacementPosition))) {
			findFirstTargetPostion(vulture);
		}

	}

	private void findTargetPostion(Unit vulture, Position newTargetPostion) {

		int lowerlimit = -5;
		int upperlimit = 5;

		int plusX = (int) (Math.random() * (upperlimit - lowerlimit + 1)) + lowerlimit;
		int plusY = (int) (Math.random() * (upperlimit - lowerlimit + 1)) + lowerlimit;

		int currentX = newTargetPostion.toTilePosition().getX();
		int currentY = newTargetPostion.toTilePosition().getY();
		TilePosition resultPosition = new TilePosition(currentX + plusX, currentY + plusY);
		minePlacementPosition = resultPosition.toPosition();
		// 0808 - 최혜진 추가 vulture가 갈 수 없는 곳이라면 다시 지정
		if (!MyBotModule.Broodwar.isWalkable(StrategyManager.Instance().toWalkPosition(minePlacementPosition))) {
			findTargetPostion(vulture, newTargetPostion);
		}

	}

	// 0804 - 최혜진 추가 원하는 위치에 해당 vulture가 Spider Mine을 심는 메서드
	// 0818 - 최혜진 수정 public으로 바꾸고 position 수정
	public void useSpiderMineTech(Unit vulture, Position position) {
		vulture.useTech(TechType.Spider_Mines, position);
	}

}
