import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bwapi.Player;
import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Chokepoint;
import bwta.Region;

/// 건설위치 탐색을 위한 class
public class ConstructionPlaceFinder {

	/// 건설위치 탐색 방법
	public enum ConstructionPlaceSearchMethod {
		SpiralMethod, /// < 나선형으로 돌아가며 탐색
		NewMethod /// < 예비
	};

	/// 건물 건설 예정 타일을 저장해놓기 위한 2차원 배열<br>
	/// TilePosition 단위이기 때문에 보통 128*128 사이즈가 된다<br>
	/// 참고로, 건물이 이미 지어진 타일은 저장하지 않는다
	private boolean[][] reserveMap = new boolean[128][128];

	/// BaseLocation 과 Mineral / Geyser 사이의 타일들을 담는 자료구조. 여기에는 Addon 이외에는 건물을 짓지 않도록
	/// 합니다
	private Set<TilePosition> tilesToAvoid = new HashSet<TilePosition>();

	private static ConstructionPlaceFinder instance = new ConstructionPlaceFinder();
	private InformationManager info = InformationManager.Instance();
	private Player self = MyBotModule.Broodwar.self();
	private Player enemy = MyBotModule.Broodwar.enemy();
	private StrategyManager stmgr = StrategyManager.Instance();

	private static boolean isInitialized = false;
	// 0701 - 최혜진 추가 Supply Depot 위치 지정을 위한 변수 선언
	private static boolean isSupplyDepotBuild = false;

	public static int locationOfBase = 0;
	public static int numberOfSupply = 0;

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
	private static int[] turretXLocationForSpirit = { 12, 0, 2, 8, 26, 22, 85, 0, 94, 80, 106, 120, 40, 0, 32, 46, 21,
			6, 114, 0, 124, 115, 101, 102 };
	private static int[] turretYLocationForSpirit = { 35, 0, 30, 44, 19, 6, 18, 0, 3, 12, 20, 29, 108, 0, 125, 113, 104,
			97, 92, 0, 95, 82, 107, 120 };
	// 0730 - 최혜진 추가
	public static int numberOfFactoryBuilt = 0;
	private static boolean isFirstBuilt;

	// 0801 - 최혜진 추가 Factory 좌표 지정
	// 0802 - 최혜진 수정 투혼 맵에 Factory 좌표 지정
	// 0806 - 최혜진 수정 투혼 맵 Factory 좌표 입구와 가깝게 수정
	// 0807 - 최혜진 수정 서킷 맵 Factory y 좌표 수정
	private static int[] FactoryXLocationForCircuit = { 0, 7, 11, 15, 122, 117, 113, 109 };
	private static int[] FactoryYLocationForCircuit = { 20, 16, 105, 109 };
	private static int[] FactoryXLocationForSpirit = { 0, 7, 11, 15, 109, 116, 120, 124, 13, 8, 4, 0, 122, 117, 113,
			109 };
	private static int[] FactoryYLocationForSpirit = { 22, 18, 14, 18, 108, 104, 103, 107 };

	// 0805 - 최혜진 추가 투혼 맵 1시 방향 Bunker 올바른 위치 건설 위한 변수
	public static boolean zergNot4Drone;

	// 0807 - 최혜진 앞마당 막기 좌표 수정
	private static int[] expansionXLocaitonForCircuit = { 13, 8, 11, 112, 117, 114, 11, 8, 14, 113, 117, 111 };
	private static int[] expansionYLocaitonForCircuit = { 30, 32, 33, 30, 32, 33, 94, 95, 97, 94, 95, 97 };
	private static int[] expansionXLocaitonForSpirit = { 18, 15, 18, 87, 88, 91, 36, 37, 34, 106, 110, 107 };
	private static int[] expansionYLocaitonForSpirit = { 34, 36, 37, 18, 16, 16, 107, 110, 110, 92, 91, 90 };
	private static int expansionOrder;

	// 0814 - 최혜진 추가
	public static Map<BaseLocation, Boolean> multipleExpansionBuildMap = new HashMap<>();
	public static Map<BaseLocation, Boolean> multipleRefineryBuildMap = new HashMap<>();

	/// static singleton 객체를 리턴합니다
	public static ConstructionPlaceFinder Instance() {
		if (isInitialized == false) {
			instance.setTilesToAvoid();
			isInitialized = true;
		}
		return instance;
	}

