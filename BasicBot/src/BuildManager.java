import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import bwapi.Player;
import bwapi.Position;
import bwapi.Race;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitCommand;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Chokepoint;
import bwta.Region;

/// 빌드(건물 건설 / 유닛 훈련 / 테크 리서치 / 업그레이드) 명령을 순차적으로 실행하기 위해 빌드 큐를 관리하고, 빌드 큐에 있는 명령을 하나씩 실행하는 class<br>
/// 빌드 명령 중 건물 건설 명령은 ConstructionManager로 전달합니다
/// @see ConstructionManager
public class BuildManager {

	private InformationManager info = InformationManager.Instance();
	private Player self = MyBotModule.Broodwar.self();
	private Player enemy = MyBotModule.Broodwar.enemy();
	private StrategyManager stmgr = StrategyManager.Instance();

	// 0703 - 최혜진 추가 Supply Depot 위치 지정을 위한 변수 선언
	private static boolean isSupplyDepotBuild = false;
	// 0722 - 최혜진 수정 변수 값 삭제
	private static int leftcornerX;
	private static int rightcornerX;
	private static int uppercornerY;
	private static int lowercornerY;
	private static int numberOfSupply = 0;
	// 0704 - 김성수 수정 StrategyManager에서 사용
	// 0805 - 김성수 수정 Getter 등록
	private static int locationOfBase = 0;

	// 0807 - 최혜진 삭제
	// private static boolean isBarrackBuilt = false;

	// 0729 - 최혜진 추가
	// 0802 - 최혜진 수정 투혼 맵에 Turret 좌표 동일하게 지정 추후 전면 수정 필요
	// 0805 - 최혜진 수정 투혼 맵 Turret 좌표
	// 0806 - 최혜진 수정 Turret이 두번째 길목에 가장 먼저 건설되도록 변경
	// 0807 - 최혜진 수정 앞마당 Command Center 옆에 Turret 추가
	public static int numberOfTurretBuilt = 0;
	private static int[] turretXLocationForCircuit = { 5, 0, 6, 20, 24, 121, 0, 120, 106, 102, 5, 0, 6, 20, 24, 121, 0,
			120, 106, 102 };
	private static int[] turretYLocationForCircuit = { 33, 0, 42, 19, 6, 33, 0, 42, 19, 6, 96, 0, 85, 107, 120, 96, 0,
			85, 107, 120 };
	private static int[] turretXLocationForSpirit = { 0, 2, 8, 26, 22, 0, 94, 80, 106, 120, 0, 32, 46, 21, 6, 0, 124,
			115, 101, 102 };
	private static int[] turretYLocationForSpirit = { 0, 30, 44, 19, 6, 0, 3, 12, 20, 29, 0, 125, 113, 104, 97, 0, 95,
			82, 107, 120 };

	// 0730 - 최혜진 추가
	public static int numberOfFactoryBuilt = 0;
	private static boolean isFirstBuilt;

	// 0801 - 최혜진 추가 Factory 좌표 지정
	// 0802 - 최혜진 수정 투혼 맵에 Factory 좌표 지정
	// 0806 - 최혜진 수정 투혼 맵 Factory 좌표 입구와 가깝게 수정
	// 0807 - 최혜진 수정 서킷 맵 Factory y 좌표 수정
	private static int[] FactoryXLocationForCircuit = { 0, 7, 11, 15, 122, 117, 113, 109, 0 };
	private static int[] FactoryYLocationForCircuit = { 20, 16, 105, 109, 0 };
	private static int[] FactoryXLocationForSpirit = { 0, 7, 11, 15, 109, 116, 120, 124, 13, 8, 4, 0, 122, 117, 113,
			109 };
	private static int[] FactoryYLocationForSpirit = { 24, 20, 15, 19, 107, 103, 102, 106 };

	// 0805 - 최혜진 추가 투혼 맵 1시 방향 Bunker 올바른 위치 건설 위한 변수
	public static boolean zergNot4Drone;

	// 0807 - 최혜진 앞마당 막기 좌표 수정
	private static int[] expansionXLocaitonForCircuit = { 13, 8, 11, 112, 117, 114, 11, 8, 14, 113, 117, 111 };
	private static int[] expansionYLocaitonForCircuit = { 30, 32, 33, 30, 32, 33, 94, 95, 97, 94, 95, 97 };
	private static int[] expansionXLocaitonForSpirit = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	private static int[] expansionYLocaitonForSpirit = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	private static int expansionOrder;

	/// BuildOrderItem 들의 목록을 저장하는 buildQueue
	public BuildOrderQueue buildQueue = new BuildOrderQueue();

	private static BuildManager instance = new BuildManager();

	public WorkerData workerdata = new WorkerData();

	/// static singleton 객체를 리턴합니다
	public static BuildManager Instance() {
		return instance;
	}

	public int getLocationOfBase() {
		return locationOfBase;
	}

	/// buildQueue 에 대해 Dead lock 이 있으면 제거하고, 가장 우선순위가 높은 BuildOrderItem 를 실행되도록
	/// 시도합니다
	public void update() {
		// 1초(24프레임)에 4번 정도만 실행해도 충분하다
		if (MyBotModule.Broodwar.getFrameCount() % 6 != 0)
			return;

		if (buildQueue.isEmpty()) {
			return;
		}

		// RefineryDeadLock을 먼저 제거한다.
		checkRefineryDeadLock();
		// Dead Lock 을 체크해서 제거한다
		checkBuildOrderQueueDeadlockAndAndFixIt();
		// Dead Lock 제거후 Empty 될 수 있다
		if (buildQueue.isEmpty()) {
			return;
		}

		// the current item to be used
		BuildOrderItem currentItem = buildQueue.getHighestPriorityItem();

		// System.out.println("current HighestPriorityItem is " +
		// currentItem.metaType.getName());

		// while there is still something left in the buildQueue
		while (!buildQueue.isEmpty()) {
			boolean isOkToRemoveQueue = true;

			// BasicBot 1.1 Patch Start
			// ////////////////////////////////////////////////
			// 빌드 실행 유닛 (일꾼/건물) 결정 로직이 seedLocation 이나 seedLocationStrategy 를 잘
			// 반영하도록 수정

			// seedPosition 을 도출한다
			Position seedPosition = null;
			if (currentItem.seedLocation != TilePosition.None && currentItem.seedLocation != TilePosition.Invalid
					&& currentItem.seedLocation != TilePosition.Unknown && currentItem.seedLocation.isValid()) {
				seedPosition = currentItem.seedLocation.toPosition();
			} else {
				// 0808 - 최혜진 수정 seedPosition과 desiredPosition의 충돌로 인해 깜빡거림 발생하는
				// 것 방지
				if (currentItem.seedLocationStrategy != BuildOrderItem.SeedPositionStrategy.BlockFirstChokePoint
						&& currentItem.seedLocationStrategy != BuildOrderItem.SeedPositionStrategy.BunkerForZerg
						&& currentItem.seedLocationStrategy != BuildOrderItem.SeedPositionStrategy.FactoryInMainBaseLocation
						&& currentItem.seedLocationStrategy != BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition
						&& currentItem.seedLocationStrategy != BuildOrderItem.SeedPositionStrategy.TurretAround
						&& currentItem.seedLocationStrategy != BuildOrderItem.SeedPositionStrategy.OtherInMainBaseLocation
						&& currentItem.seedLocationStrategy != BuildOrderItem.SeedPositionStrategy.MultipleExpansion) {
					seedPosition = getSeedPositionFromSeedLocationStrategy(currentItem.seedLocationStrategy);
				}
			}
			// this is the unit which can produce the currentItem
			Unit producer = getProducer(currentItem.metaType, seedPosition, currentItem.producerID);

			// BasicBot 1.1 Patch End
			// //////////////////////////////////////////////////

			Unit secondProducer = null;
			boolean canMake = false;

			// 건물을 만들수 있는 유닛(일꾼)이나, 유닛을 만들수 있는 유닛(건물 or 유닛)이 있으면
			if (producer != null) {

				// check to see if we can make it right now
				// 지금 해당 유닛을 건설/생산 할 수 있는지에 대해 자원, 서플라이, 테크 트리, producer 만을 갖고
				// 판단한다
				canMake = canMakeNow(producer, currentItem.metaType);

				// 프로토스 종족 유닛 중 Protoss_Archon / Protoss_Dark_Archon 은 기존
				// Protoss_High_Templar / Protoss_Dark_Templar 두 유닛을 합체시키는 기술을
				// 써서 만들기 때문에
				// secondProducer 을 추가로 찾아 확인한다
				if (canMake) {
					if (currentItem.metaType.isUnit()) {
						if (currentItem.metaType.getUnitType() == UnitType.Protoss_Archon
								|| currentItem.metaType.getUnitType() == UnitType.Protoss_Dark_Archon) {
							secondProducer = getAnotherProducer(producer, producer.getPosition());
							if (secondProducer == null) {
								canMake = false;
							}
						}
					}
				}
			}

			// if we can make the current item, create it
			if (producer != null && canMake == true) {
				MetaType t = currentItem.metaType;

				if (t.isUnit()) {
					if (t.getUnitType().isBuilding()) {

						// 저그 종족 건물 중 Zerg_Lair, Zerg_Hive, Zerg_Greater_Spire,
						// Zerg_Sunken_Colony, Zerg_Spore_Colony 는 기존 건물을 Morph
						// 시켜 만든다
						// Morph를 시작하면 isMorphing = true, isBeingConstructed =
						// true, isConstructing = true 가 되고
						// 완성되면 isMorphing = false, isBeingConstructed = false,
						// isConstructing = false, isCompleted = true 가 된다
						if (t.getUnitType().getRace() == Race.Zerg && t.getUnitType().whatBuilds().first.isBuilding()) {
							producer.morph(t.getUnitType());
						}
						// 테란 Addon 건물의 경우 (Addon 건물을 지을수 있는지는 getProducer 함수에서
						// 이미 체크완료)
						// 모건물이 Addon 건물 짓기 전에는 canBuildAddon = true,
						// isConstructing = false, canCommand = true 이다가
						// Addon 건물을 짓기 시작하면 canBuildAddon = false,
						// isConstructing = true, canCommand = true 가 되고 (Addon
						// 건물 건설 취소는 가능하나 Train 등 커맨드는 불가능)
						// 완성되면 canBuildAddon = false, isConstructing = false 가
						// 된다
						else if (t.getUnitType().isAddon()) {

							// std::cout + "addon build start " + std::endl;

							producer.buildAddon(t.getUnitType());
							// 테란 Addon 건물의 경우 정상적으로 buildAddon 명령을 내려도 SCV가 모건물
							// 근처에 있을 때 한동안 buildAddon 명령이 취소되는 경우가 있어서
							// 모건물이 isConstructing = true 상태로 바뀐 것을 확인한 후
							// buildQueue 에서 제거해야한다
							if (producer.isConstructing() == false) {
								isOkToRemoveQueue = false;
							}
							// std::cout + "8";
						}
						// 그외 대부분 건물의 경우
						else {
							// ConstructionPlaceFinder 를 통해 건설 가능 위치
							// desiredPosition 를 알아내서
							// ConstructionManager 의 ConstructionTask Queue에 추가를
							// 해서 desiredPosition 에 건설을 하게 한다.
							// ConstructionManager 가 건설 도중에 해당 위치에 건설이 어려워지면 다시
							// ConstructionPlaceFinder 를 통해 건설 가능 위치를
							// desiredPosition 주위에서 찾을 것이다
							TilePosition desiredPosition = getDesiredPosition(t.getUnitType(), currentItem.seedLocation,
									currentItem.seedLocationStrategy);
							if (desiredPosition != TilePosition.None) {
								// Send the construction task to the
								// construction manager
								ConstructionManager.Instance().addConstructionTask(t.getUnitType(), desiredPosition);
							} else {
								// 건물 가능 위치가 없는 경우는, Protoss_Pylon 가 없거나, Creep
								// 이 없거나, Refinery 가 이미 다 지어져있거나, 정말 지을 공간이 주위에
								// 없는 경우인데,
								// 대부분의 경우 Pylon 이나 Hatchery가 지어지고 있는 중이므로, 다음
								// frame 에 건물 지을 공간을 다시 탐색하도록 한다.
								System.out.print("There is no place to construct " + currentItem.metaType.getUnitType()
										+ " strategy " + currentItem.seedLocationStrategy);
								if (currentItem.seedLocation != null)
									System.out.print(" seedPosition " + currentItem.seedLocation.getX() + ","
											+ currentItem.seedLocation.getY());
								if (desiredPosition != null)
									System.out.print(" desiredPosition " + desiredPosition.getX() + ","
											+ desiredPosition.getY());
								isOkToRemoveQueue = false;
							}
						}
					}
					// 지상유닛 / 공중유닛의 경우
					else {
						// 저그 지상유닛 / 공중유닛
						if (t.getUnitType().getRace() == Race.Zerg) {
							// 저그 종족 유닛의 거의 대부분은 Morph 시켜 만든다
							if (t.getUnitType() != UnitType.Zerg_Infested_Terran) {
								producer.morph(t.getUnitType());
							}
							// 저그 종족 유닛 중 Zerg_Infested_Terran 은 Train 시켜 만든다
							else {
								producer.train(t.getUnitType());
							}
						}
						// 프로토스 지상유닛 / 공중유닛
						else if (t.getUnitType().getRace() == Race.Protoss) {
							// 프로토스 종족 유닛 중 Protoss_Archon 은 기존
							// Protoss_High_Templar 두 유닛을 합체시키는 기술을 써서 만든다
							if (t.getUnitType() == UnitType.Protoss_Archon) {
								producer.useTech(TechType.Archon_Warp, secondProducer);
							}
							// 프로토스 종족 유닛 중 Protoss_Dark_Archon 은 기존
							// Protoss_Dark_Templar 두 유닛을 합체시키는 기술을 써서 만든다
							else if (t.getUnitType() == UnitType.Protoss_Dark_Archon) {
								producer.useTech(TechType.Dark_Archon_Meld, secondProducer);
							} else {
								producer.train(t.getUnitType());
							}
						}
						// 테란 지상유닛 / 공중유닛
						else {
							producer.train(t.getUnitType());
						}
					}
				}
				// if we're dealing with a tech research
				else if (t.isTech()) {
					producer.research(t.getTechType());
				} else if (t.isUpgrade()) {
					producer.upgrade(t.getUpgradeType());
				}

				// remove it from the buildQueue
				if (isOkToRemoveQueue) {
					buildQueue.removeCurrentItem();
				}

				// don't actually loop around in here
				break;
			}
			// otherwise, if we can skip the current item
			else if (buildQueue.canSkipCurrentItem()) {
				// skip it and get the next one
				buildQueue.skipCurrentItem();
				currentItem = buildQueue.getNextItem();
			} else {
				// so break out
				break;
			}
		}
	}

