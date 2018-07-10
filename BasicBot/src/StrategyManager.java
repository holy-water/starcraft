import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import bwapi.Position;
import bwapi.Race;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
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

	// 0709 추가 - 내 유닛 리스트
	private List<Unit> MyUnits;
	// 0709 추가 - 적 유닛 리스트
	private List<Unit> EnemyUnits;

	private boolean isFullScaleAttackStarted;
	private boolean isInitialBuildOrderFinished;
	// 0709 - 최혜진 추가 배럭 Lifting 여부 체크
	private boolean BarrackLifting;
	// 0709 - 4드론 등 초반 위험 상황 체크
	private boolean isEmergency;
	// 0709 - FrameCount 저장
	private int FrameCount;

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

	// 0702 수정
	private void executeFactoryManagement() {
		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 1초에 한번만 실행
		if (FrameCount % 24 != 0) {
			return;
		}

		// 0702 추가
		int factoryCount = InformationManager.Instance().getNumUnits(UnitType.Terran_Factory,
				MyBotModule.Broodwar.self());

		if (MyBotModule.Broodwar.self().minerals() / factoryCount >= 300) {
			if (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Factory, null) == 0) {
				// 0702 - 최혜진 수정 입구로
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FirstChokePoint, true);
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
		// 0709 추가 - 컨트롤이 무의미한 경우 컨트롤 하지 않음
		for (Unit unit : EnemyUnits) {
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
					myUnit.move(position);
				} else {
					myUnit.attack(unit);
				}
			}
		}
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
	private Position getOppositePosition(Position myPosition, Position enemyPosition) {
		int x = myPosition.getX();
		int y = myPosition.getY();
		int dx = enemyPosition.getX() - x;
		int dy = enemyPosition.getY() - y;
		if (dx == 0) {
			y = dy > 0 ? y - 100 : y + 100;
		} else if (dy == 0) {
			x = dx > 0 ? x - 100 : x + 100;
		} else {
			double tangent = Math.abs((double) dy / (double) dx);
			if (tangent < 0.268) {
				x = dx > 0 ? x - 97 : x + 97;
				y = dy > 0 ? y - 26 : y + 26;
			} else if (tangent < 0.577) {
				x = dx > 0 ? x - 87 : x + 87;
				y = dy > 0 ? y - 50 : y + 50;
			} else if (tangent < 1) {
				x = dx > 0 ? x - 71 : x + 71;
				y = dy > 0 ? y - 71 : y + 71;
			} else if (tangent < 1.732) {
				x = dx > 0 ? x - 50 : x + 50;
				y = dy > 0 ? y - 87 : y + 87;
			} else if (tangent < 3.732) {
				x = dx > 0 ? x - 26 : x + 26;
				y = dy > 0 ? y - 97 : y + 97;
			} else {
				y = dy > 0 ? y - 100 : y + 100;
			}
		}
		return new Position(x, y);
	}

	// 0709 추가
	private void init() {

		FrameCount = MyBotModule.Broodwar.getFrameCount();

		MyUnits = MyBotModule.Broodwar.self().getUnits();

		EnemyUnits = MyBotModule.Broodwar.enemy().getUnits();

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

		// 0627 수정 및 추가
		executeBasicCombatUnitTraining();

		executeSeniorityCombatUnitTraining();

		// 0628 추가
		executeFactoryManagement();

		executeCombat();

		// 0630 추가
		executeControl();

		// 0708 - 최혜진 추가 배럭 컨트롤
		executeBarrackControl();

		// BasicBot 1.1 Patch Start
		// ////////////////////////////////////////////////
		// 경기 결과 파일 Save / Load 및 로그파일 Save 예제 추가

		// 이번 게임의 로그를 남깁니다
		saveGameLog();

		// BasicBot 1.1 Patch End
		// //////////////////////////////////////////////////
	}

	private void executeAnalyzeBuild() {

		// 1초에 한번만 180초 실행
		if (FrameCount % 24 != 0 || FrameCount / 24 < 180) {
			return;
		}

		if (isEmergency) {
			return;
		}

		// 0710 수정
		if (MyBotModule.Broodwar.enemy().getRace() == Race.Protoss) {
			if (InformationManager.Instance().getNumUnits(UnitType.Protoss_Gateway,
					MyBotModule.Broodwar.enemy()) != 0) {
				if (FrameCount / 24 < 120) {
					// 센터 게이트
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Bunker,
							BuildOrderItem.SeedPositionStrategy.SecondChokePoint, true);
				}
			}
		} else if (MyBotModule.Broodwar.enemy().getRace() == Race.Zerg) {
			if (InformationManager.Instance().getNumUnits(UnitType.Zerg_Zergling, MyBotModule.Broodwar.enemy()) != 0) {
				if (FrameCount / 24 < 135) {
					// 4드론
					isEmergency = true;
					BuildManager.Instance().buildQueue.clearAll();
					BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Bunker,
							BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				} else {
					// 9드론
					BuildManager.Instance().buildQueue.queueAsHighestPriority(UnitType.Terran_Bunker,
							BuildOrderItem.SeedPositionStrategy.SecondChokePoint, true);
				}
			}
		}

	}

	// 0708 - 최혜진 추가 초반 빌드 완료 후 배럭을 들어올림
	public void executeBarrackControl() {
		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		if (BarrackLifting == true) {
			return;
		}

		// 1초에 한번만 실행
		if (FrameCount % 24 != 0) {
			return;
		}

		for (Unit unit : MyUnits) {
			if (unit.getType() != UnitType.Terran_Barracks) {
				continue;
			}
			if (!unit.isLifted()) {
				unit.lift();
			} else {
				// 0709 - 최혜진 추가 배럭 이동
				TilePosition initialPosition = unit.getTilePosition();
				TilePosition targetPosition = TilePosition.None;
				if (BuildManager.Instance().locationOfBase <= 2) {
					if (MyBotModule.Broodwar.mapFileName().equals("(4)CircuitBreaker.scx")) {
						targetPosition = new TilePosition(initialPosition.getX(), initialPosition.getY() + 15);
					} else {

					}
					commandUtil.move(unit, targetPosition.toPosition());
					// System.out.println("move to " + targetPosition.getX() +
					// "," +
					// targetPosition.getY());
				} else {
					if (MyBotModule.Broodwar.mapFileName().equals("(4)CircuitBreaker.scx")) {
						targetPosition = new TilePosition(initialPosition.getX(), initialPosition.getY() - 15);
					} else {

					}
					commandUtil.move(unit, targetPosition.toPosition());
					// System.out.println("move to " + targetPosition.getX() +
					// "," +
					// targetPosition.getY());
				}
				BarrackLifting = true;
			}
		}
	}

	public void setInitialBuildOrder() {
		if (MyBotModule.Broodwar.self().getRace() == Race.Protoss) {
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(
					InformationManager.Instance().getBasicSupplyProviderUnitType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Gateway,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Zealot,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);

			/*
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Assimilator,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Forge,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Photon_Cannon,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Gateway,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Zealot);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Cybernetics_Core,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Dragoon); // 드라군 사정거리 업그레이드
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Singularity_Charge);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Citadel_of_Adun); // 질럿 속도 업그레이드
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Leg_Enhancements);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Shield_Battery);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Templar_Archives); // 하이템플러
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_High_Templar);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_High_Templar);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Psionic_Storm);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Hallucination);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Khaydarin_Amulet);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Archon);
			 * 
			 * // 다크아칸
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Dark_Templar);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Dark_Templar);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Maelstrom);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Mind_Control);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Argus_Talisman);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Dark_Archon);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Robotics_Facility);
			 * 
			 * // 셔틀
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Shuttle);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Robotics_Support_Bay);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Gravitic_Drive);
			 * 
			 * // 리버
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Reaver);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Scarab_Damage);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Reaver_Capacity);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Scarab);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Observatory); // 옵저버
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Observer);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Gravitic_Boosters);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Sensor_Array);
			 * 
			 * // 공중유닛
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Stargate);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Fleet_Beacon);
			 * 
			 * // 스카우트
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Scout);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Apial_Sensors);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Gravitic_Thrusters);
			 * 
			 * // 커세어
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Corsair);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Disruption_Web);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Argus_Jewel);
			 * 
			 * // 캐리어
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Carrier);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Carrier_Capacity);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Interceptor);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Interceptor);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Interceptor);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Interceptor);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Interceptor);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Interceptor);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Interceptor);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Interceptor);
			 * 
			 * // 아비터
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Arbiter_Tribunal);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Protoss_Arbiter);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Recall);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Stasis_Field);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Khaydarin_Core);
			 * 
			 * // 포지 - 지상 유닛 업그레이드
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Protoss_Ground_Weapons);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Protoss_Plasma_Shields);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Protoss_Ground_Armor);
			 * 
			 * // 사이버네틱스코어 - 공중 유닛 업그레이드
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Protoss_Air_Weapons);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Protoss_Air_Armor);
			 * 
			 */
		} else if (MyBotModule.Broodwar.self().getRace() == Race.Terran) {

			// 0709 추가
			if (MyBotModule.Broodwar.enemy().getRace() == Race.Zerg) {
				// 5 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 6 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 7 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 8 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Supply Depot
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						InformationManager.Instance().getBasicSupplyProviderUnitType(),
						BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
				// 9 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 10 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Barracks
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Barracks,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 11 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 12 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 13 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 14 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);

			} else {
				// 0628 수정
				// 5 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 6 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 7 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 8 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Supply Depot - 최혜진 수정
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						InformationManager.Instance().getBasicSupplyProviderUnitType(),
						BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
				// 9 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 10 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Barracks - 0704 최혜진 수정
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Barracks,
						BuildOrderItem.SeedPositionStrategy.BlockFirstChokePoint, true);
				// 11 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 12 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 13 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 14 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Command Center
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						InformationManager.Instance().getBasicResourceDepotBuildingType(),
						BuildOrderItem.SeedPositionStrategy.FirstExpansionLocation, true);
				// 1 Marine
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Marine,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 15 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Supply Depot - 0704 최혜진 수정
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						InformationManager.Instance().getBasicSupplyProviderUnitType(),
						BuildOrderItem.SeedPositionStrategy.BlockFirstChokePoint, true);
				// 16 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Refinery
				BuildManager.Instance().buildQueue.queueAsLowestPriority(
						InformationManager.Instance().getRefineryBuildingType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 17 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Bunker
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Bunker,
						BuildOrderItem.SeedPositionStrategy.SecondChokePoint, true);
				// 2 Marine
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Marine,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 18 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 19 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 20 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FirstChokePoint, true);
				// 21 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// 22 SCV
				BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				// Factory - 0702 최혜진 수정 입구로
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory,
						BuildOrderItem.SeedPositionStrategy.FirstChokePoint, true);
			}

			/*
			 * // 가스 리파이너리
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getRefineryBuildingType());
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Barracks,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Bunker,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Marine);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Academy);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Comsat_Station);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Medic);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Firebat);
			 * 
			 * // 지상유닛 업그레이드
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Engineering_Bay);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Terran_Infantry_Weapons, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Terran_Infantry_Armor, false);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Missile_Turret);
			 * 
			 * // 마린 스팀팩
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Stim_Packs, false); // 마린 사정거리 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.U_238_Shells, false);
			 * 
			 * // 메딕
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Optical_Flare, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Restoration, false); // 메딕 에너지 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Caduceus_Reactor, false);
			 * 
			 * // 팩토리
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Factory);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Machine_Shop); // 벌쳐 스파이더 마인
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Spider_Mines, false); // 벌쳐 이동속도 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Ion_Thrusters, false); // 시즈탱크 시즈모드
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Tank_Siege_Mode, false);
			 * 
			 * // 벌쳐
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Vulture);
			 * 
			 * // 시즈탱크
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Siege_Tank_Tank_Mode);
			 * 
			 * // 아머니
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Armory); // 지상 메카닉 유닛 업그레이드
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Terran_Vehicle_Plating, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Terran_Vehicle_Weapons, false); // 공중 유닛 업그레이드
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Terran_Ship_Plating, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Terran_Ship_Weapons, false); // 골리앗 사정거리 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Charon_Boosters, false);
			 * 
			 * // 골리앗
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Goliath);
			 * 
			 * // 스타포트
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Starport);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Control_Tower); // 레이쓰 클러킹
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Cloaking_Field, false); // 레이쓰 에너지 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Apollo_Reactor, false);
			 * 
			 * // 레이쓰
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Wraith);
			 * 
			 * // 발키리
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Valkyrie);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Command_Center);
			 * 
			 * // 사이언스 퍼실리티
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Science_Facility); // 사이언스 베슬 - 기술
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Irradiate, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .EMP_Shockwave, false); // 사이언스 베슬 에너지 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Titan_Reactor, false);
			 * 
			 * // 사이언스 베슬
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Science_Vessel); // 사이언스 퍼실리티 - 배틀크루저 생산 가능
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Physics_Lab);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Yamato_Gun, false); // 배틀크루저 에너지 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Colossus_Reactor, false); // 배틀크루저
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Battlecruiser);
			 * 
			 * // 사이언스 퍼실리티 - 고스트 생산 가능
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Science_Facility);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Covert_Ops);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Lockdown, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Personnel_Cloaking, false); // 고스트 가시거리 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Ocular_Implants, false); // 고스트 에너지 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Moebius_Reactor, false);
			 * 
			 * // 고스트
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Ghost);
			 * 
			 * // 핵폭탄
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Command_Center);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Nuclear_Silo);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Terran_Nuclear_Missile);
			 */
		} else if (MyBotModule.Broodwar.self().getRace() == Race.Zerg) {
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Spawning_Pool,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Zergling,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Zergling,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Zergling,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(
					InformationManager.Instance().getBasicSupplyProviderUnitType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);

			/*
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Hatchery,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType(),
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Hatchery,
			 * BuildOrderItem.SeedPositionStrategy.FirstExpansionLocation);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType(),
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Hatchery,
			 * BuildOrderItem.SeedPositionStrategy.FirstExpansionLocation);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType(),
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getBasicSupplyProviderUnitType(),
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType(),
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType(),
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType(),
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType(),
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType(),
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType(),
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			 * 
			 * // 가스 익스트랙터
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Extractor,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * 
			 * // 성큰 콜로니
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Creep_Colony,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Sunken_Colony,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * 
			 * BuildManager.Instance().buildQueue
			 * .queueAsLowestPriority(InformationManager.Instance().
			 * getRefineryBuildingType());
			 * 
			 * // 저글링 이동속도 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Metabolic_Boost);
			 * 
			 * // 에볼루션 챔버
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Evolution_Chamber); // 에볼루션 챔버 . 지상유닛 업그레이드
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Zerg_Melee_Attacks, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Zerg_Missile_Attacks, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Zerg_Carapace, false);
			 * 
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType());
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType());
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType());
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType());
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType());
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType());
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType());
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType());
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * InformationManager.Instance().getWorkerType());
			 * 
			 * // 스포어 코로니
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Creep_Colony,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Spore_Colony,
			 * BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			 * 
			 * // 히드라
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Hydralisk_Den);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Hydralisk);
			 * 
			 * // 레어
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Lair);
			 * 
			 * // 오버로드 운반가능
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Ventral_Sacs); // 오버로드 시야 증가
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Antennae); // 오버로드 속도 증가
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Pneumatized_Carapace);
			 * 
			 * // 히드라 이동속도 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Muscular_Augments, false); // 히드라 공격 사정거리 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Grooved_Spines, false);
			 * 
			 * // 럴커
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Lurker_Aspect);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Hydralisk);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Lurker);
			 * 
			 * // 스파이어
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Spire, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Mutalisk, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Scourge, true);
			 * 
			 * // 스파이어 . 공중유닛 업그레이드
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Zerg_Flyer_Attacks, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Zerg_Flyer_Carapace, false);
			 * 
			 * // 퀸
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Queens_Nest);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Queen);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Spawn_Broodlings, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Ensnare, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Gamete_Meiosis, false);
			 * 
			 * // 하이브
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Hive); // 저글링 공격 속도 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Adrenal_Glands, false);
			 * 
			 * // 스파이어 . 그레이트 스파이어
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Greater_Spire, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Mutalisk, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Guardian, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Mutalisk, true);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Devourer, true);
			 * 
			 * // 울트라리스크
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Ultralisk_Cavern);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Ultralisk); // 울트라리스크 이동속도 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Anabolic_Synthesis, false); // 울트라리스크 방어력 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Chitinous_Plating, false);
			 * 
			 * // 디파일러
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Defiler_Mound);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Defiler);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Consume, false);
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType
			 * .Plague, false); // 디파일러 에너지 업
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(
			 * UpgradeType.Metasynaptic_Node, false);
			 * 
			 * // 나이더스 캐널
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Nydus_Canal);
			 * 
			 * // 참고로, Zerg_Nydus_Canal 건물로부터 Nydus Canal Exit를 만드는 방법은 다음과 같습니다
			 * //if (MyBotModule.Broodwar.self().completedUnitCount(UnitType.
			 * Zerg_Nydus_Canal) > 0) { // for (Unit unit :
			 * MyBotModule.Broodwar.self().getUnits()) { // if (unit.getType()
			 * == UnitType.Zerg_Nydus_Canal) { // TilePosition
			 * targetTilePosition = new
			 * TilePosition(unit.getTilePosition().getX() + 6,
			 * unit.getTilePosition().getY()); // Creep 이 있는 곳이어야 한다 //
			 * unit.build(UnitType.Zerg_Nydus_Canal, targetTilePosition); // }
			 * // } //}
			 * 
			 * // 퀸 - 인페스티드 테란 : 테란 Terran_Command_Center 건물의 HitPoint가 낮을 때, 퀸을
			 * 들여보내서 Zerg_Infested_Command_Center 로 바꾸면, 그 건물에서 실행 됨
			 * BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType
			 * .Zerg_Infested_Terran);
			 */
		}
	}

	// 일꾼 계속 추가 생산
	public void executeWorkerTraining() {

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 0628 추가
		// 12초에 한번만 실행
		if (FrameCount % (24 * 12) != 0) {
			return;
		}

		if (MyBotModule.Broodwar.self().minerals() >= 50) {
			// workerCount = 현재 일꾼 수 + 생산중인 일꾼 수
			int workerCount = MyBotModule.Broodwar.self().allUnitCount(InformationManager.Instance().getWorkerType());

			if (MyBotModule.Broodwar.self().getRace() == Race.Zerg) {
				for (Unit unit : MyUnits) {
					if (unit.getType() == UnitType.Zerg_Egg) {
						// Zerg_Egg 에게 morph 명령을 내리면 isMorphing = true,
						// isBeingConstructed = true, isConstructing = true 가 된다
						// Zerg_Egg 가 다른 유닛으로 바뀌면서 새로 만들어진 유닛은 잠시
						// isBeingConstructed = true, isConstructing = true 가
						// 되었다가,
						if (unit.isMorphing() && unit.getBuildType() == UnitType.Zerg_Drone) {
							workerCount++;
						}
					}
				}
			} else {
				for (Unit unit : MyUnits) {
					if (unit.getType().isResourceDepot()) {
						if (unit.isTraining()) {
							workerCount += unit.getTrainingQueue().size();
						}
					}
				}
			}

			if (workerCount < 50) {
				for (Unit unit : MyUnits) {
					if (unit.getType().isResourceDepot()) {
						// 0628 최혜진 수정 - 기존에 빌드큐에 하나씩 무조건 넣어놓는 로직 삭제 후 직접 명령을
						// 내리는 방식으로 소스 추가
						// if (unit.isTraining() == false ||
						// unit.getLarva().size() > 0) {
						// 빌드큐에 일꾼 생산이 1개는 있도록 한다
						// if (BuildManager.Instance().buildQueue
						// .getItemCount(InformationManager.Instance().getWorkerType(),
						// null) == 0) {
						// // std.cout + "worker enqueue" + std.endl;
						// BuildManager.Instance().buildQueue.queueAsLowestPriority(
						// new
						// MetaType(InformationManager.Instance().getWorkerType()),
						// false);
						// }

						// for (Unit unit :
						// MyBotModule.Broodwar.self().getUnits()) {
						//
						// // 건물이고 트레이닝 할 수 있는 경우
						// if (unit.getType().isBuilding()) {
						// if(unit.canTrain()) {
						// System.out.println(unit.getTrainingQueue().size());
						unit.train(InformationManager.Instance().getWorkerType());
						// }
						// }
						//
						// }

						// }
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
		if (FrameCount % 24 != 0) {
			return;
		}

		// 게임에서는 서플라이 값이 200까지 있지만, BWAPI 에서는 서플라이 값이 400까지 있다
		// 저글링 1마리가 게임에서는 서플라이를 0.5 차지하지만, BWAPI 에서는 서플라이를 1 차지한다
		if (MyBotModule.Broodwar.self().supplyTotal() <= 400) {

			// 0710 추가
			int barracksCount = InformationManager.Instance().getNumUnits(UnitType.Terran_Barracks,
					MyBotModule.Broodwar.self());

			// 0630 수정
			int factoryCount = InformationManager.Instance().getNumUnits(UnitType.Terran_Factory,
					MyBotModule.Broodwar.self());

			// 서플라이가 다 꽉찼을때 새 서플라이를 지으면 지연이 많이 일어나므로, supplyMargin (게임에서의 서플라이
			// 마진 값의 2배)만큼 부족해지면 새 서플라이를 짓도록 한다
			// 이렇게 값을 정해놓으면, 게임 초반부에는 서플라이를 너무 일찍 짓고, 게임 후반부에는 서플라이를 너무 늦게 짓게 된다
			int supplyMargin = 8 + (barracksCount * 4) + (factoryCount * 8);

			// currentSupplyShortage 를 계산한다
			int currentSupplyShortage = MyBotModule.Broodwar.self().supplyUsed() + supplyMargin
					- MyBotModule.Broodwar.self().supplyTotal();

			if (currentSupplyShortage > 0) {

				// 생산/건설 중인 Supply를 센다
				int onBuildingSupplyCount = 0;

				// 저그 종족인 경우, 생산중인 Zerg_Overlord (Zerg_Egg) 를 센다. Hatchery 등 건물은
				// 세지 않는다
				if (MyBotModule.Broodwar.self().getRace() == Race.Zerg) {
					for (Unit unit : MyUnits) {
						if (unit.getType() == UnitType.Zerg_Egg && unit.getBuildType() == UnitType.Zerg_Overlord) {
							onBuildingSupplyCount += UnitType.Zerg_Overlord.supplyProvided();
						}
						// 갓태어난 Overlord 는 아직 SupplyTotal 에 반영안되어서, 추가 카운트를 해줘야함
						if (unit.getType() == UnitType.Zerg_Overlord && unit.isConstructing()) {
							onBuildingSupplyCount += UnitType.Zerg_Overlord.supplyProvided();
						}
					}
				}
				// 저그 종족이 아닌 경우, 건설중인 Protoss_Pylon, Terran_Supply_Depot 를 센다.
				// Nexus, Command Center 등 건물은 세지 않는다
				else {
					onBuildingSupplyCount += ConstructionManager.Instance().getConstructionQueueItemCount(
							InformationManager.Instance().getBasicSupplyProviderUnitType(), null)
							* InformationManager.Instance().getBasicSupplyProviderUnitType().supplyProvided();
				}

				// 주석처리
				// System.out.println("currentSupplyShortage : " +
				// currentSupplyShortage + " onBuildingSupplyCount : " +
				// onBuildingSupplyCount);

				if (currentSupplyShortage > onBuildingSupplyCount) {

					// BuildQueue 최상단에 SupplyProvider 가 있지 않으면 enqueue 한다
					boolean isToEnqueue = true;
					if (!BuildManager.Instance().buildQueue.isEmpty()) {
						BuildOrderItem currentItem = BuildManager.Instance().buildQueue.getHighestPriorityItem();
						if (currentItem.metaType.isUnit() && currentItem.metaType.getUnitType() == InformationManager
								.Instance().getBasicSupplyProviderUnitType()) {
							isToEnqueue = false;
						}
					}
					if (isToEnqueue) {
						// 주석처리
						// System.out.println("enqueue supply provider "
						// +
						// 0702 - 최혜진 수정 Supply Depot을 정렬하여 짓기 위해 기존 소스 수정
						// InformationManager.Instance().getBasicSupplyProviderUnitType());
						// BuildManager.Instance().buildQueue.queueAsHighestPriority(
						// new
						// MetaType(InformationManager.Instance().getBasicSupplyProviderUnitType()),
						// true);
						BuildManager.Instance().buildQueue.queueAsHighestPriority(
								InformationManager.Instance().getBasicSupplyProviderUnitType(),
								BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition, true);
					}
				}
			}
		}

		// BasicBot 1.1 Patch End
		// ////////////////////////////////////////////////
	}

	public void executeBasicCombatUnitTraining() {

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		if (!isEmergency) {
			return;
		}

		// 기본 병력 추가 훈련
		if (MyBotModule.Broodwar.self().minerals() >= 200 && MyBotModule.Broodwar.self().supplyUsed() < 390) {
			for (Unit unit : MyUnits) {
				if (unit.getType() == InformationManager.Instance().getBasicCombatBuildingType()) {
					if (unit.isTraining() == false || unit.getLarva().size() > 0) {
						if (BuildManager.Instance().buildQueue
								.getItemCount(InformationManager.Instance().getBasicCombatUnitType(), null) == 0) {
							BuildManager.Instance().buildQueue.queueAsLowestPriority(
									InformationManager.Instance().getBasicCombatUnitType(),
									BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
						}
					}
				}
			}
		}
	}

	public void executeSeniorityCombatUnitTraining() {

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 고급 병력 추가 훈련
		if (MyBotModule.Broodwar.self().minerals() >= 200 && MyBotModule.Broodwar.self().supplyUsed() < 390) {
			for (Unit unit : MyUnits) {
				if (unit.getType() == UnitType.Terran_Factory) {
					if (unit.isTraining() == false || unit.getLarva().size() > 0) {
						// 0628 수정
						// if
						// (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Siege_Tank_Tank_Mode,
						// null) == 0) {
						// if
						// (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Machine_Shop,
						// null) == 0) {
						// BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Machine_Shop,
						// BuildOrderItem.SeedPositionStrategy.MainBaseLocation,
						// true);
						// }
						// BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Siege_Tank_Tank_Mode,
						// BuildOrderItem.SeedPositionStrategy.MainBaseLocation,
						// true);
						// }
						if (BuildManager.Instance().buildQueue.getItemCount(UnitType.Terran_Vulture, null) == 0) {
							BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Vulture,
									BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
						}
					}
				}
			}
		}
	}

	public void executeCombat() {

		// 공격 모드가 아닐 때에는 전투유닛들을 아군 진영 길목에 집결시켜서 방어
		if (isFullScaleAttackStarted == false) {

			// 0710 추가
			BaseLocation mainBaseLocation = InformationManager.Instance()
					.getMainBaseLocation(InformationManager.Instance().selfPlayer);
			// 0627 수정 및 추가
			Chokepoint secondChokePoint = InformationManager.Instance()
					.getSecondChokePoint(InformationManager.Instance().selfPlayer);
			Unit bunker = null;

			for (Unit unit : MyUnits) {
				if (unit.getType() == UnitType.Terran_Bunker) {
					bunker = unit;
					break;
				}
			}

			// 0710 추가 - 긴급상황 시 본진 랠리 포인트
			// 앞마당 랠리 포인트
			for (Unit unit : MyUnits) {
				if (!unit.getType().isWorker() && !unit.getType().isBuilding()) {
					if (bunker != null && bunker.isCompleted() && unit.getType() == UnitType.Terran_Marine) {
						commandUtil.rightClick(unit, bunker);
					} else if (isEmergency) {
						commandUtil.attackMove(unit, mainBaseLocation.getPosition());
					} else {
						commandUtil.attackMove(unit, secondChokePoint.getCenter());
					}
				}
			}

			// 전투 유닛이 2개 이상 생산되었고, 적군 위치가 파악되었으면 총공격 모드로 전환
			if (MyBotModule.Broodwar.self().completedUnitCount(UnitType.Terran_Vulture) > 2) {
				if (InformationManager.Instance().enemyPlayer != null
						&& InformationManager.Instance().enemyRace != Race.Unknown && InformationManager.Instance()
								.getOccupiedBaseLocations(InformationManager.Instance().enemyPlayer).size() > 0) {
					isFullScaleAttackStarted = true;
				}
			}
		}
		// 공격 모드가 되면, 모든 전투유닛들을 적군 Main BaseLocation 로 공격 가도록 합니다
		else {
			// std.cout + "enemy OccupiedBaseLocations : " +
			// InformationManager.Instance().getOccupiedBaseLocations(InformationManager.Instance()._enemy).size()
			// + std.endl;

			if (InformationManager.Instance().enemyPlayer != null
					&& InformationManager.Instance().enemyRace != Race.Unknown && InformationManager.Instance()
							.getOccupiedBaseLocations(InformationManager.Instance().enemyPlayer).size() > 0) {
				// 공격 대상 지역 결정
				BaseLocation targetBaseLocation = null;
				double closestDistance = 100000000;

				for (BaseLocation baseLocation : InformationManager.Instance()
						.getOccupiedBaseLocations(InformationManager.Instance().enemyPlayer)) {
					double distance = BWTA.getGroundDistance(InformationManager.Instance()
							.getMainBaseLocation(InformationManager.Instance().selfPlayer).getTilePosition(),
							baseLocation.getTilePosition());

					if (distance < closestDistance) {
						closestDistance = distance;
						targetBaseLocation = baseLocation;
					}
				}

				if (targetBaseLocation != null) {
					for (Unit unit : MyUnits) {
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
		String enemyName = MyBotModule.Broodwar.enemy().getName();
		enemyName = enemyName.replace(' ', '_');
		String myName = MyBotModule.Broodwar.self().getName();
		myName = myName.replace(' ', '_');

		/// 이번 게임에 대한 기록
		GameRecord thisGameRecord = new GameRecord();
		thisGameRecord.mapName = mapName;
		thisGameRecord.myName = myName;
		thisGameRecord.myRace = MyBotModule.Broodwar.self().getRace().toString();
		thisGameRecord.enemyName = enemyName;
		thisGameRecord.enemyRace = MyBotModule.Broodwar.enemy().getRace().toString();
		thisGameRecord.enemyRealRace = InformationManager.Instance().enemyRace.toString();
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
		if (FrameCount % 100 != 0) {
			return;
		}

		// TODO : 파일명은 각자 봇 명에 맞게 수정하시기 바랍니다
		String gameLogFileName = "c:\\starcraft\\bwapi-data\\write\\NoNameBot_LastGameLog.dat";

		String mapName = MyBotModule.Broodwar.mapFileName();
		mapName = mapName.replace(' ', '_');
		String enemyName = MyBotModule.Broodwar.enemy().getName();
		enemyName = enemyName.replace(' ', '_');
		String myName = MyBotModule.Broodwar.self().getName();
		myName = myName.replace(' ', '_');

		StringBuilder ss = new StringBuilder();
		ss.append(mapName + " ");
		ss.append(myName + " ");
		ss.append(MyBotModule.Broodwar.self().getRace().toString() + " ");
		ss.append(enemyName + " ");
		ss.append(InformationManager.Instance().enemyRace.toString() + " ");
		ss.append(MyBotModule.Broodwar.getFrameCount() + " ");
		ss.append(MyBotModule.Broodwar.self().supplyUsed() + " ");
		ss.append(MyBotModule.Broodwar.self().supplyTotal() + "\n");

		Common.appendTextToFile(gameLogFileName, ss.toString());
	}

	// BasicBot 1.1 Patch End //////////////////////////////////////////////////

}