	/// seedPosition 및 seedPositionStrategy 파라메터를 활용해서 건물 건설 가능 위치를 탐색해서 리턴합니다<br>
	/// seedPosition 주위에서 가능한 곳을 선정하거나, seedPositionStrategy 에 따라 지형 분석결과 해당 지점 주위에서
	/// 가능한 곳을 선정합니다<br>
	/// seedPosition, seedPositionStrategy 을 입력하지 않으면, MainBaseLocation 주위에서 가능한 곳을
	/// 리턴합니다
	public final TilePosition getBuildLocationWithSeedPositionAndStrategy(UnitType buildingType,
			TilePosition seedPosition, BuildOrderItem.SeedPositionStrategy seedPositionStrategy) {
		// BasicBot 1.1 Patch Start ////////////////////////////////////////////////
		// 빌드 실행 유닛 (일꾼/건물) 결정 로직이 seedLocation 이나 seedLocationStrategy 를 잘 반영하도록 수정

		TilePosition desiredPosition = TilePosition.None;

		// seedPosition 을 입력한 경우 그 근처에서 찾는다
		if (seedPosition != TilePosition.None && seedPosition.isValid()) {
			// std::cout << "getBuildLocationNear " << seedPosition.x << ", " <<
			// seedPosition.y << std::endl;
			desiredPosition = getBuildLocationNear(buildingType, seedPosition);
		}
		// seedPosition 을 입력하지 않은 경우
		else {
			Chokepoint tempChokePoint;
			BaseLocation tempBaseLocation;
			BaseLocation tempFirstExpansion; // 0630 - 최혜진 추가
			TilePosition tempTilePosition = null;
			Region tempBaseRegion;
			int vx, vy;
			double d, t;
			int bx, by;

			switch (seedPositionStrategy) {
			case MainBaseLocation:
				desiredPosition = getBuildLocationNear(buildingType, info.getMainBaseLocation(self).getTilePosition());
				break;

			case MainBaseBackYard:
				tempBaseLocation = info.getMainBaseLocation(self);
				tempChokePoint = info.getFirstChokePoint(self);
				tempBaseRegion = BWTA.getRegion(tempBaseLocation.getPosition());

				// std::cout << "y";

				// (vx, vy) = BaseLocation 와 ChokePoint 간 차이 벡터 = 거리 d 와 각도 t 벡터. 단위는 position
				// 스타크래프트 좌표계 : 오른쪽으로 갈수록 x 가 증가 (데카르트 좌표계와 동일). 아래로 갈수록 y가 증가 (y축만 데카르트 좌표계와
				// 반대)
				// 삼각함수 값은 데카르트 좌표계에서 계산하므로, vy를 부호 반대로 해서 각도 t 값을 구함

				// FirstChokePoint 가 null 이면, MainBaseLocation 주위에서 가능한 곳을 리턴한다
				if (tempChokePoint == null) {
					// std::cout << "r";
					desiredPosition = getBuildLocationNear(buildingType,
							info.getMainBaseLocation(self).getTilePosition());
					break;
				}

				// BaseLocation 에서 ChokePoint 로의 벡터를 구한다
				vx = tempChokePoint.getCenter().getX() - tempBaseLocation.getPosition().getX();
				// std::cout << "vx : " << vx ;
				vy = (tempChokePoint.getCenter().getY() - tempBaseLocation.getPosition().getY()) * (-1);
				// std::cout << "vy : " << vy;
				d = Math.sqrt(vx * vx + vy * vy) * 0.5; // BaseLocation 와 ChokePoint 간 거리보다 조금 짧은 거리로 조정. BaseLocation가
														// 있는 Region은 대부분 직사각형 형태이기 때문
				// std::cout << "d : " << d;
				t = Math.atan2(vy, vx + 0.0001); // 라디안 단위
				// std::cout << "t : " << t;

				// cos(t+90), sin(t+180) 등 삼각함수 Trigonometric functions of allied angles 을 이용.
				// y축에 대해서는 반대부호로 적용

				// BaseLocation 에서 ChokePoint 반대쪽 방향의 Back Yard : 데카르트 좌표계에서 (cos(t+180) =
				// -cos(t), sin(t+180) = -sin(t))
				bx = tempBaseLocation.getTilePosition().getX() - (int) (d * Math.cos(t) / Config.TILE_SIZE);
				by = tempBaseLocation.getTilePosition().getY() + (int) (d * Math.sin(t) / Config.TILE_SIZE);
				// std::cout << "i";
				tempTilePosition = new TilePosition(bx, by);
				// std::cout << "ConstructionPlaceFinder MainBaseBackYard tempTilePosition " <<
				// tempTilePosition.x << "," << tempTilePosition.y << std::endl;

				// std::cout << "k";
				// 해당 지점이 같은 Region 에 속하고 Buildable 한 타일인지 확인
				if (!tempTilePosition.isValid()
						|| !MyBotModule.Broodwar.isBuildable(tempTilePosition.getX(), tempTilePosition.getY(), false)
						|| tempBaseRegion != BWTA
								.getRegion(new Position(bx * Config.TILE_SIZE, by * Config.TILE_SIZE))) {
					// std::cout << "l";

					// BaseLocation 에서 ChokePoint 방향에 대해 오른쪽으로 90도 꺾은 방향의 Back Yard : 데카르트 좌표계에서
					// (cos(t-90) = sin(t), sin(t-90) = - cos(t))
					bx = tempBaseLocation.getTilePosition().getX() + (int) (d * Math.sin(t) / Config.TILE_SIZE);
					by = tempBaseLocation.getTilePosition().getY() + (int) (d * Math.cos(t) / Config.TILE_SIZE);
					tempTilePosition = new TilePosition(bx, by);
					// std::cout << "ConstructionPlaceFinder MainBaseBackYard tempTilePosition " <<
					// tempTilePosition.x << "," << tempTilePosition.y << std::endl;
					// std::cout << "m";

					if (!tempTilePosition.isValid() || !MyBotModule.Broodwar.isBuildable(tempTilePosition.getX(),
							tempTilePosition.getY(), false)) {
						// BaseLocation 에서 ChokePoint 방향에 대해 왼쪽으로 90도 꺾은 방향의 Back Yard : 데카르트 좌표계에서
						// (cos(t+90) = -sin(t), sin(t+90) = cos(t))
						bx = tempBaseLocation.getTilePosition().getX() - (int) (d * Math.sin(t) / Config.TILE_SIZE);
						by = tempBaseLocation.getTilePosition().getY() - (int) (d * Math.cos(t) / Config.TILE_SIZE);
						tempTilePosition = new TilePosition(bx, by);
						// std::cout << "ConstructionPlaceFinder MainBaseBackYard tempTilePosition " <<
						// tempTilePosition.x << "," << tempTilePosition.y << std::endl;

						if (!tempTilePosition.isValid()
								|| !MyBotModule.Broodwar.isBuildable(tempTilePosition.getX(), tempTilePosition.getY(),
										false)
								|| tempBaseRegion != BWTA
										.getRegion(new Position(bx * Config.TILE_SIZE, by * Config.TILE_SIZE))) {

							// BaseLocation 에서 ChokePoint 방향 절반 지점의 Back Yard : 데카르트 좌표계에서 (cos(t), sin(t))
							bx = tempBaseLocation.getTilePosition().getX() + (int) (d * Math.cos(t) / Config.TILE_SIZE);
							by = tempBaseLocation.getTilePosition().getY() - (int) (d * Math.sin(t) / Config.TILE_SIZE);
							tempTilePosition = new TilePosition(bx, by);
							// std::cout << "ConstructionPlaceFinder MainBaseBackYard tempTilePosition " <<
							// tempTilePosition.x << "," << tempTilePosition.y << std::endl;
							// std::cout << "m";
						}

					}
				}
				// std::cout << "z";
				if (!tempTilePosition.isValid()
						|| !MyBotModule.Broodwar.isBuildable(tempTilePosition.getX(), tempTilePosition.getY(), false)) {
					desiredPosition = getBuildLocationNear(buildingType, tempBaseLocation.getTilePosition());
				} else {
					desiredPosition = getBuildLocationNear(buildingType, tempTilePosition);
				}
				// std::cout << "w";
				// std::cout << "ConstructionPlaceFinder MainBaseBackYard desiredPosition " <<
				// desiredPosition.x << "," << desiredPosition.y << std::endl;
				break;

			case FirstExpansionLocation:
				tempBaseLocation = info.getFirstExpansionLocation(self);
				if (tempBaseLocation != null) {
					// desiredPosition = getBuildLocationNear(buildingType,
					// tempBaseLocation.getTilePosition());
					desiredPosition = tempBaseLocation.getTilePosition();
				}
				break;

			case FirstChokePoint:
				tempChokePoint = info.getFirstChokePoint(self);
				if (tempChokePoint != null) {
					desiredPosition = getBuildLocationNear(buildingType, tempChokePoint.getCenter().toTilePosition());
				}
				break;

			case SecondChokePoint:
				tempChokePoint = info.getSecondChokePoint(self);
				if (tempChokePoint != null) {
					desiredPosition = getBuildLocationNear(buildingType, tempChokePoint.getCenter().toTilePosition());
				}
				break;

			// 0630 - 최혜진 추가 SupplyDepot에 대한 전략 추가
			case SupplyDepotPosition:
				if (isSupplyDepotBuild == false) { // Supply Depot 첫번째 위치 지정인 경우
					// BaseLocation이 맵의 어느 부분에 위치하는지 파악하고 초기값 리턴
					tempBaseLocation = info.getMainBaseLocation(self);
					if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
						tempFirstExpansion = info.getFirstExpansionLocation(self);
						tempChokePoint = info.getSecondChokePoint(self);
						int dx = tempBaseLocation.getX() - tempChokePoint.getCenter().getX();
						int dy = tempBaseLocation.getTilePosition().getY()
								- tempFirstExpansion.getTilePosition().getY();
						// 0722 - 최혜진 수정 초기 좌표 설정
						// 0723 - 최혜진 수정 좌표 이상 해결
						if (dx < 0 && dy < 0) { // BaseLocation이 좌상단 위치
							locationOfBase = 1;
							// 0815 - 최혜진 수정 가스와 커맨드 사이에는 Supply Depot 건설하지 않도록 reserve 타일 설정
							reserveTiles(new TilePosition(6, 6), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
							reserveTiles(new TilePosition(9, 6), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
						} else if (dx > 0 && dy < 0) { // BaseLocation이 우상단 위치
							locationOfBase = 2;
							// 0815 - 최혜진 수정 가스와 커맨드 사이에는 Supply Depot 건설하지 않도록 reserve 타일 설정
							reserveTiles(new TilePosition(119, 6), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
							reserveTiles(new TilePosition(116, 6), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
						} else if (dx < 0 && dy > 0) { // BaseLocation이 좌하단 위치
							locationOfBase = 3;
							// 0815 - 최혜진 수정 미네랄과 커맨드 사이에는 Supply Depot 건설하지 않도록 reserve 타일 설정
							reserveTiles(new TilePosition(8, 121), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
						} else if (dx > 0 && dy > 0) { // BaseLocation이 우하단 위치
							locationOfBase = 4;
							// 0815 - 최혜진 수정 미네랄과 커맨드 사이에는 Supply Depot 건설하지 않도록 reserve 타일 설정
							reserveTiles(new TilePosition(119, 121), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
						}
					} else {
						// 0726 - 최혜진 추가 투혼 맵 적용
						tempFirstExpansion = info.getFirstExpansionLocation(self);
						tempChokePoint = info.getSecondChokePoint(self);
						int dx = tempBaseLocation.getX() - tempChokePoint.getCenter().getX();
						int dy = tempBaseLocation.getTilePosition().getY()
								- tempFirstExpansion.getTilePosition().getY();
						// 0722 - 최혜진 수정 초기 좌표 설정
						// 0723 - 최혜진 수정 좌표 이상 해결
						if (dx < 0 && dy < 0) { // BaseLocation이 좌상단 위치
							locationOfBase = 1;
							reserveTiles(new TilePosition(6, 3), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
							reserveTiles(new TilePosition(9, 3), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
							reserveTiles(new TilePosition(6, 10), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
						} else if (dx > 0 && dy < 0) { // BaseLocation이 우상단 위치
							locationOfBase = 2;
							reserveTiles(new TilePosition(119, 4), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
							reserveTiles(new TilePosition(116, 4), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
							reserveTiles(new TilePosition(119, 10), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
							reserveTiles(new TilePosition(116, 10), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
						} else if (dx < 0 && dy > 0) { // BaseLocation이 좌하단 위치
							locationOfBase = 3;
							reserveTiles(new TilePosition(6, 119), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
							reserveTiles(new TilePosition(9, 119), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
						} else if (dx > 0 && dy > 0) { // BaseLocation이 우하단 위치
							locationOfBase = 4;
							reserveTiles(new TilePosition(119, 121), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
							reserveTiles(new TilePosition(119, 115), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
							reserveTiles(new TilePosition(116, 115), UnitType.Terran_Supply_Depot.tileWidth(),
									UnitType.Terran_Supply_Depot.tileHeight());
						}
					}
					isSupplyDepotBuild = true;
				}
				// 0814 - 최혜진 수정 getPoint 삭제
				desiredPosition = checkEveryPositionForSupplyDepot(numberOfSupply);
				numberOfSupply++;
				break;

			case BlockFirstChokePoint:
				int blockx = 0;
				int blocky = 0;
				// 0807 - 최혜진 수정 앞마당 입구 막기 좌표 수정
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					if (enemy.getRace() == Race.Terran) {
						if (expansionOrder >= 3 && buildingType == UnitType.Terran_Bunker) {
							desiredPosition = getBuildLocationWithSeedPositionAndStrategy(buildingType, seedPosition,
									BuildOrderItem.SeedPositionStrategy.SecondChokePoint);
							expansionOrder++;
							break;
						}
						if (expansionOrder == 0) {
							int index = expansionOrder + ((locationOfBase - 1) * 3);
							if (index < 12) {
								blockx = expansionXLocaitonForCircuit[index];
								blocky = expansionYLocaitonForCircuit[index];
								expansionOrder++;
							}
						} else if (expansionOrder == 1) {
							desiredPosition = getBuildLocationWithSeedPositionAndStrategy(buildingType, seedPosition,
									BuildOrderItem.SeedPositionStrategy.SecondChokePoint);
							expansionOrder++;
							break;
						} else if (expansionOrder == 2) {
							desiredPosition = getBuildLocationWithSeedPositionAndStrategy(buildingType, seedPosition,
									BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition);
							expansionOrder++;
							break;

						}
					} else {
						if (expansionOrder >= 3 && buildingType == UnitType.Terran_Bunker) {
							int index = 1 + ((locationOfBase - 1) * 3);
							if (index < 12) {
								blockx = expansionXLocaitonForCircuit[index];
								blocky = expansionYLocaitonForCircuit[index];
								expansionOrder++;
							}
						} else {
							int index = expansionOrder + ((locationOfBase - 1) * 3);
							if (index < 12) {
								blockx = expansionXLocaitonForCircuit[index];
								blocky = expansionYLocaitonForCircuit[index];
								expansionOrder++;
							}
						}
					}
				} else {
					if (enemy.getRace() == Race.Terran) {
						if (expansionOrder >= 3 && buildingType == UnitType.Terran_Bunker) {
							desiredPosition = getBuildLocationWithSeedPositionAndStrategy(buildingType, seedPosition,
									BuildOrderItem.SeedPositionStrategy.SecondChokePoint);
							expansionOrder++;
							break;
						}
						if (expansionOrder == 0) {
							int index = expansionOrder + ((locationOfBase - 1) * 3);
							if (index < 12) {
								blockx = expansionXLocaitonForSpirit[index];
								blocky = expansionYLocaitonForSpirit[index];
								expansionOrder++;
							}
						} else if (expansionOrder == 1) {
							desiredPosition = getBuildLocationWithSeedPositionAndStrategy(buildingType, seedPosition,
									BuildOrderItem.SeedPositionStrategy.SecondChokePoint);
							expansionOrder++;
							break;
						} else if (expansionOrder == 2) {
							desiredPosition = getBuildLocationWithSeedPositionAndStrategy(buildingType, seedPosition,
									BuildOrderItem.SeedPositionStrategy.SupplyDepotPosition);
							expansionOrder++;
							break;

						}
					} else {
						if (expansionOrder >= 3 && buildingType == UnitType.Terran_Bunker) {
							int index = 1 + ((locationOfBase - 1) * 3);
							if (index < 12) {
								blockx = expansionXLocaitonForSpirit[index];
								blocky = expansionYLocaitonForSpirit[index];
								expansionOrder++;
							}
						} else {
							int index = expansionOrder + ((locationOfBase - 1) * 3);
							if (index < 12) {
								blockx = expansionXLocaitonForSpirit[index];
								blocky = expansionYLocaitonForSpirit[index];
								expansionOrder++;
							}
						}
					}
				}
				tempTilePosition = new TilePosition(blockx, blocky);
				desiredPosition = tempTilePosition.getPoint();
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
				desiredPosition = tempTilePosition.getPoint();
				break;

			// 0729 - 최혜진 추가 본진 및 앞마당 방어를 위한 Turret 건설 전략
			// 0806 - 최혜진 수정 Turret이 두번째 길목에 가장 먼저 건설되도록 변경
			// 0807 - 최혜진 수정 앞마당 Command Center 옆에 터렛 추가
			case TurretAround:
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
								desiredPosition = getBuildLocationNear(UnitType.Terran_Missile_Turret, tempRallyPoint);
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
								desiredPosition = getBuildLocationNear(UnitType.Terran_Missile_Turret, tempRallyPoint);
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
								desiredPosition = getBuildLocationNear(UnitType.Terran_Missile_Turret, tempRallyPoint);
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
						if (numberOfTurretBuilt % 6 == 1) {
							tempChokePoint = info.getSecondChokePoint(self);
							if (tempChokePoint != null) {
								desiredPosition = getBuildLocationNear(UnitType.Terran_Missile_Turret,
										tempChokePoint.getCenter().toTilePosition());
								turretx = desiredPosition.getX();
								turrety = desiredPosition.getY();
							}
						} else {
							turretx = turretXLocationForSpirit[numberOfTurretBuilt % 6];
							turrety = turretYLocationForSpirit[numberOfTurretBuilt % 6];
						}
					} else if (locationOfBase == 2) {
						if (numberOfTurretBuilt % 6 == 1) {
							tempChokePoint = info.getSecondChokePoint(self);
							if (tempChokePoint != null) {
								desiredPosition = getBuildLocationNear(UnitType.Terran_Missile_Turret,
										tempChokePoint.getCenter().toTilePosition());
								turretx = desiredPosition.getX();
								turrety = desiredPosition.getY();
							}
						} else {
							turretx = turretXLocationForSpirit[(numberOfTurretBuilt % 6) + 6];
							turrety = turretYLocationForSpirit[(numberOfTurretBuilt % 6) + 6];
						}
					} else if (locationOfBase == 3) {
						if (numberOfTurretBuilt % 6 == 1) {
							tempChokePoint = info.getSecondChokePoint(self);
							if (tempChokePoint != null) {
								desiredPosition = getBuildLocationNear(UnitType.Terran_Missile_Turret,
										tempChokePoint.getCenter().toTilePosition());
								turretx = desiredPosition.getX();
								turrety = desiredPosition.getY();
							}
						} else {
							turretx = turretXLocationForSpirit[(numberOfTurretBuilt % 6) + 12];
							turrety = turretYLocationForSpirit[(numberOfTurretBuilt % 6) + 12];
						}
					} else if (locationOfBase == 4) {
						if (numberOfTurretBuilt % 6 == 1) {
							tempChokePoint = info.getSecondChokePoint(self);
							if (tempChokePoint != null) {
								desiredPosition = getBuildLocationNear(UnitType.Terran_Missile_Turret,
										tempChokePoint.getCenter().toTilePosition());
								turretx = desiredPosition.getX();
								turrety = desiredPosition.getY();
							}
						} else {
							turretx = turretXLocationForSpirit[(numberOfTurretBuilt % 6) + 18];
							turrety = turretYLocationForSpirit[(numberOfTurretBuilt % 6) + 18];
						}

					}
				}
				tempTilePosition = new TilePosition(turretx, turrety);
				desiredPosition = tempTilePosition.getPoint();
				numberOfTurretBuilt++;
				break;

			// 0730 - 최혜진 추가 본진 Factory 효율적 배치를 위한 건설 전략
			// 0801 - 최혜진 수정 Factory 좌표 값 지정 및 코드 단순화
			case FactoryInMainBaseLocation:
				int fx = 0;
				int fy = 0;

				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					if (locationOfBase == 1) {
						if (numberOfFactoryBuilt == 0) {
							fx = 0;
							fy = 24;
						} else {
							int changedNumberOfFactoryBuilt = numberOfFactoryBuilt - 1;
							int numberOfFactoryBuiltInt = changedNumberOfFactoryBuilt / 2;
							if (numberOfFactoryBuiltInt < 8) {
								fx = FactoryXLocationForCircuit[numberOfFactoryBuiltInt];
								fy = FactoryYLocationForCircuit[changedNumberOfFactoryBuilt % 2];
							}
						}
					} else if (locationOfBase == 2) {
						if (numberOfFactoryBuilt == 0) {
							fx = 122;
							fy = 24;
						} else {
							int changedNumberOfFactoryBuilt = numberOfFactoryBuilt - 1;
							int numberOfFactoryBuiltInt = (changedNumberOfFactoryBuilt / 2) + 4;
							if (numberOfFactoryBuiltInt < 8) {
								fx = FactoryXLocationForCircuit[numberOfFactoryBuiltInt];
								fy = FactoryYLocationForCircuit[changedNumberOfFactoryBuilt % 2];
							}
						}
					} else if (locationOfBase == 3) {
						if (numberOfFactoryBuilt == 0) {
							fx = 0;
							fy = 101;
						} else {
							int changedNumberOfFactoryBuilt = numberOfFactoryBuilt - 1;
							int numberOfFactoryBuiltInt = changedNumberOfFactoryBuilt / 2;
							if (numberOfFactoryBuiltInt < 8) {
								fx = FactoryXLocationForCircuit[numberOfFactoryBuiltInt];
								fy = FactoryYLocationForCircuit[(changedNumberOfFactoryBuilt % 2) + 2];
							}
						}
					} else if (locationOfBase == 4) {
						if (numberOfFactoryBuilt == 0) {
							fx = 122;
							fy = 101;
						} else {
							int changedNumberOfFactoryBuilt = numberOfFactoryBuilt - 1;
							int numberOfFactoryBuiltInt = (changedNumberOfFactoryBuilt / 2) + 4;
							if (numberOfFactoryBuiltInt < 8) {
								fx = FactoryXLocationForCircuit[numberOfFactoryBuiltInt];
								fy = FactoryYLocationForCircuit[(changedNumberOfFactoryBuilt % 2) + 2];
							}
						}
					}
				} else {
					// 0802 - 최혜진 수정 투혼 맵에 Factory 좌표 지정
					// 0806 - 최혜진 수정 투혼 맵 Factory 입구와 가깝게 좌표 수정
					if (locationOfBase == 1) {
						if (numberOfFactoryBuilt == 0) {
							fx = 0;
							fy = 26;
						} else {
							int changedNumberOfFactoryBuilt = numberOfFactoryBuilt - 1;
							int numberOfFactoryBuiltInt = changedNumberOfFactoryBuilt / 2;
							if (numberOfFactoryBuiltInt < 16) {
								fx = FactoryXLocationForSpirit[numberOfFactoryBuiltInt];
								fy = FactoryYLocationForSpirit[changedNumberOfFactoryBuilt % 2];
							}
						}
					} else if (locationOfBase == 2) {
						if (numberOfFactoryBuilt == 0) {
							fx = 109;
							fy = 10;
						} else {
							int changedNumberOfFactoryBuilt = numberOfFactoryBuilt - 1;
							int numberOfFactoryBuiltInt = (changedNumberOfFactoryBuilt / 2) + 4;
							if (numberOfFactoryBuiltInt < 16) {
								fx = FactoryXLocationForSpirit[numberOfFactoryBuiltInt];
								fy = FactoryYLocationForSpirit[(changedNumberOfFactoryBuilt % 2) + 2];
							}
						}
					} else if (locationOfBase == 3) {
						if (numberOfFactoryBuilt == 0) {
							fx = 20;
							fy = 109;
						} else {
							int changedNumberOfFactoryBuilt = numberOfFactoryBuilt - 1;
							int numberOfFactoryBuiltInt = (changedNumberOfFactoryBuilt / 2) + 8;
							if (numberOfFactoryBuiltInt < 16) {
								fx = FactoryXLocationForSpirit[numberOfFactoryBuiltInt];
								fy = FactoryYLocationForSpirit[(changedNumberOfFactoryBuilt % 2) + 4];
							}
						}
					} else if (locationOfBase == 4) {
						if (numberOfFactoryBuilt == 0) {
							fx = 122;
							fy = 99;
						} else {
							int changedNumberOfFactoryBuilt = numberOfFactoryBuilt - 1;
							int numberOfFactoryBuiltInt = (changedNumberOfFactoryBuilt / 2) + 12;
							if (numberOfFactoryBuiltInt < 16) {
								fx = FactoryXLocationForSpirit[numberOfFactoryBuiltInt];
								fy = FactoryYLocationForSpirit[(changedNumberOfFactoryBuilt % 2) + 6];
							}
						}
					}
				}
				tempTilePosition = new TilePosition(fx, fy);
				desiredPosition = tempTilePosition.getPoint();
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
							ox = 0;
							oy = 15;
						} else {
							ox = 6;
							oy = 15;
						}
					} else if (locationOfBase == 2) {
						if (isFirstBuilt == false) {
							isFirstBuilt = true;
							ox = 110;
							oy = 21;
						} else {
							ox = 116;
							oy = 21;
						}
					} else if (locationOfBase == 3) {
						if (isFirstBuilt == false) {
							isFirstBuilt = true;
							ox = 0;
							oy = 111;
						} else {
							ox = 11;
							oy = 111;
						}
					} else if (locationOfBase == 4) {
						if (isFirstBuilt == false) {
							isFirstBuilt = true;
							ox = 121;
							oy = 111;
						} else {
							ox = 112;
							oy = 111;
						}
					}
				}
				tempTilePosition = new TilePosition(ox, oy);
				desiredPosition = tempTilePosition.getPoint();
				break;

			// 0811 - 최혜진 추가 다른 지역으로의 추가적인 확장
			case MultipleExpansion:
				if (buildingType == UnitType.Terran_Command_Center) {
					BaseLocation tempMulti = null;
					if (!multipleExpansionBuildMap.isEmpty()) {
						for (BaseLocation multi : multipleExpansionBuildMap.keySet()) {
							if (multipleExpansionBuildMap.get(multi) == false) {
								tempMulti = multi;
								break;
							}
						}
					}
					desiredPosition = tempMulti.getTilePosition();
				} else {
					TilePosition tempRefinery = null;
					if (!multipleRefineryBuildMap.isEmpty()) {
						for (BaseLocation multi : multipleRefineryBuildMap.keySet()) {
							if (multipleRefineryBuildMap.get(multi) == false) {
								tempRefinery = getRefineryPositionNear(multi.getTilePosition());
								break;
							}
						}
					}
					desiredPosition = tempRefinery;
				}
				break;
			default:
				break;
			}
		}

		return desiredPosition;

		// BasicBot 1.1 Patch End //////////////////////////////////////////////////
	}

	// 0811 - 최혜진 추가 Supply Depot 처음부터 탐색
	public final TilePosition checkEveryPositionForSupplyDepot(int number) {
		TilePosition tempTilePosition = TilePosition.None;

		int nx = 0;
		int ny = 0;

		for (int i = 0; i <= number; i++) {
			if (i == 0) {
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					if (locationOfBase == 1) {
						nx = 0;
						ny = 0;
					} else if (locationOfBase == 2) {
						nx = 125;
						ny = 0;
					} else if (locationOfBase == 3) {
						nx = 9;
						ny = 125;
					} else if (locationOfBase == 4) {
						nx = 119;
						ny = 125;
					}
				} else {
					if (locationOfBase == 1) {
						nx = 0;
						ny = 0;
					} else if (locationOfBase == 2) {
						nx = 125;
						ny = 0;
					} else if (locationOfBase == 3) {
						nx = 6;
						ny = 125;
					} else if (locationOfBase == 4) {
						nx = 119;
						ny = 125;
					}
				}
			} else {
				if (MyBotModule.Broodwar.mapFileName().contains("Circuit")) {
					// 0722 - 최혜진 수정 Supply Depot 짓는 방식 변경
					if (locationOfBase == 1) {
						if (i % 8 == 0) {
							ny = ny + 2;
							nx = 0;
						} else {
							nx = nx + 3;
						}
					} else if (locationOfBase == 2) {
						if (i % 8 == 0) {
							ny = ny + 2;
							nx = 125;
						} else {
							nx = nx - 3;
						}
					} else if (locationOfBase == 3) {
						if (i % 7 == 0) {
							ny = ny - 2;
							// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
							nx = 9;
						} else {
							nx = nx + 3;
						}
					} else if (locationOfBase == 4) {
						if (i % 7 == 0) {
							ny = ny - 2;
							// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
							nx = 119;
						} else {
							nx = nx - 3;
						}
					}
				} else {
					// 0726 - 최혜진 추가 투혼 맵 적용
					// 0722 - 최혜진 수정 Supply Depot 짓는 방식 변경
					if (locationOfBase == 1) {
						if (i % 7 == 0) {
							ny = ny + 2;
							nx = 0;
						} else {
							nx = nx + 3;
						}
					} else if (locationOfBase == 2) {
						if (i % 8 == 0) {
							ny = ny + 2;
							nx = 125;
						} else {
							nx = nx - 3;
						}
					} else if (locationOfBase == 3) {
						if (i % 8 == 0) {
							ny = ny - 2;
							// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
							nx = 6;
						} else {
							nx = nx + 3;
						}
					} else if (locationOfBase == 4) {
						if (i % 6 == 0) {
							ny = ny - 2;
							// 0805 - 최혜진 수정 미네랄과 붙어있지 않도록 수정
							nx = 119;
						} else {
							nx = nx - 3;
						}
					}
				}
			}

			tempTilePosition = new TilePosition(nx, ny);
			// 0814 - 최혜진 수정 reserved tile인지 검사하는 로직 추가
			if (MyBotModule.Broodwar.canBuildHere(tempTilePosition, UnitType.Terran_Supply_Depot)
					&& !isReservedTile(tempTilePosition.getX(), tempTilePosition.getY())) {
				break;
			} else {
				tempTilePosition = TilePosition.None;
				// 0814 - 최혜진 재귀 대신 for문 내에서 해결하도록 수정
				if (i == number) {
					number++;
				}
			}
		}

		reserveTiles(tempTilePosition, UnitType.Terran_Supply_Depot.tileWidth(),
				UnitType.Terran_Supply_Depot.tileHeight());
		// 0814 - 최혜진 수정 getPoint 삭제
		return tempTilePosition;

	}

	/// desiredPosition 근처에서 건물 건설 가능 위치를 탐색해서 리턴합니다<br>
	/// desiredPosition 주위에서 가능한 곳을 찾아 반환합니다<br>
	/// desiredPosition 이 valid 한 곳이 아니라면, desiredPosition 를 MainBaseLocation 로 해서
	/// 주위를 찾는다<br>
	/// Returns a suitable TilePosition to build a given building type near
	/// specified TilePosition aroundTile.<br>
	/// Returns BWAPI::TilePositions::None, if suitable TilePosition is not exists
	/// (다른 유닛들이 자리에 있어서, Pylon, Creep, 건물지을 타일 공간이 전혀 없는 경우 등)
	public final TilePosition getBuildLocationNear(UnitType buildingType, TilePosition desiredPosition) {
		if (buildingType.isRefinery()) {
			// std::cout << "getRefineryPositionNear "<< std::endl;

			return getRefineryPositionNear(desiredPosition);
		}

		if (self.getRace() == Race.Protoss) {
			// special easy case of having no pylons
			if (buildingType.requiresPsi() && self.completedUnitCount(UnitType.Protoss_Pylon) == 0) {
				return TilePosition.None;
			}
		}

		if (desiredPosition == TilePosition.None || desiredPosition == TilePosition.Unknown
				|| desiredPosition == TilePosition.Invalid || desiredPosition.isValid() == false) {
			desiredPosition = info.getMainBaseLocation(self).getTilePosition();
		}

		TilePosition testPosition = TilePosition.None;

		// TODO 과제 : 건설 위치 탐색 방법은 ConstructionPlaceSearchMethod::SpiralMethod 로 하는데, 더
		// 좋은 방법은 생각해볼 과제이다
		int constructionPlaceSearchMethod = ConstructionPlaceSearchMethod.SpiralMethod.ordinal();

		// 일반적인 건물에 대해서는 건물 크기보다 Config::Macro::BuildingSpacing 칸 만큼 상하좌우로 더 넓게 여유공간을
		// 두어서 빈 자리를 검색한다
		int buildingGapSpace = Config.BuildingSpacing;

		// ResourceDepot (Nexus, Command Center, Hatchery),
		// Protoss_Pylon, Terran_Supply_Depot,
		// Protoss_Photon_Cannon, Terran_Bunker, Terran_Missile_Turret,
		// Zerg_Creep_Colony 는 다른 건물 바로 옆에 붙여 짓는 경우가 많으므로
		// buildingGapSpace을 다른 Config 값으로 설정하도록 한다
		if (buildingType.isResourceDepot()) {
			buildingGapSpace = Config.BuildingResourceDepotSpacing;
		} else if (buildingType == UnitType.Protoss_Pylon) {
			int numPylons = self.completedUnitCount(UnitType.Protoss_Pylon);

			// Protoss_Pylon 은 특히 최초 2개 건설할때는 Config::Macro::BuildingPylonEarlyStageSpacing
			// 값으로 설정한다
			if (numPylons < 3) {
				buildingGapSpace = Config.BuildingPylonEarlyStageSpacing;
			} else {
				buildingGapSpace = Config.BuildingPylonSpacing;
			}
		} else if (buildingType == UnitType.Terran_Supply_Depot) {
			buildingGapSpace = Config.BuildingSupplyDepotSpacing;
		} else if (buildingType == UnitType.Protoss_Photon_Cannon || buildingType == UnitType.Terran_Bunker
				|| buildingType == UnitType.Terran_Missile_Turret || buildingType == UnitType.Zerg_Creep_Colony) {
			buildingGapSpace = Config.BuildingDefenseTowerSpacing;
		}

		while (buildingGapSpace >= 0) {

			testPosition = getBuildLocationNear(buildingType, desiredPosition, buildingGapSpace,
					constructionPlaceSearchMethod);
			// std::cout << "ConstructionPlaceFinder testPosition " << testPosition.x << ","
			// << testPosition.y << std::endl;

			if (testPosition != TilePosition.None && testPosition != TilePosition.Invalid)
				return testPosition;

			// 찾을 수 없다면, buildingGapSpace 값을 줄여서 다시 탐색한다
			// buildingGapSpace 값이 1이면 지상유닛이 못지나가는 경우가 많아 제외하도록 한다
			// 4 -> 3 -> 2 -> 0 -> 탐색 종료
			// 3 -> 2 -> 0 -> 탐색 종료
			// 1 -> 0 -> 탐색 종료
			if (buildingGapSpace > 2) {
				buildingGapSpace -= 1;
			} else if (buildingGapSpace == 2) {
				buildingGapSpace = 0;
			} else if (buildingGapSpace == 1) {
				buildingGapSpace = 0;
			} else {
				break;
			}
		}

		return TilePosition.None;
	}

	/// 해당 buildingType 이 건설될 수 있는 위치를 desiredPosition 근처에서 탐색해서 탐색결과를 리턴합니다<br>
	/// buildingGapSpace를 반영해서 canBuildHereWithSpace 를 사용해서 체크<br>
	/// 못찾는다면 BWAPI::TilePositions::None 을 리턴합니다<br>
	/// TODO 과제 : 건물을 계획없이 지을수 있는 곳에 짓는 것을 계속 하다보면, 유닛이 건물 사이에 갇히는 경우가 발생할 수 있는데, 이를
	/// 방지하는 방법은 생각해볼 과제입니다
	public final TilePosition getBuildLocationNear(UnitType buildingType, TilePosition desiredPosition,
			int buildingGapSpace, int constructionPlaceSearchMethod) {

		// returns a valid build location near the desired tile position (x,y).
		TilePosition resultPosition = TilePosition.None;

		ConstructionTask b = new ConstructionTask(buildingType, desiredPosition);

		// maxRange 를 설정하지 않거나, maxRange 를 128으로 설정하면 지도 전체를 다 탐색하는데, 매우 느려질뿐만 아니라, 대부분의
		// 경우 불필요한 탐색이 된다
		// maxRange 는 16 ~ 64가 적당하다
		int maxRange = 32; // maxRange = BWAPI::Broodwar->mapWidth()/4;
		boolean isPossiblePlace = false;

		if (constructionPlaceSearchMethod == ConstructionPlaceSearchMethod.SpiralMethod.ordinal()) {
			// desiredPosition 으로부터 시작해서 spiral 하게 탐색하는 방법
			// 처음에는 아래 방향 (0,1) -> 오른쪽으로(1,0) -> 위로(0,-1) -> 왼쪽으로(-1,0) -> 아래로(0,1) -> ..
			int currentX = desiredPosition.getX();
			int currentY = desiredPosition.getY();
			int spiralMaxLength = 1;
			int numSteps = 0;
			boolean isFirstStep = true;

			int spiralDirectionX = 0;
			int spiralDirectionY = 1;
			while (spiralMaxLength < maxRange) {
				if (currentX >= 0 && currentX < MyBotModule.Broodwar.mapWidth() && currentY >= 0
						&& currentY < MyBotModule.Broodwar.mapHeight()) {

					isPossiblePlace = canBuildHereWithSpace(new TilePosition(currentX, currentY), b, buildingGapSpace);

					if (isPossiblePlace) {
						resultPosition = new TilePosition(currentX, currentY);
						break;
					}
				}

				currentX = currentX + spiralDirectionX;
				currentY = currentY + spiralDirectionY;
				numSteps++;

				// 다른 방향으로 전환한다
				if (numSteps == spiralMaxLength) {
					numSteps = 0;

					if (!isFirstStep)
						spiralMaxLength++;

					isFirstStep = !isFirstStep;

					if (spiralDirectionX == 0) {
						spiralDirectionX = spiralDirectionY;
						spiralDirectionY = 0;
					} else {
						spiralDirectionY = -spiralDirectionX;
						spiralDirectionX = 0;
					}
				}
			}
		} else if (constructionPlaceSearchMethod == ConstructionPlaceSearchMethod.NewMethod.ordinal()) {
		}

		return resultPosition;
	}

	/// 해당 위치에 건물 건설이 가능한지 여부를 buildingGapSpace 조건을 포함해서 판단하여 리턴합니다<br>
	/// Broodwar 의 canBuildHere, isBuildableTile, isReservedTile 를 체크합니다
	public final boolean canBuildHereWithSpace(TilePosition position, final ConstructionTask b, int buildingGapSpace) {
		// if we can't build here, we of course can't build here with space
		if (!canBuildHere(position, b)) {
			return false;
		}

		// height and width of the building
		int width = b.getType().tileWidth();
		int height = b.getType().tileHeight();

		// define the rectangle of the building spot
		// 건물 크기보다 상하좌우로 더 큰 사각형
		int startx;
		int starty;
		int endx;
		int endy;

		boolean horizontalOnly = false;

		// Refinery 의 경우 GapSpace를 체크할 필요 없다
		if (b.getType().isRefinery()) {
		}
		// Addon 타입의 건물일 경우에는, 그 Addon 건물 왼쪽에 whatBuilds 건물이 있는지를 체크한다
		if (b.getType().isAddon()) {
			final UnitType builderType = b.getType().whatBuilds().first;

			TilePosition builderTile = new TilePosition(position.getX() - builderType.tileWidth(),
					position.getY() + 2 - builderType.tileHeight());

			startx = builderTile.getX() - buildingGapSpace;
			starty = builderTile.getY() - buildingGapSpace;
			endx = position.getX() + width + buildingGapSpace;
			endy = position.getY() + height + buildingGapSpace;

			// builderTile에 Lifted 건물이 아니고 whatBuilds 건물이 아닌 건물이 있는지 체크
			for (int i = 0; i <= builderType.tileWidth(); ++i) {
				for (int j = 0; j <= builderType.tileHeight(); ++j) {
					for (Unit unit : MyBotModule.Broodwar.getUnitsOnTile(builderTile.getX() + i,
							builderTile.getY() + j)) {
						if ((unit.getType() != builderType) && (!unit.isLifted())) {
							return false;
						}
					}
				}
			}
		} else {
			// make sure we leave space for add-ons. These types of units can have addon:
			if (b.getType() == UnitType.Terran_Command_Center || b.getType() == UnitType.Terran_Factory
					|| b.getType() == UnitType.Terran_Starport || b.getType() == UnitType.Terran_Science_Facility) {
				width += 2;
			}

			// 상하좌우에 buildingGapSpace 만큼 간격을 띄운다
			if (horizontalOnly == false) {
				startx = position.getX() - buildingGapSpace;
				starty = position.getY() - buildingGapSpace;
				endx = position.getX() + width + buildingGapSpace;
				endy = position.getY() + height + buildingGapSpace;
			}
			// 좌우로만 buildingGapSpace 만큼 간격을 띄운다
			else {
				startx = position.getX() - buildingGapSpace;
				starty = position.getY();
				endx = position.getX() + width + buildingGapSpace;
				endy = position.getY() + height;
			}

			// 테란종족 건물의 경우 다른 건물의 Addon 공간을 확보해주기 위해, 왼쪽 2칸은 반드시 GapSpace가 되도록 한다
			if (b.getType().getRace() == Race.Terran) {
				if (buildingGapSpace < 2) {
					startx = position.getX() - 2;
					endx = position.getX() + width + buildingGapSpace;
				}
			}

			// 건물이 차지할 공간 뿐 아니라 주위의 buildingGapSpace 공간까지 다 비어있는지, 건설가능한 타일인지, 예약되어있는것은 아닌지,
			// TilesToAvoid 에 해당하지 않는지 체크
			for (int x = startx; x < endx; x++) {
				for (int y = starty; y < endy; y++) {
					// if we can't build here, or space is reserved, we can't build here
					if (isBuildableTile(b, x, y) == false) {
						return false;
					}

					if (isReservedTile(x, y)) {
						return false;
					}

					// ResourceDepot / Addon 건물이 아닌 일반 건물의 경우, BaseLocation 과 Geyser 사이 타일
					// (TilesToAvoid) 에는 건물을 짓지 않는다
					if (b.getType().isResourceDepot() == false && b.getType().isAddon() == false) {
						if (isTilesToAvoid(x, y)) {
							return false;
						}
					}
				}
			}
		}

		// if this rectangle doesn't fit on the map we can't build here
		if (startx < 0 || starty < 0 || endx > MyBotModule.Broodwar.mapWidth() || endx < position.getX() + width
				|| endy > MyBotModule.Broodwar.mapHeight()) {
			return false;
		}

		return true;
	}

	/// 해당 위치에 건물 건설이 가능한지 여부를 리턴합니다 <br>
	/// Broodwar 의 canBuildHere 및 _reserveMap 와 isOverlapsWithBaseLocation 을 체크
	public final boolean canBuildHere(TilePosition position, final ConstructionTask b) {
		// This function checks for creep, power, and resource distance requirements in
		// addition to the tiles' buildability and possible units obstructing the build
		// location.
		// if (!MyBotModule.Broodwar.canBuildHere(position, b.getType(),
		// b.getConstructionWorker()))
		if (!MyBotModule.Broodwar.canBuildHere(position, b.getType())) {
			return false;
		}

		// check the reserve map
		for (int x = position.getX(); x < position.getX() + b.getType().tileWidth(); x++) {
			for (int y = position.getY(); y < position.getY() + b.getType().tileHeight(); y++) {
				// if (reserveMap.get(x).get(y))
				if (reserveMap[x][y]) {
					return false;
				}
			}
		}

		// if it overlaps a base location return false
		// ResourceDepot 건물이 아닌 다른 건물은 BaseLocation 위치에 짓지 못하도록 한다
		if (isOverlapsWithBaseLocation(position, b.getType())) {
			return false;
		}

		return true;
	}

	/// seedPosition 근처에서 Refinery 건물 건설 가능 위치를 탐색해서 리턴합니다 <br>
	/// 지도상의 여러 가스 광산 (Resource_Vespene_Geyser) 중 예약되어있지 않은 곳(isReservedTile), 다른 섬이
	/// 아닌 곳, 이미 Refinery 가 지어져있지않은 곳 중<br>
	/// seedPosition 과 가장 가까운 곳을 리턴합니다
	public final TilePosition getRefineryPositionNear(TilePosition seedPosition) {
		// BasicBot 1.1 Patch Start ////////////////////////////////////////////////
		// Refinery 건물 건설 위치 탐색 로직 버그 수정 및 속도 개선 : seedPosition 주위에서만 geyser를 찾도록, 이미
		// Refinery가 지어져있는지 체크하지 않도록 수정

		if (seedPosition == TilePosition.None || seedPosition == TilePosition.Unknown
				|| seedPosition == TilePosition.Invalid || seedPosition.isValid() == false) {
			seedPosition = info.getMainBaseLocation(self).getTilePosition();
		}

		TilePosition closestGeyser = TilePosition.None;
		double minGeyserDistanceFromSeedPosition = 100000000;

		// 전체 geyser 중에서 seedPosition 으로부터 16 TILE_SIZE 거리 이내에 있는 것을 찾는다
		for (Unit geyser : MyBotModule.Broodwar.getStaticGeysers()) {
			// geyser->getPosition() 을 하면, Unknown 으로 나올 수 있다.
			// 반드시 geyser->getInitialPosition() 을 사용해야 한다
			TilePosition geyserTilePos = geyser.getInitialTilePosition();

			// 이미 예약되어있는가
			if (isReservedTile(geyserTilePos.getX(), geyserTilePos.getY())) {
				continue;
			}

			// geyser->getType() 을 하면, Unknown 이거나, Resource_Vespene_Geyser 이거나,
			// Terran_Refinery 와 같이 건물명이 나오고,
			// 건물이 파괴되어도 자동으로 Resource_Vespene_Geyser 로 돌아가지 않는다
			// geyser 위치에 있는 유닛들에 대해 isRefinery() 로 체크를 해봐야 한다

			// seedPosition 으로부터 16 TILE_SIZE 거리 이내에 있는가
			// Fighting Spirit 맵처럼 seedPosition 으로부터 동일한 거리 내에 geyser 가 여러개 있을 수 있는 경우
			// Refinery 건물을 짓기 위해서는 seedPosition 을 정확하게 입력해야 한다
			double thisDistance = geyserTilePos.getDistance(seedPosition);

			if (thisDistance <= 16 && thisDistance < minGeyserDistanceFromSeedPosition) {
				minGeyserDistanceFromSeedPosition = thisDistance;
				closestGeyser = geyser.getInitialTilePosition();
			}
		}

		return closestGeyser;

		// BasicBot 1.1 Patch End //////////////////////////////////////////////////
	}

	/// 해당 위치가 BaseLocation 과 겹치는지 여부를 리턴합니다<br>
	/// BaseLocation 에는 ResourceDepot 건물만 건설하고, 다른 건물은 건설하지 않기 위함입니다
	public final boolean isOverlapsWithBaseLocation(TilePosition tile, UnitType type) {
		// if it's a resource depot we don't care if it overlaps
		if (type.isResourceDepot()) {
			return false;
		}

		// dimensions of the proposed location
		int tx1 = tile.getX();
		int ty1 = tile.getY();
		int tx2 = tx1 + type.tileWidth();
		int ty2 = ty1 + type.tileHeight();

		// for each base location
		for (BaseLocation base : BWTA.getBaseLocations()) {
			// dimensions of the base location
			int bx1 = base.getTilePosition().getX();
			int by1 = base.getTilePosition().getY();
			int bx2 = bx1 + UnitType.Terran_Command_Center.tileWidth();
			int by2 = by1 + UnitType.Terran_Command_Center.tileHeight();

			// conditions for non-overlap are easy
			boolean noOverlap = (tx2 < bx1) || (tx1 > bx2) || (ty2 < by1) || (ty1 > by2);

			// if the reverse is true, return true
			if (!noOverlap) {
				return true;
			}
		}

		// otherwise there is no overlap
		return false;
	}

	/// 건물 건설 가능 타일인지 여부를 리턴합니다
	public final boolean isBuildableTile(final ConstructionTask b, int x, int y) {
		TilePosition tp = new TilePosition(x, y);
		if (!tp.isValid()) {
			return false;
		}

		// 맵 데이터 뿐만 아니라 빌딩 데이터를 모두 고려해서 isBuildable 체크
		// if (BWAPI::Broodwar->isBuildable(x, y) == false)
		if (MyBotModule.Broodwar.isBuildable(x, y, true) == false) {
			return false;
		}

		// constructionWorker 이외의 다른 유닛이 있으면 false를 리턴한다
		for (Unit unit : MyBotModule.Broodwar.getUnitsOnTile(x, y)) {
			if ((b.getConstructionWorker() != null) && (unit != b.getConstructionWorker())) {
				return false;
			}
		}

		return true;
	}

	/// 건물 건설 예정 타일로 예약해서, 다른 건물을 중복해서 짓지 않도록 합니다
	public void reserveTiles(TilePosition position, int width, int height) {
		int rwidth = reserveMap.length;
		int rheight = reserveMap[0].length;
		for (int x = position.getX(); x < position.getX() + width && x < rwidth; x++) {
			for (int y = position.getY(); y < position.getY() + height && y < rheight; y++) {
				// reserveMap.get(x).set(y, true);
				reserveMap[x][y] = true;
				// C++ : reserveMap[x][y] = true;
			}
		}
	}

	/// 건물 건설 예정 타일로 예약했던 것을 해제합니다
	public void freeTiles(TilePosition position, int width, int height) {
		int rwidth = reserveMap.length;
		int rheight = reserveMap[0].length;

		for (int x = position.getX(); x < position.getX() + width && x < rwidth; x++) {
			for (int y = position.getY(); y < position.getY() + height && y < rheight; y++) {
				// reserveMap.get(x).set(y, false);
				reserveMap[x][y] = false;
				// C++ : reserveMap[x][y] = false;
			}
		}
	}

	// 건물 건설 예약되어있는 타일인지 체크
	public final boolean isReservedTile(int x, int y) {
		int rwidth = reserveMap.length;
		int rheight = reserveMap[0].length;
		if (x < 0 || y < 0 || x >= rwidth || y >= rheight) {
			return false;
		}

		return reserveMap[x][y];
	}

	/// reserveMap을 리턴합니다
	public boolean[][] getReserveMap() {
		return reserveMap;
	}

	/// (x, y) 가 BaseLocation 과 Mineral / Geyser 사이의 타일에 해당하는지 여부를 리턴합니다
	public final boolean isTilesToAvoid(int x, int y) {
		for (TilePosition t : tilesToAvoid) {
			if (t.getX() == x && t.getY() == y) {
				return true;
			}
		}

		return false;
	}

	/// BaseLocation 과 Mineral / Geyser 사이의 타일들을 찾아 _tilesToAvoid 에 저장합니다<br>
	/// BaseLocation 과 Geyser 사이, ResourceDepot 건물과 Mineral 사이 공간으로 건물 건설 장소를
	/// 정하면<br>
	/// 일꾼 유닛들이 장애물이 되어서 건설 시작되기까지 시간이 오래걸리고, 지어진 건물이 장애물이 되어서 자원 채취 속도도 느려지기 때문에, 이
	/// 공간은 건물을 짓지 않는 공간으로 두기 위함입니다
	public void setTilesToAvoid() {
		// ResourceDepot 건물의 width = 4 타일, height = 3 타일
		// Geyser 의 width = 4 타일, height = 2 타일
		// Mineral 의 width = 2 타일, height = 1 타일

		for (BaseLocation base : BWTA.getBaseLocations()) {
			// Island 일 경우 건물 지을 공간이 절대적으로 좁기 때문에 건물 안짓는 공간을 두지 않는다
			if (base.isIsland())
				continue;
			if (BWTA.isConnected(base.getTilePosition(), info.getMainBaseLocation(self).getTilePosition()) == false)
				continue;

			// dimensions of the base location
			int bx0 = base.getTilePosition().getX();
			int by0 = base.getTilePosition().getY();
			int bx4 = base.getTilePosition().getX() + 4;
			int by3 = base.getTilePosition().getY() + 3;

			// BaseLocation 과 Geyser 사이의 타일을 BWTA::getShortestPath 를 사용해서 구한 후 _tilesToAvoid
			// 에 추가
			for (Unit geyser : base.getGeysers()) {
				TilePosition closeGeyserPosition = geyser.getInitialTilePosition();

				// dimensions of the closest geyser
				int gx0 = closeGeyserPosition.getX();
				int gy0 = closeGeyserPosition.getY();
				int gx4 = closeGeyserPosition.getX() + 4;
				int gy2 = closeGeyserPosition.getY() + 2;

				for (int i = bx0; i < bx4; i++) {
					for (int j = by0; j < by3; j++) {
						for (int k = gx0; k < gx4; k++) {
							for (int l = gy0; l < gy2; l++) {
								List<TilePosition> tileList = (List<TilePosition>) BWTA
										.getShortestPath(new TilePosition(i, j), new TilePosition(k, l));
								for (TilePosition t : tileList) {
									tilesToAvoid.add(t);
								}
							}
						}
					}
				}
			}

			// BaseLocation 과 Mineral 사이의 타일을 BWTA::getShortestPath 를 사용해서 구한 후
			// _tilesToAvoid 에 추가
			for (Unit mineral : base.getMinerals()) {
				TilePosition closeMineralPosition = mineral.getInitialTilePosition();

				// dimensions of the closest mineral
				int mx0 = closeMineralPosition.getX();
				int my0 = closeMineralPosition.getY();
				int mx2 = mx0 + 2;

				for (int i = bx0; i < bx4; i++) {
					for (int j = by0; j < by3; j++) {
						for (int k = mx0; k < mx2; k++) {
							List<TilePosition> tileList = (List<TilePosition>) BWTA
									.getShortestPath(new TilePosition(i, j), new TilePosition(k, my0));
							for (TilePosition t : tileList) {
								tilesToAvoid.add(t);
							}
						}
					}
				}
			}
		}
	}

	/// BaseLocation 과 Mineral / Geyser 사이의 타일들의 목록을 리턴합니다
	public Set<TilePosition> getTilesToAvoid() {
		return tilesToAvoid;
	}

}