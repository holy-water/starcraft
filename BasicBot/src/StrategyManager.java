import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
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
import bwapi.WeaponType;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Chokepoint;

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

	// 0709 추가 - 내 유닛 리스트
	private List<Unit> myUnits;
	// 0709 추가 - 적 유닛 리스트
	private List<Unit> enemyUnits;
	// 0805 - 최초 생산 or 건설되는 유닛 저장
	private Map<String, Unit> myUnitMap = new HashMap<>();

	private boolean isFullScaleAttackStarted;

	private boolean isInitialBuildOrderFinished;
	// 0709 - 최혜진 추가 배럭 Lifting 여부 체크
	private boolean isBarrackLifting;
	// 0728 - 최혜진 추가 Engineering Bay Lifting 여부 체크
	private boolean isEngineeringBayLifting;
	// 0804 - 첫번째 시즈탱크 이동 여부 체크
	private boolean isTankMoving;
	// 0721 - 시즈모드 상황 판단
	private boolean isSiegeMode;
	// 0805 - 공중공격 대비
	private boolean isAirAttack;
	// 0709 - FrameCount 저장
	private int frameCount;
	// 0726 - 시즈모드 시간 저장
	private int siegeModeCount;
	// 0801 - 최혜진 추가 적 본진 및 적 길목 스캔 스위치
	private boolean enemyBaseLocationScanned;

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
		// 0709 추가
		executeAnalyzeBuild();

		executeWorkerTraining();

		executeSupplyManagement();

		executeSeniorityCombatUnitTraining();
		// 0628 추가
		executeBuildingManagement();

		executeCombat();
		// 0630 추가
		executeControl();
		// 0708 - 최혜진 추가 배럭 컨트롤
		executeBarracksControl();
		// 0728 - 최혜진 추가 Engineering Bay 컨트롤
		executeEngineeringBayControl();
		// 0801 - 최혜진 추가 주기적 스캔
		executeScan();
		// 0801 - 최혜진 추가 Spider Mine 심기 컨트롤
		excuteSpiderMine();

		// BasicBot 1.1 Patch Start
		// ////////////////////////////////////////////////
		// 경기 결과 파일 Save / Load 및 로그파일 Save 예제 추가

		// 이번 게임의 로그를 남깁니다
		// saveGameLog();

		// BasicBot 1.1 Patch End
		// //////////////////////////////////////////////////
	}

	// 0702 수정
	private void executeBuildingManagement() {
		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 1초에 한번만 실행
		if (frameCount % 24 != 0) {
			return;
		}

		int count = enemy.getRace() == Race.Terran ? 2 : 0;

		if (countMgr.getFactory() > count) {
			if (countMgr.getEngineeringBay() == 0) {
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Engineering_Bay,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				countMgr.setEngineeringBay();
			} else if (self.completedUnitCount(UnitType.Terran_Engineering_Bay) > 0) {
				// 최소한의 터렛으로 모든 위치를 막을 수 있게 정해진 위치에 터렛 짓기
				// 0729 - 최혜진 테스트
				if (countMgr.getTurret() < 4) {
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
						BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Comsat_Station,
								BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
						countMgr.setComsatStation();
					}
				}
			}
		}

		// 0721 수정
		if (countMgr.getMachineShop() > 1) {
			if (self.completedUnitCount(UnitType.Terran_Command_Center) > countMgr.getRefinery()) {
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

		if (countMgr.getFactory() < self.completedUnitCount(UnitType.Terran_Command_Center) * 4) {
			if (self.minerals() >= 600 && self.gas() >= 200) {
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

	// 0704 수정
	private void executeControl() {
		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
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

		// 1초에 한번만 실행
		if (frameCount % 24 != 0) {
			return;
		}

		if (isFullScaleAttackStarted) {
			if (isSiegeMode) {
				if (siegeModeCount++ > 5) {
					Unit unit = informationMgr
							.getClosestUnitFromEnemyBaseLocation(UnitType.Terran_Siege_Tank_Siege_Mode);
					if (!unit.isAttacking() && !unit.isUnderAttack() && unit.getGroundWeaponCooldown() == 0) {
						isSiegeMode = false;
					}
				}
			} else {
				if (informationMgr.isEnemyUnitInRadius(
						informationMgr.getClosestUnitFromEnemyBaseLocation(UnitType.Terran_Siege_Tank_Tank_Mode))) {
					isSiegeMode = true;
					siegeModeCount = 0;
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
				|| unitType == UnitType.Terran_Siege_Tank_Tank_Mode || unitType == UnitType.Zerg_Hydralisk) {
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

	// 0709 추가
	private void init() {

		frameCount = MyBotModule.Broodwar.getFrameCount();

		myUnits = self.getUnits();

		enemyUnits = enemy.getUnits();

		countMgr.update();

		// 적이 테란인 경우 벙커를 건설하지 않기 때문에 크기 구분
		int size = enemy.getRace() == Race.Terran ? 3 : 4;

		if (myUnitMap.size() == size) {
			return;
		}

		for (Unit unit : myUnits) {
			if (unit.getType().isWorker()) {
				continue;
			}
			if (myUnitMap.get("Barracks") == null && unit.getType() == UnitType.Terran_Barracks) {
				myUnitMap.put("Barracks", unit);
				continue;
			}
			if (myUnitMap.get("EngineeringBay") == null && unit.getType() == UnitType.Terran_Engineering_Bay) {
				myUnitMap.put("EngineeringBay", unit);
				continue;
			}
			if (myUnitMap.get("Bunker") == null && unit.getType() == UnitType.Terran_Bunker) {
				myUnitMap.put("Bunker", unit);
				continue;
			}
			if (myUnitMap.get("Tank") == null && unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode) {
				myUnitMap.put("Tank", unit);
				continue;
			}
		}
	}

	// 0801 - 최혜진 추가 Spider Mine 심기 컨트롤
	private void excuteSpiderMine() {

		if (!self.hasResearched(TechType.Spider_Mines)) {
			return;
		}
		// 0804 - 최혜진 수정 Vulture 갯수 세는 로직 수정
		if (self.completedUnitCount(UnitType.Terran_Vulture) >= 3) {
			VultureMineManager.Instance().update();
		}

	}

	// 0801 - 최혜진 추가 주기적 스캔
	private void executeScan() {

		// 1초에 한번만 실행
		if (frameCount % 24 != 0) {
			return;
		}

		if (countMgr.getComsatStation() == 0) {
			return;
		}

		for (Unit unit : myUnits) {
			if (unit.getType() != UnitType.Terran_Comsat_Station) {
				continue;
			}
			if (unit.getEnergy() >= 100) {
				Position enemyBaseLocation = informationMgr.getMainBaseLocation(enemy).getPosition();
				if (enemyBaseLocationScanned == false) {
					unit.useTech(TechType.Scanner_Sweep, enemyBaseLocation);
					enemyBaseLocationScanned = true;
				} else {
					unit.useTech(TechType.Scanner_Sweep, informationMgr.getSecondChokePoint(enemy).getPoint());
					enemyBaseLocationScanned = false;
				}
			}
		}
	}

	private void executeEngineeringBayControl() {

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		if (isEngineeringBayLifting) {
			return;
		}

		// 1초에 한번만 실행
		if (frameCount % 24 != 0) {
			return;
		}

		Unit unit = myUnitMap.get("EngineeringBay");

		if (unit == null || !unit.isCompleted()) {
			return;
		}

		if (!unit.isLifted()) {
			unit.lift();
		} else {
			TilePosition targetPosition = TilePosition.None;
			if (BuildManager.Instance().getLocationOfBase() == 1) {
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					targetPosition = new TilePosition(9, 49);
				} else {
					// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
					// 0728 - 최혜진 수정 투혼 맵 Engineering Bay 드는 위치 수정
					targetPosition = new TilePosition(8, 53);
				}
			} else if (BuildManager.Instance().getLocationOfBase() == 2) {
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					targetPosition = new TilePosition(118, 49);
				} else {
					// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
					// 0728 - 최혜진 수정 투혼 맵 Engineering Bay 드는 위치 수정
					targetPosition = new TilePosition(71, 8);
				}
			} else if (BuildManager.Instance().getLocationOfBase() == 3) {
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					targetPosition = new TilePosition(9, 77);
				} else {
					// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
					// 0728 - 최혜진 수정 투혼 맵 Engineering Bay 드는 위치 수정
					targetPosition = new TilePosition(54, 121);
				}
			} else if (BuildManager.Instance().getLocationOfBase() == 4) {
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					targetPosition = new TilePosition(118, 77);
				} else {
					// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
					// 0728 - 최혜진 수정 투혼 맵 Engineering Bay 드는 위치 수정
					targetPosition = new TilePosition(118, 74);
				}
			}
			commandUtil.move(unit, targetPosition.toPosition());
			isEngineeringBayLifting = true;
		}
	}

	// 0712 수정 - 초반뿐만 아니라 전체적으로 상대 빌드를 분석하는 함수
	private void executeAnalyzeBuild() {

		// 1초에 한번만 실행
		if (frameCount % 24 != 0) {
			return;
		}

		// 0710 수정
		// 0712 수정 - 시간에 따라 상대 빌드 탐색
		if (enemy.getRace() == Race.Protoss) {
			if (countMgr.getBunker() == 0) {
				// 센터 게이트
				if (frameCount / 24 < 120) {
					if (enemy.allUnitCount(UnitType.Protoss_Gateway) != 0) {
						BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Bunker,
								BuildOrderItem.SeedPositionStrategy.SecondChokePoint, true);
						countMgr.setBunker();
					}
				} else {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Bunker,
							BuildOrderItem.SeedPositionStrategy.SecondChokePoint, true);
					countMgr.setBunker();
				}
			}
			// 초반 빌드를 제외하고 5초에 한번씩 탐색
			if (frameCount % (24 * 5) != 0) {
				return;
			}
			// 다크 템플러
			if (enemy.allUnitCount(UnitType.Protoss_Templar_Archives) != 0
					|| enemy.allUnitCount(UnitType.Protoss_Dark_Templar) != 0) {
				if (countMgr.getAcademy() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Academy,
							BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
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
				if (countMgr.getEngineeringBay() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Engineering_Bay,
							BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
					countMgr.setEngineeringBay();
				}
			}
		} else if (enemy.getRace() == Race.Zerg) {
			if (countMgr.getBunker() == 0) {
				if (frameCount / 24 < 155) {
					// 4드론
					if (enemy.allUnitCount(UnitType.Zerg_Zergling) != 0) {
						// 0723 - 최혜진 수정 4드론 시 BunkerForZerg 전략 적용하여 지정된 위치에
						// Bunker
						// 건설
						BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Bunker,
								BuildOrderItem.SeedPositionStrategy.BunkerForZerg, true);
						countMgr.setBunker();
					}
				} else {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Bunker,
							BuildOrderItem.SeedPositionStrategy.BlockFirstChokePoint, true);
					countMgr.setBunker();
				}
			}
		} else {
			// 레이스
			if (enemy.allUnitCount(UnitType.Terran_Wraith) > 0) {
				if (countMgr.getEngineeringBay() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Engineering_Bay,
							BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
					countMgr.setEngineeringBay();
				}
				if (countMgr.getArmory() == 0) {
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Armory,
							BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
					countMgr.setArmory();
				} else if (self.completedUnitCount(UnitType.Terran_Armory) > 0) {
					isAirAttack = true;
					if (self.getMaxUpgradeLevel(UpgradeType.Charon_Boosters) != MyBotModule.Broodwar.self()
							.getUpgradeLevel(UpgradeType.Charon_Boosters)) {
						if (BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Charon_Boosters) == 0
								&& !self.isUpgrading(UpgradeType.Charon_Boosters)) {
							// 골리앗 사정거리 업그레이드
							BuildManager.Instance().buildQueue.queueAsHighestPriority(UpgradeType.Charon_Boosters, true);
						}

					}
				}
			}
		}
		// 초반 빌드를 제외하고 5초에 한번씩 탐색
		if (frameCount % (24 * 5) != 0) {
			return;
		}
		// 러커
		if (enemy.allUnitCount(UnitType.Zerg_Lurker) != 0 || enemy.allUnitCount(UnitType.Zerg_Lurker_Egg) != 0) {
			if (countMgr.getAcademy() == 0) {
				BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Academy,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
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
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				countMgr.setArmory();
			} else if (self.completedUnitCount(UnitType.Terran_Armory) > 0) {
				isAirAttack = true;
				if (self.getMaxUpgradeLevel(UpgradeType.Charon_Boosters) != MyBotModule.Broodwar.self()
						.getUpgradeLevel(UpgradeType.Charon_Boosters)) {
					if (BuildManager.Instance().buildQueue.getItemCount(UpgradeType.Charon_Boosters) == 0
							&& !self.isUpgrading(UpgradeType.Charon_Boosters)) {
						// 골리앗 사정거리 업그레이드
						BuildManager.Instance().buildQueue.queueAsHighestPriority(UpgradeType.Charon_Boosters, true);
					}
				}
			}
		}
	}

	// 0708 - 최혜진 추가 초반 빌드 완료 후 배럭을 들어올림
	public void executeBarracksControl() {
		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		if (isBarrackLifting) {
			return;
		}

		// 1초에 한번만 실행
		if (frameCount % 24 != 0) {
			return;
		}

		Unit unit = myUnitMap.get("Barracks");

		if (unit == null || !unit.isCompleted()) {
			return;
		}

		if (!unit.isLifted()) {
			unit.lift();
		} else {
			// 0709 - 최혜진 추가 배럭 이동
			TilePosition targetPosition = TilePosition.None;
			// 0714 - 최혜진 수정 배럭스 드는 위치 수정
			// 0722 - 최혜진 수정 배럭스 드는 위치 절대값 지정
			// 0728 - 최혜진 수정 배럭스 드는 위치 수정
			if (BuildManager.Instance().getLocationOfBase() == 1) {
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					targetPosition = new TilePosition(32, 40);
				} else {
					// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
					// 0728 - 최혜진 수정 투혼 맵 배럭스 드는 위치 수정
					targetPosition = new TilePosition(28, 45);
				}
			} else if (BuildManager.Instance().getLocationOfBase() == 2) {
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					targetPosition = new TilePosition(95, 40);
				} else {
					// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
					// 0728 - 최혜진 수정 투혼 맵 배럭스 드는 위치 수정
					targetPosition = new TilePosition(82, 26);
				}
			} else if (BuildManager.Instance().getLocationOfBase() == 3) {
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					targetPosition = new TilePosition(33, 89);
				} else {
					// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
					// 0728 - 최혜진 수정 투혼 맵 배럭스 드는 위치 수정
					targetPosition = new TilePosition(45, 102);
				}
			} else if (BuildManager.Instance().getLocationOfBase() == 4) {
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					targetPosition = new TilePosition(94, 89);
				} else {
					// 0726 - 최혜진 추가 투혼 맵 적용 일단 서킷과 동일하게
					// 0728 - 최혜진 수정 투혼 맵 배럭스 드는 위치 수정
					targetPosition = new TilePosition(98, 83);
				}
			}
			commandUtil.move(unit, targetPosition.toPosition());
			isBarrackLifting = true;
		}
	}

	public void setInitialBuildOrder() {
		if (self.getRace() == Race.Terran) {
			// 0709 추가
			if (enemy.getRace() == Race.Zerg) {
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
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						informationMgr.getBasicSupplyProviderUnitType(),
						BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
				// 8 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 9 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Barracks
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Barracks,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
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
						BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
				// 16 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Refinery
				BuildManager.Instance().buildQueue.queueAsLowestPriority(informationMgr.getRefineryBuildingType(),
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
				// 20 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				// 0730 - 최혜진 수정 Factory 건설 전략 적용
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);
				// 21 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 22 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				// 0730 - 최혜진 수정 Factory 건설 전략 적용
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);

			} else {
				// 0628 수정
				// 5 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 6 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 7 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Supply Depot - 최혜진 수정
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						informationMgr.getBasicSupplyProviderUnitType(),
						BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
				// 8 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 9 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Barracks - 0704 최혜진 수정
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
				// 16 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Refinery
				BuildManager.Instance().buildQueue.queueAsLowestPriority(informationMgr.getRefineryBuildingType(),
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
				// 20 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				// 0730 - 최혜진 수정 Factory 건설 전략 적용
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);
				// 21 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 22 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_SCV,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				// 0730 - 최혜진 수정 Factory 건설 전략 적용
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation, true);
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

		// BasicBot 1.1 Patch Start
		// ////////////////////////////////////////////////
		// 가이드 추가 및 콘솔 출력 명령 주석 처리

		// InitialBuildOrder 진행중 혹은 그후라도 서플라이 건물이 파괴되어 데드락이 발생할 수 있는데, 이 상황에 대한
		// 해결은 참가자께서 해주셔야 합니다.
		// 오버로드가 학살당하거나, 서플라이 건물이 집중 파괴되는 상황에 대해 무조건적으로 서플라이 빌드 추가를 실행하기 보다 먼저
		// 전략적 대책 판단이 필요할 것입니다

		// BWAPI::Broodwar->self()->supplyUsed() >
		// BWAPI::Broodwar->self()->supplyTotal() 인 상황이거나
		// BWAPI::Broodwar->self()->supplyUsed() + 빌드매니저 최상단 훈련 대상 유닛의
		// unit->getType().supplyRequired() >
		// BWAPI::Broodwar->self()->supplyTotal() 인 경우
		// 서플라이 추가를 하지 않으면 더이상 유닛 훈련이 안되기 때문에 deadlock 상황이라고 볼 수도 있습니다.
		// 저그 종족의 경우 일꾼을 건물로 Morph 시킬 수 있기 때문에 고의적으로 이런 상황을 만들기도 하고,
		// 전투에 의해 유닛이 많이 죽을 것으로 예상되는 상황에서는 고의적으로 서플라이 추가를 하지 않을수도 있기 때문에
		// 참가자께서 잘 판단하셔서 개발하시기 바랍니다.

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 1초에 한번만 실행
		if (frameCount % 24 != 0) {
			return;
		}

		// 게임에서는 서플라이 값이 200까지 있지만, BWAPI 에서는 서플라이 값이 400까지 있다
		// 저글링 1마리가 게임에서는 서플라이를 0.5 차지하지만, BWAPI 에서는 서플라이를 1 차지한다
		if (self.supplyTotal() <= 400) {

			// 서플라이가 다 꽉찼을때 새 서플라이를 지으면 지연이 많이 일어나므로, supplyMargin (게임에서의 서플라이
			// 마진 값의 2배)만큼 부족해지면 새 서플라이를 짓도록 한다
			// 이렇게 값을 정해놓으면, 게임 초반부에는 서플라이를 너무 일찍 짓고, 게임 후반부에는 서플라이를 너무 늦게 짓게 된다
			int supplyMargin = 10 + (countMgr.getFactory() * 4);

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
		if (frameCount % 24 != 0) {
			return;
		}

		// 고급 병력 추가 훈련
		if (self.minerals() >= 200 && self.supplyUsed() < 390) {
			for (Unit unit : myUnits) {
				if (unit.getType() == UnitType.Terran_Factory) {
					if (unit.isCompleted() && !unit.isTraining()) {
						if (unit.getAddon() != null && self.gas() >= 100) {
							unit.train(UnitType.Terran_Siege_Tank_Tank_Mode);
						} else if (isAirAttack && self.gas() >= 50) {
							unit.train(UnitType.Terran_Goliath);
						} else {
							unit.train(UnitType.Terran_Vulture);
						}
					}
				}
			}
		}
	}

	public void executeCombat() {

		// 공격 모드가 아닐 때에는 전투유닛들을 아군 진영 길목에 집결시켜서 방어
		if (isFullScaleAttackStarted == false) {

			// 0627 수정 및 추가
			Chokepoint secondChokePoint = informationMgr.getSecondChokePoint(informationMgr.selfPlayer);
			Unit bunker = myUnitMap.get("Bunker");
			Unit tank = myUnitMap.get("Tank");

			// 0710 추가 - 긴급상황 시 본진 랠리 포인트
			// 0825 추가 - 첫번째 탱크는 언덕 위에 위치 / 이동 중 공격받을 때 반격 추가
			for (Unit unit : myUnits) {
				if (unit.getType().isWorker() || unit.getType().isBuilding()) {
					continue;
				}
				if (unit.getType() == UnitType.Terran_Marine && bunker != null) {
					if (bunker.isCompleted()) {
						commandUtil.rightClick(unit, bunker);
					} else {
						commandUtil.attackMove(unit, bunker.getPosition());
					}
				} else {
					commandUtil.attackMove(unit, secondChokePoint.getCenter());
				}
			}

			if (!isTankMoving && tank != null && tank.isCompleted()) {
				TilePosition targetPosition = TilePosition.None;
				if (BuildManager.Instance().getLocationOfBase() == 1) {
					if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
						targetPosition = new TilePosition(17, 25);
					} else {
						targetPosition = new TilePosition(17, 25);
					}
				} else if (BuildManager.Instance().getLocationOfBase() == 2) {
					if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
						targetPosition = new TilePosition(109, 25);
					} else {
						targetPosition = new TilePosition(109, 25);
					}
				} else if (BuildManager.Instance().getLocationOfBase() == 3) {
					if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
						targetPosition = new TilePosition(18, 102);
					} else {
						targetPosition = new TilePosition(18, 102);
					}
				} else if (BuildManager.Instance().getLocationOfBase() == 4) {
					if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
						targetPosition = new TilePosition(109, 102);
					} else {
						targetPosition = new TilePosition(109, 102);
					}
				}
				commandUtil.move(tank, targetPosition.toPosition());
				isTankMoving = true;
			}

			// 0716 수정
			if (self.completedUnitCount(UnitType.Terran_Siege_Tank_Siege_Mode) > 11) {
				if (informationMgr.enemyPlayer != null && informationMgr.enemyRace != Race.Unknown
						&& informationMgr.getOccupiedBaseLocations(informationMgr.enemyPlayer).size() > 0) {
					isFullScaleAttackStarted = true;
				}
			}

			// 5초에 한번만 실행
			if (frameCount % (24 * 5) != 0) {
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
			if (isSiegeMode) {
				for (Unit unit : myUnits) {
					if (unit.getType().isWorker() || unit.getType().isBuilding()) {
						continue;
					}
					if (unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode) {
						unit.useTech(TechType.Tank_Siege_Mode);
					}
				}
			} else {
				for (Unit unit : myUnits) {
					if (unit.getType().isWorker() || unit.getType().isBuilding()) {
						continue;
					}
					if (unit.getType() == UnitType.Terran_Siege_Tank_Siege_Mode) {
						unit.unsiege();
					}
				}
			}
			// std.cout + "enemy OccupiedBaseLocations : " +
			// InformationMgr.getOccupiedBaseLocations(InformationMgr._enemy).size()
			// + std.endl;

			// 5초에 한번만 실행
			if (frameCount % (24 * 1) != 0) {
				return;
			}

			if (informationMgr.enemyPlayer != null && informationMgr.enemyRace != Race.Unknown
					&& informationMgr.getOccupiedBaseLocations(informationMgr.enemyPlayer).size() > 0) {
				// 공격 대상 지역 결정
				BaseLocation targetBaseLocation = null;
				double closestDistance = 100000000;

				for (BaseLocation baseLocation : informationMgr.getOccupiedBaseLocations(informationMgr.enemyPlayer)) {
					double distance = BWTA.getGroundDistance(
							informationMgr.getMainBaseLocation(informationMgr.selfPlayer).getTilePosition(),
							baseLocation.getTilePosition());

					if (distance < closestDistance) {
						closestDistance = distance;
						targetBaseLocation = baseLocation;
					}
				}

				if (targetBaseLocation != null) {
					for (Unit unit : myUnits) {
						// 건물은 제외
						if (unit.getType().isBuilding()) {
							continue;
						}
						// 모든 일꾼은 제외
						if (unit.getType().isWorker()) {
							continue;
						}
						// canAttack 유닛은 attackMove Command 로 공격을 보냅니다
						if (unit.canAttack()) {
							if (unit.isIdle()) {
								commandUtil.attackMove(unit, targetBaseLocation.getPosition());
							}
						}
					}
				}
			}
		}
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