	private void checkRefineryDeadLock() {

		boolean canPlanBuildOrderNow = true;
		for (final Unit unit : self.getUnits()) {
			if (unit.getRemainingTrainTime() == 0) {
				continue;
			}

			UnitCommand unitCommand = unit.getLastCommand();
			if (unitCommand != null) {

				UnitCommandType unitCommandType = unitCommand.getUnitCommandType();
				if (unitCommandType != UnitCommandType.None) {
					if (unitCommand.getUnit() != null) {
						UnitType trainType = unitCommand.getUnit().getType();
						if (unit.getRemainingTrainTime() == trainType.buildTime()) {
							canPlanBuildOrderNow = false;
							break;
						}
					}
				}
			}

		}
		if (!canPlanBuildOrderNow) {
			return;
		}

		BuildOrderQueue buildQueue = BuildManager.Instance().getBuildQueue();
		if (!buildQueue.isEmpty()) {

			for (int i = 0; i < buildQueue.size(); i++) {
				BuildOrderItem currentItem = buildQueue.getNextItem();

				if (currentItem.blocking == true) {
					boolean isDeadlockCase = false;
					// 건물이나 유닛의 경우
					if (currentItem.metaType.isUnit()) {
						UnitType unitType = currentItem.metaType.getUnitType();

						// Refinery 건물의 경우, Refinery 가 건설되지 않은 Geyser가 있는 경우에만
						// 가능
						if (unitType == UnitType.Terran_Refinery) {
							boolean hasAvailableGeyser = true;

							// Refinery가 지어질 수 있는 장소를 찾아본다
							TilePosition testLocation = getDesiredPosition(unitType, currentItem.seedLocation,
									currentItem.seedLocationStrategy);

							// Refinery 를 지으려는 장소를 찾을 수 없으면 dead lock
							if (testLocation == TilePosition.None || testLocation == TilePosition.Invalid
									|| testLocation.isValid() == false) {
								hasAvailableGeyser = false;
							} else {
								// Refinery 를 지으려는 장소에 Refinery 가 이미 건설되어 있다면
								// dead
								// lock
								for (Unit u : MyBotModule.Broodwar.getUnitsOnTile(testLocation)) {
									if (u.getType().isRefinery() && u.exists()) {
										hasAvailableGeyser = false;
										break;
									}
								}
							}

							if (hasAvailableGeyser == false) {
								isDeadlockCase = true;
							}
							if (isDeadlockCase) {
								buildQueue.removeCurrentItem();
							}
						}
					}
				}
			}
		}

	}

