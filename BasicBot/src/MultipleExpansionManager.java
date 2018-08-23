import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bwapi.Game;
import bwapi.Player;
import bwapi.Position;
import bwapi.Race;
import bwapi.TechType;
import bwapi.TilePosition;
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

	private static List<Unit> mine = new ArrayList<>();
	/// 각 Worker 에 대한 WorkerJob 상황을 저장하는 자료구조 객체
	public WorkerData workerData = new WorkerData();

	private InformationManager info = InformationManager.Instance();
	private Player self = MyBotModule.Broodwar.self();
	private Player enemy = MyBotModule.Broodwar.enemy();
	private Game mb = MyBotModule.Broodwar;

	public enum ScoutStatus {
		Assign, MovingToBaseLocation, Arrived, CheckIfMineExists, RemoveSpiderMines

	};

	private static MultipleExpansionManager instance = new MultipleExpansionManager();

	/// static singleton 객체를 리턴합니다
	public static MultipleExpansionManager Instance() {
		return instance;
	}

	public void update() {

		initialUpdate();
		checkIfSCVDead();

		if (thisMulti != null) {
			if (mb.mapFileName().contains("Circuit")) {
				if (multipleExpansionOrder <= 11) {
					scoutBaseLocation(thisMulti, multipleExpansionOrder);
				}
				if (multipleExpansionOrder <= 10) {
					scoutBaseLocation(nextMulti, multipleExpansionOrder + 1);
				}
			} else {
				if (multipleExpansionOrder <= 8) {
					scoutBaseLocation(thisMulti, multipleExpansionOrder);
				}
				if (multipleExpansionOrder <= 7) {
					scoutBaseLocation(nextMulti, multipleExpansionOrder + 1);
				}
			}
			if (isCommandCenterBuildable(thisMulti)) {
				buildCommandCenter(thisMulti);
			}
		}
	}

	// 죽은 SCV 정리
	private void checkIfSCVDead() {

		Unit unit;
		if (!scoutSCV.isEmpty()) {

			Set<BaseLocation> keys = scoutSCV.keySet();
			List<BaseLocation> toRemove = new ArrayList<>();
			for (BaseLocation baseLocation : keys) {
				unit = scoutSCV.get(baseLocation);
				if (unit == null || !unit.exists() || unit.getHitPoints() <= 0) {
					toRemove.add(baseLocation);
				}
			}
			keys.removeAll(toRemove);
		}

		if (!statusSCV.isEmpty()) {
			Set<Unit> keys = statusSCV.keySet();
			List<Unit> toRemove = new ArrayList<>();
			for (Unit SCV : keys) {
				if (SCV == null || !SCV.exists() || SCV.getHitPoints() <= 0) {
					toRemove.add(SCV);
				}
			}
			keys.removeAll(toRemove);
		}
	}

	// 적 본진을 알게 되었을 때 확장 순서를 결정한다.
	public void initialUpdate() {

		if (isMultipleExpansionOrderDecided == false) {
			int locationOfBase = ConstructionPlaceFinder.locationOfBase;

			int enemyLocationOfBase = 0;

			BaseLocation enemyBaseLocation = info.getMainBaseLocation(enemy);
			BaseLocation enemyFirstExpansion = info.getFirstExpansionLocation(enemy);
			Chokepoint enemySecondChockPoint = info.getSecondChokePoint(enemy);
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
			if (locationOfBase != 0 && enemyLocationOfBase != 0) {
				if (mb.mapFileName().contains("Circuit")) {
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(4));
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(11));
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(13));
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(2));
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(11));
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(13));
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(2));
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(4));
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(13));
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(2));
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(4));
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
							orderOfBaseLocations.put(12, numberOfBaseLocations.get(11));
						}
					}
				} else { // 투혼 맵
					if (locationOfBase == 1) {
						if (enemyLocationOfBase == 2) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(2));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(6));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(7));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(8));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(9));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(10));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(11));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(5));
						} else if (enemyLocationOfBase == 3) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(2));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(3));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(4));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(5));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(9));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(10));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(11));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(8));
						} else if (enemyLocationOfBase == 4) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(2));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(3));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(4));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(5));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(6));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(7));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(8));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(11));
						}
					} else if (locationOfBase == 2) {
						if (enemyLocationOfBase == 1) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(5));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(9));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(10));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(11));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(6));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(7));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(8));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(2));
						} else if (enemyLocationOfBase == 3) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(5));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(0));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(1));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(2));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(9));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(10));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(11));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(8));
						} else if (enemyLocationOfBase == 4) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(5));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(0));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(1));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(2));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(6));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(7));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(8));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(11));
						}
					} else if (locationOfBase == 3) {
						if (enemyLocationOfBase == 1) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(8));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(9));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(10));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(11));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(3));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(4));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(5));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(2));
						} else if (enemyLocationOfBase == 2) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(8));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(9));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(10));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(11));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(0));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(1));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(2));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(5));
						} else if (enemyLocationOfBase == 4) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(8));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(0));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(1));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(2));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(3));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(4));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(5));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(11));
						}
					} else if (locationOfBase == 4) {
						if (enemyLocationOfBase == 1) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(11));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(6));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(7));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(8));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(3));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(4));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(5));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(2));
						} else if (enemyLocationOfBase == 2) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(11));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(6));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(7));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(8));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(0));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(1));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(2));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(5));
						} else if (enemyLocationOfBase == 3) {
							orderOfBaseLocations.put(1, numberOfBaseLocations.get(11));
							orderOfBaseLocations.put(2, numberOfBaseLocations.get(3));
							orderOfBaseLocations.put(3, numberOfBaseLocations.get(4));
							orderOfBaseLocations.put(4, numberOfBaseLocations.get(5));
							orderOfBaseLocations.put(5, numberOfBaseLocations.get(0));
							orderOfBaseLocations.put(6, numberOfBaseLocations.get(1));
							orderOfBaseLocations.put(7, numberOfBaseLocations.get(2));
							orderOfBaseLocations.put(8, numberOfBaseLocations.get(8));
						}
					}
				}
				isMultipleExpansionOrderDecided = true;
				multipleExpansionOrder = 1;
				thisMulti = orderOfBaseLocations.get(1);
				nextMulti = orderOfBaseLocations.get(2);
			}
		}
	}

	// 정찰 보내기 포함
	public void scoutBaseLocation(BaseLocation baseLocation, int order) {

		// 정찰 SCV 선정
		Unit SCV = assignScoutIfNeeded(baseLocation, order);
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
				if (SCV.getDistance(currentScoutTargetBaseLocation.getPosition()) < 100) {
					if (enemy.getRace() == Race.Terran) {
						boolean isUsed = executeScanAt(baseLocation.getPosition());
						if (isUsed) {
							statusSCV.replace(SCV, ScoutStatus.CheckIfMineExists.ordinal());
						}
					} else {
						statusSCV.replace(SCV, ScoutStatus.Arrived.ordinal());
					}
				}
			} else if (statusSCV.get(SCV) == ScoutStatus.Arrived.ordinal()) {

				boolean isAvailable = true;

				// 적군이 일정거리 이상 가까워지기만 해도 도망가는 로직
				Unit closestEnemy = WorkerManager.Instance().getClosestEnemyUnitFromWorker(SCV);
				if (closestEnemy != null && closestEnemy.exists()) {
					if (closestEnemy.getDistance(SCV) < 200) {
						isAvailable = false;
					}
				}

				if (isAvailable == true) {
					// 적이 점령하지 않은 경우
					if (mb.isBuildable(currentScoutTargetBaseLocation.getTilePosition())) {
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
					} else {
						isAvailable = false;
					}
				}

				if (isAvailable == false) {
					isBuildableBase[multipleExpansionOrder] = false;
					multipleExpansionOrder++;
					if (mb.mapFileName().contains("Circuit")) {
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
					} else {
						if (multipleExpansionOrder <= 8) {
							thisMulti = orderOfBaseLocations.get(multipleExpansionOrder);
						} else {
							thisMulti = null;
						}
						if (multipleExpansionOrder + 1 <= 8) {
							nextMulti = orderOfBaseLocations.get(multipleExpansionOrder + 1);
						} else {
							nextMulti = null;
						}
					}

				}

				WorkerManager.Instance().setIdleWorker(SCV);
				scoutSCV.remove(baseLocation);
				statusSCV.remove(SCV);

			} else if (statusSCV.get(SCV) == ScoutStatus.CheckIfMineExists.ordinal()) {

				boolean mineExists = false;
				List<Unit> enemyList = enemy.getUnits();

				if (!enemyList.isEmpty()) {
					for (int i = 0; i < enemyList.size(); i++) {
						if (enemyList.get(i) == null || !enemyList.get(i).exists()
								|| enemyList.get(i).getHitPoints() <= 0) {
							continue;
						}
						if (enemyList.get(i).getDistance(baseLocation.getPosition()) < 100
								&& enemyList.get(i).getType() == UnitType.Terran_Vulture_Spider_Mine) {
							mine.add(enemyList.get(i));
							mineExists = true;
						}
					}
					if (mineExists == true) {
						statusSCV.replace(SCV, ScoutStatus.RemoveSpiderMines.ordinal());
					} else {
						statusSCV.replace(SCV, ScoutStatus.Arrived.ordinal());
					}
				} else {
					statusSCV.replace(SCV, ScoutStatus.Arrived.ordinal());
				}

			} else if (statusSCV.get(SCV) == ScoutStatus.RemoveSpiderMines.ordinal()) {
				for (int i = 0; i < mine.size(); i++) {
					if (mine.get(i) == null || !mine.get(i).exists() || mine.get(i).getHitPoints() <= 0) {
						mine.remove(i);
					}
				}
				if (mine.size() > 0) {
					SCV.attack(mine.get(0));
				} else {
					statusSCV.replace(SCV, ScoutStatus.Arrived.ordinal());
				}

			}
		}
	}

	// Scout 역할의 SCV 선정
	private Unit assignScoutIfNeeded(BaseLocation baseLocation, int orderNumber) {

		Unit unit = null;
		if (!scoutSCV.containsKey(baseLocation)) {
			Unit currentScoutUnit = null;

			// 이전 Multi에 있던 SCV를 선정
			BaseLocation firstBuilding = null;

			// 첫번째 multi 인 경우 본진에서 출발
			// 두번째 multi 인 경우 이전 멀티에 출발
			if (orderNumber == 1) {
				firstBuilding = info.getFirstExpansionLocation(self);
			} else {
				firstBuilding = orderOfBaseLocations.get(orderNumber - 1);
			}

			if (firstBuilding != null) {
				// grab the closest worker to the first building to send to
				// scout
				unit = WorkerManager.Instance().getWorkerInTargetLocation(firstBuilding);

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
			if (mb.canBuildHere(baseLocation.getTilePosition(), UnitType.Terran_Command_Center)) {
				if (!ConstructionPlaceFinder.multipleExpansionBuildMap.containsKey(baseLocation)) {
					BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Command_Center,
							BuildOrderItem.SeedPositionStrategy.MultipleExpansion, true);
					ConstructionPlaceFinder.multipleExpansionBuildMap.put(baseLocation, false);
				}
				for (ConstructionTask b : ConstructionManager.Instance().getConstructionQueue()) {
					if (b.getStatus() != ConstructionTask.ConstructionStatus.Assigned.ordinal()) {
						continue;
					}
					if (b.getType() != UnitType.Terran_Command_Center) {
						continue;
					}
					if (b.getConstructionWorker() == null || b.getConstructionWorker().exists() == false
							|| b.getConstructionWorker().getHitPoints() <= 0) {
						// 저그 종족 건물 중 Extractor 건물의 경우 일꾼이 exists = false 이지만 isConstructing = true 가
						// 되므로, 일꾼이 죽은 경우가 아니다

						// Unassigned 된 상태로 되돌린다
						WorkerManager.Instance().setIdleWorker(b.getConstructionWorker());

						// free the previous location in reserved
						ConstructionPlaceFinder.Instance().freeTiles(b.getFinalPosition(), b.getType().tileWidth(),
								b.getType().tileHeight());
						b.setConstructionWorker(null);
						b.setBuildCommandGiven(false);
						b.setFinalPosition(TilePosition.None);
						b.setStatus(ConstructionTask.ConstructionStatus.Unassigned.ordinal());

					}
				}
			}
		}

		checkIfCompleted(baseLocation);
	}

	private void checkIfCompleted(BaseLocation baseLocation) {

		boolean isCompleted = false;

		for (Unit unit : self.getUnits()) {
			if (unit.getType() == UnitType.Terran_Command_Center && unit.isCompleted()) {
				if (unit.getDistance(baseLocation.getPosition()) < 50) {
					isCompleted = true;
					ConstructionPlaceFinder.multipleExpansionBuildMap.replace(baseLocation, true);
				}
			}
		}

		if (isCompleted) {

			if (!isMineralOnly(thisMulti)) {
				buildRefinery(thisMulti);
			}

			isBuildableBase[multipleExpansionOrder] = false;
			multipleExpansionOrder++;
			if (mb.mapFileName().contains("Circuit")) {
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
			} else {
				if (multipleExpansionOrder <= 8) {
					thisMulti = orderOfBaseLocations.get(multipleExpansionOrder);
				} else {
					thisMulti = null;
				}
				if (multipleExpansionOrder + 1 <= 8) {
					nextMulti = orderOfBaseLocations.get(multipleExpansionOrder + 1);
				} else {
					nextMulti = null;
				}
			}

		}
	}

	// Refinary 존재 여부
	public boolean isMineralOnly(BaseLocation baseLocation) {
		boolean cannotBuildRefinery = true;
		if (!baseLocation.isMineralOnly() && ConstructionPlaceFinder.multipleExpansionBuildMap != null
				&& !ConstructionPlaceFinder.multipleExpansionBuildMap.isEmpty()
				&& ConstructionPlaceFinder.multipleExpansionBuildMap.containsKey(baseLocation)
				&& ConstructionPlaceFinder.multipleExpansionBuildMap.get(baseLocation) == true) {
			cannotBuildRefinery = false;
		}
		return cannotBuildRefinery;
	}

	// Refinery를 해당 멀티에 가서 짓는다.
	public void buildRefinery(BaseLocation baseLocation) {

		TilePosition refinery = ConstructionPlaceFinder.Instance()
				.getRefineryPositionNear(baseLocation.getTilePosition());
		if (refinery != null && mb.canBuildHere(refinery, UnitType.Terran_Refinery)) {
			if (!ConstructionPlaceFinder.multipleRefineryBuildMap.containsKey(baseLocation)) {
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Refinery,
						BuildOrderItem.SeedPositionStrategy.MultipleExpansion, true);
				ConstructionPlaceFinder.multipleRefineryBuildMap.put(baseLocation, false);
			}
			for (ConstructionTask b : ConstructionManager.Instance().getConstructionQueue()) {
				if (b.getStatus() != ConstructionTask.ConstructionStatus.Assigned.ordinal()) {
					continue;
				}
				if (b.getType() != UnitType.Terran_Refinery) {
					continue;
				}
				if (b.getConstructionWorker() == null || b.getConstructionWorker().exists() == false
						|| b.getConstructionWorker().getHitPoints() <= 0) {
					// 저그 종족 건물 중 Extractor 건물의 경우 일꾼이 exists = false 이지만 isConstructing = true 가
					// 되므로, 일꾼이 죽은 경우가 아니다

					// Unassigned 된 상태로 되돌린다
					WorkerManager.Instance().setIdleWorker(b.getConstructionWorker());

					// free the previous location in reserved
					ConstructionPlaceFinder.Instance().freeTiles(b.getFinalPosition(), b.getType().tileWidth(),
							b.getType().tileHeight());
					b.setConstructionWorker(null);
					b.setBuildCommandGiven(false);
					b.setFinalPosition(TilePosition.None);
					b.setStatus(ConstructionTask.ConstructionStatus.Unassigned.ordinal());

				}
			}
		}
		checkIfRefineryCompleted(baseLocation);
	}

	private void checkIfRefineryCompleted(BaseLocation baseLocation) {

		for (Unit unit : self.getUnits()) {
			if (unit.getType() == UnitType.Terran_Refinery && unit.isCompleted()) {
				if (unit.getDistance(baseLocation.getPosition()) < 200) {
					ConstructionPlaceFinder.multipleRefineryBuildMap.replace(baseLocation, true);
				}
			}
		}

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

	private boolean executeScanAt(Position pos) {
		for (Unit comsat : info.getUnitData(self).comsatList) {
			if (comsat == null || !comsat.isCompleted())
				continue;

			if (comsat.getEnergy() >= 50) {
				comsat.useTech(TechType.Scanner_Sweep, pos);
				return true;
			}
		}
		return false;
	}

}
