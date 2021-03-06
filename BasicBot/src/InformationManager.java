import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import bwapi.Player;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Chokepoint;
import bwta.Region;

/// 게임 상황정보 중 일부를 자체 자료구조 및 변수들에 저장하고 업데이트하는 class<br>
/// 현재 게임 상황정보는 BWAPI::Broodwar 를 조회하여 파악할 수 있지만, 과거 게임 상황정보는 BWAPI::Broodwar 를 통해 조회가 불가능하기 때문에 InformationManager에서 별도 관리하도록 합니다<br>
/// 또한, BWAPI::Broodwar 나 BWTA 등을 통해 조회할 수 있는 정보이지만 전처리 / 별도 관리하는 것이 유용한 것도 InformationManager에서 별도 관리하도록 합니다
public class InformationManager {
	private static InformationManager instance = new InformationManager();

	public Player selfPlayer; /// < 아군 Player
	public Player enemyPlayer; /// < 적군 Player
	public Race selfRace; /// < 아군 Player의 종족
	public Race enemyRace; /// < 적군 Player의 종족

	/// 해당 Player의 주요 건물들이 있는 BaseLocation. <br>
	/// 처음에는 StartLocation 으로 지정. mainBaseLocation 내 모든 건물이 파괴될 경우 재지정<br>
	/// 건물 여부를 기준으로 파악하기 때문에 부적절하게 판단할수도 있습니다
	private Map<Player, BaseLocation> mainBaseLocations = new HashMap<Player, BaseLocation>();

	/// 해당 Player의 mainBaseLocation 이 변경되었는가 (firstChokePoint, secondChokePoint,
	/// firstExpansionLocation 를 재지정 했는가)
	private Map<Player, Boolean> mainBaseLocationChanged = new HashMap<Player, Boolean>();

	/// 해당 Player가 점령하고 있는 Region 이 있는 BaseLocation<br>
	/// 건물 여부를 기준으로 파악하기 때문에 부적절하게 판단할수도 있습니다
	private Map<Player, List<BaseLocation>> occupiedBaseLocations = new HashMap<Player, List<BaseLocation>>();

	/// 해당 Player가 점령하고 있는 Region<br>
	/// 건물 여부를 기준으로 파악하기 때문에 부적절하게 판단할수도 있습니다
	private Map<Player, Set<Region>> occupiedRegions = new HashMap<Player, Set<Region>>();

	/// 해당 Player의 mainBaseLocation 에서 가장 가까운 ChokePoint
	private Map<Player, Chokepoint> firstChokePoint = new HashMap<Player, Chokepoint>();
	/// 해당 Player의 mainBaseLocation 에서 가장 가까운 BaseLocation
	private Map<Player, BaseLocation> firstExpansionLocation = new HashMap<Player, BaseLocation>();
	/// 해당 Player의 mainBaseLocation 에서 두번째로 가까운 (firstChokePoint가 아닌)
	/// ChokePoint<br>
	/// 게임 맵에 따라서, secondChokePoint 는 일반 상식과 다른 지점이 될 수도 있습니다
	private Map<Player, Chokepoint> secondChokePoint = new HashMap<Player, Chokepoint>();

	/// Player - UnitData(각 Unit 과 그 Unit의 UnitInfo 를 Map 형태로 저장하는 자료구조) 를 저장하는
	/// 자료구조 객체
	private Map<Player, UnitData> unitData = new HashMap<Player, UnitData>();

	/// 위험지역
	public DangerousLocation currentDangerousLocation = null;

	/// static singleton 객체를 리턴합니다
	public static InformationManager Instance() {
		return instance;
	}

