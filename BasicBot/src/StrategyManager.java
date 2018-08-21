import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import bwapi.Player;
import bwapi.Position;
import bwapi.Race;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwapi.WalkPosition;
import bwta.BWTA;
import bwta.BaseLocation;

/// 상황을 판단하여, 정찰, 빌드, 공격, 방어 등을 수행하도록 총괄 지휘를 하는 class <br>
/// InformationManager 에 있는 정보들로부터 상황을 판단하고, <br>
/// BuildManager 의 buildQueue에 빌드 (건물 건설 / 유닛 훈련 / 테크 리서치 / 업그레이드) 명령을 입력합니다.<br>
/// 정찰, 빌드, 공격, 방어 등을 수행하는 코드가 들어가는 class
public class StrategyManager {

	private static StrategyManager instance = new StrategyManager();

	private CommandUtil commandUtil = new CommandUtil();

	private InformationManager informationMgr = InformationManager.Instance();
	// 0723 추가 - CountManager 추가
	private CountManager countMgr = CountManager.Instance();

	private Player self = MyBotModule.Broodwar.self();

	private Player enemy = MyBotModule.Broodwar.enemy();

	BaseLocation targetBaseLocation;

	// 0709 추가 - 내 유닛 리스트
	private List<Unit> myUnits;
	// 0709 추가 - 적 유닛 리스트
	private List<Unit> enemyUnits;
	// 0805 - 최초 생산 or 건설되는 유닛 저장
	private Map<String, Unit> myUnitMap = new HashMap<>();

	private boolean isFullScaleAttackStarted;

	private boolean isInitialBuildOrderFinished;
	// 0813 - 적 앞마당 근처 도착
	private boolean isOverLoaction;
	// 0709 - FrameCount 저장
	private int frameCount;
	// 0814 - 공중 공격 위험도
	private int airAttackLevel;
	// 0811 - 적과의 거리
	private double mainDistance;
	// 0812 - 최혜진 추가 Expansion 순서 지정
	public Map<Integer, BaseLocation> numberOfBaseLocations = new HashMap<>();

	private String[] buildingNames = { "Barracks", "EngineeringBay", "Bunker" };

	// 0813 - 배럭스 이전 프레임 체력
	private int[] lastHitPoints = new int[2];

	private UnitType[] buildingTypes = { UnitType.Terran_Barracks, UnitType.Terran_Engineering_Bay,
			UnitType.Terran_Bunker };

	// 0821 - 최혜진 추가 Refinery 앞마당 위치
	private TilePosition firstChokePointRefinery;
	// BasicBot 1.1 Patch Start ////////////////////////////////////////////////
	// 경기 결과 파일 Save / Load 및 로그파일 Save 예제 추가를 위한 변수 및 메소드 선언

	/// 한 게임에 대한 기록을 저장하는 자료구조
	private class GameRecord {
		String mapName;
		String enemyName;
		String enemyRace;
		String enemyRealRace;
		String myName;
		String myRace;
		int gameFrameCount = 0;
		int myWinCount = 0;
		int myLoseCount = 0;
	}

	/// 과거 전체 게임들의 기록을 저장하는 자료구조
	ArrayList<GameRecord> gameRecordList = new ArrayList<GameRecord>();

	// BasicBot 1.1 Patch End //////////////////////////////////////////////////

	/// static singleton 객체를 리턴합니다
	public static StrategyManager Instance() {
		return instance;
	}

	public StrategyManager() {
		mainDistance = 2500;
		Arrays.fill(lastHitPoints, 1000);
		isFullScaleAttackStarted = false;
		isInitialBuildOrderFinished = false;
	}

	/// 경기가 시작될 때 일회적으로 전략 초기 세팅 관련 로직을 실행합니다
	public void onStart() {

		// BasicBot 1.1 Patch Start
		// ////////////////////////////////////////////////
		// 경기 결과 파일 Save / Load 및 로그파일 Save 예제 추가

		// 과거 게임 기록을 로딩합니다
		loadGameRecordList();

		// BasicBot 1.1 Patch End
		// //////////////////////////////////////////////////

		setInitialBuildOrder();
	}

	/// 경기가 종료될 때 일회적으로 전략 결과 정리 관련 로직을 실행합니다
	public void onEnd(boolean isWinner) {

		// BasicBot 1.1 Patch Start
		// ////////////////////////////////////////////////
		// 경기 결과 파일 Save / Load 및 로그파일 Save 예제 추가

		// 과거 게임 기록 + 이번 게임 기록을 저장합니다
		saveGameRecordList(isWinner);

		// BasicBot 1.1 Patch End
		// //////////////////////////////////////////////////
	}

	/// 경기 진행 중 매 프레임마다 경기 전략 관련 로직을 실행합니다
	public void update() {
		if (BuildManager.Instance().buildQueue.isEmpty()) {
			isInitialBuildOrderFinished = true;
		}
		// 0709 추가
		init();

		executeWorkerTraining();

		executeSupplyManagement();

		executeSeniorityCombatUnitTraining();
		// 0709 추가
		executeAnalyzeBuild();
		// 0628 추가
		executeBuildingManagement();

		executeCombat();
		// 0630 추가
		executeControl();
		// 0801 - 최혜진 추가 주기적 스캔
		executeScan();
		// 0801 - 최혜진 추가 Spider Mine 심기 컨트롤
		excuteSpiderMine();
		// 0816 추가
		executeDefense();
		// 0820 - 최혜진 추가 Multiple Expansion
		executeMultipleExpansion();

		// BasicBot 1.1 Patch Start
		// ////////////////////////////////////////////////
		// 경기 결과 파일 Save / Load 및 로그파일 Save 예제 추가

		// 이번 게임의 로그를 남깁니다
		// saveGameLog();

		// BasicBot 1.1 Patch End
		// //////////////////////////////////////////////////
	}

	// 0709 추가
	private void init() {

		frameCount = MyBotModule.Broodwar.getFrameCount();

		myUnits = self.getUnits();

		enemyUnits = enemy.getUnits();

		countMgr.update();

		updateUnitMap();
	}

	private void updateUnitMap() {
		if (myUnitMap.size() == buildingNames.length) {
			Unit unit = myUnitMap.get("Barracks");
			if (unit != null && !unit.exists()) {
				if (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Barracks) == 0) {
					BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Barracks,
							BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
					myUnitMap.remove("Barracks");
				}
			}
			return;
		}

