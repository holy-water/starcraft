import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import bwapi.Unit;
import bwapi.UnitType;
import bwta.BaseLocation;
import bwta.Chokepoint;

public class MultipleExpansionManager {

	private CommandUtil commandUtil = new CommandUtil();

	// 이번 베이스
	public BaseLocation thisMulti;

	// 다음 베이스
	public BaseLocation nextMulti;

	// Multiple Expansion 확장 가능 여부 체크
	// 각각의 인덱스는 orderOfBaseLocations의 키
	// true이면 그 자리에 적이 없으며 아직 확장하지 않는 BaseLocation이라는 의미
	// false이면 그 자리에 적이 있거나 이미 확장한 BaseLocation이라는 의미
	private boolean[] isBuildableBase = new boolean[12];

	// 0811 - 최혜진 추가 순서가 아직 정해진지 여부를 판단
	private static boolean isMultipleExpansionOrderDecided;
	private static Map<Integer, BaseLocation> numberOfBaseLocations = new HashMap<>();
	public Map<Integer, BaseLocation> orderOfBaseLocations = new HashMap<>();
	private static int multipleExpansionOrder;

	private static Map<BaseLocation, Unit> scoutSCV = new HashMap<>();
	private static Map<Unit, Integer> statusSCV = new HashMap<>();

	public enum ScoutStatus {
		Assign, MovingToBaseLocation, Arrived

	};

	private static MultipleExpansionManager instance = new MultipleExpansionManager();

	/// static singleton 객체를 리턴합니다
	public static MultipleExpansionManager Instance() {
		return instance;
	}