	/// 해당 MetaType 을 build 할 수 있는 producer 를 찾아 반환합니다
	/// @param t 빌드하려는 대상의 타입
	/// @param closestTo 파라메타 입력 시 producer 후보들 중 해당 position 에서 가장 가까운 producer
	/// 를
	/// 리턴합니다
	/// @param producerID 파라메타 입력 시 해당 ID의 unit 만 producer 후보가 될 수 있습니다
	public Unit getProducer(MetaType t, Position closestTo, int producerID) {
		// get the type of unit that builds this
		UnitType producerType = t.whatBuilds();

		// make a set of all candidate producers
		List<Unit> candidateProducers = new ArrayList<Unit>();
		for (Unit unit : self.getUnits()) {

			if (unit == null)
				continue;

			// reasons a unit can not train the desired type
			if (unit.getType() != producerType) {
				continue;
			}
			if (!unit.exists()) {
				continue;
			}
			if (!unit.isCompleted()) {
				continue;
			}
			if (unit.isTraining()) {
				continue;
			}
			if (!unit.isPowered()) {
				continue;
			}
			// if unit is lifted, unit should land first
			if (unit.isLifted()) {
				continue;
			}

			if (producerID != -1 && unit.getID() != producerID) {
				continue;
			}

			// 0729 추가 - 업그레이드 진행 중인 아모리는 후보에서 제외
			if (unit.isUpgrading()) {
				continue;
			}

			// 0722 추가 - 탱크나 골리앗을 생산할 때 팩토리에 머신샵이 설치되어 있지 않다면 해당 팩토리는 후보에서 제외
			if (t.getUnitType() == UnitType.Terran_Siege_Tank_Tank_Mode || t.getUnitType() == UnitType.Terran_Goliath) {
				if (unit.getAddon() == null || !unit.isCompleted()
						|| unit.getAddon().getType() != UnitType.Terran_Machine_Shop) {
					continue;
				}
			}

			if (t.isUnit()) {
				// if the type requires an addon and the producer doesn't have
				// one
				// C++ : typedef std::pair<BWAPI::UnitType, int> ReqPair;
				Map<UnitType, Integer> requiredUnitsMap = t.getUnitType().requiredUnits();
				if (requiredUnitsMap != null) {
					Iterator<UnitType> it = requiredUnitsMap.keySet().iterator();

					// for (final Pair<UnitType, Integer> pair :
					// t.getUnitType().requiredUnits())
					while (it.hasNext()) {
						UnitType requiredType = it.next();
						if (requiredType.isAddon()) {
							if (unit.getAddon() == null || (unit.getAddon().getType() != requiredType)) {
								continue;
							}
						}
					}
				}

				// if the type is an addon
				if (t.getUnitType().isAddon()) {
					// if the unit already has an addon, it can't make one
					if (unit.getAddon() != null) {
						continue;
					}

					// 모건물은 건설되고 있는 중에는 isCompleted = false, isConstructing =
					// true, canBuildAddon = false 이다가
					// 건설이 완성된 후 몇 프레임동안은 isCompleted = true 이지만, canBuildAddon
					// = false 인 경우가 있다
					if (!unit.canBuildAddon()) {
						continue;
					}

					// if we just told this unit to build an addon, then it will
					// not be building another one
					// this deals with the frame-delay of telling a unit to
					// build an addon and it actually starting to build
					if (unit.getLastCommand().getUnitCommandType() == UnitCommandType.Build_Addon // C++
																									// :
																									// unit.getLastCommand().getType()
							&& (MyBotModule.Broodwar.getFrameCount() - unit.getLastCommandFrame() < 10)) {
						continue;
					}

					boolean isBlocked = false;

					// if the unit doesn't have space to build an addon, it
					// can't make one
					TilePosition addonPosition = new TilePosition(
							unit.getTilePosition().getX() + unit.getType().tileWidth(),
							unit.getTilePosition().getY() + unit.getType().tileHeight() - t.getUnitType().tileHeight());

					for (int i = 0; i < t.getUnitType().tileWidth(); ++i) {
						for (int j = 0; j < t.getUnitType().tileHeight(); ++j) {
							TilePosition tilePos = new TilePosition(addonPosition.getX() + i, addonPosition.getY() + j);

							// if the map won't let you build here, we can't
							// build it.
							// 맵 타일 자체가 건설 불가능한 타일인 경우 + 기존 건물이 해당 타일에 이미 있는경우
							if (!MyBotModule.Broodwar.isBuildable(tilePos, true)) {
								isBlocked = true;
							}

							// if there are any units on the addon tile, we
							// can't build it
							// 아군 유닛은 Addon 지을 위치에 있어도 괜찮음. (적군 유닛은 Addon 지을 위치에
							// 있으면 건설 안되는지는 아직 불확실함)
							for (Unit u : MyBotModule.Broodwar.getUnitsOnTile(tilePos.getX(), tilePos.getY())) {
								if (u.getPlayer() != info.selfPlayer) {
									isBlocked = false;
								}
							}
						}
					}

					if (isBlocked) {
						continue;
					}
				}
			}

			// if we haven't cut it, add it to the set of candidates
			candidateProducers.add(unit); // C++ :
											// candidateProducers.insert(unit);

		}

		return getClosestUnitToPosition(candidateProducers, closestTo);
	}

	/// 해당 MetaType 을 build 할 수 있는 producer 를 찾아 반환합니다
	public Unit getProducer(MetaType t, Position closestTo) {
		return getProducer(t, closestTo, -1);
	}

	/// 해당 MetaType 을 build 할 수 있는 producer 를 찾아 반환합니다
	public Unit getProducer(MetaType t) {
		return getProducer(t, Position.None, -1);
	}

	/// 해당 MetaType 을 build 할 수 있는, getProducer 리턴값과 다른 producer 를 찾아 반환합니다<br>
	/// 프로토스 종족 유닛 중 Protoss_Archon / Protoss_Dark_Archon 을 빌드할 때 사용합니다
	public Unit getAnotherProducer(Unit producer, Position closestTo) {
		if (producer == null)
			return null;

		List<Unit> candidateProducers = new ArrayList<Unit>();
		for (Unit unit : self.getUnits()) {
			if (unit == null) {
				continue;
			}
			if (unit.getType() != producer.getType()) {
				continue;
			}
			if (unit.getID() == producer.getID()) {
				continue;
			}
			if (!unit.isCompleted()) {
				continue;
			}
			if (unit.isTraining()) {
				continue;
			}
			if (!unit.exists()) {
				continue;
			}
			if (unit.getHitPoints() + unit.getEnergy() <= 0) {
				continue;
			}

			candidateProducers.add(unit); // C++ :
											// candidateProducers.insert(unit);
		}

		return getClosestUnitToPosition(candidateProducers, closestTo);
	}

	public Unit getClosestUnitToPosition(final List<Unit> units, Position closestTo) {
		if (units.size() == 0) {
			return null;
		}

		// BasicBot 1.1 Patch Start
		// ////////////////////////////////////////////////
		// 빌드 실행 유닛 (일꾼/건물) 결정 로직이 seedLocation 이나 seedLocationStrategy 를 잘
		// 반영하도록 수정

		// if we don't care where the unit is return the first one we have
		if (closestTo == Position.None || closestTo == Position.Invalid || closestTo == Position.Unknown
				|| closestTo.isValid() == false) {
			return units.get(0); // C++ : return units.begin();
		}

		// BasicBot 1.1 Patch End
		// //////////////////////////////////////////////////

		Unit closestUnit = null;
		double minDist = 1000000000;

		for (Unit unit : units) {
			if (unit == null)
				continue;

			double distance = unit.getDistance(closestTo);
			if (closestUnit == null || distance < minDist) {
				// 0806 - 최혜진 수정 정찰 중인 scv가 건설에 동원되지 않도록 수정
				if (unit.getType() == UnitType.Terran_SCV) {
					if (workerdata.getWorkerJob(unit) == WorkerData.WorkerJob.Scout) {
						continue;
					}
					closestUnit = unit;
					minDist = distance;
				} else {
					closestUnit = unit;
					minDist = distance;
				}

			}
		}

		return closestUnit;
	}

	// 지금 해당 유닛을 건설/생산 할 수 있는지에 대해 자원, 서플라이, 테크 트리, producer 만을 갖고 판단한다<br>
	// 해당 유닛이 건물일 경우 건물 지을 위치의 적절 여부 (탐색했었던 타일인지, 건설 가능한 타일인지, 주위에 Pylon이
	// 있는지,<br>
	// Creep이 있는 곳인지 등) 는 판단하지 않는다
	public boolean canMakeNow(Unit producer, MetaType t) {
		if (producer == null) {
			return false;
		}

		boolean canMake = hasEnoughResources(t);

		if (canMake) {
			if (t.isUnit()) {
				// MyBotModule.Broodwar.canMake : Checks all the requirements
				// include resources, supply, technology tree, availability, and
				// required units
				canMake = MyBotModule.Broodwar.canMake(t.getUnitType(), producer);
			} else if (t.isTech()) {
				canMake = MyBotModule.Broodwar.canResearch(t.getTechType(), producer);
			} else if (t.isUpgrade()) {
				canMake = MyBotModule.Broodwar.canUpgrade(t.getUpgradeType(), producer);
			}
		}

		return canMake;
	}

	// 건설 가능 위치를 찾는다<br>
	// seedLocationStrategy 가 SeedPositionSpecified 인 경우에는 그 근처만 찾아보고,<br>
	// SeedPositionSpecified 이 아닌 경우에는 seedLocationStrategy 를 조금씩 바꿔가며 계속
	// 찾아본다.<br>
	// (MainBase . MainBase 주위 . MainBase 길목 . MainBase 가까운 앞마당 . MainBase 가까운
	// 앞마당의
	// 길목 . 탐색 종료)
	public TilePosition getDesiredPosition(UnitType unitType, TilePosition seedPosition,
			BuildOrderItem.SeedPositionStrategy seedPositionStrategy) {
		TilePosition desiredPosition = ConstructionPlaceFinder.Instance()
				.getBuildLocationWithSeedPositionAndStrategy(unitType, seedPosition, seedPositionStrategy);

		// desiredPosition 을 찾을 수 없는 경우
		boolean findAnotherPlace = true;
		while (desiredPosition == TilePosition.None) {

			switch (seedPositionStrategy) {
			case MainBaseLocation:
				seedPositionStrategy = BuildOrderItem.SeedPositionStrategy.MainBaseBackYard;
				break;
			case MainBaseBackYard:
				seedPositionStrategy = BuildOrderItem.SeedPositionStrategy.FirstChokePoint;
				break;
			case FirstChokePoint:
				seedPositionStrategy = BuildOrderItem.SeedPositionStrategy.FirstExpansionLocation;
				break;
			case FirstExpansionLocation:
				seedPositionStrategy = BuildOrderItem.SeedPositionStrategy.SecondChokePoint;
				break;
			case SecondChokePoint:
			case SeedPositionSpecified:
			default:
				findAnotherPlace = false;
				break;
			}

			// 다른 곳을 더 찾아본다
			if (findAnotherPlace) {
				desiredPosition = ConstructionPlaceFinder.Instance()
						.getBuildLocationWithSeedPositionAndStrategy(unitType, seedPosition, seedPositionStrategy);
			}
			// 다른 곳을 더 찾아보지 않고, 끝낸다
			else {
				break;
			}
		}

		// System.out.println(desiredPosition.getX()+"
		// "+desiredPosition.getY());
		return desiredPosition;
	}

	// 사용가능 미네랄 = 현재 보유 미네랄 - 사용하기로 예약되어있는 미네랄
	public int getAvailableMinerals() {
		return self.minerals() - ConstructionManager.Instance().getReservedMinerals();
	}

	// 사용가능 가스 = 현재 보유 가스 - 사용하기로 예약되어있는 가스
	public int getAvailableGas() {
		return self.gas() - ConstructionManager.Instance().getReservedGas();
	}

	// return whether or not we meet resources, including building reserves
	public boolean hasEnoughResources(MetaType type) {
		// return whether or not we meet the resources
		return (type.mineralPrice() <= getAvailableMinerals()) && (type.gasPrice() <= getAvailableGas());
	}