		for (Unit unit : myUnits) {
			if (!unit.getType().isBuilding()) {
				continue;
			}

			for (int i = 0; i < buildingNames.length; i++) {
				if (myUnitMap.get(buildingNames[i]) == null && unit.getType() == buildingTypes[i]) {
					myUnitMap.put(buildingNames[i], unit);
					break;
				}
			}
		}
	}

	// 일꾼 계속 추가 생산
	public void executeWorkerTraining() {

		if (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Comsat_Station) != 0) {
			return;
		}

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 0628 추가
		// 12초에 한번만 실행
		if (frameCount % (24 * 12) != 0) {
			return;
		}

		if (self.minerals() >= 50) {
			// workerCount = 현재 일꾼 수 + 생산중인 일꾼 수
			int workerCount = self.allUnitCount(UnitType.Terran_SCV);

			for (Unit unit : myUnits) {
				if (unit.getType().isResourceDepot()) {
					if (unit.isTraining()) {
						workerCount += unit.getTrainingQueue().size();
					}
				}
			}

			if (workerCount < 50) {
				for (Unit unit : myUnits) {
					if (unit.getType().isResourceDepot()) {
						unit.train(UnitType.Terran_SCV);
					}
				}
			}
		}
	}

	// Supply DeadLock 예방 및 SupplyProvider 가 부족해질 상황 에 대한 선제적 대응으로서<br>
	// SupplyProvider를 추가 건설/생산한다
	public void executeSupplyManagement() {

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 1초에 한번만 실행
		if (frameCount % 24 != 2) {
			return;
		}

		// 게임에서는 서플라이 값이 200까지 있지만, BWAPI 에서는 서플라이 값이 400까지 있다
		// 저글링 1마리가 게임에서는 서플라이를 0.5 차지하지만, BWAPI 에서는 서플라이를 1 차지한다
		if (self.supplyTotal() < 400) {

			// 서플라이가 다 꽉찼을때 새 서플라이를 지으면 지연이 많이 일어나므로, supplyMargin (게임에서의 서플라이
			// 마진 값의 2배)만큼 부족해지면 새 서플라이를 짓도록 한다
			// 이렇게 값을 정해놓으면, 게임 초반부에는 서플라이를 너무 일찍 짓고, 게임 후반부에는 서플라이를 너무 늦게 짓게 된다
			int supplyMargin = 12 + (countMgr.getFactory() * 6);

			// currentSupplyShortage 를 계산한다
			int currentSupplyShortage = self.supplyUsed() + supplyMargin - self.supplyTotal();

			if (currentSupplyShortage > 0) {

				// 생산/건설 중인 Supply를 센다
				int onBuildingSupplyCount = 0;

				// 저그 종족인 경우, 생산중인 Zerg_Overlord (Zerg_Egg) 를 센다. Hatchery 등 건물은
				// 세지 않는다

				onBuildingSupplyCount += ConstructionManager.Instance()
						.getConstructionQueueItemCount(informationMgr.getBasicSupplyProviderUnitType(), null)
						* informationMgr.getBasicSupplyProviderUnitType().supplyProvided();

				if (currentSupplyShortage > onBuildingSupplyCount) {
					boolean isToEnqueue = true;
					if (!BuildManager.Instance().buildQueue.isEmpty()) {
						BuildOrderItem currentItem = BuildManager.Instance().buildQueue.getHighestPriorityItem();
						if (currentItem.metaType.isUnit() && currentItem.metaType.getUnitType() == InformationManager
								.Instance().getBasicSupplyProviderUnitType()) {
							isToEnqueue = false;
						}
					}
					if (isToEnqueue) {
						BuildManager.Instance().buildQueue.queueAsHighestPriority(
								informationMgr.getBasicSupplyProviderUnitType(),
								BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
					}
				}
			}
		}

		// BasicBot 1.1 Patch End
		// ////////////////////////////////////////////////
	}

	public void executeSeniorityCombatUnitTraining() {

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 1초에 한번만 실행
		if (frameCount % 24 != 3) {
			return;
		}

		// 고급 병력 추가 훈련
		if (self.supplyUsed() < 390) {
			for (Unit unit : myUnits) {
				if (unit.getType() == UnitType.Terran_Factory) {
					if (unit.isCompleted() && !unit.isTraining()) {
						UnitType unitType = selectTrainUnitType(unit);
						if (unitType != null) {
							unit.train(unitType);
						}
					}
				}
			}
		}
	}

	// 0712 수정 - 초반뿐만 아니라 전체적으로 상대 빌드를 분석하는 함수
	private void executeAnalyzeBuild() {

		// 1초에 한번만 실행
		if (frameCount % 24 != 5) {
			return;
		}

		// 0710 수정
		// 0712 수정 - 시간에 따라 상대 빌드 탐색
		if (enemy.getRace() == Race.Protoss) {
			if (countMgr.getBunker() == 0) {
				if (self.completedUnitCount(UnitType.Terran_Barracks) > 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Bunker,
							BuildOrderItem.SeedPositionStrategy.BlockFirstChokePoint, true);
					countMgr.setBunker();
				}
			}
			// 셔틀
			if (enemy.allUnitCount(UnitType.Protoss_Shuttle) != 0) {
				if (countMgr.getArmory() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Armory,
							BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
					countMgr.setArmory();
				} else if (self.completedUnitCount(UnitType.Terran_Armory) > 0) {
					airAttackLevel = 1;
					if (self.getMaxUpgradeLevel(UpgradeType.Charon_Boosters) != MyBotModule.Broodwar.self()
							.getUpgradeLevel(UpgradeType.Charon_Boosters)) {
						if (BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Charon_Boosters) == 0
								&& !self.isUpgrading(UpgradeType.Charon_Boosters)) {
							// 골리앗 사정거리 업그레이드
							BuildManager.Instance().buildQueue.queueAsHighestPriority(UpgradeType.Charon_Boosters,
									true);
						}
					}
				}
			}
			// 다크 템플러
			if (enemy.allUnitCount(UnitType.Protoss_Templar_Archives) != 0
					|| enemy.allUnitCount(UnitType.Protoss_Dark_Templar) != 0) {
				if (countMgr.getAcademy() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Academy,
							BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
					countMgr.setAcademy();
				} else if (self.completedUnitCount(UnitType.Terran_Academy) > 0) {
					if (countMgr.getComsatStation() == 0) {
						for (int i = 0; i < self.completedUnitCount(UnitType.Terran_Command_Center); i++) {
							BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Comsat_Station,
									BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
							countMgr.setComsatStation();
						}
					}
				}
			}
			// 캐리어 생산
			if (enemy.allUnitCount(UnitType.Protoss_Stargate) != 0
					|| enemy.allUnitCount(UnitType.Protoss_Fleet_Beacon) != 0) {
				if (countMgr.getArmory() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Armory,
							BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
					countMgr.setArmory();
				} else if (self.completedUnitCount(UnitType.Terran_Armory) > 0) {
					airAttackLevel = 6;
					if (self.getMaxUpgradeLevel(UpgradeType.Charon_Boosters) != MyBotModule.Broodwar.self()
							.getUpgradeLevel(UpgradeType.Charon_Boosters)) {
						if (BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Charon_Boosters) == 0
								&& !self.isUpgrading(UpgradeType.Charon_Boosters)) {
							// 골리앗 사정거리 업그레이드
							BuildManager.Instance().buildQueue.queueAsHighestPriority(UpgradeType.Charon_Boosters,
									true);
						}
					}
				}
			}
			// 캐리어 발견
			if (enemy.allUnitCount(UnitType.Protoss_Carrier) > 1) {
				isFullScaleAttackStarted = true;
			}
		} else if (enemy.getRace() == Race.Zerg) {
			if (countMgr.getBunker() == 0) {
				if (self.completedUnitCount(UnitType.Terran_Barracks) == 0) {
					// 4드론
					if (enemy.allUnitCount(UnitType.Zerg_Zergling) != 0) {
						// 0723 - 최혜진 수정 4드론 시 BunkerForZerg 전략 적용하여 지정된 위치에
						// Bunker 건설
						BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Bunker,
								BuildOrderItem.SeedPositionStrategy.BunkerForZerg, true);
						countMgr.setBunker();
					}
				} else {
					// 0805 - 최혜진 추가 투혼 맵 1시 방향 Bunker 올바른 위치 건설 위한 변수
					BuildManager.zergNot4Drone = true;
					ConstructionPlaceFinder.zergNot4Drone = true;
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Bunker,
							BuildOrderItem.SeedPositionStrategy.BlockFirstChokePoint, true);
					countMgr.setBunker();
				}
			}
			// 러커
			if (enemy.allUnitCount(UnitType.Zerg_Lurker) != 0 || enemy.allUnitCount(UnitType.Zerg_Lurker_Egg) != 0) {
				if (countMgr.getAcademy() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Academy,
							BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
					countMgr.setAcademy();
				} else if (self.completedUnitCount(UnitType.Terran_Academy) > 0) {
					if (countMgr.getComsatStation() == 0) {
						for (int i = 0; i < self.completedUnitCount(UnitType.Terran_Command_Center); i++) {
							BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Comsat_Station,
									BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
							countMgr.setComsatStation();
						}
					}
				}
			}
			// 뮤탈
			if (enemy.allUnitCount(UnitType.Zerg_Spire) != 0 || enemy.allUnitCount(UnitType.Zerg_Mutalisk) != 0) {
				if (countMgr.getEngineeringBay() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Engineering_Bay,
							BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
					countMgr.setEngineeringBay();
				}
				if (countMgr.getArmory() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Armory,
							BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
					countMgr.setArmory();
				} else if (self.completedUnitCount(UnitType.Terran_Armory) > 0) {
					airAttackLevel = 4;
					if (self.getMaxUpgradeLevel(UpgradeType.Charon_Boosters) != MyBotModule.Broodwar.self()
							.getUpgradeLevel(UpgradeType.Charon_Boosters)) {
						if (BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Charon_Boosters) == 0
								&& !self.isUpgrading(UpgradeType.Charon_Boosters)) {
							// 골리앗 사정거리 업그레이드
							BuildManager.Instance().buildQueue.queueAsHighestPriority(UpgradeType.Charon_Boosters,
									true);
						}
					}
				}
			}
		} else {
			// 0806 추가
			if (countMgr.getBunker() == 0) {
				if (self.completedUnitCount(UnitType.Terran_Barracks) > 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Bunker,
							BuildOrderItem.SeedPositionStrategy.BlockFirstChokePoint, true);
					countMgr.setBunker();
				}
			}
			// 레이스
			if (enemy.allUnitCount(UnitType.Terran_Wraith) > 0) {
				if (countMgr.getEngineeringBay() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Engineering_Bay,
							BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
					countMgr.setEngineeringBay();
				}
				if (countMgr.getArmory() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Armory,
							BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
					countMgr.setArmory();
				} else if (self.completedUnitCount(UnitType.Terran_Armory) > 0) {
					airAttackLevel = 2;
					if (self.getMaxUpgradeLevel(UpgradeType.Charon_Boosters) != MyBotModule.Broodwar.self()
							.getUpgradeLevel(UpgradeType.Charon_Boosters)) {
						if (BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Charon_Boosters) == 0
								&& !self.isUpgrading(UpgradeType.Charon_Boosters)) {
							// 골리앗 사정거리 업그레이드
							BuildManager.Instance().buildQueue.queueAsHighestPriority(UpgradeType.Charon_Boosters,
									true);
						}
					}
				}
			}
			// 배틀 크루저
			if (enemy.allUnitCount(UnitType.Terran_Physics_Lab) > 0
					|| enemy.allUnitCount(UnitType.Terran_Battlecruiser) > 0) {
				if (countMgr.getEngineeringBay() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Engineering_Bay,
							BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
					countMgr.setEngineeringBay();
				}
				if (countMgr.getArmory() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Armory,
							BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
					countMgr.setArmory();
				} else if (self.completedUnitCount(UnitType.Terran_Armory) > 0) {
					airAttackLevel = 6;
					if (self.getMaxUpgradeLevel(UpgradeType.Charon_Boosters) != MyBotModule.Broodwar.self()
							.getUpgradeLevel(UpgradeType.Charon_Boosters)) {
						if (BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Charon_Boosters) == 0
								&& !self.isUpgrading(UpgradeType.Charon_Boosters)) {
							// 골리앗 사정거리 업그레이드
							BuildManager.Instance().buildQueue.queueAsHighestPriority(UpgradeType.Charon_Boosters,
									true);
						}
					}
				}
			}
		}
	}

	// 0702 수정
	private void executeBuildingManagement() {
		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 1초에 한번만 실행
		if (frameCount % 24 != 7) {
			return;
		}

		if (isConstructEngineeringBay(enemy.getRace())) {
			if (countMgr.getEngineeringBay() == 0) {
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Engineering_Bay,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				countMgr.setEngineeringBay();
			} else if (self.completedUnitCount(UnitType.Terran_Engineering_Bay) > 0) {
				// 최소한의 터렛으로 모든 위치를 막을 수 있게 정해진 위치에 터렛 짓기
				// 0729 - 최혜진 테스트
				// 0805 - 최혜진 추가 Turret 최대 갯수 서킷 맵 5개 투혼 맵 6개
				int limit = MyBotModule.Broodwar.mapFileName().contains("Circuit") ? 5 : 6;
				if (countMgr.getTurret() == 0) {
					if (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Missile_Turret, null) == 0) {
						BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Missile_Turret,
								BuildOrderItem.SeedPositionStrategy.TurretAround, true);
						countMgr.setTurret();
					}
				} else if (self.minerals() >= 500 && countMgr.getTurret() < limit) {
					if (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Missile_Turret, null) == 0) {
						BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Missile_Turret,
								BuildOrderItem.SeedPositionStrategy.TurretAround, true);
						countMgr.setTurret();
					}
				}
			}
		}

		// 0715 추가 - 머신샵 추가
		// 0722 추가 - 터렛 및 메카닉 업그레이드 추가
		if (countMgr.getCompletedFactory() > 0) {
			if (countMgr.getMachineShop() < Math.max(2, (int) Math.sqrt(countMgr.getCompletedFactory()))) {
				if (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Machine_Shop, null) == 0) {
					// 0702 - 최혜진 수정 입구로
					// 0730 - 최혜진 수정 Factory 건설 전략 적용
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Machine_Shop,
							BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);
					countMgr.setMachineShop();
				}
			}

			if (countMgr.getAcademy() == 0) {
				BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Academy,
						BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
				countMgr.setAcademy();
			} else if (self.completedUnitCount(UnitType.Terran_Academy) > 0) {
				if (countMgr.getComsatStation() == 0) {
					for (int i = 0; i < self.completedUnitCount(UnitType.Terran_Command_Center); i++) {
						BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Comsat_Station,
								BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
						countMgr.setComsatStation();
					}
				}
			}
		}

		// 0721 수정
		// 0821 - 최혜진 수정 앞마당에 가스 있는지 검사한 후 없으면 건설 (부셔지는 경우도 대비)
		if (countMgr.getMachineShop() > 1) {
			boolean shouldContruct = true;
			for (Unit refinery : myUnits) {
				if (refinery.getType() == UnitType.Terran_Refinery
						&& (refinery.isConstructing() || refinery.isCompleted())
						&& refinery.getTilePosition().getX() == firstChokePointRefinery.getX()
						&& refinery.getTilePosition().getY() == firstChokePointRefinery.getY()) {
					shouldContruct = false;
					break;
				}
			}

			if (shouldContruct == true) {
				if (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Refinery, null) == 0) {
					BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Refinery,
							BuildOrderItem.SeedPositionStrategy.FirstChokePoint, true);
					countMgr.setRefinery();
				}
			}
		}

		// 0716 수정
		if (countMgr.getCompletedMachineShop() > 0) {
			if (!self.hasResearched(TechType.Tank_Siege_Mode)) {
				if (BuildManager.Instance().buildQueue.getItemCount(TechType.Tank_Siege_Mode) == 0
						&& !self.isResearching(TechType.Tank_Siege_Mode)) {
					// 시즈탱크 시즈모드
					BuildManager.Instance().buildQueue.queueAsHighestPriority(TechType.Tank_Siege_Mode, true);
				}
			} else if (!self.hasResearched(TechType.Spider_Mines)) {
				if (BuildManager.Instance().buildQueue.getItemCount(TechType.Spider_Mines) == 0
						&& !self.isResearching(TechType.Spider_Mines)) {
					// 벌처 마인
					BuildManager.Instance().buildQueue.queueAsHighestPriority(TechType.Spider_Mines, true);
				}
			} else if (self.getMaxUpgradeLevel(UpgradeType.Ion_Thrusters) != MyBotModule.Broodwar.self()
					.getUpgradeLevel(UpgradeType.Ion_Thrusters)) {
				if (BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Ion_Thrusters) == 0
						&& !self.isUpgrading(UpgradeType.Ion_Thrusters)) {
					// 벌처 스피드
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UpgradeType.Ion_Thrusters, true);
				}
			}
		}

		if (countMgr.getCompletedMachineShop() > 1) {
			if (countMgr.getArmory() == 0) {
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Armory,
						BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
				countMgr.setArmory();
			} else if (self.completedUnitCount(UnitType.Terran_Armory) > 0) {
				if (self.getUpgradeLevel(UpgradeType.Terran_Vehicle_Weapons) < 3) {
					if (BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Terran_Vehicle_Weapons) == 0
							&& !self.isUpgrading(UpgradeType.Terran_Vehicle_Weapons)) {
						// 메카닉 공격력 업그레이드
						BuildManager.Instance().buildQueue.queueAsHighestPriority(UpgradeType.Terran_Vehicle_Weapons,
								true);
					} else {
						if (countMgr.getStarport() == 0) {
							// 0730 - 최혜진 수정 Other 건설 전략 적용
							BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Starport,
									BuildOrderItem.SeedPositionStrategy.OtherInMainBaseLocation, true);
							countMgr.setStarport();
						} else if (self.completedUnitCount(UnitType.Terran_Starport) > 0) {
							if (countMgr.getArmory() < 2) {
								BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Armory,
										BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
								countMgr.setArmory();
							}
							if (countMgr.getScienceFacility() == 0) {
								// 0730 - 최혜진 수정 Other 건설 전략 적용
								BuildManager.Instance().buildQueue.queueAsLowestPriority(
										UnitType.Terran_Science_Facility,
										BuildOrderItem.SeedPositionStrategy.OtherInMainBaseLocation, true);
								countMgr.setScienceFacility();
							}
						}
					}
				}

				if (self.completedUnitCount(UnitType.Terran_Armory) > 1) {
					if (self.getUpgradeLevel(UpgradeType.Terran_Vehicle_Plating) < 3) {
						if (BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Terran_Vehicle_Plating) == 0
								&& !self.isUpgrading(UpgradeType.Terran_Vehicle_Plating)) {
							// 메카닉 방어력 업그레이드
							BuildManager.Instance().buildQueue
									.queueAsHighestPriority(UpgradeType.Terran_Vehicle_Plating, true);
						}
					}
				}
			}
		}

		// 0820 수정 - 최대 팩토리 개수 9개
		if (countMgr.getFactory() < 9) {
			if (self.minerals() >= 600 && self.gas() >= 150) {
				if (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Factory, null) == 0) {
					// 0702 - 최혜진 수정 입구로
					// 0730 - 최혜진 수정 Factory 건설 전략 적용
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Factory,
							BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);
					countMgr.setFactory();
				}
			}
		}
	}

	private boolean isConstructEngineeringBay(Race race) {
		if (race == Race.Terran) {
			return countMgr.getFactory() > 3;
		} else if (race == Race.Zerg) {
			return countMgr.getFactory() > 1;
		}
		return true;

	}

	public void executeCombat() {

		if (frameCount % 3 != 2) {
			return;
		}

		// 공격 모드가 아닐 때에는 전투유닛들을 아군 진영 길목에 집결시켜서 방어
		if (isFullScaleAttackStarted == false) {
			Unit bunker = myUnitMap.get("Bunker");
			Unit barracks = myUnitMap.get("Barracks");
			Unit engineeringBay = myUnitMap.get("EngineeringBay");
			Position rallyPoint = getRallyPosition().toPosition();
			// 0627 수정 및 추가
			if (enemy.getRace() != Race.Terran && barracks != null) {
				rallyPoint = barracks.isLifted() ? getRallyPosition().toPosition() : barracks.getPosition();
			}

			// 0710 추가 - 긴급상황 시 본진 랠리 포인트
			// 0825 추가 - 첫번째 탱크는 언덕 위에 위치 / 이동 중 공격받을 때 반격 추가
			for (Unit unit : myUnits) {
				if (unit.getType().isWorker() || unit.getType().isBuilding()) {
					continue;
				}
				// 0816 추가
				if (informationMgr.getUnitData(self).unitJobMap.containsKey(unit)) {
					continue;
				}
				if (unit.getType() == UnitType.Terran_Marine && bunker != null) {
					if (bunker.isCompleted()) {
						commandUtil.rightClick(unit, bunker);
					} else {
						commandUtil.attackMove(unit, bunker.getPosition());
					}
				} else if (enemy.getRace() != Race.Terran && barracks != null && !barracks.isLifted()
						&& unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode && unit.isCompleted()) {
					TilePosition targetPosition = getTankPosition();
					commandUtil.attackMove(unit, targetPosition.toPosition());
				} else {
					commandUtil.attackMove(unit, rallyPoint);
				}
			}

			if (barracks != null) {
				if (!barracks.isLifted() && self.completedUnitCount(UnitType.Terran_Siege_Tank_Siege_Mode) > 1) {
					barracks.lift();
				} else {
					barracks.move(getBarracksPosition(informationMgr.getMainBaseLocation(self)).toPosition());
				}
			}

			if (engineeringBay != null) {
				if (!engineeringBay.isLifted()) {
					engineeringBay.lift();
				} else {
					engineeringBay.move(getEngineeringBayPosition().toPosition());
				}
			}

			// 0806 수정
			if (isFullScaleAttackStart(enemy.getRace())) {
				if (informationMgr.enemyPlayer != null && informationMgr.enemyRace != Race.Unknown
						&& informationMgr.getOccupiedBaseLocations(informationMgr.enemyPlayer).size() > 0) {
					isFullScaleAttackStarted = true;
				}
			}

			// 1초에 한번만 실행
			if (frameCount % 24 != 5) {
				return;
			}

			if (self.hasResearched(TechType.Tank_Siege_Mode)) {
				for (Unit unit : myUnits) {
					if (!unit.getType().isWorker() && !unit.getType().isBuilding()) {
						if (!unit.isMoving() && !unit.isAttacking()) {
							if (unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode) {
								unit.useTech(TechType.Tank_Siege_Mode);
							}
						}
					}
				}
			}
		}
		// 공격 모드가 되면, 모든 전투유닛들을 적군 Main BaseLocation 로 공격 가도록 합니다
		// 0726 추가 - 공격모드가 되면, 모든 시즈탱크가 탱크모드로 변경
		else

		{
			// 병력이 일정 수 이하이면 공격 모드 해제
			if (isNotFullScaleAttackStart(enemy.getRace())) {
				isFullScaleAttackStarted = false;
				mainDistance = 2500;
				return;
			}
			if (informationMgr.enemyPlayer != null && informationMgr.enemyRace != Race.Unknown
					&& informationMgr.getOccupiedBaseLocations(informationMgr.enemyPlayer).size() > 0) {
				// 공격 대상 지역 결정

				targetBaseLocation = informationMgr.getMainBaseLocation(enemy);

				Unit barracks = myUnitMap.get("Barracks");
				Unit engineeringBay = myUnitMap.get("EngineeringBay");

				if (barracks != null && barracks.exists()) {
					moveBuilding(barracks);
				}
				if (engineeringBay != null && engineeringBay.exists()) {
					moveBuilding(engineeringBay);
				}

				if (targetBaseLocation != null) {
					double distance = mainDistance;
					boolean changeDistance = true;
					for (Unit unit : myUnits) {
						// 일꾼, 건물은 제외
						if (unit.getType().isWorker() || unit.getType().isBuilding()) {
							continue;
						}
						if (informationMgr.getUnitData(self).unitJobMap.containsKey(unit)) {
							continue;
						}
						// canAttack 유닛은 attackMove Command 로 공격을 보냅니다
						double subDistance = unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode ? distance + 100
								: distance + 300;
						if (unit.getDistance(targetBaseLocation.getPosition()) > subDistance) {
							if (unit.getType() == UnitType.Terran_Siege_Tank_Siege_Mode) {
								if (!informationMgr.isGroundEnemyUnitInSight(unit)) {
									unit.unsiege();
								}
							} else if (unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode) {
								if (informationMgr.isGroundEnemyUnitInSight(unit)) {
									unit.useTech(TechType.Tank_Siege_Mode);
								} else if (frameCount % (24 * 5) == 5) {
									if (!unit.isMoving() && !unit.isAttacking()) {
										unit.stop();
									}
								}
							} else if (frameCount % (24 * 5) == 5) {
								if (!unit.isMoving() && !unit.isAttacking()) {
									unit.stop();
								}
							}
							commandUtil.attackMove(unit, targetBaseLocation.getPosition());
						} else if (unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode) {
							unit.useTech(TechType.Tank_Siege_Mode);
						} else if (frameCount % (24 * 5) == 5) {
							if (changeDistance && unit.getType() == UnitType.Terran_Siege_Tank_Siege_Mode) {
								if (unit.getGroundWeaponCooldown() == 0
										&& !informationMgr.isGroundEnemyUnitInSight(unit)) {
									mainDistance -= 50;
									changeDistance = false;
								}
							}
						} else if (informationMgr.isGroundEnemyUnitInSight(unit)) {
							commandUtil.attackMove(unit, targetBaseLocation.getPosition());
						} else if (!unit.isHoldingPosition()) {
							unit.holdPosition();
						}
					}
				}
			}
		}
	}

	private void moveBuilding(Unit unit) {
		int index = unit.getType() == UnitType.Terran_Barracks ? 0 : 1;
		if (!unit.isLifted()) {
			unit.lift();
		} else if (unit.isUnderAttack() || unit.getHitPoints() < lastHitPoints[index]) {
			unit.move(getBarracksPosition(informationMgr.getMainBaseLocation(self)).toPosition());
		} else if (unit.getDistance(targetBaseLocation.getPosition()) > mainDistance) {
			if (isOverLoaction || unit.getDistance(getBarracksPosition(targetBaseLocation).toPosition()) < 50) {
				unit.move(targetBaseLocation.getPosition());
				isOverLoaction = true;
			} else {
				unit.move(getBarracksPosition(targetBaseLocation).toPosition());
			}
		}
		lastHitPoints[index] = unit.getHitPoints();
	}

	private boolean isFullScaleAttackStart(Race race) {
		if (race == Race.Protoss) {
			return self.supplyUsed() > 360;
		} else if (race == Race.Terran) {
			return self.allUnitCount(UnitType.Terran_Siege_Tank_Siege_Mode) > 5
					&& self.allUnitCount(UnitType.Terran_Vulture) > 5;
		} else {
			return self.supplyUsed() > 300;
		}
	}

	private boolean isNotFullScaleAttackStart(Race race) {
		if (race == Race.Protoss) {
			return self.supplyUsed() < 200;
		} else if (race == Race.Terran) {
			return self.allUnitCount(UnitType.Terran_Siege_Tank_Siege_Mode)
					+ self.allUnitCount(UnitType.Terran_Siege_Tank_Tank_Mode) < 6;
		} else {
			return self.supplyUsed() < 160;
		}
	}

	// 0704 수정
	private void executeControl() {
		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		if (isFullScaleAttackStarted) {
			return;
		}

		// 0705 추가 - 내 유닛을 공격하는 적 유닛이 있으면 반대로 이동
		// 0706 수정 - 내 유닛이 공격이 가능 하면 공격, 불가능하면 반대로 이동
		// 0712 추가 - 해당 위치로 이동할 수 있을 때만 이동
		for (Unit unit : enemyUnits) {
			if (unit.getType().isBuilding() || unit.getType().isWorker() || isGroundRangeUnit(unit.getType())) {
				continue;
			}

			Unit myUnit = unit.getOrderTarget();
			if (myUnit != null) {
				if (myUnit.getGroundWeaponCooldown() != 0) {
					Position enemyPosition = unit.getPosition();
					Position myPosition = myUnit.getPosition();
					// 0706 추가 - 받아온 위치로 이동할 수 있는지 확인하고 만약 이동할 수 없다면 수정하는 로직 필요
					Position position = getOppositePosition(myPosition, enemyPosition);
					if (MyBotModule.Broodwar.isWalkable(toWalkPosition(position))) {
						myUnit.move(position);
					} else {
						myUnit.attack(unit);
					}
				} else {
					myUnit.attack(unit);
				}
			}
		}
	}

	// 0712 추가
	public WalkPosition toWalkPosition(Position position) {
		return new WalkPosition(position.getX() / 8, position.getY() / 8);
	}

	// 0709 추가
	private boolean isGroundRangeUnit(UnitType unitType) {
		if (unitType == UnitType.Protoss_Dragoon || unitType == UnitType.Protoss_Reaver
				|| unitType == UnitType.Terran_Ghost || unitType == UnitType.Terran_Goliath
				|| unitType == UnitType.Terran_Marine || unitType == UnitType.Terran_Siege_Tank_Siege_Mode
				|| unitType == UnitType.Terran_Siege_Tank_Tank_Mode || unitType == UnitType.Terran_Vulture
				|| unitType == UnitType.Zerg_Hydralisk) {
			return true;
		}
		return false;
	}

	// 0705 추가 - 내 위치와 적 위치를 기반으로 반대위치를 리턴
	// 0706 수정
	// 0711 수정
	private Position getOppositePosition(Position myPosition, Position enemyPosition) {
		int weight = 75;
		int x = myPosition.getX();
		int y = myPosition.getY();
		int dx = enemyPosition.getX() - x;
		int dy = enemyPosition.getY() - y;
		if (dx == 0) {
			y = dy > 0 ? y - weight : y + weight;
		} else if (dy == 0) {
			x = dx > 0 ? x - weight : x + weight;
		} else {
			double tangent = Math.abs((double) dy / (double) dx);
			if (tangent < 0.268) {
				x = dx > 0 ? x - (int) (weight * 0.97) : x + (int) (weight * 97);
				y = dy > 0 ? y - (int) (weight * 0.26) : y + (int) (weight * 0.26);
			} else if (tangent < 0.577) {
				x = dx > 0 ? x - (int) (weight * 0.87) : x + (int) (weight * 0.87);
				y = dy > 0 ? y - (int) (weight * 0.5) : y + (int) (weight * 0.5);
			} else if (tangent < 1) {
				x = dx > 0 ? x - (int) (weight * 0.71) : x + (int) (weight * 0.71);
				y = dy > 0 ? y - (int) (weight * 0.71) : y + (int) (weight * 0.71);
			} else if (tangent < 1.732) {
				x = dx > 0 ? x - (int) (weight * 0.5) : x + (int) (weight * 0.5);
				y = dy > 0 ? y - (int) (weight * 0.87) : y + (int) (weight * 0.87);
			} else if (tangent < 3.732) {
				x = dx > 0 ? x - (int) (weight * 0.26) : x + (int) (weight * 0.26);
				y = dy > 0 ? y - (int) (weight * 0.97) : y + (int) (weight * 0.97);
			} else {
				y = dy > 0 ? y - weight : y + weight;
			}
		}
		return new Position(x, y);
	}

	// 0801 - 최혜진 추가 주기적 스캔
	private void executeScan() {

		// 1초에 한번만 실행
		if (frameCount % 24 != 11) {
			return;
		}

		if (self.completedUnitCount(UnitType.Terran_Comsat_Station) == 0) {
			return;
		}

		boolean isUsed = false;

		for (Unit unit : informationMgr.getUnitData(enemy).scanObjList) {
			if (unit == null || !unit.exists()) {
				continue;
			}

			if (!unit.isDetected()) {
				// 주변에 우리 공격유닛이 있을 때만 스캔을 사용할 것
				List<Unit> unitList = MyBotModule.Broodwar.getUnitsInRadius(unit.getPosition(), 8 * Config.TILE_SIZE);
				Unit tempUnit;
				for (int i = 0; i < unitList.size(); i++) {
					tempUnit = unitList.get(i);
					if (tempUnit.getPlayer() == self) {
						if (informationMgr.isCombatUnitType(tempUnit.getType())) {
							isUsed = true;
							executeScanAt(unit.getPosition());
							break;
						}
					}
				}
			}
		}

		if (isUsed) {
			return;
		}

		if (isFullScaleAttackStarted) {
			return;
		}

		// scan 작동 규칙
		// 적진 위치 모를 경우 : 본진을 제외한 baseLocation을 돌아가면서 사용한다
		// 적진 위치 알 경우 : 적의 본진에 사용한다
		for (Unit comsat : informationMgr.getUnitData(self).comsatList) {
			if (comsat == null || !comsat.isCompleted())
				continue;

			if (comsat.getEnergy() >= 100) {
				BaseLocation enemyBase = informationMgr.getMainBaseLocation(enemy);
				BaseLocation selfBase = informationMgr.getMainBaseLocation(self);

				if (enemyBase == null) {
					for (BaseLocation startLocation : BWTA.getStartLocations()) {
						if (selfBase == startLocation)
							continue;
						// if we haven't explored it yet (방문했었던 곳은 다시 가볼 필요 없음)
						if (MyBotModule.Broodwar.isExplored(startLocation.getTilePosition()) == false) {
							comsat.useTech(TechType.Scanner_Sweep, startLocation.getPosition());
							break;
						}
					}
				} else {
					comsat.useTech(TechType.Scanner_Sweep, enemyBase.getPosition());
					break;
				}
			} else {
				break;
			}
		}
	}

	// 0801 - 최혜진 추가 Spider Mine 심기 컨트롤
	private void excuteSpiderMine() {

		// 1초에 한번만 실행
		if (frameCount % 24 != 13) {
			return;
		}

		if (!self.hasResearched(TechType.Spider_Mines)) {
			return;
		}
		// 0804 - 최혜진 수정 Vulture 갯수 세는 로직 수정
		if (self.completedUnitCount(UnitType.Terran_Vulture) >= 3) {
			VultureMineManager.Instance().update();

		}

	}

	private void executeDefense() {
		// 1초에 한번만 실행
		if (frameCount % 24 != 17) {
			return;
		}

		DefenseManager.Instance().update();
	}

	private void executeMultipleExpansion() {

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 1초에 한번만 실행
		if (frameCount % 24 != 19) {
			return;
		}

		if (self.supplyUsed() < 160) {
			return;
		}

		MultipleExpansionManager.Instance().update();

	}

	public void setInitialBuildOrder() {

		// 0812 - 최혜진 추가 Expansion 번호 부여
		setNumberOfBaseLocations();

		if (self.getRace() == Race.Terran) {
			// 5 SCV
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			// 6 SCV
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			// 7 SCV
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			// Supply Depot
			BuildManager.Instance().buildQueue.queueAsLowestPriority(informationMgr.getBasicSupplyProviderUnitType(),
					BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
			// 8 SCV
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			// 9 SCV
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			// Barracks
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Barracks,
					BuildOrderItem.SeedPositionStrategy.BlockFirstChokePoint, true);
			// 10 SCV
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			// 11 SCV
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			// 12 SCV
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			// 13 SCV
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			// 1 Marine
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Marine,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			// 14 SCV
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			// 0709 추가
			if (enemy.getRace() == Race.Zerg) {
				// 2 Marine
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Marine,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 15 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Command Center
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						informationMgr.getBasicResourceDepotBuildingType(),
						BuildOrderItem.SeedPositionStrategy.FirstExpansionLocation, true);
				// Supply Depot - 0704 최혜진 수정
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						informationMgr.getBasicSupplyProviderUnitType(),
						BuildOrderItem.SeedPositionStrategy.BlockFirstChokePoint, true);
				// 16 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Refinery
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Refinery,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 17 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Bunker - 0724 최혜진 테스트용
				// BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Bunker,
				// BuildOrderItem.SeedPositionStrategy.BunkerForZerg, true);
				// 18 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 19 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				// 0730 - 최혜진 수정 Factory 건설 전략 적용
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);
				// 20 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 3 Marine
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Marine,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 21 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 4 Marine
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Marine,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				// 0730 - 최혜진 수정 Factory 건설 전략 적용
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);

			} else if (enemy.getRace() == Race.Protoss) {
				// Command Center
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						informationMgr.getBasicResourceDepotBuildingType(),
						BuildOrderItem.SeedPositionStrategy.FirstExpansionLocation, true);
				// 15 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Supply Depot - 0704 최혜진 수정
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						informationMgr.getBasicSupplyProviderUnitType(),
						BuildOrderItem.SeedPositionStrategy.BlockFirstChokePoint, true);
				// Refinery
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Refinery,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 2 Marine
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Marine,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 16 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 3 Marine
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Marine,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 17 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 4 Marine
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Marine,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				// 0730 - 최혜진 수정 Factory 건설 전략 적용
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);
				// 18 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Engineering Bay
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Engineering_Bay,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 19 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				// 0730 - 최혜진 수정 Factory 건설 전략 적용
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);
				// 20 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			} else {
				// Command Center
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						informationMgr.getBasicResourceDepotBuildingType(),
						BuildOrderItem.SeedPositionStrategy.FirstExpansionLocation, true);
				// 15 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Supply Depot - 0704 최혜진 수정
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						informationMgr.getBasicSupplyProviderUnitType(),
						BuildOrderItem.SeedPositionStrategy.BlockFirstChokePoint, true);
				// Refinery
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Refinery,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 16 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 17 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 18 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 19 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				// 0730 - 최혜진 수정 Factory 건설 전략 적용
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);
				// 20 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 21 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				// 0730 - 최혜진 수정 Factory 건설 전략 적용
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);
			}
		}
	}

	// 0812 - 최혜진 추가 Expansion 위치에 번호 부여
	private void setNumberOfBaseLocations() {
		List<BaseLocation> multipleExpansionList = BWTA.getBaseLocations();
		if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
			firstChokePointRefinery = ConstructionPlaceFinder.Instance()
					.getRefineryPositionNear(informationMgr.getFirstExpansionLocation(self).getTilePosition());
			for (BaseLocation tempBaseLocations : multipleExpansionList) {
				int x = tempBaseLocations.getTilePosition().getX();
				int y = tempBaseLocations.getTilePosition().getY();
				if (x == 7 && y == 9) {
					numberOfBaseLocations.put(0, tempBaseLocations);
				} else if (x == 7 && y == 34) {
					numberOfBaseLocations.put(1, tempBaseLocations);
				} else if (x == 35 && y == 15) {
					numberOfBaseLocations.put(2, tempBaseLocations);
				} else if (x == 62 && y == 5) {
					numberOfBaseLocations.put(3, tempBaseLocations);
				} else if (x == 89 && y == 15) {
					numberOfBaseLocations.put(4, tempBaseLocations);
				} else if (x == 117 && y == 9) {
					numberOfBaseLocations.put(5, tempBaseLocations);
				} else if (x == 117 && y == 34) {
					numberOfBaseLocations.put(6, tempBaseLocations);
				} else if (x == 14 && y == 63) {
					numberOfBaseLocations.put(7, tempBaseLocations);
				} else if (x == 110 && y == 63) {
					numberOfBaseLocations.put(8, tempBaseLocations);
				} else if (x == 7 && y == 92) {
					numberOfBaseLocations.put(9, tempBaseLocations);
				} else if (x == 7 && y == 118) {
					numberOfBaseLocations.put(10, tempBaseLocations);
				} else if (x == 35 && y == 110) {
					numberOfBaseLocations.put(11, tempBaseLocations);
				} else if (x == 62 && y == 119) {
					numberOfBaseLocations.put(12, tempBaseLocations);
				} else if (x == 89 && y == 110) {
					numberOfBaseLocations.put(13, tempBaseLocations);
				} else if (x == 117 && y == 118) {
					numberOfBaseLocations.put(14, tempBaseLocations);
				} else if (x == 117 && y == 92) {
					numberOfBaseLocations.put(15, tempBaseLocations);
				}
			}
		} else {

		}
	}

	private UnitType selectTrainUnitType(Unit unit) {
		if (self.completedUnitCount(UnitType.Terran_Science_Facility) != 0) {
			if (BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Terran_Vehicle_Weapons) != 0
					|| BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Terran_Vehicle_Plating) != 0) {
				return null;
			}
		}
		if (airAttackLevel != 0) {
			if (self.allUnitCount(UnitType.Terran_Goliath) < airAttackLevel * 3) {
				if (self.minerals() > 100 && self.gas() >= 50) {
					return UnitType.Terran_Goliath;
				}
			}
		}
		if (unit.getAddon() != null) {
			if (self.minerals() >= 150 && self.gas() >= 100) {
				return UnitType.Terran_Siege_Tank_Tank_Mode;
			} else if (self.gas() >= 300) {
				return null;
			}
		}
		if (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Machine_Shop) == 0
				&& self.minerals() >= 100) {
			return UnitType.Terran_Vulture;
		}
		return null;
	}

	public void executeScanAt(Position pos) {
		for (Unit comsat : informationMgr.getUnitData(self).comsatList) {
			if (comsat == null || !comsat.isCompleted())
				continue;

			if (comsat.getEnergy() >= 50) {
				comsat.useTech(TechType.Scanner_Sweep, pos);
				break;
			}
		}
	}

	public TilePosition getBarracksPosition(BaseLocation baseLocation) {
		TilePosition targetPosition = TilePosition.None;
		if (informationMgr.getDirectionOfStartLocation(baseLocation) == 11) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(32, 39);
			} else {
				targetPosition = new TilePosition(34, 41);
			}
		} else if (informationMgr.getDirectionOfStartLocation(baseLocation) == 1) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(95, 39);
			} else {
				targetPosition = new TilePosition(88, 28);
			}
		} else if (informationMgr.getDirectionOfStartLocation(baseLocation) == 7) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(33, 88);
			} else {
				targetPosition = new TilePosition(40, 98);
			}
		} else if (informationMgr.getDirectionOfStartLocation(baseLocation) == 5) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(94, 88);
			} else {
				targetPosition = new TilePosition(93, 86);
			}
		}
		return targetPosition;
	}

	public TilePosition getEngineeringBayPosition() {
		TilePosition targetPosition = TilePosition.None;
		if (informationMgr.getDirectionOfStartLocation(self) == 11) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(9, 49);
			} else {
				// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
				// 0728 - 최혜진 수정 투혼 맵 Engineering Bay 드는 위치 수정
				targetPosition = new TilePosition(8, 53);
			}
		} else if (informationMgr.getDirectionOfStartLocation(self) == 1) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(118, 49);
			} else {
				// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
				// 0728 - 최혜진 수정 투혼 맵 Engineering Bay 드는 위치 수정
				targetPosition = new TilePosition(71, 8);
			}
		} else if (informationMgr.getDirectionOfStartLocation(self) == 7) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(9, 77);
			} else {
				// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
				// 0728 - 최혜진 수정 투혼 맵 Engineering Bay 드는 위치 수정
				targetPosition = new TilePosition(54, 121);
			}
		} else if (informationMgr.getDirectionOfStartLocation(self) == 5) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(118, 77);
			} else {
				// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
				// 0728 - 최혜진 수정 투혼 맵 Engineering Bay 드는 위치 수정
				targetPosition = new TilePosition(118, 74);
			}
		}
		return targetPosition;
	}

	public TilePosition getRallyPosition() {
		TilePosition targetPosition = TilePosition.None;
		if (informationMgr.getDirectionOfStartLocation(self) == 11) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(25, 34);
			} else {
				targetPosition = new TilePosition(27, 36);
			}
		} else if (informationMgr.getDirectionOfStartLocation(self) == 1) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(102, 34);
			} else {
				targetPosition = new TilePosition(92, 25);
			}
		} else if (informationMgr.getDirectionOfStartLocation(self) == 7) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(25, 93);
			} else {
				targetPosition = new TilePosition(34, 102);
			}
		} else if (informationMgr.getDirectionOfStartLocation(self) == 5) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(102, 93);
			} else {
				targetPosition = new TilePosition(100, 91);
			}
		}
		return targetPosition;
	}

	private TilePosition getTankPosition() {
		TilePosition targetPosition = TilePosition.None;
		if (informationMgr.getDirectionOfStartLocation(self) == 11) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(17, 25);
			} else {
				targetPosition = new TilePosition(21, 30);
			}
		} else if (informationMgr.getDirectionOfStartLocation(self) == 1) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(109, 25);
			} else {
				targetPosition = new TilePosition(100, 19);
			}
		} else if (informationMgr.getDirectionOfStartLocation(self) == 7) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(18, 102);
			} else {
				targetPosition = new TilePosition(27, 105);
			}
		} else if (informationMgr.getDirectionOfStartLocation(self) == 5) {
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				targetPosition = new TilePosition(109, 102);
			} else {
				targetPosition = new TilePosition(104, 97);
			}
		}
		return targetPosition;
	}

	// BasicBot 1.1 Patch Start ////////////////////////////////////////////////
	// 경기 결과 파일 Save / Load 및 로그파일 Save 예제 추가

	/// 과거 전체 게임 기록을 로딩합니다
	void loadGameRecordList() {

		// 과거의 게임에서 bwapi-data\write 폴더에 기록했던 파일은 대회 서버가 bwapi-data\read 폴더로
		// 옮겨놓습니다
		// 따라서, 파일 로딩은 bwapi-data\read 폴더로부터 하시면 됩니다

		// TODO : 파일명은 각자 봇 명에 맞게 수정하시기 바랍니다
		String gameRecordFileName = "c:\\starcraft\\bwapi-data\\read\\NoNameBot_GameRecord.dat";

		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(gameRecordFileName));

			System.out.println("loadGameRecord from file: " + gameRecordFileName);

			String currentLine;
			StringTokenizer st;
			GameRecord tempGameRecord;
			while ((currentLine = br.readLine()) != null) {

				st = new StringTokenizer(currentLine, " ");
				tempGameRecord = new GameRecord();
				if (st.hasMoreTokens()) {
					tempGameRecord.mapName = st.nextToken();
				}
				if (st.hasMoreTokens()) {
					tempGameRecord.myName = st.nextToken();
				}
				if (st.hasMoreTokens()) {
					tempGameRecord.myRace = st.nextToken();
				}
				if (st.hasMoreTokens()) {
					tempGameRecord.myWinCount = Integer.parseInt(st.nextToken());
				}
				if (st.hasMoreTokens()) {
					tempGameRecord.myLoseCount = Integer.parseInt(st.nextToken());
				}
				if (st.hasMoreTokens()) {
					tempGameRecord.enemyName = st.nextToken();
				}
				if (st.hasMoreTokens()) {
					tempGameRecord.enemyRace = st.nextToken();
				}
				if (st.hasMoreTokens()) {
					tempGameRecord.enemyRealRace = st.nextToken();
				}
				if (st.hasMoreTokens()) {
					tempGameRecord.gameFrameCount = Integer.parseInt(st.nextToken());
				}

				gameRecordList.add(tempGameRecord);
			}
		} catch (FileNotFoundException e) {
			System.out.println("loadGameRecord failed. Could not open file :" + gameRecordFileName);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	/// 과거 전체 게임 기록 + 이번 게임 기록을 저장합니다
	void saveGameRecordList(boolean isWinner) {

		// 이번 게임의 파일 저장은 bwapi-data\write 폴더에 하시면 됩니다.
		// bwapi-data\write 폴더에 저장된 파일은 대회 서버가 다음 경기 때 bwapi-data\read 폴더로
		// 옮겨놓습니다

		// TODO : 파일명은 각자 봇 명에 맞게 수정하시기 바랍니다
		String gameRecordFileName = "c:\\starcraft\\bwapi-data\\write\\NoNameBot_GameRecord.dat";

		System.out.println("saveGameRecord to file: " + gameRecordFileName);

		String mapName = MyBotModule.Broodwar.mapFileName();
		mapName = mapName.replace(' ', '_');
		String enemyName = enemy.getName();
		enemyName = enemyName.replace(' ', '_');
		String myName = self.getName();
		myName = myName.replace(' ', '_');

		/// 이번 게임에 대한 기록
		GameRecord thisGameRecord = new GameRecord();
		thisGameRecord.mapName = mapName;
		thisGameRecord.myName = myName;
		thisGameRecord.myRace = self.getRace().toString();
		thisGameRecord.enemyName = enemyName;
		thisGameRecord.enemyRace = enemy.getRace().toString();
		thisGameRecord.enemyRealRace = informationMgr.enemyRace.toString();
		thisGameRecord.gameFrameCount = MyBotModule.Broodwar.getFrameCount();
		if (isWinner) {
			thisGameRecord.myWinCount = 1;
			thisGameRecord.myLoseCount = 0;
		} else {
			thisGameRecord.myWinCount = 0;
			thisGameRecord.myLoseCount = 1;
		}
		// 이번 게임 기록을 전체 게임 기록에 추가
		gameRecordList.add(thisGameRecord);

		// 전체 게임 기록 write
		StringBuilder ss = new StringBuilder();
		for (GameRecord gameRecord : gameRecordList) {
			ss.append(gameRecord.mapName + " ");
			ss.append(gameRecord.myName + " ");
			ss.append(gameRecord.myRace + " ");
			ss.append(gameRecord.myWinCount + " ");
			ss.append(gameRecord.myLoseCount + " ");
			ss.append(gameRecord.enemyName + " ");
			ss.append(gameRecord.enemyRace + " ");
			ss.append(gameRecord.enemyRealRace + " ");
			ss.append(gameRecord.gameFrameCount + "\n");
		}

		Common.overwriteToFile(gameRecordFileName, ss.toString());
	}

	/// 이번 게임 중간에 상시적으로 로그를 저장합니다
	void saveGameLog() {

		// 100 프레임 (5초) 마다 1번씩 로그를 기록합니다
		// 참가팀 당 용량 제한이 있고, 타임아웃도 있기 때문에 자주 하지 않는 것이 좋습니다
		// 로그는 봇 개발 시 디버깅 용도로 사용하시는 것이 좋습니다
		if (frameCount % 100 != 0) {
			return;
		}

		// TODO : 파일명은 각자 봇 명에 맞게 수정하시기 바랍니다
		String gameLogFileName = "c:\\starcraft\\bwapi-data\\write\\NoNameBot_LastGameLog.dat";

		String mapName = MyBotModule.Broodwar.mapFileName();
		mapName = mapName.replace(' ', '_');
		String enemyName = enemy.getName();
		enemyName = enemyName.replace(' ', '_');
		String myName = self.getName();
		myName = myName.replace(' ', '_');

		StringBuilder ss = new StringBuilder();
		ss.append(mapName + " ");
		ss.append(myName + " ");
		ss.append(self.getRace().toString() + " ");
		ss.append(enemyName + " ");
		ss.append(informationMgr.enemyRace.toString() + " ");
		ss.append(MyBotModule.Broodwar.getFrameCount() + " ");
		ss.append(self.supplyUsed() + " ");
		ss.append(self.supplyTotal() + "\n");

		Common.appendTextToFile(gameLogFileName, ss.toString());
	}

	// BasicBot 1.1 Patch End //////////////////////////////////////////////////

}