	// 적 본진을 알게 되었을 때 확장 순서를 결정한다.
	public void initialUpdate() {

		int locationOfBase = ConstructionPlaceFinder.locationOfBase;

		int enemyLocationOfBase = 0;

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

		numberOfBaseLocations = StrategyManager.Instance().numberOfBaseLocations;
		// 본진 위치, 적 본진 위치를 알고, 아직 순서가 정해지지 않은 경우 순서를 정한다
		if (locationOfBase != 0 && enemyLocationOfBase != 0 && isMultipleExpansionOrderDecided == false) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				if (locationOfBase == 1) {
					if (enemyLocationOfBase == 2) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(2));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(7));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(10));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(9));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(11));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(12));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(14));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(13));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(15));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(3));
					} else if (enemyLocationOfBase == 3) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(2));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(3));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(5));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(4));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(6));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(14));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(15));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(13));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(12));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(7));
					} else if (enemyLocationOfBase == 4) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(2));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(3));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(5));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(4));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(6));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(7));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(10));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(9));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(11));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(12));
					}
				} else if (locationOfBase == 2) {
					if (enemyLocationOfBase == 1) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(4));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(14));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(15));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(13));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(12));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(10));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(11));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(9));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(7));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(3));
					} else if (enemyLocationOfBase == 3) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(4));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(3));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(0));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(2));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(1));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(14));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(13));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(15));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(7));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(12));
					} else if (enemyLocationOfBase == 4) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(4));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(3));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(0));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(1));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(2));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(7));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(10));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(9));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(11));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(12));
					}
				} else if (locationOfBase == 3) {
					if (enemyLocationOfBase == 1) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(11));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(12));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(14));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(13));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(15));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(5));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(6));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(4));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(7));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(3));
					} else if (enemyLocationOfBase == 2) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(11));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(7));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(0));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(1));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(2));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(12));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(14));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(13));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(15));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(3));
					} else if (enemyLocationOfBase == 4) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(11));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(7));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(0));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(1));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(2));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(3));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(5));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(4));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(6));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(12));
					}
				} else if (locationOfBase == 4) {
					if (enemyLocationOfBase == 1) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(13));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(12));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(10));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(11));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(9));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(5));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(6));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(4));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(7));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(3));
					} else if (enemyLocationOfBase == 2) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(13));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(12));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(10));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(11));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(9));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(7));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(0));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(1));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(2));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(3));
					} else if (enemyLocationOfBase == 3) {
						orderOfBaseLocations.put(1, numberOfBaseLocations.get(13));
						orderOfBaseLocations.put(2, numberOfBaseLocations.get(8));
						orderOfBaseLocations.put(3, numberOfBaseLocations.get(5));
						orderOfBaseLocations.put(4, numberOfBaseLocations.get(6));
						orderOfBaseLocations.put(5, numberOfBaseLocations.get(4));
						orderOfBaseLocations.put(6, numberOfBaseLocations.get(3));
						orderOfBaseLocations.put(7, numberOfBaseLocations.get(0));
						orderOfBaseLocations.put(8, numberOfBaseLocations.get(2));
						orderOfBaseLocations.put(9, numberOfBaseLocations.get(1));
						orderOfBaseLocations.put(10, numberOfBaseLocations.get(7));
						orderOfBaseLocations.put(11, numberOfBaseLocations.get(12));
					}
				}
			} else { // 투혼 맵

			}
			isMultipleExpansionOrderDecided = true;
			multipleExpansionOrder = 1;
			thisMulti = orderOfBaseLocations.get(1);
			nextMulti = orderOfBaseLocations.get(2);
		}
	}

	// 정찰 보내기 포함
	public void scountBaseLocation(BaseLocation baseLocation) {
		// 정찰 SCV 선정
		Unit SCV = assignScoutIfNeeded(baseLocation);
		// if (SCV != null && SCV.exists() == true && SCV.getHitPoints() > 0) {
		// System.out.println("SCV " + SCV.getID());
		// }
		moveScoutUnit(baseLocation, SCV);
	}

	// 해당 위치 정찰
	private void moveScoutUnit(BaseLocation baseLocation, Unit SCV) {

		if (SCV == null || SCV.exists() == false || SCV.getHitPoints() <= 0) {
			SCV = null;
			return;
		}

		int number = 0;
		for (int i : orderOfBaseLocations.keySet()) {
			if (orderOfBaseLocations.get(i).getTilePosition().getX() == baseLocation.getTilePosition().getX()
					&& orderOfBaseLocations.get(i).getTilePosition().getY() == baseLocation.getTilePosition().getY()) {
				number = i;
				break;
			}
		}
		if (isBuildableBase[number] == true) {
			return;
		}

		BaseLocation currentScoutTargetBaseLocation = baseLocation;

		// if scout is exist, move scout into enemy region
		if (SCV != null) {
			// 아직 가보지 않은 곳이라면 move 명령
			if (statusSCV.get(SCV) == ScoutStatus.Assign.ordinal()) {
				commandUtil.move(SCV, currentScoutTargetBaseLocation.getPosition());
				statusSCV.replace(SCV, ScoutStatus.MovingToBaseLocation.ordinal());
			} else if (statusSCV.get(SCV) == ScoutStatus.MovingToBaseLocation.ordinal()) {
				// System.out.println(SCV.getDistance(currentScoutTargetBaseLocation.getPosition()));
				if (SCV.getDistance(currentScoutTargetBaseLocation.getPosition()) < 100) {
					statusSCV.replace(SCV, ScoutStatus.Arrived.ordinal());
				}
			} else if (statusSCV.get(SCV) == ScoutStatus.Arrived.ordinal()) {

				// 적이 점령하지 않은 경우
				if (MyBotModule.Broodwar.isBuildable(currentScoutTargetBaseLocation.getTilePosition())) {
					// System.out.println("can build here");
					int orderNumber = 0;
					for (int i : orderOfBaseLocations.keySet()) {
						if (orderOfBaseLocations.get(i).getTilePosition().getX() == baseLocation.getTilePosition()
								.getX()
								&& orderOfBaseLocations.get(i).getTilePosition().getY() == baseLocation
										.getTilePosition().getY()) {
							orderNumber = i;
							break;
						}
					}
					isBuildableBase[orderNumber] = true;
				}
				WorkerManager.Instance().setIdleWorker(SCV);
				scoutSCV.remove(baseLocation);
				statusSCV.remove(SCV);
			}
		}
	}

	// Scout 역할의 SCV 선정
	private Unit assignScoutIfNeeded(BaseLocation baseLocation) {

		Unit unit = null;
		if (!scoutSCV.containsKey(baseLocation)) {
			Unit currentScoutUnit = null;

			// 이전 Multi에 있던 SCV를 선정
			BaseLocation firstBuilding = null;
			int orderNumber = 0;
			for (int i : orderOfBaseLocations.keySet()) {
				if (orderOfBaseLocations.get(i).getTilePosition().getX() == baseLocation.getTilePosition().getX()
						&& orderOfBaseLocations.get(i).getTilePosition().getY() == baseLocation.getTilePosition()
								.getY()) {
					orderNumber = i;
					break;
				}
			}

			// 첫번째 multi 인 경우 본진에서 출발
			// 두번째 multi 인 경웨 이전 멀티에 출발
			if (orderNumber == 1) {
				firstBuilding = InformationManager.Instance().getMainBaseLocation(MyBotModule.Broodwar.self());
			} else {
				firstBuilding = orderOfBaseLocations.get(orderNumber - 1);
			}

			if (firstBuilding != null) {
				// grab the closest worker to the first building to send to
				// scout
				unit = WorkerManager.Instance().getClosestMineralWorkerTo(firstBuilding.getPosition());

				// if we find a worker (which we should) add it to the scout
				// units
				// 정찰 나갈 일꾼이 없으면, 아무것도 하지 않는다
				if (unit != null) {
					// set unit as scout unit
					currentScoutUnit = unit;
					WorkerManager.Instance().setScoutWorker(currentScoutUnit);

					scoutSCV.put(baseLocation, currentScoutUnit);
					statusSCV.put(currentScoutUnit, ScoutStatus.Assign.ordinal());
				}
			}
			return unit;
		} else {
			return scoutSCV.get(baseLocation);
		}
	}

	// Command Center를 해당 멀티에 가서 짓는다.
	public void buildCommandCenter(BaseLocation baseLocation) {

		int number = 0;
		for (int i : orderOfBaseLocations.keySet()) {
			if (orderOfBaseLocations.get(i).getTilePosition().getX() == baseLocation.getTilePosition().getX()
					&& orderOfBaseLocations.get(i).getTilePosition().getY() == baseLocation.getTilePosition().getY()) {
				number = i;
				break;
			}
		}
		if (isBuildableBase[number] == true) {
			if (MyBotModule.Broodwar.canBuildHere(baseLocation.getTilePosition(), UnitType.Terran_Command_Center)) {
				if (!ConstructionPlaceFinder.multipleExpansionBuildMap.containsKey(baseLocation)) {
					BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Command_Center,
							BuildOrderItem.SeedPositionStrategy.MultipleExpansion, true);
					ConstructionPlaceFinder.multipleExpansionBuildMap.put(baseLocation, false);
				}
			}
		}

		checkIfCompleted(baseLocation);
	}

	private void checkIfCompleted(BaseLocation baseLocation) {

		boolean isCompleted = false;

		for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
			if (unit.getType() == UnitType.Terran_Command_Center && unit.isCompleted()) {
				if (unit.getDistance(baseLocation.getPosition()) < 50) {
					isCompleted = true;
					ConstructionPlaceFinder.multipleExpansionBuildMap.replace(baseLocation, true);
				}
			}
		}

		if (isCompleted) {

			isBuildableBase[multipleExpansionOrder] = false;
			multipleExpansionOrder++;
			System.out.println(multipleExpansionOrder);
			if (multipleExpansionOrder <= 11) {
				thisMulti = orderOfBaseLocations.get(multipleExpansionOrder);
			} else {
				thisMulti = null;
			}
			if (multipleExpansionOrder + 1 <= 11) {
				nextMulti = orderOfBaseLocations.get(multipleExpansionOrder + 1);
			} else {
				nextMulti = null;
			}
		}
	}

	// Refinary 존재 여부
	public boolean isMineralOnly(BaseLocation baseLocation) {
		return baseLocation.isMineralOnly();
	}

	// Refinery를 해당 멀티에 가서 짓는다.
	public void buildRefinery(BaseLocation baseLocation) {
		BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Refinery,
				BuildOrderItem.SeedPositionStrategy.MultipleExpansion, true);
		ConstructionPlaceFinder.multipleRefineryBuildMap.put(baseLocation, false);
	}

	// 해당 멀티에 Command Center 지을 수 있는지 여부 리턴
	public boolean isCommandCenterBuildable(BaseLocation baseLocation) {
		int number = 0;
		for (int i : orderOfBaseLocations.keySet()) {
			if (orderOfBaseLocations.get(i).getTilePosition().getX() == baseLocation.getTilePosition().getX()
					&& orderOfBaseLocations.get(i).getTilePosition().getY() == baseLocation.getTilePosition().getY()) {
				number = i;
				break;
			}
		}
		return isBuildableBase[number];

	}

}