	// selects a unit of a given type
	public Unit selectUnitOfType(UnitType type, Position closestTo) {
		// if we have none of the unit type, return null right away
		if (self.completedUnitCount(type) == 0) {
			return null;
		}

		Unit unit = null;

		// if we are concerned about the position of the unit, that takes
		// priority
		if (closestTo != Position.None) {
			double minDist = 1000000000;

			for (Unit u : self.getUnits()) {
				if (u.getType() == type) {
					double distance = u.getDistance(closestTo);
					if (unit == null || distance < minDist) {
						unit = u;
						minDist = distance;
					}
				}
			}

			// if it is a building and we are worried about selecting the unit
			// with the least
			// amount of training time remaining
		} else if (type.isBuilding()) {
			for (Unit u : self.getUnits()) {
				if (u.getType() == type && u.isCompleted() && !u.isTraining() && !u.isLifted() && u.isPowered()) {

					return u;
				}
			}
			// otherwise just return the first unit we come across
		} else {
			for (Unit u : self.getUnits()) {
				if (u.getType() == type && u.isCompleted() && u.getHitPoints() > 0 && !u.isLifted() && u.isPowered()) {
					return u;
				}
			}
		}

		// return what we've found so far
		return null;
	}

	/// BuildOrderItem 들의 목록을 저장하는 buildQueue 를 리턴합니다
	public BuildOrderQueue getBuildQueue() {
		return buildQueue;
	}

	/// seedPositionStrategy 을 현재 게임상황에 맞게 seedPosition 으로 바꾸어 리턴합니다
	private Position getSeedPositionFromSeedLocationStrategy(BuildOrderItem.SeedPositionStrategy seedLocationStrategy) {

		// BasicBot 1.1 Patch Start
		// ////////////////////////////////////////////////
		// 빌드 실행 유닛 (일꾼/건물) 결정 로직이 seedLocation 이나 seedLocationStrategy 를 잘
		// 반영하도록 수정

		Position seedPosition = null;
		Chokepoint tempChokePoint;
		BaseLocation tempBaseLocation;
		BaseLocation tempFirstExpansion; // 0703 - 최혜진 추가
		TilePosition tempTilePosition = null;
		Region tempBaseRegion;
		int vx, vy;
		double d, theta;
		int bx, by;

		switch (seedLocationStrategy) {
		case MainBaseLocation:
			tempBaseLocation = info.getMainBaseLocation(self);
			if (tempBaseLocation != null) {
				seedPosition = tempBaseLocation.getPosition();
			}
			break;
		case MainBaseBackYard:
			tempBaseLocation = info.getMainBaseLocation(self);
			tempChokePoint = info.getFirstChokePoint(self);
			tempBaseRegion = BWTA.getRegion(tempBaseLocation.getPosition());

			// std::cout << "y";

			// (vx, vy) = BaseLocation 와 ChokePoint 간 차이 벡터 = 거리 d 와 각도 t 벡터.
			// 단위는 position
			// 스타크래프트 좌표계 : 오른쪽으로 갈수록 x 가 증가 (데카르트 좌표계와 동일). 아래로 갈수록 y가 증가 (y축만
			// 데카르트 좌표계와
			// 반대)
			// 삼각함수 값은 데카르트 좌표계에서 계산하므로, vy를 부호 반대로 해서 각도 t 값을 구함

			// MainBaseLocation 이 null 이거나, ChokePoint 가 null 이면,
			// MainBaseLocation 주위에서 가능한
			// 곳을 리턴한다
			if (tempBaseLocation != null && tempChokePoint != null) {

				// BaseLocation 에서 ChokePoint 로의 벡터를 구한다
				vx = tempChokePoint.getCenter().getX() - tempBaseLocation.getPosition().getX();
				// std::cout << "vx : " << vx ;
				vy = (tempChokePoint.getCenter().getY() - tempBaseLocation.getPosition().getY()) * (-1);
				// std::cout << "vy : " << vy;
				d = Math.sqrt(vx * vx + vy * vy) * 0.5; // BaseLocation 와
														// ChokePoint 간 거리보다 조금
														// 짧은 거리로 조정.
														// BaseLocation가
														// 있는 Region은 대부분 직사각형
														// 형태이기 때문
				// std::cout << "d : " << d;
				theta = Math.atan2(vy, vx + 0.0001); // 라디안 단위
				bx = tempBaseLocation.getTilePosition().getX() - (int) (d * Math.cos(theta) / Config.TILE_SIZE);
				by = tempBaseLocation.getTilePosition().getY() + (int) (d * Math.sin(theta) / Config.TILE_SIZE);
				// std::cout << "i";
				tempTilePosition = new TilePosition(bx, by);
				// std::cout << "ConstructionPlaceFinder MainBaseBackYard
				// tempTilePosition " <<
				// tempTilePosition.x << "," << tempTilePosition.y << std::endl;

				// std::cout << "k";
				// 해당 지점이 같은 Region 에 속하고 Buildable 한 타일인지 확인
				if (!tempTilePosition.isValid()
						|| !MyBotModule.Broodwar.isBuildable(tempTilePosition.getX(), tempTilePosition.getY(), false)
						|| tempBaseRegion != BWTA
								.getRegion(new Position(bx * Config.TILE_SIZE, by * Config.TILE_SIZE))) {
					// std::cout << "l";

					// BaseLocation 에서 ChokePoint 방향에 대해 오른쪽으로 90도 꺾은 방향의 Back
					// Yard : 데카르트 좌표계에서
					// (cos(t-90) = sin(t), sin(t-90) = - cos(t))
					bx = tempBaseLocation.getTilePosition().getX() + (int) (d * Math.sin(theta) / Config.TILE_SIZE);
					by = tempBaseLocation.getTilePosition().getY() + (int) (d * Math.cos(theta) / Config.TILE_SIZE);
					tempTilePosition = new TilePosition(bx, by);

					if (!tempTilePosition.isValid() || !MyBotModule.Broodwar.isBuildable(tempTilePosition.getX(),
							tempTilePosition.getY(), false)) {
						// BaseLocation 에서 ChokePoint 방향에 대해 왼쪽으로 90도 꺾은 방향의
						// Back Yard : 데카르트 좌표계에서
						// (cos(t+90) = -sin(t), sin(t+90) = cos(t))
						bx = tempBaseLocation.getTilePosition().getX() - (int) (d * Math.sin(theta) / Config.TILE_SIZE);
						by = tempBaseLocation.getTilePosition().getY() - (int) (d * Math.cos(theta) / Config.TILE_SIZE);
						tempTilePosition = new TilePosition(bx, by);

						if (!tempTilePosition.isValid()
								|| !MyBotModule.Broodwar.isBuildable(tempTilePosition.getX(), tempTilePosition.getY(),
										false)
								|| tempBaseRegion != BWTA
										.getRegion(new Position(bx * Config.TILE_SIZE, by * Config.TILE_SIZE))) {

							// BaseLocation 에서 ChokePoint 방향 절반 지점의 Back Yard :
							// 데카르트 좌표계에서 (cos(t), sin(t))
							bx = tempBaseLocation.getTilePosition().getX()
									+ (int) (d * Math.cos(theta) / Config.TILE_SIZE);
							by = tempBaseLocation.getTilePosition().getY()
									- (int) (d * Math.sin(theta) / Config.TILE_SIZE);
							tempTilePosition = new TilePosition(bx, by);
						}

					}
				}
				// std::cout << "z";
				if (tempTilePosition.isValid() == false || MyBotModule.Broodwar.isBuildable(tempTilePosition.getX(),
						tempTilePosition.getY(), false) == false) {
					seedPosition = tempTilePosition.toPosition();
				} else {
					seedPosition = tempBaseLocation.getPosition();
				}
			}
			break;

		case FirstExpansionLocation:
			tempBaseLocation = info.getFirstExpansionLocation(self);
			if (tempBaseLocation != null) {
				seedPosition = tempBaseLocation.getPosition();
			}
			break;

		case FirstChokePoint:
			tempChokePoint = info.getFirstChokePoint(self);
			if (tempChokePoint != null) {
				seedPosition = tempChokePoint.getCenter();
			}
			break;

		case SecondChokePoint:
			tempChokePoint = info.getSecondChokePoint(self);
			if (tempChokePoint != null) {
				seedPosition = tempChokePoint.getCenter();
			}
			break;
		// 0703 - 최혜진 추가 ConstructionPlaceFinder와 동일한 로직
		case SupplyDepotPosition:
			int nx = 0;
			int ny = 0;
			if (isSupplyDepotBuild == false) { // Supply Depot 첫번째 위치 지정인 경우
				// BaseLocation이 맵의 어느 부분에 위치하는지 파악하고 초기값 리턴
				tempBaseLocation = info.getMainBaseLocation(self);
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					tempFirstExpansion = info.getFirstExpansionLocation(self);
					tempChokePoint = info.getSecondChokePoint(self);
					int dx = tempBaseLocation.getX() - tempChokePoint.getCenter().getX();
					int dy = tempBaseLocation.getTilePosition().getY() - tempFirstExpansion.getTilePosition().getY();
					numberOfSupply = 1;
					// 0722 - 최혜진 수정 초기 좌표 설정
					// 0723 - 최혜진 수정 좌표 이상 해결
					if (dx < 0 && dy < 0) { // BaseLocation이 좌상단 위치
						nx = leftcornerX = 0;
						ny = uppercornerY = 0;
						locationOfBase = 1;
					} else if (dx > 0 && dy < 0) { // BaseLocation이 우상단 위치
						nx = rightcornerX = 125;
						ny = uppercornerY = 0;
						locationOfBase = 2;
					} else if (dx < 0 && dy > 0) { // BaseLocation이 좌하단 위치
						// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
						nx = leftcornerX = 9;
						ny = lowercornerY = 125;
						locationOfBase = 3;
					} else if (dx > 0 && dy > 0) { // BaseLocation이 우하단 위치
						// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
						nx = rightcornerX = 119;
						ny = lowercornerY = 125;
						locationOfBase = 4;
					}
				} else {
					// 0726 - 최혜진 추가 투혼 맵 적용
					tempFirstExpansion = info.getFirstExpansionLocation(self);
					tempChokePoint = info.getSecondChokePoint(self);
					int dx = tempBaseLocation.getX() - tempChokePoint.getCenter().getX();
					int dy = tempBaseLocation.getTilePosition().getY() - tempFirstExpansion.getTilePosition().getY();
					numberOfSupply = 1;
					// 0722 - 최혜진 수정 초기 좌표 설정
					// 0723 - 최혜진 수정 좌표 이상 해결
					if (dx < 0 && dy < 0) { // BaseLocation이 좌상단 위치
						nx = leftcornerX = 0;
						ny = uppercornerY = 0;
						locationOfBase = 1;
					} else if (dx > 0 && dy < 0) { // BaseLocation이 우상단 위치
						nx = rightcornerX = 125;
						ny = uppercornerY = 0;
						locationOfBase = 2;
					} else if (dx < 0 && dy > 0) { // BaseLocation이 좌하단 위치
						// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
						nx = leftcornerX = 6;
						ny = lowercornerY = 125;
						locationOfBase = 3;
					} else if (dx > 0 && dy > 0) { // BaseLocation이 우하단 위치
						// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
						nx = rightcornerX = 119;
						ny = lowercornerY = 125;
						locationOfBase = 4;
					}
				}
				isSupplyDepotBuild = true;
				// System.out.println(locationOfBase + " " + nx + " " + ny);

			} else { // 첫번째가 아닌 경우
				numberOfSupply++;
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					// 0722 - 최혜진 수정 Supply Depot 짓는 방식 변경
					if (locationOfBase == 1) {
						if (numberOfSupply % 8 == 1) {
							uppercornerY = uppercornerY + 2;
							leftcornerX = 0;
						} else {
							leftcornerX = leftcornerX + 3;
						}
						nx = leftcornerX;
						ny = uppercornerY;
					} else if (locationOfBase == 2) {
						if (numberOfSupply % 8 == 1) {
							uppercornerY = uppercornerY + 2;
							rightcornerX = 125;
						} else {
							rightcornerX = rightcornerX - 3;
						}
						nx = rightcornerX;
						ny = uppercornerY;
					} else if (locationOfBase == 3) {
						if (numberOfSupply % 7 == 1) {
							lowercornerY = lowercornerY - 2;
							// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
							leftcornerX = 9;
						} else {
							leftcornerX = leftcornerX + 3;
						}
						nx = leftcornerX;
						ny = lowercornerY;
					} else if (locationOfBase == 4) {
						if (numberOfSupply % 7 == 1) {
							lowercornerY = lowercornerY - 2;
							// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
							rightcornerX = 119;
						} else {
							rightcornerX = rightcornerX - 3;
						}
						nx = rightcornerX;
						ny = lowercornerY;
					}

				} else {
					// 0726 - 최혜진 추가 투혼 맵 적용
					// 0722 - 최혜진 수정 Supply Depot 짓는 방식 변경
					if (locationOfBase == 1) {
						if (numberOfSupply % 6 == 1) {
							uppercornerY = uppercornerY + 2;
							leftcornerX = 0;
						} else {
							leftcornerX = leftcornerX + 3;
						}
						nx = leftcornerX;
						ny = uppercornerY;
					} else if (locationOfBase == 2) {
						if (numberOfSupply % 8 == 1) {
							uppercornerY = uppercornerY + 2;
							rightcornerX = 125;
						} else {
							rightcornerX = rightcornerX - 3;
						}
						nx = rightcornerX;
						ny = uppercornerY;
					} else if (locationOfBase == 3) {
						if (numberOfSupply % 8 == 1) {
							lowercornerY = lowercornerY - 2;
							// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
							leftcornerX = 6;
						} else {
							leftcornerX = leftcornerX + 3;
						}
						nx = leftcornerX;
						ny = lowercornerY;
					} else if (locationOfBase == 4) {
						if (numberOfSupply % 6 == 1) {
							lowercornerY = lowercornerY - 2;
							// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
							rightcornerX = 119;
						} else {
							rightcornerX = rightcornerX - 3;
						}
						nx = rightcornerX;
						ny = lowercornerY;
					}

				}
			}
			tempTilePosition = new TilePosition(nx, ny);
			// 0724 - 최혜진 추가 해당 위치에 건설이 불가능할 경우 다음 번으로 넘김
			if (MyBotModule.Broodwar.canBuildHere(tempTilePosition, UnitType.Terran_Supply_Depot)) {
				seedPosition = tempTilePosition.toPosition();
			} else {
				seedPosition = getSeedPositionFromSeedLocationStrategy(seedLocationStrategy);
			}
			break;