	public InformationManager() {
		selfPlayer = MyBotModule.Broodwar.self();
		enemyPlayer = MyBotModule.Broodwar.enemy();
		selfRace = selfPlayer.getRace();
		enemyRace = enemyPlayer.getRace();

		unitData.put(selfPlayer, new UnitData());
		unitData.put(enemyPlayer, new UnitData());

		occupiedBaseLocations.put(selfPlayer, new ArrayList<BaseLocation>());
		occupiedBaseLocations.put(enemyPlayer, new ArrayList<BaseLocation>());
		occupiedRegions.put(selfPlayer, new HashSet());
		occupiedRegions.put(enemyPlayer, new HashSet());

		mainBaseLocations.put(selfPlayer, BWTA.getStartLocation(MyBotModule.Broodwar.self()));
		mainBaseLocationChanged.put(selfPlayer, new Boolean(true));

		occupiedBaseLocations.get(selfPlayer).add(mainBaseLocations.get(selfPlayer));
		if (mainBaseLocations.get(selfPlayer) != null) {
			updateOccupiedRegions(BWTA.getRegion(mainBaseLocations.get(selfPlayer).getTilePosition()),
					MyBotModule.Broodwar.self());
		}

		mainBaseLocations.put(enemyPlayer, null);
		mainBaseLocationChanged.put(enemyPlayer, new Boolean(false));

		firstChokePoint.put(selfPlayer, null);
		firstChokePoint.put(enemyPlayer, null);
		firstExpansionLocation.put(selfPlayer, null);
		firstExpansionLocation.put(enemyPlayer, null);
		secondChokePoint.put(selfPlayer, null);
		secondChokePoint.put(enemyPlayer, null);

		updateChokePointAndExpansionLocation();

	}

	/// Unit 및 BaseLocation, ChokePoint 등에 대한 정보를 업데이트합니다
	public void update() {
		updateUnitsInfo();

		// 위험지역 정보 업데이트
		if (MyBotModule.Broodwar.getFrameCount() % 24 == 0) {
			updateDangerousInfo();
		}

		// occupiedBaseLocation 이나 occupiedRegion 은 거의 안바뀌므로 자주 안해도 된다
		if (MyBotModule.Broodwar.getFrameCount() % 120 == 0) {
			updateBaseLocationInfo();
		}
	}

	/// 전체 unit 의 정보를 업데이트 합니다 (UnitType, lastPosition, HitPoint 등)
	public void updateUnitsInfo() {
		// update our units info
		for (Unit unit : MyBotModule.Broodwar.enemy().getUnits()) {
			updateUnitInfo(unit);
		}
		for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
			updateUnitInfo(unit);
		}