		case BlockFirstChokePoint:
			int blockx = 0;
			int blocky = 0;
			// 0807 - 최혜진 수정 앞마당 입구 막기 좌표 수정
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				if (enemy.getRace() == Race.Terran) {
					if (expansionOrder == 0) {
						int index = expansionOrder + ((locationOfBase - 1) * 3);
						if (index < 12) {
							blockx = expansionXLocaitonForCircuit[index];
							blocky = expansionYLocaitonForCircuit[index];
							expansionOrder++;
						}
					} else if (expansionOrder == 1) {
						seedPosition = getSeedPositionFromSeedLocationStrategy(
								BuildOrderItem.SeedPositionStrategy.SecondChokePoint);
						expansionOrder++;
						break;
					} else if (expansionOrder == 2) {
						seedPosition = getSeedPositionFromSeedLocationStrategy(
								BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition);
						expansionOrder++;
						break;

					}
				} else {
					int index = expansionOrder + ((locationOfBase - 1) * 3);
					if (index < 12) {
						blockx = expansionXLocaitonForCircuit[index];
						blocky = expansionYLocaitonForCircuit[index];
						expansionOrder++;
					}
				}
			} else {
				if (enemy.getRace() == Race.Terran) {
					if (expansionOrder == 1) {
						seedPosition = getSeedPositionFromSeedLocationStrategy(
								BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition);
					} else if (expansionOrder == 2) {
						seedPosition = getSeedPositionFromSeedLocationStrategy(
								BuildOrderItem.SeedPositionStrategy.SecondChokePoint);
					}
					expansionOrder++;
					break;
				}
				int index = expansionOrder + ((locationOfBase - 1) * 3);
				if (index < 12) {
					blockx = expansionXLocaitonForSpirit[index];
					blocky = expansionYLocaitonForSpirit[index];
					expansionOrder++;
				}
			}
			tempTilePosition = new TilePosition(blockx, blocky);
			seedPosition = tempTilePosition.toPosition();
			break;

		// 0723 - 최혜진 추가 4드론 시 Bunker 건설 전략
		case BunkerForZerg:
			int bunkerx = 0;
			int bunkery = 0;
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				if (locationOfBase == 1) {
					bunkerx = 7;
					bunkery = 12;
				} else if (locationOfBase == 2) {
					bunkerx = 117;
					bunkery = 12;
				} else if (locationOfBase == 3) {
					bunkerx = 7;
					bunkery = 115;
				} else if (locationOfBase == 4) {
					bunkerx = 117;
					bunkery = 115;
				}
			} else {
				// 0726 - 최혜진 추가 투혼 맵 적용
				if (locationOfBase == 1) {
					bunkerx = 7;
					bunkery = 9;
				} else if (locationOfBase == 2) {
					bunkerx = 117;
					bunkery = 10;
				} else if (locationOfBase == 3) {
					bunkerx = 7;
					bunkery = 113;
				} else if (locationOfBase == 4) {
					bunkerx = 117;
					bunkery = 114;
				}
			}
			tempTilePosition = new TilePosition(bunkerx, bunkery);
			seedPosition = tempTilePosition.toPosition();
			break;

		// 0729 - 최혜진 추가 본진 및 앞마당 방어를 위한 Turret 건설 전략
		// 0806 - 최혜진 수정 Turret이 두번째 길목에 가장 먼저 건설되도록 변경
		// 0807 - 최혜진 수정 앞마당 Command Center 옆에 터렛 추가
		// 0808 - 최혜진 수정 앞마당 Command Center 옆 터렛을 Rally Point로 변경
		case TurretAround:
			TilePosition desiredPosition = TilePosition.None;
			int turretx = 0;
			int turrety = 0;
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				if (locationOfBase == 1) {
					if (numberOfTurretBuilt % 5 == 1) {
						TilePosition tempRallyPoint = stmgr.getRallyPosition();
						if (tempRallyPoint != null) {
							desiredPosition = ConstructionPlaceFinder.Instance()
									.getBuildLocationNear(UnitType.Terran_Missile_Turret, tempRallyPoint);
							turretx = desiredPosition.getX();
							turrety = desiredPosition.getY();
						}
					} else {
						turretx = turretXLocationForCircuit[numberOfTurretBuilt % 5];
						turrety = turretYLocationForCircuit[numberOfTurretBuilt % 5];
					}
				} else if (locationOfBase == 2) {
					if (numberOfTurretBuilt % 5 == 1) {
						TilePosition tempRallyPoint = stmgr.getRallyPosition();
						if (tempRallyPoint != null) {
							desiredPosition = ConstructionPlaceFinder.Instance()
									.getBuildLocationNear(UnitType.Terran_Missile_Turret, tempRallyPoint);
							turretx = desiredPosition.getX();
							turrety = desiredPosition.getY();
						}
					} else {
						turretx = turretXLocationForCircuit[(numberOfTurretBuilt % 5) + 5];
						turrety = turretYLocationForCircuit[(numberOfTurretBuilt % 5) + 5];
					}
				} else if (locationOfBase == 3) {
					if (numberOfTurretBuilt % 5 == 1) {
						TilePosition tempRallyPoint = stmgr.getRallyPosition();
						if (tempRallyPoint != null) {
							desiredPosition = ConstructionPlaceFinder.Instance()
									.getBuildLocationNear(UnitType.Terran_Missile_Turret, tempRallyPoint);
							turretx = desiredPosition.getX();
							turrety = desiredPosition.getY();
						}
					} else {
						turretx = turretXLocationForCircuit[(numberOfTurretBuilt % 5) + 10];
						turrety = turretYLocationForCircuit[(numberOfTurretBuilt % 5) + 10];
					}
				} else if (locationOfBase == 4) {
					if (numberOfTurretBuilt % 5 == 1) {
						TilePosition tempRallyPoint = stmgr.getRallyPosition();
						if (tempRallyPoint != null) {
							desiredPosition = ConstructionPlaceFinder.Instance()
									.getBuildLocationNear(UnitType.Terran_Missile_Turret, tempRallyPoint);
							turretx = desiredPosition.getX();
							turrety = desiredPosition.getY();
						}
					} else {
						turretx = turretXLocationForCircuit[(numberOfTurretBuilt % 5) + 15];
						turrety = turretYLocationForCircuit[(numberOfTurretBuilt % 5) + 15];
					}

				}
			} else {
				// 0802 - 최혜진 수정 투혼 맵에 Turret 좌표 지정
				// 0805 - 최혜진 수정
				if (locationOfBase == 1) {
					if (numberOfTurretBuilt % 5 == 0) {
						tempChokePoint = info.getSecondChokePoint(self);
						if (tempChokePoint != null) {
							desiredPosition = ConstructionPlaceFinder.Instance().getBuildLocationNear(
									UnitType.Terran_Missile_Turret, tempChokePoint.getCenter().toTilePosition());
							turretx = desiredPosition.getX();
							turrety = desiredPosition.getY();
						}
					} else {
						turretx = turretXLocationForSpirit[numberOfTurretBuilt % 5];
						turrety = turretYLocationForSpirit[numberOfTurretBuilt % 5];
					}
				} else if (locationOfBase == 2) {
					if (numberOfTurretBuilt % 5 == 0) {
						tempChokePoint = info.getSecondChokePoint(self);
						if (tempChokePoint != null) {
							desiredPosition = ConstructionPlaceFinder.Instance().getBuildLocationNear(
									UnitType.Terran_Missile_Turret, tempChokePoint.getCenter().toTilePosition());
							turretx = desiredPosition.getX();
							turrety = desiredPosition.getY();
						}
					} else {
						turretx = turretXLocationForSpirit[(numberOfTurretBuilt % 5) + 5];
						turrety = turretYLocationForSpirit[(numberOfTurretBuilt % 5) + 5];
					}
				} else if (locationOfBase == 3) {
					if (numberOfTurretBuilt % 5 == 0) {
						tempChokePoint = info.getSecondChokePoint(self);
						if (tempChokePoint != null) {
							desiredPosition = ConstructionPlaceFinder.Instance().getBuildLocationNear(
									UnitType.Terran_Missile_Turret, tempChokePoint.getCenter().toTilePosition());
							turretx = desiredPosition.getX();
							turrety = desiredPosition.getY();
						}
					} else {
						turretx = turretXLocationForSpirit[(numberOfTurretBuilt % 5) + 10];
						turrety = turretYLocationForSpirit[(numberOfTurretBuilt % 5) + 10];
					}
				} else if (locationOfBase == 4) {
					if (numberOfTurretBuilt % 5 == 0) {
						tempChokePoint = info.getSecondChokePoint(self);
						if (tempChokePoint != null) {
							desiredPosition = ConstructionPlaceFinder.Instance().getBuildLocationNear(
									UnitType.Terran_Missile_Turret, tempChokePoint.getCenter().toTilePosition());
							turretx = desiredPosition.getX();
							turrety = desiredPosition.getY();
						}
					} else {
						turretx = turretXLocationForSpirit[(numberOfTurretBuilt % 5) + 15];
						turrety = turretYLocationForSpirit[(numberOfTurretBuilt % 5) + 15];
					}

				}
			}
			tempTilePosition = new TilePosition(turretx, turrety);
			seedPosition = tempTilePosition.toPosition();
			numberOfTurretBuilt++;
			break;

		// 0730 - 최혜진 추가 본진 Factory 효율적 배치를 위한 건설 전략
		// 0801 - 최혜진 수정 Factory 좌표 값 지정 및 코드 단순화
		case FactoryInMainBaseLocation:
			int fx = 0;
			int fy = 0;
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				if (locationOfBase == 1) {
					int numberOfFactoryBuiltInt = numberOfFactoryBuilt / 2;
					if (numberOfFactoryBuiltInt < 8) {
						fx = FactoryXLocationForCircuit[numberOfFactoryBuiltInt];
						fy = FactoryYLocationForCircuit[numberOfFactoryBuilt % 2];
					}
				} else if (locationOfBase == 2) {
					int numberOfFactoryBuiltInt = (numberOfFactoryBuilt / 2) + 4;
					if (numberOfFactoryBuiltInt < 8) {
						fx = FactoryXLocationForCircuit[numberOfFactoryBuiltInt];
						fy = FactoryYLocationForCircuit[numberOfFactoryBuilt % 2];
					}
				} else if (locationOfBase == 3) {
					int numberOfFactoryBuiltInt = numberOfFactoryBuilt / 2;
					if (numberOfFactoryBuiltInt < 8) {
						fx = FactoryXLocationForCircuit[numberOfFactoryBuiltInt];
						fy = FactoryYLocationForCircuit[(numberOfFactoryBuilt % 2) + 2];
					}
				} else if (locationOfBase == 4) {
					int numberOfFactoryBuiltInt = (numberOfFactoryBuilt / 2) + 4;
					if (numberOfFactoryBuiltInt < 8) {
						fx = FactoryXLocationForCircuit[numberOfFactoryBuiltInt];
						fy = FactoryYLocationForCircuit[(numberOfFactoryBuilt % 2) + 2];
					}
				}
			} else {
				// 0802 - 최혜진 수정 투혼 맵에 Factory 좌표 지정
				// 0806 - 최혜진 수정 투혼 맵 Factory 입구와 가깝게 좌표 수정
				if (locationOfBase == 1) {
					int numberOfFactoryBuiltInt = numberOfFactoryBuilt / 2;
					if (numberOfFactoryBuiltInt < 16) {
						fx = FactoryXLocationForSpirit[numberOfFactoryBuiltInt];
						fy = FactoryYLocationForSpirit[numberOfFactoryBuilt % 2];
					}
				} else if (locationOfBase == 2) {
					int numberOfFactoryBuiltInt = (numberOfFactoryBuilt / 2) + 4;
					if (numberOfFactoryBuiltInt < 16) {
						fx = FactoryXLocationForSpirit[numberOfFactoryBuiltInt];
						fy = FactoryYLocationForSpirit[(numberOfFactoryBuilt % 2) + 2];
					}
				} else if (locationOfBase == 3) {
					int numberOfFactoryBuiltInt = (numberOfFactoryBuilt / 2) + 8;
					if (numberOfFactoryBuiltInt < 16) {
						fx = FactoryXLocationForSpirit[numberOfFactoryBuiltInt];
						fy = FactoryYLocationForSpirit[(numberOfFactoryBuilt % 2) + 4];
					}
				} else if (locationOfBase == 4) {
					int numberOfFactoryBuiltInt = (numberOfFactoryBuilt / 2) + 12;
					if (numberOfFactoryBuiltInt < 16) {
						fx = FactoryXLocationForSpirit[numberOfFactoryBuiltInt];
						fy = FactoryYLocationForSpirit[(numberOfFactoryBuilt % 2) + 6];
					}
				}
			}
			tempTilePosition = new TilePosition(fx, fy);
			seedPosition = tempTilePosition.toPosition();
			numberOfFactoryBuilt++;
			break;

		// 0730 - 최혜진 추가 본진 Factory와 Supply Depot 피해서 건설하기 위한 전략
		// 0808 - 최혜진 서킷맵 좌표 수정
		case OtherInMainBaseLocation:
			tempBaseLocation = info.getMainBaseLocation(self);
			int ox = 0;
			int oy = 0;
			if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
				if (locationOfBase == 1) {
					if (isFirstBuilt == false) {
						isFirstBuilt = true;
						ox = 15;
						oy = 13;
					} else {
						ox = 11;
						oy = 13;
					}
				} else if (locationOfBase == 2) {
					if (isFirstBuilt == false) {
						isFirstBuilt = true;
						ox = 106;
						oy = 13;
					} else {
						ox = 112;
						oy = 13;
					}
				} else if (locationOfBase == 3) {
					if (isFirstBuilt == false) {
						isFirstBuilt = true;
						ox = 15;
						oy = 112;
					} else {
						ox = 11;
						oy = 112;
					}
				} else if (locationOfBase == 4) {
					if (isFirstBuilt == false) {
						isFirstBuilt = true;
						ox = 106;
						oy = 112;
					} else {
						ox = 112;
						oy = 112;
					}
				}
			} else {
				// 0805 - 최혜진 추가 투혼 맵도 동일하게 적용
				if (locationOfBase == 1) {
					if (isFirstBuilt == false) {
						isFirstBuilt = true;
						ox = tempBaseLocation.getTilePosition().getX() + 8;
						oy = tempBaseLocation.getTilePosition().getY() - 2;
					} else {
						ox = tempBaseLocation.getTilePosition().getX() + 8;
						oy = tempBaseLocation.getTilePosition().getY() + 2;
					}
				} else if (locationOfBase == 2) {
					if (isFirstBuilt == false) {
						isFirstBuilt = true;
						ox = tempBaseLocation.getTilePosition().getX() - 10;
						oy = tempBaseLocation.getTilePosition().getY() - 2;
					} else {
						ox = tempBaseLocation.getTilePosition().getX() - 10;
						oy = tempBaseLocation.getTilePosition().getY() + 2;
					}
				} else if (locationOfBase == 3) {
					if (isFirstBuilt == false) {
						isFirstBuilt = true;
						ox = tempBaseLocation.getTilePosition().getX() + 8;
						oy = tempBaseLocation.getTilePosition().getY() - 2;
					} else {
						ox = tempBaseLocation.getTilePosition().getX() + 8;
						oy = tempBaseLocation.getTilePosition().getY() + 2;
					}
				} else if (locationOfBase == 4) {
					if (isFirstBuilt == false) {
						isFirstBuilt = true;
						ox = tempBaseLocation.getTilePosition().getX() - 10;
						oy = tempBaseLocation.getTilePosition().getY() - 2;
					} else {
						ox = tempBaseLocation.getTilePosition().getX() - 10;
						oy = tempBaseLocation.getTilePosition().getY() + 2;
					}
				}
			}
			tempTilePosition = new TilePosition(ox, oy);
			seedPosition = tempTilePosition.toPosition();
			break;
		default:
			break;
		}

		return seedPosition;

		// BasicBot 1.1 Patch End
		// //////////////////////////////////////////////////
	}

	/// buildQueue 의 Dead lock 여부를 판단하기 위해, 가장 우선순위가 높은 BuildOrderItem 의
	/// producer 가
	/// 존재하게될 것인지 여부를 리턴합니다
	public boolean isProducerWillExist(UnitType producerType) {
		boolean isProducerWillExist = true;

		if (self.completedUnitCount(producerType) == 0 && self.incompleteUnitCount(producerType) == 0) {
			// producer 가 건물 인 경우 : 건물이 건설 중인지 추가 파악
			// 만들려는 unitType = Addon 건물. Lair. Hive. Greater Spire. Sunken
			// Colony. Spore Colony. 프로토스 및 테란의 지상유닛 / 공중유닛.
			if (producerType.isBuilding()) {
				if (ConstructionManager.Instance().getConstructionQueueItemCount(producerType, null) == 0) {
					isProducerWillExist = false;
				}
			}
			// producer 가 건물이 아닌 경우 : producer 가 생성될 예정인지 추가 파악
			// producerType : 일꾼. Larva. Hydralisk, Mutalisk
			else {
				// Larva 는 시간이 지나면 Hatchery, Lair, Hive 로부터 생성되기 때문에 해당 건물이 있는지
				// 추가 파악
				if (producerType == UnitType.Zerg_Larva) {
					if (self.completedUnitCount(UnitType.Zerg_Hatchery) == 0
							&& self.incompleteUnitCount(UnitType.Zerg_Hatchery) == 0
							&& self.completedUnitCount(UnitType.Zerg_Lair) == 0
							&& self.incompleteUnitCount(UnitType.Zerg_Lair) == 0
							&& self.completedUnitCount(UnitType.Zerg_Hive) == 0
							&& self.incompleteUnitCount(UnitType.Zerg_Hive) == 0) {
						if (ConstructionManager.Instance().getConstructionQueueItemCount(UnitType.Zerg_Hatchery,
								null) == 0
								&& ConstructionManager.Instance().getConstructionQueueItemCount(UnitType.Zerg_Lair,
										null) == 0
								&& ConstructionManager.Instance().getConstructionQueueItemCount(UnitType.Zerg_Hive,
										null) == 0) {
							isProducerWillExist = false;
						}
					}
				}
				// Hydralisk, Mutalisk 는 Egg 로부터 생성되기 때문에 추가 파악
				else if (producerType.getRace() == Race.Zerg) {
					boolean isInEgg = false;
					for (Unit unit : self.getUnits()) {
						if (unit.getType() == UnitType.Zerg_Egg && unit.getBuildType() == producerType) {
							isInEgg = true;
						}
						// 갓태어난 유닛은 아직 반영안되어있을 수 있어서, 추가 카운트를 해줘야함
						if (unit.getType() == producerType && unit.isConstructing()) {
							isInEgg = true;
						}
					}
					if (isInEgg == false) {
						isProducerWillExist = false;
					}
				} else {
					isProducerWillExist = false;
				}
			}
		}

		return isProducerWillExist;
	}

	public void checkBuildOrderQueueDeadlockAndAndFixIt() {
		// 빌드오더를 수정할 수 있는 프레임인지 먼저 판단한다
		// this will be true if any unit is on the first frame if it's training
		// time remaining
		// this can cause issues for the build order search system so don't plan
		// a search on these frames
		boolean canPlanBuildOrderNow = true;
		for (final Unit unit : self.getUnits()) {
			if (unit.getRemainingTrainTime() == 0) {
				continue;
			}

			UnitCommand unitCommand = unit.getLastCommand();
			if (unitCommand != null) {

				UnitCommandType unitCommandType = unitCommand.getUnitCommandType();
				if (unitCommandType != UnitCommandType.None) {
					if (unitCommand.getUnit() != null) {
						UnitType trainType = unitCommand.getUnit().getType();
						if (unit.getRemainingTrainTime() == trainType.buildTime()) {
							canPlanBuildOrderNow = false;
							break;
						}
					}
				}
			}

		}
		if (!canPlanBuildOrderNow) {
			return;
		}

		// BuildQueue 의 HighestPriority 에 있는 BuildQueueItem 이 skip 불가능한 것인데,
		// 선행조건이 충족될 수 없거나, 실행이 앞으로도 계속 불가능한 경우, dead lock 이 발생한다
		// 선행 건물을 BuildQueue에 추가해넣을지, 해당 BuildQueueItem 을 삭제할지 전략적으로 판단해야 한다
		BuildOrderQueue buildQueue = BuildManager.Instance().getBuildQueue();
		if (!buildQueue.isEmpty()) {
			BuildOrderItem currentItem = buildQueue.getHighestPriorityItem();

			// if (buildQueue.canSkipCurrentItem() == false)
			if (currentItem.blocking == true) {
				boolean isDeadlockCase = false;

				// producerType을 먼저 알아낸다
				UnitType producerType = currentItem.metaType.whatBuilds();

				// 건물이나 유닛의 경우
				if (currentItem.metaType.isUnit()) {
					UnitType unitType = currentItem.metaType.getUnitType();
					TechType requiredTechType = unitType.requiredTech();
					final Map<UnitType, Integer> requiredUnits = unitType.requiredUnits();
					// 건물을 생산하는 유닛이나, 유닛을 생산하는 건물이 존재하지 않고, 건설 예정이지도 않으면 dead
					// lock
					if (isProducerWillExist(producerType) == false) {
						isDeadlockCase = true;
					}

					// Refinery 건물의 경우, Refinery 가 건설되지 않은 Geyser가 있는 경우에만 가능
					if (!isDeadlockCase && unitType == UnitType.Terran_Refinery) {
						boolean hasAvailableGeyser = true;

						// Refinery가 지어질 수 있는 장소를 찾아본다
						TilePosition testLocation = getDesiredPosition(unitType, currentItem.seedLocation,
								currentItem.seedLocationStrategy);

						// Refinery 를 지으려는 장소를 찾을 수 없으면 dead lock
						if (testLocation == TilePosition.None || testLocation == TilePosition.Invalid
								|| testLocation.isValid() == false) {
							System.out
									.println("Build Order Dead lock case . Cann't find place to construct " + unitType); // C++
																															// :
																															// unitType.getName()
							hasAvailableGeyser = false;
						} else {
							// Refinery 를 지으려는 장소에 Refinery 가 이미 건설되어 있다면 dead
							// lock
							for (Unit u : MyBotModule.Broodwar.getUnitsOnTile(testLocation)) {
								if (u.getType().isRefinery() && u.exists()) {
									hasAvailableGeyser = false;

									// BasicBot 1.1 Patch Start
									// ////////////////////////////////////////////////
									// 콘솔 출력 추가. 하지 않아도 됨

									System.out.println(
											"Build Order Dead lock case -> Refinery Building was built already at "
													+ testLocation.getX() + ", " + testLocation.getY());

									// BasicBot 1.1 Patch End
									// ////////////////////////////////////////////////

									break;
								}
							}
						}

						if (hasAvailableGeyser == false) {
							isDeadlockCase = true;
						}
					}

					// 선행 기술 리서치가 되어있지 않고, 리서치 중이지도 않으면 dead lock
					if (!isDeadlockCase && requiredTechType != TechType.None) {
						if (self.hasResearched(requiredTechType) == false) {
							if (self.isResearching(requiredTechType) == false) {
								isDeadlockCase = true;
							}
						}
					}

					Iterator<UnitType> it = requiredUnits.keySet().iterator();
					// 선행 건물/유닛이 있는데
					if (!isDeadlockCase && requiredUnits.size() > 0) {
						// for (Unit u : it)
						while (it.hasNext()) {
							UnitType requiredUnitType = it.next(); // C++ :
																	// u.first;

							if (requiredUnitType != UnitType.None) {

								// BasicBot 1.1 Patch Start
								// ////////////////////////////////////////////////
								// Zerg_Mutalisk 나 Zerg_Scourge 를 만들려고하는데
								// Zerg_Greater_Spire 만 있는 경우 deadlock 으로
								// 판정하는 버그 수정

								// 만들려는 유닛이 Zerg_Mutalisk 이거나 Zerg_Scourge 이고,
								// 선행 유닛이 Zerg_Spire 인 경우,
								// Zerg_Greater_Spire 가 있으면 dead lock 이 아니다
								if ((unitType == UnitType.Zerg_Mutalisk || unitType == UnitType.Zerg_Scourge)
										&& requiredUnitType == UnitType.Zerg_Spire
										&& self.allUnitCount(UnitType.Zerg_Greater_Spire) > 0) {
									isDeadlockCase = false;
								} else

								// BasicBot 1.1 Patch End
								// //////////////////////////////////////////////////

								// 선행 건물 / 유닛이 존재하지 않고, 생산 중이지도 않고
								if (self.completedUnitCount(requiredUnitType) == 0
										&& self.incompleteUnitCount(requiredUnitType) == 0) {
									// 선행 건물이 건설 예정이지도 않으면 dead lock
									if (requiredUnitType.isBuilding()) {
										if (ConstructionManager.Instance()
												.getConstructionQueueItemCount(requiredUnitType, null) == 0) {
											isDeadlockCase = true;
										}
									}
									// 선행 유닛이 Larva 인 Zerg 유닛의 경우, Larva,
									// Hatchery, Lair, Hive 가 하나도 존재하지 않고, 건설
									// 예정이지 않은 경우에 dead lock
									else if (requiredUnitType == UnitType.Zerg_Larva) {
										if (self.completedUnitCount(UnitType.Zerg_Hatchery) == 0
												&& self.incompleteUnitCount(UnitType.Zerg_Hatchery) == 0
												&& self.completedUnitCount(UnitType.Zerg_Lair) == 0
												&& self.incompleteUnitCount(UnitType.Zerg_Lair) == 0
												&& self.completedUnitCount(UnitType.Zerg_Hive) == 0
												&& self.incompleteUnitCount(UnitType.Zerg_Hive) == 0) {
											if (ConstructionManager.Instance()
													.getConstructionQueueItemCount(UnitType.Zerg_Hatchery, null) == 0
													&& ConstructionManager.Instance().getConstructionQueueItemCount(
															UnitType.Zerg_Lair, null) == 0
													&& ConstructionManager.Instance().getConstructionQueueItemCount(
															UnitType.Zerg_Hive, null) == 0) {
												isDeadlockCase = true;
											}
										}
									}
								}
							}
						}
					}

					// 건물이 아닌 지상/공중 유닛인 경우, 서플라이가 400 꽉 찼으면 dead lock
					if (!isDeadlockCase && !unitType.isBuilding() && self.supplyTotal() == 400
							&& self.supplyUsed() + unitType.supplyRequired() > 400) {
						isDeadlockCase = true;
					}

					// 건물이 아닌 지상/공중 유닛인데, 서플라이가 부족하면 dead lock 상황이 되긴 하지만,
					// 이 경우는 빌드를 취소하기보다는, StrategyManager 등에서 서플라이 빌드를 추가함으로써
					// 풀도록 한다
					if (!isDeadlockCase && !unitType.isBuilding()
							&& self.supplyUsed() + unitType.supplyRequired() > self.supplyTotal()) {
						// isDeadlockCase = true;
					}

					// Pylon 이 해당 지역 주위에 먼저 지어져야 하는데, Pylon 이 해당 지역 주위에 없고,
					// 예정되어있지도 않으면 dead lock
					if (!isDeadlockCase && unitType.isBuilding() && unitType.requiresPsi()
							&& currentItem.seedLocationStrategy == BuildOrderItem.SeedPositionStrategy.SeedPositionSpecified) {

						boolean hasFoundPylon = false;
						List<Unit> ourUnits = MyBotModule.Broodwar
								.getUnitsInRadius(currentItem.seedLocation.toPosition(), 4 * Config.TILE_SIZE);

						for (Unit u : ourUnits) {
							if (u.getPlayer() == self && u.getType() == UnitType.Protoss_Pylon) {
								hasFoundPylon = true;
							}
						}

						if (hasFoundPylon == false) {
							isDeadlockCase = true;
						}
					}

					// Creep 이 해당 지역 주위에 Hatchery나 Creep Colony 등을 통해 먼저 지어져야
					// 하는데, 해당 지역 주위에 지어지지 않고
					// 있으면 dead lock
					if (!isDeadlockCase && unitType.isBuilding() && unitType.requiresCreep()
							&& currentItem.seedLocationStrategy == BuildOrderItem.SeedPositionStrategy.SeedPositionSpecified) {
						boolean hasFoundCreepGenerator = false;
						List<Unit> ourUnits = MyBotModule.Broodwar
								.getUnitsInRadius(currentItem.seedLocation.toPosition(), 4 * Config.TILE_SIZE);

						for (Unit u : ourUnits) {
							if (u.getPlayer() == self && (u.getType() == UnitType.Zerg_Hatchery
									|| u.getType() == UnitType.Zerg_Lair || u.getType() == UnitType.Zerg_Hive
									|| u.getType() == UnitType.Zerg_Creep_Colony
									|| u.getType() == UnitType.Zerg_Sunken_Colony
									|| u.getType() == UnitType.Zerg_Spore_Colony)) {
								hasFoundCreepGenerator = true;
							}
						}

						if (hasFoundCreepGenerator == false) {
							isDeadlockCase = true;
						}
					}

				}
				// 테크의 경우, 해당 리서치를 이미 했거나, 이미 하고있거나, 리서치를 하는 건물 및 선행건물이 존재하지않고
				// 건설예정이지도 않으면 dead lock
				else if (currentItem.metaType.isTech()) {
					TechType techType = currentItem.metaType.getTechType();
					UnitType requiredUnitType = techType.requiredUnit();

					if (self.hasResearched(techType) || self.isResearching(techType)) {
						isDeadlockCase = true;
					} else if (self.completedUnitCount(producerType) == 0
							&& self.incompleteUnitCount(producerType) == 0) {
						if (ConstructionManager.Instance().getConstructionQueueItemCount(producerType, null) == 0) {

							// 테크 리서치의 producerType이 Addon 건물인 경우, Addon 건물 건설이
							// 명령 내려졌지만 시작되기 직전에는 getUnits, completedUnitCount,
							// incompleteUnitCount 에서 확인할 수 없다
							// producerType의 producerType 건물에 의해 Addon 건물 건설의
							// 명령이 들어갔는지까지 확인해야 한다
							if (producerType.isAddon()) {

								boolean isAddonConstructing = false;

								UnitType producerTypeOfProducerType = producerType.whatBuilds().first;

								if (producerTypeOfProducerType != UnitType.None) {

									for (Unit unit : self.getUnits()) {
										if (unit == null)
											continue;
										if (unit.getType() != producerTypeOfProducerType) {
											continue;
										}

										// 모건물이 완성되어있고, 모건물이 해당 Addon 건물을 건설중인지
										// 확인한다
										if (unit.isCompleted() && unit.isConstructing()
												&& unit.getBuildType() == producerType) {
											isAddonConstructing = true;
											break;
										}
									}
								}

								if (isAddonConstructing == false) {
									isDeadlockCase = true;
								}
							} else {
								isDeadlockCase = true;
							}
						}
					} else if (requiredUnitType != UnitType.None) {
						if (self.completedUnitCount(requiredUnitType) == 0
								&& self.incompleteUnitCount(requiredUnitType) == 0) {
							if (ConstructionManager.Instance().getConstructionQueueItemCount(requiredUnitType,
									null) == 0) {
								isDeadlockCase = true;
							}
						}
					}
				}
				// 업그레이드의 경우, 해당 업그레이드를 이미 했거나, 이미 하고있거나, 업그레이드를 하는 건물 및 선행건물이
				// 존재하지도 않고 건설예정이지도 않으면 dead lock
				else if (currentItem.metaType.isUpgrade()) {
					UpgradeType upgradeType = currentItem.metaType.getUpgradeType();
					int maxLevel = self.getMaxUpgradeLevel(upgradeType);
					int currentLevel = self.getUpgradeLevel(upgradeType);
					UnitType requiredUnitType = upgradeType.whatsRequired();

					if (currentLevel >= maxLevel || self.isUpgrading(upgradeType)) {
						isDeadlockCase = true;
					} else if (self.completedUnitCount(producerType) == 0
							&& self.incompleteUnitCount(producerType) == 0) {
						if (ConstructionManager.Instance().getConstructionQueueItemCount(producerType, null) == 0) {

							// 업그레이드의 producerType이 Addon 건물인 경우, Addon 건물 건설이
							// 시작되기 직전에는 getUnits, completedUnitCount,
							// incompleteUnitCount 에서 확인할 수 없다
							// producerType의 producerType 건물에 의해 Addon 건물 건설이
							// 시작되었는지까지 확인해야 한다
							if (producerType.isAddon()) {

								boolean isAddonConstructing = false;

								UnitType producerTypeOfProducerType = producerType.whatBuilds().first;

								if (producerTypeOfProducerType != UnitType.None) {

									for (Unit unit : self.getUnits()) {
										if (unit == null)
											continue;
										if (unit.getType() != producerTypeOfProducerType) {
											continue;
										}
										// 모건물이 완성되어있고, 모건물이 해당 Addon 건물을 건설중인지
										// 확인한다
										if (unit.isCompleted() && unit.isConstructing()
												&& unit.getBuildType() == producerType) {
											isAddonConstructing = true;
											break;
										}
									}
								}

								if (isAddonConstructing == false) {
									isDeadlockCase = true;
								}
							} else {
								isDeadlockCase = true;
							}
						}
					} else if (requiredUnitType != UnitType.None) {
						if (self.completedUnitCount(requiredUnitType) == 0
								&& self.incompleteUnitCount(requiredUnitType) == 0) {
							if (ConstructionManager.Instance().getConstructionQueueItemCount(requiredUnitType,
									null) == 0) {
								isDeadlockCase = true;
							}
						}
					}
				}

				if (!isDeadlockCase) {
					// producerID 를 지정했는데, 해당 ID 를 가진 유닛이 존재하지 않으면 dead lock
					if (currentItem.producerID != -1) {
						boolean isProducerAlive = false;
						for (Unit unit : self.getUnits()) {
							if (unit != null && unit.getID() == currentItem.producerID && unit.exists()
									&& unit.getHitPoints() > 0) {
								isProducerAlive = true;
								break;
							}
						}
						if (isProducerAlive == false) {
							isDeadlockCase = true;
						}
					}
				}

				if (isDeadlockCase) {
					System.out.println(
							"Build Order Dead lock case . remove BuildOrderItem " + currentItem.metaType.getName());

					buildQueue.removeCurrentItem();
				}

			}
		}
	}
};