		// remove bad enemy units
		if (unitData.get(enemyPlayer) != null) {
			unitData.get(enemyPlayer).removeBadUnits();
		}
		if (unitData.get(selfPlayer) != null) {
			unitData.get(selfPlayer).removeBadUnits();
		}
	}

	/// 해당 unit 의 정보를 업데이트 합니다 (UnitType, lastPosition, HitPoint 등)
	public void updateUnitInfo(Unit unit) {
		try {
			if (!(unit.getPlayer() == selfPlayer || unit.getPlayer() == enemyPlayer)) {
				return;
			}

			if (enemyRace == Race.Unknown && unit.getPlayer() == enemyPlayer) {
				enemyRace = unit.getType().getRace();
			}
			unitData.get(unit.getPlayer()).updateUnitInfo(unit);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/// 위험지역에 대한 정보를 업데이트
	public void updateDangerousInfo() {
		DangerousLocation mostDangerousLoca = new DangerousLocation();
		mostDangerousLoca.setEnemyCnt(0);

		DangerousLocation tempDangerousLocation = null;
		for (BaseLocation baseLocation : occupiedBaseLocations.get(selfPlayer)) {
			tempDangerousLocation = researchForce(baseLocation);
			if (tempDangerousLocation != null) {
				if (tempDangerousLocation.getEnemyCnt() > mostDangerousLoca.getEnemyCnt()) {
					mostDangerousLoca = tempDangerousLocation;
				}
			}
		}
		if (mostDangerousLoca.getEnemyCnt() > 0) {
			currentDangerousLocation = mostDangerousLoca;
		} else {
			currentDangerousLocation = null;
		}
	}

	/// Unit 에 대한 정보를 업데이트합니다
	public void onUnitShow(Unit unit) {
		updateUnitInfo(unit);
	}

	/// Unit 에 대한 정보를 업데이트합니다
	public void onUnitHide(Unit unit) {
		updateUnitInfo(unit);
	}

	/// Unit 에 대한 정보를 업데이트합니다
	public void onUnitCreate(Unit unit) {
		updateUnitInfo(unit);
	}

	/// Unit 에 대한 정보를 업데이트합니다
	public void onUnitComplete(Unit unit) {
		updateUnitInfo(unit);
	}

	/// Unit 에 대한 정보를 업데이트합니다
	public void onUnitMorph(Unit unit) {
		updateUnitInfo(unit);
	}

	/// Unit 에 대한 정보를 업데이트합니다
	public void onUnitRenegade(Unit unit) {
		updateUnitInfo(unit);
	}

	/// Unit 에 대한 정보를 업데이트합니다 <br>
	/// 유닛이 파괴/사망한 경우, 해당 유닛 정보를 삭제합니다
	public void onUnitDestroy(Unit unit) {
		if (unit.getType().isNeutral()) {
			return;
		}

		unitData.get(unit.getPlayer()).removeUnit(unit);
	}

	/// 해당 Player (아군 or 적군) 의 모든 유닛 통계 UnitData 을 리턴합니다
	public final UnitData getUnitData(Player player) {
		return unitData.get(player);
	}

	// 해당 지역에 적 일꾼 유닛이 있다면 적 일꾼 리턴
	public Unit getWorkerInRegion(Region region) {
		Unit targetWorker = null;
		for (Unit enemyUnit : MyBotModule.Broodwar.enemy().getUnits()) {
			if (BWTA.getRegion(enemyUnit.getPosition()) == region && enemyUnit.getType().isWorker()) {
				targetWorker = enemyUnit;
				break;
			}
		}
		return targetWorker;
	}

	/// 0808 수정
	/// 우리 유닛의 시야 내에 적이 있는가 체크하는 메소드
	public boolean isEnemyUnitInSight(Unit targetUnit) {
		if (targetUnit == null)
			return false;

		// 시야 내에 있는 유닛 리스트
		List<Unit> list = targetUnit.getUnitsInRadius(targetUnit.getType().sightRange());

		// 적 유닛이 있는지 확인
		for (Unit unit : list) {
			if (unit == null)
				continue;
			if (unit.getPlayer() == enemyPlayer) {
				if (isCombatUnitType(unit.getType())) {
					return true;
				}
			}
		}

		return false;
	}

	// 0813 추가 - 우리 유닛 시야에 적 지상 유닛이 있는지 체크
	public boolean isGroundEnemyUnitInWeaponRange(Unit targetUnit) {
		if (targetUnit == null)
			return false;

		// 시야 내에 있는 유닛 리스트
		List<Unit> list = targetUnit.getUnitsInRadius(targetUnit.getType().groundWeapon().maxRange());

		// 적 유닛이 있는지 확인
		for (Unit unit : list) {
			if (unit == null)
				continue;
			if (unit.getPlayer() == enemyPlayer) {
				if (isGroundCombatUnitType(unit.getType())) {
					return true;
				}
			}
		}

		return false;
	}

	// 0813 추가 - 우리 유닛 시야에 적 지상 유닛이 있는지 체크
	public boolean isGroundEnemyUnitInSiegeRange(Unit targetUnit, int weight) {
		if (targetUnit == null)
			return false;

		// 시야 내에 있는 유닛 리스트
		List<Unit> list = targetUnit
				.getUnitsInRadius(UnitType.Terran_Siege_Tank_Siege_Mode.groundWeapon().maxRange() + weight);

		// 적 유닛이 있는지 확인
		for (Unit unit : list) {
			if (unit == null)
				continue;
			if (unit.getPlayer() == enemyPlayer) {
				if (isGroundCombatUnitType(unit.getType())) {
					return true;
				}
			}
		}

		return false;
	}

	// 0813 추가 - 지상 유닛
	public boolean isGroundCombatUnitType(UnitType unitType) {
		if (enemyRace == Race.Protoss) {
			if (unitType == UnitType.Protoss_Archon || unitType == UnitType.Protoss_Dark_Archon
					|| unitType == UnitType.Protoss_Dragoon || unitType == UnitType.Protoss_High_Templar
					|| unitType == UnitType.Protoss_Reaver || unitType == UnitType.Protoss_Zealot) {
				return true;
			}
		} else if (enemyRace == Race.Terran) {
			if (unitType == UnitType.Terran_Firebat || unitType == UnitType.Terran_Ghost
					|| unitType == UnitType.Terran_Goliath || unitType == UnitType.Terran_Marine
					|| unitType == UnitType.Terran_Siege_Tank_Siege_Mode
					|| unitType == UnitType.Terran_Siege_Tank_Tank_Mode || unitType == UnitType.Terran_Vulture) {
				return true;
			}
		} else {
			if (unitType == UnitType.Zerg_Broodling || unitType == UnitType.Zerg_Defiler
					|| unitType == UnitType.Zerg_Hydralisk || unitType == UnitType.Zerg_Infested_Terran
					|| unitType == UnitType.Zerg_Lurker || unitType == UnitType.Zerg_Lurker_Egg
					|| unitType == UnitType.Zerg_Ultralisk || unitType == UnitType.Zerg_Zergling) {
				return true;
			}
		}
		return false;
	}

	// 특정 baseLocation을 위험도 체크 가능한 객체로 반환
	public DangerousLocation researchForce(BaseLocation baseLocation) {

		List<Unit> enemyList = MyBotModule.Broodwar.enemy().getUnits();
		DangerousLocation tempDangerLocation = new DangerousLocation();

		int forcePoint = 0;
		int airForcePoint = 0;
		int groundForcePoint = 0;

		for (Unit enemy : enemyList) {
			if (enemy == null || !enemy.exists() || !enemy.isCompleted()) {
				continue;
			}

			if (enemy.getType().isWorker() || enemy.getType().isBuilding()) {
				continue;
			}

			// 유닛이 해당 지역에 들어와있는지 확인
			if (BWTA.getRegion(enemy.getPosition()) == baseLocation.getRegion()) {
				// 전투 유닛인 경우만 고려
				if (isCombatUnitType(enemy.getType())) {
					forcePoint++;
					if (enemy.getType().isFlyer()) {
						airForcePoint++;
					} else {
						groundForcePoint++;
					}
				}
			}
		}

		// 병력이 있을 때만 위험지역으로 return
		if (forcePoint > 0) {
			tempDangerLocation.setBaseLocation(baseLocation);
			tempDangerLocation.setEnemyCnt(forcePoint);
			tempDangerLocation.setGroundCnt(groundForcePoint);
			tempDangerLocation.setAirCnt(airForcePoint);

			if (airForcePoint > 0 && groundForcePoint > 0) {
				tempDangerLocation.setAttackType(DangerousLocation.AttackType.Both);
			} else if (airForcePoint > 0) {
				tempDangerLocation.setAttackType(DangerousLocation.AttackType.Air);
			} else if (groundForcePoint > 0) {
				tempDangerLocation.setAttackType(DangerousLocation.AttackType.Ground);
			}

			return tempDangerLocation;
		}
		// 병력이 없는 경우 null
		else {
			return null;
		}
	}

	public void updateBaseLocationInfo() {
		if (occupiedRegions.get(selfPlayer) != null) {
			occupiedRegions.get(selfPlayer).clear();
		}
		if (occupiedRegions.get(enemyPlayer) != null) {
			occupiedRegions.get(enemyPlayer).clear();
		}
		if (occupiedBaseLocations.get(selfPlayer) != null) {
			occupiedBaseLocations.get(selfPlayer).clear();
		}
		if (occupiedBaseLocations.get(enemyPlayer) != null) {
			occupiedBaseLocations.get(enemyPlayer).clear();
		}

		// enemy 의 startLocation을 아직 모르는 경우
		if (mainBaseLocations.get(enemyPlayer) == null) {
			// how many start locations have we explored
			int exploredStartLocations = 0;
			boolean enemyStartLocationFound = false;

			// an unexplored base location holder
			BaseLocation unexplored = null;

			for (BaseLocation startLocation : BWTA.getStartLocations()) {
				if (existsPlayerBuildingInRegion(BWTA.getRegion(startLocation.getTilePosition()), enemyPlayer)) {
					if (enemyStartLocationFound == false) {
						enemyStartLocationFound = true;
						mainBaseLocations.put(enemyPlayer, startLocation);
						mainBaseLocationChanged.put(enemyPlayer, new Boolean(true));
					}
				}

				if (MyBotModule.Broodwar.isExplored(startLocation.getTilePosition())) {
					// if it's explored, increment
					exploredStartLocations++;
				} else {
					// otherwise set it as unexplored base
					unexplored = startLocation;
				}
			}

			// if we've explored every start location except one, it's the enemy
			if (!enemyStartLocationFound && exploredStartLocations == ((int) BWTA.getStartLocations().size() - 1)) {
				enemyStartLocationFound = true;
				mainBaseLocations.put(enemyPlayer, unexplored);
				mainBaseLocationChanged.put(enemyPlayer, new Boolean(true));
				// C++ : _occupiedBaseLocations[_enemy].push_back(unexplored);
				if (occupiedBaseLocations.get(enemyPlayer) == null) {
					occupiedBaseLocations.put(enemyPlayer, new ArrayList<BaseLocation>());
				}
				occupiedBaseLocations.get(enemyPlayer).add(unexplored);
			}
		}

		// update occupied base location
		// 어떤 Base Location 에는 아군 건물, 적군 건물 모두 혼재해있어서 동시에 여러 Player 가 Occupy 하고
		// 있는 것으로 판정될 수 있다
		for (BaseLocation baseLocation : BWTA.getBaseLocations()) {
			if (hasBuildingAroundBaseLocation(baseLocation, enemyPlayer)) {
				// C++ : _occupiedBaseLocations[_enemy].push_back(baseLocation);
				occupiedBaseLocations.get(enemyPlayer).add(baseLocation);
			}

			if (hasBuildingAroundBaseLocation(baseLocation, selfPlayer)) {
				// C++ : _occupiedBaseLocations[_self].push_back(baseLocation);
				occupiedBaseLocations.get(selfPlayer).add(baseLocation);
			}
		}

		// enemy의 mainBaseLocations을 발견한 후, 그곳에 있는 건물을 모두 파괴한 경우
		// _occupiedBaseLocations 중에서 _mainBaseLocations 를 선정한다
		if (mainBaseLocations.get(enemyPlayer) != null) {

			// BasicBot 1.1 Patch Start
			// ////////////////////////////////////////////////
			// 적 MainBaseLocation 업데이트 로직 버그 수정

			// 적군의 빠른 앞마당 건물 건설 + 아군의 가장 마지막 정찰 방문의 경우,
			// enemy의 mainBaseLocations를 방문안한 상태에서는 건물이 하나도 없다고 판단하여
			// mainBaseLocation 을 변경하는 현상이 발생해서
			// enemy의 mainBaseLocations을 실제 방문했었던 적이 한번은 있어야 한다라는 조건 추가.
			if (MyBotModule.Broodwar.isExplored(mainBaseLocations.get(enemyPlayer).getTilePosition())) {

				if (existsPlayerBuildingInRegion(BWTA.getRegion(mainBaseLocations.get(enemyPlayer).getTilePosition()),
						enemyPlayer) == false) {
					for (BaseLocation loaction : occupiedBaseLocations.get(enemyPlayer)) {
						if (existsPlayerBuildingInRegion(BWTA.getRegion(loaction.getTilePosition()), enemyPlayer)) {
							mainBaseLocations.put(enemyPlayer, loaction);
							mainBaseLocationChanged.put(enemyPlayer, new Boolean(true));
							break;
						}
					}
				}
			}

			// BasicBot 1.1 Patch End
			// //////////////////////////////////////////////////

		}

		// self의 mainBaseLocations에 대해, 그곳에 있는 건물이 모두 파괴된 경우
		// _occupiedBaseLocations 중에서 _mainBaseLocations 를 선정한다
		if (mainBaseLocations.get(selfPlayer) != null) {
			if (existsPlayerBuildingInRegion(BWTA.getRegion(mainBaseLocations.get(selfPlayer).getTilePosition()),
					selfPlayer) == false) {
				for (BaseLocation loaction : occupiedBaseLocations.get(selfPlayer)) {
					if (existsPlayerBuildingInRegion(BWTA.getRegion(loaction.getTilePosition()), selfPlayer)) {
						mainBaseLocations.put(selfPlayer, loaction);
						mainBaseLocationChanged.put(selfPlayer, new Boolean(true));
						break;
					}
				}
			}
		}

		Iterator<Integer> it = null;
		if (unitData.get(enemyPlayer) != null) {
			it = unitData.get(enemyPlayer).getUnitAndUnitInfoMap().keySet().iterator();

			// for each enemy building unit we know about
			// for (const auto & kv : unitData.get(enemy).getUnits())
			while (it.hasNext()) {
				final UnitInfo ui = unitData.get(enemyPlayer).getUnitAndUnitInfoMap().get(it.next());
				if (ui.getType().isBuilding()) {
					updateOccupiedRegions(BWTA.getRegion(ui.getLastPosition().toTilePosition()),
							MyBotModule.Broodwar.enemy());
				}
			}
		}

		if (unitData.get(selfPlayer) != null) {
			it = unitData.get(selfPlayer).getUnitAndUnitInfoMap().keySet().iterator();

			// for each of our building units
			// for (const auto & kv : _unitData[_self].getUnits())
			while (it.hasNext()) {
				final UnitInfo ui = unitData.get(selfPlayer).getUnitAndUnitInfoMap().get(it.next());
				if (ui.getType().isBuilding()) {
					updateOccupiedRegions(BWTA.getRegion(ui.getLastPosition().toTilePosition()),
							MyBotModule.Broodwar.self());
				}
			}
		}

		updateChokePointAndExpansionLocation();
	}

	public void updateChokePointAndExpansionLocation() {
		if (mainBaseLocationChanged.get(selfPlayer).booleanValue() == true) {

			if (mainBaseLocations.get(selfPlayer) != null) {
				BaseLocation sourceBaseLocation = mainBaseLocations.get(selfPlayer);

				firstChokePoint.put(selfPlayer, BWTA.getNearestChokepoint(sourceBaseLocation.getTilePosition()));

				double tempDistance;
				double closestDistance = 1000000000;
				for (BaseLocation targetBaseLocation : BWTA.getBaseLocations()) {
					if (targetBaseLocation.getTilePosition()
							.equals(mainBaseLocations.get(selfPlayer).getTilePosition()))
						continue;

					tempDistance = BWTA.getGroundDistance(sourceBaseLocation.getTilePosition(),
							targetBaseLocation.getTilePosition());
					if (tempDistance < closestDistance && tempDistance > 0) {
						closestDistance = tempDistance;
						firstExpansionLocation.put(selfPlayer, targetBaseLocation);
					}
				}

				closestDistance = 1000000000;
				for (Chokepoint chokepoint : BWTA.getChokepoints()) {
					if (chokepoint.getCenter().equals(firstChokePoint.get(selfPlayer).getCenter()))
						continue;

					tempDistance = BWTA.getGroundDistance(sourceBaseLocation.getTilePosition(),
							chokepoint.getCenter().toTilePosition());
					if (tempDistance < closestDistance && tempDistance > 0) {
						closestDistance = tempDistance;
						secondChokePoint.put(selfPlayer, chokepoint);
					}
				}
			}
			mainBaseLocationChanged.put(selfPlayer, new Boolean(false));
		}

		if (mainBaseLocationChanged.get(enemyPlayer).booleanValue() == true) {

			if (mainBaseLocations.get(enemyPlayer) != null) {
				BaseLocation sourceBaseLocation = mainBaseLocations.get(enemyPlayer);

				firstChokePoint.put(enemyPlayer, BWTA.getNearestChokepoint(sourceBaseLocation.getTilePosition()));

				double tempDistance;
				double closestDistance = 1000000000;
				for (BaseLocation targetBaseLocation : BWTA.getBaseLocations()) {
					if (targetBaseLocation.getTilePosition()
							.equals(mainBaseLocations.get(enemyPlayer).getTilePosition()))
						continue;

					tempDistance = BWTA.getGroundDistance(sourceBaseLocation.getTilePosition(),
							targetBaseLocation.getTilePosition());
					if (tempDistance < closestDistance && tempDistance > 0) {
						closestDistance = tempDistance;
						firstExpansionLocation.put(enemyPlayer, targetBaseLocation);
					}
				}

				closestDistance = 1000000000;
				for (Chokepoint chokepoint : BWTA.getChokepoints()) {
					if (chokepoint.getCenter().equals(firstChokePoint.get(enemyPlayer).getCenter()))
						continue;

					tempDistance = BWTA.getGroundDistance(sourceBaseLocation.getTilePosition(),
							chokepoint.getCenter().toTilePosition());
					if (tempDistance < closestDistance && tempDistance > 0) {
						closestDistance = tempDistance;
						secondChokePoint.put(enemyPlayer, chokepoint);
					}
				}
			}
			mainBaseLocationChanged.put(enemyPlayer, new Boolean(false));
		}
	}

	public void updateOccupiedRegions(Region region, Player player) {
		// if the region is valid (flying buildings may be in null regions)
		if (region != null) {
			// add it to the list of occupied regions
			if (occupiedRegions.get(player) == null) {
				occupiedRegions.put(player, new HashSet<Region>());
			}
			occupiedRegions.get(player).add(region);
		}
	}

	/// 해당 BaseLocation 에 player의 건물이 존재하는지 리턴합니다
	/// @param baseLocation 대상 BaseLocation
	/// @param player 아군 / 적군
	/// @param radius TilePosition 단위
	public boolean hasBuildingAroundBaseLocation(BaseLocation baseLocation, Player player, int radius) {

		// invalid regions aren't considered the same, but they will both be
		// null
		if (baseLocation == null) {
			return false;
		}

		// 반지름 10 (TilePosition 단위) 이면 거의 화면 가득이다
		int maxRadius = 10;

		if (unitData.get(player) != null) {
			Iterator<Integer> it = unitData.get(player).getUnitAndUnitInfoMap().keySet().iterator();

			// for (const auto & kv : _unitData[player].getUnits())
			while (it.hasNext()) {
				final UnitInfo ui = unitData.get(player).getUnitAndUnitInfoMap().get(it.next());
				if (ui.getType().isBuilding()) {
					TilePosition buildingPosition = ui.getLastPosition().toTilePosition();

					if (buildingPosition.getX() >= baseLocation.getTilePosition().getX() - maxRadius
							&& buildingPosition.getX() <= baseLocation.getTilePosition().getX() + maxRadius
							&& buildingPosition.getY() >= baseLocation.getTilePosition().getY() - maxRadius
							&& buildingPosition.getY() <= baseLocation.getTilePosition().getY() + maxRadius) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/// 해당 BaseLocation 주위 10타일 반경 내에 player의 건물이 존재하는지 리턴합니다
	/// @param baseLocation 대상 BaseLocation
	/// @param player 아군 / 적군
	public boolean hasBuildingAroundBaseLocation(BaseLocation baseLocation, Player player) {
		return hasBuildingAroundBaseLocation(baseLocation, player, 10);
	}

	/// 해당 Region 에 해당 Player의 건물이 존재하는지 리턴합니다
	public boolean existsPlayerBuildingInRegion(Region region, Player player) {
		// invalid regions aren't considered the same, but they will both be
		// null
		if (region == null || player == null) {
			return false;
		}

		Iterator<Integer> it = unitData.get(player).getUnitAndUnitInfoMap().keySet().iterator();

		// for (const auto & kv : unitData.get(self).getUnits())
		while (it.hasNext()) {
			final UnitInfo ui = unitData.get(player).getUnitAndUnitInfoMap().get(it.next());
			if (ui.getType().isBuilding()) {

				// Terran 종족의 Lifted 건물의 경우, BWTA.getRegion 결과가 null 이다
				if (BWTA.getRegion(ui.getLastPosition()) == null)
					continue;

				if (BWTA.getRegion(ui.getLastPosition()) == region) {
					return true;
				}
			}
		}
		return false;
	}

	/// 해당 Player의 처음 본진 위치(방향)를 리턴합니다 > 11, 1, 5, 7시로 리턴
	public int getDirectionOfStartLocation(Player player) {
		BaseLocation base = BWTA.getStartLocation(player);
		int x = base.getTilePosition().getX();
		int y = base.getTilePosition().getY();

		int direction = 0; // 방향 리턴값

		if (x < 64 && y < 64)
			direction = 11;
		else if (x > 64 && y < 64)
			direction = 1;
		else if (x > 64 && y > 64)
			direction = 5;
		else if (x < 64 && y > 64)
			direction = 7;

		return direction;
	}

	/// 해당 Player의 처음 본진 위치(방향)를 리턴합니다 > 11, 1, 5, 7시로 리턴
	public int getDirectionOfStartLocation(BaseLocation baseLocation) {
		int x = baseLocation.getTilePosition().getX();
		int y = baseLocation.getTilePosition().getY();

		int direction = 0; // 방향 리턴값

		if (x < 64 && y < 64)
			direction = 11;
		else if (x > 64 && y < 64)
			direction = 1;
		else if (x > 64 && y > 64)
			direction = 5;
		else if (x < 64 && y > 64)
			direction = 7;

		return direction;
	}

	/// 해당 Player (아군 or 적군) 의 모든 유닛 목록 (가장 최근값) UnitAndUnitInfoMap 을 리턴합니다<br>
	/// 파악된 정보만을 리턴하기 때문에 적군의 정보는 틀린 값일 수 있습니다
	public final Map<Integer, UnitInfo> getUnitAndUnitInfoMap(Player player) {
		return getUnitData(player).getUnitAndUnitInfoMap();
	}

	/// 해당 Player (아군 or 적군) 가 건물을 건설해서 점령한 Region 목록을 리턴합니다
	public Set<Region> getOccupiedRegions(Player player) {
		return occupiedRegions.get(player);
	}

	/// 해당 Player (아군 or 적군) 의 건물을 건설해서 점령한 BaseLocation 목록을 리턴합니다
	public List<BaseLocation> getOccupiedBaseLocations(Player player) {
		return occupiedBaseLocations.get(player);
	}

	/// 해당 Player (아군 or 적군) 의 Main BaseLocation 을 리턴합니다
	public BaseLocation getMainBaseLocation(Player player) {
		return mainBaseLocations.get(player);
	}

	/// 해당 Player (아군 or 적군) 의 Main BaseLocation 에서 가장 가까운 ChokePoint 를 리턴합니다
	public Chokepoint getFirstChokePoint(Player player) {
		return firstChokePoint.get(player);
	}

	/// 해당 Player (아군 or 적군) 의 Main BaseLocation 에서 가장 가까운 Expansion
	/// BaseLocation 를 리턴합니다
	public BaseLocation getFirstExpansionLocation(Player player) {
		return firstExpansionLocation.get(player);
	}

	/// 해당 Player (아군 or 적군) 의 Main BaseLocation 에서 두번째로 가까운 ChokePoint 를
	/// 리턴합니다<br>
	/// 게임 맵에 따라서, secondChokePoint 는 일반 상식과 다른 지점이 될 수도 있습니다
	public Chokepoint getSecondChokePoint(Player player) {
		return secondChokePoint.get(player);
	}

	/// 해당 UnitType 이 전투 유닛인지 리턴합니다
	public final boolean isCombatUnitType(UnitType type) {
		if (type == null) {
			return false;
		}
		if (type != null && type.isWorker() || type.isBuilding()) {
			return false;
		}
		// check for various types of combat units
		if (type.canAttack()) {
			return true;
		}
		return false;
	}

}
